## 1. BookTimeline (pure logic, no Android dependency)

- [ ] 1.1 Define `ChapterBounds` and `Location`/`PlayerTarget` types (design D1)
- [ ] 1.2 Implement `locate(mediaItemIndex, positionMs)` mapping player coordinates to `(chapterIndex, offsetMs)`
- [ ] 1.3 Implement `seekTarget(from, deltaMs)`: clamps at the book's start and end, rolls into the neighboring chapter when its boundary is known, clamps to the chapter end when it is not (design D1, D2)
- [ ] 1.4 Implement `previousChapterTarget(from, toleranceMs = 3000)`: restarts the current chapter past the tolerance, otherwise targets the previous chapter; restarts at the first chapter rather than no-op (design D3)
- [ ] 1.5 Implement `nextChapterTarget(from)`: targets the next chapter's start, or `null` at the final chapter
- [ ] 1.6 Unit-test the **folder shape** (bounds where `chapterIndex == mediaItemIndex`) against cases mirroring Born To Run: mid-book seek, cross-boundary forward seek, clamp at book start/end, 3-second rule on both sides of the threshold, next-chapter no-op at the end
- [ ] 1.7 Unit-test the **`.m4b` shape** (bounds sharing one `mediaItemIndex` with sub-range `startInItemMs`/`endInItemMs`) with the same scenarios, even though nothing in the app produces this shape yet
- [ ] 1.8 Unit-test the unknown-duration case: `seekTarget` clamps to the chapter end rather than rolling over when `endInItemMs` is null

## 2. ForwardingPlayer

- [ ] 2.1 Implement a `ForwardingPlayer` wrapping the service's `ExoPlayer`, overriding `seekToPrevious()`/`seekToPreviousMediaItem()` to compute its target via `BookTimeline` instead of the default (design D3)
- [ ] 2.2 Give `MediaSession.Builder` the forwarding player rather than the raw `ExoPlayer`, so session commands — including Bluetooth — go through it
- [ ] 2.3 Confirm `getAvailableCommands()` still reports `COMMAND_SEEK_TO_PREVIOUS` correctly at every position, including the first chapter (where the rule restarts rather than no-ops)
- [ ] 2.4 Verify `seekToNext()` remains stock (no override needed) and still does nothing at the final chapter

## 3. Room migration and speed storage

- [ ] 3.1 Add `lastMediaItemIndex: Int?`, `lastPositionMs: Long?`, `playbackSpeed: Float?` to `AudiobookEntity`, bump `@Database(version = 2)`
- [ ] 3.2 Write `Migration(1, 2)` with three `ALTER TABLE audiobooks ADD COLUMN` statements; register it on the database builder; keep `fallbackToDestructiveMigration` absent (design D8)
- [ ] 3.3 Export and commit the version-2 schema JSON alongside the existing version-1 file
- [ ] 3.4 Add DAO methods: update a book's `lastMediaItemIndex`/`lastPositionMs`, update a book's `playbackSpeed`, read a book's `playbackSpeed`
- [ ] 3.5 Add a small `SpeedPreferences` wrapper over `androidx.datastore.preferences` for the single global last-used-speed value (design D5)
- [ ] 3.6 Write a migration test: open a v1 database (from the committed schema), run the migration, confirm the new columns exist and are nullable, and that existing rows are untouched

## 4. Service-side progress persistence

- [ ] 4.1 Add a `Player.Listener` in `PlaybackService`, alongside the session's own, parsing the current book id from the playing `MediaItem`'s media id (design D6)
- [ ] 4.2 Write progress on `onIsPlayingChanged(false)`, on `onMediaItemTransition`, and on `onDestroy()`
- [ ] 4.3 Add a 15-second periodic tick that writes progress while `isPlaying` is true, canceled when playback stops
- [ ] 4.4 Confirm all writes go through the service's own coroutine scope, not the (possibly absent) UI's

## 5. Resuming on open

