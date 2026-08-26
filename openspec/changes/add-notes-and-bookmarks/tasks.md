## 1. Schema

- [x] 1.1 Add `NoteEntity` to `data/Entities.kt` with the D11 shape: `id`, `audiobookId`,
      `mediaItemIndex`, `positionMs`, `chapterTitle`, nullable `text`, `createdAt`; a
      `ForeignKey(onDelete = CASCADE)` to `audiobooks` and an `Index("audiobookId")`, matching how
      `ChapterEntity` already declares both
- [x] 1.2 Register the entity and bump `@Database(version = 7)` in `data/AudiobookDatabase.kt`
- [x] 1.3 Write `MIGRATION_6_7` in `data/Migrations.kt` — `CREATE TABLE notes` plus its index, and
      nothing else. Document it in the shape of `MIGRATION_1_2` through `MIGRATION_3_4` (additive, no
      backfill), explicitly *not* the table rebuild `MIGRATION_5_6` needed
- [x] 1.4 Build once so Room exports `app/schemas/…/7.json`, then diff the generated DDL against the
      hand-written migration — column order, types, nullability, the foreign key, and the index name
      must match exactly (design D10's mechanical trap)
- [x] 1.5 Add a migration test against the exported 6 and 7 schemas, in the shape of the existing
      migration tests: a v6 database with a book and chapters migrates to v7, the book and chapters
      are still there, and the `notes` table exists and is empty

## 2. Data access

- [x] 2.1 Add DAO methods to `data/LibraryDao.kt`: insert a note, observe one book's notes ordered by
      `mediaItemIndex, positionMs` (design D11), update a note's text, delete a note by id
- [x] 2.2 Add the note count the removal confirmation needs — per book, observed alongside the rest of
      the confirmation's data rather than fetched at press time
- [x] 2.3 Test the cascade directly: insert a book with notes, delete the book, assert the notes are
      gone and another book's notes are not. `chapters` has the same relationship, so this is
      confirming the declaration is right, not that Room works
- [x] 2.4 Test the ordering with entries inserted out of book order, including two in the same media
      item

## 3. The anchor

- [x] 3.1 Add `LEAD_IN_MS = 15_000` as a constant with the comment design D5 argues for — why behind
      the tap, and why fixed rather than a preference
- [x] 3.2 Compute the anchor in `PlayerViewModel` as
      `seekTarget(locate(currentMediaItemIndex, currentPosition), -LEAD_IN_MS)`, storing the resulting
      `PlayerTarget`'s two fields verbatim (design D2). No new arithmetic and no new method on
      `BookTimeline`
- [x] 3.3 Snapshot the chapter title of the **anchored** position — `locate` the anchor, not the tap —
      and store it on the note (design D4, D5). This is the wiring bug this feature is most likely to
      ship with, because a passing test of the anchor math says nothing about which position was
      passed to `locate`
- [x] 3.4 Test the anchor across the four cases the spec names: mid-chapter, near a chapter start with
      the previous chapter's length known (rolls back, and the *recorded chapter is the previous
      one*), near a chapter start with it unknown (clamps at the current chapter), and near the start
      of the book (clamps at zero)
- [x] 3.5 Test the anchor for an `.m4b` book, where every chapter shares media item 0

## 4. Marking from the Player

- [x] 4.1 Split `PlayerFooter` into three equal segments — Chapters | Mark | Speed — keeping the
      hairlines, the `IntrinsicSize.Min` height, and the disabled alpha the two existing halves use
- [x] 4.2 Wire the middle segment to mark rather than open a sheet, with the note count as its
      sub-label (design D6). Comment the departure at the call site; a later reader will otherwise
      read it as an oversight
- [x] 4.3 **Revised by the owner after first implementation.** Marking pauses the book and goes
      straight to the new note's text field on the notes screen; the navigation is the confirmation
      (design D8). The snackbar this task originally specified is gone, along with its three strings
- [x] 4.4 Keep the segment inert until the session is connected, matching the rest of the footer
- [x] 4.5 Verify marking pauses but never repositions — no `seekTo`, and no `play`, on either path.
      Confirmed by inspection of `PlayerViewModel.mark()`: it reads the controller's position, calls
      `pause()`, and writes a row. Device check 9.3 is the other half

## 5. The Notes screen

- [x] 5.1 Add the `notes/{bookId}` route to `Routes` and `AudiobooksApp`, alongside `player` and
      `reader`, with back returning to the Player (design D7). Carries an optional `noteId`, so the
      snackbar's offer opens the note it just took rather than only the list
- [x] 5.2 **Removed by the owner after first implementation.** The cover disc was built, then cut —
      the cover row was crowding, and the Mark segment already navigates here (design D6, D7). The
      cover is back to Back on the left and the ebook control on the right, as before this change
- [x] 5.3 No new glyph in `ui/Icons.kt` in the end. The footer's mark segment is text like the two
      segments beside it, the notes screen's row menu reuses `OverflowIcon`, and the `BookmarkIcon`
      drawn for the cover disc was deleted with it in 5.2 rather than left as dead code
