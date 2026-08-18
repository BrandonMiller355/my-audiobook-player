## MODIFIED Requirements

### Requirement: System and Bluetooth media controls operate playback

The app SHALL publish a media session so that notification, lock-screen, and Bluetooth controls can
operate playback. The notification and lock screen SHALL play, pause, and move between chapters.
A Bluetooth control SHALL play and pause, and its next-track and previous-track commands SHALL seek
backward by a fixed interval rather than moving between chapters.

The two differ deliberately. A Bluetooth headset without dedicated skip buttons offers only
multi-press gestures, which are easy to trigger by accident and impossible to confirm visually;
chapter navigation from such a gesture is both useless on a single-chapter book and destructive of
the saved listening position, whereas a bounded backward seek is always recoverable. The
notification and lock screen are looked at while being used and keep chapter navigation.

#### Scenario: Notification controls

- **WHEN** playback is active and the user presses pause in the media notification
- **THEN** playback pauses
- **AND** the notification updates to show the play action

#### Scenario: Lock-screen controls

- **WHEN** the device is locked during playback
- **THEN** media controls for this book appear on the lock screen and operate playback

#### Scenario: Notification and lock screen still navigate chapters

- **WHEN** the user presses next or previous on the media notification or the lock screen
- **THEN** playback moves between chapters, applying the 3-second rule for previous

#### Scenario: Bluetooth play and pause

- **WHEN** the user presses play or pause on a connected Bluetooth headset or car stereo
- **THEN** playback responds accordingly

#### Scenario: Bluetooth next-track seeks backward instead of skipping

- **WHEN** a next-track command arrives from a connected Bluetooth device
- **THEN** playback seeks backward by one minute
- **AND** playback does not move to another chapter

#### Scenario: Bluetooth previous-track seeks backward instead of skipping

- **WHEN** a previous-track command arrives from a connected Bluetooth device
- **THEN** playback seeks backward by three minutes
- **AND** playback does not move to another chapter
- **AND** the 3-second rule is not applied

#### Scenario: A Bluetooth press never restarts the book

- **WHEN** either Bluetooth gesture is used well into a book that has only one chapter
- **THEN** playback moves backward by the gesture's interval
- **AND** playback does not jump to the beginning of the book
