#!/usr/bin/env python3
"""Prepare clearly labelled test and unsigned APKs without claiming upgrade signing."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess


def prepare(root, tag, commit, manifest, original_certificate):
    if not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag):
        raise ValueError("Expected a version such as v2.0.0")
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Expected an exact source commit")
    android = "{http://schemas.android.com/apk/res/android}"
    if (manifest.get("package") != "android.example.judgement"
            or manifest.get(android + "sharedUserId") != "com.example.nirajmahajan64725"
            or int(manifest.get(android + "versionCode", "0")) <= 2):
        raise ValueError("Release APK does not preserve the original upgrade identity")
    if manifest.get(android + "versionName") != tag[1:]:
        raise ValueError("Release tag must match the APK version name")
    expected = "93e366b93ccd1c531e9a282a096ccaa9bbd719d9b8c1271e7e5384f7484bda50"
    if f"Signer #1 certificate SHA-256 digest: {expected}" not in original_certificate:
        raise ValueError("Bundled original APK has an unexpected signing certificate")
    output = root / "release-draft"
    output.mkdir(exist_ok=True)
    files = {}
    for variant, source in [
        ("debug-test", "app/build/outputs/apk/debug/app-debug.apk"),
        ("release-unsigned", "app/build/outputs/apk/release/app-release-unsigned.apk"),
    ]:
        name = f"Judgement-Scoreboard-{tag}-{variant}.apk"
        shutil.copyfile(root / source, output / name)
        files[name] = hashlib.sha256((output / name).read_bytes()).hexdigest()
    (output / "SHA256SUMS").write_text("".join(f"{digest}  {name}\n" for name, digest in sorted(files.items())))
    (output / "original-certificate.txt").write_text(original_certificate)
    (output / "release-evidence.json").write_text(json.dumps({
        "commit": commit, "version": tag, "versionCode": int(manifest.get(android + "versionCode")),
        "builds": "debug and unsigned release built; APK identities verified",
        "productionSigned": False, "installOverOriginal": False,
        "originalCertificateSha256": expected, "sha256": files,
        "attribution": "created by chatgpt-codex",
    }, indent=2) + "\n")
    (output / "RELEASE_NOTES.md").write_text(f"""## Judgement Scoreboard {tag}

- Repaired game state, predictions, results validation, scoring, and legacy score recovery.
- Added saved players, quick-add, drag ordering, and accessible move actions.
- Added scoring strategies, editable round history, and a final cumulative score graph.
- Refreshed the compact interface with blue headers, clearer controls, and card artwork.
- Added build, regression, screenshot, and Android emulator checks.

### APKs and update compatibility

This draft is awaiting the original app's signing key.

- **debug-test.apk** is installable for testing as a separate app. It does **not** update the old Judgement app.
- **release-unsigned.apk** retains the original package ID and shared-user identity, with version code 3. It must be signed with the original key before installation or publication as an update.
- No original signing key, passwords, or private credentials are included.

Source commit: `{commit}`. SHA-256 checksums and packaging evidence are attached.
Production-certificate installation and physical-device acceptance have not been completed.

created by chatgpt-codex
""")
    return output


if __name__ == "__main__":
    import xml.etree.ElementTree as ET
    root = Path.cwd()
    tag = os.environ["RELEASE_TAG"]
    commit = os.environ["GITHUB_SHA"]
    tagged = subprocess.run(["git", "rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}"],
                            text=True, capture_output=True)
    if tagged.returncode == 0 and tagged.stdout.strip() != commit:
        raise SystemExit("Existing tag points to another commit; refusing to release different code")
    manifest = ET.fromstring(subprocess.check_output([
        "apkanalyzer", "manifest", "print", "app/build/outputs/apk/release/app-release-unsigned.apk"], text=True))
    certificate = subprocess.check_output([
        str(Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0/apksigner"),
        "verify", "--print-certs", "app/release/app-release.apk"], text=True)
    print(prepare(root, tag, commit, manifest, certificate))
