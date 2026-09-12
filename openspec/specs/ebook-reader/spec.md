# ebook-reader Specification

## Purpose

Reading a linked EPUB inside the app, on a black page the Player's cover art flips to and back.
While an audiobook plays, the reader can follow the narration, scrolling automatically to keep pace
with the audio, and a user scroll jumps the audio to match.

**Read-along tier.** When the book carries tagged chapter marks and the ebook has a table of contents,
the reader follows the audio via piecewise-linear interpolation: character counts predict narration
time within a chapter, and chapter boundaries eliminate drift. The reading position and the playback
position become the same position. When the book lacks chapter marks or the ebook lacks a table of
contents, read-along is unavailable and the reader behaves as a manual tier: the two positions are
independent.

The four tiers that were considered, their costs, and which the owner's library can actually support
are set out in `handoffs/2026-08-10-ebook-audio-readalong.md`. The full list of what this tier
deliberately leaves out — word-level sync, forced alignment, learning from use, deriving chapters
from structure — is in the proposal for this change at
`openspec/changes/add-readalong-scroll/proposal.md`.

A finer tier would add requirements to this capability rather than replace it, the way `playback`
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

While read-along is on, the reading position and the playback position are the same position, and the
reader opens where the audio is rather than where reading stopped. The independence of the two
positions is a guarantee about read-along being off, not about the reader in general.

#### Scenario: Returning to the reader

- **WHEN** the user scrolls partway into an ebook with read-along off, leaves the reader, and opens it
  again
- **THEN** the reader returns to the place the user was reading

#### Scenario: The position survives a restart

- **WHEN** the user reads partway with read-along off, force-stops the app, and reopens the reader
- **THEN** the reader returns to the same place

#### Scenario: The position survives a font change

- **WHEN** the user changes text size, line spacing, or typeface
- **THEN** the same passage remains on screen rather than the reader jumping elsewhere in the book

#### Scenario: Positions are independent per book

- **WHEN** the user reads in two different books' ebooks
- **THEN** each book returns to its own reading position

#### Scenario: Reading position is independent of playback position while read-along is off

- **WHEN** read-along is off and the user seeks the audio to a different place in the book
- **THEN** the reading position is unchanged

#### Scenario: The two positions are one while read-along is on

- **WHEN** read-along is on
- **THEN** the place the reader shows and the place the audio is narrating are the same place
- **AND** reopening the reader returns to where the audio is

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

### Requirement: The ebook's text can be searched

The reader SHALL let the user search the ebook's text for a word or phrase, and selecting a result
SHALL move the reader to that place the way a table-of-contents entry does. Matching SHALL be
case-insensitive plain text within a single block; it is not required to match across block
boundaries, and the number of results MAY be capped.

#### Scenario: Finding a word

- **WHEN** the user searches for a word that appears in the ebook
- **THEN** the places it appears are listed, each with the surrounding text

#### Scenario: Following a result

- **WHEN** the user selects a search result
- **THEN** the reader moves to that place in the book
- **AND** the search closes

#### Scenario: A word that is not in the book

- **WHEN** the user searches for text the ebook does not contain
- **THEN** the reader says there are no matches rather than showing an empty list

#### Scenario: Case does not matter

- **WHEN** the user searches in a different case than the book uses
- **THEN** the matches are found anyway

#### Scenario: Searching does not move the reader on its own

- **WHEN** the user searches and then dismisses the search without selecting a result
- **THEN** the reader is still where it was and the saved reading position is unchanged

### Requirement: Reader controls are revealed by tapping and hide themselves

The reader's controls SHALL be hidden while reading and SHALL be revealed by a tap in the middle of
the page, hiding again after a short delay or on the next tap. They SHALL be visible when the reader
is first opened so that the gesture is discoverable.

A tap that dismisses a live text selection SHALL also toggle the controls, because the reader cannot
tell the two taps apart. Compose clears the selection without consuming the tap and exposes no
selection state to consult, so the gesture arrives at the chrome looking exactly like any other tap.
This is stated as behavior rather than left as a surprise: putting a selection away reveals the
controls, and they hide themselves on the usual delay.

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

