## Why

The reader renders the book's text but nothing in it can be selected: `ReaderText` has no
`SelectionContainer`, so a passage worth quoting cannot be copied, and the one thing a reader most
often wants to do with a sentence — keep it — has no path. The owner asked for it directly: long
press to highlight a passage and copy it.

Selection is not free on this screen, because the reader already spends its gestures. A tap reveals
the controls, a drag scrolls, and while the narration plays the page scrolls itself. The gesture
split itself is clean and standard: a finger that starts moving promptly scrolls, and a finger that
stays still past the long-press timeout selects. What is not free is selecting against a page that is
moving on its own, which is the case this change decides rather than discovers.

Text selection is not covered by the PRD. It follows the exception §3.1 and §3.2 already record for
the ebook companion and for notes: explicitly requested later, written into the PRD rather than left
standing outside it. It is also not the highlighting §3.1 rules out — that non-goal is the app
marking the sentence *being narrated*, which is forced alignment. This is the user dragging their own
finger across their own choice of words, and nothing derives it from the audio.

## What Changes

- The reader's text becomes selectable **while playback is paused**. Long press selects a word,
  handles extend the selection, and the system's selection toolbar offers Copy.
- **Scrolling is untouched, paused or playing.** Touching the page and dragging scrolls it, exactly
  as it does today. A drag that begins before the long-press timeout never starts a selection, so the
  ordinary way a reader moves the page is never intercepted.
- **While playback is playing, the text is not selectable.** Long press does nothing. The reason is
  not gesture ambiguity but motion: the page glides under the finger while the narration runs, so a
  word selected there slides out from under its own handles the moment it is caught. A selection is
  only offered against a page that is holding still. Starting playback clears any live selection.
- Narrow accepted consequence, stated rather than hidden: **while paused, resting a finger still on
  the text for the length of a long press and only then dragging will select rather than scroll.**
  That is ordinary Android behavior in every app that shows selectable text, and it does not affect
  touch-and-scroll, which is the gesture a reader actually uses to move the page.
- The tap that clears a selection also reveals the reader's controls. This was specified the other
  way round and reversed on device: Compose clears the selection without consuming the tap and offers
  no selection state to consult, so the reader has no way to tell that tap from any other. Recorded
  as behavior rather than left as a surprise.
- A stated limitation rather than a latent bug: a selection cannot extend past paragraphs that have
  scrolled out of the lazy list's composition, so a passage spanning several screens will not fully
  select.

## Capabilities

### New Capabilities

None. This extends one capability that already exists.

### Modified Capabilities

- `ebook-reader`: a new requirement for selecting, copying, and the paused gate; an amended scenario
  under "Reader controls are revealed by tapping and hide themselves" for the tap that clears a
  selection instead of toggling the controls.

`book-notes` is **no longer modified by this change.** Keeping a selected passage as a note was in
scope when this was written and was deferred during implementation — see "Deferred" below.

`readalong-sync` is deliberately **not** modified. The glide already idles while paused — its target
is static and the existing sub-pixel threshold suppresses the residue — so a selection never fights
it, and the gate is expressed once, in `ebook-reader`, rather than restated here.

## Impact

Affected code:

- `ui/reader/ReaderScreen.kt` — `ReaderText` gains a `SelectionContainer` conditional on the paused
  state; the root `Box`'s `detectTapGestures` (the chrome toggle) has to keep working inside it.
- `playback/NoteAnchor.kt` — a `leadInMs` parameter and an absolute-position anchoring path, built
  before the deferral and kept for it. Every existing call site reads unchanged.

Dependencies: **none added.** The selection machinery is Compose foundation, already on the classpath
(foundation 1.11.4 via the 2026.06.01 BOM).

Permissions, manifest, foreground-service types: **no change.** Copying uses the system clipboard,
which needs no permission, and nothing here touches storage, the network, or the media session.

PRD: §20.4 gains selection in the Reader screen's contents, and §3.1 gains a line separating
user-driven selection from the narration highlighting that remains out of scope.

## Deferred

**Keeping a selected passage as a note** was in this change's scope and came out of it during
implementation, by the owner's call. It is not abandoned — it needs its own proposal, because the
thing that stopped it is bigger than a task.

Compose will not let an app add an item to the selection toolbar without rendering that toolbar
itself. The platform's default provider is installed *inside* `SelectionContainer`, and the modifier
node that requests the toolbar sits at that same level, so there is no position in the tree that can
both see the default and be the one the requester resolves. Providing our own provider above
`SelectionContainer` does take effect — `ProvideDefaultPlatformTextContextMenuProviders` checks for a
non-null provider and stands aside — but leaves nothing to delegate to, since
`platformTextContextMenuToolbarProvider` is internal. A custom item therefore costs a hand-rendered
toolbar, including re-rendering the platform's own Share and `PROCESS_TEXT` entries or losing them.

That is a real piece of design work with its own trade-offs, and it does not belong bolted onto a
change about a gesture. The anchoring arithmetic it needs was already built and tested here and stays
in `playback/NoteAnchor.kt` for it.

## Non-goals

- **Persistent highlights.** A selection is transient. Nothing is stored that would re-tint the
  passage the next time the book is opened. Highlight-as-an-annotation is a second store, a second
  list, and a second thing to migrate, and it was not asked for.
- **Highlighting the narrated sentence or word.** Still out of scope per PRD §3.1. Unchanged.
- **Selection across the whole book.** Bounded by what the lazy list has composed, as above. No
  windowing scheme, no whole-book text buffer.
- **Share, Translate, Web search, or other system text actions** beyond what the platform's own
  toolbar already offers for selected text. Nothing is added, and nothing present is removed.
- **Selection anywhere else in the app.** The Player, the Library, and the Notes screen are
  unaffected.
- **Making the paused gate configurable.** There is no setting for it. One rule, always true.
