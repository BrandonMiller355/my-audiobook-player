## 1. The media-button callback

- [ ] 1.1 Add a `MediaSession.Callback` to `PlaybackService` and pass it to `MediaSession.Builder`,
      initially overriding `onMediaButtonEvent` to return `false` for everything — a no-op that
      proves the callback is wired without changing any behavior (design D1)
- [ ] 1.2 Build and run: confirm the notification's previous/next buttons still navigate chapters
      and the Player screen is unaffected
- [ ] 1.3 Return `false` immediately when `session.isMediaNotificationController(controllerInfo)`
      is true, so the notification and lock screen keep falling through to `ChapterAwarePlayer`
      (design D2)
- [ ] 1.4 Read the `KeyEvent` from the intent's `Intent.EXTRA_KEY_EVENT`; return `false` when it is
      absent rather than assuming one is present

## 2. The two gestures

- [ ] 2.1 Add `BLUETOOTH_DOUBLE_PRESS_SEEK_MS = -60_000L` and
      `BLUETOOTH_TRIPLE_PRESS_SEEK_MS = -180_000L` as private constants in `PlaybackService`,
      named for the gesture rather than the interval, and not shared with `PlayerScreen`'s
      `SEEK_LONG_MS` (design D6)
- [ ] 2.2 Map `KEYCODE_MEDIA_NEXT` to the one-minute rewind and `KEYCODE_MEDIA_PREVIOUS` to the
      three-minute rewind; return `false` for every other key code so play/pause and everything
      else keep their current handling
- [ ] 2.3 Act only on `ACTION_DOWN` with `repeatCount == 0`, and return `true` for the matching
      `ACTION_UP` as well, so one press produces exactly one seek and a held button does not walk
      backward through the book (design, Risks)
- [ ] 2.4 Perform the seek by building a `PlayerTarget` from `BookTimeline.seekTarget(location,
      delta)` via the existing `chapterTimeline()`/`currentLocation()` helpers, then
      `player.seekTo(target.mediaItemIndex, target.positionMs)` — no new seek math
- [ ] 2.5 Confirm the callback runs on the main thread as Media3 documents, so the player reads in
      2.4 are legal; do not add a dispatcher hop that would let a press race the player's state
- [ ] 2.6 Leave `ChapterAwarePlayer` untouched, including its `getAvailableCommands()` override —
      removing it would stop the platform session advertising the next-track action and the
      gestures would never arrive (design D5)

## 3. Tests

- [ ] 3.1 Unit-test the key-code-to-delta mapping as a pure function, separate from the session
      plumbing: next maps to −60s, previous to −180s, everything else to no mapping
- [ ] 3.2 Unit-test that only `ACTION_DOWN` with `repeatCount == 0` produces a seek, while
      `ACTION_UP` and repeats are consumed without one
- [ ] 3.3 Extend `BookTimelineTest` if a −180s case is not already covered: a rewind crossing one
      chapter boundary, a rewind clamping at the start of the book, and a rewind on a
      single-chapter book landing at position minus the interval rather than at zero
- [ ] 3.4 Do not add a test that only exists to reach the callback's session plumbing — keep the
      testable part a pure function and let the wiring be verified on device (§4)
- [ ] 3.5 Run the full unit test suite

## 4. Device verification

Task 4.1 tests the assumption this whole change rests on (proposal, Assumptions; design D7). It
comes first because a negative result invalidates §1 and §2 rather than merely failing them.

- [ ] 4.1 With the OpenMove connected, watch `adb logcat` while double-pressing and
      triple-pressing the side button, and record which key codes actually arrive. Expected
      `KEYCODE_MEDIA_NEXT` and `KEYCODE_MEDIA_PREVIOUS`. If the log instead shows repeated
      `KEYCODE_MEDIA_PLAY_PAUSE`, stop: this change cannot work as designed and D7's alternative
      needs its own proposal
- [ ] 4.2 Double-press well into a Mistborn `.m4b`: playback resumes one minute earlier, in the
      same book, and does not restart the file
- [ ] 4.3 Triple-press well into the same book: playback resumes three minutes earlier
- [ ] 4.4 Double-press within one minute of the start of a book: playback clamps to the start and
      does not error
- [ ] 4.5 Both gestures on a folder book: the rewind crosses into the preceding chapter when its
      duration is known, and clamps at the current chapter's start when it is not
- [ ] 4.6 Play and pause from the headset still work, and single-press play/pause has not become
      slower or less reliable
- [ ] 4.7 Regression: the media notification's previous and next buttons still navigate chapters,
      including the 3-second rule on previous
- [ ] 4.8 Regression: the Player screen's previous/next chapter buttons and its ±10s/±60s buttons
      all behave exactly as before
- [ ] 4.9 Pause after a headset rewind, reopen the book, and confirm the resumed position is the
      rewound one — the gesture must persist through the normal progress write, not bypass it

## 5. Documentation

- [ ] 5.1 Amend PRD §7.4's "map previous/next media actions to chapter navigation" line to say
      that this holds for the notification and lock screen, and that Bluetooth next/previous are
      fixed backward seeks, with the one-line reason
- [ ] 5.2 Correct `ChapterAwarePlayer`'s class comment, which claims it serves "the in-app button,
      the notification, or a Bluetooth remote". After this change it serves the notification and
      lock screen only; the in-app buttons never reached it (design, Context)
- [ ] 5.3 Record in `README.md` what the two headset gestures do, since it is the one behavior in
      the app that cannot be discovered from the screen
