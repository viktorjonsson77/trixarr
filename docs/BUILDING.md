# Building Trixarr

Trixarr is an Android TV media player based on Jellyfin Android TV. Jellyfin provides the media server, authentication and API backend. Upstream copyright, licenses and notices remain applicable.

Use JDK21, the Android SDK required by the Gradle version catalog, and the checked-in Gradle wrapper. Set `ANDROID_HOME` or configure your SDK through Android Studio.

```sh
git clone https://github.com/viktorjonsson77/jellyfintv.git
cd jellyfintv
./gradlew :app:assembleDebug
```

The debug application ID remains `se.viktorjonsson.jellyfintv.debug`, independent of its visible Trixarr name. Keep the existing signing key to update an installed copy. Inspect the package, certificate and artifact hash before using `adb install -r`; never uninstall or clear app data as a rename step. Existing historical APK filenames need not change.

For focused playback checks, use the existing test filter:

```sh
./gradlew -I docs/evidence/player-engine-phase-2-completion/focused-tests.gradle \
  :playback:core:testDebugUnitTest :playback:jellyfin:testDebugUnitTest :app:testDebugUnitTest
```

The current workspace also contains unfinished startup/refresh work. The latest verified player candidate used explicitly controlled build inputs; see the [local-audio acceptance](evidence/trixarr-dv-local-audio-decode-v1/ACCEPTANCE.md). Do not silently incorporate or discard that work when reproducing an APK. Branding-only validation must retain the same player/audio/DV policies.

Approved Trixarr logo assets have not been found. Existing launcher artwork and animated splash assets are legacy ArrStreamer graphics, not approved Trixarr designs. This is a release acceptance limitation, not permission to synthesize a replacement logo.
