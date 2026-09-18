## Context

`handoffs/2026-08-10-ai-chapter-analysis.md` explored this feature before the playback layer existed
and left three questions open. They are answered now, and answering them shrank the feature
considerably: v1 is text the owner wrote elsewhere, displayed on request. The handoff's central
argument — that an audible analysis must be a `MediaItem` rather than a text-to-speech overlay — is
about a version this change does not build, and nothing below contradicts it.

Two pieces of existing code decide most of this design.

`normalizeChapterLabel` in `playback/ChapterMatching.kt` already reduces a chapter label to what it
denotes — `Numbered(12)`, `Named("prologue")`, or `Unrecognized` — across digits, Roman numerals, and
spelled-out numbers. It was written to pair audio chapters with EPUB table-of-contents entries, which
is the same problem this change has with a different file on the other side. The matcher here is
therefore a caller, not a new implementation.

`matchChapters` already produces the audio-chapter-to-nav-entry pairing the reader uses for
read-along. The reader's table of contents can ask that pairing which audio chapter an entry
corresponds to, so putting summary controls in both chapter lists costs one lookup rather than a
second matching strategy.

One piece of existing behavior sets a trap. `ChapterMatching`'s own doc comment records it: a chapter
*index* stringifies into a label that `normalizeChapterLabel` reads as a valid chapter number, so
matching against indices silently pairs the wrong chapters and looks like ordinary drift. Everything
below matches against stored `ChapterEntity.title` values only.

## Goals / Non-Goals

**Goals:**

- Get summaries written elsewhere into the app in one pick, and say plainly how many matched.
- Match by what a label denotes, so that a file numbered for a book whose audio begins with a
  prologue still lands correctly.
- Offer a chapter's summary at the moment it becomes relevant, without ever interrupting the audio.
- Make every summary reachable at any time from the chapter list the owner is already looking at.
- Add no dependency, no permission, and no manifest entry.

**Non-Goals:**

- Generating, speaking, or editing summaries. See `proposal.md`.
- Prompting from the reader. The arming logic lives with the playback samples the Player's ViewModel
  already collects; a second arming site in `ReaderViewModel` would double the surface to reach a
  screen that has the contents-sheet control on it already (D8).
- Surviving a book being removed and re-added. Summaries cascade away with the book, as notes and
  read-along corrections do, and the file is re-imported in one pick.

## Decisions

### D1: Read the file once at import and store its text; do not keep the file

The picked file is parsed, matched, and written into Room during the import. The app then has no
further relationship with it — no persistable URI grant, no re-read on open.

This departs from how the audio and the EPUB are handled, and the difference is the point. §16
forbids copying *audiobook files* and requires persisting access to them, because they are
multi-gigabyte, are the user's library, and are opened by a player that streams from them. A summary
file is a few kilobytes of text that is fully consumed the moment it is read. Holding a URI instead
would mean re-parsing and re-matching on every open of the chapter list, and would lose every summary
the first time the owner tidies a folder on the desktop — for a file the app can simply have finished
with.

It also means the import can use a plain `ActivityResultContracts.OpenDocument()` rather than
`ui/library/OpenPersistableDocument.kt`. That subclass exists solely to add
`FLAG_GRANT_PERSISTABLE_URI_PERMISSION`, which this needs no part of.

**Alternative considered:** store the URI on `audiobooks` beside `ebookUri` and parse on demand.
Rejected for the two costs above. The symmetry with the ebook is superficial: an EPUB is re-read
because it is too large to hold and its text is needed in full; a summary file is neither.

### D2: One entry per chapter, keyed on the audio chapter index

```kotlin
@Entity(
    tableName = "chapter_summaries",
    primaryKeys = ["audiobookId", "chapterIndex"],
    foreignKeys = [ForeignKey(AudiobookEntity::class, ["id"], ["audiobookId"], onDelete = CASCADE)],
    indices = [Index("audiobookId")],
)
data class ChapterSummaryEntity(
    val audiobookId: Long,
    val chapterIndex: Int,
    val text: String,
    val prompted: Boolean = false,
)
```

The same shape and the same key as `ReadAlongCorrectionEntity`, for the same reason: the audio
chapter index is the side that stays addressable, and a book's chapters are written once when it is
added and never rescanned in place — `LibraryDao` has `insertBookWithChapters` and `deleteBook`, and
no path between them that renumbers.

`NoteEntity` denormalizes `chapterTitle` and this deliberately does not. A note is displayed in a
list of its own, away from the book's chapters, and has to stay truthful there. A summary is only
ever displayed from a chapter row, next to that chapter's own title; carrying a second copy of the
title would create a way for the two to disagree on screen and buy nothing.

