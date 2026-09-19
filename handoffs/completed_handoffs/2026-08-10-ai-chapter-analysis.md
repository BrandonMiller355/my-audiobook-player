# Handoff: AI Chapter Analysis feature exploration

**Branch:** `add-folder-audiobooks` (clean at handoff)
**Date:** 2026-08-10
**Session type:** `/opsx:explore` — exploration only. **No code written, no OpenSpec artifacts created.**

---

## Why this doc carries so much detail

Normally a handoff points at artifacts. This session produced none — it was pure exploration,
so the reasoning below exists nowhere else on disk. Everything about the *existing* project
is referenced by path rather than repeated.

---

## Reference material (read these, don't re-derive)

| What | Where |
|---|---|
| Product requirements | `simple_android_audiobook_player.prd` |
| M4B chapter parser spec | `openspec/specs/m4b-chapters/spec.md` |
| App shell / nav spec | `openspec/specs/app-shell/spec.md` |
| Parsed chapter model | `app/src/main/java/com/brandonmiller/audiobookplayer/m4b/Chapter.kt` |

PRD sections that constrain this feature specifically: **§18** (playback queue/timeline model),
**§19** (data model), **§24** (privacy — prefer no INTERNET permission at all), **§3** (non-goals).

`openspec list --json` → no active changes. Only `openspec/changes/archive/` exists.

---

## Current state of the codebase (verified this session)

App is early. Present: app shell, Compose theme, Library + Player screens (stubs), navigation
graph, and the complete `.m4b` chapter parser with tests (~1450 LOC total).

**Not yet written: the entire Media3 / ExoPlayer / MediaSession playback layer.**

That last fact is the single most important input to this feature's timing — see the fork below.

---

## The user's idea (as stated)

At the end of each chapter, play an AI-generated analysis of that chapter aloud. Must be
skippable. For MVP, the analyses are generated *outside the app* and imported as text
alongside the `.m4b`. Later, possibly interactive — talking back and forth with it. User
asked for other good ideas in this space.

---

## Conclusions reached (the substance of the session)

### 1. The load-bearing decision: analysis as MediaItem vs. TTS overlay

Two models were compared:

- **Model A — the analysis is a MediaItem** inserted into the ExoPlayer queue.
- **Model B — a TTS overlay** that pauses the player, speaks, and resumes.

Model A won decisively. One audio path instead of two; audio focus, ducking, playback speed,
lock-screen display, and process-death survival are all inherited from work the player must do
anyway. Model B needs a parallel audio path that fights ExoPlayer for focus, has its own rate
scale, lies on the lock screen, and has transient state.

**Decisive detail:** the analysis fires precisely when the user cannot look at the phone
(walking, running, driving). Under Model A the Bluetooth "next track" button skips it for free,
because it genuinely *is* the next track. Under Model B a blind skip gesture must be invented.

### 2. Text vs. audio is a false conflict

`TextToSpeech.synthesizeToFile()` is on-device, needs no INTERNET permission, and bridges the
two: **text stays the source of truth** (needed for on-screen display and for any interactive
future), audio is a derived, regenerable cache played as a normal MediaItem.

Caveat raised: on-device Android TTS sounds robotic, and the tonal whiplash after 40 minutes of
a professional narrator is real. So the likely *actual* MVP is pre-rendered audio sidecars the
user generates on their desktop, with on-device TTS as the fallback when audio is missing.
Either way the app-side plumbing is identical — that is the point of choosing Model A.

### 3. The trigger is harder than it first appears

For folder books, "end of chapter" is a free track-transition event. For `.m4b` it is **not an
event at all** — just a timestamp crossed inside one long media item.

Proposed rules (user has not confirmed):
- **Only a genuine playback crossing arms it.** Scrubbing, chapter-skip, and resume-from-saved-
  position do not fire it.
- **Per-(book, chapter) "already heard" flag** so it never fires twice.
- Both rules serve one goal: it must never feel like it is interrupting *the user*, only
  punctuating *the book*.

