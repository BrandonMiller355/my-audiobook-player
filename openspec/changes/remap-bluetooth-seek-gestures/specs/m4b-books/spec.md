## MODIFIED Requirements

### Requirement: Chapter navigation and seeking work within the single item

Previous chapter, next chapter, the fixed-interval seeks, and the book-wide scrubber SHALL
operate on an `.m4b` book's stored chapter boundaries, and SHALL behave the same whether the
command comes from the app's controls, the media notification, or the lock screen. A Bluetooth
device's next-track and previous-track commands SHALL instead seek backward within the file by a
fixed interval, and SHALL NOT navigate chapters.

#### Scenario: Next chapter within the file

- **WHEN** next chapter is pressed partway through a chapter of an `.m4b` book
- **THEN** playback moves to the start of the following chapter within the same file

#### Scenario: Previous chapter obeys the 3-second rule

- **WHEN** previous chapter is pressed more than 3 seconds into a chapter of an `.m4b` book
- **THEN** the current chapter restarts from its own start position, not the start of the file

#### Scenario: A Bluetooth command seeks backward within the file

- **WHEN** a next-track or previous-track command arrives from a Bluetooth device while an
  `.m4b` book is playing
- **THEN** playback seeks backward within the same file by that gesture's fixed interval
- **AND** it does not move between that book's embedded chapters

#### Scenario: A Bluetooth command on an unchaptered `.m4b`

- **WHEN** either Bluetooth gesture is used hours into an `.m4b` book whose whole file is a
  single chapter
- **THEN** playback seeks backward by that gesture's interval
- **AND** playback does not restart the file from its beginning

#### Scenario: Seeking across a chapter boundary

- **WHEN** a forward seek from near the end of a chapter would pass its end
- **THEN** playback continues into the next chapter at the correct offset

#### Scenario: The scrubber spans the whole file

- **WHEN** an `.m4b` book is open
- **THEN** the progress control spans the whole book
- **AND** its total reflects the file's full duration rather than only the current chapter
