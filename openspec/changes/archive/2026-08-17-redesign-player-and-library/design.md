# Design

The handoff (`handoffs/2026-08-15-App redesign request/design_handoff_player_redesign/README.md`)
is the specification for what things look like, and it is detailed enough that most of this change
needs no design work at all: colors, sizes, weights, spacing, and radii are given as final values
and are simply transcribed. What follows are only the decisions the handoff leaves open, the
places where following it literally would have been wrong, and the one thing it assumes about the
database that is not true.

---

## D1 — Design tokens live in one `CompositionLocal`, not stretched over Material roles

**Decision.** Map the tokens that have a genuine Material role onto the `ColorScheme`
(`surface`, `onSurface`, `primary` = `Ink`, `surfaceVariant` = `Fill`, `outlineVariant` = `Track`,
`error`). Put the rest — `InkMuted`, `TextSecondary`, `TextTertiary`, `TextQuaternary`,
`ErrorTint`, the two cover placeholders, the scrims, the dark-only cover base fade — in an
`AudiobookColors` data class provided by `AudiobooksTheme` through a `CompositionLocal`.

**Why.** The design has four levels of text emphasis and Material 3 has two
(`onSurface`, `onSurfaceVariant`). The alternatives were both worse. Forcing the extra levels into
unrelated roles — `tertiary`, `outline`, `scrim` — makes every call site a lie about what the
color is for, and the next person to touch it has no way to know that `outline` means "mono
metadata". Referencing raw vals directly from composables works, but then the light and dark
values have to be chosen at every call site, which is exactly the thing that goes wrong once and
stays wrong.

