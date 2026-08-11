## Context

The app shell exists and does nothing. This change gives it content, and most of its decisions
were settled by surveying the owner's real library rather than reasoning in the abstract.

What is actually on disk under `H:\eBooks`:

| Book | Shape | What it forces |
|---|---|---|
| Born To Run | **128 mp3s, flat**, `1-01 … 9-15` (9 discs) | ordering must handle disc-track names; scan must be fast |
| I Am Legend | 9 mp3s, `01 of 09`, **no image file at all** | no cover to find; embedded art only |
| Metro 2033 | audio in an `ENGLISH AUDIOBOOK` **subfolder**, ebook in a sibling | picked folder may be a parent |
| Project Hail Mary | audio **two** levels down (`ProjectHailMary / … - Audiobook / Andy Weir - Project Hail Mary /`) | picked folder may be well above the audio |
| **Expanse, The** | **357 mp3s spanning nine books** under `Audiobooks/The Expanse/` | recursion would be catastrophic |
| The Fisherman | a folder containing a single `.m4b` | "folder" does not imply mp3 |
| Mistborn | 7 `.m4b` files in one folder | not this change |

Counted across the library: 643 mp3 files against 8 `.m4b`. Folder books are the common case.

Constraints inherited: SAF only, never broad storage permissions, never copy media (PRD §16);
Room for persistence and no DI framework (`config.yaml`); removing a book must never delete the
user's files (PRD §7.1).

## Goals / Non-Goals

**Goals:**

- Add a folder as a book, list it, open it, remove it — with the library surviving reboot.
- Order chapters the way a person reads the filenames, for every book in the real library.
- Add a 128-file book without a visible stall.
- Establish the first Room schema, with a committed baseline for future migrations.

**Non-Goals:**

- Playback, `.m4b` books, cover art, embedded metadata, progress, speed — see the proposal.
- A general-purpose media scanner. This scans one folder on demand, never the whole device.

## Decisions

### D1: One folder is one book — direct children only, no recursion

The scanner reads the picked folder's immediate children and ignores subdirectories entirely.

*Why:* `Expanse, The` contains 357 mp3s across nine novels. Recursing from a folder the user
plausibly picks would produce a single 357-chapter "book" spanning an entire series — not a bug
that degrades gracefully, but a useless library entry that is tedious to undo. Non-recursion is
predictable: what you picked is what you get.

*Cost, accepted:* Metro 2033 keeps its audio one level down and Project Hail Mary two, so picking
their top folder finds nothing. That path is not silent — it surfaces the "no supported audio
files here" error from PRD §22, which tells the user to pick the inner folder. A wrong-but-quiet
result would be worse than a clear refusal.

*Alternative considered:* recurse when the picked folder directly contains no audio and exactly
one descendant subtree does. It would handle Metro and PHM automatically, but it is a heuristic
that guesses at intent, and on `Expanse, The` it would still have to decide between nine
candidates. Rejected as too clever for a first pass; can be revisited if picking inner folders
proves annoying in practice.

### D2: Scan with one `DocumentsContract` cursor, not `DocumentFile.listFiles()`

Query `DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)` once, with an
explicit projection of document id, display name, and MIME type.

*Why:* `DocumentFile.listFiles()` performs a separate IPC round trip per child and then another
per attribute read. On the 128-file Born To Run folder that is hundreds of round trips for data
one cursor already contains.

*Revised during implementation:* `androidx.documentfile` was going to be kept for the narrow job of
resolving the tree URI to its document id and display name. It turned out to be unnecessary —
`DocumentsContract.getTreeDocumentId` and a one-row query cover both — so the dependency was
dropped rather than added. Fewer dependencies is the PRD §28 rule 13 preference anyway.

### D3: Natural sort on file names, with metadata ordering deferred

Split each name into runs of digits and non-digits; compare digit runs numerically and the rest
case-insensitively.

*Why:* it produces the right order for every folder book in the real library — `1-01 … 1-99`
before `2-01`, `Chapter 2` before `Chapter 10`, `01 of 09` in sequence. PRD §17 asks to prefer
embedded track/disc metadata "when it is complete and reliable", but establishing that reliably
means opening and tag-reading 128 files during an add, which is slow and belongs with the metadata
change. Filename order is correct for everything on hand today, so the simpler rule ships first.

