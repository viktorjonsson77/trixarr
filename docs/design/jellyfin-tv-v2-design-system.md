# Jellyfin TV v2 design system

This document turns the approved reference images into implementation rules. The images define composition and hierarchy only; all runtime artwork and metadata must come from Jellyfin or Seerr.

The reference inventory contains 43 explicitly named approved screens and five excluded Live-TV/recording screens. The series-detail reference was restored as a valid 1672×941 PNG; its wide episode cards, restrained lower content band, left-weighted gradient, and compact action row govern the series composition.

## Product identity and navigation

- Product names: **Jellyfin TV** and **Jellyfin TV Debug**.
- Header identity: the existing Jellyfin symbol and `Jellyfin` wordmark, never a separate `TV` suffix.
- Primary navigation order: **Hem**, **Upptäck**, **Filmer**, **Serier**, **Sök**.
- The active destination uses a 2 px purple-to-cyan underline. Keyboard focus is a separate dark surface or luminous outline.
- The header is transparent over hero artwork and participates in vertical scrolling. It must never become a sticky opaque panel.

## Acceptance surface and safe area

- Primary composition: 1920×1080; layouts must reflow at 1280×720 and scale cleanly on 4K output.
- Safe margins at 1080p: 56–72 px horizontal and 32–48 px vertical. Compose token: 32–36 dp horizontal and 20–24 dp vertical at the app density.
- Hero content occupies roughly the upper 38–52% depending on screen purpose. Browsing pages favor a compact hero; detail pages may use a taller hero.
- Do not place essential copy, focus outlines, or controls in overscan edges.

## Color and elevation

| Token | Value | Use |
|---|---:|---|
| `Background` | `#08090C` | Root background |
| `Surface` | `#17181D` | Cards and dialogs |
| `SurfaceRaised` | `#22242B` | Focused/raised controls |
| `TextPrimary` | `#F7F7FA` | Primary copy |
| `TextSecondary` | `#B8B9C2` | Metadata and supporting copy |
| `Purple` | `#9B4DFF` | Primary accent |
| `Cyan` | `#19C7F3` | Secondary accent |
| `Success` | `#58C86B` | Available/success states |
| `Warning` | `#F2B84B` | Pending/partial states |
| `Danger` | `#E45B64` | Non-media destructive list actions only |

Use black-to-transparent left, top, and lower gradients over artwork. Avoid broad gray panels and strong glass effects. Dialog scrims use black around 65–75% opacity.

## Typography

- Use the platform/project sans family for navigation, actions, metadata, and dense lists.
- Hero and editorial headings may use the restrained serif treatment shown in references when a bundled, licensed font is available; otherwise use the project sans family rather than adding an unverified font.
- 1080p target sizes: hero title 34–48 sp, page title 28–36 sp, row heading 18–22 sp, card title 12–15 sp, body 14–18 sp, metadata 12–15 sp.
- Descriptions are normally limited to 2–4 lines. Missing fields are omitted without placeholders.

## Spacing and shape

- Base spacing unit: 4 dp. Common gaps: 8, 12, 16, 24, and 32 dp.
- Media cards: 10–14 dp corners. Dialogs: 14–18 dp corners. Chips/buttons: 8–12 dp corners; avoid oversized pills.
- Landscape cards use 16:9. Posters use approximately 2:3. Episode cards use 16:9 art with adjacent metadata.
- Rows include enough leading/trailing and vertical padding for focus scale without clipping.

## Focus and motion

- Default focus scale: 1.06; permitted range 1.05–1.08.
- Focus outline: 2 dp purple-to-cyan gradient or bright neutral outline on already colorful controls.
- Focus motion: 100–140 ms. Hero crossfade: 180–240 ms after a short focus debounce.
- Stable keys and focus requesters are mandatory. Returning from dialogs/details restores the originating element when it still exists.
- A long press is recognized before activation and opened only after the consumed key release, preventing the release from activating the first dialog action.

## Reusable Compose primitives

- `JellyfinTvTheme`: colors, typography, shapes, dimensions, and motion.
- `CinematicScaffold`: safe area, backdrop, gradients, loading/empty/error handling.
- `CinematicTopNavigation`: exact five destinations and separate active/focused state.
- `CinematicHero`: real artwork, metadata, progress, and actions.
- `MediaRow`, `MediaCard`, `PosterCard`, `LandscapeCard`: stable keyed TV rows/cards.
- `FocusableTvButton`: compact dark action with explicit focus colors.
- `MetadataLine`, `StatusChip`, `ProgressIndicator`.
- `CinematicDialog`: constrained width, deterministic initial focus, Back dismissal, focus restoration.
- `EmptyState` and `ErrorState`: actionable, nontechnical states without fabricated content.

## Data and status language

- Jellyfin wins when provider identifiers match Seerr results. Preferred keys: TMDb, TVDb, then IMDb; normalized title and year is only a cautious fallback.
- Swedish request terminology is always `Begär`, `Begärd`, and `Begäranden`.
- User-facing request states: `Kan begäras`, `Begärd`, `Behandlas`, `Delvis tillgänglig`, `Tillgänglig`, `Avslagen`, `Misslyckades`.
- Never expose Radarr, Sonarr, indexers, quality profiles, API keys, tokens, or internal URLs in primary UI or logs.
- No runtime demo data, Live TV, DVR, recording UI, shuffle action, or physical media deletion.

## Accessibility and failure behavior

- Every icon-only action has a localized content description.
- Text and essential controls maintain readable contrast over the darkest expected artwork.
- Loading, empty, offline, and error are distinct states. Retry actions are focusable and errors avoid secrets or stack traces.
- Seerr failure never prevents Jellyfin library, home, search, details, or playback from functioning.
