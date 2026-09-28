## ADDED Requirements

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

## MODIFIED Requirements

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
