## Context

An `.m4b` is an MP4 container. Its metadata lives in a tree of boxes ("atoms"), each an 8-byte
header (4-byte big-endian size, 4-byte type) followed by a payload; size `1` means a 64-bit size
follows the header, size `0` means "to end of file". Chapters are not part of the audio stream and
Media3 does not surface them, so they have to be read directly.

Two encodings exist in practice:

```
Nero:                              QuickTime chapter track:
  moov/udta/chpl                     moov/trak[audio]/tref/chap ──▶ track ID
    version+flags, reserved          moov/trak[text]/
    count                              mdia/mdhd        → timescale
    for each chapter:                  mdia/hdlr        → 'text'
      timestamp (100 ns units)         mdia/minf/stbl/
      title length (1 byte)              stts  → sample start times
      title bytes                        stsz  → sample sizes
                                          stsc  → samples per chunk
                                          stco  → chunk file offsets
                                        sample payload = 2-byte length + title text
```

The QuickTime form is indirect: the sample *table* gives times, and the sample *payloads* — which
live out in `mdat` — give titles. Reading it means resolving sample index → file offset through
`stsc`/`stco`/`stsz`, then seeking to each title.

Measured against the owner's real files (verified, not assumed):

| Observation | Value |
|---|---|
| `moov` size | ~9.1 MB, positioned at the **front** (faststart) |
| Audio track `stsz` | 6.16 MB, 1,541,483 samples |
| Audio track `stsc` | 1.76 MB |
| Chapter `text` track | **1 sample**, 64 bytes, at t=0, spanning 13:42:07 |
| Nero `chpl` | absent in all seven files |
| Cover art | `udta/meta/ilst` (~332 KB) — Media3 handles it |

The audio track's sample tables being megabytes is the reason the walker must be selective: it
must never expand the audio track's `stsz`/`stco`, only the chapter track's (which has a handful
of entries).

## Goals / Non-Goals

**Goals:**

- Turn an `.m4b` byte stream into an ordered chapter list, or a clear "no chapters here" answer.
- Never throw, never hang, and never allocate unboundedly on malformed or hostile input.
- Read kilobytes, not gigabytes, regardless of file size.
- Stay unit-testable on the JVM with no device, no emulator, and no real audiobook file.
- Fix the `Chapter` shape that `BookTimeline` and the Room schema will be built against.

**Non-Goals:**

- Wiring into the app, caching, artwork, general metadata — see the proposal's non-goals.
- Being a general MP4 parser. It walks only the boxes chapters need and ignores everything else.
- Sample-accurate audio timing. Chapter marks are navigation targets, not edit points.

## Decisions

### D1: Input is `java.nio.channels.SeekableByteChannel`

The parser takes a `SeekableByteChannel` rather than a file path, an `InputStream`, or an Android
`ParcelFileDescriptor`.

*Why:* the parser needs random access — `moov` may sit after a multi-gigabyte `mdat`, and title
samples are scattered — so a plain `InputStream` is unusable. `SeekableByteChannel` is a JDK
interface, so production can pass `FileInputStream(pfd.fileDescriptor).channel` from a SAF
`ParcelFileDescriptor` while tests pass an in-memory implementation over a `ByteArray` **defined
inline in the test file**. That satisfies the project rule against source files whose only consumer
is the test suite: no fake is invented in `main`, and no custom interface is invented at all.

*Alternative considered:* defining our own `ByteSource` interface. Rejected — `config.yaml` forbids
single-implementation interfaces, and the JDK already has the right abstraction.

*Consequence to handle:* not every SAF provider backs a document with a seekable file. When the
channel cannot seek, that is a real failure mode and surfaces as `Unreadable`, not a crash (D6).

### D2: Prefer the QuickTime chapter track; fall back to Nero `chpl`

When both encodings are present, use whichever yields **two or more** chapters, preferring the
QuickTime track on a tie.

*Why:* the QuickTime track carries an explicit timescale from `mdhd` and is what most modern
encoders write. Nero `chpl` is a fixed 100-nanosecond unit and is more often the vestigial one
left behind by a re-mux. Preferring the richer source, but accepting either, means a file with a
stale empty track and a good `chpl` still works.

*Alternative considered:* always prefer `chpl` because it is far simpler to parse (no sample-table
indirection). Rejected — simplicity of parsing is the wrong tiebreaker when correctness of the
result is at stake.

### D3: One chapter covering the whole file means unchaptered

If chapter extraction yields exactly one entry that starts at (or within a small tolerance of)
zero and extends to the end of the file, the result is `Unchaptered`, not a one-element list.

