# Kryptos — Release Build Guide

Everything needed to build, sign, and ship a Kryptos release to Google Play.

---

## Current release

| | |
|---|---|
| **Version name** | 1.0.8 |
| **Version code** | 9 |
| **Application ID** | `com.fmz.kryptos` |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 35 (Android 15) |
| **AAB output** | `app/build/outputs/bundle/release/app-release.aab` |
| **AAB size** | ~35 MB |
| **Signature** | Verified ✓ |

---

## Signing keys

Kryptos uses **Google Play App Signing**. There are two keys involved:

### 1. Upload key (yours — you sign uploads with this)

| | |
|---|---|
| **Keystore path** | `~/keystores/kryptos-upload.jks` |
| **Properties file** | `~/keystores/kryptos-upload.properties` (chmod 600) |
| **Type** | PKCS12 |
| **Algorithm** | RSA 4096 |
| **Validity** | 30 years (10,950 days) |
| **Alias** | `kryptos-upload` |
| **DN** | `CN=Faizal Zain, OU=Kryptos, O=Faizal Zain, L=Kuala Lumpur, ST=WP, C=MY` |
| **Store / key password** | _stored in `~/keystores/kryptos-upload.properties` — keep offline, never commit_ |
| **SHA1** | `CE:2A:C2:5C:06:33:02:A9:7D:D7:D6:BE:71:9E:EB:42:7F:E4:D7:92` |
| **SHA256** | `CD:3E:2F:41:6A:F9:F3:FD:39:ED:79:7D:7B:01:7E:C6:BF:DA:6B:D0:DC:6B:C4:9C:93:67:FA:EB:A8:FA:BA:7A` |

> ⚠️ **Critical:** Back up the `.jks` file AND the password somewhere offline (1Password, an encrypted USB, etc.). If you lose this, you cannot update the app without going through Play's [upload-key reset flow](https://support.google.com/googleplay/android-developer/answer/9842756).

### 2. App signing key (Play-managed — Google signs installs with this)

| | |
|---|---|
| **Managed by** | Google Play App Signing |
| **SHA1** | `21:0E:D0:13:D0:EC:FF:11:9F:CD:71:DD:AC:71:2C:94:6B:22:35:A4` |

This is the fingerprint Google Cloud Console / Firebase must trust so Google Sign-In and Drive work for users installing from Play.

---

## OAuth client setup (Google Cloud Console)

For Google Sign-In and Drive backup to work, both SHA1s must be registered against your OAuth Android client:

1. Open **[Google Cloud Console → APIs & Services → Credentials](https://console.cloud.google.com/apis/credentials)**
2. Edit your Android OAuth client
3. Add **both** SHA-1 certificate fingerprints:
   - `21:0E:D0:13:D0:EC:FF:11:9F:CD:71:DD:AC:71:2C:94:6B:22:35:A4` (Play App Signing — for production users)
   - `CE:2A:C2:5C:06:33:02:A9:7D:D7:D6:BE:71:9E:EB:42:7F:E4:D7:92` (upload key — for sideloaded test builds of the release AAB)
   - Plus your debug keystore SHA1 if you also use Sign-In during development
4. **Package name**: `com.fmz.kryptos`
5. Save and wait ~5 min for propagation

The Web Client ID baked into the app (`706867595241-e3ck7u69mnp2dtgf38vpu22k4ic5pcv9.apps.googleusercontent.com`) must belong to the same Cloud project.

---

## Build configuration

Release signing is wired up in [app/build.gradle.kts](app/build.gradle.kts). It reads keystore credentials from one of three sources, in order:

1. **`KRYPTOS_KEYSTORE_PROPERTIES`** environment variable (path to a `.properties` file) — recommended for CI
2. **`~/keystores/kryptos-upload.properties`** — local default, used by this machine
3. **`keystore.properties`** in the project root — legacy fallback

The properties file format:

```properties
storeFile=/Users/faizalzain/keystores/kryptos-upload.jks
storePassword=<store password>
keyAlias=kryptos-upload
keyPassword=<key password>
```

If none of these files exist, the release build will still compile but will be unsigned.

Release build type has:
- `isMinifyEnabled = true` (R8)
- `isShrinkResources = true`
- Proguard rules in [app/proguard-rules.pro](app/proguard-rules.pro) (keeps SQLCipher, ML Kit, Room)

---

## How to build

```bash
# clean release AAB (for Play Console)
./gradlew :app:bundleRelease

# release APK (for sideload testing only)
./gradlew :app:assembleRelease

# outputs
app/build/outputs/bundle/release/app-release.aab
app/build/outputs/apk/release/app-release.apk
```

To install the release APK on a connected device for smoke-testing:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

To verify a built AAB is signed correctly:

```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab \
  | grep -E "jar verified|CN="
```

---

## How to recreate the upload keystore (reference)

The keystore was generated with:

```bash
keytool -genkeypair -v \
  -keystore ~/keystores/kryptos-upload.jks \
  -alias kryptos-upload \
  -keyalg RSA -keysize 4096 \
  -validity 10950 \
  -storepass "<store password>" \
  -keypass "<key password>" \
  -dname "CN=Faizal Zain, OU=Kryptos, O=Faizal Zain, L=Kuala Lumpur, ST=WP, C=MY"
```

> Note: PKCS12 keystores do not support different store and key passwords — both are set to the store password automatically.

To inspect the keystore:

```bash
keytool -list -v -keystore ~/keystores/kryptos-upload.jks -alias kryptos-upload
```

---

## Pre-release checklist

Before bumping `versionCode` and uploading:

- [ ] `versionCode` incremented in [app/build.gradle.kts](app/build.gradle.kts)
- [ ] `versionName` updated (semver)
- [ ] `RELEASE.md` / `CHANGELOG.md` updated
- [ ] `./gradlew :app:bundleRelease` succeeds locally
- [ ] AAB signature verifies (`jarsigner -verify`)
- [ ] OAuth client has both SHA1s registered
- [ ] Sign-In + Drive backup tested on a Play-installed build (internal testing track)
- [ ] Privacy policy URL reachable: <https://kryptos.faizalmzain.com/privacy.html>
- [ ] Data safety form in Play Console matches reality
- [ ] Store listing copy + screenshots up to date ([marketing/store/listing.md](marketing/store/listing.md))
- [ ] In-app billing product `kryptos_pro` ($1.99 one-time) configured in Play Console

---

## Upload flow (Play Console)

1. Open **[Play Console](https://play.google.com/console)** → your app
2. Go to **Testing → Internal testing** (recommended for first upload)
3. **Create new release** → upload `app-release.aab`
4. Fill **Release name** = `1.0.0 (1)` and **Release notes** from `What's new` in [marketing/store/listing.md](marketing/store/listing.md)
5. Review → start rollout to internal testing
6. Once internal testing is green, promote to **Closed → Open → Production**

For first-ever upload, Play will enroll your AAB into Play App Signing and surface the App Signing SHA1 (already noted above).

---

## CI signing (when you set it up)

Store keystore as a base64-encoded GitHub Actions secret:

```bash
base64 -i ~/keystores/kryptos-upload.jks | pbcopy
# paste into secret KRYPTOS_KEYSTORE_BASE64
```

Add secrets:
- `KRYPTOS_KEYSTORE_BASE64`
- `KRYPTOS_STORE_PASSWORD`
- `KRYPTOS_KEY_PASSWORD` (same value as store password for PKCS12)
- `KRYPTOS_KEY_ALIAS` = `kryptos-upload`

Then in the workflow, decode the keystore and write a temporary props file before running `./gradlew :app:bundleRelease` with `KRYPTOS_KEYSTORE_PROPERTIES=/tmp/keystore.properties`.

---

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `Keystore was tampered with, or password was incorrect` | Wrong password in `kryptos-upload.properties` |
| `jarsigner: unable to open jar file` | Building debug instead of release — use `bundleRelease` not `bundleDebug` |
| Google Sign-In fails on Play-installed build | OAuth client missing the Play App Signing SHA1 |
| Google Sign-In fails on sideloaded release APK | OAuth client missing the upload-key SHA1 |
| Play Console rejects upload with "wrong signature" | The keystore changed; you must use the original upload keystore, or initiate an [upload-key reset](https://support.google.com/googleplay/android-developer/answer/9842756) |
| R8 strips a class at runtime | Add a `-keep` rule in [app/proguard-rules.pro](app/proguard-rules.pro) |

---

## Contacts

- Support: `hello@faizalmzain.com`
- Website: <https://kryptos.faizalmzain.com>
- Privacy: <https://kryptos.faizalmzain.com/privacy.html>
