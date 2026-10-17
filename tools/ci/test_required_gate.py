#!/usr/bin/env python3
"""Regression checks for the check that protects every pull request."""
import json
from pathlib import Path
import subprocess
import sys
import unittest

SCRIPT = Path(__file__).with_name("required_gate.py")
JOBS = ["wrapper", "build", "screenshots", "device"]


class RequiredGateTest(unittest.TestCase):
    def gate(self, jobs):
        return subprocess.run([sys.executable, str(SCRIPT), json.dumps(jobs), *JOBS],
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT).returncode

    def test_all_success_passes(self):
        self.assertEqual(0, self.gate({name: {"result": "success"} for name in JOBS}))

    def test_failure_cancellation_skip_and_unknown_fail(self):
        for state in ["failure", "cancelled", "skipped", "unknown"]:
            with self.subTest(state=state):
                jobs = {name: {"result": "success"} for name in JOBS}
                jobs["screenshots"] = {"result": state}
                self.assertNotEqual(0, self.gate(jobs))

    def test_missing_required_job_fails(self):
        self.assertNotEqual(0, self.gate({"wrapper": {"result": "success"}}))


if __name__ == "__main__":
    unittest.main()
