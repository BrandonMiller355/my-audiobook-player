## MODIFIED Requirements

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

#### Scenario: Sub-headings inside a chapter's entry

- **WHEN** a chapter's heading such as `### Chapter 1: Fyodor Pavlovitch Karamazov` is followed by
  deeper headings such as `#### Summary` and `#### Analysis`
- **THEN** those headings and the text beneath them are part of that chapter's summary, each shown
  as a bold line
- **AND** a heading at the chapter heading's level or above still ends the entry

#### Scenario: Chapters grouped under books

- **WHEN** a file groups its chapters under headings such as `## Book II: An Unfortunate Gathering`,
  or under a named section such as `## Epilogue` with `### Chapter 1` beneath it
- **THEN** each entry beneath records that book or section alongside its chapter number
- **AND** a marker may also name its book itself, as in `Book 2, Chapter 1`

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

#### Scenario: A book that numbers its chapters afresh in every part

- **WHEN** the audio's chapters are titled `Book 1 - Chapter 1` … `Book 2 - Chapter 1` …, and the
  file's `### Chapter 1` entries sit under `## Book I` and `## Book II`
- **THEN** each entry lands on the chapter of its own book with that number

#### Scenario: A renumbering file against titles that name no book

- **WHEN** the file's books each restart at chapter one, and the audio's chapters name no book
- **THEN** numbered entries are not matched by chapter number alone
- **AND** a file whose parts merely group continuously numbered chapters still matches by number

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

