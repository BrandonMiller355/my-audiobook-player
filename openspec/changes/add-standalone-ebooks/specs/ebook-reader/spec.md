## MODIFIED Requirements

### Requirement: The reader returns to the Player

The reader's controls SHALL include a way back to the Player for the same book, and hardware back
SHALL do the same thing. For a book with no audio, which was opened from the Library and has no
Player, both SHALL return to the Library instead.

#### Scenario: Flipping back from the controls

- **WHEN** the user taps the flip-back control
- **THEN** the Player for the same book is shown

#### Scenario: Hardware back

- **WHEN** the user presses the system back button in the reader
- **THEN** the Player for the same book is shown, not the Library

#### Scenario: A book with no audio

- **WHEN** the user taps the back control or presses system back in the reader of a book with no audio
- **THEN** the Library is shown
