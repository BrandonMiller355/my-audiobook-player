## 1. Verify the approach before building on it

Done. Findings are recorded in design.md under "Spike findings"; the subset holds and D1 stands.

- [x] 1.1 Confirm the owner has EPUB files for books already in the library, and note which ones
- [x] 1.2 Inspect one real EPUB by hand: list its ZIP entries, read `META-INF/container.xml`, the
      OPF spine, and the navigation document, and confirm no `META-INF/encryption.xml`
- [x] 1.3 Survey which HTML elements the real files actually use, and check that against the D2
      subset — record anything outside it and whether it matters
- [x] 1.4 Report the finding before continuing; if the subset is insufficient, stop and revisit D1

## 2. Parse an EPUB

A new `ebook/` package. Pure Kotlin over `java.util.zip` and `XmlPullParser` — no Android UI types,
so it is testable with plain JUnit. No new dependency (proposal).

- [x] 2.1 Build hand-made fixture EPUBs as ZIPs in the test source set: a minimal well-formed book,
      one with an EPUB 2 NCX (nested, with fragment targets) and one with an EPUB 3 `nav` and one
      with neither, one encrypted, one with malformed XHTML, one whose manifest ids run opposite its
      spine order, and one whose whole text is a single spine item (design R3). Keep them inline in
      tests — no source file whose only consumer is the test suite
- [x] 2.2 Read the container: locate `META-INF/container.xml`, resolve the OPF path, and detect
      `META-INF/encryption.xml` as the DRM signal (D12)
- [x] 2.3 Parse the OPF: spine order, resolving each `itemref` `idref` through the manifest to its
      `href`. The owner's files have descending ids against ascending hrefs, so anything that sorts
      by id renders the book backwards (spike finding 4) — cover this with a fixture whose ids run
      opposite its spine order
- [x] 2.4 Parse the navigation document into entries carrying a label, a target, and a depth. EPUB 2
      NCX is the primary path — the owner's library has no EPUB 3 `nav` at all (spike finding 1) —
      and `navPoint` nesting must be preserved as depth, not flattened (finding 2). Read EPUB 3
      `nav` as the secondary path
- [x] 2.5 Convert one XHTML spine item into an ordered list of blocks, each carrying its kind
      (paragraph, heading level, quote, list item, rule), its text, and inline emphasis spans.
      Implement exactly the D2 subset including `<u>`; unsupported elements contribute text without
      formatting; `<script>`/`<style>`/`<head>` are skipped. Collapse whitespace within a block
      (finding 5) and accumulate nested inline emphasis down the tree rather than per element
- [x] 2.6 Record each block's spine index and its character offset within that spine item's plain
      text — this is what the position model in D3 anchors to — and record any `id` attribute seen,
      so table-of-contents fragments can resolve to a block (finding 3)
- [x] 2.7 Assemble the whole book: parse every spine item into one flat block list (D11), and map
      each navigation entry to a block index, using its fragment where it has one and the first
      block of the document where it does not
- [x] 2.8 Make every failure a typed result rather than an exception: not a ZIP, not an EPUB,
      encrypted, no spine, unreadable
- [x] 2.9 Unit-test the above against the fixtures, including the malformed-XHTML case rendering
      what it can without throwing

## 3. Persist the link and the reading position

- [x] 3.1 Add `ebookUri`, `ebookSpineIndex`, and `ebookCharOffset` as nullable columns on
      `AudiobookEntity`, documenting why they are columns rather than a table (D4)
- [x] 3.2 Bump the Room version to 4 and add `MIGRATION_3_4` — three additive `ALTER TABLE`
      statements, no backfill, in the same shape as `MIGRATION_2_3`
- [x] 3.3 Register the migration on the database and commit the exported `app/schemas` v4 file
- [x] 3.4 Add DAO methods to read the link and position for one book, set the link (clearing the
      position), clear the link, and update the position
- [x] 3.5 Test the migration: a v3 database with existing books opens at v4 with the three columns
      null and every existing row intact

## 4. Link an ebook from the Player

- [x] 4.1 Add an EPUB MIME array to `OpenPersistableDocument` alongside `AUDIO_MIME_TYPES`, loose
      for the same reason (D13), and note the reason in the existing KDoc
- [x] 4.2 Take the persistable read grant on pick, reusing the pattern in `LibraryViewModel`
- [x] 4.3 Validate the picked file by reading it — a ZIP whose `mimetype` entry says
      `application/epub+zip` — and reject non-EPUB, encrypted, and corrupt files with distinct
      readable messages
- [x] 4.4 Draw the ebook icon in `ui/Icons.kt` in both states, on the same 24 grid and 2-unit stroke
      as the existing set (D15)
- [x] 4.5 Place it as a 44dp disc top-right on the Player's cover, mirroring the back button's
      treatment on the same scrim, and wire the unlinked state to the picker and the linked state to
      the reader route
- [x] 4.6 Release the old grant when an ebook is replaced or unlinked, and clear the stored reading
      position on replace
