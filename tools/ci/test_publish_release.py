#!/usr/bin/env python3
"""Exercise draft promotion without network access or real credentials."""
import hashlib
import io
import json
import os
from pathlib import Path
import runpy
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name("publish_release.py")
COMMIT = "a" * 40


class ReleasePublicationTest(unittest.TestCase):
    def publish(self, existing, new_key=False, refresh=False, environment=None, verification_changes=None):
        calls = []
        self.calls = calls
        release = {"id": 1, "tag_name": "v2.0.0", "draft": not refresh, "target_commitish": COMMIT,
                   "assets": [], "upload_url": "https://uploads.github.com/test{?name}",
                   "html_url": "https://github.com/test/releases/tag/v2.0.0"}
        def request(req):
            calls.append((req.get_method(), req.full_url, req.data))
            if req.get_method() == "GET":
                value = existing
            elif req.get_method() == "POST" and "uploads.github.com" in req.full_url:
                value = {}
            else:
                value = release
            return io.BytesIO(json.dumps(value).encode())
        with tempfile.TemporaryDirectory() as directory:
            previous = Path.cwd()
            try:
                os.chdir(directory)
                Path("release").mkdir()
                for name in ["judgement-scoreboard.apk", "SHA256SUMS", "certificate.txt"]:
                    Path("release", name).write_text("fixture")
                Path("release/verification.json").write_text(json.dumps({"commit": COMMIT,
                        "productionSigned": True, "payloadMatchesCommitBuild": True,
                        "version": "v2.0.0", "originalTagCommit": "b" * 40,
                        "refreshExistingRelease": refresh,
                        "apkSha256": hashlib.sha256(b"fixture").hexdigest()} | (verification_changes or {})))
                with patch.dict(os.environ, {"GITHUB_REPOSITORY": "nirajmahajan/Judgement-Scoreboard", "TESTED_COMMIT": COMMIT,
                        "RELEASE_TAG": "v2.0.0", "GITHUB_ACTOR": "nirajmahajan",
                        "GITHUB_SHA": COMMIT, "GITHUB_REF": "refs/heads/main", "GITHUB_EVENT_NAME": "push",
                        "UPDATE_EXISTING_RELEASE": "true" if refresh else "false",
                        "GITHUB_TOKEN": "fixture-token", "RELEASE_SIGNING_MODE": "new" if new_key else "legacy",
                        "EVIDENCE_RUN": "https://github.com/test/actions/runs/1"} | (environment or {}), clear=True), \
                        patch("urllib.request.urlopen", side_effect=request):
                    runpy.run_path(str(SCRIPT), run_name="__main__")
            finally:
                os.chdir(previous)
        return calls

    def test_new_release_uploads_every_asset_before_publication(self):
        calls = self.publish([])
        self.assertEqual(5, sum("uploads.github.com" in url for _, url, _ in calls))
        self.assertEqual("PATCH", calls[-1][0])
        self.assertEqual({"draft": False}, json.loads(calls[-1][2]))

    def test_matching_draft_is_promoted_without_creating_another_release(self):
        calls = self.publish([{"tag_name": "v2.0.0", "draft": True, "target_commitish": COMMIT,
                               "id": 1, "assets": []}])
        self.assertFalse(any(method == "POST" and url.endswith("/releases") for method, url, _ in calls))
        self.assertEqual({"draft": False}, json.loads(calls[-1][2]))

    def test_published_release_is_never_overwritten(self):
        with self.assertRaisesRegex(SystemExit, "refusing to overwrite"):
            self.publish([{"tag_name": "v2.0.0", "draft": False, "target_commitish": COMMIT}])

    def test_draft_for_another_commit_is_never_overwritten(self):
        with self.assertRaisesRegex(SystemExit, "refusing to overwrite"):
            self.publish([{"tag_name": "v2.0.0", "draft": True, "target_commitish": "b" * 40}])

    def test_new_key_publication_retargets_the_unpublished_draft_and_discloses_reinstall(self):
        calls = self.publish([{"id": 1, "tag_name": "v2.0.0", "draft": True, "target_commitish": "b" * 40}], new_key=True)
        update = next(json.loads(data) for method, url, data in calls
                      if method == "PATCH" and "tag_name" in json.loads(data))
        self.assertEqual(COMMIT, update["target_commitish"])
        self.assertIn("uninstall the original", update["body"])
        self.assertNotIn("were confirmed by the owner", update["body"])
        evidence = next(json.loads(data) for _, url, data in calls if url.endswith("name=test-evidence.json"))
        self.assertFalse(evidence["physicalAcceptance"])
        self.assertFalse(evidence["productionUpgradeAcceptance"])

    def test_new_key_publication_still_refuses_to_replace_a_published_release(self):
        with self.assertRaisesRegex(SystemExit, "refusing to overwrite"):
            self.publish([{"tag_name": "v2.0.0", "draft": False, "target_commitish": COMMIT}], new_key=True)

    def test_owner_refresh_replaces_assets_without_creating_hiding_or_retargeting_release(self):
        calls = self.publish([{"id": 1, "tag_name": "v2.0.0", "draft": False,
                               "target_commitish": "b" * 40, "assets": [
                                   {"name": "judgement-scoreboard.apk", "url": "https://api.github.com/old-apk"},
                                   {"name": "unrelated.txt", "url": "https://api.github.com/keep"}],
                               "upload_url": "https://uploads.github.com/existing{?name}"}], new_key=True, refresh=True)
        self.assertFalse(any(method == "POST" and url.endswith("/releases") for method, url, _ in calls))
        updates = [json.loads(data) for method, _, data in calls if method == "PATCH"]
        self.assertEqual(1, len(updates))
        self.assertFalse(updates[0]["draft"])
        self.assertNotIn("target_commitish", updates[0])
        self.assertNotIn("tag_name", updates[0])
        self.assertIn(f"Updated APK source commit: `{COMMIT}`", updates[0]["body"])
        self.assertIn("Original release tag commit (unchanged): `" + "b" * 40, updates[0]["body"])
        self.assertEqual(["https://api.github.com/old-apk"], [url for method, url, _ in calls if method == "DELETE"])
        self.assertTrue(any(method == "POST" and url.endswith("existing?name=judgement-scoreboard.apk") for method, url, _ in calls))

    def test_refresh_never_creates_missing_release_or_publishes_a_draft(self):
        for existing in [[], [{"id": 1, "tag_name": "v2.0.0", "draft": True}]]:
            with self.subTest(existing=existing), self.assertRaisesRegex(SystemExit, "already published"):
                self.publish(existing, new_key=True, refresh=True)
            self.assertTrue(all(method == "GET" for method, _, _ in self.calls))

    def test_refresh_rejects_unauthorized_context_before_network_access(self):
        for environment in [{"GITHUB_REF": "refs/heads/other"}, {"GITHUB_EVENT_NAME": "pull_request"},
                            {"GITHUB_REPOSITORY": "another/repo"}]:
            with self.subTest(environment=environment), self.assertRaisesRegex(ValueError, "owner on main"):
                self.publish([], new_key=True, refresh=True, environment=environment)
            self.assertEqual([], self.calls)

    def test_refresh_requires_new_key_and_exact_verified_workflow_commit(self):
        with self.assertRaisesRegex(SystemExit, "new-key APK"):
            self.publish([], refresh=True)
        with self.assertRaisesRegex(SystemExit, "exact workflow commit"):
            self.publish([], new_key=True, refresh=True, environment={"GITHUB_SHA": "b" * 40})
        self.assertEqual([], self.calls)

    def test_refresh_rejects_incomplete_verification_or_replaced_apk(self):
        for changes in [{"version": "v9.0.0"}, {"payloadMatchesCommitBuild": False},
                        {"refreshExistingRelease": False}, {"originalTagCommit": "invalid"},
                        {"apkSha256": "0" * 64}]:
            with self.subTest(changes=changes), self.assertRaisesRegex(SystemExit, "complete verification"):
                self.publish([], new_key=True, refresh=True, verification_changes=changes)
            self.assertEqual([], self.calls)


if __name__ == "__main__":
    unittest.main()
