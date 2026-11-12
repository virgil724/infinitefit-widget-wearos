# InfiniteFit Widget & WearOS

An Android app that logs into the [InfiniteFit](https://infapp.eip.tw/infinitefit/) gym portal and displays your entry QR code — accessible from a home screen widget or your Wear OS watch.

---

## Features

- **Home screen widgets** — tap to generate a fresh QR code without opening the app
  - **Direct widget** (`GymWidget`): shows the QR code inline on the widget
  - **Popup widget** (`GymPopupWidget`): tap to open a full-screen bottom sheet
- **Wear OS app** — view your QR code directly on your watch
  - Sync credentials from your phone via Wearable Data Layer (one tap)
  - Or enter credentials manually on the watch
  - **Wear Tile** — swipe to the tile for quick QR access without opening the app
- **Secure storage** — password stored in `EncryptedSharedPreferences` (AES256-GCM)

## Screenshots

> _TODO_

---

## Requirements

- Phone: Android 8.0+ (API 26+)
- Watch: Wear OS 2.0+
- Both devices must use the **same signing certificate** for credential sync to work

## Install (prebuilt)

Signed APKs are published on [GitHub Releases](https://github.com/virgil724/infinitefit-widget-wearos/releases) for every `v*` tag.

- **Phone**: install [Obtainium](https://github.com/ImranR98/Obtainium), add this repo URL, and set the APK filter to `phone`. Obtainium will notify you of new releases.
- **Watch**: download `gymqr-wear-*.apk` and install via wireless debugging: `adb connect <watch-ip>:<port>` then `adb install -r gymqr-wear-*.apk`.

## Build & Install

```bash
# Clone
git clone https://github.com/virgil724/infinitefit-widget-wearos.git

# Install phone app (debug)
./gradlew :app:installDebug

# Install watch app (debug)
./gradlew :wear:installDebug
```

Releases are built by `.github/workflows/release.yml`: bump `versionName`/`versionCode` in `app` and `wear`, then push a matching tag (e.g. `v1.1.0`).

> Both APKs must be signed with the **same keystore** for phone→watch credential sync to work.

---

## How It Works

1. Open the app, enter your InfiniteFit UID and password, tap **Login**
2. Credentials are saved securely on the device
3. Add a widget to your home screen — tap it to generate a QR code
4. On your watch: open the app → tap **從手機同步** to pull credentials from your phone, then your QR code loads automatically

### Authentication Flow

```
1. POST /apis/_appinf4/Initial      → returns HashCode (CSRF token)
2. POST /apis/_appinf4/login        → returns UUID session token
3. POST /apis/_appinf4/genQRCode    → returns QR code content string
```

The QR code content is then rendered locally using ZXing.

---

---

# InfiniteFit Widget & WearOS（中文）

一款 Android App，自動登入 [InfiniteFit](https://infapp.eip.tw/infinitefit/) 健身房系統並顯示入場 QR Code，可從桌面 Widget 或 Wear OS 手錶快速取用。

---

## 功能

- **桌面小工具** — 不需開啟 App 即可產生最新 QR Code
  - **直接顯示** (`GymWidget`)：點擊 Widget 即更新並顯示 QR Code
  - **彈出顯示** (`GymPopupWidget`)：點擊後以全螢幕底部面板顯示
- **Wear OS App** — 直接在手錶上顯示 QR Code
  - 透過 Wearable Data Layer 從手機一鍵同步帳號密碼
  - 或直接在手錶上手動輸入帳號密碼
  - **Wear Tile** — 滑到磚塊即可快速查看 QR Code，無需開啟 App
- **安全儲存** — 密碼以 AES256-GCM 加密儲存於 `EncryptedSharedPreferences`

---

## 需求

- 手機：Android 8.0+（API 26+）
- 手錶：Wear OS 2.0+
- 手機與手錶 APK 必須使用**相同的簽署憑證**，手機→手錶同步功能才能正常運作

## 安裝（已打包版本）

每個 `v*` 標籤都會自動發佈簽名 APK 到 [GitHub Releases](https://github.com/virgil724/infinitefit-widget-wearos/releases)。

- **手機**：安裝 [Obtainium](https://github.com/ImranR98/Obtainium)，加入本 repo 網址，APK 篩選設成 `phone`，有新版會通知更新。
- **手錶**：下載 `gymqr-wear-*.apk`，用無線偵錯安裝：`adb connect <手錶IP>:<port>` 後 `adb install -r gymqr-wear-*.apk`。

## 建置與安裝

```bash
# Clone 專案
git clone https://github.com/virgil724/infinitefit-widget-wearos.git

# 安裝手機 App（debug）
./gradlew :app:installDebug

# 安裝手錶 App（debug）
./gradlew :wear:installDebug
```

Release 由 `.github/workflows/release.yml` 自動建置：調整 `app` 與 `wear` 的 `versionName`/`versionCode`，再推送對應標籤（例如 `v1.1.0`）。

> 手機與手錶 APK 必須使用**同一個 keystore** 簽署，手機→手錶憑證同步才能正常運作。

---

## 運作原理

1. 開啟 App，輸入 InfiniteFit 帳號（UID）和密碼，點擊**登入**
2. 帳號密碼加密儲存於裝置本機
3. 在桌面新增 Widget，點擊即可產生 QR Code
4. 手錶端：開啟 App → 點擊**從手機同步**將帳號密碼傳至手錶，QR Code 自動載入

### 認證流程

```
1. POST /apis/_appinf4/Initial      → 取得 HashCode（CSRF token）
2. POST /apis/_appinf4/login        → 回傳 UUID session token
3. POST /apis/_appinf4/genQRCode    → 回傳 QR Code 內容字串
```

取得 QR Code 內容後，由 ZXing 在本機產生 Bitmap 顯示。

---

## License

GPL-3.0 — see [LICENSE](LICENSE).
