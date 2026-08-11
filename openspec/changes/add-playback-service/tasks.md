## 1. Build and manifest

- [ ] 1.1 Wire `media3-exoplayer` and `media3-session` into `:app` from the existing catalog entries (1.11.0)
- [ ] 1.2 Add `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `POST_NOTIFICATIONS` to the manifest, with a comment naming why each is needed — this ends the app's zero-permission run and should not pass unnoticed
- [ ] 1.3 Declare the service with `foregroundServiceType="mediaPlayback"`, `exported="true"`, and the `MediaSessionService` intent filter (design D4)
- [ ] 1.4 Confirm `gradlew assembleDebug` succeeds and the merged manifest gained exactly those three permissions and nothing else

## 2. The playback service

- [ ] 2.1 Write `PlaybackService : MediaSessionService` owning one `ExoPlayer` and one `MediaSession`, released properly in `onDestroy`
- [ ] 2.2 Configure the player: `AudioAttributes` with `USAGE_MEDIA` and `AUDIO_CONTENT_TYPE_SPEECH`, `handleAudioFocus = true`, and `setHandleAudioBecomingNoisy(true)` (design D3)
- [ ] 2.3 Handle `onTaskRemoved` so swiping the app away when paused does not leave an orphaned foreground service
- [ ] 2.4 Confirm Media3's default media notification appears, without hand-rolling one (design D6)

## 3. Connecting the UI

- [ ] 3.1 Write a Player ViewModel that connects a `MediaController` on start and releases it on stop, exposing connection state so the screen can render a neutral state while connecting (design D1)
- [ ] 3.2 Load the book's chapters from Room and set them as the controller's media items, skipping the reset if the controller already holds this book so reopening the Player does not restart it (design D2)
- [ ] 3.3 Expose play/pause state, current chapter index and title, position, and duration as Compose state driven by `Player.Listener`
- [ ] 3.4 Prepare on open but do not auto-play (design D8)

## 4. Player screen

- [ ] 4.1 Replace the placeholder with the book title, current chapter title, and a chapter position indicator
- [ ] 4.2 Add a large play/pause control sized for use while walking (PRD §21) — no seek, chapter, or speed controls yet
- [ ] 4.3 Show elapsed and remaining time for what is currently playing, updating while it plays
- [ ] 4.4 Keep the back control working, returning to the Library without stopping playback

## 5. Notification permission

- [ ] 5.1 Request `POST_NOTIFICATIONS` when the user first starts playback, never at launch, so `app-shell`'s no-prompt-on-first-launch promise still holds (design D5)
- [ ] 5.2 Ensure playback works normally when the permission is denied — no crash, no blocked playback, only the missing notification

## 6. Real audio for verification

The library change's device fixtures are **zero-byte files** and cannot decode. Nothing about
playback can be verified against them.

- [ ] 6.1 Push a small folder of real audio to the device — a few chapters of `I Am Legend - AudioBook - MP3` is enough — and add it through the picker
- [ ] 6.2 Confirm the zero-byte fixtures fail gracefully rather than crashing, since the owner may still have those test books in the library

## 7. Verification on the emulator

- [ ] 7.1 Play a real book, confirm audio actually decodes and the position advances
- [ ] 7.2 Confirm automatic advance from one chapter to the next at the end of a file
- [ ] 7.3 Background the app and confirm playback continues; return and confirm the Player shows the real position, not a stale one
- [ ] 7.4 Turn the screen off during playback and confirm audio continues
- [ ] 7.5 Confirm the media notification appears and its pause action works
- [ ] 7.6 Destroy the Activity while playing (rotate or use "don't keep activities") and confirm audio continues and reconnecting shows correct state
- [ ] 7.7 Confirm playback stops at the end of the final chapter rather than looping
- [ ] 7.8 Deny the notification permission and confirm audio still plays (design D5)

## 8. Verification on the phone

**These need real hardware and are the owner's to run.** Bluetooth behaviour, lock-screen controls,
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

- [ ] 9.1 Run `gradlew testDebugUnitTest` and confirm the whole suite still passes
- [ ] 9.2 Re-run the merged-manifest check: still no `INTERNET`, no `ACCESS_NETWORK_STATE`, and no storage permission, with only the three media-playback permissions present
- [ ] 9.3 Confirm the README's verification command still reflects reality now that permissions exist, and update its wording if it implies the app has none
