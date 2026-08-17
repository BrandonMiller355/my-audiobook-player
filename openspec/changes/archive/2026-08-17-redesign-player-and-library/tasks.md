## 1. Theme foundation

- [x] 1.1 Bundle Archivo (400/500/600/700) and JetBrains Mono (400/500) as static TTFs in `res/font/`, and ship both OFL license texts alongside them
- [x] 1.2 Build `Archivo` and `JetBrainsMono` `FontFamily`s in `Type.kt` and define the 14 named styles from the handoff's type table, keeping `AudiobooksTypography` populated so any Material component still picks up sensible defaults
- [x] 1.3 Add every light and dark token from the handoff to `Color.kt` as named vals
- [x] 1.4 Map the tokens with a Material role onto `lightColorScheme`/`darkColorScheme`, and provide the rest through an `AudiobookColors` `CompositionLocal` from `AudiobooksTheme` (design D1)
- [x] 1.5 Build and run — the app must still work throughout, now in the new palette and faces, with the old layouts intact

## 2. Shared drawing and formatting

- [x] 2.1 Add `ui/Icons.kt`: chevron, double chevron, plus, play triangle, pause bars, folder, document, warning, and collapse chevron, drawn on a 24dp reference grid with the handoff's stroke weights and round caps (design D2)
- [x] 2.2 Add `ui/Format.kt` and move `formatTime`/`formatSpeed` out of `PlayerScreen.kt`, since the Library now needs both; add the `8h 40m` duration form and the `4h 20m left` remaining form
- [x] 2.3 Unit-test the formatters: hours and sub-hour timestamps, a zero duration, a duration that rounds across a minute boundary, and every speed stop's label
      — `ui/FormatTest.kt`. `formatSpeed` changed while writing these: it now keeps at least one
      decimal (`1.0`, not `1`), because the chips are a monospaced row read at a glance and `1`
      beside `1.25` is both narrower than its neighbors and briefly ambiguous. The mock shows
      `1.0`/`2.0`, so this follows the drawing rather than the prose beside it.
- [x] 2.4 Give `BookCover` the 132dp resume and 64dp row sizes with a 6dp radius, plus the full-bleed no-radius Player variant, and the unavailable-book thumb treatment

## 3. Duration and progress in the data layer

- [x] 3.1 Add `updateChapterEnd` to `LibraryDao` and write each resolved folder-chapter duration to `chapters.endPositionMs` from `PlayerViewModel`'s background resolution pass (design D3)
- [x] 3.2 Extend `LibraryBook` with total duration, absolute saved position, and last-played time; extend `observeLibrary` with the all-or-nothing duration expression and the absolute-position subquery (design D3, D4)
- [x] 3.3 Unit-test the absolute-position expression against both book shapes: an `.m4b` at media item 0, a folder book part-way through its fourth chapter, a book with no saved position, and a folder book with unresolved chapters
      — `data/LibraryQueryTest.kt`, against a real in-memory Room database. Also covers a stored
      duration appearing once resolved, and `updateChapterEnd` refusing to overwrite an `.m4b`'s
      exact parsed boundary.
- [x] 3.4 Build and run — the library still lists correctly; nothing displays the new fields yet

## 4. Library screen

- [x] 4.1 Replace the `TopAppBar` with the scrolling "Library" header and the 44dp add circle, keeping `AddMenu`'s two items and its disabled-while-busy behavior
- [x] 4.2 Rebuild the book row: 64dp cover, 19sp title, `18 ch · 8h 40m` metadata, no dividers, and the unavailable treatment with its new copy; keep `combinedClickable` for tap and long-press
- [x] 4.3 Rebuild the empty state: headline, body, two full-width picker buttons wired straight to the two contracts, and the privacy note
- [x] 4.4 Replace the remove `AlertDialog` with the bottom sheet, and update its four strings
- [x] 4.5 Extract `MediaController.loadBook(...)` into `playback/` from `PlayerViewModel.loadBook`, and have `PlayerViewModel` use it — behavior unchanged (design D5)
- [x] 4.6 Connect a `MediaController` in `LibraryViewModel`, expose the resume target and whether it is playing, and add the play/pause action that loads the book only when the session is holding something else
- [x] 4.7 Add the resume card and wire its play control and its body tap
      — two corrections found on device. The right column takes `heightIn(min = 132.dp)` rather
      than a fixed height: at exactly the cover's height, a title that wraps to two lines left the
      play control under 62dp and the Column squeezed the circle into a pill. And the card takes
      `combinedClickable` with a long-press, because a library holding one book presents that book
      here and nowhere else — without it, that book could not be removed at all.
- [x] 4.8 Build and run — add a book, resume from the card without navigating, long-press to remove, and confirm the empty state's two buttons each open their picker
      — emulator: resume-from-card verified (playback starts, `mCurrentFocus` stays on
      `MainActivity`), pause from the same control, long-press → remove sheet, "Keep it" leaves the
      book. **Not covered:** adding a book through either picker, and the empty state, which the
      seeded sample means is never reached on a fresh install. See §8.4.

## 5. Player screen

