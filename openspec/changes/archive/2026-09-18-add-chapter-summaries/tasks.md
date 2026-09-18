## 1. Parsing and matching

- [x] 1.1 Add `summaries/ChapterSummaryImport.kt` with a parser turning file text into ordered
      `(label, text)` entries: a marker is a line that, trimmed, is entirely `Chapter <number>` with
      an optional trailing colon or one of the named sections `normalizeChapterLabel` recognizes;
      text before the first marker is discarded; each entry's text is what follows up to the next
      marker, trimmed of surrounding blank lines (design D3). One file rather than a parser file and
      a matcher file — they are small and always used together.
- [x] 1.2 In the same file, add the matcher: normalize each entry label and each `ChapterEntity.title`
      through `normalizeChapterLabel`, pair on equal keys, consume duplicates in order the way
      `matchByLabel` does, drop `Unrecognized` on either side, and return the matched
      `chapterIndex → text` map plus the counts the import reports (design D4). Match against stored
      titles only — never against `chapterIndex`, which stringifies into a valid chapter number and
      mismatches silently.
- [x] 1.3 Add no positional fallback, and document in the file why this deliberately differs from
      `matchChapters` (design D4).
- [x] 1.4 Write `ChapterSummaryImportTest`: numbered entries; a trailing colon; a summary whose prose
      contains a line beginning "Chapter 4 was where..."; preamble before the first marker; a
      `Prologue` marker; CRLF line endings; an empty file and a file with no markers; an entry with
      an empty body; a book whose audio chapter 1 is `Prologue` and 2 is `Chapter 1` against a file
      numbered from 1; `Chapter IX` against `Chapter 9`; duplicate labels consumed in order; a
      single-chapter book matching nothing.

## 2. Persistence

- [x] 2.1 Add `ChapterSummaryEntity` to `Entities.kt`: `audiobookId`, `chapterIndex`, `text`,
      `prompted`, composite primary key on `(audiobookId, chapterIndex)`, `ON DELETE CASCADE` from
      `audiobooks`, index on `audiobookId` (design D2). Document why the chapter title is not
      denormalized here as it is on `NoteEntity`.
- [x] 2.2 Register the entity and bump `AudiobookDatabase` to version 9.
- [x] 2.3 Write `MIGRATION_8_9` as an additive `CREATE TABLE`, in the shape of `MIGRATION_7_8`.
- [x] 2.4 Export the version 9 schema to `app/schemas/`.
- [x] 2.5 Write `Migration8To9Test` against the committed version 9 export, asserting rows in
      `audiobooks`, `chapters`, `notes`, and `read_along_corrections` survive — a migration that
      stores rows and then fails schema validation on the next launch is the failure to catch.
- [x] 2.6 Add `LibraryDao` methods: read a book's summaries, delete a book's summaries, and one
      `@Transaction` that replaces them wholesale (design D5).
- [x] 2.7 Write `ChapterSummaryQueryTest` in the shape of `ReadAlongCorrectionQueryTest`: replace
      removes entries absent from the new set, cascade on book delete, and `prompted` surviving a
      read-modify-write that only sets the flag.

## 3. The import flow

- [x] 3.1 Add a `text/plain` picker to the Player using `ActivityResultContracts.OpenDocument()`
      directly — not `OpenPersistableDocument`, whose only purpose is the persistable grant this does
      not take (design D1).
- [x] 3.2 Add the import to `PlayerViewModel`: read the document's bytes once, parse, match against
      the book's stored chapters, replace in one transaction, and expose the matched and total counts.
      Keep the file unreferenced afterward.
- [x] 3.3 Handle an unreadable file and a file with no recognizable entry by reporting it and leaving
      existing summaries untouched. Decode as UTF-8, falling back rather than throwing on malformed
      bytes.
- [x] 3.4 Expose, per book, which chapter indices have a summary, so both chapter lists can show their
      controls without reading every summary's text.

## 4. The summary display

- [x] 4.1 Add `ui/SummarySheet.kt`: a `ModalBottomSheet` with the chapter's title and its scrollable
      text, dismissible, not editable (design D9). Top-level `ui` package because both the Player and
      the reader use it.
- [x] 4.2 Add `SummaryIcon` to `ui/Icons.kt` — ragged horizontal lines inside a rounded rectangle, on
      the same 24-unit grid and 2-unit round-capped stroke as the rest, distinguishable at row size
      from `DocumentIcon` (a page with a turned corner) and `ContentsIcon` (numbered lines, no frame)
      (design D11).
- [x] 4.3 Add strings for the sheet, the controls, and the import states. American English throughout.

## 5. The Player's chapter list

- [x] 5.1 Add the import section to `ChaptersSheet`, below the speed row and above the chapters: a
      state line reading "<matched> of <total> chapters" or that none are imported, and a text button
      reading Import or Replace (design D8). A text button, not an icon.
- [x] 5.2 Show `SummaryIcon` on each chapter row that has a summary, wrapped in `IconTooltip`, opening
      `SummarySheet`. No control on a row without one.
- [x] 5.3 Make the control's tap open the summary only — it must not select the chapter or move
      playback, which means keeping it clear of the row's own `clickable`.
