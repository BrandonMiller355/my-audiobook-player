## 1. BookTimeline (pure logic, no Android dependency)

- [x] 1.1 Define `ChapterBounds` and `Location`/`PlayerTarget` types (design D1)
- [x] 1.2 Implement `locate(mediaItemIndex, positionMs)` mapping player coordinates to `(chapterIndex, offsetMs)`
- [x] 1.3 Implement `seekTarget(from, deltaMs)`: clamps at the book's start and end, rolls into the neighboring chapter when its boundary is known, clamps to the chapter end when it is not (design D1, D2)
- [x] 1.4 Implement `previousChapterTarget(from, toleranceMs = 3000)`: restarts the current chapter past the tolerance, otherwise targets the previous chapter; restarts at the first chapter rather than no-op (design D3)
- [x] 1.5 Implement `nextChapterTarget(from)`: targets the next chapter's start, or `null` at the final chapter
- [x] 1.6 Unit-test the **folder shape** (bounds where `chapterIndex == mediaItemIndex`) against cases mirroring Born To Run: mid-book seek, cross-boundary forward seek, clamp at book start/end, 3-second rule on both sides of the threshold, next-chapter no-op at the end
  - Found and fixed a real bug during this: `rollBackward`'s deficit math was wrong (returned the wrong chapter's start instead of the correct offset into the previous chapter). Caught by the folder- and m4b-shape cross-boundary tests, both of which asserted the same coordinates and both failed identically before the fix.
- [x] 1.7 Unit-test the **`.m4b` shape** (bounds sharing one `mediaItemIndex` with sub-range `startInItemMs`/`endInItemMs`) with the same scenarios, even though nothing in the app produces this shape yet
- [x] 1.8 Unit-test the unknown-duration case: `seekTarget` clamps to the chapter end rather than rolling over when `endInItemMs` is null
  - 18 tests total, all passing.

## 2. ForwardingPlayer

- [x] 2.1 Implement a `ForwardingPlayer` wrapping the service's `ExoPlayer`, overriding `seekToPrevious()`/`seekToPreviousMediaItem()` to compute its target via `BookTimeline` instead of the default (design D3)
- [x] 2.2 Give `MediaSession.Builder` the forwarding player rather than the raw `ExoPlayer`, so session commands — including Bluetooth — go through it
- [x] 2.3 Confirm `getAvailableCommands()` still reports `COMMAND_SEEK_TO_PREVIOUS` correctly at every position, including the first chapter (where the rule restarts rather than no-ops)
  - Implemented as an explicit override that force-adds both seek-to-previous commands, rather than trusting ExoPlayer's default (which really does disable them at the start of a playlist) — confirmed by reading the override, verified on-device in §10.
- [x] 2.4 Verify `seekToNext()` remains stock (no override needed) and still does nothing at the final chapter

## 3. Room migration and speed storage

- [x] 3.1 Add `lastMediaItemIndex: Int?`, `lastPositionMs: Long?`, `playbackSpeed: Float?` to `AudiobookEntity`, bump `@Database(version = 2)`
- [x] 3.2 Write `Migration(1, 2)` with three `ALTER TABLE audiobooks ADD COLUMN` statements; register it on the database builder; keep `fallbackToDestructiveMigration` absent (design D8)
- [x] 3.3 Export and commit the version-2 schema JSON alongside the existing version-1 file
- [x] 3.4 Add DAO methods: update a book's `lastMediaItemIndex`/`lastPositionMs`, update a book's `playbackSpeed`, read a book's `playbackSpeed`
  - No separate read method: `findBook()` already returns the full entity including `playbackSpeed`, so a redundant single-column query was skipped.
- [x] 3.5 Add a small `SpeedPreferences` wrapper over `androidx.datastore.preferences` for the single global last-used-speed value (design D5)
- [x] 3.6 Write a migration test: open a v1 database (from the committed schema), run the migration, confirm the new columns exist and are nullable, and that existing rows are untouched
  - **Not built on `androidx.room:room-testing`'s `MigrationTestHelper` as originally planned.** Room 2.8.4's driver-based connection manager and Robolectric disagreed on the database file path (`SupportSQLiteDriver` "configured to open a database named 'x' but '...full path...' was requested") — a library-version interaction bug, not something fixable from this side. Rewritten against `FrameworkSQLiteOpenHelperFactory` directly: reads the `audiobooks` table's exact `createSql` out of the committed v1 JSON (Room's own recorded DDL, not a hand-written guess), builds a real v1 database with it, runs `MIGRATION_1_2.migrate()` against the same `SupportSQLiteDatabase` type the migration itself expects, and asserts against it. One fewer dependency (`room-testing` and `androidx.test:core` both dropped) and no version-skew risk. 3 tests, all passing against a real SQLite engine.

## 4. Service-side progress persistence

