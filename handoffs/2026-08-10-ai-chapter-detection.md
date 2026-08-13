# Handoff: AI chapter *detection* feature exploration

**Branch:** `add-folder-audiobooks` (clean at handoff)
**Date:** 2026-08-10
**Session type:** `/opsx:explore` — exploration only. **No code written, no OpenSpec artifacts created.**

---

## Read this first: two similarly-named features, don't conflate them

| | Feature | What it is |
|---|---|---|
| Prior session | **Chapter analysis** — `handoffs/2026-08-10-ai-chapter-analysis.md` | AI-generated commentary played *at* a boundary |
| This session | **Chapter detection** | Finding where the boundaries *are* in unchaptered audio |

Detection is a **prerequisite** for analysis on this user's real library, and for several
features already in the PRD. All seven Mistborn `.m4b` files parse as a single 10–14h chapter,
so chapter navigation (PRD §7.4), chapter title display (§7.2), scrubber markers, and
end-of-chapter analysis all currently have nothing to operate on.

---

## Reference material (read, don't re-derive)

| What | Where |
|---|---|
| Product requirements | `simple_android_audiobook_player.prd` |
| Prior exploration (analysis feature) | `handoffs/2026-08-10-ai-chapter-analysis.md` |
| M4B chapter parser spec | `openspec/specs/m4b-chapters/spec.md` |
| Chapter model (would need a `source` field) | `app/src/main/java/com/brandonmiller/audiobookplayer/m4b/Chapter.kt` |
| MP4 atom reader (central to the top lead below) | `app/src/main/java/com/brandonmiller/audiobookplayer/m4b/Mp4Reader.kt` |

