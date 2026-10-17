#!/usr/bin/env python3
from pathlib import Path
import tempfile
import unittest
import zipfile
from verify_signed_release import compare_payloads


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

    def test_changed_code_or_resources_or_library_metadata_are_rejected(self):
        for name in ["classes.dex", "resources.arsc", "META-INF/androidx.version"]:
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "payload differs"):
                self.compare({name: b"modified"})


if __name__ == "__main__":
    unittest.main()
