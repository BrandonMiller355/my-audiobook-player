## Context

The reader shipped at the manual tier. `add-ebook-companion` design D8 states the reason plainly:
"nothing on this screen depends on where the audio is," and `ReaderViewModel` accordingly listens for
`onIsPlayingChanged` and nothing else. This change reverses that, and it is the only decision from
that change being undone.

Two pieces of machinery already exist and do most of the work:

- **`BookTimeline`** converts between Media3 player coordinates and book coordinates, and
  `absolutePosition()` / `targetForAbsolute()` are exactly the two directions needed on the audio
  side. It was built for the scrubber; nothing about it needs to change.
- **`Block.charOffset` and `Ebook.blockIndexFor`** are the text side. `add-ebook-companion` design D3
  chose `(spineIndex, charOffset)` over a pixel offset specifically so a future alignment map could
  anchor to it — "that is free today and a migration later." This is the change that collects on
  that.

## Spike findings

Measured against *The Hero of Ages* — 90 tagged audio chapters and the matching EPUB, both in
`D:\Claude\Mistborn\BookAndAudiobook\`. These are measurements, not estimates, and several
numbers below replace guesses that were in an earlier draft of this document.

1. **Ordinal matching pairs the book exactly.** All 84 numbered chapters plus the prologue and
   epilogue matched, with no offset required. The leftovers on each side are precisely the material
   that should not match: audio keeps `Part One`–`Part Five` and `End Credits`; the ebook keeps
   `ACKNOWLEDGMENTS`, `PART ONE`–`PART FIVE`, `METALS QUICK REFERENCE CHART`, `NAMES AND TERMS`,
   `ARS ARCANUM`, `SUMMARIES OF PREVIOUS BOOKS`, `BOOK ONE`, and `BOOK TWO`.
2. **The audio carries structural marks too.** `Part One` is a real chapter mark seven seconds long.
   D3's structural filter must run against *both* sides, not just the ebook — an earlier draft
   assumed only the ebook had them.
3. **Characters predict narration time well.** Median 13.4 characters/second, with a p10–p90 spread
   of 14.6% of the median. That is tighter than the 10–20% this design assumed, and it is the
   measurement the whole approach rests on.
4. **Worst realistic mid-chapter drift is about 1.6 minutes**, on the chapters that deviate most
   (chapter 51 at +19.8%, chapter 37 at +12.1%). Typical chapters land well under a minute.
5. **One genuine outlier: the epilogue, at 66.9 characters/second** — its text range carries roughly
   7,000 characters the audio does not narrate, because unmatched back matter falls inside it. This
   is what D13 exists to contain.
6. **The ebook's table of contents is not in narration order.** `METALS QUICK REFERENCE CHART` and
   `NAMES AND TERMS` sit between the last chapter and `EPILOGUE`. Nothing may assume TOC order
   equals audio order.
7. **The fallback path was validated separately.** With every label discarded and matching done
   purely in order, the offset scorer in D3 recovered the correct alignment (`+1`, skipping
   `ACKNOWLEDGMENTS`) with a 73% margin over the next best candidate.

Constraints shaping every decision below:

- **PRD §23** — open quickly, do not load more than needed. A 150,000-word book is roughly 900,000
  characters; whatever is precomputed must be cheap and must happen once.
- **PRD §22** — degrade gracefully. A book without chapter marks, an ebook without a navigation
  document, and an ebook whose chapters do not pair must each produce a stated condition, not a
  broken reader.
- **PRD §28.13** — minimal dependencies. Nothing new is added.
- **Owner's accuracy target** — "roughly the same page." This is a ceiling, not an aspiration, and it
  is what licenses linear interpolation. Every decision below is sized to it.

## Goals / Non-Goals

**Goals:**

- One position, not two. While read-along is on, where you are reading and where the narrator is are
  the same fact expressed in two units.
- Both directions through one mechanism, so they cannot disagree with each other.
- A map shaped so that better data can be poured into it later without changing anything that reads
  it.
- No new dependency, no new permission, no change to the playback layer.

**Non-Goals:**

The proposal's Non-goals section is the authority. The two that constrain the architecture rather
than the feature set:

- **No alignment data is computed from the audio.** Not from ASR, not from silences, not from `stsz`
  frame sizes. Every anchor in this change comes from a chapter mark the owner tagged.
- **No accuracy beyond a page.** Sub-paragraph correctness is not a goal that a later refinement is
  expected to reach incrementally. Reaching it requires forced alignment, which is a different change.

## Decisions

### D1: The whole feature is one piecewise-linear map between two absolute spaces

Rather than per-chapter interpolation logic, everything reduces to a sorted anchor list mapping
**absolute milliseconds in the book** to **absolute characters in the book**:

```
  audio    0ms        1_812_000ms       3_640_000ms          ...
           │              │                  │
  anchors  ●──────────────●──────────────────●───────────────
           │              │                  │
  text     0 chars     48_210 chars      95_004 chars       ...

  forward:  ms  → binary search → lerp within segment → chars → block
  reverse:  chars → binary search → lerp within segment → ms  → seek
