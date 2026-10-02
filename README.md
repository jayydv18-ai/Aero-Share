# 🚀 AeroShare - High-Speed Offline Peer-to-Peer File Transfer

<p align="center">
  <img src="app/src/main/res/drawable/ic_app_icon.png" width="128" height="128" alt="AeroShare Logo" style="border-radius: 28px;" />
</p>

<p align="center">
  <strong>Fast, Secure & Offline File Sharing for Android</strong><br>
  Built with Jetpack Compose, Material 3, Room, Google Nearby Connections & Wi-Fi Direct.
</p>

---

## 📱 About AeroShare

**AeroShare** is a next-generation Android file sharing application engineered for ultra-fast peer-to-peer data transfer without requiring an internet connection or cellular data. Built with modern Android architecture and Material Design 3 guidelines, it enables users to seamlessly exchange photos, 4K videos, documents, music, and APKs at speeds up to **40+ MB/s**.

### ✨ Key Features

- ⚡ **Ultra-Fast Offline Transfer**: Utilizes Wi-Fi Direct and Google Play Nearby Connections API for direct peer-to-peer data pipelines.
- 📡 **Instant Discovery**: Automatically discovers nearby sender and receiver devices using Bluetooth Low Energy (BLE).
- 📷 **QR Code Fast Connect**: Generate a one-time connection QR code or scan a receiver's QR code with the built-in camera scanner for immediate pairing.
- 🔄 **Real-Time Visual Progress**: Material 3 `CircularProgressIndicator` with live transfer speed (`MB/s`), byte telemetry, animated direction arrows, and accurate time-to-completion (ETA).
- 🛡️ **All-in-One Permissions Hub**: Intuitive permission status screen with a 1-tap **"Grant All Permissions"** flow and direct fallback to Android App Settings.
- 📜 **Transfer History & Log**: Persistent local Room database tracking sent/received files, file sizes, timestamps, and transfer statuses.
- 🎨 **Adaptive Material 3 Design**: Supports Light Mode, Dark Mode, dynamic theming, and edge-to-edge layout across phones and tablets.
- 💎 **Ad-Supported with Pro Unlock**: Integrated with Unity Ads (clean bottom banner ad) and Google Play Billing for 1-tap Pro / Remove Ads upgrade.

---

## 🛠️ Technology Stack & Architecture

- **Language**: Kotlin 100%
- **UI Framework**: Jetpack Compose with Material 3 (M3)
- **Architecture**: MVVM (Model-View-ViewModel) + Clean Architecture
- **Local Persistence**: Room Database (SQLite) + Jetpack DataStore Preferences
- **P2P Networking**: Google Play Services Nearby Connections API (P2P_STAR strategy) + Wi-Fi Direct
- **Concurrency & Reactivity**: Kotlin Coroutines & `StateFlow` / `SharedFlow`
- **Monetization**: Unity Ads SDK & Google Play In-App Billing (v7)
- **Minimum SDK**: Android 8.0 (API Level 26)
- **Target SDK**: Android 14 / 15 (API Level 34+)

---

## 📂 Project Structure

```text
app/src/main/java/com/example/
├── AeroShareApp.kt                 # Application class initializing repositories & managers
├── MainActivity.kt                 # Main Activity hosting Compose navigation root
└── aeroshare/
    ├── ads/
    │   └── AdsManager.kt           # Unity Ads integration and BannerView controller
    ├── billing/
    │   └── PremiumManager.kt       # Google Play Billing In-App Purchase logic
    ├── data/
    │   ├── db/                     # Room Entities & TransferDao
    │   ├── model/                  # Data classes (TransferSession, NearbyDevice, etc.)
    │   └── repository/             # SettingsRepository & TransferRepository
    ├── permission/
    │   └── PermissionManager.kt    # Permission checking, multi-grant & OS intent handling
    ├── service/
    │   └── TransferForegroundService.kt # Foreground service for background transfers
    ├── transfer/
    │   ├── NearbyTransportProvider.kt   # Google Nearby Connections provider
    │   ├── StorageManager.kt            # File querying & safe scoped storage saving
    │   └── TransferManager.kt           # Session orchestrator, chunking & progress
    └── ui/
        ├── components/
        │   ├── AppAvatar.kt                 # Custom customizable device avatars
        │   ├── CircularTransferProgress.kt  # Material 3 Circular Progress indicator
        │   ├── RadarPulseAnimation.kt       # Discovery pulsing radar
        │   └── UnityBannerContainer.kt      # Banner ad container
        ├── screens/
        │   ├── HomeScreen.kt                # Primary dashboard & permissions banner
        │   ├── PermissionsScreen.kt         # All-permissions checklist & 1-tap grant
        │   ├── SendPickerScreen.kt          # Tabbed file selector (Apps, Media, Docs)
        │   ├── DeviceDiscoveryScreen.kt     # Nearby peer device radar & list
        │   ├── TransferProgressScreen.kt    # Real-time circular progress & queue
        │   ├── TransferCompleteScreen.kt    # Summary with open & share actions
        │   ├── QrDisplayScreen.kt           # Receiver QR display
        │   ├── QrScannerScreen.kt           # Live camera viewfinder QR scanner
        │   ├── HistoryScreen.kt             # Room-backed transfer history
        │   ├── SettingsScreen.kt            # Preferences, theme, and profile editing
        │   └── RemoveAdsScreen.kt           # Pro tier purchase screen
        └── theme/
            ├── Color.kt                     # AeroShare Brand & semantic colors
            └── Theme.kt                     # Material 3 theme configuration
```

---

## 🔒 Permissions Used

| Permission | Android API | Purpose |
|:---|:---:|:---|
| `NEARBY_WIFI_DEVICES` | 33+ | High-speed Wi-Fi Direct direct peer-to-peer data transfer |
| `BLUETOOTH_SCAN` / `CONNECT` / `ADVERTISE` | 31+ | Discovery and handshake pairing between nearby phones |
| `ACCESS_FINE_LOCATION` | All | Required by Android OS for Wi-Fi Direct local discovery |
| `POST_NOTIFICATIONS` | 33+ | Live background notification with transfer speed and ETA |
| `CAMERA` | All | Optional scanner for 1-tap QR code pairing |
| `FOREGROUND_SERVICE_DATA_SYNC` | 34+ | Keeps background file transfers running reliably |

---

## 🏗️ Building and Installing the APK

### 1. Build via Gradle Command Line
To assemble the debug APK locally:
```bash
gradle :app:assembleDebug
```
The output APK will be generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### 2. Export / Push to GitHub & Download APK in AI Studio
In the Google AI Studio build environment:
1. **Push to GitHub**:
   - Open the **Project Settings / Source Control** menu in the top-right toolbar.
   - Click **Push to GitHub** to link your repository and publish all commits and files directly.
2. **Download APK / ZIP**:
   - In the same Settings menu, select **Export Project (ZIP)** or **Build APK / AAB** to download the package directly to your computer or Android phone.

---

## 📄 License
This project is open-source under the Apache License 2.0.
