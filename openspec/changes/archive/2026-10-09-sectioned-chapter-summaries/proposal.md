## Why

The owner's summary file for The Brothers Karamazov imported nothing. It is written the way an
assistant writes summaries for a novel published in books: `## Book II: …` headings, `### Chapter 1: …`
under each, and `#### Summary` / `#### Analysis` / `#### Quotes` under every chapter. Two things broke:

- `#### Summary` names no chapter, so it was read as a structural heading and ended the entry. Every
  chapter's text was discarded and the file reported no entries at all.
- Each book numbers its chapters from one, and the audio titles them `Book 2 - Chapter 1`. Matching by
  chapter number alone cannot tell book one's chapter 1 from book two's.

## What Changes

- **Headings deeper than a chapter's own belong to it.** They are kept in the summary as a bold line;
  a heading at the chapter's level or above still ends it.
- **Entries record the section they sit under**: a numbered `Book` / `Part` / `Volume` heading, a
  named section such as `## Epilogue` with chapters beneath it, or a book named in the marker itself
  (`Book 2, Chapter 1`). A plain `Book II` line in a plain-text file is a section too.
- **Matching prefers book and chapter together**, reading `Book 2 - Chapter 1` and
  `Epilogue - Chapter 1` from the audio's titles.
- **No guessing across renumbering.** A file whose books restart at chapter one is not matched by
  number alone against titles that name no book. Parts over continuous numbering, and files with no
  books at all, match as before. A section named identically on every chapter carries no
  information and is ignored.

## Capabilities

### Modified Capabilities

- `chapter-summaries`: sub-headings inside an entry; entries and chapters qualified by book.

## Impact

- `summaries/ChapterSummaryImport.kt` (parsing and matching). No schema, UI, or storage change.
- Tests: `ChapterSummaryImportTest` additions, and `ChapterSummaryRealBookTest` against the owner's
  Karamazov file (`src/test/resources/summaries/brothers_karamazov.md`).
