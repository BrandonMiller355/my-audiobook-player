## Context

The owner listens on runs and wants to capture a thought without stopping, then review it at book
club. `handoffs/2026-08-12-notes-and-bookmarks.md` explored this and had every open question
answered; the shape below is that handoff's, corrected where the repo has moved since it was
written.

What is already in place, and what this design leans on entirely:

- **`playback/BookTimeline.kt`** maps between Media3 player coordinates and book-chapter
  coordinates for both book shapes. `seekTarget` already rolls backward across a chapter boundary
  when durations are known and clamps at the book's start when they are not; `locate` names the
  chapter a position falls in; `absolutePosition` gives the book-wide figure. The anchor computation
  this feature needs is three calls into code that exists and is tested.
- **`AudiobookEntity` stores playback position as raw `(lastMediaItemIndex, lastPositionMs)`**, with
  a comment explaining why: the pair round-trips through `controller.seekTo(mediaItemIndex,
  positionMs)` with no translation for either book shape, and for a single-item `.m4b` the position
  already *is* the absolute book position. A note's anchor is the same problem and takes the same
  answer.
- **`chapters` cascade-deletes from `audiobooks`**, the precedent for what happens to notes.
- **The Player was redesigned** (`redesign-player-and-library`): a full-bleed cover with two 44dp
  disc controls in its top row, a book-wide scrubber, a transport row sized for running, and a
  footer split into two equal halves that each open `ChaptersSheet` at their own section.
- **Room is at version 6.** Migrations 1→2, 2→3, 3→4, and 4→5 are plain additive `ALTER TABLE`s;
  5→6 is a table rebuild because SQLite before 3.35 cannot drop a column.

Constraints that shape the result: PRD §20 requires no settings screen, so anything tunable is a
source constant; PRD §21 wants controls hittable while walking or running; PRD §28.13 wants minimal
dependencies; and PRD §3 has to be amended rather than quietly contradicted, because it names both
*Bookmarks* and *Notes* as non-goals.

## Goals / Non-Goals

**Goals:**

- Capturing a spot in a book costs exactly one tap and no reading of the screen.
- A captured spot replays *in context* — the passage that prompted it plays back, not the silence
  after it.
- A mark and a note are the same record at different stages of the same flow.
- The list is self-describing months later, and stays correct across a rescan of the book.
- No new permission, no new dependency, no new surface on the playback service.

**Non-Goals:**

- Speech recognition or recorded audio in any form (see D3).
- Any note surface outside the Player and the Notes screen — no scrubber ticks, no notification
  action, no Library note count, no notes on ebook text.
- Cross-book listing, search, tagging, sorting, or export.
- A user-configurable lead-in.
- Editing a note's anchor.

## Decisions

### D1: Notes and bookmarks are one entity, distinguished by a null `text`

One `notes` table. `text` is nullable: null is a bare mark, non-null is a note. Both live in the same
list, both seek on tap, and annotating a mark is an `UPDATE` rather than a conversion between kinds.

The alternative was two tables, or one table with a `kind` column. Both make the mark-now-annotate-
later flow — which is half the feature, not a convenience — into a migration between states, and both
double the list UI for a distinction the owner does not experience as one. The single nullable column
is also what makes the PRD amendment honest: two struck non-goals really do collapse into one
feature, rather than being two features described as one.

The trade is that "has this note been written up yet" is a null check rather than a typed state. At
this size that is a benefit.

### D2: The anchor is raw player coordinates, `(mediaItemIndex, positionMs)`

Stored verbatim as `PlayerTarget`'s two fields, exactly as `AudiobookEntity` stores the saved
position and for the reasons documented there: the pair seeks with no translation for either book
shape, and it survives the arrival of `.m4b` books unchanged because an `.m4b`'s single-item position
is already absolute.

Alternatives rejected: storing an absolute book-wide millisecond figure, which is derived from
chapter durations that a folder book does not know until the Player has resolved them — a note taken
early in a session would be stored against a total that later changes; and storing
`(chapterIndex, offsetMs)`, which has the same instability across rescans that D4 rejects for the
chapter label.

### D3: Dictation is the keyboard's job, and the app requests no microphone permission

A note is an ordinary text field, so Gboard and every other modern keyboard already offer a mic key.
That delivers the "dictate while running" outcome from the original request at zero cost.

