## 1. Schema

- [x] 1.1 Add nullable `endPositionMs` to `ChapterEntity` and nullable `artworkPath` to `AudiobookEntity` (design D2, D7)
- [x] 1.2 Add `SOURCE_TYPE_M4B` alongside the existing `SOURCE_TYPE_FOLDER` constant
- [x] 1.3 Write `MIGRATION_2_3` adding both columns, register it, bump the database to version 3, and export schema 3
- [x] 1.4 Extend the Robolectric migration test: a v2 database holding a folder book with chapters opens at v3 with every row intact and both new columns null
- [x] 1.5 Build and run — the app must behave exactly as before, with existing books untouched

## 2. Reading an `.m4b`

- [x] 2.1 Add `M4bReader` (or equivalent) in `library/`: open a `FileChannel` over a content URI via `openFileDescriptor` and run `M4bChapterParser` (design D8)
- [x] 2.2 Map the three parse outcomes to a single result type carrying chapters, the file duration, and whether chapters were unreadable — `Unchaptered` and `Unreadable` both yield one whole-file chapter, differing only in the message (design D5)
- [x] 2.3 Read the file's duration for the single-chapter case, since the parser reports no chapters to derive it from
- [x] 2.4 Read the display name and derive the book title as the file name without its extension
- [x] 2.5 Extract embedded artwork with `MediaMetadataRetriever.embeddedPicture`, downsample to a 512 px maximum edge, and write it to `filesDir/covers/<bookId>.jpg`; a missing or undecodable image is not an error (design D7)
- [x] 2.6 Unit-test the outcome mapping against synthetic parse results: chaptered, unchaptered, unreadable, and a non-seekable channel

## 3. Adding an `.m4b` from the picker

- [x] 3.1 Add an `OpenDocument` subclass that adds the read and persistable grant flags to its intent (design D3)
- [x] 3.2 Add `addM4bFile(uri)` to `LibraryViewModel`: take the grant, read off the main thread, store the book and chapters in one transaction, then write the cover file
- [x] 3.3 Reject a file whose name does not end in `.m4b`, release its grant, and say what was picked (design D4)
- [x] 3.4 Surface the "chapters could not be read" message without blocking the add
- [x] 3.5 Replace the single add button with a two-way choice — add a folder, or add a single file — and wire the second to the new picker
- [x] 3.6 Build and run: add one of the Mistborn `.m4b` files, confirm it appears in the library with a chapter count of 1, and that adding a folder still works
      — **run against a synthetic chaptered `.m4b` on an emulator, not a Mistborn file.** It was
      added with its 3 embedded chapters, alongside the 5 existing folder books. The unchaptered
      path — which is what every Mistborn file takes — has not been run on a device; see §10.

## 4. Playing it as one media item

- [x] 4.1 **Verify design D1's premise before building on it:** set a playlist whose `MediaMetadata` carries an extras `Bundle`, then read that bundle back off `mediaSession.player`'s current item inside `PlaybackService`. If the extras do not survive the controller-to-session boundary, stop and switch to D1's fallback (service reads chapters from Room, pre-warmed on media-item transition) before continuing
      — **verified; the premise holds and D1 stands.** The risk was serialization, so
      `MediaMetadataExtrasTest` round-trips a `MediaItem` through the bundling the boundary
      performs, including a 10,000-entry array. Confirmed again at runtime afterwards: with the app
      backgrounded and no Activity in the foreground, a media-button "next" moved between embedded
      chapters, which only works if the bounds reached `ChapterAwarePlayer` inside the service.
- [x] 4.2 Make `mediaItemsFor` produce one `MediaItem` for an `.m4b` book, carrying the chapter start array and the book duration in its metadata extras (design D1)
- [x] 4.3 Give `chapterTimeline()` its one new branch: build `ChapterBounds` from the extras when present, chaining each end from the next start and the last from the book duration; otherwise derive per media item as today
- [x] 4.4 Unit-test that branch — bounds built from a starts array match bounds built from an equivalent folder playlist for every `BookTimeline` operation
      — the two shapes agree everywhere except at an exact chapter boundary, where a folder book
      names the instant as the end of chapter N and a single-file book as the start of N+1. Both
      report the same book-wide position, which is what every user-visible operation goes through,
      so the difference is recorded as its own test rather than papered over.
- [x] 4.5 Build and run: play an `.m4b`, confirm audio starts, the scrubber spans the full file duration immediately, and ±10s/±60s seek works
      — the scrubber read the full 3:00 the moment the book opened, before any playback.

## 5. Chapter navigation from every control surface

- [x] 5.1 Override `seekToNext` and `seekToNextMediaItem` in `ChapterAwarePlayer` using `nextChapterTarget`, and add `COMMAND_SEEK_TO_NEXT` to the always-available commands (design D1)
- [x] 5.2 Confirm the folder-book path is unchanged by this: next chapter still moves to the next file, and auto-advance at a chapter's end still works
      — next walked items 0 → 1 → 2, each landing at position 0, and playback crossed a file
      boundary on its own with the displayed chapter following it.
- [x] 5.3 Confirm previous/next reach the wrapped player identically from the in-app buttons, the notification, and a Bluetooth device
      — **no Bluetooth device was paired.** `KEYCODE_MEDIA_NEXT`/`PREVIOUS` were used instead,
      which reach the session by the same media-button route an AVRCP command does, and were sent
      with the app backgrounded. The notification was confirmed to publish three transport actions,
      so its next button exists on a single-item book; its buttons were not pressed. Pressing
      next/previous on real Bluetooth hardware is left for the owner.

