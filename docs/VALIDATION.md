# Foundation validation

created by chatgpt-codex

Local acceptance completed on 2026-10-17. GitHub runs repeat the gates against each PR head; local results are not a substitute for those required checks.

| Check | Result |
|---|---|
| Debug/release builds, unit tests and Lint | Passed; 36 JVM methods per variant, zero Lint errors. Accepted warnings are itemized in LINT.md. |
| Domain mathematics | Independent exhaustive trick-allocation oracle covers 40,264 bid/outcome combinations, with forbidden-total vectors tested separately. Seeded full games also pass. |
| Pinned Linux screenshots | Record and strict verification passed 36 methods and 13 reviewed goldens, with no failures or skips. |
| API 26 instrumentation | All 68 methods passed, including four lifecycle fixture methods. Device line coverage is 86.7%. |
| API 36 instrumentation | All 68 methods passed, with no failures or skips. |
| External lifecycle driver | Nine scenarios passed: setup/bids/results/completed under background SIGKILL and force-stop, plus an unsaved name/checkbox under task-preserving death. PID changes and the retained task are checked. |
| Install-over upgrade | Original version 2 and candidate were signed with the same disposable CI key; install-r retained UID 10155, migrated to database v3 and preserved both fixture rows exactly, including Unicode and negative/max-int scores. No uninstall or data clear occurred between the two installations. |
| CI tooling | Wrapper integrity, aggregate gate regressions, APK identity, actionlint, Python compilation and shell syntax passed. |

The device suite covers 24 repository regressions, seven migrations, a seeded game sequence, five critical UI flows, 15 feature UI flows, four form lifecycle cases, six gesture/accessibility cases, four fixture methods and two launcher cases. The two deferred-draft regressions first reproduced incorrect mutations on the old source, then passed on both APIs after ID capture.

The device tests use the installable debug APK with SHA-256 `3432269b803dfa18d20ded1a71861c3555ef6d0d9c36583616d678137fad2cf6`. The unsigned release APK used for upgrade verification has SHA-256 `44748dad5b1f8b9d0117c2f847e4a1d8c8b92e89f477152806de0c12e8e0c128`.

The upgrade fixture runs with SELinux enforced. On a reused emulator, adb-pushed fixture files are assigned the existing package directory's actual MLS context before launch. The test neither weakens SELinux policy nor changes app permissions.

Production-certificate installation requires the original private signing key. Physical-device and TalkBack acceptance remain explicit owner gates before publishing a signed release. The CI workflow retains APKs, checksums, reports, coverage, screenshots/diffs, Logcat, hierarchies and failing seeds; nightly and release-candidate jobs are defined separately.
