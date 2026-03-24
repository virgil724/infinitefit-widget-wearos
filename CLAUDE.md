# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build phone app (debug)
./gradlew assembleDebug

# Build watch app (debug)
./gradlew :wear:assembleDebug

# Build both modules
./gradlew assemble

# Run unit tests
./gradlew test

# Run unit tests for a single module
./gradlew :app:test
./gradlew :wear:test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Install phone app on connected device
./gradlew :app:installDebug

# Install wear app on connected watch
./gradlew :wear:installDebug
```

## Project Structure

Two Gradle modules sharing the same `applicationId` (`com.example.gymqrdisplayer`):

- **`:app`** — Phone/tablet app (minSdk 26, compileSdk 36)
- **`:wear`** — Wear OS watch app (separate module under `wear/`)

## Architecture Overview

### Authentication Flow (GymRepository)

`GymRepository` is a singleton that wraps a stateful `OkHttpClient` (with cookie jar). The gym backend requires a 3-step flow:

1. `getHashCode()` — GET the login page at `infapp.eip.tw/infinitefit/login`, parse a hidden `HashCode` field (CSRF token) via Jsoup
2. `login(uid, pwd, hashCode)` — POST to `/apis/_appinf/login`, returns a `uuid` session token
3. `generateQRCode(uuid)` — POST to `/apis/_appinf/genQRCode`, returns the QR content string

`createQRCodeBitmap()` renders the QR string into a `Bitmap` using ZXing. The same `GymRepository` class exists in both `:app` and `:wear` modules — they are separate instances with identical logic.

### Credential Storage (DataStoreManager)

Two-tier storage (same pattern in both modules):
- **UID** — stored in Jetpack DataStore (`gym_prefs`)
- **Password** — stored in `EncryptedSharedPreferences` (`gym_encrypted_prefs`) using AES256-GCM

### Phone App Components

| Component | Purpose |
|---|---|
| `MainActivity` | Login UI (Compose); saves credentials via `DataStoreManager` |
| `QrBottomSheetActivity` | Full-screen QR display as a bottom sheet |
| `GymWidget` | Glance home screen widget; taps trigger `RefreshAction` which re-runs the full auth+QR flow |
| `GymPopupWidget` | Glance home screen widget; taps launch `QrBottomSheetActivity` |
| `WearDataLayerService` | `WearableListenerService`; responds to `/request_credentials` messages from the watch by writing credentials to the Wearable Data Layer, then deletes the data item after 10 seconds |

### Wear OS App Components

| Component | Purpose |
|---|---|
| `wear/MainActivity` | Entry point; hosts Compose navigation between `QrScreen` and `CredentialScreen` |
| `WearViewModel` | Manages UI state (`Loading / Success / Error / NoCredentials`); caches UUID for 15 minutes to avoid repeated logins |
| `WearCredentialSyncService` | `WearableListenerService`; receives `/gym_credentials` data from phone, saves via `DataStoreManager`, broadcasts `SYNC_COMPLETE` intent |
| `GymTileService` | Wear Tiles service; reads cached QR content from DataStore (saved by `WearViewModel`) and renders it as an inline image resource |

### Phone ↔ Watch Communication

```
Watch sends:  Message "/request_credentials"  →  Phone WearDataLayerService
Phone sends:  DataItem "/gym_credentials" (uid, pwd)  →  Watch WearCredentialSyncService
              (DataItem deleted after 10s on phone side)
```

The watch can also enter credentials manually via `CredentialScreen` without requiring the phone.

## Key Dependencies

- **Glance** (`1.1.1`) — home screen widgets using Compose-like API
- **OkHttp** (`4.12.0`) + **Jsoup** (`1.18.1`) — HTTP client and HTML parsing for gym auth
- **ZXing** (`3.5.3`) — QR code bitmap generation
- **Wearable Data Layer** (`play-services-wearable 18.1.0`) — phone/watch communication
- **Wear Tiles** (`1.4.1`) — watch tile surface
- **Wear Compose** (`1.3.1`) + **Horologist** (`0.6.21`) — Wear OS UI