Rejected: an app-side `SpeechRecognizer` (inconsistent on-device support, needs `RECORD_AUDIO`) and a
recorded voice memo per note (`RECORD_AUDIO` plus file storage plus playback UI, and a large lift).
`RECORD_AUDIO` would have been the first genuinely invasive permission this app ever requested;
`app-shell` makes a point of permission minimalism and `add-playback-service` justified each of the
three permissions it introduced one at a time. This change adds none.

### D4: The chapter title is snapshotted onto the note, not derived at display time

Deriving `ChapterEntity[chapterIndex].title` at display time keeps one source of truth, but
`chapterIndex` is only stable while the book's scan is stable. Re-adding a folder book after renaming
or adding a file shifts every index, and the notes list would then relabel old notes silently and
plausibly — the worst failure shape, because nothing looks wrong.

One denormalized string buys a list that survives a rescan. For a personal tool where the note text
is the valuable part and the label is context for it, that is the right trade. The known cost: if the
owner later renames a chapter, existing notes keep the old title. That is the correct behavior for a
record of where a thought was had.

### D5: The anchor is `now − 15 seconds`, computed through `BookTimeline.seekTarget`

Anchoring at the moment of the tap is wrong, because the tap is not the moment of interest. The real
sequence is: the passage plays, the owner registers it a few seconds later, gets the phone out, and
taps — perhaps twelve seconds after the thing they wanted. Anchored at "now", every single note needs
manual scrubbing backward on review, which is exactly the friction the feature exists to remove.

`LEAD_IN_MS = 15_000` as a source constant, not a preference: PRD §20 requires no settings screen,
and a control for this would be a setting the owner adjusts once and never again.

The computation is `seekTarget(locate(currentMediaItemIndex, currentPosition), -LEAD_IN_MS)`, which
means the existing clamping rules apply for free — it rolls into the previous chapter's tail when
that chapter's duration is known, clamps at the current chapter's start when it is not, and clamps at
zero at the start of the book. No new arithmetic, and no new edge cases.

**The consequence not to miss:** the snapshotted chapter title must be the chapter of the *anchored*
position, not of the position the user was standing at when they tapped. Marking five seconds into a
chapter correctly anchors into the tail of the previous one, and the label has to agree or the note
will point somewhere its own label denies.

### D6: The Mark control is the Player footer's middle third, and it pauses and opens the note

The footer becomes three equal segments: Chapters | Mark | Speed. Its two existing segments open
`ChaptersSheet` over the Player; the new one pauses the book and goes to the notes screen with the
entry it just created open for writing.

It sits here because the footer is the largest low-on-screen surface the Player has, and marking a
spot should not require aiming. Its sub-label carries the note count, so the segment reads as being
*about* something rather than as a bare verb.

**Pausing is the decision, and it is owner-directed.** An earlier draft of this design had the
control fire and forget — record the spot, leave the audio running, confirm with a snackbar — on the
theory that a mark should cost nothing mid-run. The owner rejected that, and the reason it was wrong
is concrete: writing a note means dictating or typing it, and both fight the narration. Dictation in
particular is unusable over a playing audiobook, because the microphone hears the book. The original
framing was "dictate notes while running", so a flow that makes dictation impossible is not serving
it. Marking is a deliberate stop.

What makes stopping cheap is the lead-in (D5): the anchor is already fifteen seconds behind the tap,
so the passage replays from before the interruption rather than from wherever the user broke off.
The two decisions hold each other up.

Playback is left paused after the note is written or abandoned. Coming back lands on the Player with
its play control under the thumb; audio restarting by itself while the user is still reading what
they typed would be the larger surprise.

Alternatives rejected: a third disc on the cover art, which reuses an existing pattern but puts a
44dp target at the top of the screen; and a slot in the transport row, which is calibrated around
the 104dp play button and has no room that does not come out of a seek control.

### D7: The Notes screen is a navigation destination, not a sheet

`notes/{bookId}`, alongside `player/{bookId}` and `reader/{bookId}` in `Routes`.

A sheet was the cheaper option and was rejected on two counts. Editing note text in a bottom sheet
puts a soft keyboard under a partially expanded sheet on a screen whose whole business is a text
field. And `ChaptersSheet` is already carrying two sections; a third would make one sheet responsible
for chapters, speed, and a full editable list, against the same judgment that made the Reader its own
route rather than a second face of the Player.

