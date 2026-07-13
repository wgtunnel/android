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

[![Google Play](https://img.shields.io/badge/Google_Play-414141?style=for-the-badge&logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.zaneschepke.wireguardautotunnel)
[![IzzyOnDroid](https://img.shields.io/static/v1?style=for-the-badge&message=IzzyOnDroid&color=1976D2&logo=F-Droid&logoColor=FFFFFF&label=)](https://apt.izzysoft.de/fdroid/index/apk/com.zaneschepke.wireguardautotunnel)
[![Obtainium](https://img.shields.io/badge/Obtainium-414141?style=for-the-badge&logo=Obtainium&logoColor=white)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.zaneschepke.wireguardautotunnel%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fzaneschepke%2Fwgtunnel%22%2C%22author%22%3A%22zaneschepke%22%2C%22name%22%3A%22WG%20Tunnel%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Atrue%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22WG%20Tunnel%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22Zane%20Schepke%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%7D%22%2C%22overrideSource%22%3Anull%7D)

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
- **AmneziaWG Support:** Full support for AmneziaWG 2.0, providing robust censorship protection.
- **Split Tunneling:** Flexible support for routing specific apps or traffic through the VPN.
- **Local Proxy Mode:** Expose WireGuard tunnels over a local SOCKS5 or HTTP proxy to browsers or firewall apps (like AdGuard).
- **Lockdown Mode:** Advanced in-app kill switch that blocks all traffic while the tunnel is down.
- **Quick Controls:** Quick Settings tile and home screen shortcuts for easy toggling.
- **Remote Control Support:** Intent-based automation for controlling tunnels and auto-tunneling from automation apps (like Tasker).
- **Dynamic DNS Handling:** Automatically detect and update endpoints on server IP changes without requiring a restart.
- **IPv6 Endpoints:** Automatically upgrade to IPv6 endpoints or fall back to IPv4 based on network conditions without requiring a restart.
- **Android TV Support:** Full support for nearly all features on Android TV.

## Building

```sh
git clone https://github.com/wgtunnel/wgtunnel
cd wgtunnel
```

```sh
./gradlew assembleDebug
```

## Translation

Help translate WG Tunnel on [Crowdin](https://translate.android.wgtunnel.com/project/wgtunnel/invite?h=11b5b7bf2099293095775d4477320c772818907).

## Acknowledgements

Thank you to the following:

- All of the users that have helped contribute to the project with ideas, translations, feedback, bug reports, testing, and donations.
- [WireGuard](https://www.wireguard.com/) - Jason A. Donenfeld (https://github.com/WireGuard/wireguard-android)
- [AmneziaWG](https://docs.amnezia.org/documentation/amnezia-wg/) - Amnezia Team (https://github.com/amnezia-vpn/amneziawg-android)
- [JetBrains](https://jetbrains.com) - For supporting open-source developers with free software licenses.

## Contributing

Any contributions in the form of feedback, issues, code, or translations are welcome and much
appreciated!

Please read
the [code of conduct](https://github.com/zaneschepke/wgtunnel?tab=coc-ov-file#contributor-code-of-conduct)
before contributing.
