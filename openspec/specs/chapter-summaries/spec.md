# chapter-summaries Specification

## Purpose
Chapter summaries the owner writes outside the app and imports as one file per book: how a file is
read, how its entries are paired to the book's chapters by what their labels denote, when a summary
is offered at a chapter's end, and where one can be opened on demand.

Nothing here generates or speaks text. The app displays what it was given, which is what keeps this
capability clear of §24.
## Requirements
### Requirement: Chapter summaries are imported from a text file

The app SHALL let the owner import, for one book, a single plain-text or Markdown file of chapter
summaries written outside the app. The app SHALL NOT generate, request, or speak summary text, and
SHALL NOT require network access for any part of this.

The file is chosen through the system file picker, which SHALL offer both `.txt` and `.md` files.
Its contents are read once during the import and kept by the app; the file itself is not retained,
referenced, or read again afterward, so editing or moving it later changes nothing until it is
imported again.

#### Scenario: Importing a summary file

- **WHEN** the owner imports a summary file for a book
- **THEN** the summaries it contains are stored against that book's chapters
- **AND** the app states how many of the book's chapters now have a summary

#### Scenario: A Markdown file is offered by the picker

- **WHEN** the owner opens the picker to import summaries
- **THEN** both `.txt` and `.md` files can be chosen
- **AND** the book's own audio and ebook files cannot

#### Scenario: The imported file is moved or deleted afterward

- **WHEN** the file that was imported is later renamed, moved, or deleted
- **THEN** the book's summaries remain available unchanged

#### Scenario: Importing is abandoned

- **WHEN** the owner opens the file picker and dismisses it without choosing a file
- **THEN** the book's existing summaries are unchanged

#### Scenario: The chosen file cannot be read or contains no entries

- **WHEN** the chosen file cannot be read, or contains no recognizable entry
- **THEN** the owner is told so
- **AND** the book's existing summaries are unchanged

### Requirement: An entry begins at a marker occupying an entire line

A summary file SHALL separate its entries by marker lines, and a line SHALL be treated as a marker
only when the whole line is a chapter reference. Text preceding the first marker line SHALL be
ignored, and the text between one marker and the next SHALL be that entry's summary.

A chapter reference is `Chapter <number>`, with the number written as digits, Roman numerals, or
words, or a named section the app recognizes such as `Prologue` or `Epilogue`, or a number standing
alone. It MAY be followed by a separator and a chapter title, which is discarded —
`Chapter 1: The Well of Ascension` names the same chapter `Chapter 1` does. A phrase that merely
contains a number, such as a document's title, SHALL NOT be read as a chapter reference.

A Markdown heading SHALL be read as a marker when what it names is a chapter reference, at any
heading level. A heading that names something else — the document's own title, or a "Part One"
above the chapters belonging to it — SHALL end the preceding entry without beginning a new one, and
its text SHALL NOT join any summary.

Requiring the marker to be the whole line is what keeps summary prose from splitting an entry:
without it, a summary opening "Chapter 4 was where the heist turned" would end the previous entry
partway through and the result would look complete.

#### Scenario: A file with numbered entries

- **WHEN** a file contains lines reading `Chapter 1` and `Chapter 2`, each followed by paragraphs of
  text
- **THEN** each block of text becomes the summary for the chapter its marker names

#### Scenario: A marker line with a trailing colon

- **WHEN** an entry's marker line reads `Chapter 12:`
- **THEN** it is recognized exactly as `Chapter 12` is

#### Scenario: A marker line carrying a chapter title

- **WHEN** an entry's marker line reads `Chapter 1: The Well of Ascension`
- **THEN** it names the same chapter `Chapter 1` would
- **AND** the title is not treated as part of the summary

#### Scenario: Summary prose that begins with a chapter reference

- **WHEN** a summary's own text contains a line beginning "Chapter 4 was where the heist turned"
- **THEN** that line stays part of the summary it is written in
- **AND** no new entry begins at it
- **AND** this holds whether or not that line goes on to contain a colon

#### Scenario: A marker with nothing written beneath it

- **WHEN** a marker line is followed by no text before the next marker
- **THEN** no summary is stored for the chapter it names