This is a fifth screen and PRD §20 has resisted growing, so the proposal amends §20 rather than
letting the app quietly exceed its own specification.

**Reached from the Mark control, and only from it.** A third disc in the Player cover's top row was
built and then removed at the owner's direction: the cover row was getting crowded, and once marking
navigates here (D6) the screen already has a way in.

The cost is real and is accepted: opening the list to read old notes means marking, which leaves a
bare entry to delete. If that grates in use, the cheap fix is a long press on the Mark segment that
opens the list without creating anything — deliberately not built now, because the ordinary path is
marking and this would be a second meaning for one control.

Back from Notes returns to the Player.

### D8: The navigation is the confirmation

Marking writes the note, pauses, and opens the notes screen with that note's text field focused. The
route carries the new note's id, which is what distinguishes "show me the list" from "here is the one
you just took".

There is no separate acknowledgement — no snackbar, no toast — because there is nothing left for one
to say. The screen changed and the audio stopped; a mis-hit on the footer is impossible to miss and
costs one back press, which leaves a bookmark rather than nothing.

**Canceling discards the entry**, because the row was written before the editor opened and the user
never confirmed it. An earlier version kept it as a bare mark, reasoning from D1 that an empty note
*is* a bookmark; the owner reported that as a bug, and it was one — pressing Cancel and being given
the thing you canceled is indefensible whatever the data model says.

A bare bookmark is still reachable, and by the more honest gesture: keeping an empty field stores a
note with no text. So *Keep* means "record this", with or without words, and *Cancel* means "forget
it". Dismissing by back or swipe takes the Cancel path.

This applies only to an entry the visit created. Canceling the editor on an entry that already
existed declines the edit and leaves the entry alone — two different intents, which is why the screen
tracks which one is open.

### D9: `notes` cascade-deletes with its book, and the confirmation says how many

`ForeignKey(onDelete = CASCADE)` to `audiobooks`, exactly as `chapters` has today. Decided by the
owner.

The counter-argument was raised and consciously overruled: notes are the only *user-authored* content
in this app, so cascading means that removing and re-adding a book — after moving files, say —
destroys book club notes that cannot be recovered from the source audio. The softening, settled here:
the removal confirmation states the note count when the book has notes, so the one path that destroys
authored content says what it will destroy before it does it.

PRD §7.1's "removing a book must not delete the user's source audio files" is not in tension with
this: notes are app data, not source files.

### D10: Schema version 7, a plain additive migration

`MIGRATION_6_7` is `CREATE TABLE notes` plus `CREATE INDEX` on `audiobookId`, in the shape of
`MIGRATION_1_2` through `MIGRATION_3_4`. It alters nothing existing, so every current row is
untouched and a library with no notes behaves exactly as it does today.

Specifically *not* the shape of `MIGRATION_5_6`: that one rebuilds `audiobooks` because SQLite before
3.35 has no `DROP COLUMN`, and its doc comment records why that was dangerous next to a cascading
foreign key. Nothing here needs to go near that.

The table Room will generate must be written out by hand in the migration to match exactly — column
order, types, nullability, the foreign key, and the index name — or `MigrationTestHelper`'s validation
fails. That is a mechanical trap, not a design one, but it is where this kind of migration usually
breaks.

### D11: Entity shape

```
notes
  id             Long   autoGenerate
  audiobookId    Long   FK → audiobooks, onDelete = CASCADE, indexed
  mediaItemIndex Int    ← anchor, raw player coordinates (D2)
  positionMs     Long   ← anchor, raw player coordinates (D2)
  chapterTitle   String ← snapshot of the ANCHORED position's chapter (D4, D5)
  text           String? ← null = bare mark (D1)
  createdAt      Long
```

`createdAt` rather than an updated timestamp: the list is ordered by position in the book, not by
recency, and the creation time is the only one with meaning to the owner ("that was the Tuesday run").
Editing text does not change where a note sits in the list.

The list orders by `mediaItemIndex, positionMs` — book order, which is discussion order at book club.
Chronological order would interleave a re-listen with a first pass and read as noise.

### D12: The absolute timestamp is derived, not stored

The "4:22:11 into the book" figure comes from `BookTimeline.absolutePosition(locate(anchor))` at
display time, against the book's current chapter durations.

