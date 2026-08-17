## MODIFIED Requirements

### Requirement: Theme follows the system light and dark setting

The app SHALL apply a Material 3 theme whose light or dark palette is selected from the current
system setting, and SHALL NOT derive its colors from the device wallpaper.

Both palettes SHALL be defined in full rather than one being derived from the other. Layout,
dimensions, spacing, and touch-target sizes SHALL be identical between them; the theme SHALL
change color only.

Where the app draws content behind the status bar, it SHALL set the status-bar icon appearance
for legibility against that content rather than against the theme, and SHALL restore the
theme-appropriate appearance when that content is no longer shown.

#### Scenario: System is in dark mode

- **WHEN** the device system theme is set to dark and the app is launched
- **THEN** the app renders using its dark color scheme

#### Scenario: System is in light mode

- **WHEN** the device system theme is set to light and the app is launched
- **THEN** the app renders using its light color scheme

#### Scenario: System theme changes while the app is running

- **WHEN** the user changes the system theme while the app is in the foreground
- **THEN** the app updates to the corresponding color scheme without restarting or crashing

#### Scenario: Dynamic color is not applied

- **WHEN** the app runs on a device that supports wallpaper-based dynamic color
- **THEN** the app's colors remain its own defined palette and do not change with the wallpaper

#### Scenario: The two palettes differ only in color

- **WHEN** the same screen is compared between the light and dark themes
- **THEN** its layout, element sizes, spacing, and touch targets are the same in both

#### Scenario: Status bar over full-bleed content

- **WHEN** a screen draws artwork behind the status bar
- **THEN** the status-bar icons are legible against that artwork in both themes

#### Scenario: Leaving a screen that drew behind the status bar

- **WHEN** the user navigates away from such a screen
- **THEN** the status-bar icon appearance returns to the one the current theme calls for