```

Both directions are the same binary search and the same linear interpolation, run backward. That is
the reason to define it this way: a bug in one direction is a bug in both, so the two can never drift
into disagreeing about where a given place in the book is.

The audio side of an anchor comes from `BookTimeline.chapterSpans()`, already computed. The text side
is a prefix sum over `Ebook.blocks` — one pass, roughly 900,000 characters summed into an `IntArray`
the size of the block list, done once when the ebook parses.

**Rejected: interpolating within each chapter independently.** It computes the same numbers with more
state, and it makes the reverse direction a separate code path rather than the same one inverted.

**Rejected: interpolating on block index or on scroll distance.** Paragraph lengths in real prose vary
by two orders of magnitude, and narration time tracks characters closely while tracking block counts
not at all:

```
  blocks:  [ ██████████ ][ █ ][ █ ][ ████████████████ ][ █ ]
            400 words     3    2     600 words          4
  50% of blocks ═══════════════▲   but only 3% of the words.
```

On a 30-minute chapter that error is minutes. On characters it is seconds.

### D2: V1 places anchors only at chapter boundaries, and the map does not know that

The anchor list is the interface. This change populates it from chapter pairing, giving one anchor
every 20–40 minutes. A later change could insert anchors validated by the owner's own
scroll-and-seek gestures, or from forced alignment, and **nothing that reads the map changes** — it
is already a list, already sorted, already interpolated piecewise.

This is the fourth cheap-now/expensive-later decision this project has taken, after `Chapter.source`,
the folder/`.m4b` unified timeline, and `(spineIndex, charOffset)` itself. It costs nothing today: a
list of two anchors and a list of forty behave identically to every caller.

### D3: Chapters pair by normalized label, then by order, corrected by a single offset

Audio chapter titles and EPUB navigation labels describe the same chapters in different notation. The
matcher normalizes both — lowercase, strip punctuation and leading zeros, extract an ordinal from
arabic numerals, roman numerals, or spelled-out numbers, and recognize the named sections
(`prologue`, `epilogue`, `prelude`, `interlude`, `appendix`) — then pairs on the extracted ordinal.

```
  "03 - Chapter 3.mp3"  ─┐
  "Chapter III"          ├─ normalize ─▶  ordinal 3  ─▶ pair
  "3"                   ─┘
```

Two filters matter before pairing:

- **Structural entries are not chapters.** A grouped table of contents lists "PART ONE" immediately
  before "1", with no text between them. An entry whose block range is empty or trivially short is a
  header and is skipped for pairing purposes. It stays in the table of contents, which is a separate
  concern.
- **Unpaired entries on either side are not errors.** A copyright page with no audio, or a credits
  track with no text, simply contributes no anchor.

When label matching yields nothing usable, the fallback is pairing in order from the first chapter of
each list. Front matter then produces a **constant** offset, not a scattered one, which is why the
correction is a single "shift by ±n" rather than a pairing table:

```
  audio:  [prologue][ 1 ][ 2 ][ 3 ] ...
  ebook:  [cover][title][©][map][prologue][ 1 ][ 2 ][ 3 ] ...
                                ▲  one control: "+4"  →  whole book fixed
