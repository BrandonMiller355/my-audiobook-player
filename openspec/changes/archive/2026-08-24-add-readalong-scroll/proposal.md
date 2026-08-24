## Why

The reader shipped as a manual companion: the ebook and the audio are two independent places in the
same book and nothing keeps them in step. The owner wants the text to follow the narration — glance
at the page and be roughly where the narrator is, without hunting for it.

That was deliberately excluded from `add-ebook-companion`, and the stated reason has since stopped
being true. The exclusion rested on one fact: every `.m4b` in the library parsed as a single 10–14
hour chapter, so there were no anchors to re-synchronize against and interpolation error accumulated
unchecked for six hours at a stretch. **The owner is tagging chapter marks into the library**, which
restores anchors every 20–40 minutes and puts error back inside a range that interpolation survives.

The second thing that changed is the accuracy target. `handoffs/2026-08-10-ebook-audio-readalong.md`
set out four tiers and treated the choice between them as open. The owner has now answered the
question that selects one: *roughly the same page* is the goal, not the highlighted sentence. That
rules out forced alignment entirely — no Whisper, no on-device ASR, no desktop preprocessing
pipeline, no EPUB3 Media Overlays. What remains is Tier 1 plus proportional interpolation, and it is
a small change.

**This is a PRD amendment, not a PRD feature**, and the second one in this area. PRD §20 specifies
three screens and no reader at all; §28.2 says not to add features the PRD does not list. The owner
requested this explicitly, which is the exception §3 names. Recorded here so the departure is
deliberate rather than silent.

**On PRD §8** ("do not require preprocessing by the user"): the owner tagging chapters into their own
files is a choice they have made about their library, not a demand this feature makes. A book with no
chapter marks still plays, still links an ebook, and still reads exactly as it does today — read-along
is simply unavailable on it, and says so. Nothing here degrades an untagged book. The distinction
matters and is the reason this does not violate §8.

## What Changes

- **The reader scrolls itself while the audio plays.** Within a chapter, the text position is
  interpolated from how far through that chapter the audio is, measured in **characters of text**
  rather than paragraphs or scroll distance. Paragraph lengths vary by two orders of magnitude;
  narration time tracks characters closely and block counts not at all.
- **Scrolling the text moves the audio.** When a user scroll settles, the audio seeks to the place in
  the book the reader is now showing. The two positions are therefore never in disagreement, which is
  why this change adds no "out of sync" state, no offset readout, and no resynchronize control.
- **Audio position and reading position become one position** while read-along is on. This
  **BREAKING**ly replaces the `ebook-reader` scenario asserting the two are independent, and the
  `playback` scenario asserting that scrolling the ebook never seeks.
- **A seek that lands while paused does not start playback.** It repositions and leaves the transport
  state alone, consistent with PRD §6 ending its flow with the user pressing play.
- **Audio chapters are paired to ebook table-of-contents entries** by normalized label match, falling
  back to pairing in order. Where front matter makes the two lists differ — the EPUB has a copyright
  page and a map, the audio has publisher credits — the mismatch is a constant offset, so the
  correction offered is **a single offset control**, not a per-chapter pairing table.
- **Read-along has no control.** It is simply on for any book that supports it. Pausing the audio
  already stops the text following it, which is what a toggle would have been for.
- **A small movement does not seek.** A settle whose implied jump is under a few seconds is ignored,
  so ordinary scroll adjustments do not produce a stream of micro-seeks against the player.
- **A seek caused by scrolling can be undone** from a passing message, restoring the previous
  playback position. The reader already hosts a snackbar for rejected picks.
- **Jumping via the table of contents or a search result behaves exactly like a drag** — one rule
  covers all three deliberate movements of the reader.
- **Read-along is silently absent when it cannot work**: a book whose audio has no chapter marks, or
  an ebook with no navigation document. The reader simply does not follow, and nothing else about the
  book is affected. With no control to appear inert there is nothing to explain.

### Non-goals

Deliberately excluded, and named so a later reader knows they were considered:

- **Word-level or sentence-level synchronization**, and any highlighting of the narrated text. This
  is the accuracy question the owner answered; interpolation cannot reach that granularity and no
  amount of tuning changes it.
- **Forced alignment in any form** — no bundled ASR model, no whisper.cpp, no aeneas, no Storyteller,
  no EPUB3 Media Overlays, no desktop preprocessing step, no sync-map sidecar file. Tiers 3 and 4 of
  the handoff remain unbuilt and this change does not approach them.
