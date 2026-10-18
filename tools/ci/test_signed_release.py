#!/usr/bin/env python3
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from verify_signed_release import compare_payloads, verify


class SignedReleasePayloadTest(unittest.TestCase):
    def compare(self, changes):
        with tempfile.TemporaryDirectory() as directory:
            unsigned, signed = [Path(directory) / name for name in ["unsigned.apk", "signed.apk"]]
            original = {"classes.dex": b"code", "resources.arsc": b"resources", "META-INF/androidx.version": b"1.0"}
            for path, contents in [(unsigned, original), (signed, original | changes)]:
                with zipfile.ZipFile(path, "w") as archive:
                    for name, data in contents.items(): archive.writestr(name, data)
            compare_payloads(signed, unsigned)

    def test_signing_metadata_does_not_change_app_payload(self):
        self.compare({"META-INF/RELEASE.SF": b"signature", "META-INF/RELEASE.RSA": b"certificate", "META-INF/MANIFEST.MF": b"manifest"})

    def test_embedded_build_commit_metadata_does_not_change_app_payload(self):
        self.compare({"META-INF/version-control-info.textproto": b"build commit before the signed APK was committed"})

    def test_changed_code_or_resources_or_library_metadata_are_rejected(self):
        for name in ["classes.dex", "resources.arsc", "META-INF/androidx.version"]:
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "payload differs"):
                self.compare({name: b"modified"})


class SignedReleaseVerificationTest(unittest.TestCase):
    COMMIT, OLD_TAG, CERTIFICATE = "a" * 40, "b" * 40, "c" * 64

    def verify_fixture(self, refresh=False, environment=None, tagged=None, head=None,
                       certificate=None, package="android.example.judgement", version="2.0.6",
                       version_code="10", debuggable="false", label="Judgement Scoreboard"):
        tagged = tagged or (self.OLD_TAG if refresh else self.COMMIT)
        manifest = f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="{package}" android:versionName="{version}" android:versionCode="{version_code}">
            <application android:debuggable="{debuggable}" /></manifest>'''
        def output(args, **kwargs):
            if args[:2] == ["git", "rev-parse"]:
                return (head or self.COMMIT) if args[-1] == "HEAD" else tagged
            if args[0].endswith("apksigner"):
                return "Signer #1 certificate SHA-256 digest: " + (certificate or self.CERTIFICATE)
            if args[0] == "apkanalyzer":
                return manifest
            if args[0].endswith("aapt"):
                return f"application-label:'{label}'"
            raise AssertionError(args)
        with tempfile.TemporaryDirectory() as directory:
            previous = Path.cwd()
            try:
                os.chdir(directory)
                for path in [Path("release-assets/v2.0.6/Judgement-Scoreboard-v2.0.6.apk"),
                             Path("app/build/outputs/apk/release/app-release-unsigned.apk")]:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    with zipfile.ZipFile(path, "w") as archive:
                        archive.writestr("classes.dex", b"same executable payload")
                Path("tools/ci").mkdir(parents=True)
                Path("tools/ci/release-certificate.sha256").write_text(self.CERTIFICATE)
                with patch.dict(os.environ, {"RELEASE_TAG": "v2.0.6", "GITHUB_SHA": self.COMMIT,
                        "ANDROID_HOME": "sdk", "GITHUB_ACTOR": "nirajmahajan",
                        "GITHUB_REPOSITORY": "nirajmahajan/Judgement-Scoreboard",
                        "GITHUB_REF": "refs/heads/main" if refresh else "refs/tags/v2.0.6",
                        "GITHUB_EVENT_NAME": "push", "UPDATE_EXISTING_RELEASE": str(refresh).lower()}
                        | (environment or {}), clear=True), patch("subprocess.check_output", side_effect=output):
                    verify()
                return json.loads(Path("release/verification.json").read_text())
            finally:
                os.chdir(previous)

    def test_normal_release_requires_tag_to_match_exact_build_commit(self):
        with self.assertRaisesRegex(ValueError, "tag does not point"):
            self.verify_fixture(tagged=self.OLD_TAG)
        evidence = self.verify_fixture()
        self.assertEqual(self.COMMIT, evidence["originalTagCommit"])
        self.assertFalse(evidence["refreshExistingRelease"])

    def test_owner_refresh_records_actual_source_and_unchanged_original_tag(self):
        for event in ["push", "workflow_dispatch"]:
            with self.subTest(event=event):
                evidence = self.verify_fixture(refresh=True, environment={"GITHUB_EVENT_NAME": event})
                self.assertEqual(self.COMMIT, evidence["commit"])
                self.assertEqual(self.OLD_TAG, evidence["originalTagCommit"])
                self.assertTrue(evidence["refreshExistingRelease"])
                self.assertTrue(evidence["payloadMatchesCommitBuild"])

    def test_refresh_requires_owner_main_and_supported_event(self):
        for changes in [{"GITHUB_ACTOR": "another-user"}, {"GITHUB_REF": "refs/tags/v2.0.6"},
                        {"GITHUB_REF": "refs/heads/other"}, {"GITHUB_EVENT_NAME": "pull_request"},
                        {"GITHUB_REPOSITORY": "another/repo"}]:
            with self.subTest(changes=changes), self.assertRaisesRegex(ValueError, "owner on main"):
                self.verify_fixture(refresh=True, environment=changes)

    def test_refresh_rejects_checkout_of_a_different_commit(self):
        with self.assertRaisesRegex(ValueError, "exact workflow commit"):
            self.verify_fixture(refresh=True, head="d" * 40)

    def test_refresh_preserves_signature_name_version_and_production_identity_checks(self):
        for changes, message in [({"certificate": "d" * 64}, "new release key"),
                                 ({"label": "Another app"}, "Launcher app name"),
                                 ({"version": "2.0.7"}, "matching version"),
                                 ({"version_code": "2"}, "matching version"),
                                 ({"package": "android.example.judgement.debug"}, "production package"),
                                 ({"debuggable": "true"}, "non-debug")]:
            with self.subTest(changes=changes), self.assertRaisesRegex(ValueError, message):
                self.verify_fixture(refresh=True, **changes)


if __name__ == "__main__":
    unittest.main()
