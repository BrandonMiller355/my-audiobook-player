## Context

`ReadAlongMap` interpolates linearly between anchors, and today every anchor is a chapter boundary
produced by `matchChapters`. That makes the correspondence exact at both ends of a chapter and
approximate in between, under the assumption that the narrator covers text at a constant rate. Real
chapters break the assumption in ways that are invisible to the matcher: a skipped epigraph, an
interlude, a slow passage.

Two things in the existing code make this change small. `ReadAlongAnchor`'s own doc comment
anticipated it — "a chapter boundary in this change, and potentially something finer in a later one
without anything below needing to change." And `textPositionAtAnchor()` already reads sub-block
precision out of `layoutInfo`, which is the measurement a correction needs.

One thing in the existing code makes it hazardous: the reader's settle handler seeks the audio to
match the text. Any correction mechanism that works by scrolling the page risks moving the very audio
position it is calibrating against.

## Goals / Non-Goals

**Goals:**

- Let the owner correct within-chapter drift while listening, without pausing or leaving the page.
- Keep both chapter boundaries exact — they are known-good and must not be disturbed by a correction.
- Confine a correction to the chapter it was made in.
- Add no new dependency, permission, or manifest entry.

**Non-Goals:**

- Forced alignment or any automatic refinement of the correspondence. See proposal.
- A UI for `readAlongChapterOffset`, the whole-book chapter-index correction. Different quantity,
  different failure, separate change.
- Carrying a correction beyond the chapter it was made in. It is held across that chapter and
  unwound before the boundary (D2), never past it.

## Decisions

### D1: Store the anchor pair, not a scalar offset

A correction is stored as `(audioMs, absoluteChars)` — "at this audio position the narrator is at
this character" — and injected into the anchor list `ReadAlongMap` already interpolates over.

A scalar offset shifts the whole chapter uniformly, which moves the chapter boundary. The boundary is
the one thing known to be exact, so a representation that cannot help but disturb it is the wrong
representation. Inserting a point instead splits the chapter's segment in two and re-slopes both
halves around the correction:

```
chars
  ^                                      ● ch.6 start (exact, untouched)
  |                                ,-'
  |                          ,-'
  |                    ,-'
  |              ★ owner-declared anchor
  |          ,-'   ⋰
  |      ,-'    ⋰ ⋅⋅⋅⋅⋅⋅⋅⋅⋅⋅⋅⋅⋅⋅⋅⋅⋅⋅   ← original straight segment
  |   ,-'   ⋰
  ● ch.5 start (exact, untouched)
  +--------------------------------------> ms
```

The pair also survives a rebuild of the underlying map in a way a delta does not. A delta is
relative to whatever the map said when it was taken; the pair remains a true statement about the
book regardless of how the chapter matching later changes.

**Alternative considered:** a `correctionMs` column on `audiobooks`, per book. Rejected — the
correction is per chapter, and a per-book scalar cannot express that at all.

### D2: An anchor pair — hold the correction, unwind it before the boundary

**Superseded once, on evidence.** This shipped first as a single anchor (option A), which re-sloped
the segment after the correction to meet the next chapter boundary and so let the correction shrink
back toward zero as that boundary approached. The alternative was an anchor pair holding it flat with
a reconciliation window at the end. Neither was obviously right without measuring a real book, so the
cheap one shipped and the measurement was scheduled (task 6.5).

The measurement came back against it. The owner listened and reported the drift returning as the
chapter ran on — the constant-offset case the table below predicts — so the pair is what ships now:

```
  A. one anchor                    B. anchor pair (now)
       ★                             ★──────────────★'
      / ⋱                           /                \
     /    ⋱                        /                  \
    ●        ●                    ●                    ●
```

Each correction contributes two anchors: the correction point, and the point it is held until. The
text advances at exactly the chapter's own rate between them, so the correction is carried whole
rather than bleeding away.

**The window is three times the correction's own size.** The unwinding segment runs at
`R · (1 − δ/W)`, so the multiple sets how abrupt it is: three gives `2R/3` pushing later and `4R/3`
pulling earlier, both comfortably inside [RATE_GUARD_RATIO] rather than sitting on it. Two is the
minimum that stays in-ratio and lands exactly on the guard, leaving nothing for rounding.

**There is always room for the window**, and not by luck: `expressibleCorrectionRange` (D8) already
bounds a correction to at most half the time left in the chapter, derived from the same rate limit.
So a correction that was accepted can always be unwound in-ratio. Where the window would not fit
anyway it is clipped to the room available, which degenerates to the single-anchor shape — still
in-ratio, for the same reason. The two decisions were derived independently and turn out to be the
same inequality.

On the owner's book, the same 80-second correction 24.9 minutes from the chapter's end:

