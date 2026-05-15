import json, os
from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build

PACKAGE = 'com.fmz.kryptos'

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

# Get all available tracks to check structure
for track_name in ['internal', 'alpha', 'beta', 'production']:
    try:
        t = service.edits().tracks().get(packageName=PACKAGE, editId=edit_id, track=track_name).execute()
        print(f"\n{track_name}:")
        print(json.dumps(t, indent=2))
    except:
        print(f"\n{track_name}: error fetching")

service.edits().delete(packageName=PACKAGE, editId=edit_id).execute()
print("\nEdit deleted")
