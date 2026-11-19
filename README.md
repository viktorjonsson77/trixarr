<h1 align="center">Trixarr</h1>
<h3 align="center">Your media. Your way.</h3>

---

<p align="center">
<br/><br/>
The links below refer to the upstream Jellyfin project and its downloads, not Trixarr releases.
<br/>
<a href="https://github.com/jellyfin/jellyfin-androidtv">
<img alt="GPL 2.0 License" src="https://img.shields.io/github/license/jellyfin/jellyfin-androidtv.svg"/>
</a>
<a href="https://github.com/jellyfin/jellyfin-androidtv/releases">
<img alt="Current Release" src="https://img.shields.io/github/release/jellyfin/jellyfin-androidtv.svg"/>
</a>
<a href="https://translate.jellyfin.org/projects/jellyfin-android/jellyfin-androidtv/">
<img alt="Translation Status" src="https://translate.jellyfin.org/widgets/jellyfin-android/-/jellyfin-androidtv/svg-badge.svg"/>
</a>
<br/>
<a href="https://opencollective.com/jellyfin">
<img alt="Donate" src="https://img.shields.io/opencollective/all/jellyfin.svg?label=backers"/>
</a>
<a href="https://features.jellyfin.org">
<img alt="Feature Requests" src="https://img.shields.io/badge/fider-vote%20on%20features-success.svg"/>
</a>
<a href="https://matrix.to/#/+jellyfin:matrix.org">
<img alt="Chat on Matrix" src="https://img.shields.io/matrix/jellyfin:matrix.org.svg?logo=matrix"/>
</a>
<a href="https://www.reddit.com/r/jellyfin">
<img alt="Join our Subreddit" src="https://img.shields.io/badge/reddit-r%2Fjellyfin-%23FF5700.svg"/>
</a>
<br/>
<a href="https://play.google.com/store/apps/details?id=org.jellyfin.androidtv">
<img width="153" alt="Jellyfin on Google Play" src="https://jellyfin.org/images/store-icons/google-play.png"/>
</a>
<a href="https://www.amazon.com/gp/aw/d/B07TX7Z725">
<img width="153" alt="Jellyfin on Amazon Appstore" src="https://jellyfin.org/images/store-icons/amazon.png"/>
</a>
<a href="https://f-droid.org/en/packages/org.jellyfin.androidtv/">
<img width="153" alt="Jellyfin on F-Droid" src="https://jellyfin.org/images/store-icons/fdroid.png"/>
</a>
<br/>
<a href="https://repo.jellyfin.org/releases/client/androidtv/">Download archive</a>
</p>

Trixarr is an independent Android TV media player for Nvidia Shield and other Android TV devices, based on Jellyfin Android TV. Jellyfin remains its media-server and API backend; Seerr provides discovery and requests.
See the [current architecture](docs/architecture/TRIXARR_OVERVIEW.md) and [developer build guide](docs/BUILDING.md). Trixarr uses the existing triangle design with the user-approved Trixarr wordmark; see [branding V2 evidence](docs/evidence/trixarr-branding-v2/ACCEPTANCE.md).
We welcome all contributions and pull requests! If you have a larger feature in mind please open an
issue so we can discuss the implementation before you start. 

## Translating

Translations can be improved very easily from our
[Weblate](https://translate.jellyfin.org/projects/jellyfin-android/jellyfin-androidtv) instance.
Look through the following graphic to see if your native language could use some work!

<a href="https://translate.jellyfin.org/engage/jellyfin-android/">
<img alt="Detailed Translation Status" src="https://translate.jellyfin.org/widgets/jellyfin-android/-/jellyfin-androidtv/multi-auto.svg"/>
</a>

## Build Process

### Dependencies

- Android Studio

### Build

1. Clone or download this repository

   ```sh
   git clone https://github.com/viktorjonsson77/jellyfintv.git
   cd jellyfintv
   ```

2. Open the project in Android Studio and run it from there or build an APK directly through Gradle:

   ```sh
   ./gradlew assembleDebug
   ```
   
   Add the Android SDK to your PATH environment variable or create the ANDROID_SDK_ROOT variable for
   this to work.

### Deploy to device/emulator

   ```sh
   ./gradlew installDebug
   ```

*You can also replace the "Debug" with "Release" to get an optimized release binary.*
