## MODIFIED Requirements

### Requirement: Switching between the Player and the reader leaves playback alone

Moving from the Player to the reader, and back, SHALL NOT start, stop, pause, or reposition
playback. Playback SHALL change only when the user operates a transport control, or — while
read-along is on — deliberately moves the reader, which repositions playback without starting or
stopping it.

#### Scenario: Opening the reader while playing

- **WHEN** a book is playing and the user opens the reader
- **THEN** the audio keeps playing without interruption

#### Scenario: Opening the reader while paused

- **WHEN** a book is paused and the user opens the reader
- **THEN** the audio stays paused and does not begin playing

#### Scenario: Returning to the Player

- **WHEN** the user returns to the Player from the reader
- **THEN** playback is in the same state and at the same position as it would have been had the
  reader never been opened, apart from any repositioning the user caused through read-along

#### Scenario: Scrolling the ebook does not seek while read-along is off

- **WHEN** read-along is off and the user scrolls the ebook or jumps to a chapter in its table of
  contents
- **THEN** the audio position is unchanged

#### Scenario: Scrolling the ebook seeks while read-along is on

- **WHEN** read-along is on and the user scrolls the ebook to a different place
- **THEN** the audio is repositioned to match
- **AND** whether it was playing or paused is unchanged
