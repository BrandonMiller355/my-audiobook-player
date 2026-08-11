## 1. Test scaffolding first

The parser is pure logic with no app wiring, so the fixtures come before the code — they are the
only way to run it. All of this lives in `app/src/test/`; nothing test-only goes into `main`.

- [x] 1.1 Write an in-memory `SeekableByteChannel` over a `ByteArray` inline in the test source, so the parser can be driven without a file on disk (design D1)
- [x] 1.2 Write a small MP4 box builder in the test source: nested boxes with correct sizes, 32-bit and 64-bit forms, and the ability to place `moov` before or after `mdat`
- [x] 1.3 Build a fixture with a Nero `chpl` atom carrying three marks
- [x] 1.4 Build a fixture with a QuickTime chapter `text` track carrying four samples, including its `stts`/`stsz`/`stsc`/`stco` tables and the sample payloads in `mdat`
- [x] 1.5 Build the degenerate fixture that mirrors the owner's real files: one chapter sample at t=0 spanning the full `mvhd` duration
- [x] 1.6 Build the adversarial fixtures: truncated mid-box, a box declaring a size smaller than its header, a sample table declaring a count far larger than its payload, and a non-MP4 byte stream

## 2. Box walking

- [x] 2.1 Implement the box header reader: 32-bit size, `size == 1` 64-bit extended size, `size == 0` to-end-of-parent, with the loop-safety rules from design D8
- [x] 2.2 Implement selective descent into `moov`, `udta`, `trak`, `mdia`, `minf`, `stbl`, and `tref`
  - `meta` was dropped from the list: nothing chapters need lives inside it, so the walker never descends there and its version/flags quirk never arises (design D5, revised). Depth is bounded by construction — descent follows fixed paths rather than recursing.
- [x] 2.3 Read `mvhd` for the movie timescale and duration, handling both version 0 and version 1 field widths
- [x] 2.4 Verify against fixtures that walking never reads `mdat` and never expands the audio track's sample tables (design D5)

## 3. Nero `chpl` reader

- [x] 3.1 Parse `moov/udta/chpl`: version/flags, reserved field, entry count, then per entry a 64-bit 100-nanosecond timestamp and a 1-byte-length UTF-8 title
- [x] 3.2 Convert timestamps to milliseconds and stop cleanly when the declared count exceeds the bytes present
- [x] 3.3 Verify against the 1.3 fixture and the over-declared-count case

## 4. QuickTime chapter track reader

- [x] 4.1 Resolve `tref/chap` track IDs and select the referenced track whose `hdlr` handler is `text`, ignoring the `vide` track that real files also reference for chapter thumbnails
- [x] 4.2 Expand `stts` into cumulative sample start times and scale by the track's `mdhd` timescale
- [x] 4.3 Resolve sample index to file offset through `stsc`, `stco`/`co64`, and `stsz`, supporting both 32-bit and 64-bit chunk offsets
- [x] 4.4 Read each sample payload as a 2-byte big-endian length followed by text, decoding UTF-16 when a BOM is present and UTF-8 otherwise, ignoring any trailing encoding atoms (design D7)
- [x] 4.5 Verify against the 1.4 fixture, including the `co64` variant

## 5. Result assembly

- [x] 5.1 Define the public `Chapter` type (index, title, start, end) and the `ChapterParseResult` sealed type with its `Chapters` / `Unchaptered` / `Unreadable` cases (design D6)
- [x] 5.2 Implement source selection: prefer the QuickTime track when it yields two or more chapters, otherwise fall back to `chpl` (design D2)
- [x] 5.3 Sort marks by start time, drop duplicates, out-of-order entries, and marks beyond the movie duration, then chain end times with the last chapter ending at the movie duration (design D4)
- [x] 5.4 Apply the whole-file-single-chapter rule and return `Unchaptered`, deciding it structurally rather than by title comparison; settle the start-offset tolerance against the fixtures (design D3, open question)
- [x] 5.5 Wrap the entry point so every internal failure — including a non-seekable channel — becomes `Unreadable` with a reason, and confirm no fixture causes an exception to escape

## 6. Verification against real files

The owner's `.m4b` files are the only real-world input available, and they exercise only the
unchaptered path. That is still worth confirming on actual bytes rather than only on synthetic
fixtures — a fixture proves the rule, the real file proves the rule fires on files that exist.

- [x] 6.1 Run the parser once against `H:\eBooks\Mistborn\Brandon Sanderson - Mistborn Series - (Macmillan Audio)\Brandon Sanderson - Mistborn 03 - The Hero of Ages, Part 1.m4b` via a scratch harness and confirm it returns `Unchaptered`
- [x] 6.2 Repeat across all seven files in that folder and confirm all seven return `Unchaptered` with no `Unreadable`
- [x] 6.3 Record the outcome in the change, and do **not** commit an environment-dependent test that depends on `H:\` existing
  - **Outcome:** all seven files return `Unchaptered`, each in under 1 ms despite being 310–420 MB — which independently confirms the metadata-only reading, since touching the payload could not be that fast.
  - Because a single mark always collapses to `Unchaptered`, that result alone cannot distinguish "found the mark" from "found nothing". So the real file's chapter track was patched in flight (stts sample count 1→2, delta→1000 ticks, stsz count 1→2, stsc samples-per-chunk 1→2) and re-parsed: it returned two chapters with the first titled `Brandon Sanderson - Mistborn 03 - The Hero of Ages`, read from real bytes in `mdat` at an offset computed through the sample tables. That is end-to-end proof the QuickTime path works on real data.
  - The scratch harness was deleted rather than committed.
- [ ] 6.4 **Owner action, not blocking:** if a genuinely chaptered `.m4b` turns up, run it through the same harness — it is the one thing the synthetic fixtures cannot fully stand in for

## 7. Final checks

- [x] 7.1 Run `gradlew testDebugUnitTest` and confirm the full suite passes
- [x] 7.2 Confirm no new dependency appeared in `app/build.gradle.kts` or the version catalog, and that `gradlew assembleDebug` still produces an APK
- [x] 7.3 Re-run the merged-manifest no-network check from the README — it should be unchanged, since this change adds nothing to the manifest
