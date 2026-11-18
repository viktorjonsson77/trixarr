# Dolby Vision with local audio decoding V1

## Cause and boundary

The accepted baseline distinguishes transport from HDMI: with Dolby Digital bitstream ON, Resident Evil DV5/EAC3 used DirectPlay, Nvidia DOVI and encoded EAC3 AudioTrack. With OFF the client removed AC3/EAC3 from its DeviceProfile, Jellyfin proposed Transcode, and the DV policy correctly rejected unverified conversion. This was a client capability advertisement problem, not evidence that local EAC3 decoding was impossible.

A second boundary was independent: the original DefaultAudioSink could accept compressed formats regardless of the preference. Adding EAC3 to the profile alone would therefore not guarantee PCM output. Neither server copy hints nor source DV metadata can prove converted DV preservation.

## Audio policy and capabilities

`playback/core/.../engine/LocalAudioDecodePolicy.kt` is a pure audio compatibility decision feeding the existing profile and DV delivery policy. It separates local native/software decoding, PCM output support and encoded sink capability. OFF requires a local decoder plus PCM support for the specified channel count/rate. ON may use encoded output; local decoding remains possible. Invalid or unsupported formats cannot become DirectPlay merely because encoded output exists.

`playback/media3/exoplayer/.../LocalAudioSupport.kt` probes Media3's MediaCodec registry using `isFormatSupported`, the actually loaded packaged FFmpeg library using `supportsFormat`, and the standard PCM sink plus AudioTrack buffer-configuration support. FFmpeg is software decoding inside the existing Android Media3 pipeline, not an Android MediaCodec claim or a new engine. Input capability does not prove successful decoding, active HDMI routing or physical speaker mapping.

OFF DeviceProfiles filter the existing audio codec candidates through that policy. Profile limits form a conservative rectangle of tested channel counts and sample rates; AC3 stops at six channels, others at eight. Dolby rates are 32/44.1/48 kHz; other codecs use a bounded common-rate list. Explicit user stereo/downmix behavior remains authoritative. Unknown mappings, including PCM20LE without an established mapping, are excluded in OFF mode. Query failure never invents support.

Both network DirectPlay and converted delivery audio lists use the resulting supported codecs. CodecProfiles constrain channels and sample rates. Empty profiles are omitted to avoid an empty codec list becoming a wildcard. The shared Jellyfin adapter distinguishes a missing profile (unknown legacy evidence) from an explicitly empty video profile (unsupported).

ON retains the legacy DeviceProfile audio advertisement and default sink behavior. Existing video/container/HDR/bitrate/subtitle checks remain in force; no server flags are forced. PlaybackInfo still selects the source and track, and the existing DV safety gate runs before a URL is opened. Original source audio travels over the network unchanged in DirectPlay; decoding happens after Media3 extraction.

## Media3 audio integration

`LocalAudioRenderersFactory` uses the standard DefaultRenderersFactory. ON returns its unmodified audio sink. OFF wraps that sink with `PcmOnlyAudioSink`, which rejects compressed support queries, reports no audio offload support, and rejects a compressed configure call. This removes MediaCodecAudioRenderer's encoded bypass path and lets the normal native or FFmpeg renderer decode to PCM. The wrapper delegates PCM configuration, channel maps, buffers, timestamps, playback position, volume and release unchanged.

The legacy VideoManager and experimental ExoPlayerBackend both use this factory and disable audio offload in OFF mode. The experimental option reads the current preference when constructing the player. No stereo map, new clock, resampler, renderer ordering, video selector or DV MIME change is introduced. Passthrough preference is captured for a player lifetime; stop/reopen after changing it. Runtime track/source changes continue through existing selection and ownership.

If audio decoding fails, the existing terminal player-error path owns stop/report/release. There is no automatic server transcode retry or unsafe DV fallback. Unsupported local audio can use converted video only if the existing independent policy has affirmative video-copy, matching delivered DV profile, container signaling and converted-audio compatibility evidence. The current SDK adapter still lacks that proof and rejects those conversions.

## Diagnostics and verification

Diagnostics separate source codec; CAN_DECODE_AUDIO, native and packaged FFmpeg declarations; CAN_PASSTHROUGH_AUDIO; enabled preference; requested audio path; actual decoder; actual AudioTrack encoding/channel count/rate; server decision; and DV preservation. Physical AVR output remains NOT VERIFIED until the user observes it. AudioTrack release clears actual-output fields. Unknown/nonmapped formats are not falsely labeled decoder-unsupported by the new diagnostic probe.

Focused synthetic tests exercise the real DeviceProfile builder, shared Jellyfin policy adapter, PCM-only sink and pure policy. Existing controller/owner/reporting tests cover track changes, pause/seek/resume, terminal decoder failure, cancellation, stale notifications, ordered reporting and exactly-once cleanup. Android tests exercise the real packaged decoders and real Media3 sink without claiming HDMI certification. The evidence document records controlled build inputs, excluded unrelated unfinished work, exact test counts, artifact identity and physical A/B observations.

Limits: capability declarations cannot guarantee every bitstream/profile decodes correctly; PCM does not preserve Atmos object metadata; no universal channel-map or DV7 FEL certification is claimed. A conservative rate/channel rectangle can request server conversion for an unusual format that a decoder might support. Route changes and broader codec/HDMI matrices need independent validation. No Kodi, new video engine, server code, global audio settings or production deployment.