- [x] 5.4 Build `ui/notes/NotesViewModel.kt`: observe the book's notes, derive each entry's absolute
      position with `BookTimeline.absolutePosition(locate(anchor))` at display time rather than
      storing it (design D12), and expose seek, edit, and delete
- [x] 5.5 Build `ui/notes/NotesScreen.kt`: rows showing recorded chapter, absolute timestamp, and text;
      a mark with no text reading as awaiting text rather than as a blank row
- [x] 5.6 Selecting a row seeks to the anchor and returns to the Player, without starting or stopping
      playback. The seek is synchronous throughout: the pop clears the ViewModel in the same gesture,
      so a coroutine would be canceled before it ran
- [x] 5.7 Text entry for adding and editing, reachable both from a row and from the snackbar action in
      4.3, with abandoning an edit keeping the previous text
- [x] 5.8 Delete an entry, confirmed. Clearing all the text leaves a mark rather than deleting the
      record (spec: a mark and a note are the same record)
- [x] 5.9 An empty state saying there are none and how to take one
- [x] 5.10 Long-press tooltips on every icon-only control on this screen

## 6. The removal confirmation

- [x] 6.1 Surface the note count in the removal sheet in `ui/library/LibraryScreen.kt` when the book
      has notes, and say nothing about notes when it has none (design D9)
- [x] 6.2 Add the strings, with a plural resource rather than a hand-built "1 notes"

## 7. Strings and appearance

- [x] 7.1 Every user-visible string in `res/values/strings.xml` — no literals in composables
- [x] 7.2 American English throughout, in strings, comments, and the documents in this change
- [ ] 7.3 Check the Notes screen in both light and dark themes, against the same palette the rest of
      the app uses

## 8. Specs and documentation

- [x] 8.1 Amend PRD §3: strike the *Bookmarks* and *Notes* bullets, and add a **§3.2** carve-out in the
      shape of the existing §3.1 — what is in (one entity, per book, anchored, reviewable) and what
      stays out (speech recognition and recorded audio, notification action, scrubber ticks, search,
      cross-book listing, export, configurable lead-in)
- [x] 8.2 Amend PRD §20: add §20.5 for the Notes screen, and add the mark control and the notes control
      to §20.2's list of what the Player contains
- [x] 8.3 Run `openspec validate add-notes-and-bookmarks --strict` and fix anything it reports
- [x] 8.4 Confirm no new permission appeared in the merged manifest, and that the existing
      `verify<Variant>Permissions` task still passes. Merged manifest still carries exactly the four
      permissions it did before, and `verifyDebugPermissions` passes

## 9. Verify on the device

The cases that fail *quietly* are 9.2 and 9.5. An anchor that is off by the lead-in still produces a
note that seeks somewhere plausible, and a chapter title taken from the tap position rather than the
anchor is wrong only on the entries that cross a boundary — neither looks like a bug in casual use,
and both make the feature slightly useless in exactly the situation it was built for.

- [ ] 9.1 Mark from the Player on a folder book and on a chaptered `.m4b`, and confirm both entries
      appear on the Notes screen in book order
- [ ] 9.2 Mark at a known moment — note what is being said — then play the entry back and confirm the
      passage that prompted it plays, rather than the silence after it
- [ ] 9.3 Confirm marking mid-playback pauses the audio without moving it, lands on the new note's
      text field, and that backing out of that field leaves a bare mark rather than nothing
- [ ] 9.4 Dictate a note with the keyboard's mic key and confirm the app itself never asks for the
      microphone
- [ ] 9.5 Mark a few seconds into a chapter and confirm the entry names the *previous* chapter and
      seeks into its tail — the D5 consequence, and the one case where "the label looks wrong" is
      correct behavior
- [ ] 9.6 Mark within the first seconds of the book and confirm it anchors at the start rather than
      failing or seeking oddly
- [ ] 9.7 Edit an entry, clear an entry's text, delete an entry, and confirm each survives killing and
      reopening the app
- [ ] 9.8 Remove a book that has notes, confirm the sheet states the count, then re-add the book and
      confirm it comes back with no notes — the cascade the owner accepted, seen once in practice
- [ ] 9.9 Confirm back from the Notes screen lands on the Player, not the Library, and that a visit
      that selects nothing leaves playback where the mark paused it
- [x] 9.10 Install over an existing build carrying real data and confirm the v6 → v7 migration runs
      with the library, chapters, progress, and linked ebooks all intact.
      **Verified on `emulator-5554` (API 36).** A debug APK built from `master` (schema 6) was
      installed, opened, and allowed to seed the bundled sample; the row was then given a saved
      position, a per-book speed, an ebook link with a reading position, and a read-along offset.
      Installing this branch's APK over it: `user_version` 6 → 7, the `notes` table and
      `index_notes_audiobookId` both created, `notes` empty, and every preserved value identical
      afterwards — title, `lastPositionMs` 987654, speed 1.25, `ebookUri`, `ebookSpineIndex` 7,
      `readAlongChapterOffset` -1, and all five chapters with their exact boundaries. The app stayed
      up with no `AndroidRuntime` or Room schema-validation entries in logcat, which is the real
      check on design D10's hand-written DDL matching what Room generates
