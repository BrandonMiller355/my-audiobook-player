## ADDED Requirements

### Requirement: A single tap on the Player captures the current spot and opens it for writing

The Player SHALL offer a control that, in one tap, records the current place in the book, pauses
playback, and opens the new entry for text entry. The control SHALL be positioned and sized to be
hittable without aiming.

Pausing is required rather than incidental: writing a note means dictating or typing it, and both
compete with the narration — a dictation key held open against a playing audiobook transcribes the
book. Marking is therefore a deliberate stop, which the lead-in makes cheap, because the anchor is
already behind the interruption.

Tapping the control SHALL NOT reposition playback, and SHALL NOT start playback that was stopped.

#### Scenario: Marking while listening

- **WHEN** the user taps the mark control on the Player while the book is playing
- **THEN** a mark is recorded against that book
- **AND** the audio pauses
- **AND** the new entry is shown, open for text entry

#### Scenario: Marking while paused

- **WHEN** the user taps the mark control while the book is paused
- **THEN** a mark is recorded at the paused position
- **AND** playback does not begin
- **AND** the new entry is shown, open for text entry

#### Scenario: Marking does not move the book

- **WHEN** the user taps the mark control
- **THEN** the position playback is stopped at is the position it had reached
- **AND** returning to the Player and pressing play resumes from there rather than from the mark

#### Scenario: The book stays paused afterwards

- **WHEN** the user finishes with the new entry, whether by keeping text or by declining to write any
- **THEN** playback is still paused
- **AND** it resumes only when the user asks it to

#### Scenario: The control is unavailable before the book is ready

- **WHEN** the Player has not yet connected to the playback session
- **THEN** the mark control is shown but does not record anything
- **AND** it takes the same disabled appearance as the Player's other controls in that state

### Requirement: A mark anchors behind the moment it was taken

A mark SHALL be anchored a fixed interval before the position playing when it was taken, so that
replaying it begins before the passage that prompted it rather than after it. The interval SHALL be
fixed in the app rather than configurable.

The anchor SHALL be clamped to the start of the book, and SHALL cross backward into the preceding
chapter when the mark is taken near the start of a chapter and the preceding chapter's extent is
known.

#### Scenario: Marking mid-chapter

- **WHEN** the user marks at a position well inside a chapter
- **THEN** the mark is anchored at that position less the fixed lead-in interval

#### Scenario: Marking near the start of a chapter

- **WHEN** the user marks a few seconds into a chapter whose preceding chapter's length is known
- **THEN** the mark is anchored in the tail of the preceding chapter
- **AND** the mark's recorded chapter is that preceding chapter

#### Scenario: Marking near the start of the book

- **WHEN** the user marks less than the lead-in interval into the first chapter of the book
- **THEN** the mark is anchored at the start of the book
- **AND** no invalid or negative position is recorded

#### Scenario: Marking near the start of a chapter of unknown length

- **WHEN** the user marks near the start of a chapter and the preceding chapter's length is not yet
  known
- **THEN** the mark is anchored at the start of the current chapter rather than at a guessed position
  inside the preceding one

#### Scenario: Marking in a single-file book

- **WHEN** the user marks in an `.m4b` book, whose chapters share one media item
- **THEN** the mark is anchored by the same rule as a folder book
- **AND** seeking to it later arrives at the same place it was taken from, less the lead-in

### Requirement: A mark and a note are the same record

A note SHALL be a mark that carries text. The app SHALL NOT distinguish them as separate kinds of
record: adding text to a mark SHALL make it a note without creating, replacing, or moving anything,
and clearing a note's text SHALL leave a mark rather than delete the record.

#### Scenario: Annotating a mark later

- **WHEN** the user adds text to a mark taken earlier
- **THEN** that same record now carries the text
- **AND** its anchor and its recorded chapter are unchanged
- **AND** no second entry is created

#### Scenario: A mark with no text

- **WHEN** a mark has never been given text
- **THEN** it is still a complete record that can be listed and seeked to

#### Scenario: Clearing the text of a note

- **WHEN** the user removes all the text from a note
- **THEN** the record remains as a mark at the same anchor
- **AND** it is not deleted

### Requirement: A mark records the chapter it landed in, at the time it was taken

