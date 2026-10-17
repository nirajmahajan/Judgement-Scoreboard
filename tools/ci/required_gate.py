#!/usr/bin/env python3
"""Do not allow cancelled, failed, or skipped jobs to satisfy the required check."""
import json
import sys

jobs = json.loads(sys.argv[1])
expected = set(sys.argv[2:])
if not expected:
    raise SystemExit("Required job names must be supplied explicitly")
if set(jobs) != expected:
    raise SystemExit("Unexpected or missing required jobs: " + json.dumps({
        "expected": sorted(expected), "received": sorted(jobs)}, sort_keys=True))
failed = {name: result.get("result", "missing") for name, result in jobs.items()
          if result.get("result") != "success"}
if failed:
    raise SystemExit("Required jobs did not succeed: " + json.dumps(failed, sort_keys=True))
print("All required jobs succeeded: " + ", ".join(sorted(jobs)))
