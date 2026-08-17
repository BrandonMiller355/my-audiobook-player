## Why

Every screen the app has was built to prove a capability worked, not to be looked at while
walking. The Player is a stack of outlined pill buttons around a 200dp cover, chapter navigation
is two more pills, and speed is a dropdown. The Library is a Material `TopAppBar` over a divided
list of 56dp thumbs. All of it functions; none of it reads at arm's length, which PRD §21 names
as the first design priority.

A design handoff — `handoffs/2026-08-15-App redesign request/design_handoff_player_redesign/` —
specifies direction **1b "Arm's Length"** in both light (`1b`) and dark (`2a`) palettes, at high
fidelity: exact colors, type scale, sizes, spacing, and radii. This change implements it.

The feature set does not change. Same speed stops, same seek amounts, same book-wide scrubber,
same long-press-to-remove, same two destinations. What changes is what those features look like
and where the user reaches for them.

PRD sections implemented: §7.1 (library list, including its optional progress and duration
items), §7.2 (Player layout), §7.3 (seek controls), §7.4 (chapter navigation), §9 (speed
stops), §20 (screen flows), §21 (large controls, high contrast, readable at arm's length),
§26.1 (light and dark).

## What Changes

**Library**

- The `TopAppBar` is replaced by a "Library" title that is list content and scrolls with it, and
  the "Add" text button becomes a 44dp circle holding a `+`. The add dropdown itself is
  unchanged.
- A **resume card** appears above the list when a book has a saved position: 132dp cover, the
  book's title, time remaining, its own 62dp play/pause control, and a progress bar. Its play
  control starts or pauses that book **without navigating**; tapping the card body opens the
  Player.
- Book rows grow to a 64dp cover with a 19sp title, and the second line becomes
  `18 ch · 8h 40m` — chapter count and total duration — in a monospace face. Dividers are gone.
- The empty state becomes the whole screen: a 30sp headline and two full-width buttons that
  launch the folder and file pickers directly, rather than sending the user back to the add menu.
- Remove-book moves from a mid-screen `AlertDialog` to a bottom sheet, so its buttons are
  thumb-reachable, and its copy says plainly that files are not deleted.

**Player**

- The cover becomes the hero: full device width, square, edge to edge behind the status bar, with
  a top scrim so the status bar and a new 44dp back button stay legible on any artwork. The
  "Back to library" text button at the bottom is removed.
- One 104dp circular play target, flanked by icon-only ±10s (60dp) and ±1m (52dp) controls with
  mono captions underneath. No pills, no outlined buttons anywhere.
- The scrubber is drawn to the design rather than being a Material `Slider`: 8dp track, 20dp
  thumb with a ring, elapsed and a **negative** remaining time in mono.
- The chapter line becomes `CH 12/26 — A SHIP'S COMPANY`.
- A footer of two halves replaces the previous-/next-chapter pills and the speed dropdown:
  "Chapters" and the current speed, each opening the same sheet.

**Chapters and speed sheet**

- Chapters and speed become inline — a bottom sheet over the Player, not a separate destination.
  It carries a mini-player header (so playback stays controllable), a horizontally scrollable row
  of all 14 speed chips, and the chapter list with the current chapter's row inverted and showing
  time remaining in that chapter. Tapping a chapter seeks to its start and closes the sheet.
- This replaces the previous-/next-chapter buttons as the *screen* affordance for chapter
  navigation. `previousChapter`/`nextChapter` and the 3-second rule stay exactly as they are —
  they are what Bluetooth and notification controls drive, and `transport-controls` still
  requires them.

**Theme**

- Both palettes are specified and both are implemented. Layout, sizes, spacing, and touch targets
  are identical between them; only color changes. The app still follows the system setting and
  still offers no in-app toggle.
- Two bundled font families arrive: Archivo for UI text, JetBrains Mono for every numeral that
  changes while playing — timestamps, durations, speed, chapter counts. Mono is not decoration
  here: it stops the scrubber's times from jittering as digit widths change.

**Data**

- Folder-chapter durations, which the Player already resolves in the background on every open,
  are now **written back** to the `chapters.endPositionMs` column that already exists. Without
  this the library row's `8h 40m` and the resume card's `4h 20m left` are unavailable for folder
  books, because a folder book's durations are nowhere in the database (`add-folder-audiobooks`
  design D5 decided not to read 128 files at add time, and that decision stands — this stores
  what a later open already paid for).