#### Scenario: A tap that clears a selection

- **WHEN** the user taps the page while a passage is selected
- **THEN** the selection is cleared
- **AND** the controls appear, the same as they would for a tap with nothing selected
- **AND** they hide themselves after the usual delay

#### Scenario: Tapping again after clearing a selection

- **WHEN** the user taps the page again, with nothing now selected
- **THEN** the controls toggle as they normally do

#### Scenario: Revealing the controls while paused

- **WHEN** the user taps the middle of the page while playback is paused and nothing is selected
- **THEN** the controls appear
- **AND** the text being selectable does not swallow the tap

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

### Requirement: The reader's text can be selected and copied while playback is paused

The reader SHALL make the book's text selectable while playback is paused, and SHALL NOT make it
selectable while playback is playing. A selection SHALL be started by a long press and extended by
dragging, and the selected passage SHALL be copyable to the system clipboard.

Scrolling SHALL be unaffected in either state. A touch that begins moving before the long-press
threshold SHALL scroll the page and SHALL NOT begin a selection, which is how a reader moves the page
and is not this change's to alter.

The gate is about motion rather than gesture ambiguity. While the narration plays, the page glides
beneath the finger, so a word selected there would slide out from under its own handles as soon as it
was caught, and the handles would spend the rest of the gesture chasing text that has moved. A
selection is therefore only offered against a page that is holding still.

The selection SHALL be plainly visible against the reader's black page, and the text under it SHALL
stay legible. This is a requirement rather than a detail because the default is neither: selection
colors are derived from the app's theme, whose primary is a muted warm neutral, and on pure black
that wash comes out very nearly black.

Starting playback SHALL clear any live selection, since the condition that allowed it no longer
holds.

#### Scenario: Selecting a passage while paused

- **WHEN** the user long-presses a word in the text while playback is paused
- **THEN** that word is selected and selection handles appear
- **AND** dragging a handle extends the selection across the surrounding text

#### Scenario: The selection is visible on the black page

- **WHEN** the user selects a passage
- **THEN** the selected words are marked by a wash that is plainly distinguishable from the page
- **AND** the selection handles are plainly visible
- **AND** the selected text remains legible through the wash

#### Scenario: Copying the selection

- **WHEN** the user has a passage selected and chooses Copy
- **THEN** the selected text is placed on the system clipboard
- **AND** it can be pasted into another app
- **AND** the reader confirms that the copy happened

#### Scenario: The confirmation stands in for the system's own

- **WHEN** the user copies from the reader, whose page hides the system bars
- **THEN** the reader says so itself, rather than relying on the clipboard notice the system shows
  elsewhere and suppresses here
- **AND** a copy is never silent

#### Scenario: Long press while the book is playing

- **WHEN** the user long-presses the text while playback is playing
- **THEN** nothing is selected and no handles appear
- **AND** the page is undisturbed

#### Scenario: Scrolling the page while playing

- **WHEN** the user touches the text while playback is playing and drags
- **THEN** the page scrolls
- **AND** nothing is selected

#### Scenario: Scrolling the page while paused

- **WHEN** the user touches the text while playback is paused and drags, beginning to move before the
  long-press threshold
- **THEN** the page scrolls
- **AND** nothing is selected, because the touch never became a long press

#### Scenario: Holding still and then dragging while paused

- **WHEN** the user holds a finger still on the text while playback is paused for the length of a
  long press, and only then drags
- **THEN** a word is selected and the drag extends the selection rather than scrolling the page
- **AND** lifting the finger and dragging again scrolls the page as usual

#### Scenario: Starting playback with a passage selected

- **WHEN** the user has a passage selected and starts playback
- **THEN** the selection is cleared
- **AND** the text is no longer selectable until playback is paused again

#### Scenario: A passage longer than the composed text

- **WHEN** the user drags a selection handle toward a paragraph that has scrolled far enough out of
  view to have left the reader's composed range
- **THEN** the selection stops at the end of what the reader has composed rather than growing
  silently wrong or crashing
- **AND** the part that is selected remains copyable

