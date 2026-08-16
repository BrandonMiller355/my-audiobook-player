## 1. The asset

- [x] 1.1 Add `app/src/main/assets/sample/the-mystery-of-black-rock-creek.m4b` (19.2 MB, LibriVox, public domain)
- [x] 1.2 Add `androidResources { noCompress += "m4b" }` — AAC does not deflate, so compressing it costs build time and saves nothing
      — verified in the built APK: the entry is stored, compressed length == uncompressed length == 20,098,943
- [x] 1.3 Record the book's provenance and license in `README.md`, since public-domain attribution belongs somewhere a reader will find it
- [x] 1.4 Build and confirm the APK grew by roughly the asset's size and the app still runs unchanged
      — `assembleDebug` succeeds, `verifyDebugPermissions` still passes, APK is 35.5 MB

## 2. Availability for a non-SAF source

- [x] 2.1 Make `UriPermissionHolder.isHeld` answer by scheme: `file://` means "the file exists", anything else keeps the persisted-grant check (design D8)
- [x] 2.2 Make `release` a no-op for `file://` — there is no grant to give back, and the existing `runCatching` would hide that rather than state it
- [x] 2.3 Unit-test both against a real temp file: present, deleted, and a `content://` URI still taking the grant path
      — `UriPermissionHolderTest`, 4 tests

## 3. Seeding

- [x] 3.1 Add `library/SampleLibrary.kt`: the asset path, the destination in `filesDir/sample/`, the DataStore seed-once flag, and the containment guard
- [x] 3.2 Copy the asset to `filesDir/sample/The Mystery of Black Rock Creek.m4b` — the display title falls out of the file name via `titleFrom`, as it does for every other book (design D4)
- [x] 3.3 Copy through a temporary file and rename, so an interrupted copy cannot leave a truncated `.m4b` that parses as a broken book
- [x] 3.4 Read the copy with the existing `M4bReader.read()` — no second reader, no SAF-specific code to work around (design D3)
- [x] 3.5 Store via `insertBookWithChapters`, then set the seed flag **after** the commit, so an interrupted seed retries instead of vanishing (design D5)
- [x] 3.6 Seed from `LibraryViewModel.init` on `Dispatchers.IO`, behind the existing `busy` flag (design D2)
      — the `init` block sits **after** the state-flow properties: `viewModelScope` dispatches on
      `Main.immediate`, so a coroutine launched above them can run before `_busy` is assigned
- [x] 3.7 Unit-test the seed-once decision and the containment guard, including a path outside the sample directory and a `content://` URI
      — `SampleLibraryTest` (6 tests, containment) and `BundledSampleTest` (7 tests, the real
      packaged asset: it installs, is named for its title, leaves no `.part` behind, does not
      accumulate on a second install, and parses as 5 ascending chapters)

## 4. Removal

- [x] 4.1 Extend `LibraryViewModel.remove()` to delete the sample's copied file, guarded by canonical-path containment in `filesDir/sample/` (design D9)
- [x] 4.2 Confirm by test that a removal whose source is a SAF URI deletes nothing
      — `BundledSampleTest.it owns and deletes only its own copy`

## 5. Manual verification (PRD §25)

**Not started — no device or emulator was available in the session that implemented this** (`adb
devices` empty, no AVD configured). Everything above is verified by build and unit test only; the
seeded book has never been seen on screen. This section is the gate before archiving.

- [ ] 5.1 Install onto a device with no app data: the sample appears in the library on first open, with 5 chapters and the placeholder cover
- [ ] 5.2 Play it — chapter navigation, the scrubber, speed, and the media notification all work; it is the first real chaptered `.m4b` any of these has been exercised against
- [ ] 5.3 Force-stop and reopen: the book is still listed, is not duplicated, and resumes where it was left
- [ ] 5.4 Remove it, reopen the app, and confirm it does not come back
- [ ] 5.5 Confirm the copied file is gone from `filesDir/sample/` after removal
- [ ] 5.6 Add a real book of the owner's and remove it; confirm the source file still exists on disk
