## ADDED Requirements

### Requirement: Seeking crosses chapter boundaries

The Player screen SHALL provide backward 10 second, forward 10 second, backward 60 second, and
forward 60 second seek controls. A seek that would move past the start or end of the current
chapter SHALL clamp cleanly rather than error, and SHALL advance into the neighboring chapter when
its boundary is known.

#### Scenario: Seeking backward near the beginning

- **WHEN** the user seeks backward and fewer than the requested seconds remain before the start of the book
- **THEN** playback position clamps to the very start rather than going negative

#### Scenario: Forward seek crosses into the next chapter

- **WHEN** the user seeks forward by an amount that exceeds the remaining time in the current chapter, and the current chapter's duration is known
- **THEN** playback continues into the next chapter at the correct overflow offset

#### Scenario: Forward seek at the last chapter

- **WHEN** the user seeks forward near the end of the final chapter
- **THEN** playback position clamps to the end of the book rather than erroring

#### Scenario: Chapter duration not yet known

- **WHEN** a forward seek would cross into a chapter whose duration has not yet been resolved
- **THEN** the seek clamps to the end of the current chapter rather than failing

### Requirement: Previous and next chapter use the 3-second rule for every book type

Previous Chapter SHALL restart the current chapter if the playback position is more than
approximately 3 seconds into it, and SHALL otherwise move to the previous chapter. Next Chapter
SHALL move to the start of the next chapter, or do nothing at the final chapter. This behavior
SHALL be identical for folder-based books and for any other book type, and SHALL apply equally
whether triggered from the app's own controls or from an external control surface such as
Bluetooth or the media notification.

#### Scenario: Previous chapter well into playback

- **WHEN** Previous Chapter is pressed more than 3 seconds into the current chapter
- **THEN** the current chapter restarts from its beginning

#### Scenario: Previous chapter near the start

- **WHEN** Previous Chapter is pressed within about 3 seconds of the current chapter's start
- **THEN** playback moves to the previous chapter

#### Scenario: Previous chapter at the first chapter

- **WHEN** Previous Chapter is pressed while on the first chapter of the book
- **THEN** the first chapter restarts from its beginning

#### Scenario: Next chapter at the final chapter

- **WHEN** Next Chapter is pressed while on the final chapter of the book
- **THEN** playback does not advance and does not error

#### Scenario: The rule applies from a Bluetooth control

- **WHEN** the previous-track command arrives from a connected Bluetooth device rather than the in-app button
- **THEN** the same 3-second rule is applied

### Requirement: A book-wide progress scrubber shows and sets absolute position

The Player screen SHALL show a single progress control spanning the whole book, not just the
current chapter, and SHALL show the target timestamp while the user is dragging it, committing the
seek only on release.

#### Scenario: Scrubber reflects playback

- **WHEN** the book is playing
- **THEN** the scrubber's position advances to reflect progress through the whole book, not just the current chapter

#### Scenario: Dragging shows a live timestamp without seeking yet

- **WHEN** the user drags the scrubber
- **THEN** a timestamp corresponding to the drag position is displayed
- **AND** playback position does not change until the drag is released

#### Scenario: Releasing the scrubber seeks

- **WHEN** the user releases the scrubber at a new position
- **THEN** playback seeks to the corresponding absolute position in the book, including into a different chapter if applicable

### Requirement: Playback speed is adjustable without changing pitch

The user SHALL be able to select a playback speed from the set defined in PRD §9 (0.75x through
3.0x). Changing speed SHALL NOT audibly raise or lower the pitch of the narration.

#### Scenario: Selecting a faster speed

- **WHEN** the user selects a speed faster than 1.0x
- **THEN** playback proceeds at that speed
- **AND** the narrator's voice pitch is not noticeably raised

#### Scenario: Selecting a slower speed

- **WHEN** the user selects a speed slower than 1.0x
- **THEN** playback proceeds at that speed
- **AND** the narrator's voice pitch is not noticeably lowered

### Requirement: Playback speed is remembered per book, with a global fallback

Once set for a book, its speed SHALL be used whenever that book is opened again. A book with no
speed set of its own SHALL use the most recently used speed from any book, or 1.0x if none has ever
been set.

#### Scenario: Reopening a book with its own saved speed

- **WHEN** a book whose speed was previously set to 1.5x is opened again
- **THEN** it plays at 1.5x without the user reselecting it

#### Scenario: A new book uses the last globally used speed

- **WHEN** the user has been listening to another book at 1.25x and opens a book that has never had a speed set
- **THEN** the new book opens at 1.25x

#### Scenario: No speed has ever been set

- **WHEN** the app has never had a speed selected on any book
- **THEN** playback defaults to 1.0x

### Requirement: Playback position persists automatically and resumes on reopen

The app SHALL save the current chapter and position within it during playback, and SHALL restore
that position — without starting playback — the next time the book is opened. Saving SHALL NOT
depend on the Player screen remaining open.

#### Scenario: Progress is saved while playing

- **WHEN** a book plays for a period of time and then the app is closed
- **THEN** reopening that book resumes at approximately the position where it was left, not from the beginning

#### Scenario: Progress is saved on pause

- **WHEN** the user pauses partway through a chapter
- **THEN** the paused position is saved

#### Scenario: Progress is saved when the Activity is gone but playback continues

- **WHEN** playback continues in the background after the app's screen has been closed, and then the service itself stops
- **THEN** the position current at that time is saved, not an earlier one from before the screen closed

#### Scenario: Reopening does not auto-play

- **WHEN** a book with saved progress is opened
- **THEN** it is positioned at the saved timestamp
- **AND** playback does not begin until the user presses play
