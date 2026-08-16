# Design

## D1 — The asset is copied out of the APK, not played in place

Media3 can play `file:///android_asset/...` through `AssetDataSource`, so playing the sample
without copying looks possible. It is not, cheaply.

`M4bChapterParser.parse(FileChannel)` needs a channel whose position 0 is the start of the MP4
container. An asset inside an APK is a *region* of the APK — `AssetManager.openFd()` gives back an
`AssetFileDescriptor` with a start offset and length, and a `FileChannel` over it is positioned at
the start of the zip entry, not the asset. Parsing in place would mean teaching the parser an
offset, which changes a component whose whole value is that it is small, isolated, and tested.
`MediaMetadataRetriever.setDataSource(fd, offset, length)` would need the same treatment, and
`openFd()` only works at all if the asset is stored uncompressed.

Copying it to `filesDir` once produces a plain file with a plain `file://` URI, and every
downstream component — parser, retriever, ExoPlayer, `CoverStore` — works with no change at all.
The cost is 19.2 MB of device storage, which the user can reclaim by removing the book.

## D2 — Seeding happens on first Library open, not "on install"

There is no install-time hook for an app's own installation; `ACTION_PACKAGE_ADDED` is not
delivered to the package being added, and there is no reliable first-run callback earlier than the
first component starting. First Library open is the standard mechanism and needs no `Application`
subclass.

It runs in `LibraryViewModel.init`, off the main thread, behind the same `busy` flag the pickers
use — so the first launch shows the progress bar for the copy and the parse, then the book appears.

## D3 — The seeder reuses `M4bReader.read()` rather than a parallel path

`M4bReader` was written for a SAF document URI, but nothing in it is SAF-specific:

- `resolver.openFileDescriptor(uri, "r")` accepts `file://`.
- `MediaMetadataRetriever.setDataSource(context, uri)` accepts `file://`.
- `displayName(uri)` queries the resolver, gets null for a file URI, and already falls back to
  `uri.lastPathSegment`.

So the sample is read by exactly the code path a picked `.m4b` is read by, including the chapter
parse, the duration, the artwork extraction, and the three-outcome mapping. A second reader would
be a second thing to keep correct.

## D4 — The title comes from the copied file's name, like every other book

`titleFrom()` already derives a book's title from its file name (PRD §11), so the sample is copied
to `filesDir/sample/The Mystery of Black Rock Creek.m4b` and gets that title for free. The asset
itself is named `the-mystery-of-black-rock-creek.m4b`, because an asset path is a build artifact
and a display title is not.

This is deliberately not a hardcoded title string. Hardcoding one would mean the sample's title
came from somewhere no other book's title comes from, and the file's own embedded `album` tag
("The Mystery of Black Rock Creek") is not what the app reads for *any* book — reading it here
only for the sample would be the same inconsistency by another route.

## D5 — Seeded at most once, tracked in DataStore

A boolean in DataStore, not a row count. "Is the library empty?" is the wrong question — a user who
removes the sample and every other book would get it back, and a user who removes only the sample
would get it back on the next launch.

The flag is set **after** the book is committed, so a crash mid-copy re-seeds on the next open
rather than losing the sample silently. The reverse ordering trades a rare duplicate for a
permanent absence with no way to notice.

`SpeedPreferences` established DataStore-for-one-value here, and this is the same shape: one
boolean, no table.

## D6 — No cover art

The file carries no embedded picture, so it shows the placeholder. Shipping a cover as a second
asset would need a seeding path that writes `CoverStore` directly for a book whose own file has no
artwork — a path no user-added book has, existing only to make one book prettier. The placeholder
is also the `cover-art` capability's own stated behavior, now with a real book behind it.

## D7 — Shipped in every build

Putting the asset in `src/debug/assets/` would keep the owner's release APK 19.2 MB smaller. It
would also mean the artifact a guest is handed is the one variant without the sample, which
inverts the change's purpose. The owner removes it once, on their own device, and it is gone.

## D8 — `file://` availability means the file exists

`UriPermissionHolder.isHeld()` currently means "a persistable read grant is still held", which is
exactly right for SAF and never true for a bundled file. Rather than special-casing the sample's
one URI, the check answers by scheme: a `file://` book is available when the file is there.

That is not a loosening. It is the honest answer to the same question for a different kind of
source — and it makes the sample degrade correctly too, listing as "Source unavailable" rather
than crashing if its file is somehow gone. `release()` is likewise a no-op for `file://`: there is
no grant to give back.

## D9 — Deletion is guarded by containment, not by name

`remove()` deletes a file only when its canonical path is inside `filesDir/sample/`. Matching on
the file name, on the URI string, or on `sourceType == M4B` would all be one bug away from
deleting a user's own book off their disk — the single thing the PRD forbids most plainly.
Containment in a directory the app created is checkable and cannot be spoofed by a picked URI,
since a SAF document URI never has the `file` scheme.
