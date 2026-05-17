# Kryptos — Google Play Store Listing

All character limits noted are Google Play's current maximums.

---

## App title (max 30 chars)

**Kryptos — Private Vault**
*(25 chars)*

Alternates:
- `Kryptos: Zero-Knowledge Vault` (29)
- `Kryptos — Document Vault` (24)

---

## Short description (max 80 chars)

**Encrypted vault for IDs, cards & notes. Share by QR. Yours.**
*(63 chars)*

Alternates:
- `Private vault for IDs, cards & notes. Share entries by QR.` (61)
- `Zero-knowledge vault for IDs, passports, cards & notes. Encrypted on-device.` (76)
- `Your private vault for documents, cards & notes. AES-256, biometric, offline.` (77)

---

## Full description (max 4000 chars)

```
Kryptos is a zero-knowledge personal vault for your most sensitive documents — passports, ID cards, driver's licences, payment cards, API keys, and private notes. Everything is encrypted on your device with AES-256. You can share entries with another Kryptos user by QR when you choose to. We never see your data, and we can't.

▸ ZERO-KNOWLEDGE BY DESIGN
Your vault is stored in a SQLCipher (AES-256) database. The encryption key lives in the Android Keystore — hardware-backed on supported devices — and is gated behind your biometric. There is no Kryptos server that holds your secrets. Even cloud backup uploads only opaque, encrypted bytes.

▸ SMART DOCUMENT SCANNING
Snap a passport, a driver's licence, or an ID. Google ML Kit's on-device OCR extracts the fields automatically, with locale-aware parsing for Malaysian and international formats. Camera frames never leave your phone.

▸ NFC CHIP READING
Read electronic passports (ICAO 9303 ePassport via JMRTD) and contactless EMV payment cards directly through NFC. Useful, fast, fully offline.

▸ BEAUTIFUL HERO CARDS
Each document type gets its own purpose-built, high-fidelity card UI — passports, driver's licences, credit cards, IDs, notes, API keys, tax numbers.

▸ PRIVATE ENTRY SHARING
Need to send an entry to someone else using Kryptos? Show a share QR code and they can scan it into their own vault with the correct category and fields filled in. It works across Android and iOS, without a Kryptos server in the middle.

▸ EXPIRY REMINDERS
Get notified before your passport, ID, or card expires. Scheduled offline via WorkManager — no cloud dependency.

▸ BIOMETRIC UNLOCK
Lock your vault behind Android Biometric (Fingerprint or Face Unlock), backed by hardware keystore.

▸ ENCRYPTED CLOUD BACKUP
Auto-backup your encrypted vault to your own Google Drive:
• Free: hidden Drive AppData folder
• Pro: a visible "KryptosBackups" folder, easier to manage and copy off-device

Google Drive can only see encrypted bytes. Nobody but you can decrypt the backup.

▸ MULTI-ACCOUNT ISOLATION
Share one device with family? Each Google account on the device gets its own fully isolated, separately encrypted vault. They never see each other.

────────────────

PRICING — SIMPLE & FAIR
• Free: store up to 10 entries, full features, no ads.
• Kryptos Pro: $1.99 one-time. Unlimited entries, visible Drive backup, priority support, and all future Pro features included.

No subscriptions. No recurring fees. Pay once, own it forever.

────────────────

WHY KRYPTOS
Most "secure" vaults are convenient because they hold your data on someone else's server. Kryptos picks the harder path: your data stays on your device, encrypted with a key only you control. The trade-off is that we can never reset your vault for you — but it also means no breach, leak, or subpoena exposes your secrets.

PERMISSIONS WE REQUEST
• Camera — to scan documents with OCR (on-device only)
• NFC — to read ePassports and EMV cards (on-device only)
• Biometric — to unlock your vault
• Internet — only for Google Sign-In, Drive backup, Play purchase verification
• Notifications — for expiry reminders

WE DO NOT
• Run a server that stores your vault contents
• Show ads or embed advertising SDKs
• Sell or share your data
• Hold a copy of your encryption key

Built in Kuala Lumpur. Privacy policy: https://kryptos.faizalmzain.com/privacy.html
```

---

## What's new (max 500 chars) — first release

```
Welcome to Kryptos!

• Zero-knowledge vault with SQLCipher AES-256 encryption
• OCR scanning for passports, IDs, driver's licences (Malaysian + international)
• Share entries by QR with category + fields included
• NFC reading for ePassports and EMV cards
• Biometric unlock & expiry reminders
• Encrypted backup to your own Google Drive
• Multi-account isolation on shared devices
• Pro unlock — $1.99 one-time, no subscriptions
```

---

## Categorisation

- **Category:** Tools (primary) — or Productivity
- **Tags:** security, privacy, password manager, document scanner, vault, NFC, biometric

---

## Content rating

- Target age: Everyone
- No user-generated content shared off-device
- No ads
- No location collection
- No in-app messaging

---

## Data safety form (Play Console answers)

**Data collected by Kryptos:**
- Email address (account function) — required to enable Google Drive backup. Encrypted in transit. Not shared.
- Crash logs / diagnostic info (analytics) — anonymous, via Google Play. Optional (user can disable). Encrypted in transit.
- Purchase history (purchases) — Google Play Billing only. Encrypted in transit.

**Data NOT collected:**
- App contents (your documents/notes/cards) — processed on-device, never transmitted unencrypted.
- Photos / camera frames — on-device OCR only.
- Location, contacts, files outside the app sandbox.
- Browsing or app activity.

**Security practices:**
- ✅ Data is encrypted in transit
- ✅ Data is encrypted at rest (AES-256, SQLCipher)
- ✅ Users can request data deletion (uninstall + Drive backup deletion)
- ✅ Independent security review: not yet — uses standard primitives (SQLCipher, Android Keystore)

---

## Required assets — at a glance

| Asset | Size | File |
|---|---|---|
| App icon | 512×512 PNG (32-bit) | `icon-512.svg` → export PNG |
| Feature graphic | 1024×500 PNG / JPG | `feature-graphic.svg` → export PNG |
| Phone screenshots | min 1080px on short side, 16:9 or 9:16, 2–8 images | `screenshot-1.svg` … `screenshot-5.svg` |
| Promo video (optional) | YouTube URL | — |

**Exporting SVG → PNG**
- Open the SVG in Chrome → Cmd+Shift+P → "Capture screenshot" → "Capture full-size screenshot"
- Or: `rsvg-convert -w 1024 feature-graphic.svg -o feature-graphic.png`
- Or: open in Figma / Sketch / Affinity and export at 1× / 2×

---

## Contact email (Play listing)

`hello@faizalmzain.com`

## Website

`https://kryptos.faizalmzain.com`

## Privacy policy URL

`https://kryptos.faizalmzain.com/privacy.html`
