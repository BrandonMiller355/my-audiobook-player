## Context

The app is complete against its MVP: folder books and `.m4b` books both play, the library and
Player were rebuilt to the Arm's Length design, and the schema is at Room version 3. This change
adds a reader beside the player, at the manual tier — two independent positions in the same book,
no attempt at synchronization.

The constraints that shape every decision below:

- **PRD §24 / §28.13** — no network, minimal dependencies. The build already fails if a dependency
  contributes `INTERNET`, via `verify<Variant>Permissions` in `app/build.gradle.kts`.
- **PRD §16** — file access is a persistable SAF grant; source files are never copied or modified.
- **PRD §21** — readability first, no decorative UI, dark mode and system theme support.
- **PRD §23** — open quickly, do not load more than needed.
- **PRD §22** — degrade gracefully when a file is gone or a grant is lost; never crash on malformed
  metadata.
- **Codebase precedent** — `Mp4Reader`/`M4bChapterParser` hand-parse MP4 atoms rather than take a
  media library; `ui/Icons.kt` draws its own icons rather than add `material-icons-extended`. Small
  hand-rolled parsers with fixture tests are the established answer here, not the exception.

## Goals / Non-Goals

**Goals**

- Read a linked EPUB on a pure-black page without leaving the app or stopping the audio.
- Return to the exact place you stopped reading, across restarts and across font changes.
- Navigate a 150,000-word book in seconds, not by scrolling.
- Add no dependency, no permission, and no manifest entry.
- Leave the playback layer completely untouched.

**Non-Goals**

The proposal's Non-goals section is the authority. The two worth restating here because they
constrain the architecture rather than just the feature set:

- **No alignment data of any kind** is stored or computed. The position model below is nonetheless
  chosen so that a future alignment map could anchor to it — that is free today and a migration
  later — but nothing in this change produces one.
- **No EPUB fidelity beyond the prose subset** in D2. This is a known, accepted limitation, not a
  gap to be closed incrementally without a decision.

## Decisions

### D1: Render by hand into Compose text, not through a WebView

An EPUB is a ZIP of XHTML. Both halves have standard-library support — `java.util.zip` and
`XmlPullParser` — so the parse costs no dependency. The rendering choice is the real fork.

| | WebView | Hand-rolled → Compose |
|---|---|---|
| Fidelity | full: CSS, images, tables, footnotes | prose subset only (D2) |
| Black background / fonts | injected CSS | native `TextStyle` |
| Scroll position | JS bridge, and mapping a DOM position back to a character offset is genuinely awkward | `LazyListState`, and the position model falls straight out |
| Dependency | a browser engine inside an offline-only app | none |
| Testability | needs an instrumented test or a Robolectric shadow | plain JUnit over fixture ZIPs |

**Chosen: hand-rolled.** The deciding factor is not fidelity — WebView wins there — it is that the
position model (D3) is the part of this feature most expensive to get wrong later, and the WebView
path makes it a JS-bridge problem while the Compose path makes it an array index. The secondary
factor is that a browser engine is the largest thing that could be added to an app whose defining
constraint is that it does not touch the network.

The cost is accepted openly: some EPUBs will look wrong. See R1.

### D2: The rendered subset is prose, explicitly enumerated

Rendered: `<p>`, `<h1>`–`<h6>`, `<em>`/`<i>`, `<strong>`/`<b>`, `<u>`, `<blockquote>`, `<br>`,
`<hr>`, `<ul>`/`<ol>`/`<li>`, and `<div>`/`<section>` as transparent containers. Everything else
contributes its text content and drops its formatting; `<script>`, `<style>`, and `<head>` are
skipped whole.

Not rendered: images, tables (text only, no grid), footnote popups, publisher CSS, right-to-left
and vertical writing modes.

Whitespace within a block is collapsed to single spaces. This is not cosmetic: the owner's EPUBs are
hard-wrapped mid-sentence in the source, so preserving source whitespace would break prose at the
wrong points on every paragraph.