`prompted` lives on this row rather than in a separate table because a chapter with no summary has
nothing to be prompted about.

### D3: The file format — a marker line is the entire line

```
Chapter 1
Vin considers the Deepness and what Kelsier left her.
It runs to as many paragraphs as it likes.

Chapter 2:
...

Prologue
...
```

A line is a marker when the part of it before any title separator (`:`, an em dash, an en dash) is,
on its own, a chapter reference — `Chapter <number>` with the number in digits, Roman numerals, or
words, or one of the named sections `normalizeChapterLabel` already recognizes (prologue, epilogue,
prelude, interlude, appendix) — **and** is at most three words long. Everything up to the first
marker line is discarded, which absorbs an assistant's "Here are the chapter summaries you asked
for." Everything between one marker and the next is that entry's text, trimmed of surrounding blank
lines. An entry whose text is blank is dropped rather than stored: an empty summary would put a
control on a chapter row that opens nothing.

**The length test, not the normalizer, is what makes this safe.** Plain text's one real weakness is
that its delimiter is also ordinary prose, and `normalizeChapterLabel` is no defense against it at
all: handed "Chapter 4 was where the heist turned" it returns `Numbered(4)` quite happily, because
its own window logic reads the word after "chapter" and stops. Requiring the reference to stand alone
in at most three words is what rejects that line, and rejects it whether or not it later contains a
colon. Three matches the window `normalizeChapterLabel` allows itself for an unprefixed label, and
leaves room for "Chapter Twenty One" and "Interlude 2".

**Revised during implementation: a marker may carry a title.** `Chapter 1: The Well of Ascension` is
what an assistant emits by default when asked for chapter summaries, and the specification's original
wording — the whole line is a chapter reference — would have failed an entire file over it, for a
reason the owner would have had to guess at. The part after the separator is discarded. This widens
what is accepted without weakening anything: the false-split case has no separator before its seventh
word, so it fails the length test on the whole line either way. The specification's scenarios were
updated to match rather than left contradicting the code.

**Alternatives considered:** JSON, which is unambiguous but brittle to hand-edit and routinely comes
back malformed from an assistant asked for ninety long text bodies; and Markdown headings, which are
robust and are what an assistant emits by default. The owner chose plain text, and the whole-line
rule is what makes that choice safe rather than merely simple.

### D12: Markdown is the same format with headings

Added after the owner supplied a real summary file, which is Markdown. Accepting it is three changes,
not a second format:

- **The picker offers `text/markdown` and `text/x-markdown` alongside `text/plain`.** Which type a
  `.md` file actually carries was checked against the device's own `MediaStore` rather than assumed —
  it reports `text/markdown`. The picker filters on the provider's type, and a wrong guess hides the
  file with no way for the owner to tell why.
- **A heading is classified, not merely matched.** A Markdown file has headings that are not
  chapters: the document's own title, and a "Part One" above the chapters under it. Neither is a
  marker, and neither is prose. Treating them as prose appends the document title to whatever entry
  precedes it; treating them as markers creates entries that match nothing. They end the current
  entry and start nothing, which is what they mean.
- **Inline emphasis is rendered rather than shown.** The owner's file uses `**bold**` throughout for
  key terms. Displaying the asterisks would be shipping something visibly broken, so `inlineMarkdown`
  reduces the marks to the same `EmphasisSpan` list the EPUB reader already produces from XHTML tags,
  and the sheet styles them exactly as `ReaderScreen.annotate` does. The *stored* text stays as the
  owner wrote it — the file is the source of truth, and stripping at import would discard what the
  emphasis said.

**The trap this exposed, which had nothing to do with Markdown.** `normalizeChapterLabel` is
deliberately generous — it exists to read whatever an EPUB or a container calls a chapter — and over
its three-word window it finds a number almost anywhere. Handed `Mistborn Book 3` it returns chapter
three, which is the right answer for a table-of-contents entry and catastrophic for the title line of
a summary file. The owner's file opens with exactly that, so the document's title would have taken
chapter three's slot and the front matter beneath it would have become chapter three's summary.

So marker detection now gates on shape before asking what a label denotes: it says "chapter", or it
names a section, or it is a single token standing alone. A phrase that merely contains a number is a
title. The gate belongs here rather than in `normalizeChapterLabel`, whose generosity is correct for
the matching side and must not be narrowed — `NAMED_SECTIONS` is shared rather than copied so the two
sides cannot drift.

### D4: Match labels to stored chapter titles, with no positional fallback

