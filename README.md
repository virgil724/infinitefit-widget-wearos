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

## Build & Install

```bash
# Clone
git clone https://github.com/virgil724/infinitefit-widget-wearos.git

# Install phone app (debug)
./gradlew :app:installDebug

# Install watch app (debug)
./gradlew :wear:installDebug
```

For release builds, see [Android Studio: Generate Signed APK](https://developer.android.com/studio/publish/app-signing).

> Both APKs must be signed with the **same keystore** for phone→watch credential sync to work.

---

## How It Works

1. Open the app, enter your InfiniteFit UID and password, tap **Login**
2. Credentials are saved securely on the device
3. Add a widget to your home screen — tap it to generate a QR code
4. On your watch: open the app → tap **從手機同步** to pull credentials from your phone, then your QR code loads automatically

### Authentication Flow

```
1. GET  /infinitefit/login          → extract HashCode (CSRF token)
2. POST /apis/_appinf/login         → returns UUID session token
3. POST /apis/_appinf/genQRCode     → returns QR code content string
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

## 建置與安裝

```bash
# Clone 專案
git clone https://github.com/virgil724/infinitefit-widget-wearos.git

# 安裝手機 App（debug）
./gradlew :app:installDebug

# 安裝手錶 App（debug）
./gradlew :wear:installDebug
```

Release 打包方式請參考 [Android Studio：產生已簽署的 APK](https://developer.android.com/studio/publish/app-signing)。

> 手機與手錶 APK 必須使用**同一個 keystore** 簽署，手機→手錶憑證同步才能正常運作。

---

## 運作原理

1. 開啟 App，輸入 InfiniteFit 帳號（UID）和密碼，點擊**登入**
2. 帳號密碼加密儲存於裝置本機
3. 在桌面新增 Widget，點擊即可產生 QR Code
4. 手錶端：開啟 App → 點擊**從手機同步**將帳號密碼傳至手錶，QR Code 自動載入

### 認證流程

```
1. GET  /infinitefit/login          → 取得 HashCode（CSRF token）
2. POST /apis/_appinf/login         → 回傳 UUID session token
3. POST /apis/_appinf/genQRCode     → 回傳 QR Code 內容字串
```

取得 QR Code 內容後，由 ZXing 在本機產生 Bitmap 顯示。
