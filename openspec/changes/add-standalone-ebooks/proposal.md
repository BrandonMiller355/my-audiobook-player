## Why

Some of the owner's ebooks have no audiobook yet, or never will. Today an EPUB can only enter the app
by being linked to an audiobook already in the library, so those books cannot be read here at all —
and an audiobook bought later for a book already being read has to be added from scratch, with the
reading position lost to a fresh link.

This extends PRD §3.1 (the ebook companion's boundary), §20.1, §20.3, and §20.4. It is a later
explicit request of the kind §3 allows for.

## What Changes

- **An ebook can be added on its own** from the Library's add control and from the empty library, as
  a third choice beside a folder and a single `.m4b`. It is parsed when picked, so a protected or
  malformed file is refused with the reason, exactly as a refused link is. It takes its title from
  the EPUB.
- **A book that is an ebook alone opens in the Reader**, straight from the Library. Its row says it
  has no audio yet in place of a chapter count.
- **The Reader leaves playback alone for such a book**: no play control, no bookmark, no read-along,
  and no connection to the playback session at all, so it can neither show nor pause another book
  that happens to be playing.
- **"Add audiobook" in the Reader's menu** for such a book, in place of "Unlink ebook". It offers the
  same two paths the Library does. The audio is added to the *same* book — same row, same title,
  same reading position — and the Player then takes the Reader's place.
- **Unlinking is refused for an ebook alone**, since it would leave a book with nothing to open. It
  leaves the library by being removed, like any other book.
- **Removing a book gives back its ebook's grant too.** Previously only the audio source's was
  released; for an ebook alone the ebook's is the only one it holds.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ebook-linking`: an ebook can exist without an audiobook, and gain one later.
- `audiobook-library`: the add control and empty state offer an ebook; rows for an ebook alone.
- `ebook-reader`: back from a book with no audio returns to the Library.

## Non-goals

- Adding several ebooks to one book, or one ebook to several books.
- Detaching the audio from a book to leave the ebook alone. Unlinking remains ebook-side only.
- Reading a cover image out of the EPUB. An ebook alone shows the placeholder until audio with a
  cover is added.
- Merging two existing library entries — an audiobook and a separately added ebook — into one.
- Any change to read-along, notes, or summaries; they apply once the book has audio, exactly as
  they do for any book.

## Impact

**Schema.** `audiobooks.sourceUri` and `audiobooks.sourceType` become nullable. `MIGRATION_9_10` is a
table rebuild in the shape of `MIGRATION_5_6`; the version 10 export is committed.

**New code.** `ui/library/AudioImport.kt`, the folder and `.m4b` read-and-refuse rules moved out of
`LibraryViewModel` so the Reader can use them too.

**Modified code.** `LibraryDao` (library row carries the ebook URI; `attachAudio`; guarded
`unlinkEbook`), `LibraryViewModel` and `LibraryScreen` (add an ebook, open it in the Reader),
`ReaderViewModel`, `ReaderScreen`, `ReaderChrome` (no-audio mode, add-audio sheet),
`AudiobooksApp` (routes), strings, and the PRD.

**Dependencies, permissions, manifest.** None added or changed.
