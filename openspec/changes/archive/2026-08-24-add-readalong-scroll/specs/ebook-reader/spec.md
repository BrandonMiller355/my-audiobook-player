## MODIFIED Requirements

### Requirement: The reading position is remembered per book

The app SHALL remember where the user stopped reading in each book's ebook, and SHALL restore that
place when the reader is reopened. The stored position SHALL remain correct when text size, line
spacing, typeface, or screen orientation changes.

While read-along is on, the reading position and the playback position are the same position, and the
reader opens where the audio is rather than where reading stopped. The independence of the two
positions is a guarantee about read-along being off, not about the reader in general.

#### Scenario: Returning to the reader

- **WHEN** the user scrolls partway into an ebook with read-along off, leaves the reader, and opens it
  again
- **THEN** the reader returns to the place the user was reading

#### Scenario: The position survives a restart

- **WHEN** the user reads partway with read-along off, force-stops the app, and reopens the reader
- **THEN** the reader returns to the same place

#### Scenario: The position survives a font change

- **WHEN** the user changes text size, line spacing, or typeface
- **THEN** the same passage remains on screen rather than the reader jumping elsewhere in the book

#### Scenario: Positions are independent per book

- **WHEN** the user reads in two different books' ebooks
- **THEN** each book returns to its own reading position

#### Scenario: Reading position is independent of playback position while read-along is off

- **WHEN** read-along is off and the user seeks the audio to a different place in the book
- **THEN** the reading position is unchanged

#### Scenario: The two positions are one while read-along is on

- **WHEN** read-along is on
- **THEN** the place the reader shows and the place the audio is narrating are the same place
- **AND** reopening the reader returns to where the audio is