- [ ] 5.1 In `PlayerViewModel.loadBook`, after `setMediaItems`/`prepare()` and only when the controller was not already holding this book, read the saved `lastMediaItemIndex`/`lastPositionMs` and `seekTo` it if the index is still valid for the loaded chapter count (design D7)
- [ ] 5.2 Confirm `playWhenReady` is still not set — resuming position must not resume playback
- [ ] 5.3 Load the book's saved speed (or the global fallback, or 1.0x) and apply it via `PlaybackParameters(speed, pitch = 1.0f)` before the user presses play (design D5)

## 6. Player screen: seek and chapter controls

- [ ] 6.1 Add −1m / −10s / +10s / +1m buttons, each calling `BookTimeline.seekTarget` and applying the result with `controller.seekTo(mediaItemIndex, positionMs)` (design D2)
- [ ] 6.2 Add Previous Chapter / Next Chapter buttons calling `BookTimeline.previousChapterTarget` / `nextChapterTarget`
- [ ] 6.3 Size all controls for use while walking, per PRD §21 and the existing large play/pause button

## 7. Player screen: book-wide scrubber

- [ ] 7.1 Track each loaded `MediaItem`'s duration from `Player.Timeline`/`onTimelineChanged`, summing known durations for a running book total (design D4)
- [ ] 7.2 Compute absolute position as the sum of prior chapters' known durations plus the offset into the current chapter
- [ ] 7.3 Add the scrubber: reflects absolute position while playing; while being dragged, shows a live timestamp and does not seek; on release, seeks to the absolute target (mapped back through `BookTimeline` to a `mediaItemIndex`/`positionMs`)
- [ ] 7.4 Handle the book total growing as more durations resolve without visibly jumping the scrubber's current thumb position

## 8. Player screen: speed control

- [ ] 8.1 Add a speed control over the 14 stops from PRD §9, defaulting to the resolved value from task 5.3 (design, open question — a dropdown unless implementation suggests otherwise)
- [ ] 8.2 On selection, apply `PlaybackParameters(speed, 1.0f)` immediately and persist it to both the book's Room column and the global DataStore value

## 9. Verification on the emulator

Real audio is required — the zero-byte fixtures cannot exercise seeking or duration resolution.
The short WAV fixtures from `add-playback-service` verification are useful again here for
fast-moving chapter-boundary cases.

- [ ] 9.1 Seek ±10s and ±60s within a chapter; confirm clean clamping at the book's start and end
- [ ] 9.2 Seek +60s near the end of a chapter with a known-duration neighbor and confirm it rolls into the next chapter at the correct offset
- [ ] 9.3 Previous Chapter well into a chapter restarts it; near the start it goes to the previous chapter; at the first chapter it restarts
- [ ] 9.4 Next Chapter at the final chapter does nothing
- [ ] 9.5 Drag the scrubber and confirm the timestamp updates live with no audible seeking until release, then confirm release seeks correctly, including across a chapter boundary
- [ ] 9.6 Open the 128-chapter Born To Run book and time how long the scrubber's total takes to converge; confirm it is not a perceptible stall (design D4)
- [ ] 9.7 Change speed, confirm no pitch shift is audible, confirm it survives closing and reopening the book
- [ ] 9.8 Open a book that has never had a speed set and confirm it uses the most recently used global speed
- [ ] 9.9 Play partway through a book, close the app entirely (not just background it), reopen, and confirm it resumes at the saved position without auto-playing
- [ ] 9.10 Pause a book, kill the app process, relaunch, and confirm the paused position was saved
- [ ] 9.11 Run the migration in place: install the previous version's APK first if available, or otherwise confirm a fresh v2 install still creates a working database

## 10. Verification on the phone

- [ ] 10.1 Confirm Bluetooth previous applies the 3-second rule identically to the in-app button, not the stock always-go-to-previous behavior (design D3)
- [ ] 10.2 Confirm the media notification's previous/next actions behave the same way

## 11. Final checks

- [ ] 11.1 Run `gradlew testDebugUnitTest` and confirm the whole suite passes, including the new `BookTimeline` and migration tests
- [ ] 11.2 Run `gradlew verifyReleasePermissions`/`verifyDebugPermissions` — DataStore should contribute nothing, but this is exactly the case those tasks exist to catch
- [ ] 11.3 Confirm `app/schemas` contains both the version-1 and version-2 exports