- `observeLibrary` gains total duration, absolute saved position, and last-played time.

## Capabilities

### Modified Capabilities

- `audiobook-library`: the requirement for what the library list shows currently names title and
  chapter count only. It gains total duration, a resume entry with its own playback control, the
  redesigned empty state's two direct add paths, and removal confirmation as a sheet. Availability,
  removal semantics, and the add flow are untouched in substance.
- `transport-controls`: the scrubber and speed requirements are written in terms of a slider and a
  menu. They become a drawn scrubber and a chip row, with the same behavior — drag updates the
  label, seek commits on release; speed applies immediately and persists per book. A new
  requirement covers reaching a chapter directly from the Player.
- `app-shell`: the theme requirement gains the second palette being fully specified rather than
  derived, and the Player's edge-to-edge cover means the app now manages status-bar icon
  appearance per screen.

### New Capabilities

None. Every requirement here modifies an existing capability; this change adds no capability the
app did not already have.

## Impact

**Code**

- `ui/theme/` — `Color.kt` gains the redesign tokens for both palettes; `Type.kt` is rewritten
  around two bundled families and 14 named styles; `Theme.kt` maps what has a Material role and
  exposes the rest through one `CompositionLocal`.
- `ui/library/LibraryScreen.kt`, `ui/player/PlayerScreen.kt` — rewritten.
- `ui/player/ChaptersSheet.kt` — new; the sheet is its own file rather than a third screen's worth
  of composables inside `PlayerScreen.kt`.
- `ui/BookCover.kt` — new size and radius constants, and the unavailable-book thumb.
- `ui/Icons.kt`, `ui/Format.kt` — new; see the dependency note and design D2.
- `ui/library/LibraryViewModel.kt` — a `MediaController` connection, needed only so the resume
  card's play control can start playback without navigating.
- `ui/player/PlayerViewModel.kt` — a chapter list and remaining-in-chapter on the UI state;
  writes resolved folder durations back to the database.
- `data/` — `LibraryBook` gains three fields, `LibraryDao` gains one column expression, one
  subquery, and one update.
- `res/values/strings.xml` — copy changes throughout.
- `res/font/` — six font files.

**Dependencies**

None added. Two deliberate departures from the handoff, both recorded in `design.md`:

- The handoff asks for Material icons. The icons it draws are 2dp strokes with round caps;
  Material's default set is filled glyphs with no stroke control, and half of what is needed
  (`Pause`, `Folder`, `Description`, the double chevrons) lives in `material-icons-extended`,
  which is not a dependency and is a large one to add for nine shapes — particularly with
  `isMinifyEnabled = false` on release. They are drawn on a `Canvas` instead, which is what
  actually matches the specified stroke weights.
- The handoff asks for Archivo and JetBrains Mono as bundled fonts. Those are bundled (OFL, no
  runtime fetching, no network), which is a resource addition rather than a dependency.

**Permissions and manifest**

No change. No new permission, no manifest edit, no foreground-service change. The fonts are
local resources and the app still declares no `INTERNET` permission.

**Data**

No schema change and no migration. `chapters.endPositionMs` already exists — added by
`add-m4b-books` at version 3 — and is already nullable. This change writes to it for folder
chapters, which previously left it null. Room stays at version 3.

**Source files**

Untouched. Nothing here reads, writes, moves, or deletes a user's audio.

## Non-goals

This change deliberately does **not**:

- Change any playback behavior. Seek amounts, the 3-second previous rule, chapter boundaries,
  speed stops, audio focus, the notification, and Bluetooth handling all behave exactly as they
  do now. `playback` is not modified.
- Add a third screen. Chapters and speed are a sheet over the Player; the navigation graph still
  has two destinations.
- Add an in-app theme toggle. The system setting stays the only input (PRD §26.1, app-shell).
- Add cover art for folder books, author or album metadata, or any new library sorting or
  filtering.
- Read durations at add time. A folder book shows its chapter count alone until it has been
  opened once, which is when the durations it already resolves get stored.
- Add animation beyond what the design names: a 120ms play/pause crossfade, a 150ms speed-chip
  color fade, and Material's stock bottom-sheet transitions.
- Introduce a design-system module, a token generator, or a component library. The tokens are
  named vals in the existing theme package.
