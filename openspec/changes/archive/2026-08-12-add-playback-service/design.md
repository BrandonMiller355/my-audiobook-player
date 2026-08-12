## Context

The app can build a library but owns no player. Everything downstream — seek, speed, progress,
`.m4b` chapters — hangs off whatever playback ownership model gets established here, which is why
this change comes before transport controls rather than after.

Two shapes were on the table:

```
Activity-owned (PRD Phase 3 order)        Service-owned (this change)
  Activity ──owns──▶ ExoPlayer              Activity ──MediaController──▶ MediaSessionService
      │                                                                       └─owns─▶ ExoPlayer
      └── dies with the UI                                                    └── outlives the UI
      ...then rewrite it all in Phase 4
```

Constraints inherited: Media3 for everything, no parallel abstraction over ExoPlayer
(`config.yaml`); audio must survive screen-off, backgrounding, and Activity destruction (PRD §13);
system, lock-screen, and Bluetooth controls must work (PRD §14); audio focus must behave (PRD §15).

What already exists to build on: `LibraryDao.chaptersFor(bookId)` returns ordered `ChapterEntity`
rows, each carrying a `mediaUri` the app holds a persisted read grant for.

## Goals / Non-Goals

**Goals:**

- Audio plays, and keeps playing with the screen off and the app backgrounded.
- The system notification, lock screen, and Bluetooth play/pause and track buttons all work.
- Audio focus is handled the way a person expects during a phone call or a notification chime.
- Playback ownership is settled once, so later changes add behavior rather than move it.

**Non-Goals:**

- Seek, speed, progress persistence, `.m4b`, artwork — see the proposal's non-goals.
- Any abstraction layer over `Player`. Later changes wrap it with a `ForwardingPlayer` where chapter
  semantics demand it; nothing else gets invented.

## Decisions

### D1: The service owns the player; the UI holds a `MediaController`

`PlaybackService : MediaSessionService` constructs the `ExoPlayer` and a `MediaSession`. The Player
screen connects a `MediaController` in `onStart` and releases it in `onStop`.

*Why:* it is the only arrangement where PRD §13's "Activity destroyed while playback continues" is
true by construction rather than by luck. It also means the notification, the lock screen, and
Bluetooth all talk to the same `Player` the UI does, so they cannot disagree.

*Consequence:* the UI can be showing stale or empty state for the moments before the controller
connects. The Player screen renders a neutral "connecting" state rather than a wrong one.

### D2: The UI supplies the playlist; the service does not read the database

When the user opens a book, the Player ViewModel loads that book's chapters from Room and calls
`setMediaItems` on the controller.

*Why:* it keeps the service to one job — owning a player — and avoids a second path into the
database with its own threading story. It is also less code for the same result today.

*Alternative considered:* a custom session command carrying a `bookId`, with the service loading
chapters itself. Genuinely better in one case — restoring playback after the whole process is killed
while the notification lives on — but that case also needs saved progress, which this change does
not have. **Flagged for the transport-controls change**: once progress is persisted, resumption
should move into the service and this decision should be revisited rather than inherited.

*Guard:* setting a playlist is idempotent per book — if the controller already holds this book's
items, do not reset them, or reopening the Player would restart playback from zero.

### D3: Audio focus through ExoPlayer's built-in handling, with speech attributes

```
AudioAttributes(usage = USAGE_MEDIA, contentType = AUDIO_CONTENT_TYPE_SPEECH)
setAudioAttributes(attrs, handleAudioFocus = true)
setHandleAudioBecomingNoisy(true)
```

*Why:* PRD §15 asks for standard behavior, and PRD §28 rule 3 prefers built-in APIs. Rolling focus
by hand is a classic source of "resumed during a phone call" bugs.

`AUDIO_CONTENT_TYPE_SPEECH` rather than `MUSIC` is deliberate: it tells the system this is spoken
word, which affects ducking decisions and, on some devices, processing. An audiobook ducked like
music becomes unintelligible rather than quieter.

