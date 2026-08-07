# Kryptos Backup Format — Cross-Platform Contract

> **Status:** living spec. Update this file whenever either platform's backup
> code changes. Both implementations must agree on anything documented here.

## Purpose

Kryptos stores an encrypted vault on-device and backs it up to the user's own
Google Drive. This document is the contract between the **Android**
(`backup/DriveBackupManager.kt`) and **iOS** (`Services/DriveBackupService.swift`)
implementations, so the two never drift silently.

## Critical rule

**There is NO cross-platform restore.** The Android and iOS formats are
completely different (see below). A user moving from Android to iOS cannot
restore their Android backup on iOS, and vice versa. If cross-platform restore
is ever desired, that is a new feature requiring a shared format + migration
path — not a bug fix.

## Where backups live

| | Android | iOS |
|---|---|---|
| Default space | Drive `appDataFolder` (hidden, private) | Drive `appDataFolder` |
| "My Drive" option | `KryptosBackups` folder (visible) | `kryptos` folder in My Drive (`toMyDrive: true`) |
| iCloud | — (not supported) | iCloud backup path (separate feature) |

## File layout

### Android

Per-account files use a sanitized user-id suffix (`kryptos_<id>.<ext>`); the
no-account case uses the bare names. Sanitization: non-`[a-zA-Z0-9]` → `_`.

| File | Contents |
|---|---|
| `kryptos.db` / `kryptos_<id>.db` | SQLCipher-encrypted Room database (`application/zip`, raw bytes) |
| `kryptos.key` / `kryptos_<id>.key` | Encryption key JSON (`application/json`) |
| `kryptos.meta.json` / `kryptos_<id>.meta.json` | Metadata: entry count, timestamp, source (`application/json`) |
| `kryptos.backup` / `kryptos_<id>.backup` | Combined bundle (legacy / My-Drive path) |

### iOS

| File | Contents |
|---|---|
| `kryptos-ios-vault.json` | Single JSON blob: encrypted vault payload (`application/json`) |
| `kryptos-ios.key` | Encryption key (`application/octet-stream`) |

## Metadata shape (Android `meta.json`)

```json
{
  "v": 1,
  "entryCount": 42,
  "userId": "user-id-or-null",
  "createdAtMillis": 1780000000000
}
```

(Source: `DriveBackupManager.uploadMetadata` — Android.)

## Versioning rule

- The **Android** format has a `v` field in `meta.json`. On format change,
  bump it and keep read-backward-compatibility for at least one version.
- The **iOS** format has no explicit version field — any future format change
  MUST add one before breaking existing backups.
- File names, MIME types, and space selection (`appDataFolder` vs My Drive)
  are part of the contract. Do not rename without a migration.

## Drive API notes

- `appDataFolder` files are hidden from the user's Drive UI — intentional.
- Queries: `name='<file>' and trashed=false`, ordered by `modifiedTime desc`,
  `fields=files(id,name,modifiedTime,mimeType)`, `pageSize=20`.
- Files are matched by name in `appDataFolder`; replacement = delete old + upload new
  (Drive does not allow overwrite-by-name in appDataFolder).

## Test coverage

- Android: `QrPayloadsTest` / `FieldsCodecTest` cover serialization primitives.
  Drive manager itself has no unit tests (network-bound) — cover the format
  helpers when extracted.
- iOS: `QRPayloadBuilderTests` covers QR payloads; Drive backup has no tests yet.
