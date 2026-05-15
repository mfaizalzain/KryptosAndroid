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

# Check editing features
edit = service.edits().insert(packageName=PACKAGE, body={}).execute()
edit_id = edit['id']
print(f"Edit created: {edit_id}")

# Try to check if there's a listing already
try:
    listing = service.edits().listings().get(packageName=PACKAGE, editId=edit_id, language='en-US').execute()
    print(f"Listing title: {listing.get('title', 'NONE')}")
    print(f"Full listing: {json.dumps(listing, indent=2)}")
except Exception as e:
    print(f"No listing yet: {e}")

# Check the app details for country availability
try:
    details = service.edits().details().get(packageName=PACKAGE, editId=edit_id).execute()
    print(f"App details: {json.dumps(details, indent=2)}")
except Exception as e:
    print(f"No details: {e}")

# Try getting the edit itself to see what tracks are listed
try:
    edit_data = service.edits().get(packageName=PACKAGE, editId=edit_id).execute()
    print(f"Edit data keys: {list(edit_data.keys())}")
except Exception as e:
    print(f"Edit get failed: {e}")

# Delete the edit since we're just exploring
service.edits().delete(packageName=PACKAGE, editId=edit_id).execute()
print("Edit deleted (cleanup)")
