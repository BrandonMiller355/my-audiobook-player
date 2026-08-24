# Handoff: Bluetooth headset gesture remap — shelved

**Branch:** `master` (clean at handoff time)
**Date:** 2026-08-24
**Session type:** `/opsx:explore` — exploration only. **No code written.** The
`openspec/changes/remap-bluetooth-seek-gestures/` proposal was removed with `git rm` when the owner
decided against building it; see "Repo state at handoff" below, which is worth reading — the
removal was less straightforward than it should have been. Nothing in `openspec/specs/`, the PRD,
or `app/` was touched.

---

## Read this first: one finding here is about shipped code, not about the shelved feature

The remap was dropped. **The bug it was going to fix was not fixed and is still live.** If you
read only one section of this document, read the next one.

---

## The live problem: a triple-press wipes the saved position

Every `.m4b` in the owner's library parses as a single chapter spanning a 10–14 hour file. On such
a book, the Bluetooth previous-track command does this:

```
triple-press
  -> KEYCODE_MEDIA_PREVIOUS
  -> ChapterAwarePlayer.seekToPrevious()          ChapterAwarePlayer.kt:30
  -> BookTimeline.previousChapterTarget()         BookTimeline.kt:68
       offsetMs > 3000  ->  restart current chapter
       current chapter IS the whole book
  -> seekTo(0, 0)
```

Playback jumps to 0:00 of the whole book. `PlaybackService`'s progress ticker then persists that
position within 15 seconds (`PlaybackService.kt:155`), or immediately on pause. The listening
position is gone with no undo.

The complementary half: **double-press does nothing at all.** `nextChapterTarget()` returns `null`
at the final chapter (`BookTimeline.kt:79-84`), and on a one-chapter book every chapter is the
final one.

So on the owner's actual library, the easy gesture is inert and the awkward one is destructive.
This is true of `master` as it stands today and is independent of whether the remap is ever built.

**If nothing else is done, the cheapest mitigation is unrelated to the remap:** make the progress
write not clobber a position the user did not choose to move to, or make `previousChapterTarget`'s
restart branch a no-op when the book has exactly one chapter. Either is a much smaller change than
what was proposed below.

---

## The hardware constraint

Shokz OpenMove has no skip buttons — only multi-presses of the single side button. Per the owner:
double-press goes to the next chapter, triple-press to the previous. The OpenMove has no companion
app support (Shokz only exposes remapping on OpenRun Pro 2 / OpenFit and newer), so what the
headset *sends* cannot be changed. Only the app's interpretation of it can.

The owner's stated want: **double-press = back 1 minute, triple-press = back 3 minutes**, with the
Player screen and the media notification left exactly as they are.

---

## Reference material (read, don't re-derive)

| What | Where |
|---|---|
| Product requirements | `simple_android_audiobook_player.prd` §7.4, §14 |
| Seek/chapter math (already handles everything needed) | `playback/BookTimeline.kt` |
| The player wrapper external surfaces reach | `playback/ChapterAwarePlayer.kt` |
| Where a session callback would go | `playback/PlaybackService.kt:95` |
| Specs that assert the current mapping | `openspec/specs/playback/spec.md:100`, `transport-controls/spec.md:36`, `m4b-books/spec.md:128` |
| media3 sources (read during this session) | `D:\Android\gradle\caches\modules-2\files-2.1\androidx.media3\media3-session\1.11.0\...-sources.jar` |

media3 version in use: **1.11.0**, wired into `:app` (`app/build.gradle.kts:118-119`).

---

## What was established (verified against media3 1.11.0 source, not assumed)

### 1. The in-app buttons cannot be affected by any of this

`PlayerViewModel.nextChapter()` / `previousChapter()` (`PlayerViewModel.kt:230-244`) compute a
`PlayerTarget` from `BookTimeline` and call `seekTo(mediaItemIndex, positionMs)`. They **never**
call `seekToNext()`/`seekToPrevious()`, so they never reach `ChapterAwarePlayer`'s overrides.

`ChapterAwarePlayer`'s class comment claims it serves "the in-app button, the notification, or a
Bluetooth remote." That is wrong about the in-app button and has been for a while — the two paths
duplicate the logic, which is why they have never disagreed. **Worth correcting regardless of this
feature**, because it makes any change in this area look riskier than it is.

### 2. Notification and Bluetooth ARE distinguishable

This was the open question, and the answer is yes, via public API. Both origins deliver an
identical `KEYCODE_MEDIA_NEXT` inside an `ACTION_MEDIA_BUTTON` intent, so the key event carries
nothing useful — but media3 attributes them differently *before* the app's callback runs:

| Origin | Path in media3-session | `ControllerInfo` |
|---|---|---|
| Notification, lock screen | `MediaSessionService.onStartCommand` uses `sessionImpl.getMediaNotificationControllerInfo()` (`MediaSessionService.java:548`) | `isMediaNotificationController()` **true** |
| Bluetooth, AVRCP | platform session → `MediaSessionLegacyStub.onMediaButtonEvent` builds a `ControllerInfo` with `LEGACY_CONTROLLER_VERSION` (`MediaSessionLegacyStub.java:534`) | `isMediaNotificationController()` **false** |

Both funnel into `MediaSessionImpl.onMediaButtonEvent`, which calls the app's
`MediaSession.Callback.onMediaButtonEvent(session, controllerInfo, intent)` first, before any
default handling (`MediaSessionImpl.java:1624`). Return `true` to consume; return `false` to fall
through to the default, which ends at `ChapterAwarePlayer`.

