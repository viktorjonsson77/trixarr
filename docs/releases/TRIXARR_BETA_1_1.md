# Trixarr Beta 1.1 candidate

Version `0.1.1-beta.1`, versionCode `10101` (previous Beta: `10001`).
Package `se.viktorjonsson.jellyfintv.debug`; applicationId base, namespaces and
existing debug signing configuration remain unchanged. Release signing is separate.

## Integration

Dedicated worktree `/opt/jellyfintv-beta-1.1`, branch `release/trixarr-beta-1.1`.
No applicable AGENTS.md exists at repository/parent or tracked subtree locations.
Exact ancestry is linear:

- `release/trixarr-beta-1`: `553465c3a20352bba27a44cf8bbfcfe7acd36c82`
- Branding V3 implementation `5d7cc3747`, branch tip `9ed83d86c`
- AudioSink regression implementation `be21d55fa`, branch tip `c443283f5`
- Image Quality V1 implementation `cce0bee5c`, branch tip `0b7e5016e7576ed66cea07d6f1320b87adc9e61a`

Starting from Image Quality V1 includes every preceding branch; no duplicate merge,
cherry-pick or implementation is required. Candidate commit `ff2430e93` only updates
version metadata. Existing PlayerEngine, Media3, DV safe negotiation, profile 5/8
support, E-AC3 passthrough/local PCM, refresh matching and session handling remain.
No AudioSink rewrite or additional image changes are included.

## Accepted improvements

Approved blue/cyan triangle, white wordmark, launcher, banner and splash are retained.
Master SHA256: `22421f41e2c3f8a00d8a6b135a6aca8c4c04fc567e5ae94f3afc559bb7967e83`.
The master and all 30 derivative hashes/dimensions pass the existing asset verifier.

Image V1 retains backdrop selection, shared Coil/Jellyfin loader and cache identity,
central bounded size policy, higher detail background resolution, episode thumbnails
and focus margins. User confirms: “Images look much better. Scrolling also feels good.”
Recorded as **IMAGE_QUALITY_USER_PASS** and **NAVIGATION_USER_PASS**. This is overall
acceptance of the existing image implementation, not verification of every artwork type.

## Delivery and readiness

See [candidate acceptance](../evidence/trixarr-beta-1.1/ACCEPTANCE.md) for fresh tests,
APK SHA256/signing, safe Shield update evidence and separated runtime/physical gates.
HDMI interruptions can still occur; long-term stability is not yet verified.
Historical physical results are not fresh candidate physical acceptance. DV7/FEL/MEL
and broad artwork-by-artwork visual checks remain outside verified coverage.
Original worktrees and the 117 unrelated dirty entries are preserved. No main merge,
release tag, public release, reset, clean, blanket stash, force push or data deletion.
