## 1. Build setup

- [ ] 1.1 Resolve the KSP plugin version that matches Kotlin 2.4.10 from the repository — its versioning scheme changed, and a mismatch fails the build rather than warning — then add it to the version catalog
- [ ] 1.2 Apply the KSP plugin and wire `room-runtime`, `room-ktx`, `room-compiler`, and `androidx.documentfile` into `:app` from the existing catalog entries
- [ ] 1.3 Enable Room schema export to `app/schemas` and make sure that directory is committed rather than gitignored
- [ ] 1.4 Confirm `gradlew assembleDebug` still succeeds before writing any feature code

## 2. Natural sort

Pure logic with no Android dependency, so it goes first and is fully unit tested.

- [ ] 2.1 Implement natural ordering: split names into digit and non-digit runs, compare digit runs numerically and the rest case-insensitively (design D3)
- [ ] 2.2 Unit-test against the real library's shapes — `Chapter 1/2/3/10`, `1-01 … 1-09 … 1-10 … 2-01 … 9-15`, `01 of 09` through `09 of 09`, names with no digits, mixed case, and equal names for stability
- [ ] 2.3 Unit-test leading zeros and very long digit runs, so `007` and a 30-digit number neither mis-sort nor overflow

## 3. Room persistence

- [ ] 3.1 Define the `Audiobook` entity: id, source tree URI, source type, title, and added/last-played timestamps (PRD §19, minus the fields no change produces yet)
- [ ] 3.2 Define the `Chapter` entity: id, audiobook id, index, title, media URI, and start position, with a foreign key that cascades on delete (design D5)
- [ ] 3.3 Write the DAO: observe all books with chapter counts, insert a book with its chapters in one transaction, load one book's chapters, delete a book
- [ ] 3.4 Create the database class with a hand-built lazy singleton — no DI framework, no destructive migration fallback (design D7)
- [ ] 3.5 Commit the exported version-1 schema JSON as the migration baseline

## 4. Folder scanning

- [ ] 4.1 Implement the scan with a single `ContentResolver.query` against `DocumentsContract.buildChildDocumentsUriUsingTree`, projecting document id, display name, and MIME type — explicitly not `DocumentFile.listFiles()` (design D2)
- [ ] 4.2 Filter to supported extensions case-insensitively, treating MIME type as advisory only (design D4)
- [ ] 4.3 Ignore subdirectories entirely, and return a distinct "no supported audio files" outcome for a folder that yields nothing (design D1)
- [ ] 4.4 Order the results with the natural sort from §2 and build chapter records with sequential indices
- [ ] 4.5 Unit-test the filtering and ordering logic by driving it from an in-memory list of names, keeping fakes inline in the test file

## 5. Adding and removing

- [ ] 5.1 Launch `ACTION_OPEN_DOCUMENT_TREE` from the Library screen with an `ActivityResultContract`
- [ ] 5.2 Take a persistable read permission on the returned tree URI (design D6)
- [ ] 5.3 Scan and insert off the main thread, in one transaction, with the folder name as the book title and file names as chapter titles (design D8)
- [ ] 5.4 Implement removal: delete the book and its chapters, and release the persisted URI permission — never touch the source files
- [ ] 5.5 Handle the picker being cancelled without changing the library or showing an error

## 6. Library screen

- [ ] 6.1 Add a ViewModel exposing the book list as observable state, constructed with an explicit factory rather than injection
- [ ] 6.2 Replace the permanent empty state with a list of books showing title and chapter count, falling back to the empty state only when the library is genuinely empty
- [ ] 6.3 Add the add-book control to the Library screen
- [ ] 6.4 Add remove-from-library as a long-press or overflow action, with a confirmation that states plainly that source files are not deleted
- [ ] 6.5 Wire a book tap to `player/{bookId}`, and have the Player placeholder show the book's title and chapter count so the argument is visibly plumbed through
- [ ] 6.6 Show a readable message for a book whose folder is missing or whose permission was revoked, keeping it removable (PRD §22)

## 7. Deferred check from the previous change

- [ ] 7.1 Verify forward navigation into `player/{bookId}` now that a tappable book exists, that system back returns to the Library, and that back from the Library exits cleanly — this is `bootstrap-android-project` task 4.4, which could not be run until something could trigger it

## 8. On-device verification

The emulator can cover the UI, but **SAF against the real books needs the owner's phone or the
files copied to the device** — the library lives on an external drive (`H:\`), and provider
behavior is exactly what is being tested. Fixtures the owner supplies:

- [ ] 8.1 Add `Born To Run_Christopher McDougall_Fred Sanders` (128 files, `1-01 … 9-15`) and confirm all 128 chapters appear in disc-then-track order with no visible stall
- [ ] 8.2 Add `I Am Legend - AudioBook - MP3` (9 files, `01 of 09`) and confirm the order
- [ ] 8.3 Select `Expanse, The` — the nine-book series folder — and confirm it reports no supported audio files rather than creating a 357-chapter book (design D1)
- [ ] 8.4 Select the `ProjectHailMary` top folder, which keeps its audio one level down, and confirm the same clear refusal; then select the inner `ProjectHailMary - Audiobook` folder and confirm it adds correctly
- [ ] 8.5 Kill the app process, relaunch, and confirm the library and chapter counts are intact
- [ ] 8.6 Reboot the device, reopen the app, and confirm the books are still listed and their files still readable without re-picking (PRD §7.1)
- [ ] 8.7 Remove a book and confirm on the filesystem that the source folder and all its files still exist
- [ ] 8.8 Confirm Android Settings still shows no permissions requested for the app

## 9. Final checks

- [ ] 9.1 Run `gradlew testDebugUnitTest` and confirm the whole suite passes
- [ ] 9.2 Re-run the merged-manifest no-network check from the README, since Room and documentfile are new dependencies and this is exactly the case that check exists for
- [ ] 9.3 Confirm no storage permission appeared in the merged manifest either
