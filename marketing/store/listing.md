# Kryptos — Google Play Store Listing

All character limits noted are Google Play's current maximums.

---

## App title (max 30 chars)

**Kryptos — ID & Document Vault**
*(29 chars)*

Alternates:
- `Kryptos: Document Vault & ID` (28)
- `Kryptos — Private Vault` (23)

---

## Short description (max 80 chars)

**Encrypted vault for passports, IDs, cards & notes. NFC scan. Offline.**
*(70 chars)*

Alternates:
- `Secure vault for passports, IDs, cards & notes — AES-256, biometric, offline` (76)
- `Private document vault: scan IDs by camera or NFC. AES-256, on-device.` (70)
- `Zero-knowledge vault for passports, IDs, cards & notes. Encrypted on-device.` (76)

---

## Full description (max 4000 chars)

```
Kryptos is a zero-knowledge personal vault for your most sensitive documents — passports, ID cards, driver's licences, payment cards, API keys, and private notes. Everything is encrypted on your device with AES-256. We never see your data, and we can't.

▸ ZERO-KNOWLEDGE BY DESIGN
Your vault lives in a SQLCipher (AES-256) database. The encryption key is held in the Android Keystore — hardware-backed on supported devices — and gated behind your biometric. There is no Kryptos server that holds your secrets. Even cloud backup uploads only opaque, encrypted bytes.

▸ NFC CHIP READING
Read electronic passports (ICAO 9303 ePassport via JMRTD) and contactless EMV payment cards directly through NFC. 100% accurate, fully offline. Most "vault" apps can't do this.

▸ SMART DOCUMENT SCANNING
Snap a passport, driver's licence, or ID. Google ML Kit's on-device OCR extracts the fields automatically, with locale-aware parsing for Malaysian and international formats. Camera frames never leave your phone.

▸ BEAUTIFUL HERO CARDS
Each document type gets its own purpose-built, high-fidelity card UI — passports, driver's licences, credit cards, IDs, notes, API keys, tax numbers.

▸ PRIVATE ENTRY SHARING
Need to send an entry to someone else using Kryptos? Show a share QR code and they can scan it into their own vault with the correct category and fields filled in. No Kryptos server in the middle.

▸ EXPIRY REMINDERS
Get notified before your passport, ID, or card expires. Scheduled offline via WorkManager — no cloud dependency.

▸ BIOMETRIC UNLOCK
Lock your vault behind Android Biometric (Fingerprint or Face Unlock), backed by hardware keystore.

▸ ENCRYPTED CLOUD BACKUP
Auto-backup your encrypted vault to your own Google Drive. Google Drive can only see encrypted bytes. Nobody but you can decrypt the backup.

▸ MULTI-ACCOUNT ISOLATION
Share one device with family? Each Google account on the device gets its own fully isolated, separately encrypted vault. They never see each other.

────────────────

FREE — UNLIMITED ENTRIES
Kryptos is free, with no entry limit and no subscription. The app is supported by occasional in-app ads displayed alongside your vault. Ads are served by Google AdMob and are NEVER personalised using the contents of your vault — your entries are encrypted on-device and the ad SDK has no access to them.

────────────────

WHY KRYPTOS
Most "secure" vaults are convenient because they hold your data on someone else's server. Kryptos picks the harder path: your data stays on your device, encrypted with a key only you control. The trade-off is that we can never reset your vault for you — but it also means no breach, leak, or subpoena exposes your secrets.

PERMISSIONS WE REQUEST
• Camera — to scan documents with OCR (on-device only)
• NFC — to read ePassports and EMV cards (on-device only)
• Biometric — to unlock your vault
• Internet — for Google Sign-In, Drive backup, and ad delivery
• Notifications — for expiry reminders

WE DO NOT
• Run a server that stores your vault contents
• Use your vault contents to personalise ads
• Sell or share your vault data with anyone
• Hold a copy of your encryption key

Built in Kuala Lumpur. Privacy policy: https://kryptos.faizalmzain.com/privacy.html
```

---

## What's new (max 500 chars) — 1.2.0

```
Kryptos is now free with unlimited entries!

• Removed the 10-entry limit — store as much as you need
• Removed in-app purchase; supported by occasional ads instead
• Ads are never personalised using your vault contents
• Theme & performance polish across the vault list and detail screens
• Bug fixes and stability improvements

Thanks for trusting Kryptos with your most sensitive documents.
```

---

## Categorisation

- **Category:** Tools (primary) — or Productivity
- **Tags:** security, privacy, password manager, document scanner, vault, NFC, biometric, passport, ID

---

## Content rating

- Target age: Everyone
- No user-generated content shared off-device
- **Contains ads:** Yes (Google AdMob, non-personalised against vault contents)
- No location collection
- No in-app messaging

---

## Data safety form (Play Console answers)

**Data collected by Kryptos:**
- Email address (account function) — required to enable Google Drive backup. Encrypted in transit. Not shared.
- Crash logs / diagnostic info (analytics) — anonymous, via Google Play. Optional (user can disable). Encrypted in transit.
- Purchase history — none (no in-app purchases as of 1.2.0).
- Advertising ID and ad interaction data — collected by Google AdMob for ad delivery and frequency capping. Not linked to vault contents.

**Data NOT collected:**
- App contents (your documents/notes/cards) — processed on-device, never transmitted unencrypted, never shared with the ad SDK.
- Photos / camera frames — on-device OCR only.
- Location, contacts, files outside the app sandbox.
- Browsing or app activity outside Kryptos.

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
