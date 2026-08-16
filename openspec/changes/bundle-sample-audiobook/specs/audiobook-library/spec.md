# audiobook-library Specification

## MODIFIED Requirements

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
