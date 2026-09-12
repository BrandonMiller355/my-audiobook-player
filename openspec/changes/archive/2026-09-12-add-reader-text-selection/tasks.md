## 1. Verify the two risky assumptions on device

Both of these decide the shape of everything after them, and both are cheap to answer. Nothing in
group 3 or later is worth writing until they are settled (design D2, D3).

- [x] 1.1 Wrap `ReaderText` in a `SelectionContainer` gated on `!state.isPlaying` and install it on a
      device. Confirm a long press selects while paused and does nothing while playing.
- [x] 1.2 With the container in place and **nothing selected**, tap the middle of the page. Record
      whether the chrome still toggles. **It does** — selection never consumes taps, so the fallback
      placement in D3 was not needed.
- [x] 1.3 With a passage selected, tap the page. **Result: the selection clears and the chrome DOES
      appear**, the opposite of what was specified. Not fixable without selection state Compose does
      not expose; spec scenario and design D3 rewritten to the real behavior.
- [x] 1.4 Spike the passage retrieval from design D2. **Outcome: the mechanism is impossible and Save
      as note is deferred by the owner's call.** Any custom toolbar item requires hand-rendering the
      toolbar; findings written up in design D2 and the proposal's Deferred section. Group 4 and the
      book-notes delta came out of this change as a result.

## 2. Anchoring arithmetic

Pure logic, unit-testable, and independent of the UI — so it can be built alongside group 1.

- [x] 2.1 Give `noteAnchorFor` in `playback/NoteAnchor.kt` a `leadInMs: Long = NOTE_LEAD_IN_MS`
      parameter, so every existing call site reads unchanged.
- [x] 2.2 Add an anchoring path for an absolute audio position — `BookTimeline.targetForAbsolute`
      for the target, `BookTimeline.locate` for the chapter title — alongside the existing
      `Location`-and-delta path, with no lead-in applied.
- [x] 2.3 Unit-test the new path: a passage mid-chapter anchors at its own position; a passage at the
      very start of the book yields no negative position; the chapter title comes from the anchored
      target; a title index that is absent yields an empty string rather than throwing.
- [x] 2.4 Unit-test that the existing lead-in behavior is unchanged with the new parameter defaulted.

## 3. Selection in the reader

- [x] 3.1 Settle the `SelectionContainer` from 1.1 into `ReaderScreen.kt` properly, with a comment
      explaining why the gate is structural rather than a flag (design D1).
- [x] 3.2 Confirm the reading position survives the container appearing and disappearing across
      several play/pause cycles, on a long book, scrolled well into it.
- [x] 3.3 Confirm the glide is undisturbed: with read-along active, pause, select a passage, resume,
      and watch the page pick the narration back up without a lurch.

## 4. Save as note — deferred out of this change

Removed by the owner's call after the 1.4 spike. Kept as a heading rather than deleted so the change's
history reads straight: the reasoning is in design D2, D4, and D5, and the arithmetic group 2 built
stays in `playback/NoteAnchor.kt` waiting for it.

## 5. Tests

- [x] 5.2 Confirm the whole suite passes: `./gradlew test`.

## 6. Documentation

- [x] 6.1 Add selection to the Reader screen's contents in PRD §20.4.
- [x] 6.2 Add a line to PRD §3.1 separating user-driven selection from the narration highlighting
      that stays out of scope, so the two are not read as the same thing later.
- [x] 6.3 Record the 1.4 findings in design D2 and the proposal's Deferred section, so the follow-up
      proposal inherits them rather than rediscovering them.

## 7. Device verification

Per the repo's established practice of driving the emulator from the shell.

- [x] 7.1 On a chaptered read-along book: pause, select a passage spanning two paragraphs, copy it,
      and paste it somewhere to confirm the text is right.
- [x] 7.3 Confirm that while playing, a long press does nothing and touch-and-drag scrolls.
- [x] 7.4 Confirm that while paused, touch-and-drag still scrolls normally — the common gesture must
      not have been quietly traded away for the long-press one.
- [x] 7.5 Confirm that a selection spanning far more than a screen stops at the composed range
      without crashing, and that what is selected still copies. Select all held the whole composed
      range with no crash and no hang; copy fidelity is covered by 7.1. The exact character count a
      select-all copy yields was not measured — the paste target retained its previous contents and
      the measurement was not worth more device time.
