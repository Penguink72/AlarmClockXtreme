# Preview 3 UX-R2: wireframes and task flows

This note records the read-only UX-R2-0 comparison against the real P3
baseline. It does not claim that backend-dependent playlist or media features
are connected.

## Baseline and integration boundary

- UI branch base: `8ce9fa93ab828b9973a85096a8bbc785318d8273`.
- The existing catalog screen reads real Room v27 `MusicPoolTrack` rows and
  retains bounded search, folder scope, and membership updates.
- The prior screen presents five peer tabs and nests a source dialog inside a
  full-screen dialog.
- Room v27 has no named playlist tables or MediaStore Video/artist metadata.
- PR #5 is a separate open draft; its auto-inclusion behavior conflicts with
  the explicit-add UX-R2 rule and is not adopted here.

## Wireframe A: search-first media list (selected)

```text
┌─────────────────────────────────────┐
│ ‹  Music & media                     │
│    Media library   |   Playlists     │
├─────────────────────────────────────┤
│ [ Search music and videos       🔍 ] │
│ [All] [Music] [Videos]              │
│ Browse: [Folders] [Artists & albums]│
│                                     │
│ □  Song title                       ⋮│
│    File name · folder · availability│
│ □  Long mixed-language title        ⋮│
│    File name · folder · availability│
│                                     │
│    ... scrollable indexed results   │
├─────────────────────────────────────┤
│       Select tracks to add          │
└─────────────────────────────────────┘

Selection mode:
┌─────────────────────────────────────┐
│ 20 selected       Clear             │
│             [Add to playlist]       │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ Add 20 tracks to                    │
│ [Morning Japanese]                  │
│ [+ Create playlist]                 │
│ [Cancel]                [Add]       │
└─────────────────────────────────────┘
```

## Wireframe B: folder-first discovery (comparison)

```text
┌─────────────────────────────────────┐
│ ‹  Music & media                     │
│    Media library   |   Playlists     │
├─────────────────────────────────────┤
│ [Search folders and tracks     🔍 ] │
│ [Phone music ▸] [SD Card music ▸]   │
│                                     │
│ ▸ Music/Japanese             128    │
│ ▸ Music/Albums                74    │
│ ▸ Other authorized folders    12    │
│                                     │
│ Enter folder:                       │
│ [All] [Music] [Videos]              │
│ □ Song title                       ⋮│
│ □ Song title                       ⋮│
├─────────────────────────────────────┤
│ 20 selected       [Add to playlist] │
└─────────────────────────────────────┘
```

Folder-first costs one extra navigation step for the common phone-library
search and cannot show MediaStore virtual folders until that metadata exists.
Search-first is selected; folder and artist/album browsing remain secondary
filters rather than peer workspaces.

## Task-flow comparison

| Task | Search-first flow | Folder-first flow | Boundary |
|---|---|---|---|
| A. Find 20 phone songs and create “Morning Japanese” | Open media library → search → long-press/select 20 → Add to playlist → create/name → confirm | Open folders → choose device/folder → select 20 → Add → create/name → confirm | Existing device audio is indexed in Room v27. Playlist creation persists only after a real playlist repository is supplied. |
| B. Add an FLV from a video folder | Search/filter Videos or open folder → inspect candidate state → add only if validated | Open folder → inspect video candidate → add only if validated | Current branch has no FLV/backend video catalog. It must show pending/not connected and must not report a successful add. |
| C. Choose “Morning Japanese” and ordered playback for one alarm | Open alarm music settings → select playlist → choose playlist order → Save | Same flow | Room v28 alarm settings save is not present in the current repository. |

## Selected interaction rules

- Two peer workspaces only: Media library and Playlists.
- Media library defaults to the real indexed list; type and folder/artist
  selection are secondary controls.
- Long-press enters selection mode; the fixed bottom action reports the
  selected count and leads to a playlist picker.
- Search, type, or source changes clear selections before batch actions.
- Playlist records are never inferred from visible rows. Creation, copy, rename,
  add, reorder, and delete require a connected repository and report its result.
- The list item identity is `playlistItemId`, not `trackUri`; duplicates remain
  independent entries.
- No preview success is shown without an alarm-aware preview controller.