*Why:* such an entry carries no navigational information — it cannot be seeked between, and a
"Chapter 1 of 1" indicator is noise. Every file in the owner's library is exactly this shape.
Deciding it **structurally** rather than by comparing the title to the book title also catches
encoders that write "Chapter 1", the filename, or the album name into that slot, and avoids a
fragile string comparison that would need the book title threaded into the parser.

*Trade-off, accepted:* a genuinely single-chapter audiobook (a short story) reports as unchaptered
and shows no chapter UI. Since the alternative is displaying "Chapter 1 of 1", nothing is lost.
Confirmed with the owner before writing this.

### D4: Chapter ends are derived, not read

Neither encoding stores chapter end times. Each chapter's end is the next chapter's start; the last
chapter's end is the movie duration from `moov/mvhd` (`duration / timescale`).

*Why:* it is the only information available, and it matches how every other player derives it.
*Edge cases handled explicitly:* chapters are sorted by start time before deriving ends; entries
with equal or out-of-order start times are dropped rather than producing negative durations; a
chapter starting beyond the movie duration is dropped.

### D5: Bounded, selective reading

The walker descends only into `moov`, `udta`, `trak`, `mdia`, `minf`, `stbl`, `tref`, and `meta`
(which carries 4 bytes of version/flags before its children). It resolves the chapter track's
sample tables only — never the audio track's. Every table expansion is checked against a maximum
entry count and against the remaining box payload before allocating.

*Why:* real files put megabyte-scale sample tables in the audio track, and a corrupt file can
declare an entry count of four billion. Both are handled by the same rule: never allocate based on
a declared count without checking it against the bytes actually present.

### D6: A three-way result, and no exceptions

```
sealed interface ChapterParseResult
  data class Chapters(val chapters: List<Chapter>)   // >= 2 entries, sorted, ends derived
  data object Unchaptered                            // no marks, or the D3 whole-file case
  data class Unreadable(val reason: String)          // not an MP4, truncated, unseekable, malformed
```

*Why:* PRD §22 requires never crashing on malformed metadata, and callers need to tell "this book
genuinely has no chapters" (show no chapter UI) apart from "something went wrong reading it" (a
condition worth surfacing). Collapsing both into an empty list would lose that distinction. All
internal parse failures are caught at the entry point and converted to `Unreadable`.

### D7: Title text decoding

QuickTime text samples are a 2-byte big-endian length followed by that many bytes, optionally
followed by encoding atoms which are ignored. If the bytes begin with a UTF-16 BOM they are decoded
as UTF-16, otherwise as UTF-8. Nero titles are a 1-byte length followed by UTF-8. Undecodable bytes
produce a replacement-character string rather than an error — a mangled title is better than a
failed book.

### D8: Loop and recursion safety

Box iteration advances by the declared box size; a size smaller than its own header, a size that
would advance past the parent's end, or a zero-length advance terminates that level rather than
looping. Descent depth is capped.

*Why:* a truncated or crafted file must terminate. This is the difference between "never crashes
because metadata is malformed" (PRD §22) and an app that hangs on a bad download.

## Risks / Trade-offs

**No real chaptered `.m4b` to test against** → Synthetic fixtures constructed in the test source
cover the encodings, and they are more thorough than one real file would be (they can express the
malformed and 64-bit cases a real file never will). The residual risk is a real-world quirk no
fixture anticipates; it is retired when a genuinely chaptered file is run through the parser.
Mitigated in the meantime by verifying against the seven real files that the unchaptered path is
correct on actual bytes.

**SAF providers that do not support seeking** → Detected and reported as `Unreadable`. Whether the
app then degrades to "no chapters" or surfaces an error is the caller's decision, made in the
change that wires this in.

**Hand-rolled binary parsing is the kind of thing PRD §28 rule 4 warns about** → Accepted
deliberately and already sanctioned by `config.yaml`: no maintained Android library exposes MP4
chapters without dragging in far more than this needs. The mitigation is containment — one entry
point, no dependencies, thorough tests — so replacing it later is local.

**The `Chapter` shape gets fixed before its consumers exist** → That is the point of sequencing it
first, but it does mean `BookTimeline` may want a field this change did not anticipate. Adding one
later is cheap; the risk is low and the alternative (build the parser last, discover its output
does not fit) is worse.

## Migration Plan

Not applicable. Nothing calls the parser when this change lands, so there is nothing to migrate and
nothing to roll back beyond deleting the new files.

## Open Questions

- **Tolerance for "starts at zero" in D3.** A mark at 0 ms is the common case, but some encoders
  write a small non-zero offset. Proposing a tolerance of 1000 ms, resolved during implementation
  against the synthetic fixtures; the exact value has no bearing on real behavior.
- **Whether `Unreadable` should distinguish "not an MP4 at all" from "MP4 but chapter data is
  broken."** Currently one case with a reason string. Splitting it only matters once a caller shows
  different messages, which is a later change's decision.
