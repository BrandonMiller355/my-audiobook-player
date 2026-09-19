# Handoff: Player & Library redesign — direction 1b "Arm's Length"

## Overview
A visual and layout rethink of the two screens in **BrandonMiller355/my-audiobook-player** (branch `master`): the library list and the playback screen, plus the empty-library state, the inline chapter list, the speed picker, and the remove-book confirmation.

Goals driving the design:
- The cover art is the hero: full device width on the player, never letterboxed or cropped-off.
- No pill/outlined buttons. One large circular play target (104dp) with plain icon-only seek controls.
- Big type and mono numerals, readable at arm's length while walking.
- Chapters and speed are reachable **inline** (a sheet over the player), not a separate destination.
- Feature set is unchanged. Same speed stops, same seek amounts, same book-wide scrubber, same long-press-to-remove.

**Both light and dark are specified.** Layout, sizes, spacing, and touch targets are identical in the two themes — only the palette changes (see "Colors (dark)"). Wire both into `AudiobooksTheme` via `lightColorScheme` / `darkColorScheme` and follow the system setting; no in-app toggle.

## About the Design Files
`Audiobooks Redesign.dc.html` in this bundle is a **design reference authored in HTML**, not production code. It renders phone-sized frames (390×844 CSS px ≈ a 390dp-wide device) for every screen. Do not port the HTML or its inline styles.

The task is to **recreate these screens in the existing Android app**: Kotlin + Jetpack Compose + Material 3, using the app's existing structure — `ui/library/LibraryScreen.kt`, `ui/player/PlayerScreen.kt`, `ui/BookCover.kt`, `ui/theme/*`, `res/values/strings.xml` — and its existing ViewModels (`LibraryViewModel`, `PlayerViewModel`). No new architecture, no new libraries. Coil (`coil3.compose.AsyncImage`) already loads covers and should keep doing so.

Open the HTML file in a browser to see the reference. It is organised in turns, newest first: **`2a` at the top is 1b in dark, and `1b` further down is the same design in light — those two are what to implement.** `1a` and `1c` are rejected alternatives and the bottom row recreates today's build; both are context only. Cover images in the reference are drag-and-drop placeholders — ignore the placeholder styling, real covers fill those boxes.

## Fidelity
**High-fidelity.** Colors, type sizes, weights, spacing, and radii below are final and should be matched. Where a value in the HTML is in CSS px, treat it as **dp** 1:1 (the frames are drawn at 390dp width). Font sizes map to `sp`.

One deliberate departure from Material defaults: seek and chapter controls are **icon-only, no container** (`IconButton` with no background), and the play control is a filled circle drawn as a `Surface`/`Box` rather than a `Button`, because Material's `Button` cannot be a 104dp circle without fighting its content padding.

---

## Design Tokens

### Colors (light)
These extend the existing `ui/theme/Color.kt` palette. The redesign uses a slightly cooler near-black for ink and a slightly whiter surface than the current `LightBackground`, because the cover now dominates and the surround should recede.

| Token | Hex | Role |
| --- | --- | --- |
| `Surface` | `#FFFDFB` | Screen background for library, player panel, sheets |
| `Ink` | `#121110` | Primary text, play button fill, active chapter row background |
| `OnInk` | `#FFFDFB` | Text/icon on `Ink` |
| `InkMuted` | `#3F3A35` | Secondary icons (the ±1m seek pair) |
| `TextSecondary` | `#57514B` | Body copy, supporting text |
| `TextTertiary` | `#6B645D` | Mono metadata (durations, chapter counts) |
| `TextQuaternary` | `#9A9189` | Uppercase labels, chapter numbers, hints |
| `Track` | `#EBE3DB` | Progress/scrubber track, hairline dividers |
| `Fill` | `#F0E9E2` | Neutral chip/secondary button fill, add-button circle |
| `Error` | `#B3261E` | Unavailable state, destructive button fill (existing `LightError`) |
| `CoverPlaceholder1` | `#E6DED3` | Coverless book thumb (existing `LightSecondaryContainer`) |
| `CoverPlaceholder2` | `#D6E0EC` | Coverless book thumb, alternate (existing `LightPrimaryContainer`) |
| `ErrorTint` | `#F0E4E2` | Thumb background for an unavailable book |
| Scrim (dialog) | `#121110` at 50% | Behind the remove sheet |
| Scrim (cover top) | vertical `#121110` 45% → 0% over the top 34% of the cover | So the status bar and back button stay legible on any artwork |

