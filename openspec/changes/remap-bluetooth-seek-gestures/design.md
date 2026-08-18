## Context

`add-m4b-books` D7 established `ChapterAwarePlayer`, a `ForwardingPlayer` wrapping the session's
player, on the reasoning that a Bluetooth command "reaches the session directly and never passes
through `PlayerViewModel`", so the only place every control surface could be made to agree was the
player itself. That reasoning was right for its purpose and the wrapper still does its job.

What has changed is the requirement. The owner no longer wants every surface to agree: the headset
should rewind while the notification keeps navigating chapters. That inverts the design goal, so
the question becomes whether the two origins can be told apart at all.

Current state that matters here:

- `ChapterAwarePlayer` overrides `seekToNext`/`seekToPrevious`/`seekToNextMediaItem`/
  `seekToPreviousMediaItem`, and force-adds the corresponding commands in `getAvailableCommands()`
  so that neither is ever reported unavailable.
- `PlayerViewModel.nextChapter()` and `previousChapter()` do **not** call those methods. They
  compute a `PlayerTarget` from `BookTimeline` and call `seekTo(mediaItemIndex, positionMs)`. The
  in-app buttons therefore never reach `ChapterAwarePlayer`'s overrides at all. The doc comment on
  that class, which says it serves "the in-app button, the notification, or a Bluetooth remote",
  overstates its reach — the in-app path duplicates the logic instead, which is why the two have
  never disagreed and nobody noticed.
- `PlaybackService` builds its `MediaSession` with no `Callback`, so Media3's default handling
  applies to every media-button event.
- `BookTimeline.seekTarget(from, deltaMs)` already implements signed seeking with chapter rollover
  and clamping at both ends of the book, and is unit-tested in `BookTimelineTest`.
- Every `.m4b` in the owner's library is a single chapter spanning the whole file.

## Goals / Non-Goals

**Goals:**

- Double-press on the headset seeks back one minute; triple-press seeks back three minutes.
- The Player screen, the media notification, and the lock screen behave exactly as they do today.
- No new persisted state, no new dependency, no schema change.
- Reuse the existing seek math rather than writing a second implementation of it.

**Non-Goals:**

- Configurability, forward gestures, notification restyling, Android Auto carve-outs — see the
  proposal's non-goals.
- Verifying the key codes on hardware before implementing. That is deliberately deferred; see D7.

## Decisions

### D1: The remap lives in `MediaSession.Callback.onMediaButtonEvent`, not in `ChapterAwarePlayer`

`ChapterAwarePlayer` sees a `seekToNext()` call with no idea who asked. By the time a media-button
event has been translated into a player command, the origin is gone. Putting the remap there would
change the notification and lock screen along with the headset, which is precisely what this change
must not do.

`MediaSession.Callback.onMediaButtonEvent(session, controllerInfo, intent)` runs earlier — before
any default handling and before any player command is issued — and receives the `ControllerInfo`.
Returning `true` consumes the event; returning `false` falls through to Media3's default, which
ends up at `ChapterAwarePlayer` exactly as it does today.

So `ChapterAwarePlayer` is not modified at all. It becomes the handler for everything that falls
through, which is every surface except the headset.

```
                    MediaSession.Callback.onMediaButtonEvent()
                                     |
            +------------------------+------------------------+
            |                                                 |
  isMediaNotificationController                    LEGACY_CONTROLLER_VERSION
     (notification, lock screen)                       (Bluetooth headset)
            |                                                 |
       return false                              MEDIA_NEXT     -> seek -1 min
    (fall through to default)                    MEDIA_PREVIOUS -> seek -3 min
            |                                                 |
            v                                            return true
     ChapterAwarePlayer                                   (consumed)
     seekToNext / seekToPrevious
       = chapter nav, UNCHANGED
```

### D2: Origin is determined by `isMediaNotificationController`, not by key code or package name

Both origins deliver a `KEYCODE_MEDIA_NEXT` inside an `ACTION_MEDIA_BUTTON` intent, so the key
event itself carries nothing distinguishing. Media3 1.11.0 attributes them differently upstream of
the callback:

| Origin | Path in media3-session | Resulting `ControllerInfo` |
|---|---|---|
| Notification, lock screen | `MediaSessionService.onStartCommand` uses `sessionImpl.getMediaNotificationControllerInfo()` | `isMediaNotificationController()` is **true** |
| Bluetooth, AVRCP | platform session to `MediaSessionLegacyStub.onMediaButtonEvent`, which constructs a `ControllerInfo` with `LEGACY_CONTROLLER_VERSION` | `isMediaNotificationController()` is **false** |

`MediaSession.isMediaNotificationController(ControllerInfo)` is public API and is the check to use.
Testing `controllerInfo.controllerVersion == ControllerInfo.LEGACY_CONTROLLER_VERSION` directly
would also work today but reads as an implementation detail; the named predicate says what is meant.

The rule is stated as "not the notification controller" rather than "is Bluetooth" because Media3
offers no positive test for a Bluetooth origin, and because the fallback direction matters: an
origin nobody anticipated should get the *old* chapter behavior rather than a silent rewrite of
what its button does.