PRD sections that constrain this feature specifically: **§8** ("Do not modify the original
audiobook file", "Do not require preprocessing by the user"), **§24** (privacy / no INTERNET),
**§28.13** (minimal dependencies), **§28.19** (preserve originals), **§20** (three screens only).

`openspec list --json` → one active change, `add-folder-audiobooks`, 0/40 tasks done.
The Media3 / ExoPlayer / MediaSession layer is still unwritten.

---

## The user's idea (as stated)

"Would be neat to have AI scan the audio and detect where the chapters are and update the m4b
appropriately so that it will know where chapters are."

---

## Conclusions reached this session

### 1. The top technical lead: detection may need no audio decoding at all

In a **VBR** AAC file, silence encodes to minimum-size frames. The `stsz` atom is a per-sample
byte-size table and `stts` gives durations — both in `moov`, which `Mp4Reader.kt` already parses.
So **frame size is an energy proxy**, and a 12h book's energy envelope could be derived in
under a second from a table already being read, with no MediaCodec, no foreground service, no
new dependencies.

Fallback if the files are CBR: real decode via MediaExtractor/MediaCodec, estimated 5–15 min
per 12h book, needing WorkManager/foreground service and thermal consideration.

**This is the single highest-leverage unknown.** A ~1 hour spike ("are the Mistborn files VBR?")
branches the entire design. It was offered and not yet run.

### 2. "AI" is a spectrum, and the cheap end likely carries most of the value

```
silence/energy → + duration priors → + targeted ASR keyword spot → full transcription + LLM
no deps          pure arithmetic     ~50MB model, NDK              hours / network / privacy
finds boundaries  ranks & filters    NAMES boundaries              names + summarizes
```

Key argument for stopping early: most properly-chaptered `.m4b` files literally title their
chapters "Chapter 1", "Chapter 2". Boundaries + ordinal numbering produces output nearly
indistinguishable from a correctly tagged file. ASR buys real titles and confirmation — nice,
not load-bearing. Stage 1 is independently shippable.

### 3. Boundary selection algorithm proposed (unsupervised, no training data)

Gap-length distribution in narration is multi-modal: words <150ms, sentences 300–700ms,
paragraphs 1–2s, chapters 2–4s. Fixed thresholds are fragile across books. Instead:

```
for threshold in 1.0s .. 4.0s:
    boundaries = gaps longer than threshold
    score = regularity(resulting chapter durations)   # real chapters are semi-uniform
          × plausibility(median duration in 10–60min)
          × plausibility(count in 15–120)
pick argmax
```

~30 lines, self-tuning per book, no model. **Free ground truth for testing:** take any properly
chaptered `.m4b`, ignore its chapter atoms, run the detector, compare — real accuracy measurement
with zero hand labeling.

### 4. If ASR is wanted later, transcribe the doorways not the book

Stage 1 yields ~60 candidates; decode only ~8s after each = ~8 minutes of audio instead of 12
hours (~0.1%). Turns an hours-long job into a minutes-long one.

Cheaper model-free variant: within one book the narrator says "Chapter" identically every time.
Cross-correlate one instance's mel-spectrogram against the candidate windows to find the rest.
Suggested UX: play 5s, user confirms **one** boundary, app finds the other 43. One tap, zero MB.

### 5. "Update the m4b" — pushed back on, reframe offered

Writing chapters into the file contradicts PRD §8 and §28.19, and is mechanically the worst of
the three options (growing `moov` shifts every `stco`/`co64` chunk offset; one bug corrupts a
500MB–2GB file the user cares about).

| Storage | Risk | Portable | Effort |
|---|---|---|---|
| **Room DB** alongside parsed chapters | none | no | trivial, table exists anyway |
| Sidecar file beside the `.m4b` | none | yes if copied | small, but see SAF note |
| Written into the `.m4b` | corrupts originals | yes | atom surgery |

**SAF constraint surfaced:** `ACTION_OPEN_DOCUMENT` on a single `.m4b` yields a URI for that
file only. Creating a sibling file requires a parent *tree* URI — so sidecars-next-to-audio
quietly require also asking for folder access. (Same constraint applies to the analysis-sidecar
idea in the prior handoff.)

Recommendation given: store in Room; add an explicit user-initiated **"Export chapters"** action
later if portability matters. Reframe — not "update the m4b so it knows", but "the app knows".

### 6. Low cost of error licenses a heuristic and no review screen

A false boundary lands next-chapter mid-sentence; a missed one yields a long chapter. Neither
corrupts anything or loses position. So: ship the heuristic, mark chapters `DETECTED` in the UI,
skip a dedicated review screen (PRD §20 wants three screens). One "rescan, expect more/fewer
chapters" control probably suffices.

### 7. Timing, and the one cheap decision available now

This is downstream of both the unwritten Media3 layer and `add-folder-audiobooks`. Should not be
built yet. But — exactly parallel to the Model A/B decision flagged in the prior handoff — there
is a free move: give `Chapter` a `source: EMBEDDED | DETECTED | MANUAL` field so everything
downstream (player UI, scrubber, Room schema, resume logic) treats a detected chapter as *just a
chapter*. Costs nothing now; costs a migration after persistence is written.

**Note there are now two such cheap-now/expensive-later decisions accumulating**, both about the
playback layer, both worth capturing as design notes whenever the playback change is proposed.

---

## Open questions put to the user — all unanswered

1. **Are the Mistborn files VBR?** Decides whether this is arithmetic on an already-parsed table
   or a real decoding pipeline. Biggest branch on the board; offered as a spike, not yet run.
2. **Is ordinal numbering enough** ("Chapter 1…44" with correct boundaries), or are real titles
   worth a ~50MB on-device speech model?
3. **Did "update the m4b" mean portability** (chapters survive moving the file to another player)
   **or just app awareness?** Only the first puts the user's files at risk.

The three open questions from the *prior* handoff (sidecar shape, voice, trigger) also remain
unanswered.

---

## Suggested next steps

1. Run the VBR spike — cheapest action with the largest design consequence.
2. Get answers to the three questions above before proposing anything.
3. Do not start this feature ahead of `add-folder-audiobooks` and the playback layer.

---

## Suggested skills

- **`/opsx:explore`** — to resume this thread (VBR spike results, algorithm detail, the
  confirm-one-find-the-rest UX) without committing to anything.
- **`/opsx:propose`** — once the open questions are answered, to create the change with proposal,
  design, delta specs, and tasks in one step.
- **`/opsx:apply`** — only after a proposal exists and the user asks to implement.

Project conventions the next agent must respect (in persistent memory; repeated because missing
them causes rework): OpenSpec CLI is on PATH — call `openspec <cmd>` directly, never `npx`.
Bash-tool writes to project files do not persist in this sandbox; use PowerShell or Write/Edit.
Commits go through the `tscommit` Bash function and carry no Claude co-authorship attribution.
Handoffs live in `handoffs/` in the repo, never the OS temp directory.
