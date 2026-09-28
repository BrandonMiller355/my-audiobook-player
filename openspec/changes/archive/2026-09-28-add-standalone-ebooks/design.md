## Context

A book in this app is a row in `audiobooks` whose audio source is mandatory and whose ebook is two
optional columns (`add-ebook-companion` design D4). The request inverts that for some books: the
ebook is the book, and the audio may come later.

## Decisions

### D1: An ebook alone is an `audiobooks` row with a null audio source

`sourceUri` and `sourceType` become nullable, null together. An ebook alone has `ebookUri` set and no
chapters. At least one of `sourceUri` and `ebookUri` is always set.

Rejected: a separate `ebooks` table. It would need its own reading-position columns, its own library
query unioned into the existing one, and a move of the row's identity when audio arrived — and every
table that cascades from `audiobooks` (notes, corrections, summaries) would have to be re-keyed or
would only become reachable after the move. One row that gains its audio keeps every one of those
relationships trivially.

Rejected: a sentinel `sourceType` with the ebook's URI in `sourceUri`. It avoids the migration but
makes every reader of `sourceUri` wrong in a way the type system cannot see.

SQLite cannot relax `NOT NULL` in place and `minSdk` is 26, so `MIGRATION_9_10` rebuilds the table,
exactly as `MIGRATION_5_6` did, and `Migration9To10Test` asserts all four child tables survive the
drop.

### D2: An ebook is added from the Library's add control, and parsed when picked

A third item in the add menu and a third button in the empty state. The EPUB is parsed before the row
is written, for the reason a link is: refusing a DRM-protected or malformed file at the moment it is
picked, with the reason, beats adding it and finding it unreadable on first open. The parse also
supplies the title; a blank one falls back to the file name.

### D3: Audio is attached to the existing row, through the Library's own import rules

`LibraryDao.attachAudio` sets the source columns and inserts the chapters in one transaction, guarded
on the row still having no audio so a double tap cannot interleave two scans' chapter indices.

The folder-scan and `.m4b`-read rules — including what is refused and when the grant is given back —
move from `LibraryViewModel` into `AudioImporter`, used by both the Library and the Reader. Two copies
is how the two paths would come to disagree about what counts as a book. The title is left as the
ebook's; any cover the `.m4b` carries becomes the book's first.

### D4: The library routes by whether the book has audio

`LibraryBook` carries the ebook URI rather than only a boolean (revising `add-ebook-companion` D16):
an ebook alone's availability is judged by its ebook's grant, and removal gives that grant back. A row
with no audio opens the Reader; every other row opens the Player as before. An ebook alone has no
saved audio position, so it never appears on the resume card.

### D5: The Reader without audio never connects to the playback session

Without audio there is nothing of this book's to play, and the session may be holding another book.
The Reader decides whether to connect once it has read the row, and for an ebook alone does not: no
play control, no bookmark (a mark is anchored in the audio), no read-along map, and nothing that could
pause someone else's book. Unlink is replaced by "Add audiobook"; `LibraryDao.unlinkEbook` also
refuses a row with no audio.

Moving the connection behind the row read means the controller can land after the ebook has parsed,
so the read-along map is also built on connect when it is still missing.

### D6: Once audio is added, the Player replaces the Reader

The Reader never loads a book into the session, and read-along needs the book loaded. Rather than
teach the Reader to load, the Player takes its place (`popUpTo` the Reader), leaving the back stack
every book with audio has — Library, Player — with the Reader one tap away at the same reading
position.
