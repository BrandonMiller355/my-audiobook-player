## ADDED Requirements

### Requirement: The reader follows the narration

While read-along is on, the reader SHALL keep the passage on screen corresponding to the place the
audio is narrating, moving continuously as the audio advances. The correspondence SHALL be derived
from how far through a chapter the audio is, measured against the text of that chapter, so that it is
exact at every chapter boundary and interpolated between them.

#### Scenario: Text moves with the audio

- **WHEN** read-along is on and the audio is playing
- **THEN** the text scrolls forward on its own
- **AND** the passage on screen is the one being narrated

#### Scenario: Paused audio does not scroll

- **WHEN** the audio is paused
- **THEN** the text stops where it is and does not continue scrolling

#### Scenario: The correspondence is exact at a chapter boundary

- **WHEN** the audio reaches the start of a chapter
- **THEN** the reader is showing the start of that chapter's text

#### Scenario: Following across a chapter boundary

- **WHEN** the audio plays past the end of one chapter into the next
- **THEN** the text continues into the next chapter's text without the reader stopping at the seam

#### Scenario: The audio is moved from somewhere else

- **WHEN** the audio is seeked from the Player, the notification, the lock screen, or a Bluetooth
  control while read-along is on
- **THEN** the reader moves to the place in the text corresponding to the new audio position

#### Scenario: A large jump is not scrolled through

- **WHEN** the audio moves to a place far from what the reader is showing
- **THEN** the reader moves there directly rather than scrolling through all the intervening text

#### Scenario: Opening the reader while read-along is on

- **WHEN** the user opens the reader for a book with read-along on
- **THEN** the reader opens at the place the audio is, rather than at the place last read

#### Scenario: Playback speed does not change the correspondence

- **WHEN** the book's playback speed is changed
- **THEN** the text still shows the narrated passage, scrolling at the new rate rather than drifting
  away from the audio

### Requirement: Moving the reader moves the audio

While read-along is on, deliberately moving the reader SHALL move the audio to match. A movement is
deliberate when the user causes it — dragging the page, selecting a table-of-contents entry, or
following a search result. The reader moving itself to follow the narration SHALL NOT move the audio.

#### Scenario: Scrolling seeks the audio

- **WHEN** the user drags the page and scrolling comes to rest
- **THEN** the audio moves to the place in the book the reader is now showing

#### Scenario: The reader following the narration does not seek

- **WHEN** the reader scrolls itself to keep up with the audio
- **THEN** the audio position is unaffected by that scrolling
- **AND** the two do not drive each other in a loop

#### Scenario: A small adjustment does not seek

- **WHEN** the user scrolls by an amount corresponding to only a few seconds of narration
- **THEN** the audio is left where it is

#### Scenario: Jumping by table of contents

- **WHEN** the user selects a table-of-contents entry
- **THEN** the reader moves to that section
- **AND** the audio moves to the corresponding place

#### Scenario: Following a search result

- **WHEN** the user selects a search result
- **THEN** the reader moves to that place
- **AND** the audio moves to the corresponding place

#### Scenario: A seek caused by scrolling can be undone

- **WHEN** scrolling has moved the audio
- **THEN** the user is offered a way to restore the previous playback position
- **AND** taking it returns the audio to where it was before the scroll

#### Scenario: Read-along off leaves the audio alone

- **WHEN** read-along is off and the user scrolls, jumps by table of contents, or follows a search
  result
- **THEN** the audio position is unchanged

### Requirement: A read-along seek does not start or stop playback

Moving the audio because the reader moved SHALL reposition playback without changing whether it is
playing.

#### Scenario: Scrolling while paused

- **WHEN** the audio is paused and the user scrolls the reader
- **THEN** the audio moves to the corresponding position
- **AND** it stays paused rather than beginning to play

#### Scenario: Scrolling while playing

- **WHEN** the audio is playing and the user scrolls the reader
- **THEN** the audio moves to the corresponding position
- **AND** it keeps playing

### Requirement: Audio chapters are matched to the ebook's table of contents

