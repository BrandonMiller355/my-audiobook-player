## Why

Read-along is exact at every chapter boundary and interpolated between them (`readalong-sync`,
design D1). The interpolation assumes the narrator covers text at a constant rate within a chapter,
and real chapters break that assumption: a skipped epigraph, a long musical interlude, a passage the
narrator takes slowly. The text then creeps ahead of or behind the narration as the chapter runs on,
and the reader has no way to say so. The correspondence is right at both ends of the chapter and
wrong in the middle, which is the one shape the existing controls cannot express.

This change gives the reader a way to correct that, in place, while listening.

**This exercises a PRD escape hatch and requires a PRD amendment.** §3.1 lists
`user-tapped "sync here" anchors` as out of scope "not to be built without a further explicit
request." That request has now been made. §3.1 is also already stale: it forbids "scrolling the page
as the narrator advances" in the same sentence, which `add-readalong-scroll` built and §7.3 and §20.4
now describe. §3.2 sets the precedent for how this is handled — a non-goal overtaken by an explicit
request is struck from the list "rather than left standing in contradiction to the feature that
exists." §3.1 gets the same treatment here.

## What Changes

- A **sync correction** control in the reader, shown only for a book where read-along is active. Two
  chevron steppers move the text a fixed step earlier or later against the narration, and the page
  glides to the corrected position while the audio keeps playing.
- The correction is stored as a **user-declared anchor** — the pair "at this audio position the
  narrator is at this character" — not as a scalar offset. Stored anchors are injected into the
  book's `ReadAlongMap` alongside the chapter-boundary anchors it already interpolates over.
- The correction applies to **one audio chapter**. Both chapter boundaries stay exact, and
  neighboring chapters are unaffected.
- A second correction in the same chapter **replaces** the first rather than accumulating.
- Corrections are **cleared when the ebook is relinked or unlinked**, since they index character
  positions in a book that is no longer there.
- A user-declared anchor is **exempt from the rate guard** (`ReadAlongMap.isGuarded`). The guard
  exists to catch bad automatic chapter matching; a correction the owner entered by hand is not that,
  and splitting one chapter into two short segments can otherwise trip it and discard the correction.
- New Room table and a schema migration to version 8.
- §3.1 of the PRD is amended, and §20.4's list of reader controls gains this one.

## Capabilities

### New Capabilities

None. This extends an existing capability rather than introducing one.

### Modified Capabilities

- `readalong-sync`: gains a requirement for an owner-entered, per-chapter correction to the
  audio-to-text correspondence — how it is expressed, what it applies to, how it is superseded, and
  when it is discarded. The existing requirement "A stored correction overrides the detected offset"
  is untouched: that one is a whole-book *chapter-index* offset, a different quantity fixing a
  different failure, and the two coexist.

## Impact

**Data**

- New table for per-`(audiobookId, chapterIndex)` corrections, with `ON DELETE CASCADE` from
  `audiobooks` so removing a book takes its corrections with it.
- `AudiobookDatabase` version 7 → 8, one additive migration, one exported schema.
- `LibraryDao`: read corrections for a book, upsert one, clear a book's corrections.

**Playback**

- `ReadAlongAnchor` gains a marker distinguishing an owner-entered anchor from a chapter boundary, so
  the rate guard can skip the former.
- `ReadAlongMap` otherwise unchanged — it already interpolates over an arbitrary sorted anchor list,
  which is what its own doc comment anticipated ("potentially something finer in a later one without
  anything below needing to change").

**UI**

- `ReaderViewModel.buildReadAlongMap` merges stored corrections into the anchor list; a new action
  applies a nudge and persists it. `changeEbook` and `unlinkEbook` clear corrections.
- `ReaderChrome` gains the control. The reader's menu is words rather than icons, so this needs a
  string but no new hand-drawn glyph; the stepper itself is the existing private `Stepper`.

**Dependencies, permissions, manifest**

- No new third-party dependency.
- No new Android permission, no foreground-service type change, no manifest change.

## Non-goals

- **Forced alignment, in any form.** PRD §3.1 rules out on-device or bundled alignment models and
  any PC preprocessing step, and that boundary is untouched. This change carries exactly the
  information the owner types in with two buttons.
- **Sentence or word highlighting.** Still out of scope per §3.1.
- **Correcting the whole-book chapter offset.** `readAlongChapterOffset` is stored, plumbed through
  `matchChapters(manualOffset =)`, specced, and has no UI. It fixes a different failure — the text
  being wrong from a chapter's first line — and giving it a control is a separate change. This one
  deliberately does not detect that case or offer to fix it.
- **A search-and-declare flow.** Pausing, searching for the narrated phrase, and confirming an anchor
  was considered and dropped: within-chapter drift is bounded by both chapter boundaries being exact,
  so the distances involved are small enough for a stepper to cover.
- **Carrying a correction into the next chapter.** A correction is held across the chapter it was
  made in and unwound before the boundary, so both boundaries stay exact. Systemic drift that spans
  chapters is corrected per chapter, not once for the book.
- **Corrections on the Player.** The reader is the only place the drift is visible.
