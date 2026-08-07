# Kryptos (Android)

A privacy-first digital vault for your identity documents, payment cards, and sensitive records — built with Kotlin and Jetpack Compose. Store everything encrypted on-device, back up to your own Google Drive, and unlock with biometrics.

## ✨ Features

- **Zero-knowledge security** — all entries encrypted with SQLCipher (AES-256); keys live in the Android Keystore and never leave the device.
- **Biometric unlock** — fingerprint / face unlock with a secure lock screen.
- **10 built-in templates** — ID card, passport, driver's license, birth certificate, payment card, bank account, tax number, API key, note, and QR code.
- **Smart hero cards** — payment cards render with masked numbers and expiry badges; documents show photo slots and key-value fields (Compose).
- **Smart document scanning**:
  - **AI-powered OCR** — extract fields from physical documents via Google ML Kit + Document Scanner.
  - **NFC chip reading** — read ePassport chips (JMRTD) and EMV payment cards directly via NFC.
  - **QR duplicator** — scan and store existing QR codes; generate codes for contacts (vCard), Wi-Fi, email, SMS, geo, calendar, and payments.
- **Cloud backup & restore** — encrypted vault + recovery key backed up to a private hidden folder in your Google Drive (`appDataFolder`), per Google account, with cross-device restore.
- **Expiry reminders** — automatic expiry detection (6 months / 1 month / 1 week ahead) via WorkManager local notifications; toggle per category.
- **Secure clipboard** — copied values auto-clear after 30 seconds.
- **Multi-user isolation** — multiple Google accounts with fully isolated encrypted databases.
- **Remove Ads** — one-time purchase (Google Play Billing) to hide native ads.

## 🗂 Project structure

```
app/src/main/kotlin/com/kryptos/vault/
├── KryptosApp.kt / MainActivity.kt   # App entry, DI wiring
├── BillingManager.kt                 # Play Billing (Remove Ads IAP)
├── data/                             # Room + SQLCipher persistence
│   ├── VaultDatabase.kt / VaultDao.kt / VaultRepository.kt
│   ├── VaultEntry.kt / FieldsCodec.kt / Converters.kt
│   ├── KeyManager.kt                 # Android Keystore key handling
│   └── TemplateShareIds.kt
├── backup/
│   └── DriveBackupManager.kt         # Google Drive backup/restore
├── security/
│   ├── AuthManager.kt                # Google Sign-In (Credential Manager)
│   ├── BiometricAuth.kt
│   └── SecureClipboard.kt            # 30s auto-clear clipboard
├── nfc/
│   ├── PassportNfcReader.kt          # ePassport chip (JMRTD)
│   └── CardNfcReader.kt              # EMV payment cards
├── ocr/
│   ├── OcrScanner.kt                 # ML Kit text recognition
│   └── OcrParsers.kt                 # Field extraction rules
├── notif/
│   ├── ExpiryScheduler.kt            # Reminder computation
│   └── ExpiryReminderWorker.kt       # WorkManager notifications
└── ui/
    ├── KryptosNavGraph.kt            # Navigation
    ├── VaultViewModel.kt             # Screen state
    ├── account/                      # AccountSheet + sections + Drive flow
    ├── cards/                        # Hero/compact card composables
    ├── detail/  edit/  list/  lock/  # Entry detail, editor, list, lock screen
    ├── scan/                         # QR scan/generate/share
    ├── nfc/                          # NFC scan screens
    ├── components/  theme/           # Shared components, Material 3 theme
```

## 🛠 Requirements

- **Android Studio** (latest stable) with **JDK 21+** — Gradle 9.7 toolchain
- **compileSdk 36, minSdk 26, targetSdk 36**
- **Kotlin 2.3.21** with KSP 2.3.11 (Room codegen)
- Dependencies managed via the version catalog in `gradle/libs.versions.toml` (AGP 9.3.1, Compose BOM 2026.06.01, Room 2.8.4, SQLCipher 4.17, JMRTD 0.8.7, CameraX 1.6.1)

## 🚀 Getting started

```bash
git clone https://github.com/mfaizalzain/KryptosAndroid.git
cd KryptosAndroid
```

Open the project in Android Studio (or `./gradlew assembleDebug` from the CLI — set `JAVA_HOME` to a JDK 21+ if `/usr/bin/java` is a stub). Google Sign-In needs your `google-services.json` in `app/` (gitignored) and the Play services config for your package `com.fmz.kryptos`.

## 🔐 Security notes

- SQLCipher database encrypted with AES-256; master keys in the Android Keystore (hardware-backed where available).
- Drive backups are encrypted and per-account (`kryptos_<userId>.db` + `.key` + `.meta.json`); the recovery key is required to restore on a new device.
- Clipboard auto-clears 30s after copying any field value.
- Multiple Google accounts each get an isolated encrypted database.

## 🧪 Testing

`./gradlew test` runs the unit test suite (`app/src/test/`). Currently focused on pure-logic modules — QR payload builders, field codecs, and card masking. UI tests (Compose) are a planned next step.

## 📦 Releases

Release builds use R8 minification (`isMinifyEnabled = true`) with rules in `app/proguard-rules.pro` — always smoke-test a release build on device after touching NFC/JMRTD or reflection-heavy code.

## 📄 License

Private repository — all rights reserved. Copyright © 2024–2026 Faizal Zain.
