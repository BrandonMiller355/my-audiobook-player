## ADDED Requirements

### Requirement: A book plays from the Player screen

The Player screen SHALL start and pause a book's audio, and SHALL show the current playback
position and the duration of what is playing.

#### Scenario: Starting playback

- **WHEN** the user opens a book and presses play
- **THEN** its first chapter begins playing from the start
- **AND** the elapsed position advances while it plays

#### Scenario: Pausing and resuming

- **WHEN** the user pauses and then presses play again
- **THEN** playback resumes from the position where it was paused

#### Scenario: Opening a book does not start it

- **WHEN** the user opens a book from the library
- **THEN** playback is prepared but does not begin until the user presses play

#### Scenario: Player state before the controller connects

- **WHEN** the Player screen is shown but has not yet connected to the playback service
- **THEN** it shows a neutral connecting state rather than incorrect position or duration

### Requirement: A folder book plays through in chapter order

A folder book SHALL play as an ordered playlist of its chapter files, advancing to the next chapter
automatically when one finishes.

#### Scenario: Automatic advance

- **WHEN** a chapter reaches its end during playback
- **THEN** the next chapter begins without user action
- **AND** the Player shows the new chapter as current

#### Scenario: Chapters play in stored order

- **WHEN** a book with numbered chapter files is played
- **THEN** the playback order matches the stored chapter order

#### Scenario: End of the last chapter

- **WHEN** the final chapter of a book finishes
- **THEN** playback stops rather than looping back to the beginning

### Requirement: Audio continues when the app is not in the foreground

Playback SHALL be owned by a media playback service so that audio continues when the screen turns
off, the app is backgrounded, the user switches apps, or the Activity is destroyed.

#### Scenario: Screen turns off

- **WHEN** the screen is switched off during playback
- **THEN** audio continues uninterrupted

#### Scenario: App is backgrounded

- **WHEN** the user leaves the app for another app during playback
- **THEN** audio continues uninterrupted

#### Scenario: Activity is destroyed while playing

- **WHEN** the Activity is destroyed but the playback service is still alive
- **THEN** audio continues
- **AND** reopening the app shows the Player reflecting the still-playing state

#### Scenario: Returning to the app after backgrounding

- **WHEN** the user returns to the app during playback
- **THEN** the Player screen reflects the actual current position rather than a stale one

### Requirement: System and Bluetooth media controls operate playback

The app SHALL publish a media session so that notification, lock-screen, and Bluetooth controls can
play, pause, and move between chapters.

#### Scenario: Notification controls

- **WHEN** playback is active and the user presses pause in the media notification
- **THEN** playback pauses
- **AND** the notification updates to show the play action

#### Scenario: Lock-screen controls

- **WHEN** the device is locked during playback
- **THEN** media controls for this book appear on the lock screen and operate playback

#### Scenario: Bluetooth play and pause

- **WHEN** the user presses play or pause on a connected Bluetooth headset or car stereo
- **THEN** playback responds accordingly

#### Scenario: Bluetooth previous and next move between chapters

- **WHEN** the user presses next or previous on a Bluetooth control while a folder book is playing
- **THEN** playback moves to the next or previous chapter of that book

### Requirement: A foreground media notification is shown while playing

While playback is active the service SHALL run in the foreground with a media notification, and
SHALL leave the foreground when playback stops.

#### Scenario: Notification appears with playback

- **WHEN** playback starts
- **THEN** a media notification showing the book is displayed

#### Scenario: Notification permission is refused

- **WHEN** the user denies the notification permission
- **THEN** audio still plays normally
- **AND** the app does not crash or block playback

#### Scenario: Notification permission is requested at the right moment

- **WHEN** the user launches the app for the first time and browses the library
- **THEN** no notification permission dialog is shown
- **AND** the request is made only when the user first starts playback

### Requirement: Playback follows Android audio focus conventions

The app SHALL request audio focus, and SHALL duck, pause, and resume in line with standard Android
behaviour rather than playing over other audio.

#### Scenario: A phone call arrives

- **WHEN** a phone call starts during playback
- **THEN** playback pauses and does not play over the call

#### Scenario: Another app takes audio focus permanently

- **WHEN** another media app starts playing
- **THEN** this app's playback pauses

#### Scenario: A transient sound plays

- **WHEN** a short notification sound plays over the audiobook
- **THEN** the audiobook ducks or pauses briefly and returns to normal afterwards

#### Scenario: Headphones are disconnected

- **WHEN** wired headphones are unplugged or a Bluetooth device disconnects during playback
- **THEN** playback pauses rather than continuing out of the speaker
