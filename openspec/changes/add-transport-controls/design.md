## Context

Only folder books exist in the app today — `.m4b` support was built as a standalone parser
(`parse-m4b-chapters`) but never wired into the library. That matters for this change: `BookTimeline`
has to be shaped for both a folder book's multi-`MediaItem` playlist and an `.m4b` book's single item
with internal chapter marks, but only the folder shape can be exercised end to end right now. The
`.m4b` shape is unit-tested against synthetic data, not real playback, until the next change supplies
real chapters.

What exists to build on: `PlaybackService` owns one `ExoPlayer`/`MediaSession`; `PlayerViewModel`
connects a `MediaController` and loads a book's chapters as `MediaItem`s via `mediaItemsFor()`, whose
media ids already encode `"$bookId:$chapterIndex"`; folder chapters have no stored duration.

## Goals / Non-Goals

**Goals:**

- ±10s/±60s seek that crosses chapter boundaries; previous/next chapter with the 3-second rule,
  applied uniformly and reachable from Bluetooth, not just the in-app buttons.
- A book-wide scrubber with a drag timestamp.
- Speed 0.75x–3.0x with pitch preserved, remembered per book with a sensible global fallback.
- Progress that survives the app being closed, written by the component that outlives the UI.
- A coordinate mapping general enough that wiring `.m4b` later is additive, not a rewrite.

**Non-Goals:**

- `.m4b` wiring, chapter durations in Room, library-list progress display — see the proposal.
- Full cold-process-death resumption via a session command — see D6.

## Decisions

### D1: `BookTimeline` maps player coordinates to book coordinates, generically over both shapes

```kotlin
data class ChapterBounds(
    val chapterIndex: Int,
    val mediaItemIndex: Int,
    val startInItemMs: Long,
    val endInItemMs: Long?,   // null = not yet known (folder) or open-ended (last chapter)
)

class BookTimeline(private val bounds: List<ChapterBounds>) {
    fun locate(mediaItemIndex: Int, positionMs: Long): Location
    fun seekTarget(from: Location, deltaMs: Long): PlayerTarget      // clamps/rolls across chapters
    fun previousChapterTarget(from: Location, toleranceMs: Long = 3000): PlayerTarget
    fun nextChapterTarget(from: Location): PlayerTarget?             // null at the last chapter
}
```

*Why this shape:* a folder book's bounds are `chapterIndex == mediaItemIndex`, `startInItemMs = 0`,
`endInItemMs` from the player's own `Timeline` once known. An `.m4b` book's bounds all share
`mediaItemIndex = 0`, with `startInItemMs`/`endInItemMs` taken from the parsed `Chapter` list. Both
reduce to the same four operations, so seek, previous/next, and the scrubber are written once and
work for whichever shape is loaded — which is the entire point of building this now rather than
waiting for `.m4b` to force a rewrite of code already written against one shape.

*Alternative considered:* two separate implementations (`FolderTimeline`, `M4bTimeline`) behind a
common interface. Rejected — the four operations are identical once expressed in terms of bounds;
a second implementation would duplicate the boundary-crossing arithmetic for no behavioral gain,
and `config.yaml` already rules out speculative interface layers.

*Consequence:* `endInItemMs` can be `null` for a folder chapter whose duration ExoPlayer has not
yet resolved. `seekTarget` degrades to "clamp at chapter end" rather than rolling over in that case
— which is exactly the fallback PRD §7.3 explicitly allows.

### D2: Seek buttons compute their own target via `BookTimeline`, not `Player.seekForward()`/`seekBack()`

*Why not the built-ins:* ExoPlayer's `seekForwardIncrementMs`/`seekBackIncrementMs` are a single
configured pair, and — more importantly — they clamp within the current `MediaItem`. They cannot
express "±60s that rolls into the next chapter when it overflows," which PRD §7.3 asks for. All
four seek buttons (−1m, −10s, +10s, +1m) call `BookTimeline.seekTarget` with a signed delta and the
result is applied with `controller.seekTo(mediaItemIndex, positionMs)`. This is a deliberate
departure from "prefer built-in APIs" (PRD §28 rule 3), justified because the built-in does not do
the thing required.

### D3: The 3-second rule lives in a `ForwardingPlayer`, so Bluetooth gets it too

`PlaybackService` wraps the `ExoPlayer` handed to `MediaSession` in a small `ForwardingPlayer` that
overrides `seekToPrevious()` (and `seekToPreviousMediaItem()`) to consult `BookTimeline` instead of
Media3's default "always go to the previous item" behavior. `seekToNext()` is left as stock — PRD
§7.4's "do nothing at the final chapter" and "go to next file" are already what the default gives.

*Why here and not in the UI layer:* a Bluetooth remote or a car head unit calls session commands
directly; it does not go through `PlayerViewModel`. If the 3-second rule only lived in an in-app
button's click handler, pressing "previous" from a steering wheel would restart-or-skip using
Media3's default rule instead, which is exactly the inconsistency this design was raised to prevent
(`add-playback-service` design D7 flagged this as the moment `ForwardingPlayer` would become
necessary).

*Applied uniformly:* the owner decided the 3-second rule should apply to folder books too, not just
`.m4b` as PRD §7.4 states. Restarting the current chapter is also the choice at the very first
chapter — no special "do nothing" case — since a uniform rule is simpler to reason about than one
with an edge-case exception, and restarting the first chapter has no user-visible downside.

### D4: The book-wide scrubber aggregates durations from the player's live `Timeline`, not from Room

