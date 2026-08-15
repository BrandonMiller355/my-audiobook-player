## Context

`M4bChapterParser` works and is unit-tested against synthetic fixtures, but no code calls it.
`BookTimeline` already accepts both book shapes — `add-transport-controls` D1 built it that way
on purpose — and `ChapterEntity` already carries `mediaUri` plus `startPositionMs` so that
`.m4b` chapters are extra rows rather than a schema change. This change is the one that finds out
whether those two bets were right.

Current state that matters here:

- `PlayerTimeline.chapterTimeline()` derives `ChapterBounds` from the player's playlist: one
  chapter per `MediaItem`, `startInItemMs = 0`, end from resolved durations or the live
  `Timeline`. For a single-item `.m4b` book this yields exactly one chapter, which is wrong.
- `ChapterAwarePlayer` wraps the session's player inside `PlaybackService` and calls the same
  `chapterTimeline()`. It is what makes a Bluetooth "previous" obey the 3-second rule. It has no
  access to the database and no knowledge of which book is loaded beyond the media id.
- `PlayerViewModel` takes the chapter title from `currentMediaItem.mediaMetadata.title`. For a
  single-item book that is the book's name, on every chapter.
- Progress is stored as raw player coordinates (`lastMediaItemIndex`, `lastPositionMs`). For a
  `.m4b` that is `(0, absolute position)`, which already round-trips correctly with no
  translation — `Entities.kt` says so explicitly and this change does not disturb it.
- Every `.m4b` in the owner's library parses as `Unchaptered`: one 10–14 hour mark spanning the
  file. The chaptered path cannot be exercised on the device with real books.

## Goals / Non-Goals

**Goals:**

- Add a `.m4b` from the system picker, with a grant that survives reboot.
- Parse chapters once, at add time, and never re-read the container on later launches.
- Play a `.m4b` as one `MediaItem` whose chapter navigation is internal seeking, with the same
  behavior from the in-app buttons, the notification, and a Bluetooth remote.
- Extract embedded artwork once and show it without decoding a multi-gigabyte file per library row.
- Keep the unchaptered and unreadable outcomes fully usable, since they are all the owner has.

**Non-Goals:**

- Folder-book cover art, author/album metadata, library-row progress — see the proposal.
- Changing anything about how chapters are parsed. `m4b-chapters` is settled.
- Showing chapter titles on the notification and lock screen for `.m4b` books — see D1's
  trade-off and Open Questions.

## Decisions

### D1: Chapter bounds travel inside the `MediaItem`, not fetched at the service

`ChapterAwarePlayer` has to answer "where is the previous chapter?" synchronously, on the main
thread, possibly with no Activity alive — a Bluetooth press after the app's UI is gone. For a
folder book it reads the answer off the playlist. A single-item book has no playlist shape to
read, so the bounds must come from somewhere else.

They are attached to the `MediaItem` when the playlist is set:

```kotlin
MediaMetadata.Builder()
    .setExtras(Bundle().apply {
        putLongArray(EXTRA_CHAPTER_STARTS_MS, starts)   // absolute, ascending, starts[0] == 0
        putLong(EXTRA_BOOK_DURATION_MS, durationMs)
    })
```

`chapterTimeline()` gains exactly one branch: if the current item carries these extras, build
`ChapterBounds` from them — all at `mediaItemIndex = 0`, `endInItemMs[i] = starts[i + 1]`, last
end `= durationMs`; otherwise derive per-item as today. Ends are chained rather than stored
twice, which enforces the no-gaps invariant `BookTimeline` requires instead of trusting two
arrays to agree.

*Why this over the alternatives:*

- **Service reads chapters from Room** (keyed by `mediaIdBookId`, cached per book). The service
  already holds the DAO, so this is not foreign — but it puts an asynchronous database read
  behind a synchronous `seekToPrevious()`. Pre-warming on media-item transition mostly hides
  that, and then loses the race exactly in the case that matters: the first Bluetooth press
  after a cold start. Rejected for that.
- **A session command from `PlayerViewModel`.** Requires the ViewModel to exist. It often does
  not — that is the entire point of the service outliving the UI. Rejected.
- **Two player wrappers, one per book type.** The service would have to know which is loaded
  before the playlist arrives. Rejected; also the kind of speculative split `config.yaml` rules
  out.