### Colors (dark)
Same roles, inverted. The play button, the scrubber fill, the current-chapter row, and the primary empty-state button all flip to **light-on-dark**, so the brightest element on screen is still the thing you press.

| Token | Hex | Role |
| --- | --- | --- |
| `SurfaceDark` | `#131211` | Screen background |
| `SurfaceRaisedDark` | `#1C1A18` | Bottom sheets (one step lighter than the screen) |
| `InkDark` | `#F7F3EF` | Primary text; **also the fill** for the play button, scrubber, active chapter row, primary button |
| `OnInkDark` | `#131211` | Text/icon on `InkDark` fills |
| `InkMutedDark` | `#C6BEB6` | Secondary icons (±1m pair), chapter titles |
| `TextSecondaryDark` | `#A9A19A` | Body copy, supporting text |
| `TextTertiaryDark` | `#8A827A` | Mono metadata |
| `TextQuaternaryDark` | `#6E6660` | Uppercase labels, chapter numbers, captions |
| `TrackDark` | `#2E2A27` | Scrubber track, hairline dividers |
| `FillDark` | `#221F1D` | Unselected speed chip, secondary button, add-button circle |
| `FillPressedDark` | `#2A2724` | "Keep it" button on the sheet |
| `ErrorDark` | `#F2B8B5` | Unavailable-source text and icon |
| `ErrorContainerDark` | `#8C1D18` | Destructive button fill |
| `OnErrorContainerDark` | `#FFDAD6` | Text on the destructive button |
| `ErrorTintDark` | `#2B1A18` | Thumb background for an unavailable book |
| `CoverPlaceholder1Dark` | `#3A332B` | Coverless book thumb |
| `CoverPlaceholder2Dark` | `#2C3742` | Coverless book thumb, alternate |
| Scrim (dialog) | `#060605` at 62% | Behind the remove sheet — deeper than light, so the sheet reads as lifted |
| Scrim (cover top) | vertical `#0B0A09` 50% → 0% over the top 34% | Back button and status bar legibility |
| Cover base fade | vertical transparent → `#131211` over the bottom **64dp** of the cover | Dark-only: keeps a bright cover from cutting hard against the dark surround |

Two dark-only details, both visible in `2a`:
1. The **cover base fade** above. In light there is no bottom fade; in dark it is required or the cover edge looks like a seam.
2. The scrubber thumb ring is `SurfaceDark` (not white), 4dp, same 20dp thumb.

Everything else — the 132dp resume cover, 104dp play, 22dp gutters, all type sizes — is unchanged between themes.

Keep these as named vals in `Color.kt` and wire them into the existing `lightColorScheme` where they map to a role (`surface`, `onSurface`, `primary` = `Ink`, `surfaceVariant` = `Fill`, `outlineVariant` = `Track`). Anything without a Material role can be a plain val referenced directly.

### Typography
Two families, both to be added as bundled fonts in `res/font/` and wired into `ui/theme/Type.kt`:
- **Archivo** (400/500/600/700) — all UI text. Google Fonts, OFL.
- **JetBrains Mono** (400/500) — every numeral that changes while playing: timestamps, durations, speed, chapter counts. Mono keeps the scrubber times from jittering as digits change width. OFL.

If bundling two families is unwanted, `FontFamily.Default` at the same sizes/weights is an acceptable fallback for Archivo, but **keep a monospace family for numerals**.