- [x] 4.7 Add a linked flag to `LibraryBook` and the library query, and show the linked-state glyph
      on rows that have one — indicator only, the row still opens the Player (D16)

## 5. The reader screen

- [x] 5.1 Add `Routes.READER = "reader/{bookId}"` to `AudiobooksApp.kt` (D5)
- [x] 5.2 Create `ReaderViewModel`: load the book row, parse the EPUB off the main thread, expose
      loading / ready / failed states, and hold the block list and navigation entries
- [x] 5.3 Create `ReaderScreen` rendering the block list in one `LazyColumn`, building each block's
      `AnnotatedString` per item so only visible blocks are materialized (D11)
- [x] 5.4 Style the reader: pure black, white text, reader-local constants rather than additions to
      `AudiobookColors`, with a comment recording why (D6)
- [x] 5.5 Set status-bar and navigation-bar appearance on entry and restore on exit, following the
      `DisposableEffect` shape of `LightStatusBarIcons` in `PlayerScreen.kt`
- [x] 5.6 Hold `FLAG_KEEP_SCREEN_ON` for the reader's lifetime and clear it on exit (D14)
- [x] 5.7 Restore the reading position on open: resolve `(spineIndex, charOffset)` to a block index
      and `scrollToItem` to it
- [x] 5.8 Save the reading position when scrolling settles, not per frame
- [x] 5.9 Render the unavailable states — file gone, grant lost, parse failed — as a readable
      message with an offer to link a different ebook

## 6. Reader controls

- [x] 6.1 Implement tap-to-reveal: a center-band tap toggles the chrome, it auto-hides after a few
      seconds, and it is visible on entry so the gesture is discoverable (D7)
- [x] 6.2 Make sure a scroll is never interpreted as a tap
- [x] 6.3 Add flip-back to the Player, and confirm hardware back does the same rather than returning
      to the Library
- [x] 6.4 Add the table of contents as a sheet, mirroring `ChaptersSheet.kt`, with entries jumping
      via `scrollToItem` and indented by their depth so parts and their chapters stay
      distinguishable; show a plain "no table of contents" state when the ebook has no navigation
      document
- [x] 6.5 Add play/pause, connected to the same `MediaController` the Player uses, reflecting state
      changed from the notification or a Bluetooth control (D8 — flag to the owner that this control
      was not explicitly requested and is cheap to cut)
- [x] 6.6 Add change-ebook and unlink to the chrome (D15)
- [x] 6.7 Add content descriptions to every control, matching the convention in `ui/Icons.kt`

## 7. Reading preferences

- [x] 7.1 Add a reading-preferences DataStore beside `SpeedPreferences.kt` holding text size, line
      spacing, typeface, and brightness, app-wide rather than per book (D10)
- [x] 7.2 Apply text size, line spacing, and typeface to the rendered blocks, with bounds that keep
      both extremes readable
- [x] 7.3 Set `WindowManager.LayoutParams.screenBrightness` on the reader's window and restore
      `BRIGHTNESS_OVERRIDE_NONE` on exit (D9)
- [x] 7.4 Add the settings controls to the revealed chrome
- [x] 7.5 Confirm the reading position holds across a text size, line spacing, and typeface change —
      the reason D3 chose a character offset over a pixel one

## 8. Verify

- [x] 8.1 Run the unit tests and `assembleDebug`, and confirm `verifyDebugPermissions` still passes
- [x] 8.2 On the emulator or device: link an ebook, read, leave, reopen, and confirm the position
      returns
- [x] 8.3 Confirm audio keeps playing across the flip in both directions, and that entering the
      reader while paused does not start it
- [x] 8.4 Confirm the reading position and the playback position move independently
- [x] 8.5 Force-stop and reopen: link, position, and reading preferences all survive
- [x] 8.6 Exercise the failure paths: delete the linked EPUB, pick a non-EPUB, pick a DRM-protected
      EPUB, and confirm each gives a readable message and playback is unaffected
      — deleted file and non-EPUB verified on device. **The DRM case was not: no DRM-protected
      EPUB was available to test with.** It is covered by the `an encrypted epub is refused`
      unit test, and shares the message path the non-EPUB case exercised end to end.
- [x] 8.7 Check the reader in both light and dark system themes, and confirm the system bars are
      restored on the way out
- [x] 8.8 Open `The Hero of Ages.epub` on the device: confirm the chapters read in forward order,
      the nested table of contents jumps correctly, paragraphs are not broken at the source's hard
      wraps, and the back-matter table degrades to readable text rather than a mess
      — forward order, nested contents, and hard wraps verified on device. **The back-matter table
      was checked by unit test rather than by scrolling to it in the app**; the parser test asserts
      one block per row with cells separated.
- [x] 8.9 Confirm the library row indicator appears for the linked book and not for the others
- [x] 8.10 Update the README's limitations section to cover the EPUB subset and the DRM refusal
- [x] 8.11 Sweep the change's prose for British spellings before committing
