## Context

`ReaderText` renders the book as a `LazyColumn` of one `Text` per parsed block, inside a root `Box`
that owns two things: a `detectTapGestures` that toggles the chrome, and a `nestedScroll` gate
(`userScrolled`) that tells the read-along glide when the user's own finger is driving the list.
Nothing in that tree is selectable.

Three facts about the surrounding code shape every decision below.

1. **The glide runs every frame while read-along is active** (`ReaderScreen.kt:213`), calling
   `listState.scrollBy` toward the narrated position. It runs while paused too, but its target is
   then static and the existing `> 0.01f` threshold idles it. A paused page is therefore still, which
   is what makes a paused page safe to select on.
2. **Compose does not expose selection state.** `SelectionContainer(modifier, content)` is public;
   the overload carrying `selection: Selection?` and `onSelectionChange` is `internal`. An app can
   turn selection on and can add items to the selection toolbar, but cannot ask what is selected.
3. **Compose documents the lazy-layout limit itself.** `SelectionContainer`'s KDoc: use within a lazy
   layout "has undefined behavior on text items that aren't composed… texts that aren't composed will
   not be included in copy operations and select all will not expand the selection to include them."
   This is a documented boundary, not a bug to engineer around.

## Goals / Non-Goals

**Goals:**

- Selecting and copying a passage while paused, with the existing tap-to-reveal gesture intact.
- Keeping a passage as an ordinary book-notes record, anchored at the passage.
- No new dependency, no new permission, no change to the glide.

**Non-Goals:**

- Persistent highlights, selection across uncomposed text, a custom selection implementation, or any
  change to selection behavior outside the reader. See the proposal's Non-goals.

## Decisions

### D1 — The gate is structural: `SelectionContainer` exists only while paused

`ReaderText` is wrapped in a `SelectionContainer` when `state.isPlaying` is false, and wrapped in
nothing when it is true.

What the gate is for is worth stating precisely, because it is easy to assume it prevents a gesture
collision and it does not. Compose resolves scroll against selection by the long-press threshold:
a pointer that moves before it goes to the scrollable, one that stays still past it starts a
selection. That split holds on its own, so scrolling is safe either way. The gate exists because of
motion — while the narration plays the glide moves the list every frame, and a selection anchored to
text that is sliding leaves the handles chasing their own content.

Making the gate structural rather than behavioral is what makes it cheap and total. There is no
"selection enabled" flag to consult in a gesture handler, no half-state where a selection survives
into playback: when playback starts, the container leaves the composition and takes the selection
with it, which is exactly the spec's "starting playback clears the selection" without code to do it.

`listState` is hoisted above this, so the scroll position survives the container appearing and
disappearing. Play/pause is the only thing that triggers it, so the recomposition cost is paid at
most once per transport change — not per frame, and never during a scroll.

*Alternative considered:* `DisableSelection` around the content while playing, with a permanent
`SelectionContainer` outside. Rejected as strictly worse — it keeps a selection manager alive to do
nothing, and `DisableSelection` toggling still recomposes the subtree, so it buys no stability while
adding a second concept.

### D2 — Save as note is deferred, and here is what the spike found

**Superseded during implementation.** This section planned a pass-through `TextContextMenuProvider`
that would record the live `TextContextMenuDataProvider`, let an appended **Save as note** item invoke
the built-in Copy component, and read the passage back off the clipboard. The spike (task 1.4) found
the first step impossible, the owner chose to defer the feature rather than pay for the alternative,
and the findings are kept here so the follow-up proposal does not have to rediscover them.

**What works.** `Modifier.appendTextContextMenuComponents { item(key, label) { … } }` is public and
stable in foundation 1.11.4, and the new context-menu path it belongs to is on by default
(`ComposeFoundationFlags.isNewContextMenuEnabled = true`). Without that flag the old `TextToolbar`
path runs instead, which has no extension point at all — worth re-checking on any Compose upgrade.

**What does not.** The pass-through cannot be positioned. `SelectionContainer` calls
`ContextMenuArea`, which calls `ProvideDefaultPlatformTextContextMenuProviders`, which installs the
platform toolbar provider *and* applies the manager's `contextMenuAreaModifier` — the node that
requests the toolbar — to a `Box` inside its own `CompositionLocalProvider`. So the requester resolves
the local at a level above anything we can override from within the container, and below anything we
provide from outside it. There is no seam.

Providing our own provider *above* `SelectionContainer` does take effect, because
`ProvideDefaultPlatformTextContextMenuProviders` explicitly stands aside when the local is already
non-null. But that leaves nothing to delegate to: `platformTextContextMenuToolbarProvider` is
internal. **Any custom toolbar item therefore costs a hand-rendered toolbar** — our own popup,
positioned from `TextContextMenuDataProvider.contentBounds`, rendering `data().components` ourselves,
including the platform's Share and `PROCESS_TEXT` entries or losing them.