Inline emphasis nests — `<strong><b>x</b></strong>` occurs in the owner's files — so emphasis is
accumulated down the tree rather than assigned per element.

Enumerating the subset in the spec rather than saying "common HTML" is what makes the limitation
testable and makes a bug report distinguishable from a known gap. `<u>` was added to the list after
the spike found it in the owner's books; it costs one branch.

### D3: Reading position is `(spineIndex, charOffset)`, never a pixel offset

A scroll offset in pixels is invalidated by every font size change, typeface change, line-spacing
change, and rotation — which is to say by the settings this same change is adding. A spine index
plus a character offset into that spine item's plain text is stable under all of them.

It is also, not coincidentally, the anchor shape a Tier 2 or Tier 4 alignment map would need. The
handoff (§6) flagged this as the third accumulating cheap-now/expensive-later decision in this
project. Making it now costs nothing; making it later is a schema migration plus a re-derivation of
every saved position.

Restoring resolves the pair to a block index and calls `scrollToItem`, landing the user at the top
of the block containing the offset. Saving happens when scrolling settles, not on every frame.

### D4: The link and the position are columns on `audiobooks`, not a new table

One audiobook has at most one ebook (proposal Non-goals), which makes a join table three files of
ceremony for a one-to-one relationship. `MIGRATION_3_4` adds `ebookUri`, `ebookSpineIndex`, and
`ebookCharOffset`, all nullable, in the same additive shape as `MIGRATION_1_2` and `MIGRATION_2_3`.
An existing row keeps all three null, which is exactly the "no ebook linked" state the icon already
has to render.

Rejected: an `ebooks` table keyed by audiobook id. It buys the ability to link several ebooks to
one book, which is an explicit non-goal, and costs a join on a query that runs on every library
emission.

### D5: The reader is a navigation route, not a second face of `PlayerScreen`

`Routes.READER = "reader/{bookId}"`, alongside `LIBRARY` and `PLAYER`.

The "flip" language suggests a toggle inside `PlayerScreen`, but a toggle means hardware back from
the reader exits to the Library, skipping the Player — wrong, and not fixable without intercepting
back, at which point the route was cheaper. A route also keeps `PlayerScreen.kt` from absorbing a
second screen's worth of state; it is already 690 lines.

The reader talks to `PlaybackService` through the same `MediaController` connection `PlayerViewModel`
uses, and needs nothing else from the Player.

### D6: The reader forces true black, ignoring the system theme

`#000000` background, white text, in light mode and dark mode alike. This is what the owner asked
for and it is what an OLED panel rewards, but it means the reader is the one screen that does not
answer to `AudiobooksTheme`.

Implemented as reader-local constants rather than by adding roles to `AudiobookColors`. The theme's
contract, stated in `Theme.kt`, is that light and dark differ only in palette while layout stays
identical; a screen that is the same in both is outside that contract, and threading it through
would make `AudiobookColors` describe something it does not govern.

Note that `SurfaceDark` is `0xFF131211`, a warm near-black — the reader is deliberately blacker than
the app's own dark theme. Status-bar and navigation-bar appearance are set on entry and restored on
exit, the same `DisposableEffect` shape `LightStatusBarIcons` already uses in `PlayerScreen.kt`.

### D7: Chrome is revealed by a center tap and auto-hides

A tap in the middle band of the page fades the chrome in; it fades out after a few seconds or on
the next tap. It carries flip-back, table of contents, brightness, font settings, play/pause, and
change/unlink ebook.

Rejected: a fixed top bar, which contradicts the point of a full-black reading page; and a
permanently visible corner button, which is quieter but has nowhere to put the other five controls.
Since brightness, fonts, and the table of contents all ship in this change, they need a home, and
one revealed layer holding all of them is why tap-to-reveal wins rather than merely being prettier.

The chrome is revealed on first entry to the reader so the gesture is discoverable — otherwise a
user who does not know to tap has a black page and no way out.

