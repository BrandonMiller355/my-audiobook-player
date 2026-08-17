## MODIFIED Requirements

### Requirement: A book-wide progress scrubber shows and sets absolute position

The Player screen SHALL show a single progress control spanning the whole book, not just the
current chapter, and SHALL show the target timestamp while the user is dragging it, committing the
seek only on release.

The control SHALL show both how far into the book playback has reached and how much of the book
remains, and SHALL mark the remaining figure as a countdown rather than leaving it
indistinguishable from the elapsed one. Both figures SHALL be rendered in a face whose digits are
of uniform width, so that the numbers do not shift horizontally as they change during playback.

The control SHALL be operable by assistive technology: it SHALL report its current position and
range, and SHALL accept a request to move to a position.

#### Scenario: Scrubber reflects playback

- **WHEN** the book is playing
- **THEN** the scrubber's position advances to reflect progress through the whole book, not just the current chapter

#### Scenario: Dragging shows a live timestamp without seeking yet

- **WHEN** the user drags the scrubber
- **THEN** a timestamp corresponding to the drag position is displayed
- **AND** playback position does not change until the drag is released

#### Scenario: Releasing the scrubber seeks

- **WHEN** the user releases the scrubber at a new position
- **THEN** playback seeks to the corresponding absolute position in the book, including into a different chapter if applicable

#### Scenario: Elapsed and remaining are distinguishable

- **WHEN** the scrubber is shown
- **THEN** the elapsed time and the remaining time are both displayed
- **AND** the remaining time is presented as a countdown rather than as a second elapsed figure

#### Scenario: Timestamps do not shift as they advance

- **WHEN** playback advances and the displayed digits change
- **THEN** the timestamps stay horizontally stable rather than reflowing as digit widths change

#### Scenario: Operated by a screen reader

- **WHEN** the scrubber is reached by assistive technology
- **THEN** its position within the book is reported
- **AND** it can be moved to a different position

### Requirement: Playback speed is adjustable without changing pitch

The user SHALL be able to select a playback speed from the set defined in PRD §9 (0.75x through
3.0x). Changing speed SHALL NOT audibly raise or lower the pitch of the narration.

Every speed in that set SHALL be reachable from the Player without leaving it, and the speed
currently in effect SHALL be visible on the Player itself rather than only while the control for
changing it is open. Selecting a speed SHALL take effect immediately and SHALL NOT dismiss the
control, so that a second adjustment can be made against what was just heard.

#### Scenario: Selecting a faster speed

- **WHEN** the user selects a speed faster than 1.0x
- **THEN** playback proceeds at that speed
- **AND** the narrator's voice pitch is not noticeably raised

#### Scenario: Selecting a slower speed

- **WHEN** the user selects a speed slower than 1.0x
- **THEN** playback proceeds at that speed
- **AND** the narrator's voice pitch is not noticeably lowered

#### Scenario: The current speed is visible without opening anything

- **WHEN** the Player screen is shown
- **THEN** the speed currently in effect is displayed on it

#### Scenario: Every stop is reachable

- **WHEN** the user opens the speed control
- **THEN** every speed in the defined set is offered
- **AND** the one currently in effect is shown as selected

#### Scenario: Adjusting twice in a row

- **WHEN** the user selects a speed
- **THEN** playback changes to it immediately
- **AND** the control stays open so another speed can be selected

## ADDED Requirements

### Requirement: Any chapter is reachable by name from the Player

The Player SHALL offer the book's chapters as a list, reached without leaving the Player screen,
showing each chapter's number, its title, and its duration. Selecting a chapter SHALL move
playback to the start of that chapter.

The list SHALL identify which chapter is currently playing and SHALL open positioned so that the
current chapter is visible without scrolling. Playback SHALL remain controllable while the list is
shown.

This is in addition to, and does not replace, previous- and next-chapter navigation, which
continues to be driven by system, notification, and Bluetooth controls under the 3-second rule.

#### Scenario: Opening the chapter list

- **WHEN** the user opens the chapter list from the Player
- **THEN** the book's chapters are listed with their numbers, titles, and durations
- **AND** the app stays on the Player screen

#### Scenario: The current chapter is identified

- **WHEN** the chapter list is shown
- **THEN** the chapter currently playing is distinguished from the others
- **AND** it is visible without the user scrolling to find it

#### Scenario: Jumping to a chapter

- **WHEN** the user selects a chapter from the list
- **THEN** playback moves to the start of that chapter
- **AND** the list closes

#### Scenario: Controlling playback from the list

- **WHEN** the chapter list is shown and the user taps its playback control
- **THEN** playback starts or pauses
- **AND** the list stays open

#### Scenario: A book with a single chapter

- **WHEN** the chapter list is opened for a book that has one chapter covering the whole file
- **THEN** that one chapter is listed
- **AND** selecting it moves playback to the start of the book

#### Scenario: Dismissing without choosing

- **WHEN** the user dismisses the chapter list without selecting a chapter
- **THEN** playback position is unchanged