#### Scenario: Text before the first marker

- **WHEN** a file opens with a line of preamble before its first marker line
- **THEN** that preamble is discarded
- **AND** the entries that follow are imported normally

#### Scenario: A named section

- **WHEN** a file contains a marker line reading `Prologue`
- **THEN** it is recognized as naming that section rather than being discarded

#### Scenario: Markdown headings name the chapters

- **WHEN** a file marks its entries with headings such as `## Prologue` and `### Chapter 1`
- **THEN** each heading begins that chapter's entry
- **AND** the heading's own hashes and emphasis are not part of the summary

#### Scenario: A heading that names no chapter

- **WHEN** a file contains a heading such as `# Mistborn Book 3: The Hero of Ages — Chapter Summaries`
  or `## Part One: Legacy of the Survivor`
- **THEN** no entry is created for it
- **AND** it does not become part of the summary above or below it

#### Scenario: Markdown emphasis in a summary

- **WHEN** a summary's text contains Markdown emphasis such as `**Ruin**`
- **THEN** the emphasized words are shown emphasized
- **AND** the marks that expressed the emphasis are not shown

### Requirement: Entries are matched to chapters by what their labels denote

The app SHALL pair a file's entries with the book's chapters by what each label denotes rather than
by position, treating two labels as naming the same chapter when they denote the same chapter number
or the same named section however each is written. Matching SHALL be against the chapter titles
stored for the book, and SHALL NOT fall back to pairing entries with chapters in order.

Digits, Roman numerals, and spelled-out numbers all denote the same chapter number. An entry that
matches no chapter, and a chapter that matches no entry, are both ordinary outcomes rather than
failures.

Order is not a fallback here because nothing would reveal a wrong alignment: every chapter would show
a confidently wrong summary, which reads as the feature working.

#### Scenario: The audio numbers chapters differently from the file

- **WHEN** a book's first audio chapter is titled `Prologue` and its second is `Chapter 1`, and the
  file has entries for `Prologue` and `Chapter 1`
- **THEN** each entry lands on the chapter it names rather than on the chapter at its position

#### Scenario: A chapter number written another way

- **WHEN** an audio chapter is titled `Chapter IX` and the file's entry is marked `Chapter 9`
- **THEN** the two are matched

#### Scenario: Some entries match nothing

- **WHEN** a file contains entries for chapters the book does not have
- **THEN** the entries that do match are imported
- **AND** the count reported reflects only the chapters that now have a summary

#### Scenario: Nothing matches

- **WHEN** no entry in the file matches any of the book's chapters
- **THEN** no summaries are stored
- **AND** the owner is told that no chapters were matched

#### Scenario: A book whose audio carries no chapter marks

- **WHEN** a summary file is imported for a book whose audio is a single chapter covering the whole
  book
- **THEN** no chapters are matched
- **AND** the owner is told so rather than shown a guess

### Requirement: Importing replaces the book's summaries

An import SHALL replace every summary previously stored for that book rather than merging with them.

Correcting a summary means editing the file and importing it again, so an entry removed from the file
must disappear from the book rather than persist from the earlier import.

#### Scenario: Re-importing a corrected file

- **WHEN** a book already has summaries and the owner imports another file for it
- **THEN** the book's summaries are exactly those in the newly imported file

#### Scenario: An entry removed from the file

- **WHEN** a book already has a summary for a chapter and the newly imported file has no entry for
  that chapter
- **THEN** that chapter no longer has a summary

### Requirement: A chapter finished under playback offers its summary

The app SHALL briefly offer a chapter's summary when playback carries the owner out of that chapter
and into the next, and the offer SHALL dismiss itself. The offer SHALL NOT pause, seek, or otherwise
alter playback, whether it is taken, ignored, or dismissed, and SHALL be made at most once for a
given chapter.

Only the narration crossing the boundary makes the offer. Seeking, scrubbing, skipping to a chapter,
and resuming a book where it was left SHALL NOT make it, because each of those is the owner already
looking at the phone and choosing where to be.

The offer fires when the owner is most likely to be walking or driving, which is why nothing about it
waits for an answer.

#### Scenario: Listening through the end of a chapter

