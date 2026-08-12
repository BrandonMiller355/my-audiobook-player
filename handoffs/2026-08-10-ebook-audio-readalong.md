# Handoff: EPUB/MOBI read-along (text synced to audio) exploration

**Branch:** `add-folder-audiobooks` (only untracked handoff docs at handoff time)
**Date:** 2026-08-10
**Session type:** `/opsx:explore` — exploration only. **No code written, no OpenSpec artifacts created.**

---

## The user's idea (as stated)

"It would be neat to be able to load epubs or mobis alongside my audiobooks and it could scroll
through the text at the same time as it's going through the audio. Would this be a huge lift?"

This is the feature commercial apps call *immersion reading* / read-along (Audible Whispersync
for Voice, Kindle + Audible pairing).

---

## Read this first: three explorations now sit in `handoffs/`, don't conflate them

| Handoff | Feature |
|---|---|
| `handoffs/2026-08-10-ai-chapter-analysis.md` | AI commentary played *at* a chapter boundary |
| `handoffs/2026-08-10-ai-chapter-detection.md` | Finding where boundaries *are* in unchaptered audio |
| **this doc** | Pairing an ebook with an audiobook and scrolling text in time with narration |

All three are downstream of the unwritten playback layer. All three are affected by the same
root fact: the owner's real `.m4b` files have no chapter marks. See below — that fact is even
more damaging here than in the other two.

---

## Reference material (read, don't re-derive)

| What | Where |
|---|---|
| Product requirements | `simple_android_audiobook_player.prd` |
| Active change (blocks this work) | `openspec/changes/add-folder-audiobooks/` |
| Existing specs | `openspec/specs/app-shell/`, `openspec/specs/m4b-chapters/` |
| Chapter model (would need to hold alignment data) | `app/src/main/java/com/brandonmiller/audiobookplayer/m4b/Chapter.kt` |
| MP4 atom reader | `app/src/main/java/com/brandonmiller/audiobookplayer/m4b/Mp4Reader.kt` |

PRD sections that constrain this feature specifically: **§3** (non-goals — DRM explicitly listed;
"keep the project intentionally small"), **§8** ("do not require preprocessing by the user"),
**§24** (privacy / no INTERNET), **§28.2** ("do not add features not listed in this PRD"),
**§28.13** (minimal dependencies), **§20** (three screens only).

**Verified state at handoff:** `openspec list --json` → one active change, `add-folder-audiobooks`,
**0/40 tasks done**. The app is `app-shell` + `M4bChapterParser` only. No Media3 dependency in
`app/build.gradle.kts`. `PlayerScreen.kt` is a stub that renders its route argument. No library
persistence. Nothing plays audio yet.

---

## Conclusions reached this session

### 1. The cost is concentrated entirely in one of four parts

```
1. Read the EPUB (zip + OPF spine + XHTML → text)      easy
2. Render scrolling text in Compose                    easy–medium
3. Persist the ebook↔audiobook pairing                 easy
4. Know WHERE in the text the narrator is              ← the entire feature
```

Steps 1–3 are ordinary work; a hand-rolled EPUB text extractor using `java.util.zip` +
`XmlPullParser` needs no dependency and mirrors the precedent already set by the hand-written
MP4 atom parser. Step 4 is **forced alignment**, and it alone determines whether this is a week
or a different project.

### 2. Naive proportional interpolation fails, and fails worst on this user's library

Interpolating text offset from elapsed time drifts, because narration rate is not constant
(dialogue vs. action, headings read aloud, publisher credits with no text behind them). Drift is
survivable only if re-anchored often, and **chapter boundaries are the natural free anchors**:

```
chapter-anchored, ~30 min chapters:
  error resets to zero at every mark; worst case mid-chapter ≈ 30–90s of narration
```

But per the verified finding in `handoffs/2026-08-10-ai-chapter-detection.md`, all seven Mistborn
`.m4b` files are a **single nominal chapter spanning 10–14 hours**. No anchors exist:

```
  ├──────────── 12 hours, ONE chapter ────────────┤
  error accumulates unchecked; midpoint drift plausibly 10–20+ minutes of narration
```

The cheap version of this feature is disproportionately broken on exactly the library the user
owns. This is the central finding of the session.

### 3. Four tiers, presented to the user

| Tier | Mechanism | Accuracy | Lift | Works on user's m4bs? |
|---|---|---|---|---|
| 0 | Manual companion — ebook opens beside player, user scrolls | none | ~3 days | yes |
| 1 | Chapter jump — tap chapter, ebook jumps | chapter-level | ~4 days | **no** (no chapters) |
| 2 | Interpolate + user-tapped "sync here" anchors, piecewise | drifts, self-correcting | ~1 week | poorly (constant tapping) |
| 3 | On-device forced alignment (whisper.cpp bundled) | word-level | **weeks**, ~75MB model, hours/book, thermal + battery cost | yes, painfully |
| 4 | Align once on PC, phone consumes a sync map | word/sentence | ~1 week phone side + PC tooling | **yes** |

