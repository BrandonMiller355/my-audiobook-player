## 1. Confirm the layout assumption before building on it

- [x] 1.1 On the device, open the reader for a read-along book and put a throwaway one-`Stepper`
      `ModalBottomSheet` on screen. Confirm the 33% anchor line and the text around it stay visible
      above it while the sheet is open (design D11, first risk).
- [x] 1.2 If the sheet obstructs the text, stop and revise D11 to a chrome-row control before
      continuing — that variant needs a hand-drawn `ui/Icons.kt` glyph and an `IconTooltip`, which
      changes the UI tasks in section 5.

## 2. Persistence

- [x] 2.1 Add `ReadAlongCorrectionEntity` to `Entities.kt`: `audiobookId`, `chapterIndex`, `audioMs`,
      `charOffset`, composite primary key on `(audiobookId, chapterIndex)`, `ON DELETE CASCADE` from
      `audiobooks`, index on `audiobookId` (design D9).
- [x] 2.2 Register the entity and bump `AudiobookDatabase` to version 8.
- [x] 2.3 Write `MIGRATION_7_8` as an additive `CREATE TABLE`, in the shape of `MIGRATION_6_7` rather
      than the table rebuild `MIGRATION_5_6` needed. Document why it is additive.
- [x] 2.4 Export the version 8 schema to `app/schemas/`.
- [x] 2.5 Write `Migration7To8Test`, pinning the DDL against the committed version 8 export and
      asserting existing rows in `audiobooks`, `chapters`, and `notes` survive — the failure mode is
      a migration that succeeds, stores rows, then fails schema validation on the next launch.
- [x] 2.6 Add `LibraryDao` methods: read a book's corrections, upsert one, delete a book's
      corrections.
- [x] 2.7 Write a DAO test covering upsert-replaces-existing for the same `(book, chapter)` and
      cascade-on-book-delete.

## 3. The map

- [x] 3.1 Add a marker to `ReadAlongAnchor` distinguishing an owner-entered anchor from a chapter
      boundary. Default it so every existing construction site is unchanged.
- [x] 3.2 Make `ReadAlongMap.isGuarded` skip a segment bounded by an owner-entered anchor (design D8),
      and update the doc comment on the guard to say why the exemption exists.
- [x] 3.3 Add `ReadAlongMapTest` cases: a correction that would trip the rate guard still shapes the
      correspondence; an uncorrected chapter implying an implausible rate is still guarded; both
      chapter boundaries remain exact with an owner anchor between them; `charsForMs` and
      `msForChars` still agree at the corrected point.

## 4. Applying a correction

- [x] 4.1 Load stored corrections in `ReaderViewModel.buildReadAlongMap` and merge them into the
      anchor list, sorted by `absoluteMs`.
- [x] 4.2 Clamp a correction's character side strictly inside its chapter's character range before it
      enters the anchor list, so the map's non-decreasing invariant cannot be violated by a stored
      row (design D6).
- [x] 4.3 Add the nudge action: freeze `T₀` at the first tap of a burst, accumulate the step on the
      character side against the *corrected* map, rebuild the map, and expose the accumulated
      correction in `ReaderUiState` for the stepper to display (design D5, D6).
- [x] 4.4 Commit the burst to the database after a debounce following the last tap, replacing any
      existing row for that chapter (design D7).
- [x] 4.5 Clear corrections in `changeEbook` and `unlinkEbook`, in the same transaction as the relink
      (design D10).
- [x] 4.6 Write tests for the nudge arithmetic as pure functions where possible: repeated steps
      accumulate, opposite steps cancel, the clamp holds at both chapter edges, and a second burst
      refines the first rather than restarting from the uncorrected line.

## 5. The control

- [x] 5.1 Add strings: the control's label, the two chevron actions framed as *move the text earlier /
      later*, and the help line. American English.
- [x] 5.2 Add the correction sheet to `ReaderChrome.kt` reusing the existing private `Stepper`, with
      the accumulated correction in the value slot (design D4).
