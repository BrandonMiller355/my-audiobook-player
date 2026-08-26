## MODIFIED Requirements

### Requirement: Navigation between Library and Player

The app SHALL provide a navigation graph with a Library destination and a Player destination
that accepts a book identifier, and system back navigation SHALL return from Player to Library.

The graph SHALL also carry a Notes destination accepting a book identifier, reached from the Player
rather than from navigation of its own, and system back navigation SHALL return from Notes to the
Player for that book rather than to the Library.

#### Scenario: Navigating to the Player destination

- **WHEN** navigation to the `player/{bookId}` route is performed with a book identifier
- **THEN** the Player destination is displayed
- **AND** the destination receives the supplied book identifier as a route argument

#### Scenario: Returning to the Library

- **WHEN** the user is on the Player destination and presses the system back button
- **THEN** the app returns to the Library destination
- **AND** the app is not closed

#### Scenario: Back from the start destination

- **WHEN** the user is on the Library destination and presses the system back button
- **THEN** the app exits normally without navigating to an empty screen

#### Scenario: Navigating to the Notes destination

- **WHEN** navigation to the `notes/{bookId}` route is performed with a book identifier
- **THEN** the Notes destination is displayed
- **AND** the destination receives the supplied book identifier as a route argument

#### Scenario: Returning from Notes to the Player

- **WHEN** the user is on the Notes destination and presses the system back button
- **THEN** the app returns to the Player destination for the same book
- **AND** the Library is not shown in between
