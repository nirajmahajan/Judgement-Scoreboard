# Android build and pull-request verification

created by chatgpt-codex

This foundation keeps the existing app screens while moving the project to JDK21, Gradle8.13, AGP8.13.2, AndroidX, Material Views, Room2.8.5, and Android8+ (API26), targeting API36. The release identity and shared-user contract are retained. Debug uses a separate `.debug` application ID without a shared UID.

Before running the wrapper:

```sh
python3 tools/ci/verify_wrapper.py
./gradlew --no-daemon --dependency-verification=strict assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug lintRelease assembleDebugAndroidTest
python3 tools/ci/verify_apk_identity.py
```

The permanent smoke tests resolve the installed launcher dynamically, assert a visible enabled action with an accessible label, and exercise Activity recreation. The same launcher contract will continue to run as later changes replace the screens. The screenshot test also records that actual launch surface.

Record and verify screenshot baselines in the same pinned Linux environment:

```sh
tools/ci/screenshots.sh record
tools/ci/screenshots.sh verify
```

Both commands use linux/amd64, a digest-pinned Temurin21.0.8 image, the Ubuntu2026-10-01 package snapshot, and pinned Android command-line tools. CI only verifies reviewed goldens. Actuals/diffs and reports remain under `app/build`; reference PNGs are committed under `app/src/test/screenshots`.

Every PR and `master` push validates the wrapper before executing it, strictly verifies dependency hashes, builds debug/release APKs, runs unit tests and both Lint variants, verifies screenshots, and explicitly launches API26/API36 emulators for instrumentation. The required aggregate fails for failed, cancelled, skipped, or absent jobs. Configure branch protection to require **Required Android checks**. All Actions are pinned to commit revisions; jobs have bounded timeouts and retain reports/APKs/device logs for14days. No test failure is retried.

`gradle/verification-metadata.xml` records SHA256 values and `app/gradle.lockfile` pins transitive classpath dependencies. Refreshing dependencies is a deliberate reviewed operation with `--write-verification-metadata sha256 --write-locks`; normal CI never changes either file.

The compatibility updates also migrate Back interception to AndroidX callbacks and icon tint attributes to supported AndroidX attributes. Remaining legacy Lint warnings are itemized in `legacy-lint-disposition.md`, with no baseline or disabled detector. Later correctness, feature, and UX changes replace those owning screens. Nightly stress/lifecycle coverage and manually approved signing/release workflows are added with the complete regression suite in the final coverage change.

Global Git, GitHub CLI, AI-tool, SSH-agent, and connector defaults are unchanged. Project commits use the repository's personal identity; personal publication remains a separate explicit repository credential.
