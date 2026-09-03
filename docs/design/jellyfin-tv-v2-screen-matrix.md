# Jellyfin TV v2 screen matrix

Status values describe the state at v2 kickoff. `REFERENCE_READY` means the image has been inventoried but the corresponding runtime screen is not yet accepted. All reference media is design-only.

| File | Screen / flow | Priority | Runtime source | Destination | Implementation | Visual verification / deviation |
|---|---|---:|---|---|---|---|
| `jellyfintv_home.png` | Home | A | Jellyfin + optional Seerr | Hem | REFERENCE_READY | Pending Shield |
| `jellyfintv_home_scrolled.png` | Home scrolled | A | Jellyfin + optional Seerr | Hem | REFERENCE_READY | Header must scroll away |
| `jellyfintv_continue_watching.png` | Continue watching | A | Jellyfin resume API | Home row/page | REFERENCE_READY | Pending Shield |
| `jellyfintv_latest.png` | Latest | B | Jellyfin latest media | Home row/page | REFERENCE_READY | Pending Shield |
| `jellyfintv_for_you.png` | For you | B | Jellyfin suggestions + Seerr | Home row/page | REFERENCE_READY | Pending Shield |
| `jellyfintv_watch_history.png` | Watch history | C | Jellyfin DatePlayed/user data | Personal page | REFERENCE_READY | Removal hidden unless supported |
| `jellyfintv_discover.png` | Discovery | A | Seerr, optional | Upptäck | REFERENCE_READY | Offline state required |
| `jellyfintv_movies.png` | Movies library | A | Jellyfin movie views | Filmer | REFERENCE_READY | Multi-library selector required |
| `jellyfintv_series.png` | Series library | A | Jellyfin TV views | Serier | REFERENCE_READY | Multi-library selector required |
| `jellyfintv_genre.png` | Genre | B | Jellyfin / Seerr | Genre details | REFERENCE_READY | Pending Shield |
| `jellyfintv_upcoming.png` | Upcoming | B | Seerr | Upptäck / upcoming | REFERENCE_READY | Request terminology overrides image |
| `jellyfintv_my_list.png` | My list | B | Verified Jellyfin list semantic | Personal page | REFERENCE_READY | Semantic mapping pending |
| `jellyfintv_library_filters.png` | Library filters | B | Jellyfin query options | Library dialog | REFERENCE_READY | Pending Shield |
| `jellyfintv_movie_available.png` | Available movie details | A | Jellyfin | Movie details | REFERENCE_READY | Pending Shield |
| `jellyfintv_movie_request.png` | Requestable movie | C | Seerr | Movie details | REFERENCE_READY | Explicit confirmation required |
| `jellyfintv_series_details.png` | Series details | A | Jellyfin | Series details | INVALID_REFERENCE | File is empty; use shared system and season reference |
| `jellyfintv_series_request.png` | Series request | C | Seerr | Series details/dialog | REFERENCE_READY | Explicit season selection |
| `jellyfintv_season_episodes.png` | Season and episodes | A | Jellyfin TV API | Series details | REFERENCE_READY | Pending Shield |
| `docsdesignjellyfintv_episode_details.png` | Episode details | B | Jellyfin | Episode details | REFERENCE_READY | Misnamed upload; maps to requested filename |
| `jellyfintv_person_details.png` | Person details | B | Jellyfin | Person details | REFERENCE_READY | Pending Shield |
| `jellyfintv_collection_details.png` | Collection details | B | Jellyfin | Collection details | REFERENCE_READY | Pending Shield |
| `jellyfintv_search_results.png` | Search results | A | Jellyfin + optional Seerr | Sök | REFERENCE_READY | Jellyfin results win duplicates |
| `jellyfintv_search_empty.png` | Empty search | A | Jellyfin + optional Seerr | Sök | REFERENCE_READY | No fabricated suggestions |
| `jellyfintv_requests.png` | User requests | C | Seerr | Begäranden | REFERENCE_READY | Hide admin concepts |
| `jellyfintv_request_confirmation.png` | Request confirmation | C | Seerr | Request dialog | REFERENCE_READY | Mutation only after confirmation |
| `jellyfintv_request_status.png` | Request status | C | Seerr | Request details | REFERENCE_READY | Simplified status labels |
| `jellyfintv_activity.png` | Request activity | C | Seerr | Begäranden/activity | REFERENCE_READY | User-scoped content |
| `jellyfintv_settings_requests.png` | Request settings | C | Encrypted local settings | Settings | REFERENCE_READY | No cleartext secret display |
| `jellyfintv_seerr_connect.png` | Seerr connection | C | Seerr | Setup/settings | REFERENCE_READY | TLS verification by default |
| `jellyfintv_context_menu.png` | Context menu | A | Jellyfin item capabilities | Modal | REFERENCE_READY | No physical delete or shuffle |
| `jellyfintv_add_to_list.png` | Add to list | C | Verified Jellyfin list API | Modal | REFERENCE_READY | Exact semantic mapping pending |
| `jellyfintv_player_controls.png` | Player controls | D | Existing Media3 chain | Player | REFERENCE_READY | Playback behavior retained |
| `jellyfintv_audio_subtitles.png` | Audio/subtitles | D | Jellyfin media streams | Player dialog | REFERENCE_READY | Pending media availability |
| `jellyfintv_version_selector.png` | Version selector | D | Jellyfin media sources | Details/player dialog | REFERENCE_READY | No invented quality claims |
| `jellyfintv_next_episode.png` | Next episode | D | Jellyfin next-up | Player overlay | REFERENCE_READY | Pending media availability |
| `jellyfintv_playback_error.png` | Playback error | D | Playback error model | Player error | REFERENCE_READY | Redacted technical details |
| `jellyfintv_profile_menu.png` | Profile menu | D | Jellyfin users/session | Profile overlay | REFERENCE_READY | Pending Shield |
| `jellyfintv_profile_select.png` | Profile selection | D | Jellyfin users | Startup/profile | REFERENCE_READY | Pending available profiles |
| `jellyfintv_profile_pin.png` | Profile PIN | D | Jellyfin authentication | Startup/profile | REFERENCE_READY | PIN never logged/stored cleartext |
| `jellyfintv_server_connect.png` | Server connect | D | Jellyfin discovery | Startup | REFERENCE_READY | Existing server layer retained |
| `jellyfintv_discovery_setup.png.png` | Discovery setup | D | Jellyfin + optional Seerr | Startup | REFERENCE_READY | Duplicate extension; maps to requested filename |
| `jellyfintv_connection_error.png` | Connection error | D | Connection state | Startup/error | REFERENCE_READY | Nontechnical, actionable copy |
| `designjellyfintv_screensaver.png` | Screensaver | D | Jellyfin artwork | Screensaver | REFERENCE_READY | Misnamed upload; maps to requested filename |
| `jellyfintv_kids_home.png` | Kids home | D | Jellyfin policy/profile | Hem | REFERENCE_READY | Requires real policy, not cosmetic filter |
| `jellyfintv_live_tv.png` | Live TV | — | — | — | EXCLUDED | Product decision: no Live TV |
| `jellyfintv_recordings.png` | Recordings | — | — | — | EXCLUDED | Product decision: no recording UI |
| `jellyfintv_recording_details.png` | Recording details | — | — | — | EXCLUDED | Product decision: no recording UI |
| `jellyfintv_recording_schedule.png` | Schedule recording | — | — | — | EXCLUDED | Product decision: no recording UI |
| `jellyfintv_recording_schedules.png` | Recording schedules | — | — | — | EXCLUDED | Product decision: no recording UI |

## Inventory notes

- The prompt labels the approved set as 44 images, while its explicit approved filename list contains 43 entries.
- Forty-two approved references are valid PNG images at 1672×941. `jellyfintv_series_details.png` exists but is zero bytes.
- Three requested concepts are present under malformed filenames: episode details, discovery setup, and screensaver.
- Five additional valid images are explicitly excluded because they depict Live TV or recording workflows.
- No binary duplicate hashes were found. `jellyfin-tv-home-spec.md` is an older supplementary document, not a screen reference.
