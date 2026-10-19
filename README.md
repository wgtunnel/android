<h1 align="center">
WG Tunnel
</h1>

<div align="center">

An alternative FOSS Android client for [WireGuard](https://www.wireguard.com/)
and [AmneziaWG](https://docs.amnezia.org/documentation/amnezia-wg/)
<br />
<br />
<a href="https://github.com/zaneschepke/wgtunnel/issues/new?assignees=zaneschepke&labels=bug&projects=&template=bug_report.md&title=%5BBUG%5D+-+Problem+with+app">Report a Bug</a>
·
<a href="https://github.com/zaneschepke/wgtunnel/issues/new?assignees=zaneschepke&labels=enhancement&projects=&template=feature_request.md&title=%5BFEATURE%5D+-+New+feature+request">Request a Feature</a>
·
<a href="https://github.com/zaneschepke/wgtunnel/discussions">Ask a Question</a>

</div>

<br/>

<div align="center">

[![Google Play](https://img.shields.io/badge/Google_Play-4285F4?style=for-the-badge&logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.zaneschepke.wireguardautotunnel)
[![IzzyOnDroid](https://img.shields.io/static/v1?style=for-the-badge&message=IzzyOnDroid&color=8AA32E&logo=F-Droid&logoColor=FFFFFF&label=)](https://apt.izzysoft.de/fdroid/index/apk/com.zaneschepke.wireguardautotunnel)
[![WG Tunnel F-Droid](https://img.shields.io/static/v1?style=for-the-badge&message=WGT%20F-Droid&color=5A9A9B&logo=F-Droid&logoColor=FFFFFF&label=)](https://fdroid.wgtunnel.com)
[![Obtainium](https://img.shields.io/badge/Obtainium-8B5CF6?style=for-the-badge&logo=Obtainium&logoColor=white)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.zaneschepke.wireguardautotunnel%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fzaneschepke%2Fwgtunnel%22%2C%22author%22%3A%22zaneschepke%22%2C%22name%22%3A%22WG%20Tunnel%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Atrue%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22WG%20Tunnel%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22Zane%20Schepke%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%7D%22%2C%22overrideSource%22%3Anull%7D)

</div>

<div align="center">

[<img  src="https://img.shields.io/badge/Telegram-26A5E4.svg?style=for-the-badge&logo=Telegram&logoColor=white">](https://t.me/wgtunnel)
[<img src="https://img.shields.io/badge/Matrix-000000.svg?style=for-the-badge&logo=Matrix&logoColor=white">](https://matrix.to/#/#wg-tunnel-space:matrix.org)
</div>

<details open="open">
<summary>Table of Contents</summary>

- [About](#about)
- [Screenshots](#screenshots)
- [Features](#features)
- [Building](#building)
- [Translation](#translation)
- [Acknowledgements](#acknowledgements)
- [Contributing](#contributing)

</details>

<div style="text-align: left;">

## About

WG Tunnel is an alternative Android client for WireGuard and AmneziaWG, inspired by the official WireGuard Android app. It fills gaps in the official client by adding advanced features like auto-tunneling, AmneziaWG support, different app modes like **Lockdown** (a custom kill switch for leak prevention), and **Local Proxy** (expose a tunnel over a local SOCKS5/HTTP proxy server) for enhanced privacy, censorship resistance, and flexibility.
 
</div>

<div style="text-align: left;">

## Screenshots

</div>
<div style="display: flex; flex-wrap: wrap; justify-content: left; gap: 10px;">
    <img label="Main" src="fastlane/metadata/android/en-US/images/phoneScreenshots/main_screen.png" width="200"  alt="Main"/>
    <img label="Config" src="fastlane/metadata/android/en-US/images/phoneScreenshots/config_screen.png" width="200"  alt="Config"/>
    <img label="Settings" src="fastlane/metadata/android/en-US/images/phoneScreenshots/settings_screen.png" width="200"  alt="Settings"/>
    <img label="Auto-tunnel" src="fastlane/metadata/android/en-US/images/phoneScreenshots/auto_screen.png" width="200"  alt="Auto-tunnel"/>
</div>

## Features

- **Auto-Tunneling:** Automatically activate tunnels based on your device's active network details.
- **Deferred Endpoint Bootstrapping:** Safely resolves endpoints and updates peers after the tunnel is up for better reliability and leak protection on startup.
- **Handshake Monitoring:** Real-time handshake monitoring for instant tunnel health feedback.
- **AmneziaWG Support:** Full support for AmneziaWG 2.0 through 3.1, providing robust censorship protection.
- **Split Tunneling:** Flexible support for routing specific apps or traffic through the VPN.
- **Split & Encrypted DNS:** Resolve DNS through the tunnel using plain DNS, DoT, or DoH, and optionally split by domain suffix (tunnel or system).
- **Local Proxy Mode:** Expose WireGuard tunnels over a local SOCKS5 or HTTP proxy to browsers or firewall apps (like AdGuard).
- **Lockdown Mode:** Advanced in-app kill switch that blocks all traffic while the tunnel is down.
- **Quick Controls:** Quick Settings tile and home screen shortcuts for easy toggling.
- **Remote Control Support:** Intent-based automation for controlling tunnels and auto-tunneling from automation apps (like Tasker).
- **Dynamic DNS Handling:** Automatically detect and update endpoints on server IP changes without requiring a restart.
- **IPv6 Endpoints:** Automatically upgrade to IPv6 endpoints or fall back to IPv4 based on network conditions without requiring a restart.
- **Android TV Support:** Full support for nearly all features on Android TV.

## Building

The app consumes published [core](https://github.com/wgtunnel/core) artifacts from Maven Central by default (`libs.bundles.wgtunnel.core`). That is enough for most app-only work.

```sh
git clone https://github.com/wgtunnel/android
cd android
./gradlew assembleDebug
```

### Local full build (app + core)

To build against a local core checkout (native JNI, backend, parser, hevtunnel), clone **core next to this repo**:

```
parent/
  android/    # this repository
  core/       # https://github.com/wgtunnel/core
```

```sh
cd /path/to/parent
git clone https://github.com/wgtunnel/android
git clone https://github.com/wgtunnel/core
cd android
```

You also need the [core build requirements](https://github.com/wgtunnel/core#requirements) (JDK 21, Android NDK, `make`, a C toolchain).

Then switch Gradle from Maven Central to the composite build:

1. In `settings.gradle.kts`, uncomment the local-dev `includeBuild`:

```kotlin
// Local dev
includeBuild("../core") {
	dependencySubstitution {
		substitute(module("com.wgtunnel.tunnel:backend"))
			.using(project(":backend"))
		substitute(module("com.wgtunnel.tunnel:backend-android-jni"))
			.using(project(":backend-android-jni"))
	}
}
```

2. In `app/build.gradle.kts`, comment out the Maven bundle and uncomment the local-dev implementations:

```kotlin
dependencies {
    implementation(project(":logcatter"))
    implementation(project(":networkmonitor"))
    // implementation(libs.bundles.wgtunnel.core)

    // Local dev
    implementation("com.wgtunnel.tunnel:backend")
    implementation("com.wgtunnel.tunnel:backend-android-jni")
}
```

3. Build from `android/`:

```sh
./gradlew assembleDebug
```

Do not commit those Gradle edits. Restore the commented `includeBuild` and Maven `libs.bundles.wgtunnel.core` dependency before opening a PR.

## Translation

Help translate WG Tunnel on [Crowdin](https://translate.wgtunnel.com/project/wgtunnel/invite?h=11b5b7bf2099293095775d4477320c772818907).

<!-- CROWDIN-CONTRIBUTORS-START -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/zaneschepke"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/16294570/medium/965e75d46c8e69124d9ad93b6648d70a.jpg" />
          <br />
          <sub><b>Zane Schepke</b></sub>
          <br />
          <sub><b>(zaneschepke)</b></sub></a>
        <br />
        <sub><b>249913 words</b></sub>
      </td>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/martinellimarco"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/17936585/medium/3e4a64d969fa19dca9cda5134eb75ace.png" />
          <br />
          <sub><b>Marco Martinelli</b></sub>
          <br />
          <sub><b>(martinellimarco)</b></sub></a>
        <br />
        <sub><b>4661 words</b></sub>
      </td>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/Pida88"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/17799913/medium/ac2cec66927300857724bef7bd432cd3.jpg" />
          <br />
          <sub><b>Petr Sedl</b></sub>
          <br />
          <sub><b>(Pida88)</b></sub></a>
        <br />
        <sub><b>2211 words</b></sub>
      </td>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/Surfoo"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/12385147/medium/2d69c468ab233ce1d371a4323309190e_default.png" />
          <br />
          <sub><b>Surfoo</b></sub></a>
        <br />
        <sub><b>1984 words</b></sub>
      </td>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/Matthaiks"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/13139078/medium/27126ff13e6785d239e4cf1d47016adc.png" />
          <br />
          <sub><b>Matthaiks</b></sub></a>
        <br />
        <sub><b>1849 words</b></sub>
      </td>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/solokot"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/12939368/medium/23417b511371f5884018368baa1de8f3.jpg" />
          <br />
          <sub><b>solokot</b></sub></a>
        <br />
        <sub><b>1107 words</b></sub>
      </td>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/alis36"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/17816977/medium/be84bd9763853d31c10c09f3708fb72b.jpeg" />
          <br />
          <sub><b>Ali Saeedi</b></sub>
          <br />
          <sub><b>(alis36)</b></sub></a>
        <br />
        <sub><b>1050 words</b></sub>
      </td>
    </tr>
    <tr>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/justcontributor"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/13442389/medium/a6e71d4e535abf3b6d5c954658a93c70.jpg" />
          <br />
          <sub><b>그냥기여자</b></sub>
          <br />
          <sub><b>(justcontributor)</b></sub></a>
        <br />
        <sub><b>965 words</b></sub>
      </td>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/LEDPixel"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/17784119/medium/fb3350aedffaf9c73b3f9de283b92b3e_default.png" />
          <br />
          <sub><b>LEDPixel</b></sub></a>
        <br />
        <sub><b>950 words</b></sub>
      </td>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/cyanwolfg"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/15465504/medium/c294e2b66970ece817e0639c12319b48.png" />
          <br />
          <sub><b>CyanWolf</b></sub>
          <br />
          <sub><b>(cyanwolfg)</b></sub></a>
        <br />
        <sub><b>891 words</b></sub>
      </td>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/EvanTechDev"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/17786633/medium/75db2d8a253d39f149683c270378d8c0.jpeg" />
          <br />
          <sub><b>Evan Huang</b></sub>
          <br />
          <sub><b>(EvanTechDev)</b></sub></a>
        <br />
        <sub><b>751 words</b></sub>
      </td>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/pixnet"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/17785035/medium/ba0625fb3822f186f2a2892f443d5af9_default.png" />
          <br />
          <sub><b>pixnet</b></sub></a>
        <br />
        <sub><b>378 words</b></sub>
      </td>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/zlronron159"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/13014680/medium/54f9e03c69b4f779ba7ef0f6e2ea70c7.jpg" />
          <br />
          <sub><b>ron</b></sub>
          <br />
          <sub><b>(zlronron159)</b></sub></a>
        <br />
        <sub><b>320 words</b></sub>
      </td>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/ppac"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/14481388/medium/a640e3856ebc6794ff424b663627bf6f.png" />
          <br />
          <sub><b>Philip</b></sub>
          <br />
          <sub><b>(ppac)</b></sub></a>
        <br />
        <sub><b>273 words</b></sub>
      </td>
    </tr>
    <tr>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/YuryKomar"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/17782179/medium/687a5b3ebd16654bef045ab89c24d237_default.png" />
          <br />
          <sub><b>YuryKomar</b></sub></a>
        <br />
        <sub><b>271 words</b></sub>
      </td>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/georgybog"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/14779108/medium/da357a1603284460ec743f3c8cb51ba5.png" />
          <br />
          <sub><b>georgybog</b></sub></a>
        <br />
        <sub><b>257 words</b></sub>
      </td>
      <td align="center" valign="top">
        <a href="https://crowdin.com/profile/darbrzozo"><img alt="logo" style="width: 100px" src="https://crowdin-static.cf-downloads.crowdin.com/avatar/17844297/medium/80791831ba4d2a563c6044832cd1a9e7.png" />
          <br />
          <sub><b>Dariusz</b></sub>
          <br />
          <sub><b>(darbrzozo)</b></sub></a>
        <br />
        <sub><b>125 words</b></sub>
      </td>
    </tr>
  </tbody>
</table>
<!-- CROWDIN-CONTRIBUTORS-END -->

## Acknowledgements

Thank you to the following:

- All of the users that have helped contribute to the project with ideas, translations, feedback, bug reports, testing, and donations.
- [WireGuard](https://www.wireguard.com/) - Jason A. Donenfeld (https://github.com/WireGuard/wireguard-android)
- [AmneziaWG](https://docs.amnezia.org/documentation/amnezia-wg/) - Amnezia Team (https://github.com/amnezia-vpn/amneziawg-android)
- [JetBrains](https://jetbrains.com) - For supporting open-source developers with free software licenses.

## Contributing

Any contributions in the form of feedback, issues, code, or translations are welcome and much
appreciated!

For PRs, please make sure to format before submitting.

```sh
./gradlew format
```

CI runs `./gradlew formatCheck`, which uses the same files and style as `format`. If that job fails, run `./gradlew format` and commit the result.

If your PR requires [core](https://github.com/wgtunnel/core) changes, please link the associated PR. 