Each marker label and each `ChapterEntity.title` goes through `normalizeChapterLabel`. Equal keys
pair. `Unrecognized` on either side never pairs. Where a key appears more than once — a two-part
omnibus with two "Chapter 1"s — markers and chapters are consumed in order, first against first,
which is what `matchByLabel` already does with its `tocByKey` map.

`matchChapters` falls back to positional matching when label matching finds nothing, and this
deliberately does not. That fallback earns its keep for read-along, where the alternative is the
feature not working at all and where `detectOffset` scores alignments against a physical quantity
(characters per millisecond) that reveals a wrong offset. Here there is no such signal, and a
positional guess produces every chapter showing a confidently wrong summary — a failure that reads as
the app working. Better to match nothing and report nothing matched.

A book whose `.m4b` carries no chapter marks therefore imports zero entries. The owner's library
contains both kinds; *The Hero of Ages* has 90 marks and the older Mistborn rips have one. This is
the honest outcome for a chapterless book, and the import's count says so plainly.

### D5: An import replaces the book's summaries wholesale

Importing deletes every `chapter_summaries` row for that book and writes the new set, inside one
transaction. Correcting a file and re-importing is the only editing path this feature has (see
`proposal.md`), so a merge would mean a chapter whose entry the owner *deleted* from the file keeps
showing the old text.

This clears `prompted` along with everything else. Harmless: a prompt fires on a genuine forward
crossing (D6), and chapters already listened through are not crossed again.

### D6: Only a forward crossing under playback arms the prompt

`PlayerViewModel` already samples position on a ticker and derives `location.chapterIndex`. A
crossing is recognized from consecutive samples:

- playback is running, and
- a previous sample exists for this same book — so resuming into the middle of a book, which produces
  a first sample with no predecessor, never arms, and
- the chapter index advanced by exactly one, and
- the absolute book position advanced by no more than a few seconds.

The last condition is what separates listening from every other way the index can change. A scrub, a
chapter-skip, or a seek moves the position by far more than one tick's worth; a backward move fails
the "advanced by exactly one" test. Nothing here needs to know *which* control caused a jump — it only
needs to know that the narration did not carry the owner across the boundary, and the size of the
position delta says that on its own.

The prompt offers the summary of the chapter that just **ended**, not the one now playing. It is
suppressed when that chapter's row is already `prompted`, and showing it sets the flag.

Expressed as a pure function over a sequence of samples, so it is unit-testable without a player:

```kotlin
fun armsPrompt(previous: Sample?, current: Sample, isPlaying: Boolean): Int?
```

**Known gap:** the final chapter has no following chapter to cross into, so its summary never
prompts. Accepted rather than special-cased — the end of a book is not a moment that needs an
interruption, and the chapter list still has it.

### D7: The prompt is a snackbar with an action and never touches playback

A brief bar over the Player reading something like "Chapter 12 finished" with a **Read** action,
dismissing itself on the host's timeout. Tapping opens the summary sheet (D9); ignoring it does
nothing at all.

`PlayerScreen.kt:122` and `ReaderScreen.kt:206` already use `SnackbarHostState` with an action and a
`SnackbarResult`, so this is the established pattern rather than a new one.

**One trap, found on the device.** The effect that shows this must consume the prompt *after*
`showSnackbar` returns, not before. Clearing it first changes the `LaunchedEffect` key, which cancels
the coroutine that was about to do the showing — the offer then never appears while everything behind
it looks like it worked. That is exactly how it failed on the first device run: the arming logic was
correct, the database showed the chapter flagged as prompted, and nothing was ever drawn. The
existing error effect immediately above it consumes after showing, for the same reason.

**The offer is a moment, not a queue.** It is marked prompted as it is made, so a crossing that
happens with the Player off screen is simply not offered again — verified on the device, where a
boundary crossed with the app backgrounded left the audio playing, drew nothing, and consumed the
offer. That is this decision's position rather than an oversight: the chapter list is the durable way
back to any summary, and an offer surfacing two chapters later would be about the wrong chapter.

It does not pause. The prompt fires precisely when the owner is walking or driving and cannot look at
the phone, which is the observation that governs this whole decision: anything that stops the audio
strands a paused book in a pocket, and anything that waits for an answer is a demand made at the
worst possible moment. When the app is not on screen, nothing appears and nothing is queued — the
chapter list is the durable way back to a summary, and it is one tap from the Player.

**Alternative considered:** pausing and asking. Rejected above. **Also considered:** a persistent
banner until dismissed, which cannot be ignored and so sits over the transport controls indefinitely.

### D8: The Player's chapter list owns the import control and the match count

A section in `ChaptersSheet`, below the speed row and above the chapters, with a text button —
**Import** when the book has none, **Replace** when it has some — and a line stating the current
state: "42 of 90 chapters." A text button rather than an icon, because it is a rarely used control
whose meaning would not survive being reduced to a glyph.

