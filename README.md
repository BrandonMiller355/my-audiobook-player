# Audiobooks

A simple, local-only Android audiobook player for personal use. No accounts, no cloud, no
network. Requirements live in [`simple_android_audiobook_player.prd`](simple_android_audiobook_player.prd).

## Current status

The project skeleton only. The app builds, installs, and opens to an empty Library screen.
There is no playback, file picking, or persistence yet — those arrive in later changes tracked
under `openspec/changes/`.

## The bundled sample audiobook

The APK carries one public-domain audiobook, and the library seeds itself with it the first time
it opens. Install the APK and there is a playable book already there — no file to supply first.

| | |
|---|---|
| Title | *The Mystery of Black Rock Creek* |
| Authors | Jerome K. Jerome, Eden Phillpotts, E. F. Benson, F. Frankfort Moore, Barry Pain |
| Readers | Various (LibriVox volunteers) |
| Source | https://archive.org/details/mysteryblackrockcreek_2407_librivox |
| License | Public domain — LibriVox recordings are released into the public domain |
| In the repo | `app/src/main/assets/sample/the-mystery-of-black-rock-creek.m4b` |
| Size | 19.2 MB (the APK is that much larger; the device holds a second copy once seeded) |
| Duration | 41m 44s, AAC 44.1 kHz |
| Chapters | 5, real QuickTime chapter marks |
| Cover art | None embedded, so it shows the placeholder |

It is worth keeping for testing beyond its role as a demo: every `.m4b` in the owner's own library
parses as a single book-length chapter, so this is the only real file the chaptered path runs
against. `BundledSampleTest` asserts it still parses as five ascending chapters, which is what will
fail if the asset is ever replaced or re-encoded.

**Removing it is permanent.** It is removed like any other book — long-press, confirm — and that
deletes the app's copy and reclaims the space. It is never seeded again, short of clearing app
data. Removing it deletes only the app's own copy; that is the sole file removal ever deletes, and
it is guarded by directory containment rather than by name (`SampleLibrary`).

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

## Permissions

The app must ship without `INTERNET` and without any storage permission (PRD §24, §16). Checking
the source manifest is not enough — a dependency can contribute a permission during manifest
merging, which is exactly what Media3 did. **The build enforces this automatically:**

```powershell
.\gradlew.bat verifyReleasePermissions   # or verifyDebugPermissions
```

`assembleDebug` and `assembleRelease` depend on it, so a forbidden permission fails the build
naming the offender. The forbidden list lives in `app/build.gradle.kts`.

What the app legitimately declares, and why:

| Permission | Why |
|---|---|
| `FOREGROUND_SERVICE` | Playback continues with the app backgrounded (PRD §13) |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required on API 34+ for a `mediaPlayback` service |
| `POST_NOTIFICATIONS` | The media notification. Runtime permission on API 33+, requested at first playback, never at launch. Denial costs the notification, not the audio |
| `WAKE_LOCK` | Contributed by `media3-exoplayer`; keeps the CPU awake while audio plays with the screen off |

Two entries appear in the merged manifest that are not network or storage permissions:
`com.brandonmiller.audiobookplayer.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, an app-private
signature-level permission AndroidX Core defines for its own receivers, and Media3's
`BluetoothValidationActivity`.

**`ACCESS_NETWORK_STATE` is deliberately stripped.** `media3-exoplayer` declares it for adaptive
streaming, which this app never does — every source is a local `content://` URI. It is removed
with `tools:node="remove"` in `AndroidManifest.xml`, and playback was verified to work without it.
If a Media3 upgrade reintroduces it, the build fails rather than shipping it.

## `.m4b` chapter parsing

Limitations will be documented here by the change that implements the chapter parser. Do not
consider this section complete until then.

One thing already known from probing real files: an `.m4b` can carry a QuickTime chapter track
that contains a single entry spanning the whole book — structurally "chaptered" but carrying no
usable chapter marks. Such files are treated as unchaptered (PRD §7.4).

## Ebook companion

An audiobook can have one EPUB linked to it, read on a black reading page that the ebook icon on
the Player's cover art flips to and back. The two positions — where you are listening and where you
are reading — are independent; nothing tries to keep them in step. That is deliberate, and the
reasoning is in `handoffs/2026-08-10-ebook-audio-readalong.md`.

The EPUB is parsed in-app with no third-party dependency, which sets the limits below.

**Format.** EPUB only. MOBI and AZW3 are not supported and are not planned — modern Kindle formats
ship DRM-protected, and DRM is a PRD §3 non-goal. Convert with Calibre on the desktop.

**DRM.** An EPUB carrying `META-INF/encryption.xml` is refused with a message saying so. No attempt
is made to decrypt it.

**What renders.** Paragraphs, headings, italic, bold, underline, block quotes, line breaks,
horizontal rules, and ordered and unordered lists.

**What does not.** Images, tables (the text of each row appears, but not as a grid), footnote
popups, publisher CSS, right-to-left text, and vertical writing modes. Anything outside the list
above contributes its text without its formatting, so words are never lost — only their appearance.

**Performance.** The whole book is parsed each time the reader opens; there is no disk cache. A
730 KB, 96-chapter novel parses in about 140 ms into roughly 7,600 paragraphs.

**Reading position** is stored as a spine index and a character offset, not a scroll offset, so it
survives changes to text size, line spacing, typeface, and orientation.

> Note: the "Current status" section above predates most of the app and is out of date — playback,
> the library, persistence, and this reader all exist. It is left alone here rather than rewritten
> as a side effect of an unrelated change.

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
