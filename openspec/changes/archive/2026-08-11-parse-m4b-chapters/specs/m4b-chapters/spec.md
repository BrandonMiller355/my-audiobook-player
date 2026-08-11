## ADDED Requirements

### Requirement: Chapter extraction reports one of three outcomes

Parsing an `.m4b` SHALL produce exactly one of: an ordered chapter list, an explicit unchaptered
result, or an explicit unreadable result carrying a reason. Parsing SHALL NOT throw an exception
for any input, and SHALL NOT hang.

#### Scenario: File carries usable chapter marks

- **WHEN** a file containing two or more distinct chapter marks is parsed
- **THEN** an ordered chapter list is returned
- **AND** the chapters are sorted by start time ascending

#### Scenario: File carries no chapter data at all

- **WHEN** a file with no `chpl` atom and no chapter track reference is parsed
- **THEN** the unchaptered result is returned
- **AND** no error is reported, because absence of chapters is not a failure

#### Scenario: Input is not an MP4 file

- **WHEN** a byte stream that is not an MP4 container is parsed
- **THEN** the unreadable result is returned with a reason
- **AND** no exception propagates to the caller

#### Scenario: Input is truncated mid-structure

- **WHEN** a file whose box tree is cut off partway through is parsed
- **THEN** either a chapter list recovered from the intact portion or an unreadable result is returned
- **AND** no exception propagates and parsing terminates

#### Scenario: Byte source cannot seek

- **WHEN** the supplied byte source does not support seeking
- **THEN** the unreadable result is returned with a reason identifying the seek failure

### Requirement: Nero `chpl` chapters are understood

The parser SHALL read chapter marks from a Nero `chpl` atom located under `moov/udta`, converting
its 100-nanosecond timestamps to milliseconds and decoding its length-prefixed UTF-8 titles.

#### Scenario: File with only a `chpl` atom

- **WHEN** a file whose sole chapter data is a `chpl` atom listing three marks is parsed
- **THEN** three chapters are returned
- **AND** each chapter's start time matches its `chpl` timestamp converted to milliseconds
- **AND** each chapter's title matches the text stored in the atom

#### Scenario: `chpl` declares more entries than the atom contains

- **WHEN** a `chpl` atom declares an entry count larger than its remaining payload can hold
- **THEN** parsing stops at the last entry that is actually present rather than reading past the atom
- **AND** no exception propagates

### Requirement: QuickTime chapter tracks are understood

The parser SHALL read chapter marks from a QuickTime chapter track: a `tref/chap` reference
identifying a track whose handler is `text`, whose start times come from that track's time-to-sample
table scaled by its media timescale, and whose titles come from the track's sample payloads located
through its sample-to-chunk and chunk-offset tables.

#### Scenario: File with only a QuickTime chapter track

- **WHEN** a file whose sole chapter data is a `text` chapter track with four samples is parsed
- **THEN** four chapters are returned
- **AND** each start time equals the sample's cumulative duration converted from the track timescale to milliseconds
- **AND** each title equals the sample's length-prefixed text

#### Scenario: Chapter track reference points at a non-text track

- **WHEN** a `tref/chap` reference names a track whose handler is not `text`
- **THEN** that track is not treated as a chapter source

#### Scenario: Chunk offsets are stored as 64-bit values

- **WHEN** the chapter track stores chunk offsets in a `co64` box rather than `stco`
- **THEN** the sample payloads are still located correctly and titles are returned

### Requirement: A single chapter spanning the whole file counts as unchaptered

The parser SHALL report a file as unchaptered when chapter extraction yields exactly one mark that
begins at or near the start of the file and extends to the end of it, rather than returning a
single-chapter list. This SHALL be determined from the mark's position and span, NOT by comparing
its title against the book or file name.

#### Scenario: Chapter track holding only the book title

- **WHEN** a file whose chapter track contains exactly one sample at time zero, spanning the full duration, is parsed
- **THEN** the unchaptered result is returned
- **AND** the result does not depend on what text that sample contains

#### Scenario: Single whole-file mark with a small non-zero offset

- **WHEN** the sole chapter mark starts slightly after zero but still spans essentially the whole file
- **THEN** the unchaptered result is returned

#### Scenario: Two genuine chapters are not collapsed

- **WHEN** a file contains two chapter marks, the first at the start and the second partway through
- **THEN** a chapter list of two entries is returned rather than the unchaptered result

### Requirement: Chapter boundaries are derived from neighbours and total duration

Each chapter's end time SHALL be the start time of the following chapter, and the final chapter's
end SHALL be the total duration taken from `moov/mvhd`. Marks that cannot yield a positive duration
SHALL be discarded.

#### Scenario: Ends are chained from starts

- **WHEN** chapters starting at 0 ms, 60000 ms, and 120000 ms are parsed from a file of 200000 ms
- **THEN** their end times are 60000 ms, 120000 ms, and 200000 ms respectively

#### Scenario: Duplicate or out-of-order marks

- **WHEN** the chapter data contains two marks with the same start time, or marks listed out of order
- **THEN** the returned chapters are ordered by start time
- **AND** no returned chapter has a zero or negative duration

#### Scenario: Mark positioned beyond the end of the file

- **WHEN** a chapter mark starts later than the movie duration
- **THEN** that mark is discarded and the remaining chapters are returned

### Requirement: Parsing reads metadata only, never the audio payload

The parser SHALL locate chapter data by walking the box tree and SHALL NOT read the `mdat` audio
payload, expand the audio track's sample tables, or load the whole file into memory. Table
allocations SHALL be bounded by the bytes actually available rather than by declared counts.

#### Scenario: Large file with large audio sample tables

- **WHEN** a file whose audio track declares over a million samples is parsed
- **THEN** the audio track's sample tables are not expanded
- **AND** the bytes read are a small fraction of the file size

#### Scenario: `moov` positioned after the audio payload

- **WHEN** a file places `moov` after `mdat` rather than at the front
- **THEN** the chapters are still found

#### Scenario: Hostile entry count

- **WHEN** a sample table declares an entry count far exceeding the bytes remaining in its box
- **THEN** no allocation is made for the declared count
- **AND** the unreadable result is returned or the intact entries are used, without exhausting memory

#### Scenario: Box declaring a size smaller than its own header

- **WHEN** a box declares a size smaller than its header length
- **THEN** iteration at that level stops rather than looping indefinitely