### D3: Both gestures rewind; there is no forward gesture

The obvious symmetric design is back-1m on double and forward-3m on triple. It is rejected.

Multi-press detection is timing-sensitive, and a triple that the firmware reads as a double (or the
reverse) is a routine occurrence rather than an edge case. With both gestures backward, every
possible misfire costs the listener some re-listening and nothing else — the failure mode is
bounded and self-correcting. Introduce a forward gesture and a misfire can skip past content
unheard, which is the failure the listener cannot detect and cannot easily undo.

An audiobook also has no real forward use case: the reason to touch a button while walking is
almost always that something was missed. Forward seeking remains available on the Player screen,
where the user is looking at what they are doing.

This is also what makes the resulting model describable in one line, which the owner asked for:
**the headset rewinds, the screen navigates.**

### D4: Android Auto and car head units inherit the gesture behavior

They connect as legacy controllers, so `isMediaNotificationController` is false for them and they
take the rewind path. Media3 exposes `isAutoCompanionController()` if they ever need carving out.

Accepted as-is rather than branched on. The app is single-user, local-only, and adding an untested
third case to satisfy a surface nobody uses would be speculative complexity. If a head unit ever
matters, the check is one line and the design note is here.

### D5: `ChapterAwarePlayer.getAvailableCommands()` must keep force-adding the commands

Tempting to remove, since Bluetooth no longer reaches those methods. It must stay.

The override exists because a command reported unavailable is one the platform session does not
advertise, and a control surface that sees no next-track action may never send one. Removing it
would leave the headset's presses undelivered on a single-item book — the app would never get the
chance to remap what it never receives. The override's own comment already says this; it applies
with more force now, because the availability of `COMMAND_SEEK_TO_NEXT` is what keeps the gesture
reaching the callback at all.

### D6: The two intervals are constants in `PlaybackService`, not settings

`private const val BLUETOOTH_DOUBLE_PRESS_SEEK_MS = -60_000L` and
`BLUETOOTH_TRIPLE_PRESS_SEEK_MS = -180_000L`, named for the gesture rather than the interval so the
mapping is legible at the call site.

They are not derived from `PlayerScreen`'s `SEEK_LONG_MS`. That constant means "what the −1m button
does"; these mean "what a headset gesture does". They coincide at one minute today and there is no
reason they must stay coincident, so sharing the constant would create a false coupling.

No settings screen. The PRD's simplicity rule and the app's single user both argue against building
configuration for a value that can be changed by editing one line.

### D7: The key codes are assumed, and the fallback is recorded rather than built

Implementation proceeds on the assumption that the headset emits `KEYCODE_MEDIA_NEXT` and
`KEYCODE_MEDIA_PREVIOUS` (proposal, Assumptions). If task 4.1 disproves it and the headset is
instead emitting repeated `KEYCODE_MEDIA_PLAY_PAUSE`:

- The gestures would have to be counted in the app, since the firmware would not be counting them.
- That means intercepting `KEYCODE_MEDIA_PLAY_PAUSE` and holding each press for a multi-press
  timeout before acting, which delays *single*-press play/pause by that timeout — a real cost paid
  by the most-used gesture to serve the two least-used.
- Media3's own double-tap handler would also need suppressing, since it acts on the same key.

That is a materially different change with a materially worse trade-off, and it would deserve its
own proposal rather than being smuggled in as a fallback branch here. Nothing in this change is
wasted if it happens: D1's callback seam is where that work would go too.

## Risks / Trade-offs

- **The change is unverifiable without the owner's hardware.** No emulator has a Shokz attached,
  and `adb shell input keyevent KEYCODE_MEDIA_NEXT` does not reproduce the origin distinction —
  it arrives through the platform session, so it exercises the legacy path, which is the useful
  half. The notification half can be exercised by pressing the notification's buttons. Neither
  proves what the headset actually sends. Accepted; see D7.
- **A key event arrives twice.** `onMediaButtonEvent` is invoked for both `ACTION_DOWN` and
  `ACTION_UP`. Acting on both would double every seek. The callback acts on `ACTION_DOWN` with
  `repeatCount == 0` only, and returns `true` for the matching `ACTION_UP` as well so that the
  default handling does not also process it.
- **Held buttons.** A long press produces `repeatCount > 0` on Android, though the headset is
  expected to send discrete events. Ignoring repeats keeps a held button from walking backward
  through the book.
- **The `playback` spec's Bluetooth scenario is being weakened, not just changed.** After this,
  no requirement anywhere asserts that a Bluetooth device can reach chapter navigation, because
  none can. That is intended and is the point of the change, but it means a future reader will not
  find a spec explaining why the headset does not skip chapters — hence the explicit rationale in
  the modified requirement's text.

## Open Questions

- Does the OpenMove's firmware emit anything on a long press that reaches the media session — some
  headsets send `KEYCODE_MEDIA_FAST_FORWARD` or `REWIND` — or is long press bound to the voice
  assistant and never seen by the app? If a third gesture exists it is a free slot, but nothing in
  this change depends on the answer.
