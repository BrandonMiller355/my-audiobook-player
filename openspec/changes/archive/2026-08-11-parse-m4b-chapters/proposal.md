## Why

PRD §8 makes `.m4b` embedded chapter support a required feature, and Media3 does not expose MP4
chapter atoms — so this is the one place the project has to parse a binary container itself
(`openspec/config.yaml`, architecture conventions). It is simultaneously the highest-risk unknown
in the project and the most isolatable piece of it: a pure function from bytes to a chapter list,
with no UI, no service, no database, and no Android framework dependency.

Doing it now, ahead of the library and playback work, means the rest of the app can be built
against a `Chapter` shape that is known to be producible from real files rather than assumed.
PRD §27 puts this in Phase 6; that ordering was explicitly revisited and moved up.

There is also a concrete finding driving the design. Probing all seven `.m4b` files in the owner's
library (`H:\eBooks\Mistborn\...`) showed **none of them carry usable chapter marks**: no Nero
`chpl` atom at all, and a QuickTime chapter track containing exactly one entry — the book title,
spanning the entire 10–14 hour file. The PRD treats "no embedded chapters" (§7.4) as a degenerate
edge case; for this library it is the normal case, and the parser has to say so cleanly rather
than reporting a useless single chapter.

## What Changes

- Add a chapter parser that reads an `.m4b` (MP4) byte stream and returns either an ordered list
  of chapters, an explicit "unchaptered" result, or an explicit "unreadable" result. It never
  throws on malformed input (PRD §22).
- Support both chapter encodings found in the wild:
  - the **Nero `chpl`** atom under `moov/udta`, and
  - the **QuickTime chapter track** — a `tref/chap` track reference pointing at a `text` handler
    track, whose sample table and sample text supply the marks.
- **Treat a single chapter spanning the whole file as unchaptered.** This is decided structurally
  (one entry, starting at zero, covering the full duration) rather than by comparing the chapter
  title against the book title, so it also catches encoders that write "Chapter 1" or the filename
  there. Confirmed with the owner.
- Read only what is needed: walk the box tree to `moov` and the chapter track's sample tables.
  Never read `mdat`, and never touch the audio track's own sample tables, which are megabytes in
  size in real files.
- Add unit tests built on **synthetic MP4 fixtures constructed in the test source** — `chpl` only,
  QuickTime track only, both present, neither, the degenerate single-entry shape, 64-bit box
  sizes, `moov` positioned after `mdat`, and malformed/truncated input.
- Add no dependency, no permission, no manifest entry, and no wiring into the app.

## Capabilities

### New Capabilities

- `m4b-chapters`: Extracting embedded chapter marks from an `.m4b` file — which encodings are
  understood, how chapter boundaries and titles are derived, when a file counts as unchaptered,
  and how malformed input is reported rather than crashed on.

### Modified Capabilities

None. `app-shell` is untouched — this change adds no screen, no route, and no build configuration.

## Impact

**Code**: New files under `app/src/main/java/com/brandonmiller/audiobookplayer/m4b/` holding the
box walker, the two chapter readers, and the public result type. New tests under `app/src/test/`
including an inline synthetic-MP4 builder. Nothing existing is modified.

**Dependencies**: **None added.** The parser uses `java.nio` only. This is deliberate — PRD §28
rule 4 prefers maintained libraries over hand-rolled binary parsers, but the available MP4
metadata libraries pull in far more than chapter reading, and `config.yaml` already sanctions this
one component as the exception. The parser stays small and behind one entry point so that swapping
it for a library later is a contained change.

**Permissions and manifest**: No change. Nothing here touches the network, and the no-network
check established by `app-shell` continues to pass unchanged.

**Interface for later changes**: This change fixes the shape of a chapter — index, title, start,
end — which the `BookTimeline` coordinate mapping and the Room schema will both be written
against. That is the main reason to land it before the library and playback work.

**Risk**: Contained. Nothing calls the parser yet, so a defect here cannot break a working app;
the exposure is that a wrong `Chapter` shape would ripple into later changes, which is exactly
what building it first is meant to prevent.

## Non-goals

This change deliberately does **not**:

- Wire the parser into the app — no library entry, no Room table, no playback, no UI. Chapters are
  parsed and returned to a caller that does not exist yet.
- Cache parsed chapters. PRD §8 requires caching at add-book time; that belongs with persistence.
- Extract cover art. The embedded artwork in these files sits in the standard `udta/meta/ilst`
  `covr` atom, which Media3 already exposes — no custom parsing needed, so none is written.
- Read any metadata beyond what chapters require (title, author, track numbers). That is PRD §11
  and belongs to the metadata change.
- Handle folder-based books. They have no embedded chapters by definition; one file is one chapter.
- Modify, rewrite, split, or re-mux the source file in any way (PRD §8, §28 rule 19).
- Shell out to FFmpeg or require any preprocessing by the user (PRD §8).
- Support chapter encodings beyond `chpl` and the QuickTime chapter track — notably not ID3
  `CHAP` frames in MP3, which are a folder-book concern and not required by the PRD.
