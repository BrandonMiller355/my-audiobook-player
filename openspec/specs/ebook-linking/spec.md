# ebook-linking Specification

## Purpose

Associating one EPUB with one audiobook: picking it, holding the read grant across restarts,
changing it, unlinking it, and behaving sanely when the file or the grant has gone.

EPUB is the only format, and that is settled rather than pending: MOBI and AZW3 ship
DRM-protected, and DRM is a PRD §3 non-goal. A protected EPUB is refused with a message rather
than worked around. See [[ebook-reader]] for what happens once a book is linked.

## Requirements
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

The reader SHALL offer a way to replace the linked ebook with a different file, and — for a book
with audio — a way to remove the link entirely. Unlinking SHALL NOT delete the EPUB file. A book
with no audio SHALL NOT be unlinkable, since it would be left with nothing to open; it leaves the
library by being removed.

#### Scenario: Changing the ebook

- **WHEN** the user chooses to change the ebook from the reader
- **THEN** the file picker opens
- **AND** choosing a different EPUB replaces the link and opens the new ebook at its beginning

#### Scenario: Unlinking

- **WHEN** the user unlinks the ebook
- **THEN** the reader closes and returns to the Player
- **AND** the ebook icon returns to its unlinked form
- **AND** the EPUB file itself is not deleted

#### Scenario: A book with no audio

- **WHEN** the user opens the reader's menu for a book with no audio
- **THEN** changing the ebook is offered
- **AND** unlinking it is not

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

### Requirement: An ebook can be added without an audiobook

The app SHALL let the user add an EPUB to the library as a book of its own, with no audio. It SHALL
be checked when picked, as a linked ebook is, and SHALL be remembered across restarts. The EPUB file
SHALL NOT be copied, modified, or moved.

#### Scenario: Adding an ebook on its own

- **WHEN** the user adds an EPUB from the library's add control
- **THEN** it appears in the library as a book, titled from the EPUB
- **AND** tapping it opens the reader on that ebook

#### Scenario: A protected or unreadable file is refused

- **WHEN** the user picks a DRM-protected EPUB, or a file that is not an EPUB
- **THEN** the user is told why
- **AND** nothing is added to the library

#### Scenario: It survives a restart

- **WHEN** the user adds an ebook on its own, force-stops the app, and reopens it
- **THEN** the book is still listed and opens without asking for the file again

### Requirement: An ebook added on its own can gain its audiobook

The reader SHALL offer, for a book with no audio, a way to add its audiobook as a folder or a single
`.m4b`, read by the same rules the library's add control uses. The audio SHALL be added to the same
book, keeping its title and reading position, and SHALL NOT create a second library entry.

#### Scenario: Adding the audiobook

- **WHEN** the user chooses "Add audiobook" in the reader of a book with no audio and picks a folder
  or `.m4b` the library would accept
- **THEN** the Player for that book opens in place of the reader
- **AND** the library lists one book, now with its chapters
- **AND** the Player's ebook control opens the reader at the place the user had been reading

#### Scenario: The audio is refused

- **WHEN** the picked folder has no supported audio, or the picked file is not a readable `.m4b`
- **THEN** the user is told why
- **AND** the book remains an ebook with no audio

#### Scenario: Adding is abandoned

- **WHEN** the user opens either picker and dismisses it
- **THEN** the book is unchanged

### Requirement: The reader does not touch playback for a book with no audio

For a book with no audio, the reader SHALL NOT offer play/pause or a bookmark, and SHALL NOT start,
stop, pause, or reposition whatever the app may be playing.

#### Scenario: Another book is playing

- **WHEN** another book is playing and the user opens the reader of a book with no audio
- **THEN** the other book keeps playing
- **AND** the reader shows no play control and no bookmark action