- [x] 5.3 Add the menu entry, shown only when `readAlongMap != null`. The reader's menu is words
      rather than icons, so no new glyph is needed.
- [x] 5.4 Wire the sheet into `ReaderScreen`, including it in the `sheetOpen` flag that holds the
      chrome and the system bars open.

## 6. Verify on the device

- [x] 6.1 Confirm a nudge moves the page and does not seek the audio — the settle handler's
      `userScrolled` guard should already prevent it, since the glide's scrolling reports
      `SideEffect` rather than `UserInput` (design D3). Assert the audio position via
      `dumpsys media_session`.
- [x] 6.2 Confirm correcting while playing leaves it playing, and correcting while paused leaves it
      paused.
- [x] 6.3 Confirm the correction survives closing and reopening the book.
- [x] 6.4 Play through to the next chapter boundary and confirm it is still exact.
- [x] 6.5 Listen through the rest of a corrected chapter and record how far the correction tapers
      before the boundary (design D2). This is the measurement that decides whether the anchor-pair
      follow-up is worth building — write the number down in the change before archiving.
      *Computed from the committed anchors rather than listened through: 3.2 s of correction lost
      per minute, table in design D2. Settles the taper's shape, not the book's real drift shape —
      a listening session is still owed before the anchor-pair follow-up is decided.*

## 7. Documentation

- [x] 7.1 Amend PRD §3.1: strike `user-tapped "sync here" anchors` from the out-of-scope list
      following the §3.2 precedent, and fix the same sentence's stale claim that scrolling the page
      as the narrator advances is out of scope — it is built and described in §7.3 and §20.4.
- [x] 7.2 Add the correction control to §20.4's list of reader controls.
- [x] 7.3 Confirm no new dependency, permission, or manifest entry was added.

## 8. Found in use, after section 7

- [x] 8.1 A burst that crosses a chapter boundary lost the first chapter's write:
      `scheduleCorrectionCommit` cancelled the pending job unconditionally, leaving that correction
      applied to the map and visible on the page but never stored, so the next reload dropped it
      silently. The outgoing chapter is now written before the new delay starts, inside the same
      tracked job so `cancelPendingNudge` still covers a relink. No automated test: the behavior is
      coroutine ordering inside `ReaderViewModel`, which has no test harness in this project, and
      building one for this is out of proportion to the fix.
- [x] 8.2 Bound a correction to what its chapter can express (`expressibleCorrectionRange`), so a
      correction near a boundary cannot produce the out-of-ratio segment the D8 exemption stopped the
      guard containing. Confirmed by the owner rather than by reconstructing the delta. Design D8
      records why bounding beats withdrawing the exemption; the sheet says when the limit is reached.

## 9. Hold the correction (design D2, option B)

- [x] 9.1 Give each correction a second anchor: held flat at the full correction until a window
      before the chapter ends, then unwound over that window. Window is three times the correction's
      own size, clipped to the room available, which degenerates to the shipped single-anchor shape.
- [x] 9.2 Prove the unwind cannot run away: the sweep test now checks *every* consecutive segment in
      the chapter rather than just the correction anchor, since a held correction makes three
      segments and the unwind is the one most likely to leave the ratio.
- [x] 9.3 Fold the superseded single-anchor sweep test into that one rather than keeping both.
- [x] 9.4 Square the documents: D2 records the supersession and the evidence, and the non-goals in
      the proposal and design that ruled out holding the correction are rewritten rather than left
      contradicting the feature.
- [ ] 9.5 Confirm on the device that a correction made mid-chapter is still applied several minutes
      later, and that the boundary is still exact. **Blocked on the emulator, not on the work**: it
      wedged with `system_server` dead after 7 days uptime and load average 29, and an `adb reboot`
      left it offline with qemu alive but the guest not booting. Not force-restarted, because the
      owner's library and his restored playback position live in that AVD and a bad boot would want
      a wipe.

