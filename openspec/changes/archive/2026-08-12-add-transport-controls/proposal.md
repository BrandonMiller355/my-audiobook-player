## Why

Playback works, but it is play/pause only: no seeking, no chapter navigation, no speed, and closing
the app loses your place. This change delivers the rest of PRD §7.2's playback screen — ±10s/±60s
seek, previous/next chapter, a book-wide scrubber, playback speed — and makes progress and speed
survive a restart (PRD §12, §9).

It also settles the mapping this project has been deferring since the very first exploration: a
folder book is a Media3 playlist (one `MediaItem` per file), while an `.m4b` book will be one
`MediaItem` with internal chapter marks. Seeking across a chapter boundary, previous/next, and the
book-wide scrubber all need one coordinate system that works for both shapes — that is `BookTimeline`,
introduced here and exercised now against folder books, so the `.m4b` change that follows only has to
supply chapter data, not invent the mapping.

The 3-second previous-chapter rule applies to both book types, per the owner's decision when the
slicing was set — PRD §7.4 states it only for `.m4b`; `BookTimeline` makes "both" the natural
outcome rather than a special case.

## What Changes

- Add `BookTimeline`, a pure-Kotlin coordinate mapping between `(mediaItemIndex, positionMs)` and
  `(chapterIndex, offsetMs)`, built once against folder books and designed to also accept the
  `.m4b` shape (one item, chapters as sub-ranges) — covered by unit tests now, wired to real `.m4b`
  data by the next change.
- Wrap the session's player in a `ForwardingPlayer` that applies the 3-second rule to
  `seekToPrevious`, so Bluetooth and lock-screen "previous" get the same behavior as the in-app
  button — not just the UI's own call site.
- Add ±10s / ±60s seek buttons that roll across chapter boundaries rather than clamping to the
  current file, and previous/next chapter buttons.
- Add a book-wide progress slider showing absolute position across the whole book, with a
  timestamp readout while dragging and no commit until release.
- Add a playback speed control (0.75x–3.0x, PRD §9's 14 stops) using `PlaybackParameters` with
  pitch pinned to 1.0, so speed changes never produce the chipmunk effect.
- Persist playback position and speed: the service — not the UI — writes progress on pause, on
  chapter change, periodically while playing, and on its own shutdown, so it survives even when
  no Activity is attached. Speed is remembered per book, falling back to the most recently used
  global speed, falling back to 1.0x (PRD §9).
- Add Room schema version 2 (three new nullable columns on `audiobooks`, with a real migration —
  no destructive fallback) and a DataStore-backed single value for the last-used global speed, per
  `config.yaml`'s guidance that DataStore fits a value this small better than a table.

## Capabilities

### New Capabilities

- `transport-controls`: seeking, chapter navigation with the 3-second rule, the book-wide scrubber,
  playback speed, and the persistence of both position and speed across sessions.

### Modified Capabilities

None. The existing `playback` capability's Bluetooth previous/next requirement stays true; this
change makes it more precise (the 3-second rule) rather than contradicting it, so no delta is
needed there. `audiobook-library`'s requirements are unaffected — the schema changes are additive
columns with no user-visible library behavior attached to them.

## Impact

**Code**: New `playback/BookTimeline.kt` (pure logic, no Android dependency) and its unit tests.
`PlaybackService` gains a `ForwardingPlayer` wrapper and a progress-writing `Player.Listener`.
`PlayerViewModel` and `PlayerScreen` gain the seek/chapter/speed/scrubber UI. `AudiobookEntity`
gains three nullable columns; a `Migration(1, 2)` is added; a small `SpeedPreferences` wrapper
around DataStore is added.

**Dependencies**: `androidx-datastore-preferences`, already declared in the version catalog and
already named in `config.yaml` for exactly this purpose. No other dependency.

**Data**: First real schema migration. `app/schemas` gains the version-2 export; the migration is
tested against the committed version-1 schema rather than assumed correct.

**Risk**: `ForwardingPlayer` sits between the session and Bluetooth/lock-screen clients — getting
its command availability wrong could make hardware buttons appear to do nothing, which is worse
than not having them. The book-wide scrubber's total duration is not stored anywhere yet (the
folder-audiobooks change deferred chapter durations to the metadata change), so it is computed
in-memory from the player's own timeline each time a book is opened; verifying this converges
quickly on a 128-chapter book is an explicit task, not an assumption.

## Non-goals

This change deliberately does **not**:

- Wire `.m4b` books into the library or call the chapter parser. `BookTimeline` is built to accept
  that shape and unit-tested against it, but no `.m4b` book exists in the app yet to exercise it
  end-to-end — that is the next change.
- Store chapter durations in Room, or show a progress percentage or duration on the Library list.
  Both need per-chapter durations captured at add-time, which is the metadata change's job; this
  change only aggregates durations transiently, in memory, for the currently open book's scrubber.
- Restore playback across a full process kill via a session resumption command. Progress is saved
  continuously and reload-on-open restores position, but the service itself does not yet resume
  automatically if the whole process was killed while backgrounded — a smaller, real improvement
  over today, not the complete PRD §12 story.
- Add a sleep timer, bookmarks, or any other PRD non-goal.
- Change how folder books are scanned, added, or removed.