Attaching them to the item means whoever sets the playlist supplies the bounds, so every control
surface reads one source and cannot disagree — the same reasoning that put the book id in the
media id.

*Trade-off:* the bounds are duplicated into a `Bundle` that crosses the Binder boundary. The
parser caps chapters at 10,000, so the worst case is an 80 KB `long[]`, well under the ~1 MB
transaction limit; a real book is a few hundred entries. Accepted.

*Consequence — `seekToNext` must now be wrapped too.* Today `ChapterAwarePlayer` overrides only
"previous", because Media3's default "next item" already is "next chapter" for a folder book. On
a single-item book the default does nothing. So next/`seekToNextMediaItem` get the same treatment
as previous, driven by `nextChapterTarget`, and `COMMAND_SEEK_TO_NEXT` joins the always-available
set. For folder books this is behavior-neutral: seeking to the next chapter's start at
`startInItemMs = 0` of the next item is what the default already did.

### D2: `.m4b` chapter ends are stored; folder chapter durations still are not

`chapters` gains a nullable `endPositionMs`. The parser already computes every chapter's end —
chained from the next start, with the last taken from `mvhd` — so storing it costs nothing at
add time and buys a lot: the scrubber's total is exact from the moment the book opens rather
than converging, and no `MediaMetadataRetriever` pass is needed for `.m4b` books at all.

Folder chapters keep `endPositionMs` null and keep resolving durations at runtime through
`resolveChapterDurations`. That is not inconsistency for its own sake: a folder book's durations
require opening every one of up to 128 files, which is why `add-folder-audiobooks` D5 chose not
to persist them, and nothing in this change makes that cheaper. A `.m4b`'s ends come free with a
parse that is already happening.

*Alternative considered:* derive ends at load time from the next chapter's start plus a
`MediaMetadataRetriever` read for the final one. Works, and avoids the migration — but it re-reads
the container on every open, which PRD §8 and §23 specifically ask the app not to do, and leaves
the final chapter's end unknown until an asynchronous read lands.

### D3: A custom contract, because `OpenDocument` does not ask for a persistable grant

`ActivityResultContracts.OpenDocumentTree` yields a URI the app can call
`takePersistableUriPermission` on, which is why folder books survive reboot today.
`ActivityResultContracts.OpenDocument` does not add `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` to
its intent, and without that flag the take call fails and the book breaks on the next launch —
silently, and not until much later, which is the worst shape a bug can have.

So: a small `ActivityResultContract` subclassing `OpenDocument` and overriding `createIntent` to
add the read and persistable flags. `UriPermissionHolder` needs no change — it takes and releases
by URI with the same read flag, and a document URI is not special to it.

### D4: The picker filters loosely; the extension decides

`.m4b` is reported as `audio/mp4`, `audio/x-m4b`, `audio/m4b`, or `application/octet-stream`
depending on the provider — the same inconsistency that made `folder-scanning` require extension
matching rather than MIME matching. The picker therefore offers
`arrayOf("audio/*", "application/octet-stream")` so nothing is hidden, and the picked file is
accepted only if its display name ends in `.m4b`, case-insensitively.

Anything else is refused with a message naming what was picked, and its grant is released rather
than leaked — the same discipline `LibraryViewModel` already applies to a folder with no audio.

### D5: Unchaptered and unreadable produce the same book, and differ only in what is said

Both become a one-chapter book spanning the whole file, titled with the book's title. PRD §7.4
requires that for the unchaptered case. Unreadable is treated identically because the audio is
almost certainly fine — the parser failing on a chapter atom says nothing about whether ExoPlayer
can decode the stream — and refusing to add a playable book because a metadata box is malformed
would be a worse outcome than losing chapter marks. The user is told chapters could not be read;
the book plays.

This is also what keeps the change useful on the owner's device: every book in the library takes
this path.

### D6: Chapter identity comes from the stored chapter list, for both book types

`PlayerViewModel` already loads the book's chapters. It keeps them and reads the title at
`location.chapterIndex` rather than from `currentMediaItem.mediaMetadata.title`. For `.m4b` this
is the only correct source; for folder books it is equivalent to what is displayed today, from
the row the chapter came from rather than from a copy of it that made a round trip through the
session.

### D7: Artwork is decoded once at add time into a bounded app-private file