| listened since the correction | A (was) | B (now) |
|---|---|---|
| 1 min | 76.8 s | 80.0 s |
| 5 min | 63.9 s | 80.0 s |
| 10 min | 47.9 s | 80.0 s |
| 20 min | 15.8 s | 80.0 s |
| 24.9 min (chapter end) | 0 s | 0 s |

Held whole for 20.9 minutes, then unwound over the last 4.

**Measured (task 6.5).** The taper is linear and closed-form: for a correction of size `δ` made at
`Tc` in a chapter ending at `T1`, option A left `δ · (T1 − t) / (T1 − Tc)` at time `t` — an erosion
of **3.2 seconds per minute** on the owner's copy of *The Hero of Ages*. The open question was
whether the book's real drift is a constant offset (in which case that table is error being
reintroduced) or proportional (in which case the taper was already right). The owner listened and
reported it worsening toward the end of the chapter, which is the constant case. That is what
retired option A.

**Note on why the obvious third option is impossible:** carrying the delta flat to the chapter end
and letting the next chapter's anchor snap it back does not work. `ReadAlongMap` requires
`absoluteChars` non-decreasing alongside `absoluteMs`; snapping a positive delta back at a boundary
decreases chars against flat ms and violates that invariant. Reconciliation must happen *before* the
boundary or not at all.

### D3: The nudge changes the map, and the glide carries the page

A nudge must not scroll the page directly. The glide loop runs every frame chasing
`currentTextPosition()`, so a direct scroll is undone within one frame. The nudge replaces the map;
the glide then carries the page to the corrected position on its own, which also makes the correction
visible as a movement rather than a jump.

This has a second benefit worth stating explicitly, because it is what makes the cheap design safe.
The settle handler's loop guard, `userScrolled`, is set only on `NestedScrollSource.UserInput`. The
glide's scrolling reports `SideEffect`. So a nudge cannot trip the settle-seek, and no explicit "sync
mode" is needed to suppress it — the existing guard already covers the case.

### D4: Two buttons framed as actions, not as a diagnosis

The controls read as *move the text earlier / later*, with chevrons, not as "text is ahead / text is
behind." A reader who has drifted has to think about which side is wrong to answer the second
framing, and that judgment is routinely made backward. The action framing is directly manipulable:
tap, watch, judge, tap again.

This is the existing private `Stepper` in `ReaderChrome.kt` — label, current value, two chevron
`ChromeButton`s — already used four times for text size, line spacing, and brightness. The value
slot shows the accumulated correction (`-20s`, `+10s`), which is also the undo affordance: the owner
can see they have gone too far.

### D5: The step is ten seconds

The app's existing fine seek grain (PRD §7.3). Reusing it means the correction is expressed in a unit
the owner already has a feel for from the transport controls. Ten seconds of narration is roughly one
to two lines of text, which is perceptible without being coarse.

### D6: The anchor's time side is frozen at the first tap of a burst

Audio keeps playing while the owner taps, so "now" moves under them. Freezing `T₀` at the first tap
of a burst and accumulating only on the character side keeps the correction stable across a
multi-tap adjustment:

```
  anchor = (T₀, effectiveMap.charsForMs(T₀ + accumulatedDeltaMs))
```

`effectiveMap` is the map *including* any correction already stored for this chapter, so a later
adjustment refines the existing one rather than starting over from the uncorrected line.

The burst commits to the database after a short debounce following the last tap. The character side
is clamped strictly inside the enclosing chapter's character range, because a correction large enough
to push past the next chapter's start would violate the map's monotonicity requirement.

### D7: Replace, not accumulate

One correction per `(book, audio chapter)`. A second correction in the same chapter overwrites the
first. Accumulating anchors would let the owner build a staircase of small, mutually inconsistent
corrections with no way to see or reason about the result.

### D8: A user anchor is exempt from the rate guard

`isGuarded()` falls back to the median rate for any segment more than 2× or less than 0.5× the book
median. Splitting one chapter into two short segments can produce exactly that, and the guard would
then quietly discard the correction the owner just entered.

The guard exists to catch bad automatic chapter matching. A correction entered by hand is not that.
`ReadAlongAnchor` gains a marker for owner-entered anchors, and `isGuarded` skips segments bounded by
one.

**Alternative considered:** widen `RATE_GUARD_RATIO`. Rejected — it weakens the guard for the
automatic matching it was built for, to fix a case that is not about matching at all.

**Raised in use, and resolved by bounding the correction rather than withdrawing the exemption.**

A correction placed close to a chapter boundary has almost no room to express itself, and this
exemption is exactly what stopped the guard containing the result. Writing `b` for the room back to
the chapter's start and `a` for the room on to its end, a correction of `δ` implies:

```
  rate before = R · (1 + δ/b)        rate after = R · (1 − δ/a)
```

