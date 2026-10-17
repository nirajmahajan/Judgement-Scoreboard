#!/usr/bin/env python3
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

from prepare_release import prepare


class ReleasePackagingTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        for variant, name in [("debug", "app-debug.apk"), ("release", "app-release-unsigned.apk")]:
            path = self.root / "app/build/outputs/apk" / variant / name
            path.parent.mkdir(parents=True)
            path.write_bytes(variant.encode())
        self.android = "{http://schemas.android.com/apk/res/android}"
        self.manifest = ET.Element("manifest", {
            "package": "android.example.judgement", self.android + "versionCode": "3",
            self.android + "versionName": "2.0.0",
            self.android + "sharedUserId": "com.example.nirajmahajan64725",
        })
        self.certificate = "Signer #1 certificate SHA-256 digest: 93e366b93ccd1c531e9a282a096ccaa9bbd719d9b8c1271e7e5384f7484bda50\n"

    def run_packaging(self, tag="v2.0.0"):
        return prepare(self.root, tag, "a" * 40, self.manifest, self.certificate)

    def test_draft_keeps_test_and_unsigned_apks_distinct_and_discloses_missing_signing(self):
        output = self.run_packaging()
        self.assertEqual(b"debug", (output / "Judgement-Scoreboard-v2.0.0-debug-test.apk").read_bytes())
        self.assertEqual(b"release", (output / "Judgement-Scoreboard-v2.0.0-release-unsigned.apk").read_bytes())
        evidence = json.loads((output / "release-evidence.json").read_text())
        self.assertFalse(evidence["productionSigned"])
        self.assertFalse(evidence["installOverOriginal"])
        self.assertEqual(2, len(evidence["sha256"]))
        notes = (output / "RELEASE_NOTES.md").read_text()
        self.assertIn("does **not** update", notes)
        self.assertIn("created by chatgpt-codex", notes)

    def test_mismatched_version_is_rejected_before_packaging(self):
        with self.assertRaisesRegex(ValueError, "version name"):
            self.run_packaging("v3.0.0")
        self.assertFalse((self.root / "release-draft").exists())

    def test_debug_identity_cannot_be_packaged_as_production(self):
        self.manifest.set("package", "android.example.judgement.debug")
        with self.assertRaisesRegex(ValueError, "upgrade identity"):
            self.run_packaging()

    def test_original_or_lower_version_code_is_rejected(self):
        for code in ["1", "2"]:
            self.manifest.set(self.android + "versionCode", code)
            with self.assertRaisesRegex(ValueError, "upgrade identity"):
                self.run_packaging()

    def test_unexpected_original_certificate_is_rejected(self):
        self.certificate = "Signer #1 certificate SHA-256 digest: another-key"
        with self.assertRaisesRegex(ValueError, "certificate"):
            self.run_packaging()


if __name__ == "__main__":
    unittest.main()