`setHandleAudioBecomingNoisy` covers PRD §15's spirit for headphone unplug — audio should not
suddenly play out loud in public.

### D4: Foreground service type and the three permissions

Manifest gains:

```
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

<service android:name=".playback.PlaybackService"
         android:foregroundServiceType="mediaPlayback"
         android:exported="true">
  <intent-filter><action android:name="androidx.media3.session.MediaSessionService"/></intent-filter>
</service>
```

*Why exported:* `MediaSessionService` must be reachable by the system media stack and Bluetooth
clients; that is what makes lock-screen and car controls work. It exposes only the media session,
not app data.

*Note on the no-network guarantee:* none of these are network or storage permissions, so PRD §24
still holds. The `app-shell` requirement is being rewritten precisely so it says that, rather than
the now-false "no permissions at all".

### D5: `POST_NOTIFICATIONS` is requested at first playback, not at launch

*Why:* `app-shell` promises that first launch shows no permission dialog, and that is a promise
worth keeping — an audiobook app that asks for notification access before showing anything is the
kind of thing this PRD is a reaction against. Asking when the user first presses play ties the
prompt to something they just did.

*And if it is denied:* playback still works. The service still runs in the foreground; only the
notification is not displayed, which costs the lock-screen controls but not the audio. This is
treated as a supported state, not an error.

### D6: Media3's default notification, not a hand-rolled one

*Why:* `DefaultMediaNotificationProvider` already produces the correct media-style notification with
the right actions wired to the session. Hand-rolling it means reimplementing action dispatch for no
gain. Artwork will slot into the same notification when the metadata change provides it.

### D7: No `ForwardingPlayer` yet — for folder books, stock behavior *is* chapter navigation

PRD §14 requires previous/next media actions to move between chapters. In a folder book each chapter
is its own `MediaItem`, so ExoPlayer's own `seekToNext`/`seekToPrevious` already do exactly that,
including from a Bluetooth remote.

*Why this matters later:* an `.m4b` book is one `MediaItem`, where the same buttons would do nothing.
That is when the `ForwardingPlayer` and the `BookTimeline` mapping become necessary — along with the
agreed 3-second previous-chapter rule, which applies to both book types and therefore must live in
that shared layer rather than here.

### D8: Opening a book prepares but does not start playback

*Why:* PRD §6's first-launch flow ends with "User presses Play". Auto-playing on open is a
different product decision and not one the PRD makes.

## Risks / Trade-offs

**Service and controller lifecycle bugs only appear under backgrounding or process death** → which
is exactly where PRD §13 lives, so the verification tasks exercise screen-off, app-switch, and
Activity destruction explicitly rather than trusting a foreground smoke test.

**Audio focus that resumes when it should not** → worse than not resuming at all. Verified against a
real interruption (an incoming call or another media app), not just by reading the code.

**Zero-byte fixtures cannot decode** → the library change's device fixtures are empty files, so they
will fail to play. Real audio must be pushed to the device before any playback claim is credible.
Called out as its own task rather than discovered mid-verification.

**Bluetooth and lock-screen behavior cannot be judged on an emulator** → those checks are the
owner's, on the phone. The emulator can cover play/pause, backgrounding, and notification presence.

**`POST_NOTIFICATIONS` denial silently degrades the experience** → handled as a supported state
(D5), and the notification-dependent checks are recorded as conditional on the grant.

## Migration Plan

Additive. No schema change, no stored data touched. Rolling back means removing the service and its
permissions; the library keeps working exactly as it does today.

## Open Questions

- **Should playback resume automatically when the app is reopened mid-book?** Needs saved progress
  to be meaningful, so it belongs with the transport-controls change. Noted here because D2's
  playlist ownership should be revisited at the same time.
- **Behavior at the end of the last chapter.** Stopping is the obvious default; PRD §7.4 only
  specifies that next-chapter does nothing at the end. Left as stop-at-end unless it feels wrong in
  use.
