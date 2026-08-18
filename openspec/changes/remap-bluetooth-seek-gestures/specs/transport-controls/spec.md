## ADDED Requirements

### Requirement: Bluetooth multi-press gestures seek backward by fixed intervals

A next-track command arriving from a Bluetooth device SHALL seek backward one minute. A
previous-track command arriving from a Bluetooth device SHALL seek backward three minutes. Both
SHALL apply to every book type, SHALL clamp at the start of the book rather than seeking before
it, and SHALL cross chapter boundaries under the same rules as the app's own fixed-interval seeks.
Neither SHALL move between chapters.

Both intervals are backward on purpose. Multi-press gestures are timing-sensitive and a press
count read wrongly by the headset is routine, so every gesture is made recoverable: the worst
outcome of a misfire is re-listening to a few minutes. Forward seeking remains available on the
Player screen.

#### Scenario: Double-press seeks back one minute

- **WHEN** a next-track command arrives from a Bluetooth device more than one minute into the book
- **THEN** playback resumes one minute earlier than it was

#### Scenario: Triple-press seeks back three minutes

- **WHEN** a previous-track command arrives from a Bluetooth device more than three minutes into
  the book
- **THEN** playback resumes three minutes earlier than it was

#### Scenario: Seeking back past the start of the book

- **WHEN** either gesture is used less than its interval from the start of the book
- **THEN** playback clamps to the very start rather than going negative

#### Scenario: Seeking back across a chapter boundary

- **WHEN** either gesture is used shortly after the start of a chapter, and the preceding
  chapter's duration is known
- **THEN** playback moves into the preceding chapter at the correct offset from its end

#### Scenario: Preceding chapter duration not yet known

- **WHEN** either gesture would cross into a chapter whose duration has not been resolved
- **THEN** the seek clamps to the start of the current chapter rather than failing

#### Scenario: The gesture does not change chapter

- **WHEN** either gesture is used in the middle of a chapter longer than the interval
- **THEN** the current chapter is still the current chapter afterward

## MODIFIED Requirements

### Requirement: Previous and next chapter use the 3-second rule for every book type

Previous Chapter SHALL restart the current chapter if the playback position is more than
approximately 3 seconds into it, and SHALL otherwise move to the previous chapter. Next Chapter
SHALL move to the start of the next chapter, or do nothing at the final chapter. This behavior
SHALL be identical for folder-based books and for any other book type, and SHALL apply equally
whether triggered from the app's own controls, the media notification, or the lock screen.

Bluetooth controls are deliberately excluded: their next-track and previous-track commands seek
backward by fixed intervals instead, and reach no chapter navigation at all.

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

#### Scenario: The rule applies from the media notification

- **WHEN** the previous-track command arrives from the media notification or the lock screen
  rather than the in-app button
- **THEN** the same 3-second rule is applied

#### Scenario: The rule does not apply from a Bluetooth control

- **WHEN** the previous-track command arrives from a connected Bluetooth device
- **THEN** the 3-second rule is not applied
- **AND** playback seeks backward by a fixed interval instead
