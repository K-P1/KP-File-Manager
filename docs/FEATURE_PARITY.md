# Desktop and Android feature parity

| Capability | Desktop | Android |
|---|---|---|
| Category sorting | Local filesystem paths and JSON rules | User-selected Storage Access Framework folders and matching built-in rules |
| Series grouping | Groups duplicate normalized titles | Groups duplicate normalized titles |
| Sequential batch rename | Extension filter, prefix, start, padding | Same |
| Scrambled batch rename | Unique values within a multiplier range | Same |
| Music rename | MP3 metadata when available, filename cleanup fallback | Platform metadata reader, filename cleanup fallback |
| Keyword rename | Extension filter and substring replacement | Same, including `*`/blank for all files |
| Preview | Operation log | On-screen change list and internal operation log |
| Undo | Last in-memory sort or rename history | Last completed in-memory operation |

## Android storage model

The Android app uses the platform Storage Access Framework. The user grants access through the system folder picker; no root access, broad storage permission, or companion file-manager app is required.

Android 11 and newer do not allow the picker to grant access to storage roots, the `Download` root, `Android/data`, or `Android/obb`. Individual document providers can also decline rename or move operations. The app reports such failures per item and uses copy/delete as a move fallback when the provider permits both operations.
