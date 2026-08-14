# m4b-books Specification

## Purpose
TBD - created by archiving change add-m4b-books. Update Purpose after archive.
## Requirements
### Requirement: A single `.m4b` file can be added as a book

The app SHALL let the user add a book by choosing one `.m4b` file with the Android Storage
Access Framework, taking a persistable read grant on that file so the book survives a reboot
without re-picking. A file SHALL be accepted based on its name ending in `.m4b`,
case-insensitively, rather than on the MIME type the document provider reports.

#### Scenario: Adding an `.m4b` from the library screen

- **WHEN** the user chooses to add a single file and picks an `.m4b`
- **THEN** it becomes a book in the library
- **AND** the book's title defaults to the file name without its extension

#### Scenario: Provider reports an unhelpful MIME type

- **WHEN** the picked `.m4b` is reported by its provider as `application/octet-stream`
- **THEN** it is still accepted

#### Scenario: A file that is not an `.m4b` is picked

- **WHEN** the user picks a file whose name does not end in `.m4b`
- **THEN** no book is added
- **AND** a readable message explains that only `.m4b` files can be added this way
- **AND** the read grant taken for that file is released rather than retained

#### Scenario: The user cancels the file picker

- **WHEN** the user opens the file picker and dismisses it without choosing
- **THEN** the library is unchanged and no error is shown

#### Scenario: The book survives a restart

- **WHEN** an `.m4b` book has been added and the device is restarted
- **THEN** the book is still listed and its file is still readable without re-picking it

### Requirement: Chapters are extracted once, when the book is added

The app SHALL read an `.m4b`'s embedded chapter marks at the moment it is added and SHALL store
them, so that opening the book later does not re-read the container. Stored chapters SHALL carry
their start and end positions within the file and SHALL all reference that one file.

#### Scenario: A chaptered file is added

- **WHEN** an `.m4b` carrying embedded chapter marks is added
- **THEN** one chapter is stored per mark, in order, each with its start and end position
- **AND** every stored chapter refers to the same source file

#### Scenario: Opening the book again

- **WHEN** a previously added `.m4b` book is opened
- **THEN** its chapters come from what was stored
- **AND** the file's chapter data is not parsed again

#### Scenario: Chapter count is shown

- **WHEN** an `.m4b` book with embedded chapters is listed in the library
- **THEN** its chapter count reflects the number of marks that were found

### Requirement: A file with no usable chapters is still a usable book

An `.m4b` reported as unchaptered SHALL become a book with exactly one chapter spanning the
whole file. An `.m4b` whose chapter data cannot be read SHALL also become such a book, and the
user SHALL be told that its chapters could not be read. Neither outcome SHALL prevent the book
from being added or played.

#### Scenario: File carries no chapter marks

- **WHEN** an `.m4b` with no chapter data is added
- **THEN** the book is added with one chapter covering the whole file
- **AND** no error is shown, because a file without chapter marks is not a failure

#### Scenario: File carries a single mark spanning its whole duration

- **WHEN** an `.m4b` whose only chapter mark covers the entire file is added
- **THEN** the book is added with one chapter covering the whole file

#### Scenario: Chapter data cannot be read

- **WHEN** an `.m4b` whose chapter metadata is malformed is added
- **THEN** the book is still added with one chapter covering the whole file
- **AND** a readable message says its chapters could not be read
- **AND** the book plays normally

#### Scenario: The file cannot be seeked

- **WHEN** the document provider supplies the file in a form that cannot be seeked
- **THEN** the book is added as a single-chapter book rather than rejected

### Requirement: An `.m4b` book plays as one media item

Playback of an `.m4b` SHALL use a single media item for the whole file, and chapter navigation
SHALL be seeking within that item. The app SHALL NOT split, re-encode, or otherwise modify the
source file.

#### Scenario: Starting an `.m4b` book

- **WHEN** an `.m4b` book is opened and played
- **THEN** the whole file is loaded as one media item
- **AND** playback runs continuously across chapter boundaries without reloading

#### Scenario: The source file is not modified

- **WHEN** an `.m4b` book has been added and played
- **THEN** the source file is byte-for-byte unchanged
- **AND** no derived audio file has been written

### Requirement: Chapter navigation and seeking work within the single item

Previous chapter, next chapter, the fixed-interval seeks, and the book-wide scrubber SHALL
operate on an `.m4b` book's stored chapter boundaries, and SHALL behave the same whether the
command comes from the app's controls, the media notification, or a Bluetooth device.

#### Scenario: Next chapter within the file

- **WHEN** next chapter is pressed partway through a chapter of an `.m4b` book
- **THEN** playback moves to the start of the following chapter within the same file

#### Scenario: Previous chapter obeys the 3-second rule

- **WHEN** previous chapter is pressed more than 3 seconds into a chapter of an `.m4b` book
- **THEN** the current chapter restarts from its own start position, not the start of the file

#### Scenario: A Bluetooth command navigates chapters

- **WHEN** a next-track or previous-track command arrives from a Bluetooth device while an
  `.m4b` book is playing
- **THEN** it moves between that book's embedded chapters rather than doing nothing

#### Scenario: Seeking across a chapter boundary

- **WHEN** a forward seek from near the end of a chapter would pass its end
- **THEN** playback continues into the next chapter at the correct offset

#### Scenario: The scrubber spans the whole file

- **WHEN** an `.m4b` book is open
- **THEN** the progress control spans the whole book
- **AND** its total reflects the file's full duration rather than only the current chapter

### Requirement: The Player identifies the current chapter of an `.m4b` book

The Player SHALL show the title, number, and total count of the current chapter taken from the
book's stored chapters, so that a single-item book reports its chapter rather than its file.

#### Scenario: Chapter title while playing a chaptered file

- **WHEN** playback is inside the third chapter of a chaptered `.m4b` book
- **THEN** the Player shows that chapter's stored title
- **AND** shows it as chapter 3 of the book's total

#### Scenario: Crossing into the next chapter during playback

- **WHEN** playback runs past a chapter boundary without any seek
- **THEN** the displayed chapter title and number update to the new chapter

#### Scenario: Single-chapter file

- **WHEN** an `.m4b` book that has no usable chapter marks is open
- **THEN** the Player shows it as a single chapter rather than showing nothing

### Requirement: An `.m4b` book resumes where it was left

Saved progress for an `.m4b` book SHALL restore both the correct chapter and the correct
position within it when the book is reopened, without starting playback.

#### Scenario: Reopening partway through a later chapter

- **WHEN** an `.m4b` book is played into a later chapter, closed, and opened again
- **THEN** playback is positioned at approximately where it was left
- **AND** the Player reports the chapter containing that position
- **AND** playback does not begin until the user presses play

