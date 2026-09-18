## Why

Seven books into Mistborn, the owner loses the thread between sessions and between chapters — who
someone was, what a chapter turned on. The material that answers this can be written outside the app
today; what is missing is a way to get it into the app and have it appear at the moment it is wanted.

`handoffs/2026-08-10-ai-chapter-analysis.md` explored this and stopped on three open questions. All
three are now answered, and two of its premises have expired: the Media3 playback layer it called
unwritten is built, and the library it called chapterless is not — *The Hero of Ages* carries 90
chapter marks. Chapter-keyed summaries now have something real to key to.

This is a later explicit request of the kind §3 allows for, so it needs its own boundary section in
the PRD, as §3.1 and §3.2 record for the ebook companion and for notes.

## What Changes

- **Importing a summary file.** The owner picks one plain-text file for a book. Entries are separated
  by a marker line that occupies the entire line — `Chapter 12`, `Chapter 12:`, `Prologue`,
  `Epilogue` — with the summary beneath it. Anything before the first marker line is ignored, which
  absorbs an assistant's preamble. Requiring the marker to be the whole line is what keeps a summary
  opening "Chapter 4 was where..." from splitting an entry in two.
- **Matching entries to chapters by what their labels denote,** not by position. This reuses
  `normalizeChapterLabel` in `playback/ChapterMatching.kt`, which already reduces a label to a
  chapter number or a named section across digits, Roman numerals, and spelled-out numbers, and
  already exists for read-along. Markers that match no chapter and chapters that match no marker are
  both ordinary, not errors; the import states how many chapters were matched and that figure stays
  visible afterward.
- **A prompt at the end of a chapter** the owner just listened through: a brief bar over the Player
  offering to read that chapter's summary, which disappears on its own. It never pauses, seeks, or
  otherwise touches playback, and it fires at most once per chapter.
- **A summary control on each chapter that has one,** in the Player's chapter list and in the
  reader's table of contents, opening that summary at any time regardless of where playback is.
- **A sheet that displays one summary** as scrollable text.
- **PRD §3.3**, recording what this feature is and — at greater length — what it is not.

No AI runs in the app, no text is generated on the device, and nothing is spoken. The summaries are
written elsewhere and read here.

## Capabilities

### New Capabilities

- `chapter-summaries`: importing a per-book summary file, matching its entries to the book's
  chapters, prompting at a chapter's end, and displaying a summary on demand from either chapter
  list.

### Modified Capabilities

None. The Player's chapter list (`transport-controls`) and the reader's table of contents
(`ebook-reader`) each gain an affordance on rows that carry a summary, but no requirement either
spec states changes behavior: every scenario they specify holds unaltered.

## Impact

**New code.** A parser for the summary file and a matcher over stored chapter titles, both pure and
unit-testable; a `chapter_summaries` table with a Room migration; a summary sheet; a glyph in
`ui/Icons.kt` for the per-chapter control.

**Modified code.** `ui/player/ChaptersSheet.kt` (the import control and the per-row affordance),
`ui/reader/ReaderChrome.kt` (the same affordance in `ContentsSheet`), `ui/player/PlayerViewModel.kt`
and `ui/reader/ReaderViewModel.kt`, `data/Entities.kt`, `data/LibraryDao.kt`, `data/Migrations.kt`,
`data/AudiobookDatabase.kt`.

**Reused rather than rebuilt.** `normalizeChapterLabel` and `ChapterKey` for label matching; the
audio-chapter-to-nav-entry pairing `matchChapters` already produces, so the reader's table of
contents needs no second mapping; the existing snackbar-with-action pattern for the prompt;
`ui/Tooltip.kt`'s `IconTooltip` for the new control.

**Dependencies.** None added. The file is plain text, so no parsing library is needed.

**Permissions and manifest.** Nothing new. The file is chosen through the Storage Access Framework,
which requires no permission, and no INTERNET permission is added or needed — §24 holds exactly as
it did.

**PRD.** §3.3 is added. §16 is respected in spirit by a different means than the audio and the EPUB
use, and `design.md` argues the case: the file's text is read once and stored, rather than the file
being referenced and re-read, because the summaries are small, are needed per chapter on every
chapter list, and must not vanish when the owner tidies up a folder on the desktop. No audiobook or
ebook file is copied by anything here.

## Non-goals

Deliberately not built, and not to be built without a further explicit request:

- **Generating summaries in the app,** or any on-device or networked model. The owner writes them
  elsewhere. This is what keeps §24 intact.
- **Speaking a summary aloud** — text-to-speech, or pre-rendered summary audio played as part of the
  book. The 2026-08-10 handoff worked through this at length and concluded a summary should be a
  `MediaItem` if it is ever audible. Nothing here forecloses that, and nothing here builds toward it.
- **A resume recap** on returning after a gap, and an **end-of-book wrap-up**. Both are natural
  neighbors, and both are separate changes.
- **Anything interactive** — asking questions about a chapter, in any modality.
- **Summary markers on the progress scrubber.** §3.2 rules note markers off the scrubber; the same
  reasoning applies here.
- **Spoiler gating.** Every chapter that has a summary opens it, whether or not the owner has reached
  it. The owner chose this explicitly.
- **Editing a summary in the app.** The file is the source of truth; correcting one means editing
  the file and importing it again.
- **Any format other than the plain-text one specified.** No JSON, no Markdown, no per-chapter files
  in a folder.
- **Per-book or global settings for the prompt.** The prompt costs nothing to ignore, and §20
  requires no settings screen.