- [x] 5.4 Check the control against the inverted current-chapter row, which draws on `colors.ink` with
      `onInk` content.

## 6. The reader's table of contents

- [x] 6.1 Expose, from `ReaderViewModel`, the nav-entry-to-audio-chapter pairing inverted out of the
      `matchChapters` result it already holds (design D10).
- [x] 6.2 Show `SummaryIcon` on a `ContentsSheet` entry whose paired chapter has a summary, wrapped in
      `IconTooltip`, opening the same `SummarySheet`. No pairing or no summary means no control.
- [x] 6.3 Confirm a book with no read-along support shows no controls and that `ContentsSheet` is
      otherwise unchanged.

## 7. The end-of-chapter prompt

- [x] 7.1 Add `playback/SummaryPrompt.kt`: a pure function over consecutive position samples returning
      the index of the chapter that just ended, or null. Arms only when playing, only with a previous
      sample for the same book, only when the index advanced by exactly one, and only when the
      absolute position advanced by no more than a few seconds (design D6).
- [x] 7.2 Write `SummaryPromptTest`: a normal crossing arms; a scrub across a boundary does not; a
      chapter-skip does not; the first sample after resuming does not; a backward move does not; a
      paused sample does not; a book change does not; the last chapter ending arms nothing.
- [x] 7.3 Wire it into `PlayerViewModel`'s existing position ticker, emitting a one-shot event only
      when that chapter has a summary and its row is not yet `prompted`.
- [x] 7.4 Show it in `PlayerScreen` on the existing `SnackbarHostState` with a Read action, in the
      shape of `ReaderScreen.kt:206`. Set `prompted` when it is shown, and open `SummarySheet` when
      the action is taken (design D7).
- [x] 7.5 Confirm nothing in this path calls pause, play, or seek — take the strongest form of this
      that the code allows rather than reading for it.

## 8. Verification on the device

- [x] 8.1 Import a file for the chaptered `.m4b` fixture and confirm the count matches what the file
      contains, then reopen the app and confirm the count and the summaries persist.
- [x] 8.2 Delete the imported file from the device and confirm the summaries are still there
      (design D1).
- [x] 8.3 Seek to a few seconds before a chapter boundary, let it play through, and confirm the
      prompt appears, dismisses itself, and that `dumpsys media_session` shows playback never paused.
- [x] 8.4 Cross the same boundary again and confirm no prompt; skip across a boundary with the chapter
      control and confirm no prompt; resume the book mid-chapter and confirm no prompt.
- [x] 8.5 Cross a boundary with the app backgrounded and confirm nothing interrupts the audio.
- [x] 8.6 Open a summary from a chapter ahead of the current position and confirm playback stays where
      it was.
- [x] 8.7 Import for one of the chapterless Mistborn rips and confirm the import reports nothing
      matched rather than guessing. **Done against a file whose entries match no chapter** rather
      than a chapterless rip — the emulator has no such book, and both reach the same branch. The
      chapterless case itself is covered by `ChapterSummaryImportTest`.
- [x] 8.8 Long-press both new icon controls and confirm the tooltips.

## 9. Documentation

- [x] 9.1 Add PRD §3.3 recording what this feature is and what it deliberately is not, in the shape of
      §3.1 and §3.2, drawing the out-of-scope list from `proposal.md`.
- [x] 9.2 Note in §3.3 that the summary file's text is stored rather than referenced, and why that
      does not contradict §16.

## 10. Markdown support (added after the owner supplied a real summary file)

- [x] 10.1 Probe the device's `MediaStore` for the MIME type a `.md` file actually carries, and add
      it to the picker's types alongside `text/plain` (design D12). It reports `text/markdown`.
- [x] 10.2 Classify a line as a marker, a structural heading, or prose, so a Markdown heading naming
      a chapter starts an entry while the document title and a "Part One" heading end one without
      starting another (design D12).
- [x] 10.3 Gate marker detection on shape before meaning: a label says "chapter", names a section, or
      is a single token. Without it `# Mistborn Book 3: …` normalizes to chapter three and the
      document title takes that chapter's slot (design D12). Share `NAMED_SECTIONS` rather than
      copying it.
- [x] 10.4 Add `summaries/InlineMarkdown.kt`, reducing inline emphasis to the `EmphasisSpan` list the
      reader already uses, and render it in `SummarySheet` the way `ReaderScreen.annotate` does.
      Store the owner's text unchanged.
- [x] 10.5 Extend `ChapterSummaryImportTest` for headings, structural headings, bold labels, and the
      title-with-a-number case; add `InlineMarkdownTest`.
- [x] 10.6 Verify on the device against the owner's own file and *The Hero of Ages*: 14 of 90
      chapters, Prologue on chapter index 0 and Chapters 1-13 on indices 2-14, skipping the
      "Part One" audio mark; bold rendered; the `.md` selectable while the `.m4b` and `.epub` are
      not.
- [x] 10.7 Verify the reader's table of contents on the same book — the gap section 6 could not close
      without a linked EPUB. Icons on the chapter entries, none on "PART ONE", and the sheet headed
      with the audio chapter's title rather than the ebook's bare ordinal.