**The clipboard retrieval itself is sound,** and is worth keeping for the follow-up. `Clipboard`'s
`setClipEntry` and `getClipEntry` are `suspend` in signature but synchronous in the Android
implementation, and `SelectionManager`'s copy handler launches `UNDISPATCHED`, so the passage is on
the clipboard by the time Copy's `onClick` returns. There is no race to guard against — only the side
effect that saving would also overwrite the clipboard.

*Alternatives considered and still open to the follow-up:*

- **An `ACTION_PROCESS_TEXT` activity.** The platform hands the selected text straight to the app, no
  clipboard and no internal APIs. Rejected for this app specifically: a `PROCESS_TEXT` activity
  appears in the selection toolbar of *every* app on the device, which is far outside what a personal,
  local-only audiobook player should install itself into.
- **Implementing selection ourselves** over each block's `TextLayoutResult`, which would give the
  range directly. Rejected: that is the gesture handling, hit testing, handle rendering, and magnifier
  that `SelectionContainer` already is, rewritten for one menu item.
- **A reader overflow action** that saves whatever the user has just copied. Cheap, two extra taps,
  and it stores the clipboard rather than the selection.

### D3 — The chrome tap keeps working, and verification comes before the rest of the build

The root `Box`'s `detectTapGestures` sits *above* the `SelectionContainer` in the tree, and Compose
delivers pointer events to children first. If the selection machinery consumes taps within its bounds
— it consumes the tap that clears a live selection, which is what the spec wants — the open question
is whether it also consumes taps when *nothing* is selected. If it does, tap-to-reveal breaks for
every paused reader, which is a regression against an existing requirement and much worse than the
feature is good.

**Verified on device, and the answer was the good one — with a twist.** Taps are *never* consumed by
the selection machinery. Tap-to-reveal keeps working while paused, so the regression this was guarding
against did not happen and the fallback placement was not needed.

The twist is that the same fact breaks the other half. Because the clearing tap is not consumed
either, it reaches the chrome toggle exactly like any other tap: dismissing a selection clears it
*and* reveals the controls. The spec originally said it must not, and that scenario has been rewritten
to match reality rather than left as an aspiration.

There is no cheap fix, which is why this is accepted rather than worked around. Suppressing that one
toggle requires knowing a selection is live, and fact 2 stands: Compose does not expose it. The only
hook that would reveal it — a custom `TextContextMenuProvider`, whose `showTextContextMenu` is called
precisely when a selection toolbar should appear — drags in the same hand-rendered-toolbar cost that
deferred Save as note in D2. Paying that to remove a chrome flash is not a trade worth making.

*Alternative considered:* inferring selection from gesture timing. Rejected as guesswork.

### D4 — The note is anchored at the passage, with no lead-in

**Built, then left waiting on D2's deferral.** The arithmetic is in `playback/NoteAnchor.kt` with
tests, because it was finished before the spike settled; nothing calls it yet. It is recorded here in
full so the follow-up inherits a decided design rather than an empty function.

Two paths, both two timeline calls, both reusing arithmetic that exists:

- **Read-along active:** the selection's start block → `book.absoluteCharsAt(blockIndex)` →
  `ReadAlongMap.msForChars(chars)` → an absolute ms → `BookTimeline.targetForAbsolute(absoluteMs)` →
  `PlayerTarget`, then `BookTimeline.locate(…)` for the chapter title. This is the same conversion
  `seekToTextPosition` already performs, read in the same direction, so the note lands where seeking
  to the passage would.
- **No read-along:** the current playback position, via the existing `noteAnchorFor` — identical to
  what the reader's overflow mark does today.

`NOTE_LEAD_IN_MS` is not applied on either path. `NoteAnchor.kt`'s own comment explains why the
lead-in exists: the tap happens some seconds after the passage registers, so "now" is the wrong
anchor. A selected passage has no such lag — the user pointed at the words. Rather than a second
copy of the anchoring logic, `noteAnchorFor` takes a `leadInMs` parameter defaulting to
`NOTE_LEAD_IN_MS`, so every existing call site reads exactly as it does now, and `noteAnchorAt`
handles the absolute case.

### D5 — Abandoning a passage entry keeps it

**Deferred with D2.** Recorded because the reasoning survives the deferral and the follow-up will
need it. `book-notes` today says abandoning a new entry discards it, "because it was never
confirmed." That reasoning does not reach a passage: selecting words and choosing Save as note *is*
the confirmation, and discarding on back-out would throw away something the user chose by hand. A
passage entry should therefore behave like an entry that already existed — backing out leaves it.

This is a real divergence in the notes screen's create-flow, not a detail, which is why it belongs in
a spec scenario rather than a footnote when it is built.

