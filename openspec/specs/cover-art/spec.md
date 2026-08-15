# cover-art Specification

## Purpose
TBD - created by archiving change add-m4b-books. Update Purpose after archive.
## Requirements
### Requirement: Embedded cover art is extracted when a book is added

The app SHALL read a book's embedded artwork when the book is added and SHALL store a
locally cached image for it. Extraction SHALL NOT prevent a book from being added, and a book
with no embedded artwork SHALL be added normally.

#### Scenario: Source carries embedded artwork

- **WHEN** an `.m4b` containing embedded cover art is added
- **THEN** a cached cover image is stored for that book

#### Scenario: Source carries no embedded artwork

- **WHEN** a book whose source has no embedded artwork is added
- **THEN** the book is added with no cached cover
- **AND** no error is reported

#### Scenario: Artwork cannot be decoded

- **WHEN** a book's embedded artwork is present but cannot be decoded as an image
- **THEN** the book is still added
- **AND** it is treated as having no cover

### Requirement: Cover art is cached rather than read from the source repeatedly

The cached cover SHALL be a downsampled image held in the app's own storage, sized for display
rather than at the source's full resolution. Displaying a book's cover SHALL NOT require opening
its audio source, and the cache SHALL persist across app restarts without re-reading the source.

#### Scenario: Library is displayed

- **WHEN** the library screen shows books that have cached covers
- **THEN** the covers are displayed from the cache
- **AND** no audiobook source file is opened to render the list

#### Scenario: Reopening the app

- **WHEN** the app is closed and reopened
- **THEN** previously cached covers are still displayed
- **AND** no artwork is extracted again

#### Scenario: A large embedded image

- **WHEN** a source contains artwork substantially larger than the display needs
- **THEN** the cached image is reduced in size rather than kept at full resolution

### Requirement: The source image and audio file are never modified

Caching a cover SHALL read from the user's file only. The app SHALL NOT write to, move, or
delete any source file, and SHALL NOT store a full-resolution duplicate of the user's artwork.

#### Scenario: After a book is added

- **WHEN** a book with embedded artwork has been added
- **THEN** its source file is unchanged
- **AND** no copy of the source audio has been made

### Requirement: A book without a cover shows a placeholder

Wherever a cover is displayed, a book that has no cached cover SHALL show a generic placeholder
rather than blank space or a broken image, and SHALL remain fully usable.

#### Scenario: Book with no cover in the library

- **WHEN** a book without a cached cover appears in the library
- **THEN** a placeholder is shown in place of the cover
- **AND** the book's title and chapter count are shown as usual

#### Scenario: Book with no cover on the Player

- **WHEN** a book without a cached cover is open in the Player
- **THEN** a placeholder is shown
- **AND** every playback control behaves normally

#### Scenario: Cached cover file is missing

- **WHEN** a book's cached cover file has been deleted from underneath the app
- **THEN** the placeholder is shown rather than an error or a crash

### Requirement: Covers appear in the library and on the Player

The library list SHALL show each book's cover alongside its title, and the Player SHALL show the
cover of the book it is playing.

#### Scenario: Library list

- **WHEN** the library contains books with cached covers
- **THEN** each book's row shows its own cover

#### Scenario: Player screen

- **WHEN** a book with a cached cover is opened in the Player
- **THEN** that book's cover is displayed

#### Scenario: Scrolling a large library

- **WHEN** the user scrolls a library of many books with covers
- **THEN** the list stays responsive
- **AND** covers load without blocking scrolling

### Requirement: Removing a book removes its cached cover

Removing a book from the library SHALL delete the cover cached for it, leaving no orphaned
image behind, while leaving the user's source files untouched.

#### Scenario: Removing a book with a cover

- **WHEN** a book that has a cached cover is removed from the library
- **THEN** its cached cover is deleted
- **AND** its source file still exists and is unmodified

#### Scenario: Re-adding a removed book

- **WHEN** a book that was removed is added again
- **THEN** its cover is extracted and cached again
- **AND** it displays as it did before