### D8: The flip never touches playback, and play/pause lives in the chrome

Entering the reader does not pause. Leaving it does not resume. This is what the owner specified,
and it is the reason play/pause is in the chrome: with audio continuing, the alternative is flipping
back to the Player to pause and flipping in again, and a control that exists solely to undo the
absence of another control is the wrong shape.

**Flagged as the one place this change is wider than what was literally asked for.** The play/pause
control was not requested; it follows from "audio keeps playing" plus "there is a chrome layer." It
is a single control reusing `PlayPauseButton` and the existing `MediaController`, and it is cheap to
cut if unwanted.

### D9: Brightness is the real window brightness, not a dimming overlay

`WindowManager.LayoutParams.screenBrightness` on the reader's window — no permission, applies to
this app only, restored to `BRIGHTNESS_OVERRIDE_NONE` on exit so the rest of the app and the system
are unaffected.

Rejected: a translucent black scrim over the content, the usual trick. On a page that is already
pure black it dims only the text, which lowers contrast rather than lowering light output — the
opposite of what a night reader wants.

### D10: Reading preferences are app-wide; brightness is app-wide too

Text size, line spacing, typeface, and brightness are properties of the reader and the room it is
read in, not of a book. Stored in DataStore beside the existing global speed preference, following
`SpeedPreferences.kt`.

Only the reading *position* is per book, and that is on the book row (D4).

### D11: The whole book is parsed on open, into one flat list of blocks

A 150,000-word book is roughly 1 MB of text. Parsing every spine item at open, off the main thread
behind a loading state, produces a flat list of blocks each carrying its spine index and its
character offset within that spine item. One `LazyColumn` renders the list; `AnnotatedString`s are
built per item, so only visible blocks are materialized.

This is what makes continuous scroll actually continuous, makes a table-of-contents jump a
`scrollToItem`, and makes position restoration a lookup. The alternative — loading one spine item at
a time and appending or prepending neighbors — bounds memory that was never a problem and
introduces scroll-jump on prepend.

Parsed results are not cached to disk. PRD §23 says do not rescan every launch, and this is
knowingly on the edge of that: the mitigation is that the parse is under a second and behind a
loading state, and adding a cache later is additive. Recorded so the omission is visible.

### D12: DRM is detected and refused, not worked around

An EPUB with `META-INF/encryption.xml` is encrypted; rendering it produces gibberish. Detect it
during the container read and show "This ebook is protected and cannot be opened." DRM is a PRD §3
non-goal, so refusing is the correct behavior rather than a limitation, and it costs a file-presence
check.

### D13: The picker's MIME filter stays loose, and the extension decides

`.epub` is reported as `application/epub+zip`, `application/zip`, or `application/octet-stream`
depending on the provider — the same lesson `OpenPersistableDocument.AUDIO_MIME_TYPES` already
records for `.m4b`. The filter admits all three plus `*/*` fallback behavior, and what is actually
accepted is decided by reading the file: a ZIP whose `mimetype` entry says `application/epub+zip`.

### D14: The screen is kept on while reading

Reading is minutes of no touch input against a screen timeout that is often 30 seconds.
`FLAG_KEEP_SCREEN_ON` for the reader's lifetime, cleared on exit — one flag, no permission.

### D15: One icon, two states; relink lives in the chrome

The icon on the Player's cover art shows an outlined book when nothing is linked and a filled one
when something is, so the user knows whether a tap opens a picker or a reader. It sits top-right,
mirroring the existing back button's 44dp disc on the same scrim.

Change-ebook and unlink live in the reader's chrome, not behind a long-press on the cover icon. A
long-press is invisible and would be the only one in the app; the reader is where you are when you
discover the ebook is wrong, and it is where you would look.

### D17: A lenient tag scanner, not `XmlPullParser`

Amended during implementation. The proposal and D1 both said `XmlPullParser`; the parser uses a
small hand-written tag scanner instead, for two reasons that only became concrete once the real
files were in hand.

