# Dolby Vision safe negotiation v1

## Problem and evidence boundary

The Shield comparison established an audio-capability negotiation divergence, not a Media3 version difference. Both applications use Media3 1.8.0. With AC3 bitstream disabled, the unchanged `createDeviceProfile` removes AC3 **and EAC3** from both video DirectPlay and transcoding profile audio lists. Jellyfin can then return `AudioCodecNotSupported` and a converted delivery. Allowing video copy in the request does not prove that the returned stream preserves Dolby Vision.

The user subsequently verified correct colors and TCL Dolby Vision in both applications with original Jellyfin's bitstream enabled. Yamaha indicated PCM for original Jellyfin and DD+ for Trixarr. This physical audio-output difference is USER VERIFIED; its root cause is UNKNOWN. Enabling the setting advertises compatibility; it is not proof of encoded HDMI output.

## Implementation paths

* `app/.../util/profile/deviceProfile.kt:createDeviceProfile`: existing preference-dependent DirectPlayProfiles, TranscodingProfiles and CodecProfiles; unchanged.
* `app/.../ui/playback/PlaybackManager.kt`: `PlaybackInfoDto` carries existing profile, selected source/audio/subtitle and resume ticks. Existing copy flags remain hints. `createStreamInfo` selects the exact requested source, determines candidate delivery from server flags, and assesses safety before constructing any delivery URL.
* `playback/jellyfin/.../negotiation/DolbyVisionNegotiation.kt`: shared SDK-to-policy evidence adapter, including requested source/audio identity, original/source DV metadata and existing profile's audio declaration.
* `playback/core/.../engine/DolbyVisionSafeNegotiationPolicy.kt`: pure delivery safety decision. It neither advertises codecs nor selects a decoder.
* `PlayerEngineManager.evaluate`: merges delivery assessment into the existing capability decision. The existing capability policy still checks decoder/device support. A rejected decision prevents prepare.
* `PlaybackController.startItem`: rejected delivery terminates the existing generation before start/reporting and before the encoded-subtitle retry path; displays the reason and diagnostics. It uses existing engine stop/cleanup ownership. Closing/leaving the playback activity follows existing release lifecycle.
* Experimental `JellyfinMediaStreamResolver`: same safety adapter before returning a playable stream; original audio-only items unaffected.

## Rules

| Candidate | Result |
| --- | --- |
| Original Direct Play, server advertised, audio declaration not incompatible, requested identities match | Permit existing capability checks; original video retained; HDMI NOT VERIFIED |
| DV full video conversion, or converted delivery without preservation evidence | Reject; DV preservation UNKNOWN |
| Video copy plus audio conversion | Requires affirmative server offer, verified video copy, verified container DV signaling, matching delivered/source profile and supported converted audio |
| Unknown source HDR metadata with converted delivery | Reject; safety UNKNOWN |
| Non-DV source with matching requested identities | Existing policy applies |
| Requested source absent or changed | No silent first-source fallback |
| Explicit audio request missing/changed | Reject rather than silently selecting another track |

SDK 1.7.1 PlaybackInfo provides source metadata, delivery support flags, URL and container; the integration has no affirmative **delivered** DV-signaling verification. Therefore runtime converted DV paths remain blocked. DirectStream, HEVC, transcoding container, copy request flags and a source DV profile cannot satisfy the copy proof. Synthetic positive copy fixtures exercise the policy contract; they do not establish real server support. No new media copies or server changes are made.

Profile 5 never gains generic HEVC permission. Profile 7 original playback does not establish MEL classification or FEL enhancement processing. Profile 8.1 original DV and HDR10 fallback are separate paths: this change does not enable unverified converted HDR10 fallback. Unknown profiles remain subject to existing decoder verification. Decoder selector, audio sink, AudioTrack creation, FFmpeg preference, track selection and refresh-rate implementation are unchanged.

## Ownership and diagnostics

No new session or retry owner is introduced. Existing controller generation checks, claimPreparation/claimStart and reporting gates remain authoritative. Cancellation exceptions propagate instead of reaching an error callback. Rejected negotiations close an auto-opened server live resource before returning; no converted delivery URL is opened. Existing live-stream cancellation outside this rejection path is not redesigned here.

Diagnostics distinguish source DV profile and audio tracks, selected source, requested preservation preference, server method/support flags, client audio declaration, copy/signaling evidence, decision/reason, actual Media3 input formats/decoders and AudioTrack encoding. Existing HDMI fields remain NOT VERIFIED. No token, authenticated URL or credential is included in the new decision data.

## Audio investigation

The fresh J02 original-Jellyfin run requests the same static source, selects audio index 1, initializes Nvidia DOVI with `video/dolby-vision`, and creates PCM16 / 48 kHz / 5.1 AudioTrack output. That is different from the historical bitstream-OFF converted run. The common source EAC3 declaration and static delivery are strong identity evidence, but the original app's received compressed audio format and bitstream bytes have not been independently captured. Do not attribute PCM to AudioTrack configuration merely from source metadata.

Upstream/fork builder comparison shows the same DefaultRenderersFactory extension ON/PREFER preference, DefaultTrackSelector/offload setup, Media3 default audio sink and audio attributes. A loaded FFmpeg renderer is not proof that it was selected. Next verification must capture original selected audio input/renderer and compare FFmpeg preferences, track overrides and active audio-route capabilities with Trixarr. No audio-engine changes are justified by current evidence.
