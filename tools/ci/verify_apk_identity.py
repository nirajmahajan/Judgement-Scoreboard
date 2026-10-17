#!/usr/bin/env python3
"""Guard release upgrade identity and debug isolation using the built APK manifests."""
import subprocess
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"

def manifest(path):
    return ET.fromstring(subprocess.check_output(["apkanalyzer", "manifest", "print", path], text=True))

release = manifest("app/build/outputs/apk/release/app-release-unsigned.apk")
debug = manifest("app/build/outputs/apk/debug/app-debug.apk")
assert release.get("package") == "android.example.judgement"
assert release.get(ANDROID + "sharedUserId") == "com.example.nirajmahajan64725"
assert release.get(ANDROID + "sharedUserMaxSdkVersion") == "32"
assert int(release.get(ANDROID + "versionCode")) > 2
assert debug.get("package") == "android.example.judgement.debug"
assert debug.get(ANDROID + "sharedUserId") is None
assert debug.get(ANDROID + "sharedUserMaxSdkVersion") is None
for apk in [release, debug]:
    sdk = apk.find("uses-sdk")
    assert sdk.get(ANDROID + "minSdkVersion") == "26"
    assert sdk.get(ANDROID + "targetSdkVersion") == "36"
print("Release upgrade identity and isolated debug identity verified.")
