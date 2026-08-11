## Context

The repository holds a PRD, an OpenSpec root, and a `.gitignore` written in anticipation of an
Android project that does not exist yet. There is no Gradle build, no module, no manifest, and
no source tree.

Verified state of the development machine (checked, not assumed):

| Thing | State |
|---|---|
| Android SDK | `D:\Android\Sdk` — platforms `android-37.0`, `android-37.1` |
| Build tools | `36.0.0`, `37.0.0` |
| JDK | `D:\Android\Studio\jbr` (Studio's bundled JetBrains Runtime) |
| Gradle distribution | **none** — `D:\Android\gradle` is empty, `gradle` is not on PATH |
| Cached `gradle-wrapper.jar` | **none** under `~/.gradle` |

That last pair is the only genuinely awkward part of this change: the standard advice
"just run `./gradlew`" presumes a wrapper that cannot exist until something generates it, and
modern Android Studio no longer ships a full Gradle distribution to bootstrap from.

Constraints that shape everything below come from PRD §28 (do not overengineer, keep
dependencies minimal, prefer built-in APIs) and PRD §24 (no network, no INTERNET permission).

## Goals / Non-Goals

**Goals:**

- Produce a debug APK that installs on a phone and opens without crashing.
- Establish the module layout, theme, and navigation graph that every later change plugs into.
- Pin the AGP / Kotlin / SDK version matrix once, so later changes never have to think about it.
- Make "no network permission" a build-verifiable property from day one rather than a habit.
- Leave the test task wired so the first pure-logic test costs nothing to add.

**Non-Goals:**

- Any playback, storage, file picking, persistence, or artwork. Those are separate changes.
- Release signing, CI, Play Store metadata. PRD §29 asks only for a sideloadable debug APK.
- A settings screen — PRD §20 says none is required initially.
- Deciding the `BookTimeline` chapter abstraction. It belongs to the transport-controls change;
  designing it here without folder books or a parser to exercise it would be guesswork.

## Decisions

### D1: Single Gradle module `:app`

One module, no `:core` / `:data` / `:playback` split.

*Why:* PRD §28 rules 1 and 17 explicitly forbid over-abstraction and the app is small enough
that module boundaries would cost build complexity while buying nothing. *Alternative
considered:* splitting playback into its own module to keep the service isolated — rejected
because nothing outside this app will ever consume it, and Gradle module wiring is precisely
the "ceremony" `config.yaml` rules out.

### D2: Version catalog declares everything; `:app` wires only what it uses

`gradle/libs.versions.toml` carries version entries for Media3, Room, and Coil, but the `:app`
`dependencies { }` block does not reference them until the change that first needs them.

*Why:* `config.yaml` requires every new dependency to be justified in the proposal that adds it.
Wiring all of them here would mean either justifying them in a proposal that doesn't use them,
or (worse) letting them arrive unjustified. Declaring versions centrally still gets the
one-place-to-bump benefit. *Trade-off:* this defers part of PRD §27 Phase 1 ("Add Media3, add
persistence") by one change. The PRD calls that ordering *suggested*, and nothing is lost —
the versions are chosen here, only the `implementation` lines move.

### D3: Obtain the Gradle wrapper by installing a Gradle distribution once

Download the Gradle binary distribution, extract it to `D:\Android\gradle`, and run
`gradle wrapper --gradle-version <pinned>` in the project to generate `gradlew`, `gradlew.bat`,
and `gradle/wrapper/`. Commit all of them — `.gitignore` already carries
`!gradle/wrapper/gradle-wrapper.jar`, so committing the jar is the intended behavior.

*Why:* it leaves a reusable Gradle on the machine and keeps project creation reproducible from
the command line. *Alternatives considered:* (a) generate a throwaway project with Android
Studio's New Project wizard and copy its wrapper — works, but couples project setup to GUI
steps nobody will remember; (b) fetch `gradle-wrapper.jar` directly from a distribution
archive — fiddly and easy to get subtly wrong. Option (a) stays as the fallback if the
download path is inconvenient.

### D4: `compileSdk`/`targetSdk` 37, with a documented fallback to 36

Target the newest locally installed platform. If the AGP version required for `compileSdk 37`
turns out to be unstable or unavailable, fall back to `compileSdk 36` (build-tools `36.0.0` is
installed) and record the reason in the README.

*Why:* PRD §4 says "target the current stable Android SDK available in the development
environment." *Risk acknowledged:* the AGP ↔ Kotlin ↔ Compose-compiler ↔ `compileSdk` matrix is
the single most likely source of friction in this change, which is why pinning it is an
explicit task with a stated escape hatch rather than an assumption baked into a build file.

`minSdk 26` per PRD §4. Compose requires 21+, Media3 requires 21+, so 26 constrains nothing we
need and drops a large amount of legacy-compatibility surface.

### D5: Navigation Compose with two routes

Routes: `library` (start destination) and `player/{bookId}`. `bookId` is a string argument;
the Player placeholder simply displays it until the real screen exists.

*Why:* the PRD needs exactly two screens (§20), and passing the book identity through the route
rather than through shared mutable state means the Player screen never depends on the Library
screen having run first — which matters later when the app is restored into the Player after
process death. *Alternative considered:* a single composable with a `when` on a state enum,
avoiding the Navigation dependency entirely. Rejected because system back handling, argument
passing, and state restoration would all become hand-rolled — more code, not less.

### D6: Material 3 theme, system light/dark, dynamic color OFF

*Why:* PRD §21 asks for dark mode and system-theme support (so: `isSystemInDarkTheme()`), and
in the same breath asks for a clean, boring, high-readability design. Dynamic color would make
the palette depend on the user's wallpaper, which is the opposite of predictable contrast for
large tap targets used while walking or running.

### D7: `applicationId` = `com.brandonmiller.audiobookplayer`

*Why:* it only needs to be unique on the device, and this is a personal sideloaded app.
*Note:* changing it later means the new build installs alongside the old one rather than
upgrading it, taking its library data with it — so it is worth being happy with now. Flagged in
Open Questions.

### D8: No `Application` subclass, no DI container

Dependencies get constructed by hand where they are needed, per `config.yaml`. If a
process-wide singleton becomes genuinely necessary (the Room database is the likely candidate),
it arrives in the change that needs it, not speculatively here.

### D9: The no-network requirement is checked against the *merged* manifest

Verification reads the merged manifest from the build output, not the hand-written source
manifest.

*Why:* the source manifest trivially has no permissions — nobody is going to type `INTERNET`
into it by accident. The real risk is a transitive dependency contributing
`<uses-permission android:name="android.permission.INTERNET"/>` during manifest merging, which
is invisible unless you look at the merged result. Coil is the most likely future culprit. This
change establishes the check while the answer is trivially "none", so that later changes
inherit a check that already works.

## Risks / Trade-offs

**AGP / Kotlin / Compose-compiler / `compileSdk 37` version incompatibility** → Pin versions as
an explicit first task and verify with a real build before writing any UI. Documented fallback
to `compileSdk 36`. This is the most likely thing to consume unplanned time.

**Wrapper bootstrap requires network access on the dev machine** → Not a conflict with PRD §24,
which constrains the *app*, not the build. Called out only so nobody mistakes it for one.

**Committing `gradle-wrapper.jar` puts a binary in git** → Standard, intended practice; the
`.gitignore` un-ignore shows it was planned. Mitigation is simply pinning a known Gradle
version so the jar's provenance is clear.

**Deferring Media3/Room/Coil wiring (D2) could look like Phase 1 is incomplete** → The version
catalog carries the decisions; only the `implementation` lines move. Stated here so the
divergence from PRD §27 is deliberate and visible rather than an oversight.

**A placeholder Player screen invites scope creep** → It renders its route argument and nothing
else. The PRD §20.2 layout belongs to the transport-controls change.

## Migration Plan

Not applicable — greenfield. There is no existing build, no data, and no installed app to
migrate. Rollback is deleting the generated files; nothing downstream depends on this change
until it lands.

## Open Questions

- **`applicationId`**: `com.brandonmiller.audiobookplayer` is the working choice (D7). Cheap to
  change now, mildly annoying after the first install carries real library data.
- **`compileSdk` 37 vs 36**: resolved during implementation by whichever combination actually
  builds (D4). Recorded in the README either way.
- **App display name and launcher icon**: defaulting to "Audiobooks" and the stock adaptive-icon
  template. Not worth blocking on; trivially changed later.
