## ADDED Requirements

### Requirement: Switching between the Player and the reader leaves playback alone

Moving from the Player to the reader, and back, SHALL NOT start, stop, pause, or reposition
playback. Playback SHALL change only when the user operates a transport control.

#### Scenario: Opening the reader while playing

- **WHEN** a book is playing and the user opens the reader
- **THEN** the audio keeps playing without interruption

#### Scenario: Opening the reader while paused

- **WHEN** a book is paused and the user opens the reader
- **THEN** the audio stays paused and does not begin playing

#### Scenario: Returning to the Player

- **WHEN** the user returns to the Player from the reader
- **THEN** playback is in the same state and at the same position as it would have been had the
  reader never been opened

#### Scenario: Scrolling the ebook does not seek

- **WHEN** the user scrolls the ebook or jumps to a chapter in its table of contents
- **THEN** the audio position is unchanged

### Requirement: Playback can be paused and resumed from the reader

The reader's controls SHALL include play/pause for the book being read, acting on the same playback
session the Player uses.

#### Scenario: Pausing from the reader

- **WHEN** the user pauses from the reader's controls
- **THEN** the audio pauses
- **AND** returning to the Player shows it as paused

#### Scenario: Resuming from the reader

- **WHEN** the user presses play from the reader's controls
- **THEN** the audio resumes from the saved position

#### Scenario: The control reflects the real state

- **WHEN** playback is paused or resumed from the notification, a Bluetooth control, or the Player
- **THEN** the reader's control shows the current state
