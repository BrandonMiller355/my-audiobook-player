## ADDED Requirements

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

## MODIFIED Requirements

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