Skip wants three levels, not one: skip this instance (headset next) / off for this book /
off globally. No settings screen exists in the PRD; a per-book toggle could live on the player
screen and the global one can wait.

### 4. Two flagged risks

- **The user's `.m4b` library has no chapter boundaries.** All seven Mistborn files parse as a
  single 10–14h "chapter." So end-of-chapter analysis has nothing to fire on for the user's
  real books today. This argues that the sidecar should carry **its own trigger timestamps**
  rather than binding to parsed chapter indices — more robust anyway, since index-binding
  breaks on a re-rip.
- **INTERNET permission is the line.** The MVP as described honors PRD §24 completely (offline
  text, on-device TTS, zero network) and that property is worth protecting. The interactive
  version crosses it hard: mic permission, speech recognition, an API key on the phone,
  latency in a hands-free context, listening history leaving the device. Not an objection —
  the user's call — but it is a materially different app in privacy terms, and the MVP should
  not quietly pre-build for it.

Also noted (generation-side, not app-side): an AI that has read the whole book will leak the
ending into chapter 3's analysis unless constrained. The app cannot protect against this; the
sidecar format only carries what it is fed.

### 5. Adjacent ideas offered, in the order recommended

1. **Resume recap** — on returning after a multi-day gap, 30s of "here's where you are."
   Assessed as *more* valuable than end-of-chapter analysis: same content pipeline, different
   trigger, keyed off the `lastPlayedAt` the PRD already stores. Arrives at a moment of real
   need instead of competing with momentum. **Also the only trigger that works on the user's
   current chapterless library.**
2. **Tiered lengths** — 20s "what happened" auto-plays; 3min "what it means" on tap.
3. **Show the text on screen while it plays** — free, and doubles as the no-TTS fallback.
4. **Analysis markers on the book-wide scrubber** — makes them findable, not just encounterable.
5. **End-of-book wrap-up** — trivial once the format exists; index `-1`.
6. **Spoiler-safe "who is this again?"** — genuinely useful across seven Mistborn books, but
   needs N versions of every character bio. Filed as post-interactive.

---

## Open questions put to the user — all still unanswered

1. **Sidecar shape** — one JSON file per book, or a folder of numbered text files beside the
   audio? Does an entry bind to a chapter index or carry its own trigger timestamp?
   (Recommendation given: timestamps.)
2. **Voice** — is on-device TTS acceptable, or is desktop-generated audio the real MVP?
3. **Trigger** — end-of-chapter, resume-recap, or both? Build the recap first, given the
   chapterless library?

The session ended with an offer to either sketch the sidecar format concretely or capture the
thinking as a change proposal. The user invoked `/handoff` instead of answering, so **treat all
three as genuinely open.**

---

## Suggested next steps

The highest-value move is **not** to start building this feature. It is to settle the Model A /
Model B fork *before* the Media3 layer gets written, because deciding now costs nothing and
retrofitting onto a finished single-item `.m4b` player costs a lot. That decision could be
captured as a design note on the playback change whenever it is proposed.

If the user wants to proceed with the feature itself, get answers to the three open questions
first — particularly the sidecar format, which is the only part with no reasonable default.

---

## Suggested skills

- **`/opsx:explore`** — to resume the thinking thread if the user wants to keep exploring
  (sidecar format, interactive design) rather than commit to anything.
- **`/opsx:propose`** — once the three open questions are answered, to create the change with
  proposal, design, delta specs, and tasks in one step.
- **`/opsx:apply`** — only after a proposal exists and the user asks to implement.

Project conventions the next agent must respect (already in persistent memory, repeated here
only because they cause rework if missed): OpenSpec CLI is on PATH — call `openspec <cmd>`
directly, never `npx`. Bash-tool writes to project files do not persist in this sandbox; use
PowerShell or the Write/Edit tools. Commits go through the `tscommit` Bash function, and
carry no Claude co-authorship attribution.