| Style | Family | Size | Weight | Letter spacing | Used for |
| --- | --- | --- | --- | --- | --- |
| `displayScreen` | Archivo | 36sp | 700 | −0.03em | "Library" screen title |
| `displayEmpty` | Archivo | 30sp | 700 | −0.03em | Empty-state headline, line-height 1.1 |
| `titlePlayer` | Archivo | 27sp | 700 | −0.025em | Book title on the player, line-height 1.08 |
| `titleResume` | Archivo | 24sp | 700 | −0.02em | Book title in the resume card; also sheet titles |
| `titleRow` | Archivo | 19sp | 600 | −0.01em | Library row title, active chapter row |
| `bodyLarge` | Archivo | 18sp | 500 | 0 | Chapter row title, sheet button labels |
| `bodyEmpty` | Archivo | 17sp | 400 | 0 | Empty-state body, line-height 1.5 |
| `labelAction` | Archivo | 17sp | 600 | 0 | "Chapters" footer label |
| `bodyDialog` | Archivo | 16sp | 400 | 0 | Dialog body, line-height 1.5 |
| `labelCaps` | Archivo | 11sp | 700 | 0.14em, uppercase | "KEEP LISTENING" |
| `monoTime` | JetBrains Mono | 15sp | 400 | 0 | Scrubber timestamps, speed value |
| `monoMeta` | JetBrains Mono | 13sp | 400 | 0 | Row metadata ("18 ch · 8h 40m"), chapter durations |
| `monoCaps` | JetBrains Mono | 13sp | 400 | 0.04em, uppercase | Player chapter line |
| `monoMicro` | JetBrains Mono | 12sp | 400 | 0.04em, uppercase | Seek captions, footer sub-labels, privacy note |

### Spacing, radii, elevation
- Screen horizontal padding: **22dp** (list rows, player panel, sheet content).
- Radii: cover thumbs **6dp**; resume-card cover **6dp**; sheet/secondary buttons **10dp**; bottom sheet top corners **22dp**; add-button circle and all play buttons fully round.
- Player cover: **no radius, full bleed** — width = screen width, height = screen width (1:1).
- Dividers/hairlines: 1dp `Track`.
- No card elevation anywhere. Separation comes from the 1dp hairline and the cover edge. The only shadow is a soft one under the scrubber thumb (`4dp` blur, 35% `Ink`) — optional, drop it if it fights Compose.
- Minimum touch target 48dp everywhere; the play button is 104dp, seek ±10s is 60dp, seek ±1m is 52dp.

---

## Screens

### 1. Library
**Purpose:** pick a book, or resume the one in progress in one tap.

**Layout** (top to bottom, `Column`, `Scaffold` with no `TopAppBar` — the title is content, so it scrolls with the list):
1. Header row: `padding(start = 22, end = 22, top = 10, bottom = 20)`, `SpaceBetween`, vertically centered.
   - "Library" — `displayScreen`, `Ink`.
   - Add control: 44dp circle, `Fill` background, centered 22dp `+` icon (2.2dp stroke, round caps), `Ink`. Tapping opens the existing two-item dropdown (`Add a folder` / `Add a single file`) — keep `AddMenu` as-is, restyled to this trigger. Disabled (alpha 0.4, no ripple) while `busy`.
2. Resume card (only when a book has a saved position; otherwise omit the whole block and start the list here): `padding(horizontal = 22, bottom = 24)`, a `Row` with `gap 16`:
   - Cover: 132×132dp, radius 6dp, `ContentScale.Crop` (existing `BookCover`, new size constant).
   - Right column, `SpaceBetween`, fills height:
     - "KEEP LISTENING" — `labelCaps`, `TextQuaternary`.
     - Book title — `titleResume`, `Ink`, max 2 lines, ellipsis.
     - "4h 20m left" — `monoMeta`, `TextSecondary`.
     - Bottom row, `gap 14`, centered: 62dp circle `Ink` with a 26dp play triangle in `OnInk` (offset 3dp right for optical centering); then a `weight(1f)` progress bar, 5dp tall, radius 3dp, `Track` background, `Ink` fill at the book's fraction.
