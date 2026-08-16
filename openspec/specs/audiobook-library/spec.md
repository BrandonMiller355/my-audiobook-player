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

The app SHALL take a persistable read permission on whatever the user selected — a folder or a
single file — and SHALL store the book and its chapters locally, so that the library is intact
after the process is killed and after the device restarts, with no re-picking.

#### Scenario: Reopening after the process is killed

- **WHEN** a book has been added, the app process is killed, and the app is launched again
- **THEN** the book is still listed with its chapters

#### Scenario: After a device reboot

- **WHEN** the device is restarted and the app is opened
- **THEN** previously added books are still listed
- **AND** their files are still readable without the user selecting the folder or file again

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

Removing a book SHALL delete only the app's own record of it, together with anything the app
derived and cached for it, and SHALL release the persisted URI permission taken when it was
added. The user's audio files, and the folder they live in, SHALL be left untouched.

The one file the app MAY delete on removal is its own copy of its own bundled asset, held in the
app-private directory it created for that purpose. That file was never the user's; deleting it
reclaims space the app spent, and the containment check that permits it SHALL be by directory,
never by file name, URI text, or source type.

#### Scenario: Removing a folder book

- **WHEN** the user removes a folder book from the library
- **THEN** the book disappears from the library
- **AND** the source folder and every audio file in it still exist and are unmodified

#### Scenario: Removing a single-file book

- **WHEN** the user removes a single-file book from the library
- **THEN** the book disappears from the library
- **AND** the source file still exists and is unmodified

#### Scenario: Removing the bundled sample

- **WHEN** the user removes the bundled sample from the library
- **THEN** the book disappears from the library
- **AND** the app's copy of it in app-private storage is deleted
- **AND** no file outside that directory is touched

#### Scenario: The permission grant is released

- **WHEN** a book is removed
- **THEN** the persisted URI permission taken for its folder or file is released
- **AND** the app's persisted permission grants do not accumulate for removed books

#### Scenario: Re-adding a removed book

- **WHEN** a removed book's folder or file is selected again
- **THEN** it is added to the library as normal

### Requirement: A book whose source becomes unavailable fails readably

The app SHALL report a book whose source folder or file has been deleted, moved, or has had its
permission revoked in a readable way, SHALL NOT crash, and SHALL keep the library entry
removable.

Availability SHALL be judged by what the source actually is: for a folder or file chosen through
the Storage Access Framework, whether the persisted read permission is still held; for a file in
app-private storage, whether that file still exists. A book of the second kind SHALL NOT be
reported unavailable merely for holding no SAF grant, which it can never hold.

#### Scenario: Source folder deleted after being added

- **WHEN** a book's source folder no longer exists and the user opens the library
- **THEN** the app does not crash
- **AND** the book can still be removed from the library

#### Scenario: Source file deleted after being added

- **WHEN** a single-file book's source file no longer exists and the user opens the library
- **THEN** the app does not crash
- **AND** the book is reported as unavailable
- **AND** it can still be removed from the library

#### Scenario: Permission revoked

- **WHEN** the persisted permission for a book's folder or file is no longer held
- **THEN** the app reports that the book is unavailable rather than failing silently or crashing

#### Scenario: A book held in app-private storage

- **WHEN** the library shows a book whose source is a file in app-private storage
- **THEN** it is reported available while that file exists
- **AND** unavailable once it does not

### Requirement: Adding a book does not block the user interface

Reading a source SHALL happen off the main thread — scanning a folder's files, or reading a
single file's chapters and artwork — as SHALL storing the result, and a book SHALL be stored as
a whole or not at all.

#### Scenario: Adding a folder with many files

- **WHEN** the user adds a folder containing over a hundred audio files
- **THEN** the app remains responsive while it is scanned
- **AND** the book appears in the library when scanning completes

#### Scenario: Adding a large single file

- **WHEN** the user adds a multi-gigabyte single-file book
- **THEN** the app remains responsive while its chapters and artwork are read
- **AND** the book appears in the library when that completes

#### Scenario: Failure partway through storing

- **WHEN** storing a book's chapters fails partway through
- **THEN** no partially scanned book is left in the library

### Requirement: The add control offers both a folder and a single file

The Library screen SHALL let the user choose between adding a folder of audio files and adding a
single file, rather than assuming one of them. Both choices SHALL lead to the same library list
and the same Player.

#### Scenario: Choosing what to add

- **WHEN** the user activates the add control
- **THEN** both "add a folder" and "add a single file" are offered
- **AND** choosing either opens the corresponding system picker

#### Scenario: Dismissing the choice

- **WHEN** the user dismisses the choice without picking either
- **THEN** no picker opens and the library is unchanged

#### Scenario: Both book types in one library

- **WHEN** the library contains both a folder book and a single-file book
- **THEN** both are listed together in the same list
- **AND** tapping either opens the Player for it

