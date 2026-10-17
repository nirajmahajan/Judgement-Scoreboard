# Android verification and release tooling

created by chatgpt-codex

Use JDK 21 and the Android SDK identified by `ANDROID_HOME`. The committed wrapper pins Gradle 8.13 and its distribution checksum. `python3 tools/ci/verify_wrapper.py` runs before executing it.

```sh
./gradlew --no-daemon --dependency-verification=strict assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest createDebugUnitTestCoverageReport lintDebug lintRelease assembleDebugAndroidTest
python3 tools/ci/verify_apk_identity.py
```

`gradle/verification-metadata.xml` contains reviewed SHA-256 values. Dependency changes require a deliberate `--write-verification-metadata sha256 --write-locks` run followed by review of changed coordinates/checksums. CI never updates verification metadata or screenshot goldens.

## Screenshot environment

`tools/ci/screenshots.sh record` records reviewable baselines. `tools/ci/screenshots.sh verify` compares them. Both build and run **linux/amd64**, including on Apple Silicon, from `Dockerfile.screenshots`: a digest-pinned Temurin 21.0.8 image, Ubuntu's 2026-10-01 package snapshot, pinned Android command-line tools, and fixed SDK/build tools. Record and verification use the same image. Goldens belong in `app/src/test/screenshots`; actuals and differences belong in `app/build/outputs/roborazzi`.

## Device coverage

The PR workflow launches API 26 and API 36 emulators explicitly with KVM. API 26 does not use Gradle Managed Devices. Nightly covers API 28/32/35/37 and an API 36 tablet with landscape, 200% font scale, external process-death checks, and a seeded Monkey sequence. API 37 uses the SDK catalog's stable `android-37.2/google_apis_ps16k/x86_64` image and asserts runtime SDK 37. Instrumentation failures are not retried.

`createDebugUnitTestCoverageReport` and `createDebugAndroidTestCoverageReport` produce real JaCoCo 0.8.13 reports from the executed debug tests. CI retains HTML/XML reports plus unit `.exec` and device `.ec` data; device report tasks run the instrumentation suite rather than replaying it just for coverage.

`process_death.py` runs outside the target process. For setup, bidding, results, and completion, it seeds state through a test fixture, opens it, records text, moves the app into the background, kills or force-stops it, proves the old PID exited, and checks a different PID restores the same visible state. A fixture-provided expected marker prevents an empty or unrelated screen from passing. Every start matches Android's MAIN/LAUNCHER intent. Background death waits for Android to acknowledge STOPPED state and a saved activity bundle, preserves the task ID, and uses a same-UID SIGKILL without force-stopping the app. An additional open-form case verifies unsaved name text and the Save-for-future checkbox survive task-preserving process death.

All emulator scripts retain Logcat, hierarchy, screenshots, runtime properties, and SDK revisions through exit traps. GitHub retains ordinary evidence for 14 days, nightly evidence for 30, and release-candidate evidence for 90. Configure branch protection to require **Required Android checks**; the aggregate fails for failed, cancelled, skipped, or missing required jobs.

## Release gates

For a quick release draft, open **Actions → Create release → Run workflow**, select `main` (or `master`), and enter the app version, for example `2.0.0`. Pushing a matching `v2.0.0` tag starts the same action. It builds both APK variants, checks their package/version identities, and creates a draft with checksums and release notes. The debug APK is a separate test app; the production APK is unsigned. Draft preparation does not require account changes or a signing key. It never presents a test-signed APK as an update to the original app.

The original APK was signed by Niraj Mahajan with certificate SHA-256 `93:E3:66:B9:3C:CD:1C:53:1E:9A:28:2A:09:6C:CA:A9:BB:D7:19:D9:B8:C1:27:1E:7E:53:84:F7:48:4B:DA:50`. A certificate or APK cannot recreate its private key. To ship an install-over update, locate the original `.jks`/`.keystore` and supply its signing secrets to the protected production workflow below.

Dispatch **Release candidate and approved publication** with the exact 40-character commit SHA. It reruns the full PR suite and an install-over test using a disposable CI key. The upgrade test re-signs both the bundled original APK and the candidate with that same temporary key, verifies an unchanged application UID, and checks exact recovered names/scores. It does not prove possession of the production key.

Unsigned release APKs and debug APKs are downloadable from ordinary build artifacts. A production-compatible release requires the original signing key. Configure a protected GitHub environment named `release`, restrict its approving reviewer to the repository owner, and add these environment secrets:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Publication requires owner dispatch, physical-device/TalkBack/production-upgrade acceptance for that exact commit, successful automated gates, and environment approval. Signing fails unless the certificate SHA-256 matches the bundled original APK. Release assets are uploaded to a draft before publication. No PR or `master` push publishes a release.

These scripts do not change global Git, `gh`, Codex, Claude, SSH-agent, or connector credentials. Workflow writes use the repository's ephemeral GitHub Actions token. Personal repository access must remain separate from Wisdom defaults.