`MediaMetadataRetriever.embeddedPicture` returns the artwork bytes. They are decoded with
`inJustDecodeBounds` first, downsampled via `inSampleSize` to a maximum edge of 512 px, and
written as JPEG to `filesDir/covers/<bookId>.jpg`. `audiobooks.artworkPath` holds the path;
null means "show the placeholder". Coil loads the file into the library rows and the Player.

*Why not store the bytes in Room:* cover art runs to hundreds of kilobytes, and a blob column is
read by the library query on every emission.

*Why not decode from the source on demand:* opening a multi-gigabyte `.m4b` once per library row
is exactly the performance failure PRD §23 names.

*Why `filesDir` and not `cacheDir`:* the system may evict `cacheDir` at any time, and covers
would vanish with no way to notice or rebuild them short of reparsing. The files are small and
are deleted with the book.

Removal has to delete the cover file explicitly — Room's cascade covers rows, not files. The
source `.m4b` is never touched; a downsampled thumbnail in private storage is a cache, not a
copy of the user's image, which is what PRD §10 forbids.

### D8: A `FileChannel` over the content URI feeds the parser

`M4bChapterParser` needs a `SeekableByteChannel` because `moov` can sit after a multi-gigabyte
`mdat`. `contentResolver.openFileDescriptor(uri, "r")` then
`FileInputStream(pfd.fileDescriptor).channel` gives one, closed with the descriptor.

Some providers hand back a pipe rather than a real file, and a pipe cannot seek. That needs no
handling here: `m4b-chapters` already specifies `Unreadable` with a seek reason for exactly this
input, and D5 already says an unreadable book is added as one chapter. The awkward case was
designed for before it existed.

## Risks / Trade-offs

- **`MediaMetadata` extras might not survive the controller → session boundary intact.** The
  whole of D1 rests on it. → Verified first, before anything is built on top of it: set a
  playlist with extras from the ViewModel, read them back off `mediaSession.player`'s current
  item in the service. If they do not survive, D1's rejected alternative (service reads Room,
  pre-warmed on transition) is the fallback and the rest of the change is unaffected.
- **The notification and lock screen will show the book title where a folder book shows the
  chapter title.** `MediaMetadata.title` is per-item and a `.m4b` has one item. → Accepted for
  this change. Fixing it means the service updating session metadata on every chapter boundary,
  which is a real feature with its own state to keep correct, and PRD §14's requirement is about
  controls working, not about what text appears.
- **The chaptered path ships without device verification.** No real chaptered `.m4b` exists in
  the owner's library. → Unit tests cover the timeline arithmetic against synthetic chapter
  lists, and the coordinate mapping being exercised is the same `BookTimeline` that folder books
  have proven on the device. The residual risk is in the wiring, not the math.
- **A 10,000-chapter book would put an 80 KB array through Binder on every playlist set.** →
  Bounded by the parser's existing cap and far under the transaction limit. Noted, not mitigated.
- **Wrapping `seekToNext` changes a code path folder books depend on.** → Behavior-neutral by
  construction (next chapter start == next item position 0), and folder next/previous is on the
  manual verification list.

## Migration Plan

Room 2 → 3, additive and non-destructive, matching `MIGRATION_1_2`'s shape:

```sql
ALTER TABLE chapters   ADD COLUMN endPositionMs INTEGER;
ALTER TABLE audiobooks ADD COLUMN artworkPath   TEXT;
```

No backfill. Existing folder books keep both null and behave exactly as before — `endPositionMs`
null is the "duration not yet known" state `BookTimeline` already handles, and a null
`artworkPath` shows the placeholder. `fallbackToDestructiveMigration` stays absent. The migration
gets the same Robolectric test treatment as `MIGRATION_1_2`, including a v2 database with a
folder book in it opening cleanly at v3 with its rows intact.

Rollback is a downgrade problem only — the columns are ignorable, but Room refuses to open a v3
file at v2, so a rollback means restoring the database file, not just the code.

## Open Questions

- Should the session's metadata track the current chapter so the notification names it? Deferred;
  it is a behavior change for folder books too, and belongs with whatever change next touches the
  notification.
- Should folder books also carry baked bounds in their items once durations resolve, retiring the
  live-`Timeline` fallback? Possibly tidier, but it would mean rewriting the playlist mid-playback
  to update metadata. Not now.
- Whether a `.m4b` should be offered a cover from a sibling image file when it has no embedded
  art. That is folder-style lookup, which this change scopes out; revisit with the folder cover
  change.
