# Trixarr architecture overview

**Trixarr — Your media. Your way.** An independent Android TV media player based on Jellyfin Android TV, with Jellyfin as its server/API backend and Seerr for discovery and requests. Upstream attribution and licensing remain intact.

The home/catalog, search, server/account selection and settings surfaces reuse the existing application navigation. A product rename changes human-readable labels without changing the application ID, namespaces, component names, authorities, deep links, signing, preference keys or API contracts.

The user-facing player name is **Trixarr Player Engine**. Its existing Media3 1.8.0 engine and session/reporting identifiers remain stable; the technical engine ID `Jellyfin Media3` is intentionally not renamed. The [Phase 2 completion](PLAYER_ENGINE_PHASE_2_COMPLETION.md), [DV safe negotiation](TRIXARR_DV_SAFE_NEGOTIATION_V1.md) and [local audio decode](TRIXARR_DV_LOCAL_AUDIO_DECODE_V1.md) documents describe the existing implementation. Rebranding does not alter codec selection, audio sinks, DV MIME, refresh switching, ownership, reporting or resume handling.

Current Android labels resolve through `app_name_release` and `app_name_debug`, both Trixarr. Localized app-facing text keeps its surrounding translation. Human-readable diagnostic/report labels may use Trixarr while protocol field names and identifiers remain unchanged. Jellyfin server/authentication/compatibility references remain accurate backend terminology.

Approved Trixarr artwork is missing. The legacy symbol, TV banner and animated splash assets remain unchanged; some raster wordmarks still visibly say ArrStreamer. No replacement artwork or new animation framework is introduced. This prevents full branding acceptance until approved assets are supplied. Historical design/evidence documents, filenames and APKs retain their original names.

See the [build guide](../BUILDING.md) and [rebranding acceptance](../evidence/trixarr-rebranding-v1/ACCEPTANCE.md) for actual validation, artifact identity and remaining limits.
