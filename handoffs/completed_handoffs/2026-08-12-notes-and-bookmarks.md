# Handoff: Timestamped notes / bookmarks exploration

**Branch:** `add-transport-controls`
**Date:** 2026-08-12
**Session type:** `/opsx:explore` — exploration only. **No code written, no OpenSpec artifacts created.**

---

## The user's idea (as stated)

First framing:

> "It would be nice to be able to add notes while listening to the book. It would be nice for it to
> flag where in the book the note was added, so that the relevant context would be available to be
> reviewed. This way, when I'm running or something, I can dictate notes that I can later explore
> during my book club discussions."

Second, narrower framing later in the session:

> "I would like to be able to take notes while listening to an audiobook. The notes should be
> visible somewhere, and should be timestamped to a spot in the book, from when they were added
> probably. Maybe also, on the page where they are visible, show which chapter they were in when it
> was added."

The driving use case is **book club preparation**: capture a thought mid-listen (often mid-run),
review and discuss it later with enough context to remember what prompted it.

---

## Read this first: this handoff is different from the other three

| Handoff | Status |
|---|---|
| `handoffs/2026-08-10-ai-chapter-analysis.md` | open questions unanswered |
| `handoffs/2026-08-10-ai-chapter-detection.md` | open questions unanswered |
| `handoffs/2026-08-10-ebook-audio-readalong.md` | open questions unanswered |
| **this doc** | **every open question was answered. This is proposal-ready.** |

The other three explorations stalled on decisions the user had not made. This one did not — all
four forks were put to the user and all four were decided in-session. Do not treat this as another
speculative thread. The remaining work is to write the proposal, not to re-explore.

This feature is also **far smaller and far less speculative** than the other three. It needs no AI,
no alignment, no new dependency, and no new permission.

---

## Reference material (read, don't re-derive)

| What | Where |
|---|---|
| Product requirements | `simple_android_audiobook_player.prd` |
| Active change (this work sits on top of it) | `openspec/changes/add-transport-controls/` |
| **The anchor primitive — read this first** | `app/src/main/java/com/brandonmiller/audiobookplayer/playback/BookTimeline.kt` |
| Existing entities + the position-storage precedent | `app/src/main/java/com/brandonmiller/audiobookplayer/data/Entities.kt` |
| Migration pattern to copy | `app/src/main/java/com/brandonmiller/audiobookplayer/data/Migrations.kt`, `app/schemas/` |
| Where the UI hooks go | `app/src/main/java/com/brandonmiller/audiobookplayer/ui/player/` |

PRD sections that constrain this feature specifically: **§3** (non-goals — *Notes* and *Bookmarks*
are both explicitly listed), **§20** (three screens only; no settings screen required initially),
**§24** (privacy), **§28.2** ("do not add features not listed in this PRD"), **§28.13** (minimal
dependencies).

**Verified state at handoff:** one active change, `add-transport-controls`, **34/50 tasks done**.
Sections 1–8 (all implementation) are complete; sections 9–11 (emulator verification, phone
verification, final checks) are not started. `BookTimeline.kt` exists in the working tree but is
**untracked** — it is not committed yet. Room is at schema version 2 with a real, tested
`Migration(1, 2)`.

---

## The PRD tension, stated plainly

PRD §3 lists both **Notes** and **Bookmarks** as non-goals. However, that section opens with:

> "Do NOT build any of the following unless explicitly requested later"

The user explicitly requested this feature in this session. That is the escape hatch the sentence
exists for, so this is a **PRD amendment, not a PRD violation** — but the amendment must be written
rather than the contradiction left silently in place. The proposal should strike *Notes* and
*Bookmarks* from §3 and add the feature to the functional requirements, noting the two collapse
into one feature (see below).

---

## Decisions reached this session

### 1. Notes and bookmarks are ONE entity, not two

A bookmark is a note whose `text` is null. A note is a bookmark that got written up. This collapses
two PRD non-goals into a single feature with a single table and a single list UI.

