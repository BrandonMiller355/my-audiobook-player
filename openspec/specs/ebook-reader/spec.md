# ebook-reader Specification

## Purpose

Reading a linked EPUB inside the app, on a black page the Player's cover art flips to and back.

**Scope: this is the manual companion tier.** The reading position and the playback position are
two independent places in the same book, and nothing here attempts to keep them in step — no
highlighting the narrated sentence, no scrolling with the narration, no manual sync anchors, no
deriving audio chapters from the ebook's structure. That absence is a decision, not a gap.

Synchronization is a different project, and the reason is concrete: it requires forced alignment,
and the owner's `.m4b` files are each a single 10–14 hour chapter, so there are no chapter
boundaries to re-anchor against and drift accumulates unchecked. The four tiers that were
considered, what each would cost, and which the owner's library can actually support are set out in
`handoffs/2026-08-10-ebook-audio-readalong.md`. The full list of what this tier deliberately leaves
out is in the archived proposal at
`openspec/changes/archive/2026-08-17-add-ebook-companion/proposal.md`.

A later tier would add requirements to this capability rather than replace it, the way `playback`
has grown across four changes.

## Requirements
### Requirement: A linked ebook is readable as continuously scrollable text

The app SHALL provide a reader screen that renders a linked EPUB as one continuous scrollable body
of text spanning the whole book, without page turns and without page numbers.

#### Scenario: Opening the reader

- **WHEN** the user opens the reader for a book with a linked ebook
- **THEN** the ebook's text is shown
- **AND** it can be scrolled from the beginning of the book to the end without any further action

#### Scenario: Scrolling past the end of a chapter

- **WHEN** the user scrolls past the end of one chapter
- **THEN** the next chapter's text follows continuously in the same scroll

#### Scenario: While the ebook is being read from disk

- **WHEN** the reader is opened and the ebook has not finished loading
- **THEN** a loading state is shown rather than a blank page or partial text

### Requirement: The reader renders a defined subset of EPUB content

The reader SHALL render paragraphs, headings, emphasis, strong emphasis, block quotes, line breaks,
horizontal rules, and ordered and unordered lists. Content outside that subset SHALL contribute its
text without its formatting rather than being dropped or rendered as markup. Scripts and stylesheets
SHALL be skipped entirely.

#### Scenario: Prose formatting is preserved

- **WHEN** the ebook contains paragraphs, headings, italics, bold text, block quotes, and lists
- **THEN** each is rendered with formatting that distinguishes it from body text

#### Scenario: Unsupported elements degrade to their text

- **WHEN** the ebook contains a table, an image with a caption, or any other unsupported element
- **THEN** the element's text content still appears in reading order
- **AND** no raw HTML tags are shown to the user

#### Scenario: Scripts and styles are not shown

- **WHEN** the ebook's XHTML contains `<script>` or `<style>` blocks
- **THEN** their contents do not appear in the rendered text

#### Scenario: Malformed markup does not crash the reader

- **WHEN** an ebook contains unclosed tags, unknown entities, or invalid XHTML
- **THEN** the reader renders what it can and does not crash

### Requirement: The reading position is remembered per book

The app SHALL remember where the user stopped reading in each book's ebook, and SHALL restore that
place when the reader is reopened. The stored position SHALL remain correct when text size, line
spacing, typeface, or screen orientation changes.

#### Scenario: Returning to the reader

- **WHEN** the user scrolls partway into an ebook, leaves the reader, and opens it again
- **THEN** the reader returns to the place the user was reading

#### Scenario: The position survives a restart

- **WHEN** the user reads partway, force-stops the app, and reopens the reader
- **THEN** the reader returns to the same place

#### Scenario: The position survives a font change

- **WHEN** the user changes text size, line spacing, or typeface
- **THEN** the same passage remains on screen rather than the reader jumping elsewhere in the book

#### Scenario: Positions are independent per book

- **WHEN** the user reads in two different books' ebooks
- **THEN** each book returns to its own reading position

#### Scenario: Reading position is independent of playback position

- **WHEN** the user seeks the audio to a different place in the book
- **THEN** the reading position is unchanged

### Requirement: The ebook can be navigated by its table of contents

The reader SHALL offer the ebook's own table of contents, and selecting an entry SHALL move the
reader to the start of that section. Where the ebook groups its entries — parts containing chapters
— that grouping SHALL be visible rather than flattened away.

#### Scenario: Jumping to a chapter

- **WHEN** the user opens the table of contents and selects a chapter
- **THEN** the reader moves to the start of that chapter
- **AND** the table of contents closes

#### Scenario: A grouped table of contents

- **WHEN** the ebook's table of contents nests chapters under parts
- **THEN** the nesting is shown
- **AND** both the parts and the chapters under them can be selected

#### Scenario: An entry targeting a place inside a document

- **WHEN** a table of contents entry points at a specific position within a document rather than at
  its start
- **THEN** the reader moves to that position

#### Scenario: An ebook with no navigation document

- **WHEN** the ebook provides no table of contents
- **THEN** the reader says so rather than showing an empty list
- **AND** scrolling still works normally

### Requirement: Reader controls are revealed by tapping and hide themselves

The reader's controls SHALL be hidden while reading and SHALL be revealed by a tap in the middle of
the page, hiding again after a short delay or on the next tap. They SHALL be visible when the reader
is first opened so that the gesture is discoverable.

#### Scenario: Revealing the controls

- **WHEN** the user taps the middle of the page while the controls are hidden
- **THEN** the controls appear

#### Scenario: The controls hide themselves

- **WHEN** the controls have been visible for a few seconds without interaction
- **THEN** they fade away and the page is left uninterrupted

#### Scenario: Controls are shown on entry

- **WHEN** the reader is opened
- **THEN** the controls are visible before they auto-hide

#### Scenario: Reading is not interrupted by the gesture

- **WHEN** the user scrolls the page
- **THEN** scrolling is not interpreted as a tap and the controls do not appear

### Requirement: The reader returns to the Player

The reader's controls SHALL include a way back to the Player for the same book, and hardware back
SHALL do the same thing.

#### Scenario: Flipping back from the controls

- **WHEN** the user taps the flip-back control
- **THEN** the Player for the same book is shown

#### Scenario: Hardware back

- **WHEN** the user presses the system back button in the reader
- **THEN** the Player for the same book is shown, not the Library

### Requirement: The reader is black with white text in every theme

The reader SHALL use a pure black background with white text regardless of whether the system is in
light or dark mode, and system bars SHALL be styled to match while the reader is shown and restored
on exit.

#### Scenario: Light system theme

- **WHEN** the device is in light mode and the user opens the reader
- **THEN** the reader is black with white text

#### Scenario: Dark system theme

- **WHEN** the device is in dark mode and the user opens the reader
- **THEN** the reader is black with white text, blacker than the app's own dark surfaces

#### Scenario: Leaving the reader

- **WHEN** the user returns to the Player from the reader
- **THEN** the system bars are restored to what the rest of the app uses

### Requirement: The screen stays on while reading

The reader SHALL prevent the screen from timing out while it is shown, and SHALL stop doing so when
it is left.

#### Scenario: Reading without touching the screen

- **WHEN** the reader is shown and the user does not touch the screen for longer than the system
  screen timeout
- **THEN** the screen stays on

#### Scenario: Leaving the reader

- **WHEN** the user returns to the Player or the Library
- **THEN** the normal system screen timeout applies again