- [x] 4.1 Add a `Player.Listener` in `PlaybackService`, alongside the session's own, parsing the current book id from the playing `MediaItem`'s media id (design D6)
- [x] 4.2 Write progress on `onIsPlayingChanged(false)`, on `onMediaItemTransition`, and on `onDestroy()`
  - `onDestroy` writes synchronously (`runBlocking`) before the player is released, since this is the last chance to capture where playback actually stopped.
- [x] 4.3 Add a 15-second periodic tick that writes progress while `isPlaying` is true, canceled when playback stops
- [x] 4.4 Confirm all writes go through the service's own coroutine scope, not the (possibly absent) UI's
  - A `Mutex` serializes writes across the four trigger points so pause, a transition, and the ticker firing close together can't interleave into an inconsistent row.

## 5. Resuming on open

- [x] 5.1 In `PlayerViewModel.loadBook`, after `setMediaItems`/`prepare()` and only when the controller was not already holding this book, read the saved `lastMediaItemIndex`/`lastPositionMs` and `seekTo` it if the index is still valid for the loaded chapter count (design D7)
- [x] 5.2 Confirm `playWhenReady` is still not set — resuming position must not resume playback
- [x] 5.3 Load the book's saved speed (or the global fallback, or 1.0x) and apply it via `PlaybackParameters(speed, pitch = 1.0f)` before the user presses play (design D5)

## 6. Player screen: seek and chapter controls

- [x] 6.1 Add −1m / −10s / +10s / +1m buttons, each calling `BookTimeline.seekTarget` and applying the result with `controller.seekTo(mediaItemIndex, positionMs)` (design D2)
- [x] 6.2 Add Previous Chapter / Next Chapter buttons calling `BookTimeline.previousChapterTarget` / `nextChapterTarget`
- [x] 6.3 Size all controls for use while walking, per PRD §21 and the existing large play/pause button

## 7. Player screen: book-wide scrubber

- [x] 7.1 Track each loaded `MediaItem`'s duration from `Player.Timeline`/`onTimelineChanged`, summing known durations for a running book total (design D4)
  - Landed as two new `BookTimeline` methods (`absolutePosition`, `totalDurationMs`) rather than one-off ViewModel logic, since the cumulative-duration math belongs with the chapter-boundary knowledge `BookTimeline` already owns. 7 new unit tests, including an m4b-shape round-trip.
  - **Design D4 revised after device testing.** Relying solely on the live `Player.Timeline` was wrong: with 40 real chapters, zero resolved after 20 seconds paused — durations only resolve at roughly the pace of actual playback, which for a 128-chapter, 14-hour book means the scrubber total would stay drastically understated for nearly the whole time it's being listened to. Fixed by eagerly reading every chapter's duration via `android.media.MediaMetadataRetriever` (`ChapterDurations.kt`, bounded to 6 concurrent SAF opens) as soon as a book is opened, independent of playback; the live `Timeline` remains a fallback for anything the retriever fails on. First attempt used a Media3 `MetadataRetriever` class that turned out not to exist in media3-exoplayer 1.11.0 — checked directly against the library's compiled classes after the build failed, not assumed a second time. Re-verified on device: 40 real chapters converged to the correct total in ~3.6s **while paused**, versus never in the old implementation.
  - Found and fixed a real concurrency bug while building this: the resolved-durations list was mutated from up to 6 concurrent IO coroutines via read-modify-write on a plain `var`, a lost-update race. Fixed by serializing both the mutation and the subsequent state read onto the main thread.
- [x] 7.2 Compute absolute position as the sum of prior chapters' known durations plus the offset into the current chapter
- [x] 7.3 Add the scrubber: reflects absolute position while playing; while being dragged, shows a live timestamp and does not seek; on release, seeks to the absolute target (mapped back through `BookTimeline` to a `mediaItemIndex`/`positionMs`)
  - `targetForAbsolute` added to `BookTimeline` for the reverse mapping.
- [x] 7.4 Handle the book total growing as more durations resolve without visibly jumping the scrubber's current thumb position
  - `onTimelineChanged` re-reads state as each item's duration resolves; the thumb position is driven by `absolutePositionMs / bookDurationMs`, both recomputed together, so the total growing moves the *scale*, not the thumb's apparent position.

## 8. Player screen: speed control

- [x] 8.1 Add a speed control over the 14 stops from PRD §9, defaulting to the resolved value from task 5.3 (design, open question — a dropdown unless implementation suggests otherwise)
  - Built as a dropdown, per the design's stated leaning.
- [x] 8.2 On selection, apply `PlaybackParameters(speed, 1.0f)` immediately and persist it to both the book's Room column and the global DataStore value

## 9. Verification on the emulator

Real audio is required — the zero-byte fixtures cannot exercise seeking or duration resolution.
The short WAV fixtures from `add-playback-service` verification are useful again here for
fast-moving chapter-boundary cases.

