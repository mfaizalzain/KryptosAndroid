# Kryptos

Kryptos is a zero-knowledge personal vault for Android, designed to securely store and manage your most sensitive documents—passports, ID cards, payment cards, and private notes.

## Features

-   **Zero-Knowledge Security**: Your data is encrypted locally using SQLCipher with AES-256 encryption. Encryption keys are stored securely in the Android Keystore.
-   **Smart Document Scanning**:
    -   **AI-Powered OCR**: Automatically extract fields from physical documents using Google ML Kit.
    -   **NFC Chip Reading**: Securely read electronic passport (ePassport) chips and EMV payment cards directly via NFC for 100% accuracy.
    -   **QR Code Duplicator**: Scan and store existing QR codes to use Kryptos as a digital duplicator.
-   **Universal Expiry Reminders**: 
    -   Automatically detects expiry dates in any entry.
    -   Get notified 6 months, 1 month, or 1 week before expiration (customizable per category).
    -   Toggle reminders on or off via the settings.
-   **Beautiful Hero Cards**: High-fidelity UI representations for all document types including Passports, Driver's Licenses, Credit Cards, and API keys.
-   **Cloud Backup & Restore**:
    -   **Encrypted AppData Backup**: Securely back up your vault to a private, hidden folder in your Google Drive.
    -   **Cross-Device Restore**: Seamlessly restore your vault on new devices using your Google account.
-   **Pro Version (One-Time Upgrade)**:
    -   **Unlimited Entries**: Remove the free tier limit.
    -   **Advanced Backup**: Backup your encrypted vault directly to a visible folder in "My Drive" for easier manual management.
    -   **Support Development**: Help us build more privacy-focused features.
-   **Privacy First**:
    -   **Biometric Protection**: Secure your vault with Fingerprint or Face Unlock.
    -   **Auto-Clear Clipboard**: Sensitive data is automatically cleared from the clipboard after 30 seconds.
    -   **Multi-User Isolation**: Supports multiple Google accounts with completely isolated encrypted databases.

## Tech Stack

-   **Language**: Kotlin
-   **UI**: Jetpack Compose
-   **Database**: Room with SQLCipher (AES-256)
-   **OCR/Scanning**: Google ML Kit & Google Document Scanner
-   **NFC**: JMRTD (Passport) & EMV reading
-   **Auth**: Google Identity (Credential Manager)
-   **Billing**: Google Play Billing Library
-   **Background Tasks**: WorkManager for reliable notifications

## Getting Started

1.  Clone the repository.
2.  Add your `google-services.json` to the `app/` directory.
3.  Ensure your environment has the necessary Google Play Services dependencies.
4.  Build and run using Android Studio.

## License

Copyright © 2024 Faizal Zain. All rights reserved.
