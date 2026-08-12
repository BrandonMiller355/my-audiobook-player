## Why

The library works and nothing plays. This change makes audio come out of the speaker, and makes it
keep coming out when the screen is off, the app is backgrounded, or the Activity is destroyed.

It is deliberately placed *before* seek and speed controls. PRD §27 puts background playback in
Phase 4, after transport controls in Phase 3, but building play/pause against an Activity-scoped
`ExoPlayer` and then moving it into a service means rewriting the ownership model — who holds the
player, how the UI observes it, and where state is written. Doing it service-first costs nothing
extra now and avoids that rework. The reordering was raised and agreed when the slicing was set.

Implements PRD §13 (background playback), §14 (system media controls), §15 (audio focus), and the
play/pause part of §7.2.

## What Changes

- Add a Media3 `MediaSessionService` that owns the `ExoPlayer` instance, and have the UI talk to it
  through a `MediaController` rather than holding a player of its own.
- Turn a book's stored chapters into an ordered Media3 playlist, one `MediaItem` per file, so a
  folder book plays end to end and advances between files on its own.
- Give the Player screen real play/pause with live position and duration readouts, replacing the
  placeholder. Seek buttons, the scrubber, chapter buttons, and speed stay out — they are the next
  change.
- Add a foreground media notification through Media3's notification provider, so playback survives
  backgrounding and the system shows lock-screen controls.
- Wire audio focus so the app ducks or pauses for calls and other media, stops on headphone unplug,
  and resumes where Android's conventions allow (PRD §15).
- **BREAKING for the manifest contract**: add the first permissions this app has ever declared —
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `POST_NOTIFICATIONS` — and declare
  the service with `foregroundServiceType="mediaPlayback"`.

## Capabilities

### New Capabilities

- `playback`: playing a book's audio — starting and pausing, advancing through chapters, continuing
  in the background, behaving correctly when another app takes audio focus, and responding to
  system, lock-screen, and Bluetooth transport controls.

### Modified Capabilities

- `app-shell`: its "requests no network permission" requirement currently asserts the app declares
  **no** `<uses-permission>` elements at all. That was true when written and stops being true here.
  The requirement is rewritten to say what it actually needs to: no network permission, no storage
  permission, and only the permissions background media playback requires.

## Impact

**Code**: New `playback/` package holding the `MediaSessionService`, the player construction, and
the mapping from stored chapters to `MediaItem`s. `PlayerScreen` gains a ViewModel that binds a
`MediaController` to Compose state. `AndroidManifest.xml` gains the service declaration and the
permissions. Nothing in `library/`, `data/`, or `m4b/` changes.

**Dependencies**: Adds `media3-exoplayer` and `media3-session`, both already in the version catalog
at 1.11.0 and both named in `config.yaml` as the agreed playback stack. No other dependency.

**Permissions and manifest**: This is the change that ends the app's zero-permission run.
`FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` are install-time and unavoidable for
background playback on API 34+. `POST_NOTIFICATIONS` is a **runtime** permission on API 33+, and is
requested when the user first starts playback rather than at launch — so `app-shell`'s promise that
first launch shows no permission dialog stays true. Playback must still work if it is denied; only
the notification is lost. The merged-manifest network check must still pass.

**Risk**: The service lifecycle is the least forgiving part of Android in this project. Getting
`MediaController` connection, service start, and foreground promotion wrong produces crashes that
only appear under backgrounding or process death, which is exactly where PRD §13 lives. Audio focus
is similarly easy to get subtly wrong — resuming when it should not is worse than not resuming.

**Testing constraint worth stating up front**: the emulator fixtures used for the library change
are **zero-byte files**, which will not decode. Verifying that audio actually plays requires real
audio on the device — a small real folder pushed to the emulator, and ideally a pass on the phone,
since Bluetooth and lock-screen behavior cannot be judged from an emulator at all.

## Non-goals

This change deliberately does **not**:

- Add ±10s/±60s seek, the book-wide scrubber, previous/next chapter buttons, or playback speed.
  Those are the transport-controls change, together with the `BookTimeline` mapping and the agreed
  3-second previous-chapter rule.
- Save or restore playback position. Nothing persists progress yet, so closing the app loses your
  place. That lands with transport controls, written by the service rather than the UI.
- Support `.m4b` books, or use the chapter parser. A single-file book needs chapter-aware
  previous/next, which needs the mapping layer that does not exist yet.
- Show cover art in the notification or the player. Artwork belongs to the metadata change.
- Add a sleep timer, EQ, normalization, Chromecast, or Android Auto — all PRD non-goals.
- Read chapter durations. The Player shows the current item's position and duration from the player
  itself, not a precomputed total for the whole book.