```

**The offset is detected before it is offered.** Rather than making the owner discover and dial in the
number, the app tries every plausible offset and scores each by how consistent the implied reading
rate is across the whole book — the coefficient of variation of characters-per-second over all pairs.
A correct alignment makes long chapters line up with long text; a wrong one scatters the rate:

```
  offset   CV(chars/sec)      correct alignment is not a close call
    -1       2.2685
     0       1.6195
    +1       0.4725   ◀── argmin
    +2       1.7763
    +3       0.8171
```

Those are real figures from spike finding 7, produced with every label discarded so that only the
duration-versus-length signal was available. Chapter durations in this book range from 2 to 44
minutes, and that variance is exactly what makes the score discriminating. The manual control remains,
now as an override of a detected value rather than a blank to fill in.

**Rejected: a per-chapter pairing UI.** Forty rows of tedium to fix a failure mode that is one number.
It can be added if the offset proves insufficient on a real book, and the anchor list would not change
shape.

### D4: The loop is broken on scroll *source*, not on scroll *motion*

This is the decision the feature's correctness rests on. Audio drives the text and the text drives the
audio, so if "the text moved" does not distinguish why, the two rules feed each other:

```
      audio position ─────────► scrolls the text
            ▲                          │
            │                          ▼
      seeks the audio ◄────── "the text moved"
```

`ReaderScreen` currently detects settle by watching `LazyListState.isScrollInProgress` fall to false,
and uses it to save the reading position. **That signal fires for programmatic scrolls too.** Attaching
the seek to it unchanged would make every auto-scroll step report a settle, reverse-map to the current
position, and — because forward mapping lands at a block's start while reverse mapping reads from
wherever the view sits — pull the audio slightly backward, forever.

The fix is a `NestedScrollConnection` on the reader's `LazyColumn`, which reports whether a scroll
originated from user input or from a programmatic call. A user-sourced scroll sets a "dirty" flag;
settle seeks only when that flag is set, and clears it. Programmatic scrolls never set it.

*Verified against the actual library:* Compose UI 1.11.4, from the `2026.06.01` BOM in the catalog,
exposes `NestedScrollSource.UserInput` and `NestedScrollSource.SideEffect`, with the older `Drag` and
`Fling` retained only as deprecated aliases of them. **D4 gates on `UserInput`.**

Note what that covers: a flick reports `UserInput` for the drag and `SideEffect` for the fling that
follows, while a programmatic scroll reports only `SideEffect`. Setting the flag on `UserInput` and
clearing it after settle therefore catches the whole flick — the drag sets it before the fling starts
— while never being set by the reader scrolling itself. That is exactly the discrimination the loop
guard needs.

The same flag fixes the existing position-save hook, which has the same latent misfire and only
escapes it today because nothing scrolls the reader programmatically except a one-shot restore.

### D5: Seeking happens on settle, gated by a dead zone, and is undoable

Chosen by the owner over a deliberate tap. It is the smaller design: with a tap gate you need a
suspended state, an offset readout, and two reconciliation actions; with settle-seek the two positions
are never in disagreement long enough to need a UI for it. There is no "out of sync" state in this
change at all.

```
  audio drives text ──► user drags ──► settles ──► text drives audio
        ▲                                                    │
        └────────────────────────────────────────────────────┘