*Note:* zero-padded names like Born To Run's would also sort correctly lexicographically. Natural
sort is not redundant — `Chapter 1 / Chapter 10 / Chapter 2` in the Expanse folders is exactly the
PRD §17 failure case, and unpadded names are common.

### D4: Accept files by extension, checked against Media3's supported formats

Accept `mp3`, `m4a`, `m4b`, `aac`, `ogg`, `opus`, `flac`, `wav` by file-name extension, and treat
MIME type from the cursor as a secondary signal rather than the authority.

*Why:* SAF providers report MIME types inconsistently — `application/octet-stream` for perfectly
good mp3s is common, especially from removable storage. Extension is the more reliable signal for
the files this app cares about. `m4b` is accepted here because The Fisherman is a folder containing
one; it is treated as an ordinary single-chapter media file in this change, with real chapter
support arriving when `.m4b` books do.

### D5: `Chapter` rows now, one per file

Each accepted file becomes a `Chapter` row with its own `mediaUri`, `startPositionMs = 0`, and
`index` from the sort order — the shape PRD §19 describes for folder books.

*Why:* it matches the data model the PRD already specifies, and it means the `.m4b` change adds a
second way to *populate* chapters rather than a second schema. Durations are left null: obtaining
them requires opening every file, which is the metadata change's job. The library therefore shows
chapter counts, not total time.

### D6: Persist and release URI permissions explicitly

Take `FLAG_GRANT_READ_URI_PERMISSION` persistably on add; call `releasePersistableUriPermission`
on remove.

*Why:* PRD §7.1 requires the library to survive a reboot, which persistable permissions are exactly
for. Releasing on remove matters because the per-app grant table is a finite, system-wide resource
— leaking grants for every removed book eventually breaks adding new ones, and the failure appears
much later and looks unrelated.

### D7: Construct Room by hand; database as a lazily-created singleton

A single `AudiobookDatabase` instance created on first use, passed explicitly into the ViewModel
factory. No Hilt, no Koin, no service locator.

*Why:* `config.yaml` forbids a DI framework, and Room's `INSTANCE` pattern is one small piece of
boilerplate against a whole dependency. Schema export is on and the v1 JSON is committed, so the
first real migration has a baseline rather than falling back to destructive recreation.

### D8: Book title from the folder name, chapter titles from file names

Per PRD §11's fallback rules. No tag reading in this change.

*Consequence worth seeing:* Born To Run's folder is
`Born To Run_Christopher McDougall_Fred Sanders`, so that is the title the library will show until
the metadata change reads real tags. That is ugly but honest, and PRD §11 explicitly names folder
name as the fallback.

## Risks / Trade-offs

**SAF behavior on an external drive is the real unknown** → The books live on `H:\`. How a given
provider enumerates children, whether it grants persistable permissions, and whether it survives
a remount are provider-specific. Mitigated by making the on-device checks explicit tasks against
the actual folders rather than a synthetic test directory.

**Picking a parent folder finds nothing (D1)** → Surfaces as a clear error naming the problem
rather than an empty book. Revisit only if it proves annoying in daily use.

**KSP version must match Kotlin 2.4.10** → Its versioning scheme changed and a mismatch fails the
build. Pinned as an explicit task, resolved from the repository rather than guessed.

**A 128-chapter insert on the main thread would jank** → Scanning and insertion happen off the main
thread, and the whole book is inserted in one transaction so a failure cannot leave a half-scanned
book in the library.

**Room schema decisions outlive this change** → Chapters are deliberately shaped for both book
types now (`mediaUri` plus `startPositionMs`), so `.m4b` support adds rows of a different shape
rather than a migration.

## Migration Plan

First schema; nothing to migrate from. Schema version 1 is exported and committed. Uninstalling
the debug app is an acceptable reset during development, but destructive fallback is **not**
enabled in code — a future migration failure should be loud, not silently wipe a library.

## Open Questions

- **Should a folder containing exactly one `.m4b` be offered as a folder book at all?** Under D4 it
  becomes a one-chapter folder book, which is correct-but-poor for The Fisherman; the `.m4b` change
  will do better. Not blocking, and no data is lost either way — the book can be removed and
  re-added.
- **Duplicate adds.** Adding the same folder twice currently creates two entries. Deduplicating on
  tree URI is a small addition; deferred until it is known whether it is actually a nuisance.
