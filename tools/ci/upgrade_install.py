#!/usr/bin/env python3
"""Test a real install-over upgrade using one disposable CI certificate.

Only an emulator is accepted. This deliberately installs the release application ID.
It proves install/data/UID compatibility, not possession of the production certificate.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

PACKAGE = "android.example.judgement"
DB_PATH = f"/data/user/0/{PACKAGE}/databases/player-database"
parser = argparse.ArgumentParser()
parser.add_argument("candidate", type=Path)
args = parser.parse_args()
out = Path("ci-artifacts/upgrade")
out.mkdir(parents=True, exist_ok=True)


def run(*cmd, timeout=120, **kwargs):
    return subprocess.run(cmd, check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, timeout=timeout, **kwargs).stdout


def adb(*cmd, timeout=120):
    return run("adb", *cmd, timeout=timeout).decode()


def installed_uid():
    # PackageManager's exact package listing is stable across the dumpsys
    # userId/appId terminology change and excludes the similarly named debug app.
    listing = adb("shell", "pm", "list", "packages", "-U", "--user", "0", PACKAGE)
    matches = re.findall(r"^package:" + re.escape(PACKAGE) + r" uid:(\d+)\s*$", listing, re.MULTILINE)
    if len(matches) != 1:
        raise AssertionError(f"Expected one installed release UID, found: {listing}")
    return matches[0]


if adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
    raise SystemExit("Upgrade test only runs on an Android emulator")
sdk = Path(os.environ["ANDROID_HOME"])
apksigner = sdk / "build-tools/36.0.0/apksigner"
zipalign = sdk / "build-tools/36.0.0/zipalign"
original = Path("app/release/app-release.apk")
expected_old_hash = "20b80a1dd705a1c22b14da5290e3ee8fa9d7fe2727a1135e9a44c508580f28ef"
assert hashlib.sha256(original.read_bytes()).hexdigest() == expected_old_hash
with tempfile.TemporaryDirectory(prefix="judgement-upgrade-") as temp:
    temp = Path(temp)
    key = temp / "ci-upgrade.p12"
    run("keytool", "-genkeypair", "-keystore", str(key), "-storetype", "PKCS12",
        "-storepass", "ci-upgrade-only", "-keypass", "ci-upgrade-only", "-alias", "upgrade",
        "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=CI upgrade fixture")
    signed = []
    for label, source in [("original", original), ("candidate", args.candidate)]:
        aligned = temp / f"{label}-aligned.apk"
        target = out / f"{label}-ci-signed.apk"
        run(str(zipalign), "-f", "-p", "4", str(source), str(aligned))
        run(str(apksigner), "sign", "--ks", str(key), "--ks-key-alias", "upgrade",
            "--ks-pass", "pass:ci-upgrade-only", "--key-pass", "pass:ci-upgrade-only",
            "--out", str(target), str(aligned))
        signed.append(target)
    adb("root")
    adb("wait-for-device")
    adb("install", str(signed[0]))
    uid_before = installed_uid()
    # Install over a seven-column legacy fixture without uninstalling or clearing data.
    # Separate Room migration tests cover the four-column and six-column shapes.
    db = temp / "player-database"
    con = sqlite3.connect(db)
    con.execute('CREATE TABLE players (id INTEGER NOT NULL PRIMARY KEY, Name TEXT, Score INTEGER NOT NULL, dealer INTEGER NOT NULL, Prediction INTEGER NOT NULL, Result INTEGER NOT NULL, Cement INTEGER NOT NULL)')
    fixtures = [(7, "  Legacy %_ name  ", -2147483648, 1, -1, 0, 0),
                (91, "Unicode खिलाड़ी 👑", 2147483647, 0, 3, 1, 1)]
    con.executemany('INSERT INTO players VALUES (?,?,?,?,?,?,?)', fixtures)
    con.execute('PRAGMA user_version=1')
    con.commit()
    con.close()
    adb("shell", "mkdir", "-p", f"/data/user/0/{PACKAGE}/databases")
    adb("push", str(db), DB_PATH)
    adb("shell", "chown", "-R", f"{uid_before}:{uid_before}", f"/data/user/0/{PACKAGE}/databases")
    database_directory = f"/data/user/0/{PACKAGE}/databases"
    adb("shell", "restorecon", "-RF", database_directory)
    # Reused emulator fixtures can retain a prior install's MLS category on
    # adb-pushed files. Match the package directory's actual category; never
    # disable SELinux or change the application's access policy for this test.
    directory_context = adb("shell", "ls", "-Zd", database_directory).split()[0]
    assert re.fullmatch(r"u:object_r:app_data_file:s0(?::[a-z0-9,]+)?", directory_context), directory_context
    adb("shell", "chcon", directory_context, DB_PATH)
    fixture_context = adb("shell", "ls", "-Z", DB_PATH)
    assert fixture_context.split()[0] == directory_context, fixture_context
    (out / "legacy-fixture-selinux.txt").write_text(fixture_context)
    adb("install", "-r", str(signed[1]))
    uid_after = installed_uid()
    assert uid_after == uid_before, f"Upgrade changed UID {uid_before} -> {uid_after}"
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/android.example.judgement.ui.MainActivity")
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        adb("shell", "uiautomator", "dump", "/sdcard/upgrade-ready.xml")
        hierarchy = adb("shell", "cat", "/sdcard/upgrade-ready.xml")
        (out / "upgrade-ready.xml").write_text(hierarchy)
        tree = ET.fromstring(hierarchy)
        if any(node.get("package") == PACKAGE and "Recovered legacy scorecard" in node.get("text", "")
               for node in tree.iter("node")):
            break
        time.sleep(0.25)
    else:
        (out / "upgrade-logcat.txt").write_text(adb("logcat", "-d", "-v", "threadtime"))
        raise AssertionError("Legacy recovery UI did not become ready within30seconds")
    adb("shell", "am", "force-stop", PACKAGE)
    adb("pull", DB_PATH, str(temp / "upgraded.db"))
    # Pull WAL too so the verification reads the complete transaction after close.
    for suffix in ["-wal", "-shm"]:
        try:
            adb("pull", DB_PATH + suffix, str(temp / ("upgraded.db" + suffix)))
        except subprocess.CalledProcessError:
            pass
    upgraded = sqlite3.connect(temp / "upgraded.db")
    version = upgraded.execute("PRAGMA user_version").fetchone()[0]
    assert version == 4, f"Expected database version4, found {version}"
    recovered = upgraded.execute("SELECT rawValues FROM legacy_recovery ORDER BY sourceRow").fetchall()
    assert len(recovered) == len(fixtures)
    for actual, expected in zip(recovered, fixtures):
        raw = {item["column"]: item["value"] for item in json.loads(actual[0])}
        assert raw["Name"] == expected[1], raw
        assert raw["Score"] == str(expected[2]), raw
        assert raw["id"] == str(expected[0]), raw
    upgraded.close()
    (out / "upgrade-evidence.json").write_text(json.dumps({
        "originalApkSha256": expected_old_hash,
        "candidateApkSha256": hashlib.sha256(args.candidate.read_bytes()).hexdigest(),
        "uidBefore": uid_before, "uidAfter": uid_after,
        "databaseVersion": version, "rawRecoveryRows": len(recovered),
        "installation": "adb install -r; no uninstall or data clear",
        "certificate": "Disposable CI key; production signing is a separate gate",
    }, indent=2))
print("Legacy install upgraded with unchanged UID and exact raw recovery values.")