### D9 — The glide must be able to arrive, or Copy does not work

Reported from use as "the Copy button doesn't seem to do anything," on the emulator and on real
hardware. It had two causes, and the first one was mine.

**The glide could never settle.** It scrolled by `delta * GLIDE_GAIN` whenever that step exceeded a
hundredth of a pixel — a threshold below anything a screen can render. On a paused page, whose target
cannot move, it therefore spent every frame pushing a residual it had no way to close. Device logs
showed `delta=-0.23` repeating frame after frame unchanged, then flipping to `+0.77` as a whole pixel
finally landed, and back again, indefinitely. A paused, motionless reader rendered 118 frames in four
idle seconds, most of them janky, on the one screen in the app that also holds the display awake.

**That is what broke Copy.** Every one of those frames relaid the list out, and a relayout moves the
platform's selection toolbar. A toolbar that moves between a finger going down and coming up does not
register the press. Hence a Copy that did nothing, often enough to look broken and irregularly enough
to look haunted — it depended on where in the oscillation the tap landed.

The fix is to measure arrival in pixels rather than hundredths of one, and to stop asking for frames
once arrived and paused. Sub-pixel *steps* are still applied while there is real distance to cover,
which is what keeps the glide smooth rather than stepped; what changed is only the test for having
got there. Measured after: zero frames in four idle seconds, the glide unchanged while playing, and
Copy six times out of six where it had been failing about half the time.

The lesson worth keeping: a convergence threshold finer than the medium's own resolution is not a
tighter tolerance, it is a loop that cannot terminate.

### D8 — The reader confirms a copy itself

Separate from D9 and still worth having. Even when the copy lands, nothing says so. The system
normally answers with SystemUI's clipboard chip, and device logs show that overlay suppressed on this
screen — `ClipboardListener: Clipboard overlay suppressed`, every time — because the reader runs with
the system bars hidden. A copy that works but reports nothing still reads as a dead button.

The confirmation is a snackbar on the host the reader already has for pick errors, driven by
`ClipboardManager.OnPrimaryClipChangedListener`.

Listening to the clipboard is an odd-looking hook, and it is the only one available. Copy is the
platform's own toolbar item; D2 established we can neither add to that toolbar nor observe it without
hand-rendering it, and Compose exposes no selection state to watch instead. The listener needs no
permission and reads nothing — the callback carries no clip, only the fact that one arrived, which is
all the word "Copied" requires. It is registered for the reader's composition only, so it cannot
fire for clipboard traffic from anywhere else.

### D7 — The selection colors are reader-local, and amber

Found on device: with the defaults, the highlight was effectively invisible. Compose derives the
selection wash from the Material theme's primary, and this app's primary is a muted warm neutral, so
on a pure black page the wash composites to something very close to the page itself.

`LocalTextSelectionColors` is provided inside `SelectableWhilePaused`, alongside the container it
belongs to. The colors sit with `ReaderBackground` and `ReaderInk` rather than on `AudiobookColors`,
for the reason already recorded there: the reader is the one screen that is identical in both themes,
which puts it outside that palette's stated contract.

Amber rather than a white or grey wash. A neutral wash of sufficient strength greys the page toward
the white text sitting on it, which costs legibility exactly where it matters; a saturated hue stays
distinct from both the black page and the white ink. It also reads as a highlighter, which is what
the gesture means, and it agrees with the warmth of the rest of the palette.

The alpha is the balance point: strong enough that the band is unmistakable against pure black,
light enough that white text over four lines of it stays comfortable. Both were checked on device.

### D6 — `readalong-sync` is not touched

Stated so nobody goes looking. The glide idles while paused (see Context), a selection cannot exist
while playing (D1), and the two therefore never meet. No gate, no pause hook, no new state.

## Risks / Trade-offs

- **[The chrome tap is swallowed inside the selection container]** → Verified on device as the first
  task, before anything else is built, with the fallback placement in D3 already chosen. This is the
  one risk that could regress existing behavior rather than merely fail to add new behavior.
- **[A custom selection-toolbar item needs a hand-rendered toolbar]** → Materialized, and resolved by
  deferring Save as note (D2). The retrieval risk and the clipboard side effect went with it.
- **[Selection stops at the composed range]** → Accepted, specified, and Compose's own documented
  behavior. In practice a reader selects a sentence or a paragraph, which is always composed; the
  limit bites only on a multi-screen drag.
- **[Recomposing the whole list on play/pause]** → Bounded: `listState` is hoisted so position holds,
  and it happens once per transport change. If it proves visible on a large book, the cheaper
  `DisableSelection` variant rejected in D1 is still available.

## Open Questions

None blocking. Both verification items in Risks are resolved by the first task rather than by asking.