Not stored, unlike the chapter title, because the two have opposite failure modes. A stored title
survives a rescan that would corrupt it (D4); a stored absolute figure would *freeze* against chapter
durations that legitimately improve — a folder book's durations resolve lazily, so a note taken in the
first minutes of a session would carry a figure computed against a partial total forever. Deriving it
means it is right as soon as the book is fully resolved, and consistent with the scrubber directly
above it on the previous screen.

Consequence: a folder book whose durations have not resolved shows an approximate figure, the same
transient undercount `absolutePosition` documents and the scrubber already lives with. The chapter
title carries the meaning; the timestamp is a locator.

### D13: The Reader can mark too, from its overflow menu

"Bookmark this spot" in the reader's menu does exactly what the Player's Mark segment does: pauses,
records at the same lead-in anchor, and opens the new entry for writing. Owner-requested after the
Player flow was built.

It is the same call into `noteAnchorFor` rather than a reader-specific path, so an entry taken while
reading is indistinguishable from one taken while listening — same table, same anchor coordinates,
same list. **The anchor is the audio position, not the reading position**, which is what makes that
true. For a book that supports read-along the two track each other anyway (`readalong-sync`), so the
audio anchor *is* where the user is reading; for a book that does not, the anchor is still the only
coordinate a note can seek to.

An earlier attempt gave the reader its own gentler behavior — no pause, no navigation, a snackbar and
a bare mark — on the theory that reading should not be interrupted. That was wrong and the owner said
so: one control that means two different things depending on which screen it is pressed from is worse
than one interruption. The menu item is in the first group, with the actions that concern how this
book is being read, rather than below the divider with the two that change which ebook is linked.

## Risks / Trade-offs

- **Cascade deletes irreplaceable content.** Notes are the only user-authored data here, and removing
  a book destroys them permanently. → Owner-decided (D9), softened by stating the count in the
  confirmation. The alternative — orphaned notes belonging to no book — is worse: it needs a home in
  the UI, a re-adoption rule when the book comes back, and it makes removal not actually remove.
- **The footer's middle segment behaves unlike its neighbors.** A user expecting a sheet over the
  Player gets a paused book and a different screen. → The change is loud rather than subtle, which is
  its own feedback, and the count sub-label distinguishes the segment before it is pressed. One back
  press returns, keeping the mark.
- **Marking costs the listener their place in the flow of the book.** This is the deliberate trade in
  D6, and it is real: a mark can no longer be taken without breaking off. → The lead-in means
  resuming replays the passage rather than dropping the user after it. If device use shows marks are
  going untaken because stopping is too expensive, the fix is a second control that captures without
  opening — not a change to this one.
- **A stored chapter title can drift from the book.** Rename a chapter and old notes keep the old
  name. → Correct behavior for a historical record, and the failure is legible where the derived
  version's failure would be invisible (D4).
- **The lead-in can overshoot.** A mark taken immediately after a passage begins anchors into the
  previous chapter, which will read as surprising in the list even though it is right. → The label
  agrees with the anchor (D5), so the entry is self-consistent; and `BookTimeline` clamps rather than
  producing an invalid target.
- **The lead-in is a guess.** Fifteen seconds fits the described flow, but no measurement backs it. →
  A source constant is cheap to change, and the failure is mild in one direction (a little extra
  context) and recoverable in the other (scrub forward).
- **A note taken before durations resolve shows an approximate position.** → D12; the chapter title
  carries the meaning, and the figure corrects itself.
- **The migration's hand-written DDL must match Room's generated schema exactly.** → An automated
  migration test against the exported schemas, as `Migration5To6Test` already does for the risky one.

## Migration Plan

1. Add `NoteEntity`, bump `@Database(version = 7)`, register `MIGRATION_6_7`, and export the version 7
   schema under `app/schemas`.
2. `MIGRATION_6_7` creates the table and its index. No backfill: a library with no notes is exactly
   what every existing install has, and it is the state the empty Notes screen already renders.
3. `fallbackToDestructiveMigration` stays absent, as `add-folder-audiobooks` design D7 established.

Rollback is a downgrade concern only. An install that has run this migration and is then given an
older APK will fail to open the database rather than lose data; that is the same position every prior
migration leaves, and it is acceptable for a personally sideloaded app.

## Open Questions

None. The exploration's four forks were decided by the owner in-session, its two deferred details are
settled above (D5's constant, D9's count), and the two placement questions the Player redesign
reopened were decided by the owner in the session that produced this change (D6, D7).