`PlaybackService` currently builds its session with **no** `Callback`, so that seam is unused.

### 3. The shape the change would take

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

`ChapterAwarePlayer` and `BookTimeline` would not be modified. The seek reuses
`BookTimeline.seekTarget(location, -delta)`, which already rolls back across chapter boundaries,
clamps at the start of the book, and clamps to the current chapter's start when the preceding
chapter's duration is unresolved.

---

## The assumption that was never tested

The owner declined device testing before implementation, so this was never confirmed:

**Assumed:** the OpenMove's firmware counts the presses and emits distinct AVRCP commands, arriving
as `KEYCODE_MEDIA_NEXT` (double) and `KEYCODE_MEDIA_PREVIOUS` (triple).

**Alternative not ruled out:** the headset emits repeated `KEYCODE_MEDIA_PLAY_PAUSE` and media3's
own double-tap detector (`MediaSessionImpl.java:1650-1678`) converts them to skip-next. That would
produce the same observed behavior while making the whole design above a no-op.

Considered unlikely, because media3's built-in detector only handles *double*-tap and therefore
cannot explain the reported triple-press-to-previous. But it is one `adb logcat` check with the
headset connected, and **it should be step one of any future attempt** — a negative result
invalidates the design rather than merely failing it.

If the alternative turns out to be true, the app would have to count presses itself, which means
holding every `PLAY_PAUSE` press for a multi-press timeout before acting — delaying single-press
play/pause, the most-used gesture, to serve the two least-used. That is a materially worse trade
and deserves its own decision, not a fallback branch.

---

## Decisions the owner made, worth not re-litigating

- **Both gestures rewind. No forward gesture from the headset.** Multi-press detection is
  timing-sensitive and a miscounted press is routine; with both directions backward, every misfire
  costs re-listening and nothing worse. A forward gesture makes a misfire skip content unheard,
  which is the failure the listener cannot detect. Forward seeking stays on the Player screen.
- **Chapter navigation from the headset is not wanted at all.** The books have no chapters to
  navigate.
- **The notification and lock screen keep chapter navigation**, including the 3-second rule.
- **No settings screen.** Two constants for a single-user app.

## Non-obvious traps found while designing it

- `ChapterAwarePlayer.getAvailableCommands()` force-adds `COMMAND_SEEK_TO_NEXT` and friends. It
  looks removable once Bluetooth stops using chapter nav. **It is not.** A command reported
  unavailable is one the platform session does not advertise, and a surface that sees no next-track
  action may never send one — the presses would stop arriving at all.
- `onMediaButtonEvent` fires for **both** `ACTION_DOWN` and `ACTION_UP`. Acting on both double-seeks
  every press. Act on `ACTION_DOWN` with `repeatCount == 0`, and return `true` for the matching
  `ACTION_UP` so the default does not also process it.
- Ignore `repeatCount > 0`, or a held button walks backward through the book.
- Android Auto and car head units connect as legacy controllers and would inherit the rewind
  behavior. `isAutoCompanionController()` exists if that ever needs carving out; it was deliberately
  not branched on.

---

## Spec impact, if this is ever revived

Three requirements assert the current mapping and would need MODIFIED deltas:

- `openspec/specs/playback/spec.md:100` — "Bluetooth previous and next move between chapters"
- `openspec/specs/transport-controls/spec.md:36` — the 3-second rule "SHALL apply equally whether
  triggered from the app's own controls or from an external control surface such as Bluetooth or
  the media notification"
- `openspec/specs/m4b-books/spec.md:128` — "A Bluetooth command navigates chapters"

PRD §7.4's line "Map previous/next media actions to chapter navigation" would become true of the
notification and lock screen but false of Bluetooth, so it needs amending rather than deleting.

Note that after such a change, **no** requirement anywhere would assert that a Bluetooth device can
reach chapter navigation. That is intended, but it means the reason has to live in the modified
requirement's prose or it will read as an oversight later.

---

## Repo state at handoff

- `openspec/changes/` — no active changes. `remap-bluetooth-seek-gestures` was removed with
  `git rm -r`; **the deletion is staged but not committed.** It is not in `archive/`, because
  archiving means shipped and this never did.
- No code, spec, or PRD file was modified in this session.

### Unresolved: the proposal folder was already in git history

Worth knowing before anyone re-creates this change, because it will look like déjà vu:

The seven files under `openspec/changes/remap-bluetooth-seek-gestures/` were **already tracked at
HEAD** when this session removed them, added by commit `47d4d09` (Aug 18) — a commit whose message
is entirely about read-along scroll and which appears to have swept them in incidentally.

Their committed content is byte-identical to what this session's exploration produced, modulo line
endings (HEAD blob 11,428 bytes vs 11,617 on disk for `design.md`; the 189-byte difference is
exactly its line count, i.e. CRLF vs LF). That also explains why `git status` reported the tree
clean earlier in the session while the files sat on disk — git saw them as matching HEAD, because
they did.

No explanation was found for how content composed in this session was already present in a
week-old commit. Two candidates, neither confirmed: a near-identical proposal was drafted on Aug 18
and independently reproduced, or git's view in this environment does not reflect the real
repository. If the latter, treat every git observation in this document as suspect — the media3
source findings above were read directly from the AAR and do not depend on it.

To recover the deleted proposal: `git restore --source=47d4d09 -- openspec/changes/remap-bluetooth-seek-gestures/`
