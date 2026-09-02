# Jellyfin TV cinematic home

`jellyfintv_startpage.png` is the visual source of truth for the home screen. It is a design-only reference: none of its fictional titles, people, artwork, ratings, or descriptions may ship in the app.

## Acceptance surface

- Primary: 1920×1080 Google TV. The layout must scale proportionally at 1280×720 and 3840×2160.
- Keep interactive content inside the existing app overscan inset, targeting roughly 56–72 px horizontal and 32–48 px top inset at 1080p.
- Toolbar plus compact hero should occupy about 35–37% of screen height. At 1080p the complete Continue Watching row, most of Recently Added, and the Next Up heading/row start should remain visible.

## Composition and focus

- Transparent navigation overlays the real Jellyfin backdrop. A strong left-to-transparent gradient supports hero copy, with subtle top shading and a black lower gradient behind rows.
- Hero title is dominant but compact; optional metadata has no invented placeholders. Overview is limited to two lines in the dense home composition.
- Landscape cards are approximately 16:9; posters approximately 2:3. Corners are softly rounded. Focus scales to about 1.08 with the existing Leanback focus system, leaving enough row padding to avoid clipping.
- D-pad is the primary input. Focus returns between toolbar and the first row using the existing Compose/Leanback bridge; Back retains the existing navigation behavior.

## Real data mapping

- Continue Watching: the existing `HomeSectionType.RESUME` query and `ItemRowAdapter`, retaining Jellyfin playback progress and filtering.
- Recently Added: the existing `HomeSectionType.LATEST_MEDIA` loader, scoped to accessible user views and real item artwork.
- Next Up: the existing `HomeSectionType.NEXT_UP` loader and Jellyfin Next Up response.
- Remaining rows: existing user-configured home sections, library access, notifications, audio queue, and Live TV sources.

Rows without results remain governed by the existing row adapters and are not replaced by placeholders. The selected `BaseItemDto` drives both hero metadata and `BackgroundService`; clicks continue through the existing `ItemLauncher` and playback/detail flows.
