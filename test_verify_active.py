import json
import requests

req = {
    "repo_path": "target_repo",
    "speciality": "Pagination",
    "active_file": "THIS IS A COMPLETELY FAKE FILE CONTENT THAT HAS THE BUG skip."
}

resp = requests.post("http://localhost:8000/verify", json=req)
print(resp.json())