Each mark SHALL store the title of the chapter its anchor falls in, captured when the mark is
created. The stored title SHALL be the chapter of the anchored position rather than of the position
playing when the user tapped, and SHALL NOT change when the book is later rescanned, re-added, or has
its chapters renumbered.

#### Scenario: The chapter is recorded at creation

- **WHEN** a mark is created
- **THEN** the title of the chapter containing its anchor is stored on it

#### Scenario: The chapter agrees with the anchor across a boundary

- **WHEN** a mark taken near the start of a chapter is anchored into the preceding chapter
- **THEN** the chapter recorded on it is the preceding chapter, not the one the user was in when they
  tapped

#### Scenario: The book is re-added after its files change

- **WHEN** a book is removed and re-added after a file has been renamed or added, shifting the
  chapter numbering
- **THEN** existing marks for a book still in the library continue to name the chapters they were
  taken in
- **AND** no mark silently changes which chapter it claims

#### Scenario: A chapter is renamed

- **WHEN** a book's chapter titles change on a later scan
- **THEN** marks taken before the change keep the title they recorded

### Requirement: Marking and writing up are one gesture, and writing is optional

Marking SHALL land the user in the new entry's text field with no further navigation. Keeping an
empty field SHALL store a bare mark, which is a bookmark; abandoning the field SHALL discard the
entry entirely, because it was never confirmed.

Abandoning the text field of an entry that already existed SHALL leave that entry alone rather than
discard it. The two cases are different intents and SHALL behave differently.

The user SHALL be shown which chapter the entry landed in while writing it, so that an entry anchored
into the previous chapter reads as deliberate rather than as an error.

#### Scenario: Writing the note immediately

- **WHEN** the user marks and types text and keeps it
- **THEN** the entry carries that text
- **AND** it appears in this book's list at the marked position

#### Scenario: Canceling a mark

- **WHEN** the user marks and then dismisses the text field without keeping anything
- **THEN** no entry is added to the book
- **AND** the list is as it was before the mark

#### Scenario: Bookmarking without words

- **WHEN** the user marks and keeps the entry with the text field left empty
- **THEN** a bare mark is stored at that anchor
- **AND** it is listed as an entry awaiting text

#### Scenario: Canceling an edit to an existing entry

- **WHEN** the user opens an entry that already existed, changes the text, and dismisses without
  keeping it
- **THEN** that entry remains, with the text it had before

#### Scenario: The entry states where it landed

- **WHEN** the new entry is opened for text entry
- **THEN** it shows the chapter it was anchored in and its position in the book

#### Scenario: Leaving afterwards returns to the book

- **WHEN** the user has finished with the new entry and navigates back
- **THEN** the Player for the same book is shown

### Requirement: Notes belong to one book and are removed with it

A mark or note SHALL belong to exactly one book. Removing a book from the library SHALL remove its
marks and notes with it. Marks and notes SHALL survive app restart, device reboot, and the book being
played from any other surface.

#### Scenario: Notes survive a restart

- **WHEN** the app is closed and reopened, or the device is rebooted
- **THEN** every mark and note previously taken is still present, with its text, anchor, and chapter

#### Scenario: Removing a book removes its notes

- **WHEN** a book is removed from the library
- **THEN** its marks and notes are removed with it
- **AND** no orphaned entry remains

#### Scenario: Notes are scoped to their own book

- **WHEN** a book is removed from the library
- **THEN** the marks and notes belonging to other books are unaffected

### Requirement: Note capture requires no microphone and no network

The app SHALL NOT record audio, request microphone access, or perform speech recognition in order to
support notes. Note text SHALL be entered through an ordinary text field, leaving dictation to the
device keyboard. Notes SHALL be stored only on the device.

#### Scenario: No microphone permission is requested

- **WHEN** the user marks, annotates, edits, or reviews notes
- **THEN** the app requests no microphone permission at any point
- **AND** the installed app declares none

#### Scenario: Dictating a note

- **WHEN** the user selects the keyboard's own dictation key while typing a note
- **THEN** the dictated text enters the field like any other input
- **AND** the app takes no part in the recognition

#### Scenario: Notes stay on the device

- **WHEN** notes are created, edited, or deleted
- **THEN** nothing is transmitted off the device
- **AND** the app still requests no network permission
