import json, os
from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build

creds = Credentials.from_authorized_user_file(
    os.path.expanduser('~/.hermes/google_token.json'),
    ['https://www.googleapis.com/auth/cloud-platform', 'https://www.googleapis.com/auth/androidpublisher']
)
if creds.expired and creds.refresh_token:
    creds.refresh(Request())

# Try cloudresourcemanager API
try:
    crm = build('cloudresourcemanager', 'v1', credentials=creds)
    proj = crm.projects().get(projectId='806577312359').execute()
    print('Project:', json.dumps(proj, indent=2)[:500])
except Exception as e:
    print(f'CRM error: {e}')