```
   [ tap Mark ]───► note(text = null) ────────┐
                          │                   │
                    (later, at leisure)       │  both live in the same list,
                          ▼                   │  both seek on tap
   [ tap Note ]───► note(text = "…") ◄────edit┘
```

**Mark-now-annotate-later was explicitly requested** as a first-class flow, precisely so it doubles
as a bookmarking system. It is not a nice-to-have; it is half the feature.

### 2. Dictation is out of scope — the keyboard already does it

**Decided:** the app writes no speech code and requests no audio permission. If a note is an
ordinary text field, the user's keyboard (Gboard et al.) already provides a mic key, which gives
the "dictate while running" outcome from the original framing for zero cost.

Options considered and rejected:

| Option | Why rejected |
|---|---|
| App-side `SpeechRecognizer` | Inconsistent on-device support; needs `RECORD_AUDIO` |
| Voice memo per note (recorded audio clip) | Needs `RECORD_AUDIO` + file storage; large lift |

`RECORD_AUDIO` would have been the first genuinely invasive permission this app ever requested. The
`app-shell` spec makes a point of permission minimalism, and `add-playback-service` was careful to
justify each of the three permissions it introduced. **This change adds no permission at all** and
should say so explicitly in its proposal.

### 3. The Mark button lives on the Player screen only

**Decided by the user: "Obviously just on the player screen."**

