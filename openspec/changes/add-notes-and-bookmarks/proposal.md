## Why

The owner listens while running and wants to capture a thought at the moment it lands — a passage
worth raising at book club — then find it again later with enough context to remember what prompted
it. Today there is nowhere to put that thought, so it is either lost or it interrupts the run.

The exploration behind this is `handoffs/2026-08-12-notes-and-bookmarks.md`. Unlike the other
handoffs in that directory, every open question in it was put to the owner and answered in-session;
what remained was writing it up. The two details it left to the implementer are settled here (the
lead-in constant, and the note count in the removal confirmation), as is UI placement, which the
handoff could not have settled because `redesign-player-and-library` reshaped the Player after it
was written.

**This is a PRD amendment, not a PRD feature.** PRD §3 lists both *Bookmarks* and *Notes* as
non-goals, and §28.2 says not to add features the PRD does not list. That section opens with "Do NOT
build any of the following unless explicitly requested later" — the owner explicitly requested this,
which is the exception the sentence exists for. The amendment gets written rather than left implied:
§3 loses those two bullets and gains a **§3.2** carve-out in the same shape as the existing §3.1
ebook-companion one, stating what is in and what stays out.

**Notes and bookmarks are one entity, not two.** A bookmark is a note whose text is null; a note is a
bookmark that got written up. Marking now and annotating later was requested as a first-class flow
precisely so it doubles as bookmarking. That collapses two of the struck non-goals into a single
table, a single list, and a single set of requirements.

## What Changes

- **A one-tap Mark control on the Player** pauses the book, records the current spot, and opens the
  new entry for writing. The Player footer becomes three equal segments — Chapters | Mark | Speed.
- **A mark anchors 15 seconds behind the tap**, not at it. Registering an interesting passage,
  getting the phone out, and tapping takes about that long, so anchoring at "now" puts every note
  after the thing that caused it — and it is what makes stopping to write cheap, because resuming
  replays from before the interruption.
- **A note is a mark with text.** Marking goes straight to the text field, and backing out of it
  leaves a bare mark, which is a bookmark. Any mark can be written up later from the Notes screen.
  There is no separate "add note" path and no separate kind of record.
- **A new Notes screen, per book**, reached by marking. Each entry shows the chapter it was taken in,
  its position in the book, and its text.
- **Tapping an entry seeks the player there** and returns to the Player.
- **Entries can be edited and deleted.**
- **The chapter name is snapshotted onto the note** when it is created, rather than derived at
  display time. Chapter indices only hold while a book's scan holds; re-adding a folder book after
  renaming a file shifts them, and derived labels would then quietly misattribute every old note.
- **Notes are removed with their book**, and the removal confirmation says how many will go.
- **Dictation comes from the keyboard, not from the app.** A note is an ordinary text field, so
  Gboard's mic key already delivers the dictate-while-running outcome from the original request.

### Non-goals

Deliberately excluded, and named so a later reader knows they were considered:

- **Any speech recognition or audio recording in the app.** No `SpeechRecognizer`, no voice memo
  attached to a note. Both were considered and rejected: each needs `RECORD_AUDIO`, which would be
  the first genuinely invasive permission this app has ever asked for.
- **A Mark action on the media notification.** It would put new surface on `PlaybackService` and
  compete with the transport actions already in the session's custom layout. Plausible follow-up if
  phone-in-pocket use proves it matters; not this change.
- **Note markers as ticks on the book-wide scrubber.** Raised and deliberately left out of a first
  pass.
- **A configurable lead-in.** PRD §20 requires no settings screen, and this change does not add one.
  The constant is tunable in source, not in the app.
- **Notes across books, search, tags, sort orders, or export.** Book club is one book at a time; the
  list is chronological within one book and that is the whole of it.
- **Notes anywhere but the Player and the Notes screen.** The Library row does not show a note count,
  and the Reader does not take notes on the ebook text.
- **Editing a note's anchor.** The position a note points at is the position it was taken at, moved
  back by the fixed lead-in. Only its text is editable.

### Dependencies

**No new third-party dependency is added.** Everything this needs is already present: Room for the
table, `BookTimeline` for the anchor arithmetic, Compose for the screen, and `ui/Icons.kt` for the
two glyphs it draws by hand — the codebase draws its own icons rather than pull
`material-icons-extended`, and that holds here.

### Permissions and manifest

**No new permission, no new foreground-service type, no manifest change.** This is worth stating
plainly because the obvious reading of "dictate notes while running" is a microphone, and it is not
one. Notes are text the keyboard produces; the app never touches the microphone. The existing
`verify<Variant>Permissions` Gradle task keeps failing the build if a dependency contributes
`INTERNET` or the storage permissions, and nothing here goes near it. PRD §24 is untouched: notes are
local rows in the app's own database and never leave the device.

## Capabilities

### New Capabilities

- `book-notes`: Capturing a mark or a note against a book — the one-tap control, the lead-in anchor,
  the chapter-title snapshot, what a note stores, and what happens to notes when their book is
  removed.
- `notes-review`: The Notes screen — listing a book's marks and notes, seeking to one, annotating a
  bare mark, editing text, deleting, and the empty state.

### Modified Capabilities

- `app-shell`: Adds the Notes destination to the app's navigation and states its back behavior —
  back from Notes returns to the Player, never skipping it, for the reason the Reader is its own
  destination rather than a face of the Player.
- `audiobook-library`: Adds a requirement that the removal confirmation states how many notes the
  book has, when it has any, and that removal takes them with it.

## Impact

**Schema** — Room goes from version 6 to 7. `MIGRATION_6_7` creates the `notes` table and its
`audiobookId` index. Additive and non-destructive, in the shape of `MIGRATION_1_2` through
`MIGRATION_3_4` rather than the table rebuild `MIGRATION_5_6` needed: nothing existing is altered, so
every current row is untouched and a library with no notes behaves exactly as it does today. The
exported schema under `app/schemas` gains a version 7 file.

**New code** — `ui/notes/` holding the Notes screen and its ViewModel. The note entity and its DAO
methods join the existing `data/` files rather than start new ones.

**Touched code** — `data/Entities.kt` and `data/Migrations.kt` for the table; `data/LibraryDao.kt`
for creating, observing, updating, and deleting notes, and for the count the removal confirmation
needs; `ui/AudiobooksApp.kt` for the route; `ui/player/PlayerScreen.kt` for the third footer segment
and the cover control; `ui/player/PlayerViewModel.kt` for computing the anchor and writing the note;
`ui/library/LibraryScreen.kt` for the note count in the confirmation; `ui/Icons.kt` for the two new
glyphs; `res/values/strings.xml` throughout.

**Untouched** — everything under `playback/` except as a reader. `BookTimeline` gains no method:
`seekTarget`, `locate`, and `absolutePosition` already do the whole anchor computation, which is the
main reason this change is small. No change to `PlaybackService`, the media session, the
notification, or Bluetooth handling.

**PRD** — §3 loses the *Bookmarks* and *Notes* bullets and gains §3.2; §20 grows a fifth screen and
§20.2 gains two Player controls. Recorded above.