- **WHEN** playback carries from the end of a chapter that has a summary into the next chapter
- **THEN** the offer to show that chapter's summary appears briefly
- **AND** the audio continues playing throughout

#### Scenario: Taking the offer

- **WHEN** the owner takes the offer
- **THEN** that chapter's summary is shown
- **AND** playback is unaffected

#### Scenario: Ignoring the offer

- **WHEN** the offer is not taken
- **THEN** it disappears on its own
- **AND** playback is unaffected
- **AND** the summary is still reachable from the chapter list

#### Scenario: The same boundary is crossed again

- **WHEN** a chapter boundary that has already been offered is crossed again
- **THEN** no offer is made

#### Scenario: Skipping to the next chapter

- **WHEN** the owner moves to the next chapter with the chapter control, the scrubber, or a seek
- **THEN** no offer is made

#### Scenario: Resuming a book partway through a chapter

- **WHEN** a book is reopened and resumes from its saved position
- **THEN** no offer is made for the chapter that position falls in

#### Scenario: The chapter that ended has no summary

- **WHEN** playback crosses out of a chapter that has no summary
- **THEN** no offer is made

#### Scenario: The app is not on screen

- **WHEN** a chapter boundary is crossed while the app is not in the foreground
- **THEN** nothing is shown and nothing interrupts the audio

### Requirement: Any chapter with a summary opens it from the Player's chapter list

The Player's chapter list SHALL show a control on each chapter that has a summary, opening that
summary when it is used, whatever the book's playback position. A chapter with no summary SHALL show
no such control, and using the control SHALL NOT move playback or select the chapter.

No chapter is withheld. A summary ahead of where the owner has listened opens like any other.

#### Scenario: Opening a summary from the chapter list

- **WHEN** the owner uses the summary control on a chapter row
- **THEN** that chapter's summary is shown
- **AND** playback continues from where it was, in the chapter it was already in

#### Scenario: A chapter without a summary

- **WHEN** a chapter has no summary
- **THEN** its row shows no summary control

#### Scenario: A chapter ahead of the current position

- **WHEN** the owner uses the summary control on a chapter later in the book than the current
  position
- **THEN** that chapter's summary is shown

#### Scenario: A book with no summaries imported

- **WHEN** the chapter list is opened for a book that has no summaries
- **THEN** no chapter row shows a summary control
- **AND** the list behaves exactly as it did before

### Requirement: The reader's table of contents offers the same summaries

The reader's table of contents SHALL show a summary control on each entry whose corresponding chapter
has a summary, opening the same summary the Player's chapter list would, for a book whose ebook is
linked and whose chapters correspond to its table of contents.

An entry with no corresponding chapter, and one whose chapter has no summary, SHALL show no control.

#### Scenario: Opening a summary from the reader

- **WHEN** the owner uses the summary control on a table-of-contents entry
- **THEN** that chapter's summary is shown
- **AND** the reading position and the audio are unaffected

#### Scenario: A book whose chapters do not correspond to its table of contents

- **WHEN** the reader's table of contents is opened for a book whose chapters cannot be paired with
  its entries
- **THEN** no entry shows a summary control
- **AND** the table of contents behaves exactly as it did before

### Requirement: A summary is shown as readable, scrollable text

A summary SHALL be displayed with the title of the chapter it belongs to and its full text, scrolling
when the text is longer than the screen, and SHALL be dismissible without changing anything. It SHALL
NOT be editable in the app.

The same display serves both the offer made at a chapter's end and the controls in the two chapter
lists.

#### Scenario: Reading a long summary

- **WHEN** a summary is longer than fits on screen
- **THEN** it scrolls
- **AND** the chapter's title identifies what is being read

#### Scenario: Dismissing a summary

- **WHEN** the owner dismisses a summary
- **THEN** nothing about the book, its position, or its summaries changes

### Requirement: Summaries are removed with the book

Removing a book from the library SHALL remove its summaries with it, and SHALL NOT alter any file
belonging to the owner, including a summary file that was previously imported.

#### Scenario: Removing a book that has summaries

- **WHEN** the owner removes a book that has summaries
- **THEN** its summaries are removed with it
- **AND** the file they were imported from is untouched

