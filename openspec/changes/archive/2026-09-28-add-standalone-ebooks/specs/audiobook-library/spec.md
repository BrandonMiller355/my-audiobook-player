## ADDED Requirements

### Requirement: A book with no audio is listed as such and opens in the reader

A book added as an ebook alone SHALL be listed with the other books, SHALL say that it has no audio
in place of a chapter count, and SHALL open in the reader rather than the Player. Its availability
SHALL be judged by its ebook's permission, and removing it SHALL give that permission back and
leave the EPUB file untouched.

#### Scenario: Opening a book with no audio

- **WHEN** the user taps a book that has no audio
- **THEN** the reader opens on its ebook

#### Scenario: Its ebook is lost

- **WHEN** the permission for a book-with-no-audio's EPUB is no longer held
- **THEN** the book is reported unavailable
- **AND** it can still be removed

## MODIFIED Requirements

### Requirement: The add control offers both a folder and a single file

The Library screen SHALL let the user choose between adding a folder of audio files and adding a
single file, rather than assuming one of them, and SHALL also offer adding an ebook on its own. Both
audio choices SHALL lead to the same library list and the same Player.

#### Scenario: Choosing what to add

- **WHEN** the user activates the add control
- **THEN** "add a folder", "add a single file", and "add an ebook" are offered
- **AND** choosing any of them opens the corresponding system picker

#### Scenario: Dismissing the choice

- **WHEN** the user dismisses the choice without picking any
- **THEN** no picker opens and the library is unchanged

#### Scenario: Both book types in one library

- **WHEN** the library contains both a folder book and a single-file book
- **THEN** both are listed together in the same list
- **AND** tapping either opens the Player for it

### Requirement: The empty library offers both add paths directly

When the library is empty, the Library screen SHALL offer the folder picker, the single-file picker,
and the ebook picker as distinct controls that each open their picker in one tap, without going
through the add menu first.

#### Scenario: Choosing a folder from the empty state

- **WHEN** the library is empty and the user taps the folder control
- **THEN** the system folder picker opens directly

#### Scenario: Choosing a single file from the empty state

- **WHEN** the library is empty and the user taps the single-file control
- **THEN** the system file picker opens directly

#### Scenario: Choosing an ebook from the empty state

- **WHEN** the library is empty and the user taps the ebook control
- **THEN** the system file picker opens directly

#### Scenario: Dismissing a picker from the empty state

- **WHEN** the user dismisses any picker without choosing anything
- **THEN** the library is unchanged
- **AND** the empty state is still shown
