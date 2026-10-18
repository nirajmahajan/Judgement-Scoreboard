#!/usr/bin/env python3
"""Publish only after CI, owner dispatch, certificate checks, and manual acceptance."""
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request
from verify_signed_release import updating_existing_release

repository = os.environ["GITHUB_REPOSITORY"]
commit = os.environ["TESTED_COMMIT"]
tag = os.environ["RELEASE_TAG"]
assert re.fullmatch(r"[0-9a-f]{40}", commit)
assert os.environ["GITHUB_ACTOR"] == "nirajmahajan"
root = Path("release")
assert (root / "judgement-scoreboard.apk").is_file()
new_key = os.environ.get("RELEASE_SIGNING_MODE") == "new"
refresh = updating_existing_release()
if refresh and (not new_key or os.environ.get("GITHUB_SHA") != commit):
    raise SystemExit("Existing release refresh requires a new-key APK verified at the exact workflow commit")
body = f"""Judgement Scoreboard 2.0

- Repaired predictions, scoring, lifecycle state, and legacy data recovery.
- Added a saved-player library and accessible drag ordering.
- Rebuilt the compact interface with blue headers, card artwork, and light/dark themes.

Exact tested commit: `{commit}`

Test and manual acceptance evidence: {os.environ['EVIDENCE_RUN']}

Physical-device, TalkBack, and production-certificate upgrade acceptance were confirmed by the owner for this commit before publication.

created by chatgpt-codex
"""
if new_key:
    verification = json.loads((root / "verification.json").read_text())
    assert verification["commit"] == commit and verification["productionSigned"]
    if refresh and (not verification.get("payloadMatchesCommitBuild")
                    or verification.get("version") != tag
                    or not verification.get("refreshExistingRelease")
                    or not re.fullmatch(r"[0-9a-f]{40}", verification.get("originalTagCommit", ""))
                    or verification.get("apkSha256") != hashlib.sha256((root / "judgement-scoreboard.apk").read_bytes()).hexdigest()):
        raise SystemExit("Existing release refresh requires complete verification of this exact APK and source commit")
    body = f"""Judgement Scoreboard {tag}

- Compact blue interface, saved players, drag ordering, and fast 0/1/2/3 predictions.
- Responsive text and controls for smaller screens, display zoom, and large fonts.
- Scoring strategies, durable game state, editable round history, and cumulative score graph.
- Repaired scoring, results validation, lifecycle behavior, and large-score/graph layouts.
- Launcher app name: **Judgement Scoreboard**.
- Saved-player selection stays in place; each saved name has its own delete button. Removing a saved name preserves game participants and scores.
- Dealer and result selections stay in place; taps on names work.
- Prediction keyboards keep the player list usable; Aggressive text meets the contrast check.
- Restored forms keep one dialog window and preserve edits during rotation.
- Clearer scoring validation and consistent rounds, cards, hands, and prediction wording.
- Named custom scoring strategies persist, with dropdown selection and Modify/Delete controls.
- Roster changes preserve completed scores, step descending deals down to fit, and finish ascending games beyond the new limit.
- Final results show rounds played and points per round when participation differs, and rank by points per round. Score graphs show gaps for absent rounds.

Download **judgement-scoreboard.apk** below. Requires Android 8 or later.

**Installation:** uninstall the original Judgement app first, then install this APK. This release uses a new signing key and cannot update the old installation. Uninstalling removes the old app's local data. Future releases will reuse the new key so they can update v2 normally.

Already using v2.0.0? Install this APK over it to update without uninstalling.

Source commit: `{commit}`
APK signature, production identity, version, and payload matching the source build were verified by the [release action]({os.environ['EVIDENCE_RUN']}). Checksums and the signing certificate are attached. Physical-device/TalkBack acceptance is not claimed.

created by chatgpt-codex
"""
    if refresh:
        body = body.replace(f"Source commit: `{commit}`", f"Updated APK source commit: `{commit}`\nOriginal release tag commit (unchanged): `{verification['originalTagCommit']}`")
(root / "CHANGELOG.md").write_text(body)
evidence = {
    "commit": commit, "run": os.environ["EVIDENCE_RUN"],
    "owner": os.environ["GITHUB_ACTOR"],
    "physicalAcceptance": True, "talkBackAcceptance": True,
    "productionUpgradeAcceptance": True,
    "attribution": "created by chatgpt-codex",
}
if new_key:
    evidence = verification | {"run": os.environ["EVIDENCE_RUN"],
                               "physicalAcceptance": False, "talkBackAcceptance": False,
                               "productionUpgradeAcceptance": False}
(root / "test-evidence.json").write_text(json.dumps(evidence, indent=2))
headers = {"Authorization": "Bearer " + os.environ["GITHUB_TOKEN"],
           "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
api = f"https://api.github.com/repos/{repository}"
release = None
page = 1
while release is None:
    releases = json.load(urllib.request.urlopen(urllib.request.Request(
        api + f"/releases?per_page=100&page={page}", headers=headers)))
    release = next((item for item in releases if item["tag_name"] == tag), None)
    if len(releases) < 100:
        break
    page += 1
if refresh and (release is None or release["draft"]):
    raise SystemExit("Existing release refresh requires an already published release; refusing to create or publish one")
if not refresh and release is not None and (not release["draft"] or (not new_key and release["target_commitish"] != commit)):
    raise SystemExit("Release already published or draft targets another commit; refusing to overwrite it")
payload = json.dumps({"tag_name": tag, "target_commitish": commit,
                      "name": f"Judgement Scoreboard {tag}", "body": body,
                      "draft": True, "prerelease": False}).encode()
if refresh:
    # Keep the release public throughout asset replacement, and never move its tag.
    pass
elif release is None:
    request = urllib.request.Request(api + "/releases", data=payload,
                                     headers=headers | {"Content-Type": "application/json"}, method="POST")
    release = json.load(urllib.request.urlopen(request))
else:
    request = urllib.request.Request(api + "/releases/" + str(release["id"]), data=payload,
                                     headers=headers | {"Content-Type": "application/json"}, method="PATCH")
    release = json.load(urllib.request.urlopen(request))
draft_only_assets = {"RELEASE_NOTES.md", "release-evidence.json", "original-certificate.txt",
                     f"Judgement-Scoreboard-{tag}-debug-test.apk", f"Judgement-Scoreboard-{tag}-release-unsigned.apk"}
for asset in release["assets"]:
    if asset["name"] in draft_only_assets:
        urllib.request.urlopen(urllib.request.Request(asset["url"], headers=headers, method="DELETE")).read()
for name in ["judgement-scoreboard.apk", "SHA256SUMS", "CHANGELOG.md", "test-evidence.json", "certificate.txt"]:
    for asset in release["assets"]:
        if asset["name"] == name:
            urllib.request.urlopen(urllib.request.Request(asset["url"], headers=headers, method="DELETE")).read()
    content = (root / name).read_bytes()
    upload = release["upload_url"].split("{")[0] + "?name=" + urllib.parse.quote(name)
    request = urllib.request.Request(upload, data=content,
                                     headers=headers | {"Content-Type": "application/octet-stream"}, method="POST")
    urllib.request.urlopen(request).read()
update = {"name": f"Judgement Scoreboard {tag}", "body": body, "draft": False, "prerelease": False} if refresh else {"draft": False}
request = urllib.request.Request(api + "/releases/" + str(release["id"]),
                                 data=json.dumps(update).encode(),
                                 headers=headers | {"Content-Type": "application/json"}, method="PATCH")
release = json.load(urllib.request.urlopen(request))
print("Published owner-approved release: " + release["html_url"])