The app SHALL determine which table-of-contents entry corresponds to each audio chapter, matching
labels that denote the same chapter in different notation and falling back to matching in order.
Entries that group other entries rather than carrying text SHALL NOT be treated as chapters, and
entries with no corresponding audio SHALL contribute nothing rather than shifting the rest.

#### Scenario: Labels written differently

- **WHEN** an audio chapter is titled `03 - Chapter 3` and the corresponding table-of-contents entry
  reads `3`, `Chapter III`, or `Chapter Three`
- **THEN** the two are matched to each other

#### Scenario: Named sections

- **WHEN** both sides carry a prologue, an epilogue, or an appendix
- **THEN** those are matched to each other rather than being matched by position among the numbered
  chapters

#### Scenario: Front matter with no audio

- **WHEN** the ebook's table of contents lists a cover, a title page, a copyright page, or a map that
  the audio does not narrate
- **THEN** those entries are not matched to any audio chapter
- **AND** the chapters after them are still matched correctly

#### Scenario: A grouped table of contents

- **WHEN** the table of contents nests chapters under parts, so that a part heading is immediately
  followed by the first chapter under it with no text between them
- **THEN** the part heading is not treated as a chapter for matching
- **AND** it remains selectable in the table of contents

#### Scenario: Labels that cannot be matched

- **WHEN** the two sets of labels have nothing in common to match on
- **THEN** the chapters are matched in order, first to first

#### Scenario: An offset is detected rather than asked for

- **WHEN** the chapters must be matched in order because the labels carry nothing to match on, and the
  two lists start at different places
- **THEN** the app determines the offset itself, by choosing the alignment under which the amount of
  text per minute of audio is most consistent across the whole book
- **AND** the user is not required to discover or enter the number

#### Scenario: Correcting a matching that is off by a constant

- **WHEN** every chapter is matched to a text section a fixed number of chapters away from the right
  one
- **THEN** the user can shift the matching by a whole number of chapters, overriding whatever the app
  detected
- **AND** the correction applies to the whole book rather than to one chapter

#### Scenario: The correction is remembered

- **WHEN** the user has corrected a book's chapter matching and reopens the book later
- **THEN** the correction is still in effect
- **AND** another book's matching is unaffected

### Requirement: Read-along is turned on and off per book

The reader SHALL offer a control that turns read-along on and off, remembered per book across
restarts, and on by default for a book that supports it.

#### Scenario: A book that has never been configured

- **WHEN** the user opens the reader for a book that supports read-along and has never had it
  configured
- **THEN** read-along is on

#### Scenario: Turning it off

- **WHEN** the user turns read-along off
- **THEN** the text stops following the narration
- **AND** the reader behaves as it does for a book with read-along unavailable

#### Scenario: Turning it back on

- **WHEN** the user turns read-along back on
- **THEN** the reader moves to the place the audio is

#### Scenario: The setting survives a restart

- **WHEN** the user turns read-along off, force-stops the app, and reopens the reader
- **THEN** read-along is still off

#### Scenario: The setting is per book

- **WHEN** the user turns read-along off for one book
- **THEN** other books are unaffected

### Requirement: Read-along states when it cannot work

The app SHALL state that read-along is unavailable, and why, when the audio carries no chapter marks
or the ebook provides no table of contents, rather than offering a control that appears to work and
does nothing. Nothing else about the book SHALL be affected.

#### Scenario: The audio has no chapter marks

- **WHEN** the user opens the reader for a book whose audio carries no chapter marks
- **THEN** read-along is shown as unavailable, with the reason

#### Scenario: One chapter spanning the whole book

- **WHEN** a book's audio carries a single chapter mark covering the entire book
- **THEN** it is treated the same as carrying no chapter marks

#### Scenario: The ebook has no table of contents

- **WHEN** the linked ebook provides no navigation document
- **THEN** read-along is shown as unavailable, with the reason

#### Scenario: Everything else still works

- **WHEN** read-along is unavailable for a book
- **THEN** the audio still plays normally
- **AND** the ebook still opens, scrolls, and remembers where it was read