- **Learning correction anchors from use.** Every "scroll settles, audio seeks" is a true
  `(text position ↔ audio time)` pair the owner validated by using the app normally, and storing them
  would make the map piecewise accurate rather than one straight line per chapter. That is a real
  and cheap future improvement; it is not in this change. The data model below is nonetheless shaped
  to hold anchors, because doing so now is free and doing it later is a migration.
- **Deriving audio chapter boundaries from the ebook's structure** — detecting silences, reading
  `stsz` frame sizes as an energy proxy, or matching either against the ebook's chapter proportions.
  Explored and set aside: the owner is tagging chapters directly, which is strictly better than any
  inference.
- **Seeking continuously while the finger is down.** The audio moves once, when the scroll settles.
- **A per-chapter pairing table.** The offset control covers the failure mode that actually occurs.
- **Any change to playback machinery.** No change to `PlaybackService`, the media session, the
  notification, the lock screen, or Bluetooth handling. Read-along seeks through the same
  `MediaController` the reader already holds for its play/pause control.
- **Read-along outside the reader.** Nothing on the Player or the Library changes.

### Dependencies

**No new third-party dependency is added.** Every input already exists in the app: `BookTimeline`
supplies chapter extents and converts book positions to player coordinates, `Block.charOffset` and
`Ebook.blockIndexFor` supply the text-side addressing, and `LazyListState` supplies both the scroll
control and the layout information needed for sub-block precision.

### Permissions and manifest

**No new permission, no new foreground-service type, no manifest change.** The feature is arithmetic
over data the app already holds, and the existing `verify<Variant>Permissions` Gradle task continues
to guard the build.

## Capabilities

### New Capabilities

- `readalong-sync`: The correspondence between a place in the ebook and a moment in the audio — how
  audio chapters pair to table-of-contents entries, how a position is interpolated in each direction,
  when scrolling seeks and when it does not, the per-book chapter offset, and what happens when a
  book cannot support any of it.

### Modified Capabilities

- `ebook-reader`: The requirement that reading position is remembered per book gains read-along's
  effect on it, and its scenario asserting that reading position is independent of playback position
  is replaced — under read-along they are the same position, and the independence guarantee is
  re-scoped to read-along being off. The reader's own table-of-contents and search requirements are
  unchanged; what those jumps do to the *audio* belongs to `readalong-sync` rather than being
  restated here.
- `playback`: The requirement that switching between the Player and the reader leaves playback alone
  keeps its entry and exit scenarios, but its "Scrolling the ebook does not seek" scenario is
  replaced: with read-along on, scrolling seeks; with it off, the existing guarantee stands unchanged.

## Impact

**Schema** — Room goes from version 4 to 5. `MIGRATION_4_5` adds columns to `audiobooks` for whether
read-along is enabled and for the chapter pairing offset, additive and non-destructive in the same
shape as the four migrations before it. Existing rows keep them null and behave exactly as they do
today. The exported schema under `app/schemas` gains a version 5 file.

**New code** — a small pure-Kotlin mapper alongside `BookTimeline`, holding the character prefix sums
per chapter and converting in both directions. No Android types, so it is testable with plain JUnit
over synthetic block lists and chapter spans, the same way `BookTimeline` already is.

**Touched code** — `data/Entities.kt` and `data/Migrations.kt` for the schema; `data/LibraryDao.kt`
for reading and writing the offset; `ebook/Ebook.kt` for chapter block ranges derived
from `NavEntry`; `ui/reader/ReaderViewModel.kt`, which currently tracks only play/pause state and
must now follow position and issue seeks — reversing `add-ebook-companion` design D8, which chose
otherwise for the manual tier; `ui/reader/ReaderScreen.kt` for the scroll driving and for
distinguishing user-initiated movement from programmatic; `ui/reader/ReaderChrome.kt` for the offset
control.

**A trap worth naming up front** — the reader currently saves its position by watching
`LazyListState.isScrollInProgress` fall to false. That fires for programmatic scrolls as well as user
ones. Attaching the seek to that same signal unchanged would make every auto-scroll step report a
settle, reverse-map to the top of the current block, and drag the audio backward on a loop. The seek
must be gated on genuine user input, not on scroll motion. design.md carries the mechanism.

**Untouched** — everything under `playback/` except read-only use of `BookTimeline`. `ebook-linking`,
`reading-preferences`, `cover-art`, `folder-scanning`, and the library and Player screens are
unaffected.

**PRD** — §7 grows the reader as a place a seek can originate. Recorded above.
