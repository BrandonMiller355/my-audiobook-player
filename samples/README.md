# Sample audiobook

One public-domain audiobook, committed so anyone cloning the repo can try the app without
supplying their own files.

## `MysteryBlackRockCreek_librivox.m4b`

| | |
|---|---|
| Title | *The Mystery of Black Rock Creek* |
| Author | Jerome K. Jerome, Eden Phillpotts, E. F. Benson, F. Frankfort Moore, Barry Pain |
| Reader | Various (LibriVox volunteers) |
| Source | https://archive.org/details/mysteryblackrockcreek_2407_librivox |
| License | Public domain (LibriVox recordings are released into the public domain) |
| Size | 19.2 MB |
| Duration | 41m 44s |
| Audio | AAC, 44.1 kHz |
| Chapters | 5, real QuickTime chapter marks |
| Cover art | None embedded |

Five genuine chapter marks in a small file make this the useful fixture for exercising chapter
navigation — unlike the large single-"chapter" `.m4b` files that motivated the unchaptered
fallback in PRD §7.4. Because it carries no embedded cover, it also exercises the cover-art
fallback path.

## Getting it onto a device

The app has no network permission and reads only `content://` URIs from a folder the user picks,
so the file has to be copied onto the device first. With the emulator or a device attached:

```powershell
D:\Android\Sdk\platform-tools\adb.exe push samples\MysteryBlackRockCreek_librivox.m4b /sdcard/Books/
```

Then open the app and pick the file (or the folder holding it) in the picker.