Folder chapter durations are not persisted — `add-folder-audiobooks` deliberately deferred that to
the metadata change. Rather than block the scrubber on that future work, `PlayerViewModel` reads
`Player.Timeline` for the currently loaded book each time it is opened, summing each window's
duration as it becomes known (`onTimelineChanged`) and treating not-yet-known windows as
contributing zero until they resolve. Media3 determines a local file's duration from its container
metadata, not by decoding it, so this is expected to converge in well under a second even for a
128-chapter book — verified on-device against Born To Run rather than assumed (§7 tasks).

*Consequence:* the scrubber's total can visibly grow for a moment after opening a large book, from
an undercount toward the true total. Treated as an acceptable, honest reflection of what is
actually known, rather than blocking the screen on a full-book duration scan.

*Not persisted here:* this aggregation is session-only. Caching it in Room is explicitly left to
the metadata change, which needs per-chapter durations for other reasons (PRD §11) and can populate
it once, at add-time, rather than every time a book is opened.

### D5: Speed uses `PlaybackParameters(speed, pitch = 1.0f)`, per book with a global fallback

Setting pitch explicitly to `1.0f` alongside a changed speed is what invokes ExoPlayer's Sonic-based
time-stretching rather than a naive resample — this is the mechanism PRD §9 is pointing at when it
says "use Media3/ExoPlayer playback parameters... not a sample-rate hack."

Resolution order on opening a book: the book's own saved speed (Room, if set) → the last globally
used speed (DataStore) → `1.0f`. Changing speed during playback writes both the per-book value and
the global one, so the *next new* book defaults to whatever the owner was last using — matching
PRD §9's stated preference exactly.

*Why DataStore for the global value and a Room column for the per-book one:* `config.yaml`
explicitly names this split — DataStore for a single lightweight preference, Room for anything
tied to a specific entity. One global float does not warrant its own table.

### D6: Progress is persisted by the service, driven by the player's own events

`PlaybackService` adds a second `Player.Listener` (alongside the session's own) that writes
`lastMediaItemIndex` and `lastPositionMs` to the book's `AudiobookEntity` row on: `isPlaying`
becoming `false`, a media-item transition, a 15-second ticker while playing, and `onDestroy()`. The
book id is read from the current `MediaItem`'s media id (already `"$bookId:$chapterIndex"`), so the
service does not need to track "which book is this" separately.

*Why raw player coordinates, not chapter-domain ones:* PRD §19 explicitly allows "simpler equivalent
storage." Storing `(mediaItemIndex, positionMs)` round-trips through `controller.seekTo(index,
position)` with no translation, for either book shape — for `.m4b` there is exactly one item, so
`positionMs` already *is* the absolute book position. Storing chapter index and offset instead would
require re-deriving player coordinates on resume for no benefit.

*Why the service and not the UI:* this directly answers the question the previous change's design
raised and deliberately left open (`add-playback-service` design D2's flagged revisit). The service
outlives the UI and is exactly the component PRD §12's "on service shutdown" example describes.
Persisting from the UI would miss progress made after the Activity is gone but before the service
stops.

*What this does not do:* it does not make the service resume playback automatically after the whole
process is killed. That needs `MediaSession.Callback.onPlaybackResumption`, which is a larger,
separate mechanism explicitly deferred to the open questions.

### D7: Resuming a book seeks to the saved position after `prepare()`, still without auto-playing

`PlayerViewModel.loadBook` — after `setMediaItems` and `prepare()`, and only when the controller did
not already hold this book (the existing idempotence guard) — checks for saved
`lastMediaItemIndex`/`lastPositionMs` and, if the index is still valid for the loaded chapter count,
calls `controller.seekTo(index, position)`. `playWhenReady` stays `false`: opening a book still does
not start it (unchanged from `add-playback-service` design D8).

### D8: Migration is explicit; no destructive fallback

`Migration(1, 2)` runs three `ALTER TABLE audiobooks ADD COLUMN` statements. This is the project's
first real migration, and it exists specifically because `add-folder-audiobooks` design D7 already
ruled out `fallbackToDestructiveMigration` — a schema change without a migration would crash on
upgrade rather than silently wiping data, which is the correct failure mode, but a written migration
is the better one.

## Risks / Trade-offs

**`ForwardingPlayer` misreporting available commands** → a Bluetooth or car UI that thinks "previous"
is unavailable when it should not be (or vice versa) looks like broken hardware. Verified against
the actual availableCommands the wrapped player reports, not assumed correct from the override alone.

**Scrubber total is best-effort and can move after the screen appears** → acceptable and disclosed
(D4); the alternative (blocking on a full scan) is worse for a 128-chapter book.

**A second `Player.Listener` writing to Room from the service** → runs on top of whatever listener
the session already needs; kept intentionally simple (four trigger points, direct DAO calls in a
service-scoped coroutine) rather than introducing a queue or debouncer that this app's scale does
not need.

**First real Room migration** → tested against the committed v1 schema specifically, not just
"assumed to work because the SQL looks right."

## Migration Plan

Room `Migration(1, 2)` is additive and non-destructive: three nullable columns, no data loss, no
existing row invalidated. Existing books (already added by the owner during verification) keep
working with `lastMediaItemIndex`/`lastPositionMs`/`playbackSpeed` simply absent until first played
under the new version.

## Open Questions

- **Full process-death resumption** (D6): worth real design attention once it matters in practice —
  it needs `onPlaybackResumption`, which is a genuinely separate mechanism, not a natural extension
  of what this change builds.
- **Speed control's exact widget** — PRD §9 allows "a slider or button/menu." Proposing a compact
  dropdown over the 14 fixed stops, since they are discrete values rather than a continuum and a
  slider invites imprecise selection between them. Resolved during implementation, not a blocking
  decision.
