## Why

The owner has EPUB copies of books already in the library and wants to read them on the same
device, in the same app, without the audio stopping — glancing at a name's spelling, re-reading a
passage that was missed, or switching from listening to reading for a while and back.

This is Tier 0 of the four tiers set out in `handoffs/2026-08-10-ebook-audio-readalong.md`: a
manual companion. The ebook and the audio are two independent positions in the same book, and
nothing attempts to keep them aligned. That is deliberate — every higher tier depends on forced
alignment, which the handoff established is a different project, and which is disproportionately
broken on this owner's library because all seven `.m4b` files are a single 10–14 hour chapter with
no anchors to re-sync against.

That handoff also said "do not start this feature," on the grounds that the playback layer did not
exist yet. It does now: `add-playback-service`, `add-transport-controls`, `add-m4b-books`, and
`redesign-player-and-library` are all archived and merged. The blocking condition is gone.

**This is a PRD amendment, not a PRD feature.** PRD §20 specifies three screens and no reader;
§28.2 says not to add features the PRD does not list. The owner has asked for it explicitly, which
is the exception §3 names ("unless explicitly requested later"). Recording it here so the departure
is deliberate rather than silent.

## What Changes

- **An audiobook can have one EPUB linked to it.** The owner picks it through the system file
  picker; the app takes a persistable read grant and stores the URI, never a copy of the file.
- **An icon on the Player's cover art carries both states.** With no ebook linked it opens the
  picker; with one linked it opens the reader. Its appearance says which.
- **A new Reader screen** renders the EPUB as continuously scrollable text on a pure-black
  background with white text, in both light and dark system themes.
- **The EPUB is parsed by hand** — `java.util.zip` plus `XmlPullParser` over the container, the OPF
  spine, and the navigation document, rendering a subset of XHTML into Compose text. No new
  dependency (see below).
- **Reading position is remembered per book** as a spine index and a character offset, so it
  survives font and size changes rather than being a pixel scroll offset that does not.
- **A table of contents** from the EPUB's own navigation document, reachable from the reader. A
  12-hour book is roughly 150,000 words; scrolling alone does not navigate it.
- **Chrome is revealed by a tap in the middle of the page** and hides again, Kindle-style. It
  carries: flip back to the Player, the table of contents, brightness, font settings, play/pause,
  and the controls to change or unlink the ebook.
- **Font settings**: text size, line spacing, and a serif/sans toggle, stored as app-wide
  preferences alongside the existing global speed preference.
- **Brightness**: the reader sets the window's screen brightness directly and restores the system
  value on exit.
- **Audio keeps playing across the flip.** Neither direction starts or stops playback on its own;
  the only thing that changes playback is the play/pause control in the revealed chrome.
- **The Library row marks books that have an ebook linked** with a small glyph — an indicator, not
  a control.
- **Failure is stated, not rendered.** A DRM-protected EPUB, a deleted file, or a lost URI grant
  each produce a readable message and an offer to link a different ebook.

### Non-goals

Deliberately excluded, and named so a later reader knows they were considered:

- **Any synchronization between text and audio.** No highlighting the narrated sentence, no
  scrolling with the narration, no "sync here" anchors, no chapter-to-chapter jumping between the
  two. Those are Tiers 1–4 in the handoff and each is a separate change.
- **Deriving audio chapters from the ebook's structure.** The side effect the handoff identified as
  possibly worth more than read-along itself. It requires alignment; it is not available at Tier 0.
- **MOBI, AZW3, and any other format.** Established in the handoff: modern Kindle formats ship
  DRM-protected, and DRM is a PRD §3 non-goal. EPUB only.
- **Stripping or working around EPUB DRM.** Detected and refused.
- **Full EPUB fidelity.** Embedded images, tables, footnote popups, publisher CSS, right-to-left
  text, and vertical writing modes are not rendered. See design.md for exactly what the subset
  covers and why.
- **Pagination.** The reader scrolls; it does not turn pages or estimate page numbers.
- **More than one ebook per audiobook**, and linking an ebook to anything other than an audiobook
  already in the library.
- **Bundled font families.** Serif and sans come from the system; no font files are added to the
  APK.
- **Linking or reading from the Library screen.** The Library only *indicates* that an ebook is
  linked; both linking and reading still start from the Player. (This was originally a blanket
  non-goal covering the indicator too — the owner overrode that part; design D16.)

### Dependencies

**No new third-party dependency is added.** An EPUB is a ZIP of XHTML, and both halves have
standard-library support on Android: `java.util.zip.ZipInputStream` and `XmlPullParser`. This
mirrors the precedent already set twice in this codebase — `Mp4Reader`/`M4bChapterParser` parse MP4
atoms by hand rather than take a media library, and `ui/Icons.kt` draws its own icon set rather
than pull `material-icons-extended`. The alternative considered and rejected was rendering through
a `WebView`; design.md records why.

### Permissions and manifest

**No new permission, no new foreground-service type, no manifest change.** File access is a
persistable SAF read grant, exactly as folder and `.m4b` books already work (PRD §16). The reader
is local-only and needs no network; the existing `verify<Variant>Permissions` Gradle task continues
to fail the build if any dependency contributes `INTERNET` or the storage permissions.

## Capabilities

### New Capabilities

- `ebook-linking`: Associating one EPUB with one audiobook — picking it, holding the grant across
  restarts, changing it, unlinking it, and behaving sanely when the file or the grant is gone.
- `ebook-reader`: The reading screen — parsing and rendering the EPUB, scrolling it, remembering
  the reading position, navigating by table of contents, the tap-to-reveal chrome, and the
  black-background reading appearance.
- `reading-preferences`: Text size, line spacing, typeface, and screen brightness — their storage,
  their scope, and their effect on the reader.

### Modified Capabilities

- `playback`: Adds a requirement that switching to and from the reader leaves playback untouched —
  neither entering nor leaving the reader starts, stops, or repositions audio.
- `audiobook-library`: Adds a requirement that a library row shows whether that book has an ebook
  linked.

## Impact

**Schema** — Room goes from version 3 to 4. `MIGRATION_3_4` adds columns to `audiobooks` for the
ebook URI and the reading position, additive and non-destructive in the same shape as
`MIGRATION_1_2` and `MIGRATION_2_3`. Existing rows keep them null and behave exactly as before. The
exported schema under `app/schemas` gains a version 4 file.

**New code** — an `ebook/` package holding the EPUB reader, the spine and navigation parsing, and
the XHTML-to-Compose conversion; a `ui/reader/` package holding the screen, its ViewModel, and the
chrome.

**Touched code** — `data/Entities.kt` and `data/Migrations.kt` for the schema; `data/LibraryDao.kt`
for reading and writing the link and the position, and for the library row's linked flag;
`ui/AudiobooksApp.kt` for the new route; `ui/player/PlayerScreen.kt` for the icon on the cover;
`ui/library/LibraryScreen.kt` for the row indicator; `ui/Icons.kt` for the icons it needs;
`ui/library/OpenPersistableDocument.kt` for the EPUB MIME filter; `data/SpeedPreferences.kt` (or a
sibling) for the reading preferences.

**Untouched** — everything under `playback/`. The reader does not talk to the player beyond the
play/pause control in its chrome, which goes through the same `MediaController` the Player already
uses. No change to `PlaybackService`, the media session, the notification, or Bluetooth handling.

**PRD** — §20 grows a fourth screen. Recorded above.
