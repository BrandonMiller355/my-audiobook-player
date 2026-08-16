# sample-library Specification

## Purpose

The one audiobook the app carries itself, so that a library which has never had a book added to it
is not simply empty. Covers what ships in the APK, when it is seeded, what removing it does, and
the boundary that keeps "the app may delete its own copy of its own asset" from ever becoming "the
app may delete the user's files".

## Requirements
### Requirement: The application ships with one sample audiobook

The APK SHALL contain exactly one bundled audiobook, a public-domain `.m4b`, as an application
asset. The bundled file SHALL be the application's own content and SHALL NOT be derived from,
copied from, or written over any file belonging to the user.

#### Scenario: The sample ships in every build variant

- **WHEN** either a debug or a release APK is built
- **THEN** the bundled sample is present in it

### Requirement: The sample is added to the library on first open

The first time the Library is opened after installation, the application SHALL copy the bundled
asset into app-private storage and add it to the library as an ordinary `.m4b` book.

The copy SHALL be read with the same reader used for a user-picked `.m4b`, so the seeded book
carries its chapter marks, its duration, and its title derived from the file name.

#### Scenario: First launch after install

- **WHEN** the Library is opened for the first time after installation
- **THEN** the sample is copied into app-private storage
- **AND** it appears in the library as a book with its embedded chapters
- **AND** the user interface is not blocked while that happens

#### Scenario: The seeded book is an ordinary book

- **WHEN** the seeded sample is opened in the Player
- **THEN** it plays, navigates chapters, scrubs, changes speed, and resumes exactly as a book added
  through the file picker does

#### Scenario: Installing over an existing library

- **GIVEN** a library that already contains books added by the user
- **WHEN** the sample is seeded
- **THEN** it is added alongside them and no existing book is altered

### Requirement: The sample is seeded at most once

The application SHALL record that seeding has occurred and SHALL NOT seed the sample again, whether
or not the book is still in the library.

The record SHALL be written only after the book has been committed to the library, so that a
failure partway through seeding is retried rather than silently skipped forever.

#### Scenario: Later launches

- **WHEN** the Library is opened on any launch after the first
- **THEN** no copy is made and no book is added

#### Scenario: The user removed it

- **GIVEN** the user removed the sample from the library
- **WHEN** the Library is opened again
- **THEN** the sample does not come back

#### Scenario: Seeding is interrupted

- **GIVEN** seeding failed before the book was committed
- **WHEN** the Library is next opened
- **THEN** seeding is attempted again

### Requirement: Removing the sample deletes its copied file

Removing the sample from the library SHALL delete the application's copy of it from app-private
storage, reclaiming the space.

The application SHALL delete a source file only when that file lies inside the app-private
directory it created for the sample. A book whose source lies anywhere else SHALL have its library
row removed and its file left untouched.

#### Scenario: Removing the sample

- **WHEN** the user removes the seeded sample
- **THEN** its library row is gone
- **AND** the copied file is deleted from app-private storage

#### Scenario: Removing a user's own book

- **WHEN** the user removes a book they added themselves
- **THEN** no file anywhere on the device is deleted

### Requirement: The sample's source is available while its file exists

A book whose source is a file in app-private storage SHALL be treated as available when that file
exists, and as unavailable when it does not. Availability for such a book SHALL NOT depend on a
persistable URI permission, which no bundled file can hold.

#### Scenario: The sample lists normally

- **WHEN** the seeded sample is shown in the library
- **THEN** it shows its chapter count, not "Source unavailable"

#### Scenario: The copied file has gone missing

- **GIVEN** the sample's copied file no longer exists
- **WHEN** the library is shown
- **THEN** the book lists as unavailable and can still be removed