```

Two guards make it safe to live with:

- **A dead zone.** A settle whose implied jump is under a few seconds does not seek. Ordinary
  adjustment scrolling would otherwise produce a stream of micro-seeks against the player, and a jump
  that small is well inside the map's own error anyway.
- **An undo.** A settle-seek posts a passing message offering to restore the previous playback
  position. The reader already hosts a `SnackbarHost` for rejected picks, so this is a message, not a
  mechanism. It is what makes "glance back at the previous page" recoverable rather than costly.

### D6: The text position is measured to sub-block precision, not to the first visible block

`saveReadingPosition` currently uses `firstVisibleItemIndex`. Quantizing to a block was harmless when
the value only restored a scroll position; now it seeks the audio, and a 400-word paragraph is over
thirty seconds of narration.

`LazyListState.layoutInfo` carries each visible item's size and offset, so the fractional position
within the first visible block is available directly, and converts to a character offset by
proportion. The same information supplies the reverse direction: a target of "63% into block 412"
becomes `scrollToItem(412, offsetPx)`.

### D7: Auto-scroll glides toward a continuously recomputed target, and jumps only when far

`animateScrollToItem` on a tick is the obvious approach and reads badly: the target block does not
change at all during a long paragraph, so the page sits still and then lurches.

Instead the target is recomputed on a modest tick (a few times a second is ample — the text moves
about one character per 65 milliseconds) as a **fractional** position via D6, and the list is moved
toward it. Because the target advances smoothly with the audio, the scroll advances smoothly.

Two regimes, because pixel-accurate targeting needs measured item heights and `layoutInfo` only
measures what is on screen:

| Target | Behavior |
|---|---|
| On or near screen | Glide — scroll by the pixel delta toward the fractional target |
| Far off screen (after a seek, a chapter skip, opening the reader) | Jump — `scrollToItem`, then resume gliding |

Polling position a few times a second on a screen that is already held awake by
`FLAG_KEEP_SCREEN_ON` is not a battery consideration worth designing around.

### D8: A read-along seek never changes the transport state

Seeking while paused repositions and leaves playback paused. This matches PRD §6, whose flow ends with
the user pressing play, and the existing `playback` guarantee that opening a book prepares it without
starting it. The reader's chrome already carries play/pause for the case where starting is what the
owner wants.

### D9: The toggle and the offset are columns on `audiobooks`

Two nullable columns via `MIGRATION_4_5`, additive and non-destructive in the same shape as the four
migrations before it. Per book rather than app-wide because the chapter offset is inherently per book,
and splitting the pair across two storage mechanisms to make one of them global would be worse than
either choice alone.

**Rejected: a `readalong` table.** A one-to-one relationship does not need a join, and the same
argument was already made and settled for the ebook link itself (`add-ebook-companion` D4).

### D10: Unavailability is a stated condition, not a silently inert toggle

Read-along requires chapter marks on the audio side and a navigation document on the ebook side.
Both absences are states the app already recognizes — `ChapterParseResult.Unchaptered` and the
existing "this ebook provides no table of contents" case. Neither degrades anything: the book still
plays, the ebook still reads, and the toggle explains why it is unavailable rather than appearing
functional and doing nothing.

A book with exactly one chapter mark spanning the whole file counts as unchaptered here, for the same
reason `M4bChapterParser` already treats it that way — a single anchor pair defines a straight line
across twelve hours, which is precisely the configuration this change exists because the owner is
eliminating.

### D11: While read-along is on, the saved reading position is the playback position

`ebook-reader` currently guarantees the two are independent, and asserts it in a scenario. Under
read-along that is no longer true and cannot be made true — it is the feature. The scenario is
replaced rather than amended, and the independence guarantee is re-scoped to read-along being off,
where it continues to hold exactly as before.

Practically this means the reader stops writing a reading position of its own while read-along is on;
the playback position already persists through `PlaybackService`, and writing a second copy of the
same fact on every auto-scroll tick would be hundreds of writes for one chapter.

### D12: One rule for every deliberate movement of the reader

A drag, a table-of-contents jump, and a search result all move the reader on purpose, and all settle
the same way, so all three seek. Special-casing any of them would mean two mental models for what
moving the page does.

### D13: A segment whose implied rate is absurd falls back to the book's median rate

Spike finding 5 is the shape of this problem. The epilogue's text range implies 66.9
characters/second against a book median of 13.4 — five times too fast — because unmatched back
matter sits inside the range while the audio narrates none of it. Interpolating linearly across that
segment would race the text through the epilogue and out the other side.

The guard is cheap: compute the book's median rate across all segments, and for any segment implying
a rate wildly out of line with it, advance at the median rate instead of interpolating to the far
anchor. The text then tracks correctly through the narrated part and simply stops short of the
segment's end, which is the right failure — trailing reference material is not being read aloud, so
not scrolling into it is what the owner wants anyway.

**Rejected: excluding unmatched entries' characters from the segment.** It sounds like the more
principled fix and does not work here: the epilogue's excess text is inside its own range rather than
between anchors, so there is nothing to exclude. The rate guard handles both that case and the
between-anchors one without needing to know which it is facing.

## Risks / Trade-offs

**[The feedback loop escapes D4's gate]** → The failure is loud and continuous rather than subtle, so
it will not ship unnoticed. Mitigation is a test that drives the mapper with a synthetic auto-scroll
sequence and asserts no seek is issued, plus device verification against a real book.

**[Drift within a chapter]** → Accepted, and now measured rather than estimated. Across *The Hero of
Ages*, characters-per-second has a median of 13.4 and a p10–p90 spread of 14.6%, which puts worst-case
mid-chapter error around 1.6 minutes of narration and typical error well under a minute — roughly one
screen. Anchors at every chapter boundary reset it to zero every 20–40 minutes. Note the book has a
44-minute chapter, so the long tail is longer than a 30-minute assumption would suggest. If this
proves worse in practice than the arithmetic says, D2's anchor list is where the fix goes, not the
interpolation.

**[Chapter pairing lands off by a constant]** → Wrong pairing is immediately obvious to a user who is
reading, and the offset control fixes a whole book with one number.

**[Search is where settle-seek is most surprising]** → Searching a name to check its spelling, tapping a
result, and having the narration jump there is the least expected consequence of D12. Mitigated by
D5's undo. This is the first thing to re-examine after device testing; if it grates, exempting search
alone is a small change and does not disturb the map.

**[The chapter heading is narrated but is barely any text]** → "Chapter Three" is a few seconds of
audio against thirteen characters, so each chapter opens with a small systematic lead. It is seconds,
inside the accuracy budget, and it resets at the next anchor.

**[The EPUB is a different edition from the audio]** → The map would be wrong everywhere and no
mitigation in this design helps. The owner has confirmed the editions match; if one does not, turning
read-along off for that book is the answer.

**[Reversing `add-ebook-companion` D8]** → The reader now holds a position listener and a polling
tick. Scoped by D7 to a few samples a second on an already-awake screen, and the `MediaController`
connection it needs is already there for play/pause.

## Migration Plan

`MIGRATION_4_5` adds two nullable columns to `audiobooks`. Existing rows keep both null, which reads
as "read-along has never been configured for this book" — the toggle then defaults on for books that
support it and the offset defaults to zero. No backfill, nothing destructive, and the exported schema
gains `app/schemas/5.json`.

Rollback is turning the toggle off, which restores the previous behavior exactly: the reader stops
following, the independence guarantee applies again, and the columns sit unread.

## Open Questions

- **Where the chapter offset control lives** — the reader's chrome row is already six controls wide,
  and this is a rarely touched correction rather than a reading control. The settings sheet is the
  likelier home, but it currently holds only app-wide preferences while this is per book.
- **Whether the dead-zone threshold should be a duration or a proportion of the chapter.** A fixed few
  seconds is the simpler starting point and what this design assumes; a long chapter may want more.
- **Whether search should be exempt from D12**, per the risk above. Deliberately left as written until
  there is device experience to argue from.