3. Book list: `LazyColumn`, `contentPadding` horizontal 22dp, item vertical padding 12dp, `gap 4` between items, **no dividers**.
   - Each row: `Row`, `gap 16`, centered.
     - Cover 64×64dp, radius 6dp.
     - Column, `gap 4`: title — `titleRow`, `Ink`, max 2 lines; metadata — `monoMeta`, `TextTertiary`, formatted `"%d ch · %s"` (chapter count · total duration, e.g. `18 ch · 8h 40m`).
   - Unavailable book: thumb background `ErrorTint` with a centered 22dp outlined warning icon in `Error`; second line becomes "Source unavailable — tap to relink" in Archivo 13sp/600, `Error`. (Copy change from today's plain "Source unavailable" — add a new string.)
   - Row click → player. Long-press → remove sheet. Keep `combinedClickable`.
   - `busy` still shows a `LinearProgressIndicator` full-width; place it directly under the header row, `Ink` indicator on `Track`.

**Exact copy used:** `Library` (screen title — a change from today's "Audiobooks"; the app name stays "Audiobooks"), `KEEP LISTENING`, `4h 20m left`, `18 ch · 8h 40m`, `Source unavailable — tap to relink`.

### 2. Player
**Purpose:** listen; scrub; jump; adjust speed.

**Layout** (`Column`, fills screen, no `TopAppBar`):
1. **Cover, full bleed.** `Box` of `width = maxWidth`, `height = maxWidth` (square), `ContentScale.Crop`, no radius, drawn edge-to-edge **behind the status bar** — the screen goes edge-to-edge (`enableEdgeToEdge`, no top window inset consumed by this element).
   - Overlay 1: top scrim, `Brush.verticalGradient(0f to Ink@45%, 0.34f to Transparent)`, non-interactive.
   - Overlay 2: back button, 44dp circle of `Ink@42%`, centered 24dp left-chevron in `OnInk`, positioned 16dp from the left edge and just below the status-bar inset (58dp from the top of the cover in the mock). This is the only navigation affordance — the "Back to library" text button at the bottom of today's screen is removed.
   - Status-bar icons need light content over the cover: set the appearance so system icons are light while this screen is shown — in both themes, since the scrim handles the contrast.
2. **Content column**, `weight(1f)`, `padding(start = 22, end = 22, top = 24)`:
   - Book title — `titlePlayer`, `Ink`, max 2 lines. Falls back to the existing `player_unknown_book` string.
   - 8dp gap. Chapter line — `monoCaps`, `TextTertiary`, format `"CH %1$d/%2$d — %3$s"` (number/total — chapter title, uppercased), e.g. `CH 12/26 — A SHIP'S COMPANY`. When `chapterCount == 0`, show only the elapsed/total, no chapter line.
   - 22dp gap. **Scrubber** (book-wide, same semantics as today's `BookScrubber` — drag updates the label only, seek commits on release):
     - Track 8dp tall, radius 4dp, `Track`; active fill `Ink`; thumb 20dp circle `Ink` with a 4dp `Surface` ring.
     - 10dp gap, then a `SpaceBetween` row: elapsed `monoTime` in `Ink`; remaining `monoTime` in `TextTertiary`, prefixed with a **minus sign** (`−6:33:05`). Existing `formatTime` is correct; add the `−`.
   - **Transport row**, vertically centered in the remaining space, `SpaceBetween`, `fillMaxWidth`:
     | Control | Box | Icon | Color |
     | --- | --- | --- | --- |
     | −1m | 52dp | 32dp double-chevron left | `InkMuted` |
     | −10s | 60dp | 40dp single chevron left | `Ink` |
     | Play/Pause | 104dp circle, `Ink` fill | 40dp triangle / two 4×16dp bars, radius 1dp | `OnInk` |
     | +10s | 60dp | 40dp single chevron right | `Ink` |
     | +1m | 52dp | 32dp double-chevron right | `InkMuted` |
     All icons: 2dp stroke, round caps and joins, no container, no ripple background (a subtle bounded ripple is fine).
   - **Caption row** directly under the transport, same widths so captions sit under their control: `1M`, `10S`, (empty under play), `10S`, `1M` — `monoMicro`, `TextQuaternary`. These replace the old text-labelled buttons; they are labels, not tap targets. Keep the icons' `contentDescription` for a11y ("Back one minute", etc.).
3. **Footer**, `flex: none`, 1dp top hairline `Track`, two equal halves split by a 1dp vertical hairline. Each half is a 48dp+ tappable column, `padding(vertical = 22)`, centered, `gap 4`:
   - Left: "Chapters" — `labelAction`, `Ink`; under it "12/26" — `monoMicro`, `TextQuaternary`. Opens the chapters sheet.
   - Right: "1.25×" — JetBrains Mono 17sp/500, `Ink`; under it "SPEED" — `monoMicro`, `TextQuaternary`. Opens the same sheet, scrolled to the speed row. Format with the existing `formatSpeed`, followed by `×` (U+00D7, not the letter x).
4. 20dp bottom spacer above the navigation-bar inset.

**Connecting / not-connected state:** keep today's behavior but show it in place — cover area renders the placeholder, title area shows `player_connecting` in `bodyEmpty`/`TextSecondary`, controls disabled at alpha 0.4. Do not center a lone "Connecting…" on an empty screen.

**Notification permission:** unchanged. Still requested on the first play tap only, and playback proceeds either way.

### 3. Chapters + speed sheet (inline)
**Purpose:** jump to a chapter, change speed, without leaving playback.

A `ModalBottomSheet` at ~92% height (or a full-height sheet — it should read as covering the player, with the mini-player at top). Structure:
1. Mini-player header, `padding(start = 22, end = 22, top = 6, bottom = 18)`, 1dp bottom hairline, `Row`, `gap 14`, centered:
   - 26dp collapse chevron (points up), `Ink`, 2dp stroke — dismisses the sheet.
   - Column, `gap 2`: "Chapters" — Archivo 19sp/700, −0.02em, `Ink`; "THE WAGER · 26 TOTAL" — `monoMeta` uppercase, `TextTertiary`.
   - 52dp circle `Ink`, 20dp play/pause in `OnInk` — playback stays controllable from here.
2. Speed row: `padding(22)`, 1dp bottom hairline, horizontally scrollable `Row`, `gap 10`. Each stop is a chip: `padding(horizontal 15, vertical 11)`, radius 6dp, JetBrains Mono 15sp. Unselected `Fill` / `InkMuted`; selected `Ink` / `OnInk`. All 14 existing `SPEED_STOPS` (0.75 → 3.0) in order, labelled by `formatSpeed` (`1.0`, `1.25`, `1.75`, `2.0` — no `×` on the chips). Scroll the selected chip into view on open. This replaces the dropdown menu.
3. Chapter list: `LazyColumn`, one row per chapter, `padding(horizontal = 22, vertical = 16)`, `gap 14`:
   - Number — `monoMeta`, `TextQuaternary`, fixed 26dp column, right-aligned digits.
   - Title — `bodyLarge`, `InkMuted`, `weight(1f)`, 1 line + ellipsis.
   - Duration — `monoMeta`, `TextQuaternary`.
   - **Current chapter row:** full-width `Ink` background (no radius, bleeds to both edges), vertical padding 18dp; title becomes `titleRow` in `OnInk`; duration becomes the time **remaining in the chapter**, in `#CFC7BF`; number stays `TextQuaternary`.
   - Tap → `seekToAbsolute` at that chapter's start, sheet closes.
   - Open scrolled so the current chapter is the second visible row.

### 4. Empty library
**Purpose:** get the first book in.

`Column`, centered vertically, `padding(horizontal = 30, bottom = 60)`, `gap 18`, under the same "Library" header:
- "Nothing here yet." — `displayEmpty`, `Ink`.
- Body — `bodyEmpty`, `TextSecondary`: "Point the app at a folder of audio files or a single .m4b, and it appears here with its chapters."
- 8dp extra gap, then two full-width buttons, `gap 12`, each `padding(20)`, radius 10dp, `Row` with `gap 14`, 24dp leading icon (2dp stroke):
  - Primary: `Ink` background, `OnInk` text/icon, folder icon, "Choose a folder" — `bodyLarge` at 600.
  - Secondary: `Fill` background, `Ink` text/icon, document icon, "Choose a single .m4b".
- 6dp gap, then "STAYS ON THIS DEVICE · NO ACCOUNT" — `monoMicro`, `TextQuaternary`.

These two buttons launch the same `OpenDocumentTree` / `OpenPersistableDocument` contracts the `AddMenu` uses. Copy replaces `library_empty_title` / `library_empty_body`.

### 5. Remove book
**Purpose:** confirm removal, and make clear files are not deleted.

Not an `AlertDialog` — a bottom sheet, so the buttons are thumb-reachable. Scrim `Ink@50%`. Sheet: `Surface` background, top corners 22dp, `padding(start = 24, end = 24, top = 28, bottom = 30)`, `gap 12`:
- "Remove Piranesi?" — `titleResume`, `Ink`. Format `"Remove %1$s?"` with the book title, unquoted; ellipsize a long title at 2 lines.
- Body — `bodyDialog`, `TextSecondary`: "Removes the book from your library. Your files stay exactly where they are."
- 12dp extra gap, then two stacked full-width buttons, `gap 10`, each `padding(18)`, radius 10dp, centered `bodyLarge` at 600:
  - Destructive first: `Error` background, white text, "Remove from library".
  - Then: `Fill` background, `Ink` text, "Keep it".

Copy replaces `library_remove_title` / `library_remove_body` / `library_remove_confirm` / `library_remove_cancel`.

---

## Interactions & Behavior
- **Library row tap** → player (existing nav route, unchanged). **Long-press** → remove sheet.
- **Resume card play** → starts/pauses the in-progress book without navigating; tapping the card body opens the player.
- **Scrubber**: drag updates the displayed timestamps only; `seekToAbsolute` fires on release. Unchanged from `BookScrubber`.
- **Seek buttons**: ±10 000 ms and ±60 000 ms, unchanged.
- **Chapter jump**: sheet row tap seeks to the chapter start and closes the sheet.
- **Speed chip tap**: applies immediately, chip selection animates (150ms color fade), sheet stays open, value persists per book via the existing `SpeedPreferences`.
- **Sheets**: standard Material bottom-sheet enter/exit. No custom animation.
- **Play/pause**: icon swaps with a 120ms crossfade; no size change.
- **Pressed states**: icon-only controls get a bounded ripple; filled buttons darken ~8%; list rows use the default ripple.
- **Errors**: snackbars stay as they are, hosted by the `Scaffold`.
- **Responsive**: the cover is always `width × width`, so on a short device the content column may need `verticalScroll` — the transport row must never be pushed off-screen; if height is tight, shrink the cover to at most 46% of screen height and keep it square.

## State Management
No new state beyond what the ViewModels already expose. What the redesign additionally needs:
- **Library**: a resume target (book id, title, cover, fraction complete, remaining time) and each book's total duration for the row metadata. Chapter counts already exist; total duration and saved position are in the DB but not currently surfaced on `LibraryBook` — add them to the query rather than computing per row.
- **Player**: a chapter list (number, title, duration, start offset) for the sheet, and remaining-in-chapter for the current row. `BookTimeline` / `ChapterDurations` already have this; expose it on the player UI state.
- **Sheet UI state**: one `sheetVisible` flag plus which section to scroll to (`chapters` | `speed`) — local `remember`, not ViewModel state.

## Assets
- **Fonts**: Archivo and JetBrains Mono (both OFL) — download the static weights listed above into `res/font/` and build the `FontFamily`s in `Type.kt`. No runtime font fetching.
- **Icons**: all icons in the reference are simple strokes available in `androidx.compose.material.icons` — chevrons (single/double), plus, play, pause, folder, description, warning, more-vert, list. Use the Material icon set, not custom vectors. Sizes and stroke weights above are what matters.
- **Cover placeholder**: keep the existing `res/drawable/ic_cover_placeholder.xml`, on `CoverPlaceholder1` / `CoverPlaceholder2` tints for list thumbs.
- No images ship with this design — every cover in the reference is a placeholder the real artwork replaces.

## Files
- `Audiobooks Redesign.dc.html` — the design reference. Build **`2a` (dark)** and **`1b` (light)**; they are the same design in two palettes.
- `image-slot.js`, `support.js` — runtime files the reference HTML needs to open in a browser. Not part of the design.

Source screens this replaces, in the app: `app/src/main/java/com/brandonmiller/audiobookplayer/ui/library/LibraryScreen.kt`, `ui/player/PlayerScreen.kt`, `ui/BookCover.kt` (add the 132dp and 64dp size constants), `ui/theme/Color.kt`, `ui/theme/Type.kt`, `res/values/strings.xml`.