As `a` shrinks the second factor runs away, and nothing caught it. On the owner's copy a correction
landed 19.5 seconds before a chapter ended, where only `[−19.5 s, +9.7 s]` is expressible — a
correction of a minute made the text sprint through the final seconds to the next chapter's opening,
which reads as that chapter starting in the wrong place.

`expressibleCorrectionRange` now bounds `δ` so both factors stay within [RATE_GUARD_RATIO] of the
chapter's own rate, and `nudge` clamps to it. This is the better fix than withdrawing the exemption,
because withdrawing it does not help: the endpoints of a segment are anchors either way, so a
guarded cramped segment reads at the median and then *jumps* at the boundary instead of sprinting
into it. Neither is acceptable, because the real fault is that the correction was unsatisfiable —
the chapter simply has less text left than the correction asks to redistribute.

With the bound in place the exemption is no longer load-bearing against pathology: a correction that
fits in the range cannot produce an out-of-ratio segment at all. It still earns its keep for a
chapter whose own rate differs from the book median. `ReadAlongCorrectionTest` pins the property by
sweeping correction positions and sizes and asserting no resulting segment leaves the ratio.

The bound is surfaced rather than silent — the sheet says the chapter has no more room instead of
letting the stepper look unresponsive.

### D9: A new table, keyed on the audio chapter index

```
read_along_corrections
  audiobookId   INTEGER NOT NULL   -- FK → audiobooks(id) ON DELETE CASCADE
  chapterIndex  INTEGER NOT NULL   -- audio-side index, as ChapterEntity/NoteEntity use
  audioMs       INTEGER NOT NULL
  charOffset    INTEGER NOT NULL   -- absolute characters into the ebook
  PRIMARY KEY (audiobookId, chapterIndex)
```

Keyed on the *audio* chapter index because that is what stays meaningful. The ebook side is a
character position into a specific linked file, which is why relinking discards corrections (D10).

Cascade from `audiobooks`, matching `ChapterEntity` and `NoteEntity`. Database version 7 → 8, one
additive migration in the shape of `MIGRATION_6_7` rather than the table rebuild `MIGRATION_5_6`
needed. As that migration's comment warns, hand-written DDL must match Room's generated schema
exactly — column order, affinities, nullability, foreign key clause, index name — so the migration
test pins both statements against the committed version 8 export.

### D10: Relinking or unlinking the ebook clears corrections

A correction stores a character offset into one specific EPUB. Point it at a different file and it
addresses an arbitrary place. `changeEbook` already drops search hits for precisely this reason;
corrections go the same way, in the same transaction as the relink.

### D11: The control lives in a sheet reached from the reader's menu

The reader's low-frequency actions are all behind the overflow menu, and this is one — a book needs
correcting once or twice per chapter at most, not once a page. A one-stepper sheet is short enough
that the 33% anchor line stays visible above it, so the owner can watch the text move against the
line while adjusting.

That last claim is a layout assertion and needs eyeballing on a device rather than trusting; see
Risks.

## Risks / Trade-offs

**The sheet covers the text being judged** → A `ModalBottomSheet` with one `Stepper` should leave the
top third of the page clear, but "should" is doing work there. Verify on the device early, before the
persistence work is built on the assumption. If it does obstruct, the fallback is a control in the
chrome row itself rather than a sheet, which costs a hand-drawn glyph and a tooltip.

**The correction tapers back before the chapter ends** (D2) → Accepted deliberately. Replace
semantics make a second nudge cheap. If a real book shows this is a constant annoyance, the anchor
pair from D2 is a small follow-up, and by then the right reconciliation window will be known.

**A large accumulated correction makes the page jump rather than glide** → The glide calls
`scrollToItem` when the target block is not laid out. A ten-second step keeps the target on screen,
so single nudges glide; a long burst may jump once. Acceptable, and arguably correct — a large
correction *is* a large movement.

**Hand-written migration DDL drifting from Room's generated schema** → The established failure in
this codebase: it migrates, it stores rows, and it fails schema validation on the next launch.
Mitigated the way `MIGRATION_6_7` was — a test pinning the statements to the committed schema export
rather than to a doc comment.

**The correction is silently wrong if the chapter matching is wrong** → This change fixes
within-chapter drift. If the underlying chapter pairing is off, a per-chapter correction is
whack-a-mole and the owner will be nudging every chapter forever. Not detected or warned about here
(proposal non-goals); the distinguishing symptom is text that is wrong from a chapter's first line
rather than drifting as the chapter runs.

**PRD §3.1 currently forbids this by name** → It lists `user-tapped "sync here" anchors` as out of
scope absent a further explicit request. That request has been made, and §3.1 is independently stale
— it forbids narration-following scroll in the same sentence, which is built and described in §7.3
and §20.4. Amend §3.1 following the §3.2 precedent, in the same change that builds this, so the
document never stands in contradiction to the app.
