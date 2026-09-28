## 1. Schema

- [x] 1.1 Make `AudiobookEntity.sourceUri` and `sourceType` nullable; document the invariant (design D1).
- [x] 1.2 Write `MIGRATION_9_10` as a table rebuild in the shape of `MIGRATION_5_6`; bump to version 10.
- [x] 1.3 Commit the version 10 schema export.
- [x] 1.4 Write `Migration9To10Test`: every column of an existing book survives, every child table's
      rows survive the drop, a null source can be written, and the table matches the v10 export.

## 2. Data access

- [x] 2.1 `observeLibrary` returns the ebook URI; `LibraryBook.hasEbook` and `hasAudio` derive from it.
- [x] 2.2 Add `LibraryDao.attachAudio`, transactional and guarded on the row having no audio (D3).
- [x] 2.3 Guard `unlinkEbook` so a row with no audio keeps its ebook (D5).
- [x] 2.4 Write `StandaloneEbookQueryTest`: an ebook alone's row figures, attaching audio keeps the
      title and reading position, a second attach is refused, the unlink guard.

## 3. Library

- [x] 3.1 Move the folder and `.m4b` read-and-refuse rules into `AudioImporter` (D3), unchanged.
- [x] 3.2 Add `LibraryViewModel.addEbook`: take the grant, parse, refuse with the reader's messages, insert.
- [x] 3.3 Judge an ebook alone's availability by its ebook's grant; release the ebook's grant on removal.
- [x] 3.4 Add "Add an ebook" to the add menu and "Choose an ebook" to the empty state.
- [x] 3.5 A row with no audio says so and opens the Reader (D4).

## 4. Reader

- [x] 4.1 Connect to the session only for a book with audio; build the map on connect if missing (D5).
- [x] 4.2 Hide play/pause, bookmark, and unlink without audio; back is labeled as going to the Library.
- [x] 4.3 Add "Add audiobook" to the menu, opening a sheet with the folder and `.m4b` pickers.
- [x] 4.4 Attach the picked audio through `AudioImporter` and `attachAudio`; show progress and refusals.
- [x] 4.5 Replace the Reader with the Player once audio is attached (D6).

## 5. Documentation

- [x] 5.1 Update PRD §3.1, §20, §20.1, §20.3, §20.4.

## 6. Manual verification (PRD §25) — needs an EPUB and a folder book or chaptered `.m4b` from the owner

- [ ] 6.1 Add an EPUB from the add menu and from the empty library; it lists with "no audio yet" and
      opens in the Reader. A DRM-protected EPUB and a non-EPUB are refused with a message.
- [ ] 6.2 With another book playing, open the ebook alone: no play control, no bookmark, the other
      book keeps playing and cannot be paused from here. Text selection works.
- [ ] 6.3 Read partway, then "Add audiobook" → folder, and separately → `.m4b`: the Player opens,
      back goes to the Library, the row now shows chapters, and the Player's ebook control reopens
      the Reader at the same place. Read-along follows once playing.
- [ ] 6.4 Pick a folder with no audio from "Add audiobook": refused with a message, the book stays an
      ebook alone.
- [ ] 6.5 Force-stop and reopen: the ebook alone is still listed and opens without re-picking.
- [ ] 6.6 Remove an ebook alone: the EPUB file is untouched.
