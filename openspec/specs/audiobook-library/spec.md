# audiobook-library Specification

## Purpose
TBD - created by archiving change add-folder-audiobooks. Update Purpose after archive.
## Requirements
### Requirement: The user can add a folder audiobook through the system picker

The app SHALL let the user add a book by choosing a folder with the Android Storage Access
Framework, and SHALL NOT request any broad storage permission.

#### Scenario: Adding a folder from the library screen

- **WHEN** the user taps the add control and selects a folder containing audio files
- **THEN** the folder becomes an audiobook in the library
- **AND** the book's title defaults to the folder name
- **AND** each chapter's title defaults to its file name without the extension

#### Scenario: No storage permission is requested

- **WHEN** the user adds a book
- **THEN** no runtime storage permission dialog is shown
- **AND** the app declares no storage permission in its manifest

#### Scenario: The user cancels the picker

- **WHEN** the user opens the folder picker and dismisses it without choosing
- **THEN** the library is unchanged and no error is shown

### Requirement: The library survives restart and reboot

The app SHALL take a persistable read permission on the selected folder and SHALL store the book
and its chapters locally, so that the library is intact after the process is killed and after the
device restarts, with no re-picking.

#### Scenario: Reopening after the process is killed

- **WHEN** a book has been added, the app process is killed, and the app is launched again
- **THEN** the book is still listed with its chapters

#### Scenario: After a device reboot

- **WHEN** the device is restarted and the app is opened
- **THEN** previously added books are still listed
- **AND** their files are still readable without the user selecting the folder again

#### Scenario: Source files are never copied

- **WHEN** a book is added
- **THEN** the app stores references to the user's files
- **AND** it does not copy the audio files into app storage

### Requirement: The library lists the books that have been added

The Library screen SHALL show each added book with its title and its number of chapters, and SHALL
open the Player for a book when it is tapped.

#### Scenario: Library with books

- **WHEN** the library contains one or more books and the Library screen is shown
- **THEN** each book is listed with its title and chapter count
- **AND** the empty state is not shown

#### Scenario: Opening a book

- **WHEN** the user taps a book in the list
- **THEN** the app navigates to the Player destination for that book
- **AND** the Player receives that book's identifier

#### Scenario: Library with no books

- **WHEN** the library contains no books
- **THEN** the empty state is shown together with the add control

### Requirement: Removing a book never deletes the user's files

Removing a book SHALL delete only the app's own record of it and SHALL release the persisted URI
permission taken when it was added. The user's audio files and folder SHALL be left untouched.

#### Scenario: Removing a book

- **WHEN** the user removes a book from the library
- **THEN** the book disappears from the library
- **AND** the source folder and every audio file in it still exist and are unmodified

#### Scenario: The permission grant is released

- **WHEN** a book is removed
- **THEN** the persisted URI permission taken for its folder is released
- **AND** the app's persisted permission grants do not accumulate for removed books

#### Scenario: Re-adding a removed book

- **WHEN** a removed book's folder is selected again
- **THEN** it is added to the library as normal

### Requirement: A book whose source becomes unavailable fails readably

When a book's folder has been deleted, moved, or has had its permission revoked, the app SHALL
report it in a readable way and SHALL NOT crash, and the library entry SHALL remain removable.

#### Scenario: Source folder deleted after being added

- **WHEN** a book's source folder no longer exists and the user opens the library
- **THEN** the app does not crash
- **AND** the book can still be removed from the library

#### Scenario: Permission revoked

- **WHEN** the persisted permission for a book's folder is no longer held
- **THEN** the app reports that the book is unavailable rather than failing silently or crashing

### Requirement: Adding a book does not block the user interface

Scanning a folder and storing its chapters SHALL happen off the main thread, and a book SHALL be
stored as a whole or not at all.

#### Scenario: Adding a folder with many files

- **WHEN** the user adds a folder containing over a hundred audio files
- **THEN** the app remains responsive while it is scanned
- **AND** the book appears in the library when scanning completes

#### Scenario: Failure partway through storing

- **WHEN** storing a book's chapters fails partway through
- **THEN** no partially scanned book is left in the library

