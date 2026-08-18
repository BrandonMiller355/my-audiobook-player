## Why

The owner listens through Shokz OpenMove headphones, which have no dedicated skip buttons. The
only gestures available are multi-presses of the single side button, and the headset's firmware
maps them to AVRCP next-track (double-press) and previous-track (triple-press). Today the app
turns those into next chapter and previous chapter.

Against the owner's actual library that mapping is not merely unhelpful — it is destructive.
Every `.m4b` in the library parses as a single chapter spanning a 10 to 14 hour file, so:

- **Double-press** reaches `nextChapterTarget()`, which returns `null` at the final chapter.
  There is no next chapter, so nothing happens at all.
- **Triple-press** reaches `previousChapterTarget()`, which applies the 3-second rule: more than
  three seconds in, it restarts the current chapter. The current chapter is the whole book, so
  the press jumps to 0:00 of a twelve-hour file. `PlaybackService` then persists that position on
  its next tick and the listening position is gone, with no undo.

So the one gesture that is easy to perform does nothing, and the one that is easy to perform by
accident loses the owner's place in the book. Chapter navigation from the headset has no value
here — the books have no chapters to navigate — while "I missed that, go back" is the thing
actually wanted while walking or running with the phone in a pocket.

This change reassigns the two Bluetooth gestures to fixed backward seeks, and leaves every other
control surface exactly as it is.

PRD sections affected: §7.4 (system playback controls — the "map previous/next media actions to
chapter navigation" line becomes true of the notification and lock screen but no longer of
Bluetooth), §14 (Bluetooth controls), §26.

## What Changes

- A **double-press** on a Bluetooth headset seeks **backward one minute**.
- A **triple-press** on a Bluetooth headset seeks **backward three minutes**.
- Neither gesture navigates chapters any more, on either book type.
- Both reuse the existing `BookTimeline.seekTarget()` math, so they roll back across chapter
  boundaries on a folder book and clamp at the start of the book rather than going negative —
  identical in behavior to the Player screen's existing −1m button.
- **Nothing else changes.** The Player screen's transport row, its previous/next chapter buttons,
  the media notification's buttons, and the lock screen all keep their current behavior, including
  the 3-second rule.

The separation is possible because Media3 attributes media-button events by origin before the app
sees them: notification buttons arrive as the media notification controller, Bluetooth arrives as
a legacy controller. See design D2.

## Capabilities

### Modified Capabilities

- `playback`: the requirement covering system and Bluetooth media controls currently states that
  Bluetooth next and previous move between chapters. It is rewritten so that Bluetooth play/pause
  is unchanged while the next-track and previous-track commands become fixed backward seeks, and
  so that the notification and lock screen are stated separately and keep chapter navigation.
- `transport-controls`: the 3-second-rule requirement currently says the rule applies "whether
  triggered from the app's own controls or from an external control surface such as Bluetooth or
  the media notification". Bluetooth is removed from that list; the notification and lock screen
  stay in it. A new requirement covers the two Bluetooth seek gestures and their interval values.
- `m4b-books`: its chapter-navigation requirement claims a Bluetooth next-track or previous-track
  command "moves between that book's embedded chapters rather than doing nothing". That scenario
  is replaced by one covering backward seeking inside the single item.

No new capability. This reassigns behavior across surfaces the app already owns.

## Impact

**Code**

- `playback/PlaybackService.kt` — the `MediaSession.Builder` gains a `MediaSession.Callback`
  implementing `onMediaButtonEvent`. This is the only place that can tell the two origins apart,
  which is why the remap cannot live in `ChapterAwarePlayer` (design D1).
- `playback/ChapterAwarePlayer.kt` — **unchanged.** It keeps serving the notification and lock
  screen with chapter navigation, including its `getAvailableCommands()` override, which must stay
  so the platform session keeps advertising the next and previous actions at all (design D5).
- `ui/player/` — untouched. `PlayerViewModel.nextChapter()`/`previousChapter()` compute targets
  from `BookTimeline` and call `seekTo(index, position)`; they never issue the next/previous
  command, so no possible change here can reach them.
- `playback/BookTimeline.kt` — unchanged. `seekTarget()` already does everything the two new
  gestures need.

**Dependencies**

None. `MediaSession.Callback.onMediaButtonEvent(MediaSession, ControllerInfo, Intent)` and
`MediaSession.isMediaNotificationController(ControllerInfo)` are both public API in the
media3 1.11.0 already wired into `:app`.

**Permissions and manifest**

No change.

**Data**

No schema change. Nothing new is persisted; the intervals are compile-time constants (design D6).

**Source files**

Untouched, as always.

## Assumptions

This change is being built without device verification first, at the owner's direction. The
assumption it rests on is stated here so that a failure is diagnosed rather than re-derived:

**The OpenMove's firmware performs the multi-press detection and emits distinct AVRCP commands**,
which reach Android as `KEYCODE_MEDIA_NEXT` (double) and `KEYCODE_MEDIA_PREVIOUS` (triple).

The alternative is that the headset emits repeated `KEYCODE_MEDIA_PLAY_PAUSE` presses and Media3's
own double-tap detector (`MediaSessionImpl.onMediaButtonEvent`) converts them into skip-next. That
would produce the same observed "double-press goes to the next chapter" while making this change a
no-op. It is considered unlikely because Media3's built-in detector only handles double-tap, and
so cannot explain the reported triple-press-to-previous behavior — but it has not been ruled out
on hardware. Task 4.1 is the check, and design D7 records the fallback.

## Non-goals

This change deliberately does **not**:

- Add a settings screen, or make the two intervals user-configurable. Two constants, chosen by the
  one user of this app, are the whole feature (design D6).
- Provide any forward seek from the headset. Both gestures rewind; see design D3 for why that
  asymmetry is the point rather than an oversight.
- Change the media notification's buttons, icons, or layout in any way. No use of
  `setMediaButtonPreferences`, no custom commands.
- Change anything on the Player screen, including its existing ±10s and ±60s buttons.
- Carve out Android Auto or car head units, which connect as legacy controllers and will therefore
  inherit the new gesture behavior (design D4).
- Add multi-press detection of the app's own. All press counting stays in the headset's firmware.
- Revisit the 3-second rule, which remains correct and unchanged for every surface that still
  navigates chapters.