**Real EPUB XHTML is not reliably well-formed XML.** `&nbsp;` is undefined without loading the XHTML
DTD and makes a conforming XML parser throw; unclosed `<br>`, `<img>`, and `<meta>` appear in
plenty of shipping books. The `ebook-reader` spec requires that malformed markup renders what it can
rather than failing, and building that on a parser whose contract is to reject malformed input means
fighting it.

**It keeps the parser off Android.** `XmlPullParser` on Android comes from `android.util.Xml`, so
every parser test would need Robolectric. A scanner over a `String` is plain Kotlin, so the whole
`ebook/` package tests under plain JUnit — which is what the tasks assumed and what the rest of the
non-UI code in this project does.

The same scanner reads `container.xml`, the OPF, and the NCX. Those three *are* well-formed by
specification, so a strict parser would be defensible for them, but a second parsing path earns its
keep only if it does something the first cannot.

Cost: roughly 150 lines of tokenizer, plus a named-entity table. Still no third-party dependency,
which was D1's point.

### D16: The Library row marks a linked ebook with a small glyph, and nothing more

Added after the owner overrode the proposal's non-goal (resolved question 2). The row shows the same
linked-state ebook glyph used on the Player cover, small, beside the existing metadata line — it is
an indicator, not a control. Tapping the row still opens the Player.

Rejected: making the glyph tappable to jump straight into the reader. It would put two tap targets
on a list row whose whole design is one large target, and the reader is one further tap away
regardless. Rejected also: showing anything for books with no ebook, which would mark the majority of
rows with an absence.

`LibraryBook` gains a boolean rather than the URI itself — the list needs to know *whether*, not
*which*, and carrying the URI through the query that runs on every library emission buys nothing.

## Risks / Trade-offs

**R1: Some EPUBs will render badly, and it will not be predictable which.** → The subset is
enumerated in D2 and in the spec, so a bad render is diagnosable as "uses something outside the
subset" rather than a mystery. Mitigation for the owner specifically: verify against the actual
Mistborn EPUBs during implementation, and if the subset proves insufficient for those files, that is
grounds to revisit D1 before the change lands, not after.

**R2: Character offsets shift if the parser's text extraction changes.** A whitespace-handling fix
in a later change moves every saved position in every book slightly. → Offsets resolve to the
containing block and scroll to its top, so a drift of a few characters lands on the same paragraph.
Only a change that adds or removes whole blocks would be visible, and that is a large enough change
to warrant a deliberate reset.

**R3: A very large single spine item** — some EPUBs put an entire book in one XHTML file — makes one
block list entry enormous and one `AnnotatedString` expensive. → Blocks are paragraphs, not spine
items, so a single-file EPUB produces many blocks like any other; the risk is the parse, not the
render, and the parse is already off the main thread.

**R4: Screen brightness not restored if the process dies in the reader.** → `screenBrightness` is a
window attribute, not a system setting; it dies with the window. Nothing to restore.

**R5: PRD §23's "open quickly" versus parsing on every reader open.** → Named in D11 rather than
mitigated. Sub-second behind a loading state, and a disk cache is additive if it proves annoying.

**R6: This is a fourth screen against a PRD that specifies three.** → Recorded as an explicit
amendment in the proposal. The owner requested it, which is the exception PRD §3 names.

## Migration Plan

Room 3 → 4 via `MIGRATION_3_4`, three additive nullable columns on `audiobooks`. No backfill: an
existing book has no ebook, which is the null state the UI already renders as "link one."
`fallbackToDestructiveMigration` remains unused, per `add-folder-audiobooks` design D7. The exported
schema gains `app/schemas/…/4.json`, committed alongside.

Rollback is a downgrade problem only, and there is no distribution channel to roll back through —
the app is sideloaded. Reverting the change before release means reverting the migration and the
version bump together.

## Resolved Questions

All three were open when this design was written and were answered before implementation began.

