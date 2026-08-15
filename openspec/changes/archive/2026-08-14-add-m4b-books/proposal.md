## Why

The app plays folder books end to end, and `parse-m4b-chapters` built a working MP4 chapter
parser — but nothing calls it. A `.m4b` cannot be added to the library at all, so half of the
PRD's two required book types (§1, §7.1, §16, §26.2, §26.4) does not exist yet, and the parser
is dead code. This change connects the two.

Cover art comes along because the `.m4b` container is where artwork actually lives for this
library's books, and the extraction happens on the same file, at the same moment, using the same
open handle as chapter parsing. Implementing PRD §10 for folder books is a separate matter of
looking for `cover.jpg` in a directory and shares no code with it.

PRD sections implemented: §7.1 (accepted book types — single-file audiobook), §7.4 (`.m4b`
chapter navigation), §8 (M4B embedded chapter support), §10 (cover art, embedded source only),
§16 (single-file selection through SAF), §18 (one media item plus an internal chapter timeline),
§19 (`sourceType`, shared `mediaUri`, chapter start/end), §20.3 (add flow offering both choices),
§22 (`.m4b` with no chapters, `.m4b` with unusual chapter metadata), §27 phase 6.

## What Changes

- The add control becomes a choice of two: pick a folder, or pick a single `.m4b` file. Both
  land in the same library list and open the same Player.
- Adding a `.m4b` parses its chapters once, at add time, and stores them — the app never
  re-parses the file on later launches (PRD §8, §23).
- A `.m4b` book plays as **one** `MediaItem`. Chapter navigation, ±10s/±60s seeking, and the
  book-wide scrubber all become seeks within that single item, driven by the stored chapter
  boundaries rather than by the playlist's shape.
- A `.m4b` with no usable chapter marks — which is every book in the owner's current library —
  becomes a one-chapter book covering the whole file (PRD §7.4). A `.m4b` whose chapter data is
  unreadable is added the same way, with a message saying chapters could not be read; the audio
  is still playable and refusing the book would be worse than losing chapter marks.
- Embedded cover art is extracted at add time, downsampled once into app-private storage, and
  shown in the library list and on the Player screen. Books without embedded art show a
  placeholder.
- The Player's chapter title and chapter count come from the stored chapter list rather than
  from the current `MediaItem`'s metadata, which for a single-item book names the book, not the
  chapter.
- Room schema goes to version 3: `chapters.endPositionMs` and `audiobooks.artworkPath`, both
  additive and nullable, with a real migration.

## Capabilities

### New Capabilities

- `m4b-books`: adding a single `.m4b` file to the library, extracting and caching its chapters
  at add time, and playing it as one media item whose chapter navigation is internal seeking.
  Covers the unchaptered and unreadable outcomes as normal, non-fatal states.
- `cover-art`: extracting embedded artwork from a book's source, caching a downsampled copy
  locally, displaying it in the library and Player, and falling back to a placeholder. Scoped in
  this change to embedded `.m4b` artwork; the requirements are written so folder-book cover
  lookup is an addition later, not a rewrite.

### Modified Capabilities

- `audiobook-library`: the add flow currently specifies folder selection as the only way to add
  a book, and states removal and unavailability in terms of a folder's URI grant. Those
  requirements change to cover a single picked file as an equal alternative — a document URI
  grant taken, released on removal, and reported when lost, exactly as a tree URI is.

`transport-controls` is deliberately **not** modified. Its requirements were written to be
book-type-agnostic ("identical for folder-based books and for any other book type"), and
`BookTimeline` was built to accept both shapes. If wiring `.m4b` required changing those
requirements, the earlier design was wrong; it does not.

## Impact

**Code**

- `ui/library/` — a second picker and the two-way add choice; a custom `ActivityResultContract`
  is needed because `ActivityResultContracts.OpenDocument` does not request a persistable grant.
- `library/` — a new single-file path alongside `FolderScanner`: read display name, open a
  seekable channel over the content URI, run `M4bChapterParser`, extract artwork.
- `data/` — `Migrations.kt` gains `MIGRATION_2_3`; `Entities.kt` gains two nullable columns;
  `LibraryDao` gains artwork-path and duration reads for the library list.
- `playback/PlayerTimeline.kt` — `chapterTimeline()` currently derives bounds from the player's
  playlist, which is only correct for folder books. It has to build from stored chapters.
- `playback/ChapterAwarePlayer.kt` — the same problem at the service level: a Bluetooth
  "previous" on a single-item book has no playlist shape to read. This is the one genuinely
  awkward part of the change and is where design effort goes.
- `ui/player/` — chapter identity from the stored list; cover art on screen.

**Dependencies**

One new dependency wired into `:app`: `coil-compose` 3.5.0, already declared in the version
catalog and named in `config.yaml`'s tech stack, unwired until now under the "add it in the
change that needs it" rule. Justification: the library list decodes a cover per row and the
Player decodes a large one; Coil provides asynchronous decoding off the main thread, size-aware
downsampling, and a memory/disk cache keyed per image. Hand-rolling that against
`BitmapFactory` for a scrolling list is the kind of thing the PRD's "prefer maintained
libraries" rule exists for. It is a local image loader — no network use is introduced.

No other new dependency. The chapter parser, the duration reader (`MediaMetadataRetriever`), and
the artwork reader are all already present or stock SDK.

**Permissions and manifest**

No change. `ACTION_OPEN_DOCUMENT` requires no permission, and the artwork cache lives in
app-private storage. The app still declares no storage permission and no `INTERNET` permission —
Coil's core artifact does not require one, and the existing no-network check in `app-shell`
must keep passing.

**Data**

Room version 2 → 3. Additive columns only; existing folder books keep working with both new
columns null. No destructive migration, consistent with `add-folder-audiobooks` design D7.

**Source files**

Untouched, as always. Artwork is decoded from the source and cached as a separate downsampled
file; the original `.m4b` is never written to, and removing a book deletes only the app's
record and its cached cover.

## Non-goals

This change deliberately does **not**:

- Add cover art for **folder** books — no `cover.jpg`/`folder.png` directory lookup, no embedded
  art from `.mp3`/`.m4a` chapter files. The `cover-art` capability is written to accept those as
  additional sources later.
- Add author, album, or track metadata reading of any kind (PRD §11 beyond the title fallback).
  Book title comes from the file name without its extension; author stays blank.
- Show playback progress or the current chapter name in the library list (PRD §7.1's optional
  items). That is a library-screen change, unrelated to book type.
- Add `.m4a` or any other single-file format. The PRD names `.m4b` specifically for single-file
  books, and the chapter parser is an MP4 parser.
- Split, re-mux, transcode, or preprocess `.m4b` files in any way.
- Revisit chapter-parsing behavior. `m4b-chapters` is settled and its requirements are unchanged
  here; this change is purely about calling it and storing what it returns.
- Device-verify chaptered `.m4b` navigation. Every `.m4b` in the owner's library parses as
  unchaptered, so on-device checks cover the unchaptered path; chaptered behavior is covered by
  unit tests against synthetic fixtures, as it has been since `parse-m4b-chapters`.
