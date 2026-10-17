#!/usr/bin/env python3
"""Exercise draft promotion without network access or real credentials."""
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
    def publish(self, existing, new_key=False):
        calls = []
        release = {"id": 1, "tag_name": "v2.0.0", "draft": True, "target_commitish": COMMIT,
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
                        "productionSigned": True, "payloadMatchesCommitBuild": True}))
                with patch.dict(os.environ, {"GITHUB_REPOSITORY": "test/repo", "TESTED_COMMIT": COMMIT,
                        "RELEASE_TAG": "v2.0.0", "GITHUB_ACTOR": "nirajmahajan",
                        "GITHUB_TOKEN": "fixture-token", "RELEASE_SIGNING_MODE": "new" if new_key else "legacy",
                        "EVIDENCE_RUN": "https://github.com/test/actions/runs/1"}), \
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


if __name__ == "__main__":
    unittest.main()
