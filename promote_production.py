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

# Create edit
edit = service.edits().insert(packageName=PACKAGE, body={}).execute()
edit_id = edit['id']
print(f"Edit: {edit_id}")

# Try countryavailability
try:
    ca = service.edits().countryavailability().get(
        packageName=PACKAGE,
        editId=edit_id
    ).execute()
    print(f"Current country availability: {json.dumps(ca, indent=2)}")
except Exception as e:
    print(f"Get country availability failed: {e}")

# Try setting country availability
try:
    ca_update = service.edits().countryavailability().update(
        packageName=PACKAGE,
        editId=edit_id,
        body={
            "availability": {
                "trackWise": {
                    "production": {
                        "countries": [],
                        "includeRestOfWorld": True
                    }
                }
            }
        }
    ).execute()
    print(f"Country availability updated: {json.dumps(ca_update, indent=2)}")
except Exception as e:
    print(f"Update country availability failed: {e}")

# Now try promote
alpha = service.edits().tracks().get(packageName=PACKAGE, track='alpha', editId=edit_id).execute()
releases = alpha.get('releases', [])
version_codes = releases[0]['versionCodes']
release_notes = releases[0]['releaseNotes']

prod_release = {
    'versionCodes': version_codes,
    'releaseNotes': release_notes,
    'status': 'completed',
}

result = service.edits().tracks().update(
    packageName=PACKAGE,
    editId=edit_id,
    track='production',
    body={'track': 'production', 'releases': [prod_release]}
).execute()
print(f"Production track updated: {result['track']}")

# Commit
commit = service.edits().commit(packageName=PACKAGE, editId=edit_id).execute()
print(f"✅ Production released!")
for t in commit.get('tracks', []):
    for r in t.get('releases', []):
        print(f"  {t['track']} → v{r['versionCodes']} ({r['status']})")
