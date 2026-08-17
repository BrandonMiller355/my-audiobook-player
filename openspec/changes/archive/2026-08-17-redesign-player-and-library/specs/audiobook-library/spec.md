## MODIFIED Requirements

### Requirement: The library lists the books that have been added

The Library screen SHALL show each added book with its title, its number of chapters, and its
total duration, and SHALL open the Player for a book when it is tapped.

A book's total duration SHALL be shown only when it is actually known. A folder book's chapter
durations are not read when it is added; until they have been read, the book SHALL list its
chapter count alone rather than a total that is understated or still growing.

#### Scenario: Library with books

- **WHEN** the library contains one or more books and the Library screen is shown
- **THEN** each book is listed with its title and chapter count
- **AND** the empty state is not shown

#### Scenario: A book whose duration is known

- **WHEN** a book's chapter durations are all known
- **THEN** its row shows its total duration alongside its chapter count

#### Scenario: A folder book that has never been opened

- **WHEN** a folder book has been added but not yet opened
- **THEN** its row shows its chapter count
- **AND** no total duration is shown for it
- **AND** no placeholder or zero duration is shown in place of one

#### Scenario: A folder book's duration becomes known

- **WHEN** a folder book has been opened, so that its chapter durations have been read
- **THEN** its row shows its total duration from then on
- **AND** reopening the app does not require reading them again

#### Scenario: Opening a book

- **WHEN** the user taps a book in the list
- **THEN** the app navigates to the Player destination for that book
- **AND** the Player receives that book's identifier

#### Scenario: Library with no books

- **WHEN** the library contains no books
- **THEN** the empty state is shown together with the add control

## ADDED Requirements

### Requirement: The library offers the book in progress for immediate resumption

When a book has a saved playback position, the Library screen SHALL present that book separately
from the list, showing its cover, its title, how much of it remains, and how far through it the
user is. That entry SHALL provide a playback control that starts or pauses the book **without
leaving the Library screen**, and tapping the entry itself SHALL open the Player for it.

Where more than one book has a saved position, the most recently played SHALL be the one
presented. A book presented this way SHALL NOT also appear in the list below it, and SHALL remain
removable from the entry itself — a library holding a single book presents that book here and
nowhere else.

#### Scenario: A book has been played before

- **WHEN** the library contains a book with a saved playback position and the Library screen is shown
- **THEN** that book is presented above the list with its cover, title, remaining time, and progress
- **AND** it does not also appear in the list below

#### Scenario: Resuming without navigating

- **WHEN** the user taps the playback control on that entry
- **THEN** the book begins playing from its saved position
- **AND** the app stays on the Library screen

#### Scenario: Pausing from the library

- **WHEN** that book is playing and the user taps the same control
- **THEN** playback pauses
- **AND** the app stays on the Library screen

#### Scenario: Opening the book in progress

- **WHEN** the user taps the entry itself rather than its playback control
- **THEN** the Player opens for that book
- **AND** playback is neither started nor stopped by the navigation

#### Scenario: Several books have been played

- **WHEN** more than one book has a saved position
- **THEN** the most recently played of them is the one presented

#### Scenario: Nothing has been played yet

- **WHEN** no book in the library has a saved position
- **THEN** no such entry is shown
- **AND** the list begins at the top of the screen

#### Scenario: Removing the only book in the library

- **WHEN** the library holds one book, that book has been played, and the user long-presses the entry presenting it
- **THEN** the removal confirmation is shown for that book

#### Scenario: Progress cannot be expressed yet

- **WHEN** a book has a saved position but its total duration is not yet known
- **THEN** the app does not show a remaining time or a progress fraction it cannot compute
- **AND** the book is still resumable

### Requirement: The empty library offers both add paths directly

When the library is empty, the Library screen SHALL offer the folder picker and the single-file
picker as two distinct controls that each open their picker in one tap, without going through the
add menu first.

#### Scenario: Choosing a folder from the empty state

- **WHEN** the library is empty and the user taps the folder control
- **THEN** the system folder picker opens directly

#### Scenario: Choosing a single file from the empty state

- **WHEN** the library is empty and the user taps the single-file control
- **THEN** the system file picker opens directly

#### Scenario: Dismissing a picker from the empty state

- **WHEN** the user dismisses either picker without choosing anything
- **THEN** the library is unchanged
- **AND** the empty state is still shown

### Requirement: Removal is confirmed in a thumb-reachable sheet

Confirming the removal of a book SHALL be presented as a bottom sheet rather than a centered
dialog, so that its controls fall within reach of a thumb. The confirmation SHALL name the book
being removed and SHALL state that the user's files are not deleted. Dismissing the sheet, by any
means the platform offers, SHALL remove nothing.

#### Scenario: Long-pressing a book

- **WHEN** the user long-presses a book in the list
- **THEN** a bottom sheet asks whether to remove that book
- **AND** the sheet names the book
- **AND** the sheet states that the user's files stay where they are

#### Scenario: Confirming removal

- **WHEN** the user confirms removal from the sheet
- **THEN** the book is removed from the library
- **AND** the sheet closes

#### Scenario: Declining removal

- **WHEN** the user declines removal from the sheet
- **THEN** the book remains in the library
- **AND** the sheet closes

#### Scenario: Dismissing the sheet without answering

- **WHEN** the user dismisses the sheet by swiping it away or pressing back
- **THEN** the book remains in the library
