# Kryptos

Kryptos is a zero-knowledge personal vault for Android, designed to securely store and manage your most sensitive documents—passports, ID cards, payment cards, and private notes.

## Features

-   **Zero-Knowledge Security**: Your data is encrypted locally using SQLCipher with AES-256 encryption. We never see your plaintext data.
-   **Smart Document Scanning**:
    -   **OCR Integration**: Automatically extract fields from physical documents using Google ML Kit.
    -   **NFC Chip Reading**: Securely read electronic passport (ePassport) chips and EMV payment cards directly via NFC.
-   **Beautiful Hero Cards**: Dedicated, high-fidelity UI representations for different document types (Passports, Driver's Licenses, Credit Cards, etc.).
-   **Multi-User Isolation**: Supporting multiple Google accounts on a single device with isolated, per-user encrypted databases.
-   **Cloud Backup & Restore**:
    -   **Hidden AppData Backup**: Automatic zero-knowledge backup to a private, hidden folder in your Google Drive.
    -   **Pro: Backup to My Drive**: Premium feature to back up your encrypted vault to a visible "KryptosBackups" folder for easier management.
-   **Expiry Reminders**: Integrated with WorkManager to notify you before your documents (passports, cards, licenses) expire.
-   **Biometric Protection**: Secure your vault with Android Biometrics (Fingerprint/Face Unlock).

## Tech Stack

-   **Language**: Kotlin
-   **UI**: Jetpack Compose
-   **Database**: Room with SQLCipher (AES-256)
-   **OCR/Scanning**: Google ML Kit & Google Document Scanner
-   **NFC**: JMRTD (Passport) & EMV reading
-   **Auth**: Google Identity (Credential Manager)
-   **Architecture**: MVVM with Clean Architecture principles

## Getting Started

1.  Clone the repository.
2.  Add your `google-services.json` to the `app/` directory.
3.  Add your Google Web Client ID to `strings.xml`.
4.  Build and run using Android Studio.

## License

Copyright © 2024 Faizal Zain. All rights reserved.