A custom action on the media notification (a Media3 `CommandButton` in the session's custom layout)
was raised and **rejected for this change**. It would put new surface on `PlaybackService` and
compete for space with the existing transport actions. It remains a plausible follow-up if
real-world running use shows the phone-in-pocket case matters, but it is **not in scope**.

### 4. Notes cascade-delete with the book

**Decided by the user: "yeah, nuke em on book deletion."**

`notes` gets a `ForeignKey(onDelete = CASCADE)` to `audiobooks`, exactly like `chapters` does today.

The counter-argument was raised and consciously overruled: notes are the only **user-authored**
content in this app, so cascading means removing and re-adding a book (e.g. after moving files)
silently destroys book club notes. The user accepted this. The **suggested softening** — showing
the note count in the removal confirmation when a book has notes — was proposed but not explicitly
confirmed; treat it as an open implementation detail, not a decision.

PRD §7.1's "removing a book must not delete the user's source audio files" is not violated — notes
are app data, not source files.

---

## The design shape that fell out

### The anchor is stored in raw player coordinates

`AudiobookEntity` already stores playback position as raw `(lastMediaItemIndex, lastPositionMs)`,
with a comment in `Entities.kt` explaining why: they round-trip through
`controller.seekTo(mediaItemIndex, positionMs)` with **no translation for either book shape**, and
for a single-item `.m4b` book the position already *is* the absolute book position.

A note anchor must use the identical pair, for the identical reasons. It then works unchanged when
`.m4b` books arrive.

```
  user taps "note" / "mark"
        │
        ▼
  controller.currentMediaItemIndex, currentPosition
        │
        ▼
  BookTimeline.seekTarget(currentLocation, -LEAD_IN_MS)     ← the lead-in, see below
        │
        ▼
  PlayerTarget(mediaItemIndex, positionMs) ──────► stored verbatim as the note's anchor
        │
        ▼
  BookTimeline.locate(...) ──► Location(chapterIndex, offsetMs)
        │
        ├─► ChapterEntity[chapterIndex].title ──► snapshotted onto the note
        └─► BookTimeline.absolutePosition(...) ──► "4:22:11 into the book"
```

**Almost none of this is new code.** `BookTimeline.seekTarget` already rolls backward across a
chapter boundary when durations are known and clamps at the book's start when they are not.
`PlayerTarget`'s two fields are exactly the two columns the note needs. The anchor computation is
one call.

### The lead-in constant is the single most important design detail

Anchoring at "now" is wrong. The real sequence while running is:

```
  [ interesting passage ]───►[ user registers it ]───►[ phone out of pocket ]───►[ tap ]
       t=0                        t≈+3s                    t≈+8s                t≈+12s
                                                                                   ▲
                                                        anchoring HERE is ~12s too late
```

Anchoring at `now − LEAD_IN_MS` (~15s was the figure discussed) means replaying the note lands
*before* the passage that prompted it, so it plays back in context. Anchoring at `now` means every
single note requires manual scrubbing backward on review.

Since PRD §20 says no settings screen is required initially, **this should be a fixed constant**,
not a user preference. The exact value is a judgment call the implementer should feel free to tune;
~15s was discussed but not fixed as a hard decision.

**Consequence not to miss:** the snapshotted chapter title must be the chapter of the **anchored**
position, not of the position where the user was standing when they tapped. Marking 5 seconds into
a chapter correctly anchors into the tail of the *previous* chapter, and the label must agree.

### Chapter label: snapshot it, do not derive it

Deriving the chapter title from `ChapterEntity[chapterIndex].title` at display time keeps one source
of truth, but `chapterIndex` is only stable while the book's scan is stable. Re-adding a folder book
after renaming a file shifts the indices and every old note silently mislabels itself.

**Snapshot the chapter title onto the note at creation time.** One denormalized string buys a
self-describing notes list that survives rescans. For a personal tool where the note text is the
valuable part, this is the right trade.

### Rough entity shape

Illustrative, not prescriptive — the proposal should settle it:

```
notes
  id             Long, autoGenerate
  audiobookId    Long, FK → audiobooks, onDelete = CASCADE, indexed
  mediaItemIndex Int          ← anchor, raw player coordinates
  positionMs     Long         ← anchor, raw player coordinates
  chapterTitle   String       ← snapshot at creation, of the ANCHORED position
  text           String?      ← null = bare bookmark
  createdAt      Long
```

### Everything else agreed without contention

- A **per-book** notes list (book club = discussing one book), reachable from the Player screen.
- Entries show chapter title + absolute book timestamp + text.
- Tap an entry to seek there.
- Edit and delete a note.
- Room **schema version 3**, additive migration following the established v2 pattern.
- Note markers as ticks on the book-wide scrubber were raised and deliberately left **out of a
  first pass**.

---

## Sequencing

This change should land **after `add-transport-controls` merges**, not alongside it. It depends on:

- `BookTimeline` (currently untracked in the working tree on that branch) for `seekTarget`,
  `locate`, and `absolutePosition`
- the absolute-position math that change introduces for the scrubber
- the v2 schema being settled, so v3 is a clean additive step

`add-transport-controls` still has all 16 of its verification tasks (§9–§11) outstanding. Those
should be worked before this change is proposed, not after.

---

## Suggested next steps

1. **Finish `add-transport-controls` verification** (tasks 9.1–11.3). That is the genuinely useful
   next action in this repo, and it is the prerequisite for this feature.
2. **Write the proposal.** No further exploration is needed. The decisions are made; see above.
3. In the proposal, be explicit about three things that reviewers will look for: the **PRD §3
   amendment**, the fact that **no new permission is added**, and that **notes and bookmarks are
   one entity**.
4. Settle the two remaining implementation-level details: the exact `LEAD_IN_MS` value, and whether
   the book-removal confirmation surfaces the note count.

---

## Suggested skills

- **`/opsx:apply`** — finish `add-transport-controls` (34/50, all remaining tasks are verification).
- **`/opsx:propose`** — write this change up once transport controls is merged. It is ready; the
  exploration phase for this feature is complete.
- **`/opsx:explore`** — only if the notification-action Mark button (decision 3) or scrubber note
  markers come back into scope later.

Project conventions the next agent must respect (in persistent memory; repeated because missing
them causes rework): American English only, never British spellings. OpenSpec CLI is on PATH — call
`openspec <cmd>` directly, never `npx`. Bash-tool writes to project files do not persist in this
sandbox; use PowerShell or Write/Edit. Commits go through the `tscommit` Bash function and carry no
Claude co-authorship attribution. Branches use a plain feature slug, never a `claude/` prefix.
Handoffs live in `handoffs/` in the repo, never the OS temp directory.