1. **Do the owner's actual EPUBs render acceptably under the D2 subset?** — **Yes.** Verified
   against `The Hero of Ages.epub` from the owner's Mistborn set; see "Spike findings" below. D1
   holds.
2. **Should the Library row show that a book has an ebook linked?** — **Yes.** This was a stated
   non-goal in the proposal; the owner overrode it. The proposal, the `audiobook-library` spec, and
   the tasks were amended accordingly. See D16.
3. **Is a disk cache of the parsed book worth adding?** — **No, not now.** Parse on every open, as
   D11 describes. R5 stands as a recorded, accepted cost.

## Spike findings

From `H:\eBooks\Mistborn\…\Brandon Sanderson - [Mistborn 03] - The Hero of Ages.epub`, 730 KB, 104
ZIP entries. This is a Calibre-produced EPUB 2, which is almost certainly true of the whole set, so
these findings should be read as describing the owner's library rather than one file.

**The subset holds.** Element counts across all 96 spine documents:

```
covered by D2   p 7733  em 2000  br 275  strong 179  b 106  h2 84  h1 10  div 9  i 3  h3 2
text-only       a 306   span 153
degraded        td 75   tr 15   col 5   table 1     ← one table, the back-matter metals reference
                u 4                                  ← added to the subset, see D2
                img 1                                 ← the cover, not rendered inline
absent          ul  ol  li  blockquote  hr
```

Everything the book uses for prose is covered. The single real degradation is one reference table at
the back rendering as lines of text rather than a grid.

**Six things this changed:**

1. **There is no EPUB 3 navigation document — only `toc.ncx`.** The NCX path described in D4 as a
   fallback is the *primary* path for this library. It must be built first and tested properly, not
   treated as a legacy afterthought.
2. **The NCX is nested.** `PART ONE` is a `navPoint` containing chapter `navPoint`s. The table of
   contents therefore carries a depth, not a flat list, and the sheet indents by it.
3. **NCX targets carry fragments** — `…_split_002.html#calibre_toc_2`. Honoring them means recording
   `id` attributes during the XHTML walk and mapping fragment id to block index, so a table-of-
   contents jump lands on the right block rather than the top of the file.
4. **Spine `idref`s are descending while hrefs ascend** — `html95` is `_split_000.html`, `html94` is
   `_split_001.html`. Reading order comes from spine sequence with an `idref` → manifest `href`
   lookup. Anything that sorts by manifest id renders the book backwards, and would do so
   convincingly enough to survive a quick look.
5. **Paragraph text is hard-wrapped mid-sentence** in the source. Whitespace inside a block must be
   collapsed to single spaces or the rendered prose breaks in the wrong places. Now explicit in D2.
6. **Files are `.html`, not `.xhtml`**, with media type `application/xhtml+xml`. Spine membership
   decides what is content; the extension does not.

Also noted and deliberately ignored: `dc:language` is `UND`, so nothing may depend on it; and
`META-INF/calibre_bookmarks.txt` is Calibre's own state, not part of the EPUB.

**Measured once the parser existed**, against the same file:

```
parse time      140 ms          ← R5's "sub-second" assumption holds with room to spare
blocks          7,640           95 of 96 spine documents produce blocks
                                (the titlepage is one image, so it produces none)
text            1.36 M chars    ← D11's "roughly a megabyte" estimate
table of contents  96 entries at depths 0 / 1 / 2  (10 / 84 / 2)
emphasis        2,190 runs
position round-trip over all 7,640 blocks: 0 mismatches
rendered text containing stray markup, raw entities, or doubled spaces: 0
```

7. **The NCX's order disagrees with the spine's, once, in the back matter** — it lists "NAMES AND
   TERMS" (spine 92) before "EPILOGUE" (spine 91). Both entries jump correctly; only their order in
   the list is odd. Left as the book declares it rather than sorted by position: the NCX is the
   publisher's stated structure, and sorting by block index would risk separating a parent entry
   from the children nested under it in books where that nesting carries meaning. Faithful beats
   tidy.

## Open Questions

None outstanding.