- [x] 5.1 Draw the full-bleed square cover with its top scrim, the dark-only base fade, and the 44dp back button positioned below the status-bar inset; drop the "Back to library" text button (design D9 for the short-screen cap)
- [x] 5.2 Force light status-bar icons while the Player is shown and restore the theme's own appearance on leaving (design D8)
- [x] 5.3 Lay out the title and the `CH 12/26 — A SHIP'S COMPANY` chapter line, omitting the chapter line when the book has no chapters
- [x] 5.4 Replace the `Slider` with the drawn scrubber, keeping drag-updates-label/seek-on-release and adding the progress and set-progress semantics (design D6)
      — the travel is inset by the thumb's radius at both ends, or half the thumb hangs off the
      control at 0% and 100%; width is read with `onSizeChanged` rather than assigned during draw.
- [x] 5.5 Build the transport row and its caption row: 104dp play, 60dp ±10s, 52dp ±1m, icon-only, with `contentDescription` on each control and the captions as labels rather than targets
- [x] 5.6 Build the two-half footer with its hairlines, showing the chapter position and the current speed
- [x] 5.7 Render the not-yet-connected state in place — placeholder cover, connecting text, controls at reduced opacity — rather than as a lone centered line
- [x] 5.8 Build and run — play, pause, all four seek amounts, scrub across a chapter boundary, and confirm the notification permission is still requested on the first play tap only
      — emulator: play and pause confirmed against `dumpsys media_session`; all four seek amounts
      exact (+10s → +10000ms, +1m → +60000ms, −1m from 41s clamping to the book start).
      **Not covered:** dragging the scrubber across a chapter boundary, and the notification
      permission prompt, which `adb install -g` pre-grants. See §8.4.

## 6. Chapters and speed sheet

- [x] 6.1 Expose the chapter list (number, title, duration, start offset) and remaining-in-chapter on `PlayerUiState`, derived from the `BookTimeline` the ViewModel already builds
- [x] 6.2 Add `ui/player/ChaptersSheet.kt` with the mini-player header and its own play/pause
- [x] 6.3 Add the horizontally scrollable speed chip row over all 14 stops, selection applying immediately without dismissing, scrolled to the selected chip on open (design D7)
      — a `LazyRow`, so the chip to scroll to is named by index. The chips differ in width (`0.9`
      against `0.75`), so the computed scroll offset the first attempt used was a guess.
- [x] 6.4 Add the chapter list with the inverted current row showing time remaining in that chapter, tap-to-seek closing the sheet, opened with the current chapter as the second visible row
- [x] 6.5 Wire both footer halves to open the sheet at their respective section, and delete the previous-/next-chapter buttons and the speed dropdown from the Player
- [x] 6.6 Build and run — jump between chapters from the sheet, change speed from it twice in a row, and confirm playback keeps running throughout
      — emulator: 1.0 → 1.25 applied immediately with the sheet staying open and the chip row not
      jumping under the tap; chapter 3 selected, sheet closed, position moved to 17:26 and the
      footer read `3/5`; the speed survived a process restart at 1.25.

## 7. Copy

- [x] 7.1 Update `strings.xml`: the `Library` title, `KEEP LISTENING`, the row metadata format, the relink copy, the empty-state pair, the four removal strings, the chapter line format, and the seek captions
- [x] 7.2 Sweep every new string for American spellings and for the `×` (U+00D7) in the speed label rather than the letter x

## 8. Verification

- [x] 8.1 Run the unit test suite and a debug `assembleDebug`; the forbidden-permission check must still pass with the fonts and licenses added
      — 124 tests, 0 failures; `verifyDebugPermissions` passes.
- [x] 8.2 Emulator pass against the bundled sample and a synthetic chaptered `.m4b`: play, pause, ±10s, ±60s, chapter jump from the sheet, scrub across a boundary, speed change, and resume after killing the process (PRD §25 "M4B audiobook" and "Persistence")
      — run against the bundled sample (5 chapters) on an API 36 emulator at 1080×2400/440dpi
      ≈ 392dp, which is within 2dp of the design's 390dp frames. Every item above covered except
      scrubbing across a boundary. No crash or app-level exception in logcat across the whole
      session.
- [x] 8.3 Emulator pass in both light and dark: every screen, the sheet, and the remove confirmation, checking that only color differs between them
      — library, resume card, player, chapters sheet, and remove sheet in both. Dark's two
      dark-only details are visible and correct: the cover base fade at the artwork's bottom edge,
      and the scrubber thumb's ring in the surface color rather than white.
- [x] 8.4 **Owner, on device, with a real folder book** — the emulator cannot cover this and the repo has no folder-book fixture: add a folder, confirm the row shows its chapter count alone before first open and its total duration after, resume it from the library card without navigating, and confirm next/previous still work from Bluetooth and the lock screen (PRD §25 "Folder audiobook")
      — also covering the four items the emulator pass could not: adding a book through either
      picker, the empty state's two buttons, dragging the scrubber across a chapter boundary, and
      the notification permission being requested on the first play tap only.
      **Owner confirmed on device.**
- [x] 8.5 **Owner, on device** — confirm the redesigned controls are actually hittable while walking, which is the change's whole premise and the one thing no automated check can answer (PRD §21)
      **Owner confirmed on device.**
