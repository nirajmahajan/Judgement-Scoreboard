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
        return {name: hashlib.sha256(archive.read(name)).hexdigest() for name in archive.namelist()
                if not name.endswith("/") and not re.fullmatch(
                    r"META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))", name, re.IGNORECASE)}


def compare_payloads(signed, unsigned):
    if payload(signed) != payload(unsigned):
        raise ValueError("Signed APK payload differs from the release built from this commit")


def verify():
    tag, commit = os.environ["RELEASE_TAG"], os.environ["GITHUB_SHA"]
    if not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag):
        raise ValueError("Invalid release version")
    tagged = subprocess.check_output(["git", "rev-parse", f"refs/tags/{tag}^{{commit}}"], text=True).strip()
    if tagged != commit:
        raise ValueError("Release tag does not point to this exact commit")
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
    output = Path("release")
    output.mkdir(exist_ok=True)
    shutil.copyfile(source, output / "judgement-scoreboard.apk")
    (output / "certificate.txt").write_text(certificate)
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    (output / "SHA256SUMS").write_text(f"{digest}  judgement-scoreboard.apk\n")
    (output / "verification.json").write_text(json.dumps({
        "commit": commit, "version": tag, "apkSha256": digest,
        "signingCertificateSha256": expected, "payloadMatchesCommitBuild": True,
        "productionSigned": True, "installOverOriginal": False,
        "attribution": "created by chatgpt-codex",
    }, indent=2) + "\n")
    print("Signed release verified: certificate, version, identity, and exact build payload")


if __name__ == "__main__":
    verify()
