# StickerTransfer — Android App

Transfer Telegram sticker packs directly to WhatsApp or export as ZIP.
Built with **Kotlin + Jetpack Compose + Material You (MD3)**.

---

## Screenshots / Features

| Home Screen | Import Screen |
|---|---|
| Load any Telegram sticker pack via share link | Import your own WEBP ZIP packs |
| Preview + sticker grid | 512×512 WEBP validation |
| Download as ZIP | Add to WhatsApp / WA Business |
| Add to WhatsApp directly | |

---

## Architecture

```
app/
├── data/
│   ├── model/          # StickerPack, TelegramModels
│   ├── network/        # TelegramApiService (Ktor), PreferencesRepository
│   └── repository/     # StickerRepository (fetch, download, convert)
├── provider/           # StickerContentProvider (WhatsApp protocol)
├── ui/
│   ├── navigation/     # NavGraph, Screen sealed class
│   ├── screens/        # HomeScreen, ImportScreen
│   ├── theme/          # Material You theme, Color, Type
│   └── viewmodels/     # HomeViewModel, ImportViewModel
└── utils/              # WhatsAppUtils, ZipUtils
```

**Stack:** MVVM · Repository pattern · Coroutines · StateFlow · Jetpack Compose · Material3

---

## Prerequisites

| Tool | Version |
|---|---|
| Android Studio | Hedgehog 2023.1.1+ or newer |
| JDK | 17+ |
| Android SDK | API 34 (compile), API 24 (min) |
| Kotlin | 1.9.22 |

---

## Setup (One-Time)

### 1. Get a Telegram Bot Token (Free)

> The app uses the official Telegram Bot API to download sticker packs.

1. Open Telegram → search **@BotFather**
2. Send `/newbot`
3. Follow the prompts (name + username)
4. Copy the token — looks like: `1234567890:ABCdefGHIjklMNOpqrSTUvwxYZ`

You enter this token inside the app (Settings icon → top-right).
It is stored locally on your device only.

### 2. Clone / Open Project

```bash
git clone https://github.com/yourname/StickerTransfer.git
# OR extract the ZIP
```

Open **Android Studio** → `File > Open` → select the `StickerTransfer` folder.

Wait for Gradle sync to complete (~2-5 min first time).

---

## Building the APK

### Option A — Debug APK (fastest, for testing)

```bash
# From project root:
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Option B — Release APK (optimized, for distribution)

#### 1. Create a keystore (one time only)

```bash
keytool -genkey -v \
  -keystore stickertransfer.jks \
  -alias stickertransfer \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

#### 2. Add signing config to `app/build.gradle.kts`

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../stickertransfer.jks")
            storePassword = "YOUR_STORE_PASSWORD"
            keyAlias = "stickertransfer"
            keyPassword = "YOUR_KEY_PASSWORD"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ... rest unchanged
        }
    }
}
```

#### 3. Build release

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### Option C — Android Studio GUI

1. Build menu → **Generate Signed Bundle / APK**
2. Choose **APK**
3. Select / create keystore
4. Choose `release` build variant
5. Click **Finish**

---

## Installing the APK

### Via ADB (USB debugging)

```bash
# Enable USB Debugging on your device first
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Direct install

1. Copy `app-debug.apk` to your device
2. Open it from Files app
3. Allow "Install unknown apps" if prompted

---

## Testing WhatsApp Integration

### Checklist

- [x] WhatsApp 2.19.51+ installed
- [x] Sticker pack fully downloaded (green checkmark state)
- [x] Tap **Add to WhatsApp**
- [x] WhatsApp opens with sticker pack preview
- [x] Tap **Add** inside WhatsApp

### Troubleshooting

| Issue | Fix |
|---|---|
| "WhatsApp not installed" | Install WhatsApp from Play Store |
| WhatsApp opens but pack empty | Ensure download completed first |
| "Failed to launch WhatsApp" | WhatsApp version too old — update |
| Stickers appear low quality | Normal — WEBP compression applied |
| Bot token error | Verify token from @BotFather |
| "Pack not found" | Check pack name spelling (case-sensitive) |

### Content Provider Verification

```bash
# Query our sticker provider
adb shell content query \
  --uri content://com.stickertransfer.app.debug.StickerContentProvider/metadata
```

---

## How Telegram Download Works

```
User pastes link
        ↓
Parse: t.me/addstickers/{packName}
        ↓
Bot API: getStickerSet?name={packName}
  → Returns sticker metadata + file_ids
        ↓
For each sticker:
  Bot API: getFile?file_id={id}
    → Returns file_path on CDN
  Download: api.telegram.org/file/bot{token}/{path}
        ↓
Convert each file → 512×512 WEBP (≤100KB)
        ↓
Save to: filesDir/stickers/{packName}/001.webp … N.webp
Save meta: filesDir/stickers/{packName}/meta.json
Save tray: filesDir/stickers/{packName}/tray.webp (96×96)
        ↓
StickerContentProvider serves files to WhatsApp on demand
```

---

## WhatsApp Protocol

WhatsApp queries our `StickerContentProvider` at:
- `content://{authority}/metadata` — list of packs
- `content://{authority}/stickers/{id}` — sticker list for pack
- `content://{authority}/stickers/{id}/{file.webp}` — raw file bytes

The authority is: `com.stickertransfer.app.StickerContentProvider`
(debug builds append `.debug`)

---

## ZIP Export Format

When exporting as ZIP, the file is saved to:
- **Android 10+**: `Downloads/` via MediaStore
- **Android 9 and below**: `/sdcard/Downloads/`

ZIP contains:
```
{packName}.zip
├── 001.webp
├── 002.webp
├── ...
└── tray.webp
```

---

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Download stickers from Telegram |
| `READ_EXTERNAL_STORAGE` (≤API 32) | Read imported ZIP files |
| `WRITE_EXTERNAL_STORAGE` (≤API 29) | Save ZIP to Downloads |

No sensitive permissions required. Scoped storage used on API 29+.

---

## Key Files Explained

| File | Purpose |
|---|---|
| `StickerContentProvider.kt` | WhatsApp protocol — serves sticker data |
| `StickerRepository.kt` | Downloads + converts stickers to WEBP |
| `TelegramApiService.kt` | Ktor HTTP client for Telegram Bot API |
| `HomeViewModel.kt` | State management for main screen |
| `WhatsAppUtils.kt` | Fires the WhatsApp add-sticker Intent |
| `ZipUtils.kt` | ZIP export + import extraction |

---

## Known Limitations

- **Animated stickers (.tgs)**: Lottie-format stickers pass through as-is; WhatsApp requires animated WEBP. Full animated conversion needs a Lottie→WEBP converter library.
- **Private packs**: Only publicly shareable packs work (t.me/addstickers/ links).
- **Rate limits**: Telegram Bot API has generous limits but very large packs may be slow.

---

## License

MIT License — see LICENSE file.
