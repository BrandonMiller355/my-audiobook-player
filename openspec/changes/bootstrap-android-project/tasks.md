## 1. Gradle toolchain

- [x] 1.1 Install a Gradle distribution to `D:\Android\gradle` and confirm `gradle --version` runs (per design D3; Android Studio's New Project wizard is the documented fallback if this is inconvenient)
- [x] 1.2 Determine the AGP, Kotlin, Compose-compiler, and Gradle version combination that supports `compileSdk 37`; if no stable combination exists, fall back to `compileSdk 36` and note the reason for the README (design D4)
- [x] 1.3 Generate the wrapper with `gradle wrapper --gradle-version <pinned>` and commit `gradlew`, `gradlew.bat`, and `gradle/wrapper/` including the jar
- [x] 1.4 Create `local.properties` with `sdk.dir=D\:\\Android\\Sdk` and confirm it is gitignored rather than committed

## 2. Project skeleton

- [x] 2.1 Write `settings.gradle.kts` declaring the `:app` module and the plugin/dependency repositories
- [x] 2.2 Write `gradle/libs.versions.toml` with version entries for AGP, Kotlin, the Compose BOM, Navigation Compose, JUnit, **and** Media3, Room, and Coil — the latter three declared but not referenced by any module yet (design D2)
- [x] 2.3 Write the root `build.gradle.kts` applying plugins with `apply false`
- [x] 2.4 Write `app/build.gradle.kts`: `applicationId` `com.brandonmiller.audiobookplayer`, `minSdk 26`, `compileSdk`/`targetSdk` from 1.2, JVM toolchain matching Studio's bundled JDK, Compose enabled, and dependencies limited to the Compose BOM, Material 3, Navigation Compose, and JUnit
- [x] 2.5 Write `app/src/main/AndroidManifest.xml` with a single launcher `MainActivity` and **no `<uses-permission>` elements**
- [x] 2.6 Add the app label ("Audiobooks") and a stock adaptive launcher icon
- [x] 2.7 Verify `gradlew assembleDebug` succeeds and produces `app/build/outputs/apk/debug/app-debug.apk` — the app has no UI yet beyond an empty activity, but it must build and install from here on

## 3. Theme

- [x] 3.1 Define the Material 3 light and dark color schemes, typography, and the `AudiobooksTheme` composable selecting the scheme via `isSystemInDarkTheme()`, with dynamic color deliberately not used (design D6)
- [x] 3.2 Apply the theme in `MainActivity` with `setContent`, using edge-to-edge defaults consistent with the target SDK
- [ ] 3.3 Confirm the activity renders in both light and dark, and survives a system theme change while in the foreground, without restart or crash
  - **Needs a device.** No phone is connected and no emulator AVD exists (the emulator binary is installed but there are no system images). Verified statically only: `AudiobooksTheme` selects its scheme via `isSystemInDarkTheme()`, and `values-night/` overrides exist for the pre-Compose window background. Runtime confirmation happens with 8.2.

## 4. Navigation and placeholder screens

- [x] 4.1 Add the `NavHost` with `library` as the start destination and `player/{bookId}` taking a string route argument (design D5)
- [x] 4.2 Write the Library screen: a top app bar with the app title and a centered empty state stating that no audiobooks have been added — no add button yet, since the picker belongs to a later change
- [x] 4.3 Write the Player placeholder screen: renders the `bookId` route argument and nothing else, deliberately not the PRD §20.2 layout
- [ ] 4.4 Verify forward navigation into `player/{bookId}` passes the argument through, that system back returns to Library, and that back from Library exits the app cleanly
  - **Blocked, deferred.** There is no UI affordance that reaches the Player: the Library has no books and deliberately no add button, so forward navigation cannot be triggered by hand in this change. The route strings are covered by `RoutesTest`, and end-to-end verification moves to the change that adds the library list — the first thing that can tap into a book. Back-from-Library-exits is covered by 8.1 once a device is available.

## 5. Test wiring

- [x] 5.1 Add the JVM unit test source set with one trivial assertion so `gradlew testDebugUnitTest` has something to run and later changes can add pure-logic tests without touching the build

## 6. No-network verification

- [x] 6.1 Build the debug variant and locate the merged manifest under `app/build/intermediates/`
- [x] 6.2 Confirm the merged manifest declares no `INTERNET` and no `ACCESS_NETWORK_STATE` permission (design D9)
- [x] 6.3 Record in the README how to re-run this check, so the change that adds Coil can repeat it rather than rediscover it

## 7. README

- [x] 7.1 Write `README.md` covering: opening the project in Android Studio, building from the command line, producing a debug APK, the APK output path, the pinned SDK/AGP/Kotlin versions and the `minSdk 26` requirement (PRD §29)
- [x] 7.2 Note in the README that `.m4b` chapter-parsing limitations will be documented by the change that implements the parser, so the section is not silently forgotten

## 8. Manual verification on device

No audiobook fixture is required for this change — there is nothing yet that can open a file.
The PRD §25 playback, persistence, and metadata checks do not apply and are verified by later
changes.

**These are the owner's to run.** No device was connected when this change was implemented and
no emulator AVD exists on the machine, so every item below is unverified. Build and install with
the commands in the README.

- [ ] 8.1 Install the debug APK on the phone and confirm it launches to the Library empty state with no permission prompt and no crash
- [ ] 8.2 Toggle the system theme to dark and back, confirming the app follows it
- [ ] 8.3 Kill the app process from recents, relaunch, and confirm it returns to the Library screen without error
- [ ] 8.4 Confirm the app's entry in Android Settings → App info → Permissions shows no permissions requested
