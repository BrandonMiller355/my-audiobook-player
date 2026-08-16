## Why

The app opens to an empty library and stays empty until someone points it at their own audio.
That is right for the owner, who has a library already, and wrong for anyone else: a guest who
installs the APK to see what it does gets an empty screen and an invitation to go find an `.m4b`
first. Every feature the app has — chapter navigation, the scrubber, speed, resume, the media
notification — is unreachable without supplying content.

So the APK now carries one small public-domain book, and the library seeds itself with it the
first time it opens.

The book is *The Mystery of Black Rock Creek* (LibriVox, public domain): 19.2 MB, 41m44s, AAC,
and — unlike every `.m4b` in the owner's own library — it carries five real QuickTime chapter
marks. It therefore exercises the chaptered path that until now could only be tested against a
synthetic fixture (`add-m4b-books` task 3.6).

PRD sections affected: §7.4, §11 (title fallback), §16 (file access), §22 (unavailable sources).
§10 and §16 are knowingly bent; see "Assumptions".

## What Changes

- The APK bundles one `.m4b` as an asset.
- **The first time the Library screen opens**, the app copies that asset into app-private storage
  and adds it to the library as an ordinary `.m4b` book, chapters and all.
- The seeded book is a **normal library row**: it plays, resumes, remembers its speed, and is
  removed by the same long-press-and-confirm as any other.
- **Removing it is permanent.** The copied file is deleted with the row, and the sample is never
  seeded again — not on the next launch, not ever, short of clearing app data.
- A book whose source is a `file://` URI in app-private storage counts as available when the file
  exists. Today availability means "a persistable SAF grant is still held", which no bundled file
  can ever satisfy, so without this the sample would list as "Source unavailable" from the moment
  it appeared.

## Capabilities

### Added Capabilities

- `sample-library`: the bundled book, when it is seeded, what happens when it is removed, and the
  guarantee that it is seeded at most once.

### Modified Capabilities

- `audiobook-library`: two requirements gain the seeded book as a case. "Removing a book never
  deletes the user's files" is sharpened rather than weakened — removing the sample *does* delete a
  file, and the requirement now says why that is not an exception (the file is the app's own copy
  of its own asset, and was never the user's). "A book whose source becomes unavailable fails
  readably" gains the app-private-file case.

## Impact

**Code**

- `app/src/main/assets/sample/the-mystery-of-black-rock-creek.m4b` — new, 19.2 MB.
- `library/SampleLibrary.kt` — new. Owns the asset copy, the seed-once flag, and the deletion
  guard. The only new file.
- `library/M4bReader.kt` — **unchanged**, and deliberately so. `ContentResolver.openFileDescriptor`
  and `MediaMetadataRetriever.setDataSource(Context, Uri)` both accept a `file://` URI, and
  `displayName()` already falls back to the URI's last segment when no provider answers. The seeder
  reads the sample through the same `read()` the picker uses (design D3).
- `ui/library/LibraryViewModel.kt` — seeds on init; `remove()` also deletes the sample's file.
- `ui/library/LibraryViewModel.kt` (`UriPermissionHolder`) — `isHeld` answers for `file://` by
  asking whether the file exists, and `release` is a no-op for it.

**Dependencies**

None added. DataStore, Room, and `M4bChapterParser` are all already wired into `:app`.

**Permissions and manifest**

No change. The asset ships inside the APK and is copied into `filesDir`; neither needs a
permission, which is the point of not putting it on shared storage.

**Data**

No schema change and no migration. The seeded book is an ordinary `audiobooks` row with
`sourceType = M4B`. One new DataStore key records that seeding has happened.

**Build**

`androidResources { noCompress += "m4b" }`. AAC is already compressed; deflating it at build time
costs time and saves nothing.

**Size**

The APK grows by 19.2 MB, and the device holds a second 19.2 MB copy after first launch until the
user removes the book. Both are accepted (design D1, D2).

## Assumptions

`config.yaml` states that the app gets file access *solely* through SAF grants and *never copies
media*, and PRD §10 forbids duplicating the user's artwork and audio. This change copies a file and
plays it from a non-SAF URI, so the conflict is recorded rather than glossed:

**Those rules exist to protect the user's own library** — not to prevent the app from carrying its
own content. The sample is the app's asset, shipped in its own APK. Copying it duplicates nothing
of the user's, and the user's files remain read-only and SAF-only, untouched by this change. The
`sample-library` spec states the boundary so a later change cannot cite this one as precedent for
copying user media.

## Non-goals

This change deliberately does **not**:

- Add cover art for the sample. The file carries none embedded, so it shows the placeholder —
  which also exercises the `cover-art` fallback. Shipping a separate cover image would mean a
  second asset and a seeding path that no ordinary book has (design D6).
- Add a "restore the sample" affordance, or any settings entry. Removal is meant to be final and
  quiet; a guest who wants it back can clear app data.
- Bundle more than one book, or make the set of bundled books configurable.
- Seed anything on a build the owner installs over an existing library. An install that already
  has a library still seeds — the flag is per-app-data, not per-library — but the sample simply
  joins the list and can be removed like anything else (design D5).
- Change how any user-supplied book is added, read, stored, or played.
- Ship the sample only in debug builds. A guest installing a release APK is exactly the audience
  (design D7).