A `CompositionLocal` keeps the theme as the single place light and dark diverge, which is what
makes the handoff's "layout is identical, only the palette changes" checkable rather than hoped
for. One class, one provider, no design-system module (the proposal's non-goals).

## D2 — Icons are drawn, not taken from the Material icon set

**Decision.** Draw the nine icon shapes on a `Canvas` in `ui/Icons.kt`: single and double
chevrons, plus, play triangle, pause bars, folder, document, warning, and the collapse chevron.

**Why.** The handoff says to use `androidx.compose.material.icons` and describes every icon as a
2dp stroke with round caps and joins. Those two instructions are incompatible. Material's default
icon set is filled glyphs — there is no stroke to set a width on — and the specific weight of the
strokes is what gives this design its character at a 40dp icon size. The handoff's own tiebreaker
is explicit: "Sizes and stroke weights above are what matters."

Availability decides it independently. `material-icons-core`, which `material3` brings in, has
`PlayArrow`, `Add`, `List`, `Warning`, and the single `KeyboardArrow*` chevrons — but not `Pause`,
`Folder`, `Description`, or the double chevrons. Those are in `material-icons-extended`, a large
artifact to add for four shapes, and `isMinifyEnabled = false` on release means its full weight
would ship. `config.yaml` requires justifying every new dependency; there is no justification for
that one when the shapes in question are two line segments each.

The scale is small and fixed: nine shapes, none more than four path operations, all built from
`drawLine` and `drawPath` against a 24dp reference grid so the handoff's `viewBox="0 0 24 24"`
coordinates transcribe directly. `contentDescription` is kept on every control, so this costs
nothing in accessibility.

**Rejected:** vector drawables in `res/drawable`. Same result, but the stroke width would then be
fixed at authoring time rather than scaling with the icon, and the reference's coordinates would
have to be re-expressed in XML rather than read straight across.

## D3 — Folder-book durations are stored when the Player resolves them

**Decision.** When `PlayerViewModel` resolves a folder book's chapter durations in the background,
write each one to `chapters.endPositionMs`. Report a book's total duration in the library only
when *every* one of its chapters has a stored end.

**Why.** The handoff says total duration "is in the DB but not currently surfaced on
`LibraryBook` — add [it] to the query rather than computing per row." That is true for an `.m4b`
book, whose chapter ends are parsed and stored at add time, and false for a folder book, whose
`endPositionMs` is null on every chapter. `add-folder-audiobooks` design D5 decided against
reading durations at add time, because obtaining them means opening up to 128 files through SAF
while the user waits — and that decision is still right.

But the app already pays that cost, later and off the critical path:
`resolveChapterDurations` reads every chapter's duration on a background dispatcher each time the
book is opened, because the scrubber needs the total. Today the result is discarded when the
`PlayerViewModel` clears, and re-read from scratch on the next open. Storing it makes the library
row's `18 ch · 8h 40m` and the resume card's `4h 20m left` work for folder books, and it makes the
Player's scrubber total exact immediately on every open after the first.

The column already exists and is already nullable, so there is no migration. The write is
semantically what the column means: a folder chapter starts at 0 within its own file, so its end
*is* its duration.

**Consequence, accepted:** a folder book added but never opened shows `18 ch` with no duration,
and does not appear as a resume target. That is honest — the app genuinely does not know how long
it is — and it corrects itself the first time the book is opened. The alternative, showing a total
that grows while the user watches, is worse.

**All-or-nothing totals.** The query reports a duration only when the resolved chapter count
equals the total chapter count (`COUNT(c.id) = COUNT(c.endPositionMs)`). A partial sum is a
number that looks authoritative and is wrong; absent is a number the UI knows not to draw.

## D4 — Absolute saved position is one expression that covers both book shapes

**Decision.** Compute the library's saved position in SQL as

```
(SELECT COALESCE(SUM(c.endPositionMs - c.startPositionMs), 0)
   FROM chapters c
  WHERE c.audiobookId = a.id AND c.chapterIndex < a.lastMediaItemIndex)
+ a.lastPositionMs
```

**Why.** Progress is stored as raw player coordinates — `lastMediaItemIndex` plus
`lastPositionMs` — which `Entities.kt` already explains and which round-trip through
`seekTo` with no translation. Converting them to a book-wide position needs the durations of
whatever came before, and the two book shapes look different: a folder book has one media item per
chapter, an `.m4b` has one media item for the whole book.

They are the same expression anyway. For an `.m4b`, every chapter sits at media item 0, so
`lastMediaItemIndex` is 0, no chapter matches `chapterIndex < 0`, the subquery is 0, and
`lastPositionMs` is already the absolute position. For a folder book, `chapterIndex` and
`mediaItemIndex` are equal, so the subquery sums exactly the chapters that precede the current
one. One expression, no `CASE` on `sourceType`, and no second query path to keep correct.

This is deliberately not a duplicate of `BookTimeline`. `BookTimeline` maps live player
coordinates during playback and must handle unresolved durations, mid-chapter offsets, and seek
targets; this is a single read of a stored position for a book that is not loaded. Giving the
library a `BookTimeline` would mean constructing one per row per emission from a chapter list the
query would have to fetch — which is the "computing per row" the handoff asks to avoid.

## D5 — The Library gets its own `MediaController`

**Decision.** `LibraryViewModel` connects a `MediaController` and owns the resume card's
play/pause. If the controller is already holding the resume book, the control toggles it;
otherwise the book is loaded, seeked to its saved position, given its speed, and played.

**Why.** The handoff requires the resume card's play control to start playback "without
navigating." There is no way to do that without a connection to the session from the Library —
the alternative is navigating to the Player and auto-playing, which is the behavior the design
specifically removes.

The loading sequence is shared with `PlayerViewModel.loadBook` rather than copied: it is extracted
to a `suspend fun MediaController.loadBook(...)` in `playback/`, alongside `mediaItemsFor`, which
is where the rest of the playlist-shaping code already lives. Two independently maintained copies
of "how to put a book into the session" is precisely how the two screens end up disagreeing about
whether opening a book should start it.

Playback state itself stays in the service, which is unchanged. The Library holds a controller the
same way the Player does — a second connection to one session, released when the ViewModel clears
— and the service keeps playing regardless, which is the existing design.

## D6 — The scrubber is drawn rather than a restyled `Slider`

**Decision.** Draw the track, fill, and thumb with `Canvas` and drive them from
`pointerInput`/`detectHorizontalDragGestures`, keeping the existing semantics exactly: dragging
updates the displayed timestamps only, and the seek commits on release.

**Why.** The design calls for an 8dp track with a 4dp radius, a 20dp thumb carrying a 4dp ring in
the *surface* color, and no value indicator, ripple, or state layer. Material 3's `Slider` can be
re-skinned to nearly that through `thumb`/`track` slots, but its thumb keeps a 48dp interaction
box with an internal state layer that changes the thumb's visual size on press, which the design
says explicitly does not happen ("no size change" is stated for play/pause and the scrubber thumb
is specified at one fixed 20dp). Fighting that is more code than drawing two rounded rectangles
and a circle.

The handoff already accepts this shape of departure for the play button, and for the same reason:
"Material's `Button` cannot be a 104dp circle without fighting its content padding."

**Accessibility is not dropped on the floor.** The scrubber carries a `progressBarRangeInfo`
semantics property and a `setProgress` action, so it is operable by a screen reader and by
switch access even though it is not a `Slider`.

## D7 — Chapters and speed are one sheet with two sections, not two sheets

**Decision.** A single `ModalBottomSheet` holds the mini-player header, the speed chip row, and
the chapter list. The Player's two footer halves open the same sheet; the speed half scrolls the
selected chip into view, the chapters half scrolls the current chapter to the second visible row.

**Why.** This is what the handoff draws, and the reason it draws it is worth recording: speed and
chapter are the two things adjusted mid-walk, and putting them behind one gesture means the user
learns one place rather than two. Two separate sheets would also mean two dismiss animations to
get from "wrong chapter" to "wrong speed."

The section to scroll to is local `remember` state in `PlayerScreen`, not `PlayerUiState`. It has
no meaning outside the sheet's own lifetime, does not survive process death in any way that
matters, and putting it in the ViewModel would make the ViewModel responsible for a scroll
position.

## D8 — Status-bar icons are forced light on the Player

**Decision.** While the Player is shown, set the window's status-bar appearance to light icons in
both themes; restore the theme-appropriate appearance when leaving.

**Why.** The Player's cover runs edge to edge behind the status bar and is topped with a 45–50%
dark scrim. Under that scrim, light icons are legible over any artwork; dark icons — which is what
the light theme would otherwise ask for — are not. The scrim exists precisely so that this one
choice is safe regardless of what the cover looks like, which is why it can be a constant rather
than something derived from the image.

This is the only place the app touches window appearance, and it is scoped with a
`DisposableEffect` so returning to the Library restores the system default rather than leaving the
Library's dark-on-light header under light icons.

## D9 — A short screen shrinks the cover; the transport row never moves off-screen

**Decision.** The Player's cover is `width × width`, capped at 46% of screen height, and stays
square at whatever size that yields.

**Why.** The handoff specifies both the square cover and the rule that the transport row must
never be pushed off-screen, and on a short or landscape-ish device those conflict. Capping the
height and keeping the aspect ratio honors the design's actual priority — the cover is the hero,
but the controls are the thing being reached for while walking. Cropping is already
`ContentScale.Crop`, so a smaller square shows the same framing, just less of it.

Scrolling the content column was the alternative and is worse: a transport row you have to scroll
to find is a transport row you cannot hit without looking.

---

## Requirements not changed, and why that is deliberate

`playback` is untouched. Nothing here changes what plays, when, or how — the service, the session,
audio focus, the notification, and Bluetooth handling are all outside this change.

`transport-controls`' seek amounts and the 3-second previous rule are unchanged in substance. The
previous-/next-chapter *buttons* leave the Player screen, but the operations stay: they are what
`ChapterAwarePlayer` runs for a Bluetooth or notification "previous", and the sheet's chapter rows
are a different affordance for a different job (jump to a named chapter, rather than step by one).
If removing the buttons had required changing those requirements, removing them would have been
wrong.

`cover-art`, `m4b-books`, `m4b-chapters`, `folder-scanning`, and `sample-library` are untouched.
The redesign changes how a cover is displayed — its size, its radius, its full-bleed treatment —
but `cover-art`'s requirements are about extraction, caching, and the placeholder fallback, none of
which move.
