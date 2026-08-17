## ADDED Requirements

### Requirement: Text size, line spacing, and typeface are adjustable from the reader

The reader SHALL let the user change the text size, the line spacing, and the typeface between a
serif and a sans-serif face. Changes SHALL take effect immediately on the text being read.

#### Scenario: Changing text size

- **WHEN** the user changes the text size from the reader's controls
- **THEN** the rendered text resizes immediately
- **AND** the passage the user was reading remains on screen

#### Scenario: Changing line spacing

- **WHEN** the user changes the line spacing
- **THEN** the rendered text reflows immediately

#### Scenario: Switching typeface

- **WHEN** the user switches between the serif and sans-serif face
- **THEN** the rendered text changes face immediately

#### Scenario: Settings stay within legible bounds

- **WHEN** the user moves text size or line spacing to either extreme
- **THEN** the result is still readable — neither too small to read nor so large that a line holds
  only a word or two

### Requirement: Screen brightness is adjustable from the reader

The reader SHALL let the user set the screen brightness while reading, affecting the actual screen
output rather than dimming the text. The system brightness SHALL be restored when the reader is
left.

#### Scenario: Lowering brightness for night reading

- **WHEN** the user lowers brightness from the reader's controls
- **THEN** the screen output dims
- **AND** the white text stays as bright relative to the background as it was

#### Scenario: Leaving the reader

- **WHEN** the user returns to the Player or the Library
- **THEN** the device returns to its normal system brightness

#### Scenario: The rest of the system is unaffected

- **WHEN** the user has set a reading brightness
- **THEN** the device's system-wide brightness setting is unchanged

### Requirement: Reading preferences apply to every book

Text size, line spacing, typeface, and reading brightness SHALL be app-wide settings rather than
per-book ones, and SHALL persist across app restarts.

#### Scenario: Settings carry across books

- **WHEN** the user sets a text size while reading one book and then opens a different book's ebook
- **THEN** the second ebook uses the same text size

#### Scenario: Settings survive a restart

- **WHEN** the user changes reading settings, force-stops the app, and reopens the reader
- **THEN** the settings are as the user left them

#### Scenario: Defaults on first use

- **WHEN** the reader is opened for the first time and no settings have been chosen
- **THEN** it uses readable defaults rather than an unset or extreme value
