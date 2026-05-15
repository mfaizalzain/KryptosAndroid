import json, os
from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE = 'com.fmz.kryptos'
AAB_PATH = os.path.expanduser('~/KryptosAndroid/app/build/outputs/bundle/release/app-release.aab')

creds = Credentials.from_authorized_user_file(
    os.path.expanduser('~/.hermes/google_token.json'),
    ['https://www.googleapis.com/auth/androidpublisher']
)
if creds.expired and creds.refresh_token:
    creds.refresh(Request())

service = build('androidpublisher', 'v3', credentials=creds)

edit = service.edits().insert(packageName=PACKAGE, body={}).execute()
edit_id = edit['id']
print(f"Edit: {edit_id}")

media = MediaFileUpload(AAB_PATH, mimetype='application/octet-stream', resumable=True)
bundle = service.edits().bundles().upload(
    packageName=PACKAGE, editId=edit_id, media_body=media
).execute()
vc = bundle['versionCode']
print(f"AAB uploaded: v{vc}")

notes = "\u2022 Fixed profile picture not showing on release builds\n\u2022 More reliable profile picture extraction from ID token"

prod = {
    'versionCodes': [vc],
    'releaseNotes': [{'language': 'en-US', 'text': notes}],
    'status': 'completed',
}
service.edits().tracks().update(
    packageName=PACKAGE, editId=edit_id, track='production',
    body={'track': 'production', 'releases': [prod]}
).execute()
print("Production updated")

commit = service.edits().commit(packageName=PACKAGE, editId=edit_id).execute()
print(f"v{vc} (1.0.5) live on production!")