## 6. Chapter identity in the Player

- [x] 6.1 Hold the loaded chapter list in `PlayerViewModel` and read the chapter title and number from it at `location.chapterIndex`, replacing the read from `currentMediaItem.mediaMetadata.title` (design D6)
- [x] 6.2 Confirm a folder book still shows the same chapter titles it did before the change
      — a folder book showed "02 second chapter" / "Chapter 2 of 3", the file-derived titles it
      showed before.
- [x] 6.3 Confirm the displayed chapter updates when playback crosses a boundary with no seek
      — checked for both shapes: a folder book crossing into the next file, and an `.m4b` running
      past an embedded boundary at 2:00 within the one media item.

## 7. Cover art

- [x] 7.1 Wire `coil-compose` into `:app` from the version catalog, and confirm no `INTERNET` permission is pulled in — the `app-shell` no-network check must still pass
- [x] 7.2 Expose `artworkPath` on the library row query and load it into the library list with a placeholder fallback
- [x] 7.3 Show the cover on the Player screen, with the same placeholder fallback
- [x] 7.4 Confirm a missing cover file falls back to the placeholder rather than erroring
      — the cached file was deleted from underneath the app; the row fell back to the placeholder
      with no error and no crash, and kept its title and chapter count.

## 8. Removal and unavailability

- [x] 8.1 Delete a book's cached cover file when the book is removed, since Room's cascade covers rows and not files
      — the covers directory was empty after removal, and re-adding the book cached a fresh one.
- [x] 8.2 Confirm the persisted grant is released for a document URI the same way it is for a tree URI
      — the document grant was listed as persisted while the book existed and gone after removal.
      Also checked on the rejection path: a picked `.wav` left no grant behind.
- [x] 8.3 Confirm a single-file book whose source is gone reports as unavailable and stays removable
      — deleting the file caused the system to drop the persisted grant; the row read "Source
      unavailable", the app did not crash, and the book removed normally.

## 9. Test pass

- [x] 9.1 Run the full unit suite; every existing test still passes alongside the new ones
      — 92 tests, all passing: the 71 that existed before, plus 21 new ones.
- [x] 9.2 Re-run the two permission checks: no storage permission, no `INTERNET` permission
      — `verifyDebugPermissions` passes with Coil wired in; the merged manifest declares only
      `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, and
      `WAKE_LOCK`.

## 10. On-device verification (PRD §25)

Fixtures: the owner's seven Mistborn `.m4b` files. No new fixture is required — every one of
them parses as unchaptered, which is the path these checks exercise. **The chaptered-`.m4b`
checks below are covered by unit tests only, by decision:** no real chaptered `.m4b` exists in
this library to verify against.

**What was actually run, and where it differs from the plan above.** These were run on an
emulator against a *synthetic three-chapter* `.m4b` — a 3-minute file generated with ffmpeg,
carrying embedded chapter marks and cover art — together with the five folder books already on
that device. That inverts the plan's expectation in a useful way and a limiting one:

- It exercised the **chaptered** path on a device, which the plan had written off as
  unit-test-only. Embedded chapters were discovered, their titles displayed, and navigation
  between them worked from every surface.
- It did **not** exercise the **unchaptered** path on a device, which is the path every one of
  the owner's Mistborn files takes, nor anything at their scale: 10–14 hours and several
  gigabytes against 3 minutes and 1 MB. Reading chapters and artwork out of a multi-gigabyte
  container through SAF, and the "1 chapter" library row that produces, remain unverified on
  real hardware.

**Still to be run by the owner, on their device, against a Mistborn file:** 10.1 through 10.9 as
written below, plus the real-Bluetooth half of 5.3 and 10.11.

- [x] 10.1 Add `.m4b` — the file is added and appears in the library
- [x] 10.2 Cover art displayed — in the library list and on the Player
- [x] 10.3 Play, pause, and resume
- [x] 10.4 +10s / −10s / +60s / −60s within the file
      — including a −60s that rolled backward across a chapter boundary and clamped at the book's
      start.
- [x] 10.5 Playback speed retained across reopening the book
- [x] 10.6 Background playback — screen off and app backgrounded, audio continues
      — verified both ways; with the device asleep, position advanced 3.5s → 15.0s.
- [x] 10.7 Resume restores the correct timestamp after the app process is killed
      — force-stopped at 0:41, reopened at 0:42, in the right chapter, not playing.
- [x] 10.8 Both book types coexist: a folder book and an `.m4b` in one library, each opening and playing correctly
- [x] 10.9 Removing the `.m4b` leaves the source file on disk, unmodified
      — same size and same MD5 as the file originally pushed.
- [x] 10.10 Existing folder books survive the migration with their saved progress and speed intact
      — the one check that ran against real pre-existing data: five folder books on a v2 database
      opened at v3 with their chapter counts, saved position, and per-book speed intact.
- [x] 10.11 Bluetooth previous/next on a folder book still behaves as it did (regression check for task 5.1)
      — by media button rather than by Bluetooth hardware; see 5.3.

Covered by unit tests rather than the device, and recorded as such: embedded chapters
discovered, chapter titles displayed, next/previous chapter within a file, seeking across an
embedded chapter boundary, resume restoring the correct embedded chapter.
