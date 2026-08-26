# notes-review Specification

## Purpose
Reviewing one book's marks and notes: the list, what each entry shows, playing the book from an entry, writing an entry up, editing it, and deleting it.

## Requirements

### Requirement: A book's marks and notes are listed on a screen of their own

The app SHALL provide a screen listing every mark and note belonging to one book, reached from the
Player for the book being played. The list SHALL be ordered by position in the book rather than by
when each entry was taken.

The screen SHALL list one book's entries only. There SHALL be no combined list across books.

#### Scenario: Opening the notes for a book

- **WHEN** the user marks a spot on the Player
- **THEN** the marks and notes for that book are listed, with the new one open for writing
- **AND** entries belonging to other books are not shown

#### Scenario: Ordering

- **WHEN** the list contains entries taken in a different order from the order they occur in the book
- **THEN** they are listed in the order they occur in the book

#### Scenario: A book with no notes

- **WHEN** the user opens the notes for a book that has none
- **THEN** the screen states that there are none and how to take one
- **AND** it does not present an empty list or an error

#### Scenario: The list reflects a new mark

- **WHEN** a mark is taken and the user then opens the notes screen
- **THEN** the new entry is present in book order

### Requirement: Each entry says where in the book it came from

Every entry SHALL show the chapter recorded on it, its position in the book as a timestamp, and its
text when it has any. An entry with no text SHALL be shown as a complete entry rather than as a blank
row, and SHALL be distinguishable from one that carries text.

#### Scenario: A note with text

- **WHEN** an entry carries text
- **THEN** the row shows its chapter, its position in the book, and its text

#### Scenario: A mark with no text

- **WHEN** an entry carries no text
- **THEN** the row shows its chapter and its position in the book
- **AND** it reads as a mark awaiting text rather than as an empty note

#### Scenario: A long note

- **WHEN** an entry's text is longer than the row can show
- **THEN** the row remains readable and the full text is reachable

#### Scenario: A book whose chapter lengths are not yet known

- **WHEN** a folder book's chapter lengths have not all been resolved
- **THEN** each entry still shows its recorded chapter
- **AND** the position shown is consistent with what the Player's scrubber reports for the same book

### Requirement: Selecting an entry plays the book from there

Selecting an entry SHALL move playback to that entry's anchor and return the user to the Player.
Selecting an entry SHALL NOT start or stop playback: a paused book SHALL remain paused at the new
position, and a playing book SHALL continue playing from it.

#### Scenario: Seeking to a note while paused

- **WHEN** the user selects an entry while the book is paused
- **THEN** playback moves to that entry's anchor
- **AND** the book stays paused
- **AND** the Player is shown

#### Scenario: Seeking to a note while playing

- **WHEN** the user selects an entry while the book is playing
- **THEN** playback moves to that entry's anchor and keeps playing

#### Scenario: The passage that prompted the note plays back

- **WHEN** the user plays from a note taken during a passage
- **THEN** playback begins before that passage rather than after it

### Requirement: An entry's text can be added, changed, and removed

The user SHALL be able to give text to an entry that has none, change the text of one that has some,
and delete an entry outright. Editing text SHALL NOT move an entry's anchor or change its recorded
chapter, and SHALL NOT move it in the list.

Deleting an entry SHALL be confirmed, and SHALL remove only that entry.

#### Scenario: Adding text to a mark

- **WHEN** the user opens a mark with no text and enters some
- **THEN** the entry now shows that text
- **AND** it remains in the same place in the list at the same position in the book

#### Scenario: Editing a note

- **WHEN** the user changes the text of an entry
- **THEN** the new text is shown and kept
- **AND** the entry's chapter and position are unchanged

#### Scenario: Abandoning an edit

- **WHEN** the user leaves an edit without keeping it
- **THEN** the entry's previous text is retained

#### Scenario: Deleting an entry

- **WHEN** the user deletes an entry and confirms
- **THEN** that entry is removed from the list
- **AND** every other entry for that book remains

#### Scenario: Declining a deletion

- **WHEN** the user is asked to confirm a deletion and declines
- **THEN** the entry remains

#### Scenario: Edits survive a restart

- **WHEN** the app is closed and reopened after an entry has been edited or deleted
- **THEN** the change is still in effect

### Requirement: Leaving the notes screen returns to the Player

System back navigation from the notes screen SHALL return to the Player for the same book rather than
to the Library, and SHALL leave playback exactly as it was.

#### Scenario: Back from the notes screen

- **WHEN** the user presses the system back button on the notes screen
- **THEN** the Player for the same book is shown
- **AND** the Library is not shown

#### Scenario: Playback is untouched by the visit

- **WHEN** the user opens the notes screen and returns without selecting an entry
- **THEN** playback is in the same state and at the same position as before
