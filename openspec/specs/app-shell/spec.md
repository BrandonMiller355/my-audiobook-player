# app-shell Specification

## Purpose
TBD - created by archiving change bootstrap-android-project. Update Purpose after archive.
## Requirements
### Requirement: Command-line debug build produces a sideloadable APK

The project SHALL build from the command line using the committed Gradle wrapper, without a
system-wide Gradle installation, and SHALL produce an installable debug APK at a documented
path.

#### Scenario: Building from a clean checkout

- **WHEN** a developer clones the repository, sets `sdk.dir` in `local.properties`, and runs `gradlew assembleDebug`
- **THEN** the build completes successfully without requiring `gradle` on the PATH
- **AND** a debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`

#### Scenario: Installing the APK on a device

- **WHEN** the produced debug APK is installed on an Android device running API 26 or newer
- **THEN** the installation succeeds
- **AND** the app appears in the launcher

#### Scenario: Unit test task is wired

- **WHEN** a developer runs `gradlew testDebugUnitTest`
- **THEN** the task executes the JVM unit test source set and reports success

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

### Requirement: Navigation between Library and Player

The app SHALL provide a navigation graph with a Library destination and a Player destination
that accepts a book identifier, and system back navigation SHALL return from Player to Library.

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

### Requirement: The application requests no network permission

The merged Android manifest SHALL contain no `INTERNET` permission, no other network-related
permission, and no storage permission. It SHALL declare only those permissions genuinely required
for background media playback. This SHALL be verified automatically against the **merged** manifest
rather than the source manifest, and the build SHALL fail when a forbidden permission appears,
including one contributed by a dependency during manifest merging.

#### Scenario: Merged manifest is inspected after a build

- **WHEN** a build completes and the merged manifest is inspected
- **THEN** it declares no `android.permission.INTERNET`
- **AND** it declares no `android.permission.ACCESS_NETWORK_STATE`
- **AND** it declares no `android.permission.ACCESS_WIFI_STATE`

#### Scenario: A dependency contributes a forbidden permission

- **WHEN** a dependency contributes a forbidden permission during manifest merging
- **THEN** the build fails naming the permission that appeared
- **AND** the failure states that it must either be removed or deliberately justified

#### Scenario: A contributed permission is removed rather than accepted

- **WHEN** a playback dependency contributes `ACCESS_NETWORK_STATE` for streaming features this app does not use
- **THEN** it is stripped from the merged manifest
- **AND** playback of local files continues to work without it

#### Scenario: No storage permission is declared

- **WHEN** the merged manifest is inspected
- **THEN** it declares no `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, or `READ_MEDIA_AUDIO` permission
- **AND** file access continues to come solely from Storage Access Framework grants

#### Scenario: Only playback-supporting permissions are declared

- **WHEN** the merged manifest is inspected
- **THEN** the declared permissions are limited to those that background media playback requires — running a foreground media service, posting its notification, and holding a wake lock while audio plays with the screen off
- **AND** any permission outside that set is treated as a regression to be justified or removed

### Requirement: Minimum and target Android versions

The app SHALL declare `minSdk` 26 and SHALL compile and target the newest Android SDK platform
that is installed in the development environment and supported by the pinned Android Gradle
Plugin.

#### Scenario: Declared SDK levels

- **WHEN** the `:app` module's build configuration is inspected
- **THEN** `minSdk` is 26
- **AND** `compileSdk` and `targetSdk` are equal to each other
- **AND** the chosen `compileSdk` value is recorded in the README

#### Scenario: Device below the minimum

- **WHEN** installation is attempted on a device running an Android version below API 26
- **THEN** the package manager rejects the install rather than the app crashing at runtime

