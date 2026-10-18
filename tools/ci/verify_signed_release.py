#!/usr/bin/env python3
"""Verify a public, locally signed APK before publishing it with the workflow token."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile


def payload(path):
    with zipfile.ZipFile(path) as archive:
        # A checked-in APK cannot embed the hash of the commit that contains itself.
        # This build-provenance file is not executable code or an application resource.
        return {name: hashlib.sha256(archive.read(name)).hexdigest() for name in archive.namelist()
                if not name.endswith("/") and name != "META-INF/version-control-info.textproto" and not re.fullmatch(
                    r"META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))", name, re.IGNORECASE)}


def compare_payloads(signed, unsigned):
    signed_payload, unsigned_payload = payload(signed), payload(unsigned)
    changed = sorted(name for name in signed_payload.keys() | unsigned_payload.keys()
                     if signed_payload.get(name) != unsigned_payload.get(name))
    if changed:
        raise ValueError("Signed APK payload differs from the release built from this commit: " + ", ".join(changed))


def updating_existing_release():
    if os.environ.get("UPDATE_EXISTING_RELEASE", "false") != "true":
        return False
    if (os.environ.get("GITHUB_ACTOR") != "nirajmahajan"
            or os.environ.get("GITHUB_REPOSITORY") != "nirajmahajan/Judgement-Scoreboard"
            or os.environ.get("GITHUB_REF") != "refs/heads/main"
            or os.environ.get("GITHUB_EVENT_NAME") not in {"push", "workflow_dispatch"}):
        raise ValueError("Existing release refresh requires the repository owner on main")
    return True


def verify():
    tag, commit = os.environ["RELEASE_TAG"], os.environ["GITHUB_SHA"]
    if not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag):
        raise ValueError("Invalid release version")
    refresh = updating_existing_release()
    tagged = subprocess.check_output(["git", "rev-parse", f"refs/tags/{tag}^{{commit}}"], text=True).strip()
    if tagged != commit and not refresh:
        raise ValueError("Release tag does not point to this exact commit")
    if refresh and subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip() != commit:
        raise ValueError("Release refresh must build the exact workflow commit")
    source = Path(f"release-assets/{tag}/Judgement-Scoreboard-{tag}.apk")
    unsigned = Path("app/build/outputs/apk/release/app-release-unsigned.apk")
    compare_payloads(source, unsigned)
    signer = Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0/apksigner"
    certificate = subprocess.check_output([str(signer), "verify", "--verbose", "--print-certs", str(source)], text=True)
    expected = Path("tools/ci/release-certificate.sha256").read_text().strip()
    if not re.fullmatch(r"[0-9a-f]{64}", expected) or f"Signer #1 certificate SHA-256 digest: {expected}" not in certificate:
        raise ValueError("APK is not signed with the new release key")
    manifest = ET.fromstring(subprocess.check_output(["apkanalyzer", "manifest", "print", str(source)], text=True))
    android = "{http://schemas.android.com/apk/res/android}"
    if (manifest.get("package") != "android.example.judgement"
            or manifest.get(android + "versionName") != tag[1:]
            or int(manifest.get(android + "versionCode", "0")) <= 2
            or manifest.find("application").get(android + "debuggable", "false") != "false"):
        raise ValueError("APK must be the non-debug production package and matching version")
    badging = subprocess.check_output([str(signer.with_name("aapt")), "dump", "badging", str(source)], text=True)
    if "application-label:'Judgement Scoreboard'" not in badging:
        raise ValueError("Launcher app name must be Judgement Scoreboard")
    output = Path("release")
    output.mkdir(exist_ok=True)
    shutil.copyfile(source, output / "judgement-scoreboard.apk")
    (output / "certificate.txt").write_text(certificate)
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    (output / "SHA256SUMS").write_text(f"{digest}  judgement-scoreboard.apk\n")
    (output / "verification.json").write_text(json.dumps({
        "commit": commit, "version": tag, "apkSha256": digest,
        "originalTagCommit": tagged, "refreshExistingRelease": refresh,
        "applicationLabel": "Judgement Scoreboard",
        "signingCertificateSha256": expected, "payloadMatchesCommitBuild": True,
        "excludedBuildMetadata": ["META-INF/version-control-info.textproto"],
        "productionSigned": True, "installOverOriginal": False,
        "attribution": "created by chatgpt-codex",
    }, indent=2) + "\n")
    print("Signed release verified: certificate, version, identity, and exact build payload")


if __name__ == "__main__":
    try:
        verify()
    except Exception as error:
        message = str(error).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        print("::error::" + message, flush=True)
        raise
