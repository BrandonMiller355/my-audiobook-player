## MODIFIED Requirements

### Requirement: Application launches to the Library screen

The app SHALL open directly to the Library screen with no splash screen, login, onboarding, or
permission prompt. It SHALL display an empty state when no books have been added, and the list of
books once any exist.

#### Scenario: First launch on a clean install

- **WHEN** the user taps the app icon after a fresh install
- **THEN** the Library screen is displayed as the start destination
- **AND** an empty-state message indicating that no audiobooks have been added is shown
- **AND** no account prompt, network call, or runtime permission dialog is presented

#### Scenario: Relaunch after the process is killed

- **WHEN** the app process is killed and the user launches the app again
- **THEN** the Library screen is displayed without error

#### Scenario: Launch with books already in the library

- **WHEN** the app is launched and the library already contains books
- **THEN** the Library screen shows those books rather than the empty state