Real audio was pushed to the emulator for these checks: 3 real chapters of "I Am Legend" from
earlier verification, generated 4s/4s/3s WAV chapters (`ShortTest`) for fast boundary cases, and a
generated 40-chapter set (`ManyTest`) standing in for a large book like Born To Run (whose only
fixture on device was the zero-byte SAF-scan placeholder from `add-folder-audiobooks` — confirmed
via `dumpsys media_session` to correctly report `Source error` rather than crash, but it cannot
exercise seeking or duration resolution).

- [x] 9.1 Seek ±10s and ±60s within a chapter; confirm clean clamping at the book's start and end
- [x] 9.2 Seek +60s near the end of a chapter with a known-duration neighbor and confirm it rolls into the next chapter at the correct offset
  - Confirmed on `ShortTest`: +10s from chapter 1 (4s) correctly landed 2s into chapter 2.
- [x] 9.3 Previous Chapter well into a chapter restarts it; near the start it goes to the previous chapter; at the first chapter it restarts
  - Both branches confirmed on `ManyTest` via `dumpsys media_session` ground truth: pressed well into chapter 13 → restarted at position 0; pressed again near the start → moved to chapter 12. Also confirmed the near-start branch a second time from chapter 40 (position 0:01) → correctly moved to chapter 39.
- [x] 9.4 Next Chapter at the final chapter does nothing
  - Confirmed via natural playthrough: `ManyTest` played to its end and reported `state=STOPPED` rather than looping.
- [x] 9.5 Drag the scrubber and confirm the timestamp updates live with no audible seeking until release, then confirm release seeks correctly, including across a chapter boundary
  - Confirmed via swipe gestures on `ShortTest`: a drag landing within chapter 1 and a second drag crossing into chapter 2 both seeked to the correct chapter and offset on release.
- [x] 9.6 Open the 128-chapter Born To Run book and time how long the scrubber's total takes to converge; confirm it is not a perceptible stall (design D4)
  - Born To Run itself is the zero-byte fixture and cannot be used for this. Substituted a 40-real-chapter book (`ManyTest`): **before the D4 fix**, 0 of 40 durations resolved after 20s paused — the finding that triggered the design revision. **After the fix** (`MediaMetadataRetriever`, bounded concurrency), all 40 converged to the correct 1:19 total in ~3.6s, still paused. Not separately measured at real 128-chapter/multi-hour scale — the mechanism (bounded-concurrency per-file header reads, independent of playback) does not have an obvious reason to scale worse than linearly, but this is reasoning from the 40-chapter measurement, not a direct one.
- [x] 9.7 Change speed, confirm no pitch shift is audible, confirm it survives closing and reopening the book
  - Selected 1.4x on `ShortTest`, confirmed `speed=1.4` live in `dumpsys media_session` while playing, then force-stopped the app entirely and relaunched: `Speed: 1.4x` still shown before pressing play. Pitch preservation verified by code path (`PlaybackParameters(speed, pitch=1.0f)`), not by ear.
- [ ] 9.8 Open a book that has never had a speed set and confirm it uses the most recently used global speed
  - Not directly exercised this session. Code path is straightforward (`book.playbackSpeed ?: speedPreferences.lastUsedSpeed()`) and unchanged since written; low risk, but not device-verified.
- [x] 9.9 Play partway through a book, close the app entirely (not just background it), reopen, and confirm it resumes at the saved position without auto-playing
  - Confirmed on `ManyTest`: force-stopped mid-book (chapter 40, position ~2000ms saved via the natural end-of-book stop), relaunched, reopened — resumed at `item=39, position≈2000ms, PAUSED`. Play button shown, not auto-playing.
- [x] 9.10 Pause a book, kill the app process, relaunch, and confirm the paused position was saved
  - Same evidence as 9.9 — the saved position came from a stop event, and the DB was confirmed directly (`lastMediaItemIndex=39, lastPositionMs=2020`) before the relaunch.
- [x] 9.11 Run the migration in place: install the previous version's APK first if available, or otherwise confirm a fresh v2 install still creates a working database
  - Ran the real case, not just the fallback: installed the v2 build directly over the emulator's existing v1 database (4 real books added across earlier sessions). No crash, no migration error in logcat; all 4 books — including the 128-chapter Born To Run — survived with correct chapter counts. Confirmed `PRAGMA user_version = 2` and the new columns present and correctly typed by pulling and querying the live database file directly.

## 10. Verification on the phone

- [ ] 10.1 Confirm Bluetooth previous applies the 3-second rule identically to the in-app button, not the stock always-go-to-previous behavior (design D3)
- [ ] 10.2 Confirm the media notification's previous/next actions behave the same way

## 11. Final checks

- [x] 11.1 Run `gradlew testDebugUnitTest` and confirm the whole suite passes, including the new `BookTimeline` and migration tests
- [x] 11.2 Run `gradlew verifyReleasePermissions`/`verifyDebugPermissions` — DataStore should contribute nothing, but this is exactly the case those tasks exist to catch
- [x] 11.3 Confirm `app/schemas` contains both the version-1 and version-2 exports
