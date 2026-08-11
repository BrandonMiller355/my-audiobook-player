## Why

The repository currently contains a PRD and an OpenSpec root, and nothing else — no Gradle
build, no module, no manifest, no source tree. Every subsequent change (folder books, the
playback service, `.m4b` chapters) needs somewhere to land, and each of those changes should
be able to spend its effort on behavior rather than on scaffolding.

This change creates the smallest project skeleton that actually builds, installs, and runs on
a phone, so that from here on "does it work?" is a question we can answer by installing an APK.
It implements PRD §27 Phase 1 (project foundation), and the build/output requirements of
PRD §29, with the theme requirements of PRD §21 and the no-network constraint of PRD §24
established as a baseline the rest of the project inherits.

## What Changes

- Add a single-module Gradle project (`:app`) using Kotlin DSL build scripts and a
  `gradle/libs.versions.toml` version catalog.
- Add the Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) so the project builds
  from the command line without a system Gradle install. **There is currently no Gradle
  distribution and no cached `gradle-wrapper.jar` on this machine**, so the wrapper has to be
  obtained as part of this change rather than assumed.
- Configure `minSdk 26`, and `compileSdk`/`targetSdk` pinned to the newest platform installed
  locally (`android-37`). Java/Kotlin toolchain targets the JDK bundled with Android Studio
  (`D:\Android\Studio\jbr`).
- Add the Android manifest with a single `MainActivity` (no exported components beyond the
  launcher), an application class only if one is actually needed, and app label/icon.
- Add Jetpack Compose with Material 3, a theme that follows the system light/dark setting,
  and dynamic color left off so the palette stays predictable and boring per PRD §21.
- Add Navigation Compose with two routes — `library` and `player/{bookId}` — where Library
  renders a permanent empty state and Player renders a placeholder. No playback, no storage,
  no picker, no data model.
- Add the JUnit test source set with one trivial test, so the test task is wired and later
  changes can add pure-logic tests without touching the build.
- Add `README.md` covering opening the project in Android Studio, building from the command
  line, producing a debug APK, and where the APK lands (PRD §29). The `.m4b` limitations
  section of the README is deferred to the change that implements chapter parsing.

## Capabilities

### New Capabilities

- `app-shell`: The application builds into an installable APK, launches to the Library screen,
  navigates between Library and Player, follows the system light/dark theme, and ships with no
  network permission. This is the container every later capability plugs into.

### Modified Capabilities

None — this is the first change in the project and there are no existing specs.

## Impact

**Code**: Creates the entire source tree from nothing — `settings.gradle.kts`,
`build.gradle.kts` (root and `:app`), `gradle/libs.versions.toml`, `AndroidManifest.xml`,
`MainActivity.kt`, theme files, two placeholder composables, and `README.md`.

**Dependencies**: This change wires only what it uses — the Compose BOM (Compose UI, Material 3,
tooling) and Navigation Compose, plus the AGP and Kotlin plugins. Media3, Room, and Coil are
*declared in the version catalog* but deliberately **not** added to `:app` dependencies yet;
each gets wired in by the change that first needs it, so its justification and its APK cost
land together rather than arriving as unused weight here. No dependency added by this change
is new to the agreed stack in `openspec/config.yaml`.

**Permissions and manifest**: This change adds **no permissions at all**. Notably it does not
add `INTERNET`, and it establishes the requirement that the *merged* manifest contain no
network permission — a check that matters later, when transitively-declared permissions from
image and media libraries could otherwise slip in silently (PRD §24).

**Build/tooling**: Requires resolving and pinning AGP and Kotlin versions compatible with
`compileSdk 37`, and requires `local.properties` pointing at `D:\Android\Sdk` (already
gitignored). Committing `gradle/wrapper/gradle-wrapper.jar` is intended — `.gitignore` already
carries an explicit un-ignore for it.

**Risk**: Low behaviorally — there is no behavior yet. The real risk is version-compatibility
churn between AGP, Kotlin, the Compose compiler, and `compileSdk 37`, which is why pinning
those versions is an explicit task rather than an assumption.

## Non-goals

This change deliberately does **not**:

- Play any audio, or add Media3/ExoPlayer to the module's dependencies.
- Add the Storage Access Framework picker, or any file or folder selection.
- Add Room, a database schema, or any persistence.
- Add cover art loading or Coil.
- Add any real Library or Player UI beyond an empty state and a placeholder — the layouts in
  PRD §20 belong to their own changes.
- Add a settings screen (PRD §20 explicitly says none is required initially).
- Set up signing config, release builds, CI, or Play Store metadata. PRD §29 asks for a
  sideloadable debug APK; release signing is not in scope for the MVP.