That line is also how the import reports itself. A snackbar saying "42 of 90 matched" is gone in four
seconds, and the number is exactly what the owner needs when checking whether a file was numbered the
way the audio is; keeping it on the sheet means the answer is still there after the file has been
edited and the app reopened.

**Revised during implementation: the failures are stated here too, not in a snackbar.** The three
ways an import can come to nothing — unreadable file, no entries, nothing matched — were originally
routed to the Player's existing snackbar. They were never visible. This sheet is a `ModalBottomSheet`
and renders above the `Scaffold` that hosts the snackbar, and it covers 92% of the screen, so every
message was being displayed underneath the sheet that triggered it. Since the only control that
starts an import lives in this sheet, no snackbar raised from it can ever be seen.

Device testing is what caught this. Nothing about it is visible from a unit test, the import itself
worked correctly throughout, and the state it produced was right — only the report was invisible. It
is the second defect of exactly this shape found in this change; see D7.

### D9: A summary is shown in a modal bottom sheet

Chapter title, then scrollable text. `ModalBottomSheet` is what the chapter list, the contents sheet,
the search sheet, and the removal confirmation all use; a summary is a transient thing read and
dismissed, which is what that component is for. One composable serves both entry points.

### D10: The reader's contents sheet reuses the read-along pairing

`ReaderViewModel` already holds the result of `matchChapters` when the book supports read-along.

**Revised during implementation.** That result cannot be inverted: `matchChapters` returns anchors —
`(absoluteMs, absoluteChars)` pairs — and discards which entry paired with which chapter on the way
out. The anchors turn out to be enough on their own, because of what each one *is*: a matched
chapter's start expressed on both sides at once. Looking an anchor's `absoluteChars` up against the
entries' own character offsets recovers the pairing exactly, with no interpolation and no second
matching strategy. An entry that matched no chapter is simply absent from the result, which is what
leaves front matter, part headings, and sub-sections without a control.

This must read the *uncorrected* anchors (`baseAnchors`). An owner's read-along correction adds an
anchor mid-chapter (`add-readalong-nudge` design D1), and that anchor is not a chapter start —
matching against it would put a summary control on whatever entry happened to sit near where the
owner last nudged.

An entry with no pairing shows no control, as does one whose paired chapter has no summary. A book
with no read-along support — no chapter marks, or no table of contents — shows no controls in the
reader at all, and loses nothing: without chapter marks there are no summaries to show anywhere, and
the Player's chapter list remains the complete view either way.

### D11: A new `SummaryIcon` in `ui/Icons.kt`

Three ragged horizontal lines inside a rounded rectangle — a card of text. It has to be told apart
from two glyphs already in that file at the same size: `DocumentIcon` is a page with a turned corner
and no lines, and `ContentsIcon` is numbered lines with no frame. Same 24-unit grid, same 2-unit
stroke with round caps and joins as everything else there.

Wrapped in `IconTooltip`, as every icon-only control in this app is.

## Risks / Trade-offs

**A summary's prose splits an entry** → The marker must be the entire line (D3). A collision then
requires a line containing nothing but a chapter reference.

**A file numbered differently from the audio matches nothing** → `normalizeChapterLabel` absorbs the
common cases (Roman numerals, spelled-out numbers, a `Prologue` that occupies audio chapter 1). What
it cannot absorb is reported as a count rather than silently guessed at (D4), and the count sits on
the chapter sheet where it can be checked (D8).

**A chapterless `.m4b` supports nothing here** → Stated, not worked around. The import reports zero
matched. Deriving chapter boundaries from anywhere else is a standing PRD §3.1 non-goal.

**The prompt is missed because the phone was in a pocket** → By design; the alternative interrupts the
book. The chapter list is the durable path (D7).

**Summaries written by an assistant that has read the whole book leak the ending** → Outside the app
entirely, as the 2026-08-10 handoff noted. The app displays what the file contains. Worth the owner
knowing when generating a file, and not something the app can check.

**A very long summary in a `TEXT` column** → Not a real risk at the size chapter summaries run, and
the column is read one row at a time by the sheet rather than joined into the library query.

## Migration Plan

One Room migration adding `chapter_summaries`, following `data/Migrations.kt`'s existing pattern, with
the version bumped in `AudiobookDatabase.kt`. Additive: no existing table or column is touched, and a
database that has not run it simply has no summaries. Nothing to roll back beyond dropping the table.

## Open Questions

None. The four decisions that were open — file format, prompt behavior, which chapter lists carry the
control, and whether to gate browsing ahead — were answered by the owner before this was written.
