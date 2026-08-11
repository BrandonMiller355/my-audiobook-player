## Why

The app opens to an empty Library and has no way to fill it. This change makes the library real:
the owner picks a folder of audio files, it becomes a book, and it is still there after a restart
or a reboot. It implements PRD §7.1 (library), §16 (file access), §17 (natural sorting), §19 (data
model), and §20.1 (library screen), and is PRD §27 Phase 2 minus playback.

Folder books come before `.m4b` books because they are the bulk of the owner's actual library —
643 mp3 files across six-plus books, against 8 `.m4b` files. Surveying those folders also settled
several design questions that would otherwise have been guesswork; they are recorded in
`design.md` and reflected in the requirements below.

## What Changes

- Add a Storage Access Framework folder picker (`ACTION_OPEN_DOCUMENT_TREE`) and take a
  **persistable** URI permission so the book survives a reboot without being re-picked.
- Scan the picked folder's **direct children only** — no recursion. Surveying the real library
  showed why: `Expanse, The` contains 357 mp3 files spanning nine separate books, and recursing
  would fuse an entire series into one 357-chapter monster. One folder is one book.
- Scan with a single `ContentResolver` query against
  `DocumentsContract.buildChildDocumentsUriUsingTree`, not `DocumentFile.listFiles()`, which
  costs one IPC round trip per file and is visibly slow on the 128-file Born To Run folder.
- Order files by **natural sort** — digit runs compared numerically — so `1-01 … 9-15`,
  `01 of 09`, and `Chapter 1 … Chapter 10` all order the way a human reads them.
- Add Room with `Audiobook` and `Chapter` tables, and construct the database by hand — no DI
  framework, per `config.yaml`.
- Replace the Library empty state with a list of books showing title and chapter count, tapping
  through to the existing Player placeholder. This finally makes the navigation check deferred
  from `bootstrap-android-project` (its task 4.4) testable.
- Add "remove from library", which deletes the row and **releases** the persisted URI permission
  while never touching the user's files (PRD §7.1).
- Handle the folder-shaped error cases from PRD §22: a folder with no supported audio, a folder
  whose permission has been revoked, and a source folder that has disappeared.

## Capabilities

### New Capabilities

- `audiobook-library`: what a library is — adding a book, listing books, removing one without
  destroying source files, and surviving process death and reboot via persisted URI permissions.
- `folder-scanning`: what counts as a folder audiobook — which file types are accepted, that only
  direct children are considered, and the ordering rule that turns a set of files into chapters.

### Modified Capabilities

- `app-shell`: the Library screen requirement changes. It currently specifies a permanent empty
  state; it must now show the empty state only when no books exist, and a list otherwise. The
  navigation requirement's forward-navigation scenario becomes reachable through the UI.

## Impact

**Code**: New `library/` package (SAF picker, scanner, natural sort), new `data/` package (Room
entities, DAO, database), a rewritten `LibraryScreen` with a ViewModel. `AudiobooksApp` gains the
wiring to construct them. `PlayerScreen` stays a placeholder.

**Dependencies**: Adds **Room** (`room-runtime`, `room-ktx`, `room-compiler` via KSP), already
declared in the version catalog and the persistence choice agreed in `config.yaml`, plus
`lifecycle-runtime-compose` for lifecycle-aware state collection. `androidx.documentfile` was
expected to be needed and turned out not to be — `DocumentsContract` covers the whole job — so it
was **not** added. KSP is a new Gradle plugin and its
version must be matched to Kotlin 2.4.10 — pinning it is an explicit task, since the version scheme
changed and a mismatch fails at build time rather than silently.

**Permissions and manifest**: **No new permissions.** SAF grants access per-document through the
picker, so no storage permission is declared — PRD §16 requires exactly this. The merged-manifest
no-network check must still pass; Room and documentfile should contribute nothing, and the task
list verifies rather than assumes it.

**Data**: Introduces the first schema. Room's schema export is enabled and the version-1 schema
committed, so later migrations have a baseline instead of starting from a destructive fallback.

**Risk**: The scan performance trap and the recursion decision are both settled by evidence from
the real library rather than guessed. The genuine unknown is SAF behavior across providers — the
files live on an external drive, and how a given provider reports children and honors persisted
permissions is the thing most likely to surprise on device.

## Non-goals

This change deliberately does **not**:

- Play anything. No ExoPlayer, no MediaSession, no service. Tapping a book opens the placeholder.
- Add `.m4b` single-file books, or call the chapter parser built in `parse-m4b-chapters`. A folder
  containing one `.m4b` — the owner has one — is out of scope here and belongs with m4b support.
- Read cover art or embedded metadata. Titles come from the folder name and chapter titles from
  file names (PRD §11 fallbacks). The library list shows no artwork yet.
  Worth recording for that change: the real covers are named `Born To Run_cover-lg.jpg`, which
  PRD §10's `cover.jpg`/`folder.jpg` list would not match.
- Sort by embedded track/disc metadata. PRD §17 prefers it when "complete and reliable", but
  reading tags for 128 files at add time is slow and belongs with the metadata change. Natural
  filename sort is correct for every folder book in the owner's library as it stands.
- Store or display playback progress or speed. Nothing produces them yet.
- Recurse into subfolders, or offer multi-select, drag-reorder, or library sorting options.
