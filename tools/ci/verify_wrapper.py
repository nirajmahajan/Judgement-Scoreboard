#!/usr/bin/env python3
"""Verify the wrapper before permitting its first execution."""
import hashlib
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
EXPECTED_GRADLE = "20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
# SHA256 of the wrapper produced by the verified Gradle 8.13 distribution.
EXPECTED_JAR = "81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f"
props = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text()
checks = {
    "distribution URL": "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-bin.zip" in props,
    "distribution checksum": f"distributionSha256Sum={EXPECTED_GRADLE}" in props,
    "wrapper checksum": hashlib.sha256((ROOT / "gradle/wrapper/gradle-wrapper.jar").read_bytes()).hexdigest() == EXPECTED_JAR,
    "distribution URL validation": "validateDistributionUrl=true" in props,
}
for label, ok in checks.items():
    print(f"{label}: {'PASS' if ok else 'FAIL'}")
if not all(checks.values()):
    sys.exit(1)
