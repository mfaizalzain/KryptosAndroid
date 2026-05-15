import json, os
from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build

PACKAGE = 'com.fmz.kryptos'
AAB_PATH = os.path.expanduser('~/KryptosAndroid/app/build/outputs/bundle/release/app-release.aab')

creds = Credentials.from_authorized_user_file(
    os.path.expanduser('~/.hermes/google_token.json'),
    ['https://www.googleapis.com/auth/androidpublisher']
)
if creds.expired and creds.refresh_token:
    creds.refresh(Request())

service = build('androidpublisher', 'v3', credentials=creds)

# 1. Create edit
edit = service.edits().insert(packageName=PACKAGE, body={}).execute()
edit_id = edit['id']
print(f"Edit created: {edit_id}")

# 2. Upload AAB
from googleapiclient.http import MediaFileUpload
media = MediaFileUpload(AAB_PATH, mimetype='application/octet-stream', resumable=True)
bundle = service.edits().bundles().upload(
    packageName=PACKAGE,
    editId=edit_id,
    media_body=media
).execute()
version_code = bundle['versionCode']
print(f"Uploaded bundle version code: {version_code}")

# 3. Set release notes
release_notes_text = (
    "\u2022 Fixed profile picture not showing on Play Store builds\n"
    "\u2022 Billing flow improvements with better error logging"
)

# 4. Update production track (now that countries are configured, completed works)
prod_release = {
    'versionCodes': [version_code],
    'releaseNotes': [{'language': 'en-US', 'text': release_notes_text}],
    'status': 'completed',
}

service.edits().tracks().update(
    packageName=PACKAGE,
    editId=edit_id,
    track='production',
    body={'track': 'production', 'releases': [prod_release]}
).execute()
print("Production track updated")

# 5. Commit
commit = service.edits().commit(packageName=PACKAGE, editId=edit_id).execute()
print(f"✅ v{version_code} live on production!")
for t in commit.get('tracks', []):
    for r in t.get('releases', []):
        print(f"  {t['track']} \u2192 v{r['versionCodes']} ({r['status']})")
