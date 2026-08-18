## 1. Verify the assumptions before building on them

Mostly done. Findings are recorded in design.md under "Spike findings", measured against *The Hero of
Ages* and its EPUB in `D:\Claude\Mistborn\BookAndAudiobook\`. D1 and D3 hold; D13 was added
because of finding 5, and D3's structural filter was widened because of finding 2.

- [x] 1.1 Confirm a book in the library carries real chapter marks — *The Hero of Ages* has 90, cleanly
      titled `Prologue`, `Part One`–`Part Five`, `Chapter 1`–`Chapter 82`, `Epilogue`, `End Credits`
- [x] 1.2 Compare the audio chapter titles against the EPUB's table-of-contents labels — ordinal
      matching pairs all 84 numbered chapters plus prologue and epilogue with **no offset needed**, and
      the unmatched leftovers on both sides are exactly the non-narrated material
- [x] 1.3 Confirm characters predict narration time — median 13.4 chars/sec, p10–p90 spread 14.6%,
      worst-case mid-chapter drift about 1.6 minutes. This is the measurement the design rests on
- [x] 1.4 Validate the fallback offset scorer with all labels discarded — it recovered the correct
      alignment with a 73% margin (design.md D3)
- [x] 1.5 Confirm how the Compose version distinguishes a user-initiated scroll from a programmatic
      one — Compose UI 1.11.4 (BOM 2026.06.01) exposes `NestedScrollSource.UserInput` and
      `NestedScrollSource.SideEffect`; `Drag` and `Fling` are deprecated aliases of them. D4 uses
      `UserInput`
- [ ] 1.6 Spot-check one more book once tagged, to confirm the label conventions are consistent across
      the library rather than particular to this one

## 2. Schema

- [x] 2.1 Add the read-along enabled flag and the chapter offset to `AudiobookEntity`, both nullable,
      with the reasoning from D9 in the doc comment
- [x] 2.2 Write `MIGRATION_4_5` adding both columns, additive and non-destructive in the shape of
      `MIGRATION_3_4`; register it and bump the database to version 5
- [x] 2.3 Add a migration test asserting a version 4 database opens at version 5 with existing rows
      intact and both new columns null
- [x] 2.4 Add `LibraryDao` reads and writes for the flag and the offset

## 3. The map

A pure-Kotlin file alongside `BookTimeline`, with no Android types, so it is testable with plain
JUnit over synthetic inputs the way `BookTimeline` already is (D1, D2).

- [x] 3.1 Compute the ebook's cumulative character prefix sums once, when the ebook parses, as an
      `IntArray` over `Ebook.blocks`. Confirm the cost is negligible on a real ~900,000-character book
      (PRD §23)
- [x] 3.2 Define the anchor type — one absolute millisecond paired with one absolute character offset
      — and hold the map as a sorted list of them, not as per-chapter state (D2)
- [x] 3.3 Implement milliseconds → characters: binary search the anchors, interpolate linearly within
      the segment, clamp at both ends
- [x] 3.4 Implement characters → milliseconds as the same search and the same interpolation inverted,
      so the two cannot disagree (D1)
- [x] 3.5 Add round-trip tests: every anchor maps exactly in both directions, midpoints interpolate as
      expected, positions before the first and after the last anchor clamp rather than extrapolate,
      and a value mapped forward and back returns to itself
- [x] 3.6 Add the rate guard from D13: compute the book's median chars/sec across segments, and advance
      at that median through any segment whose implied rate is wildly out of line rather than
      interpolating to the far anchor. Test it against the epilogue case from spike finding 5 — roughly
      7,000 non-narrated characters inside a 2.2-minute segment
- [x] 3.7 Add tests for the degenerate shapes: a single anchor, two anchors, anchors with a zero-length
      segment between them, and an empty block list

## 4. Chapter matching

Also pure Kotlin and separately testable (D3).

- [x] 4.1 Derive each table-of-contents entry's block range from consecutive `NavEntry.blockIndex`
      values, and mark entries whose range is empty or trivially short as structural rather than
      chapters. Apply the same filter to the **audio** side — spike finding 2 found `Part One` present
      as a seven-second chapter mark
- [x] 4.2 Write the label normalizer: lowercase, strip punctuation and leading zeros, extract an
      ordinal from arabic numerals, roman numerals, or spelled-out numbers, and recognize `prologue`,
      `epilogue`, `prelude`, `interlude`, and `appendix`
- [x] 4.3 Match audio chapters to entries on the extracted ordinal, leaving unmatched entries on either
      side contributing nothing
- [x] 4.4 Fall back to matching in order when labels yield nothing usable
- [x] 4.5 Auto-detect the offset for the fallback path (D3): score every plausible offset by the
      coefficient of variation of implied chars/sec across all pairs, and take the lowest. Validated in
      1.4 — reproduce that result as a test using the real chapter durations and section lengths
- [x] 4.6 Apply the per-book manual offset on top of the detected one, as an override rather than as
      the only source of the number
- [x] 4.7 Convert the matching into the anchor list of section 3, using `BookTimeline.chapterSpans()`
      for the audio side and the prefix sums for the text side
- [x] 4.8 Test against the real label pairs recorded in 1.2, plus the cases in the spec: differing
      notation, named sections, front matter with no audio, a grouped table of contents, and an
      unmatched pair falling back to order

## 5. The reader follows the audio

Reversing `add-ebook-companion` design D8, which had the reader deliberately ignore playback position.

- [x] 5.1 Have `ReaderViewModel` follow playback position as well as play/pause, sampling at the modest
      rate D7 settles on rather than per frame
- [x] 5.2 Build the map when both the ebook and the book's chapters are available, and expose read-along
      as unavailable with a reason when either is missing (D10) — including a book whose audio is one
      chapter spanning the whole file
- [x] 5.3 Drive the scroll from the sampled position: glide toward the fractional target when it is on
      or near screen, jump with `scrollToItem` when it is far (D7)
- [ ] 5.4 Compute the fractional target to sub-block precision from `layoutInfo`, rather than to the
      first visible block (D6)
- [ ] 5.5 Open the reader at the audio position rather than the saved reading position while read-along
      is on, and stop writing a separate reading position while it is on (D11)
- [x] 5.x Gate auto-scroll on readAlongEnabled flag (implemented with toggle control)

## 6. The audio follows the reader

- [x] 6.1 Add the user-input gate from D4 — a `NestedScrollConnection` using `NestedScrollSource.UserInput`
      that marks the scroll as user-initiated. **This is the correctness-critical task in the change**
- [x] 6.2 Move the existing position-save hook behind the same gate, fixing the latent misfire it has
      today
- [x] 6.3 On settle with the gate set, map the reader's position back to milliseconds and seek through
      the `MediaController` the reader already holds
- [x] 6.4 Apply the dead zone: skip the seek when the implied change is under the threshold (D5)
- [x] 6.5 Leave the transport state alone across the seek — paused stays paused (D8)
- [ ] 6.6 Offer undo through the existing `SnackbarHost`, restoring the position captured before the
      seek (D5)
- [x] 6.7 Route table-of-contents jumps and search-result jumps through the same path, so one rule
      covers all three deliberate movements (D12)
- [ ] 6.8 Add a test that drives a synthetic auto-scroll sequence through the gate and asserts **no**
      seek is issued — the loop from D4 must be caught by a test, not by device testing

## 7. Reader controls

- [x] 7.1 Add the read-along toggle to the reader's chrome, reflecting the stored per-book state and
      defaulting on for a book that supports it
- [x] 7.2 Show read-along as unavailable with its reason rather than as an inert control (D10)
- [ ] 7.3 Add the chapter offset control, resolving the open question about where it lives — chrome row
      or settings sheet — and note the choice in design.md
- [x] 7.4 Add the strings for the toggle, the two unavailability reasons, the undo message, and the
      offset control

## 8. Specs and documentation

- [x] 8.1 Rewrite the Purpose prose at the top of `openspec/specs/ebook-reader/spec.md` when syncing.
      It currently states that no scrolling with the narration exists and that the absence "is a
      decision, not a gap" — the requirement deltas do not touch that paragraph and it becomes wrong
- [x] 8.2 Note in `handoffs/2026-08-10-ebook-audio-readalong.md` which tier was ultimately chosen and
      why, so the four-tier analysis is not re-litigated by a later session
- [ ] 8.3 Amend the PRD for §7 gaining the reader as a place a seek can originate

## 9. Verify on the device

Per the project's established practice, this is drivable from the shell: `uiautomator dump` for taps
and scroll state, `dumpsys media_session` for playback assertions.

- [ ] 9.1 With the book from 1.1, confirm the text scrolls with the narration and that the reader is at
      the start of a chapter's text when the audio reaches that chapter
- [ ] 9.2 Confirm no seek is issued while the reader is scrolling itself — watch the session position
      across several minutes of unattended following. This is the D4 failure, and it is the one that
      would ruin the feature silently
- [ ] 9.3 Confirm scrolling seeks the audio on settle, that a small adjustment does not, and that undo
      restores the previous position
- [ ] 9.4 Confirm a seek while paused repositions without starting playback
- [ ] 9.5 Confirm the measured drift holds in practice on chapter 51 or 37 (the two largest deviations,
      about 1.6 minutes at the midpoint), and on chapter 5 — at 44 minutes it is the longest segment in
      the book and therefore the worst case for accumulation
- [ ] 9.6 Confirm the epilogue behaves under D13's rate guard rather than racing through the back matter
- [ ] 9.7 Confirm a book with no chapter marks and an ebook with no table of contents each report
      read-along as unavailable while playing and reading normally
- [ ] 9.8 Re-examine whether following a search result should seek, now that there is device experience
      to argue from (design.md Open Questions)
