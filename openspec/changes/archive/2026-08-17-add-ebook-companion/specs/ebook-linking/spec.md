## ADDED Requirements

### Requirement: An audiobook can have one EPUB linked to it

The app SHALL let the user associate a single EPUB file with an audiobook already in the library,
and SHALL remember that association across app restarts. The EPUB file SHALL NOT be copied,
modified, or moved.

#### Scenario: Linking an ebook

- **WHEN** the user taps the ebook icon on the Player's cover art for a book with no ebook linked
- **THEN** the system file picker opens
- **AND** choosing an EPUB links it to that audiobook
- **AND** the reader opens on the newly linked ebook

#### Scenario: The link survives a restart

- **WHEN** the user links an ebook, force-stops the app, and reopens the book
- **THEN** the ebook is still linked
- **AND** it opens without asking the user to pick the file again

#### Scenario: The source file is left alone

- **WHEN** an ebook is linked
- **THEN** the EPUB file at its original location is unchanged
- **AND** no copy of it is written into app storage

#### Scenario: One ebook at a time

- **WHEN** an audiobook already has an ebook linked and the user links a different one
- **THEN** the new ebook replaces the previous one
- **AND** the reading position from the previous ebook is discarded

### Requirement: The ebook icon states whether an ebook is linked

The Player's cover art SHALL carry an ebook control whose appearance distinguishes "no ebook
linked" from "ebook linked", so that the user knows before tapping whether it opens a file picker
or the reader.

#### Scenario: No ebook linked

- **WHEN** the Player shows a book with no ebook linked
- **THEN** the ebook icon is shown in its unlinked form
- **AND** tapping it opens the file picker

#### Scenario: An ebook is linked

- **WHEN** the Player shows a book with an ebook linked
- **THEN** the ebook icon is shown in its linked form
- **AND** tapping it opens the reader

#### Scenario: The icon stays legible over any cover

- **WHEN** the cover art behind the icon is very light or very dark
- **THEN** the icon remains legible against it

### Requirement: A linked ebook can be changed or unlinked

The reader SHALL offer a way to replace the linked ebook with a different file, and a way to remove
the link entirely. Unlinking SHALL NOT delete the EPUB file.

#### Scenario: Changing the ebook

- **WHEN** the user chooses to change the ebook from the reader
- **THEN** the file picker opens
- **AND** choosing a different EPUB replaces the link and opens the new ebook at its beginning

#### Scenario: Unlinking

- **WHEN** the user unlinks the ebook
- **THEN** the reader closes and returns to the Player
- **AND** the ebook icon returns to its unlinked form
- **AND** the EPUB file itself is not deleted

### Requirement: Only EPUB files are accepted

The app SHALL accept EPUB files and SHALL reject anything else with a readable message rather than
opening a broken reader. Acceptance SHALL be decided by reading the file rather than by trusting
the MIME type the picker reports.

#### Scenario: An EPUB reported under an unexpected MIME type

- **WHEN** the picker reports a `.epub` file as `application/octet-stream` or `application/zip`
- **THEN** the file is still offered in the picker and still accepted

#### Scenario: A file that is not an EPUB

- **WHEN** the user picks a PDF, an MP3, or any other non-EPUB file
- **THEN** the app shows a message saying the file is not an EPUB
- **AND** no link is created

#### Scenario: A DRM-protected EPUB

- **WHEN** the user picks an EPUB that is encrypted
- **THEN** the app shows a message saying the ebook is protected and cannot be opened
- **AND** no link is created
- **AND** the app makes no attempt to decrypt it

#### Scenario: A corrupt or unreadable EPUB

- **WHEN** the user picks a file that is a ZIP but is not a well-formed EPUB
- **THEN** the app shows a readable message
- **AND** the app does not crash

### Requirement: A linked ebook that becomes unavailable is reported, not crashed on

If the EPUB file is deleted, moved, or the persistable read grant is lost, the app SHALL say so and
SHALL offer to link a different ebook.

#### Scenario: The file has been deleted

- **WHEN** the user opens the reader for a book whose EPUB no longer exists
- **THEN** the reader shows that the ebook is unavailable
- **AND** offers to link a different one

#### Scenario: The read grant has been revoked

- **WHEN** the persistable URI permission for the EPUB is no longer held
- **THEN** the reader shows that the ebook is unavailable rather than failing silently or crashing

#### Scenario: Playback is unaffected

- **WHEN** a linked ebook is unavailable
- **THEN** the audiobook still plays normally
