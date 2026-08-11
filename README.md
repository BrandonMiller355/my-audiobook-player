# Audiobooks

A simple, local-only Android audiobook player for personal use. No accounts, no cloud, no
network. Requirements live in [`simple_android_audiobook_player.prd`](simple_android_audiobook_player.prd).

## Current status

The project skeleton only. The app builds, installs, and opens to an empty Library screen.
There is no playback, file picking, or persistence yet — those arrive in later changes tracked
under `openspec/changes/`.

## Requirements

| Component | Version | Location on this machine |
|---|---|---|
| Android SDK platform | `android-37` | `D:\Android\Sdk` |
| Build tools | `37.0.0` | `D:\Android\Sdk\build-tools` |
| JDK | 25 (JetBrains Runtime) | `D:\Android\Studio\jbr` |
| Gradle | 9.7.0 (via wrapper) | `D:\Android\gradle\gradle-9.7.0` |
| Android Gradle Plugin | 9.3.1 | — |
| Kotlin | 2.4.10 | — |

`minSdk` is 26 (Android 8.0); `compileSdk` and `targetSdk` are both 37.

Kotlin support is built into AGP 9, so there is no separate `kotlin-android` plugin — applying
one is an error. Only the Compose compiler plugin is applied explicitly.

### local.properties

Not committed. Create it in the repo root pointing at your SDK:

```properties
sdk.dir=D\:\\Android\\Sdk
```

## Opening in Android Studio

Open the repository root as a project (`File → Open`, select `D:\projects\my-audiobook-player`).
Studio will pick up `settings.gradle.kts` and sync using the wrapper. Use the bundled JDK
(`File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK`).

## Building from the command line

```powershell
$env:JAVA_HOME = "D:\Android\Studio\jbr"
.\gradlew.bat assembleDebug
```

The APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device with:

```powershell
D:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

An unsigned release APK can be produced with `.\gradlew.bat assembleRelease`, but no signing
config is set up — the MVP targets sideloaded debug builds (PRD §29).

## Running tests

```powershell
.\gradlew.bat testDebugUnitTest
```

JVM unit tests live in `app/src/test/`. Reports land in
`app/build/reports/tests/testDebugUnitTest/index.html`.

## Verifying no network permission

The app must ship without `INTERNET` (PRD §24). The source manifest declares no permissions at
all, but that is not the check that matters — a dependency can contribute a permission during
manifest merging. Verify against the **merged** manifest:

```powershell
.\gradlew.bat processReleaseManifest
Select-String -Path app\build\intermediates\merged_manifests\release\processReleaseManifest\AndroidManifest.xml `
  -Pattern 'android.permission.(INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE)'
```

No output means the check passes. Run this after adding any dependency — Coil is the most
likely future source of a merged-in network permission.

One `<uses-permission>` does legitimately appear in the merged manifest:
`com.brandonmiller.audiobookplayer.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. It is an
app-private, signature-level permission that AndroidX Core defines for its own broadcast
receivers. It is not a network permission and grants nothing to other apps.

## `.m4b` chapter parsing

Limitations will be documented here by the change that implements the chapter parser. Do not
consider this section complete until then.

One thing already known from probing real files: an `.m4b` can carry a QuickTime chapter track
that contains a single entry spanning the whole book — structurally "chaptered" but carrying no
usable chapter marks. Such files are treated as unchaptered (PRD §7.4).

## Project layout

```
app/src/main/java/com/brandonmiller/audiobookplayer/
├── MainActivity.kt              hosts Compose, applies the theme
└── ui/
    ├── AudiobooksApp.kt         NavHost + Routes
    ├── library/LibraryScreen.kt empty state
    ├── player/PlayerScreen.kt   placeholder
    └── theme/                   colors, type, AudiobooksTheme
```
