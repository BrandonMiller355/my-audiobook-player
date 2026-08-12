## 1. Build and manifest

- [x] 1.1 Wire `media3-exoplayer` and `media3-session` into `:app` from the existing catalog entries (1.11.0)
- [x] 1.2 Add `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `POST_NOTIFICATIONS` to the manifest, with a comment naming why each is needed — this ends the app's zero-permission run and should not pass unnoticed
- [x] 1.3 Declare the service with `foregroundServiceType="mediaPlayback"`, `exported="true"`, and the `MediaSessionService` intent filter (design D4)
- [x] 1.4 Confirm `gradlew assembleDebug` succeeds and the merged manifest gained exactly those three permissions and nothing else
  - **It did not.** Media3 contributed `ACCESS_NETWORK_STATE` and `WAKE_LOCK` during manifest merging — the guardrail firing on its first real test. Raised rather than resolved quietly, per `config.yaml`. Resolution: `WAKE_LOCK` is kept and documented (audio with the screen off is the app's job); `ACCESS_NETWORK_STATE` is stripped with `tools:node="remove"`, since ExoPlayer only wants it for adaptive streaming this app never does.
- [x] 1.5 Add a `verify<Variant>Permissions` Gradle task that fails the build if a forbidden permission reaches the merged manifest, wired into `assemble`, so an upgrade cannot reintroduce one silently
  - Proved it bites: temporarily injecting `INTERNET` failed the build naming the permission; removing it restored a clean build.

## 2. The playback service

- [x] 2.1 Write `PlaybackService : MediaSessionService` owning one `ExoPlayer` and one `MediaSession`, released properly in `onDestroy`
- [x] 2.2 Configure the player: `AudioAttributes` with `USAGE_MEDIA` and `AUDIO_CONTENT_TYPE_SPEECH`, `handleAudioFocus = true`, and `setHandleAudioBecomingNoisy(true)` (design D3)
- [x] 2.3 Handle `onTaskRemoved` so swiping the app away when paused does not leave an orphaned foreground service
- [x] 2.4 Confirm Media3's default media notification appears, without hand-rolling one (design D6)

## 3. Connecting the UI

- [x] 3.1 Write a Player ViewModel that connects a `MediaController` on start and releases it on stop, exposing connection state so the screen can render a neutral state while connecting (design D1)
- [x] 3.2 Load the book's chapters from Room and set them as the controller's media items, skipping the reset if the controller already holds this book so reopening the Player does not restart it (design D2)
- [x] 3.3 Expose play/pause state, current chapter index and title, position, and duration as Compose state driven by `Player.Listener`
- [x] 3.4 Prepare on open but do not auto-play (design D8)
  - **Bug found on device and fixed.** Opening a book while a *different* book was playing inherited `playWhenReady = true` and started the new book by itself. Now cleared before setting the playlist. Re-verified: switching books lands in `PAUSED, position=0`.

## 4. Player screen

- [x] 4.1 Replace the placeholder with the book title, current chapter title, and a chapter position indicator
- [x] 4.2 Add a large play/pause control sized for use while walking (PRD §21) — no seek, chapter, or speed controls yet
- [x] 4.3 Show elapsed and remaining time for what is currently playing, updating while it plays
- [x] 4.4 Keep the back control working, returning to the Library without stopping playback

## 5. Notification permission

- [x] 5.1 Request `POST_NOTIFICATIONS` when the user first starts playback, never at launch, so `app-shell`'s no-prompt-on-first-launch promise still holds (design D5)
- [x] 5.2 Ensure playback works normally when the permission is denied — no crash, no blocked playback, only the missing notification

## 6. Real audio for verification

The library change's device fixtures are **zero-byte files** and cannot decode. Nothing about
playback can be verified against them.

- [x] 6.1 Push a small folder of real audio to the device — a few chapters of `I Am Legend - AudioBook - MP3` is enough — and add it through the picker
- [x] 6.2 Confirm the zero-byte fixtures fail gracefully rather than crashing, since the owner may still have those test books in the library
  - Playing the 128-chapter zero-byte book raised `ExoPlaybackException: Source error`, which the listener turned into a message. No `FATAL EXCEPTION`, process still alive.

## 7. Verification on the emulator

Evidence: real audio decoded (a 27:36 chapter read its true duration from the mp3), the foreground
service reported `isForeground=true types=0x00000002` (mediaPlayback), and playback continued at
70s with the screen off and 79s with the Activity destroyed. Auto-advance and end-of-book were
verified with generated 4s/4s/3s WAV chapters rather than waiting out a 27-minute file.

- [x] 7.1 Play a real book, confirm audio actually decodes and the position advances
- [x] 7.2 Confirm automatic advance from one chapter to the next at the end of a file
- [x] 7.3 Background the app and confirm playback continues; return and confirm the Player shows the real position, not a stale one
- [x] 7.4 Turn the screen off during playback and confirm audio continues
- [x] 7.5 Confirm the media notification appears and its pause action works
- [x] 7.6 Destroy the Activity while playing (rotate or use "don't keep activities") and confirm audio continues and reconnecting shows correct state
- [x] 7.7 Confirm playback stops at the end of the final chapter rather than looping
- [x] 7.8 Deny the notification permission and confirm audio still plays (design D5)

## 8. Verification on the phone

**These need real hardware and are the owner's to run.** Bluetooth behavior, lock-screen controls,
and a genuine phone call cannot be judged from an emulator, and PRD §14 and §15 are mostly about
exactly those.

- [ ] 8.1 Confirm lock-screen controls appear and operate playback
- [ ] 8.2 Confirm Bluetooth play/pause works from a headset or car stereo
- [ ] 8.3 Confirm Bluetooth previous/next moves between chapters of a folder book (design D7)
- [ ] 8.4 Take a phone call during playback and confirm it pauses and does not play over the call
- [ ] 8.5 Start another media app and confirm this app pauses
- [ ] 8.6 Unplug headphones or disconnect Bluetooth during playback and confirm it pauses rather than playing aloud
- [ ] 8.7 Re-run the folder-audiobook checks against a real book on the phone, since a different vendor's document provider is the one thing the emulator cannot stand in for

## 9. Final checks

- [x] 9.1 Run `gradlew testDebugUnitTest` and confirm the whole suite still passes
- [x] 9.2 Re-run the merged-manifest check: still no `INTERNET`, no `ACCESS_NETWORK_STATE`, and no storage permission, with only the three media-playback permissions present
- [x] 9.3 Confirm the README's verification command still reflects reality now that permissions exist, and update its wording if it implies the app has none
