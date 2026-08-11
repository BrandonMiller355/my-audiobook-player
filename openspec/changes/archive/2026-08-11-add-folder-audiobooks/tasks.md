## 1. Build setup

- [x] 1.1 Resolve the KSP plugin version that matches Kotlin 2.4.10 from the repository — its versioning scheme changed, and a mismatch fails the build rather than warning — then add it to the version catalog
- [x] 1.2 Apply the KSP plugin and wire `room-runtime`, `room-ktx`, and `room-compiler` into `:app` from the existing catalog entries
  - `androidx.documentfile` was **not** added: `DocumentsContract` covers resolving the tree URI and its display name, so the dependency was unnecessary. `lifecycle-runtime-compose` was added instead, for lifecycle-aware state collection.
- [x] 1.3 Enable Room schema export to `app/schemas` and make sure that directory is committed rather than gitignored
- [x] 1.4 Confirm `gradlew assembleDebug` still succeeds before writing any feature code

## 2. Natural sort

Pure logic with no Android dependency, so it goes first and is fully unit tested.

- [x] 2.1 Implement natural ordering: split names into digit and non-digit runs, compare digit runs numerically and the rest case-insensitively (design D3)
- [x] 2.2 Unit-test against the real library's shapes — `Chapter 1/2/3/10`, `1-01 … 1-09 … 1-10 … 2-01 … 9-15`, `01 of 09` through `09 of 09`, names with no digits, mixed case, and equal names for stability
- [x] 2.3 Unit-test leading zeros and very long digit runs, so `007` and a 30-digit number neither mis-sort nor overflow

## 3. Room persistence

- [x] 3.1 Define the `Audiobook` entity: id, source tree URI, source type, title, and added/last-played timestamps (PRD §19, minus the fields no change produces yet)
- [x] 3.2 Define the `Chapter` entity: id, audiobook id, index, title, media URI, and start position, with a foreign key that cascades on delete (design D5)
- [x] 3.3 Write the DAO: observe all books with chapter counts, insert a book with its chapters in one transaction, load one book's chapters, delete a book
- [x] 3.4 Create the database class with a hand-built lazy singleton — no DI framework, no destructive migration fallback (design D7)
- [x] 3.5 Commit the exported version-1 schema JSON as the migration baseline

## 4. Folder scanning

- [x] 4.1 Implement the scan with a single `ContentResolver.query` against `DocumentsContract.buildChildDocumentsUriUsingTree`, projecting document id, display name, and MIME type — explicitly not `DocumentFile.listFiles()` (design D2)
- [x] 4.2 Filter to supported extensions case-insensitively, treating MIME type as advisory only (design D4)
- [x] 4.3 Ignore subdirectories entirely, and return a distinct "no supported audio files" outcome for a folder that yields nothing (design D1)
- [x] 4.4 Order the results with the natural sort from §2 and build chapter records with sequential indices
- [x] 4.5 Unit-test the filtering and ordering logic by driving it from an in-memory list of names, keeping fakes inline in the test file

## 5. Adding and removing

- [x] 5.1 Launch `ACTION_OPEN_DOCUMENT_TREE` from the Library screen with an `ActivityResultContract`
- [x] 5.2 Take a persistable read permission on the returned tree URI (design D6)
- [x] 5.3 Scan and insert off the main thread, in one transaction, with the folder name as the book title and file names as chapter titles (design D8)
- [x] 5.4 Implement removal: delete the book and its chapters, and release the persisted URI permission — never touch the source files
- [x] 5.5 Handle the picker being cancelled without changing the library or showing an error

## 6. Library screen

- [x] 6.1 Add a ViewModel exposing the book list as observable state, constructed with an explicit factory rather than injection
- [x] 6.2 Replace the permanent empty state with a list of books showing title and chapter count, falling back to the empty state only when the library is genuinely empty
- [x] 6.3 Add the add-book control to the Library screen
- [x] 6.4 Add remove-from-library as a long-press or overflow action, with a confirmation that states plainly that source files are not deleted
- [x] 6.5 Wire a book tap to `player/{bookId}`, and have the Player placeholder show the book's title and chapter count so the argument is visibly plumbed through
- [x] 6.6 Show a readable message for a book whose folder is missing or whose permission was revoked, keeping it removable (PRD §22)

## 7. Deferred check from the previous change

- [x] 7.1 Verify forward navigation into `player/{bookId}` now that a tappable book exists, that system back returns to the Library, and that back from the Library exits cleanly — this is `bootstrap-android-project` task 4.4, which could not be run until something could trigger it

## 8. On-device verification

**Verified on the API 36 emulator against replicated folder structures, not on the phone.** The
books live on `H:\`, which the emulator cannot reach — but nothing in this change reads file
*bytes*, only names and MIME types through SAF. So the four folders were recreated on the device
with identical names and nesting as zero-byte files, which exercises the picker, the provider,
the scan, the ordering, and the permission grants faithfully. What this does **not** cover is a
different vendor's document provider on the owner's actual phone; re-running 8.1–8.8 there is
still worth doing.

- [x] 8.0 Replicate `Born To Run` (132 entries incl. 4 non-audio), `I Am Legend`, `ProjectHailMary`, and `Expanse, The` on the device with their real names and nesting

- [x] 8.1 Add `Born To Run_Christopher McDougall_Fred Sanders` (128 files, `1-01 … 9-15`) and confirm all 128 chapters appear in disc-then-track order with no visible stall
  - 128 chapters from 132 entries — the 2 cover images and 2 text files were excluded. Verified against the on-device database: strictly ascending by (disc, track) across all nine discs, indices 0–127 with no gaps, all `startPositionMs` zero, all media URIs distinct. Added without a visible stall.
- [x] 8.2 Add `I Am Legend - AudioBook - MP3` (9 files, `01 of 09`) and confirm the order
- [x] 8.3 Select `Expanse, The` — the nine-book series folder — and confirm it reports no supported audio files rather than creating a 357-chapter book (design D1)
- [x] 8.4 Select the `ProjectHailMary` top folder and confirm the same clear refusal; then select the folder that actually holds the audio and confirm it adds correctly
  - The audio is **two** levels down, not one — `ProjectHailMary / ProjectHailMary - Audiobook / Andy Weir - Project Hail Mary /`. Both upper folders correctly refused; the deepest added as 30 chapters.
- [x] 8.5 Kill the app process, relaunch, and confirm the library and chapter counts are intact
- [x] 8.6 Reboot the device, reopen the app, and confirm the books are still listed and their files still readable without re-picking (PRD §7.1)
- [x] 8.7 Remove a book and confirm on the filesystem that the source folder and all its files still exist
- [x] 8.8 Confirm Android Settings still shows no permissions requested for the app

## 9. Final checks

- [x] 9.1 Run `gradlew testDebugUnitTest` and confirm the whole suite passes
- [x] 9.2 Re-run the merged-manifest no-network check from the README, since Room is a new dependency and this is exactly the case that check exists for
- [x] 9.3 Confirm no storage permission appeared in the merged manifest either