Estimates are rough session-count intuitions, not commitments.

### 4. Tier 4 is the recommended shape, and it has a valuable side effect

```
 ONE-TIME, ON PC                       │  ON PHONE (the app)
 epub ─┐                               │
       ├─▶ whisper ─▶ align ─┐         │
 m4b  ─┘                     ▼         │
              epub3 + SMIL media overlay ─▶ binary-search (startMs,endMs,fragmentId)
 heavy, GPU, offline, once             │  → highlight + scroll. Trivial. No network.
```

**Storyteller** (open source, self-hosted) reportedly does exactly this pipeline and emits
**EPUB3 with Media Overlays** — a real standard where SMIL `<par>` elements pair a text fragment
ID to audio begin/end timestamps. *Not verified this session; the next agent should confirm its
current output format and license before designing against it.*

If that holds, the Android side collapses to parsing SMIL and binary-searching a sorted timestamp
list — structurally the same as the `.m4b` chapter timeline already specced.

**Side effect worth more than the feature:** alignment projects the ebook's chapter structure
onto the audio timeline, which **hands the chapterless 12-hour files real chapter boundaries for
free**. That independently solves the problem driving the `ai-chapter-detection` exploration. The
next agent should consider whether these two threads should merge.

**Tension to name, not paper over:** Tier 4 violates PRD §8's "do not require preprocessing by the
user." That rule was written about `.m4b` chapters, not this, but the conflict is real and is the
user's call.

### 5. MOBI should be dropped from scope

- Old `.mobi` (PalmDOC) is parseable but nothing currently owned is in that format.
- Modern `.azw3`/KF8 is a different container and Kindle books ship **DRM'd** — PRD §3 lists DRM
  as an explicit non-goal. Stripping DRM was declined.
- No maintained Android MOBI library worth depending on.

EPUB is the only sane target. Calibre converts MOBI on the desktop, which fits the Tier 4
workflow anyway.

### 6. Sequencing — and a **third** accumulating cheap-now/expensive-later decision

This is a PRD amendment, not a PRD feature (§3, §28.2). It sits on top of the entire unwritten
playback layer and needs a stable "current absolute position in the book," which
`add-folder-audiobooks` and the playback change are about to define.

The prior two handoffs each flagged a decision that is free now and a migration later
(Model A/B in `ai-chapter-analysis`; `Chapter.source: EMBEDDED | DETECTED | MANUAL` in
`ai-chapter-detection`). **This session adds a third:** if Tier 4 is the likely direction, the
data model should be able to hold a per-book **alignment map** (sorted `startMs`/`endMs` →
text-fragment spans) as a first-class entity. Cheap to design in before persistence is written;
a schema migration afterwards.

All three are about the playback/persistence layer and should be captured as design notes when
the playback change is proposed.

---

## Open questions put to the user — all unanswered

1. **Does the user actually have EPUBs matching the audiobooks** — same edition, unabridged?
   Alignment quality lives or dies on the text matching what is narrated.
2. **How much drift is tolerable?** "Roughly the right page" makes Tier 2 cheap; "highlight the
   sentence" forces Tier 4.
3. **Is a one-time desktop step per book acceptable?** This answer alone picks the tier.
4. **Reading mode or following mode?** (Switch to text and audio pauses, position carries over)
   vs. (audio drives, text is passive). The second is substantially smaller.
5. **Would alignment be worth running purely for the free chapter boundaries**, ignoring
   read-along entirely?

The open questions in both prior handoffs also remain unanswered.

---

## Suggested next steps

1. Get answers to questions 1–3 before any design work — they select the tier, and the tiers are
   not variations on one design, they are different projects.
2. Verify the Storyteller claim in §4 (output format, license, whether its EPUB3 + Media Overlays
   output is stable) before treating Tier 4 as de-risked.
3. Consider merging this thread with `ai-chapter-detection` — Tier 4 alignment subsumes it.
4. **Do not start this feature.** `add-folder-audiobooks` is 0/40 and the Media3 layer does not
   exist. The only appropriate action now is capturing the alignment-map design note in §6.

---

## Suggested skills

- **`/opsx:explore`** — to resume this thread (tier selection, Storyteller verification, the
  merge-with-chapter-detection question) without committing to anything.
- **`/opsx:apply`** — the genuinely useful next action in this repo is implementing
  `add-folder-audiobooks` (0/40), which everything above is blocked on.
- **`/opsx:propose`** — only once questions 1–3 are answered and the user accepts the PRD
  amendment.

Project conventions the next agent must respect (in persistent memory; repeated because missing
them causes rework): OpenSpec CLI is on PATH — call `openspec <cmd>` directly, never `npx`.
Bash-tool writes to project files do not persist in this sandbox; use PowerShell or Write/Edit.
Commits go through the `tscommit` Bash function and carry no Claude co-authorship attribution.
Branches use a plain feature slug, never a `claude/` prefix. Handoffs live in `handoffs/` in the
repo, never the OS temp directory.
