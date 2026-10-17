# Lint disposition

created by chatgpt-codex

The debug and release Lint tasks pass with zero errors. No baseline or disabled detector conceals findings. Counts below identify every warning in the saved reports from the final strict build, including the completed UI resource repairs. CI retains the complete XML, HTML, and SARIF reports.

| Detector | Debug | Release | Disposition |
|---|---:|---:|---|
| `AndroidGradlePluginVersion` | 1 | 1 | Accepted advisory. Gradle 8.13, AGP 8.13.2, and JDK 21 are the explicitly approved compatible build foundation. A newer available version does not establish a defect in this pinned combination. |
| `GradleDependency` | 14 | 14 | Accepted version advisories. Direct and transitive dependencies use fixed coordinates, strict dependency locks, and SHA256 verification. Dependency updates require a reviewed toolchain/library change and the full regression gates. |
| `MonochromeLauncherIcon` | 0 | 1 | The unqualified adaptive icon serves API26–32; API33+ selects the version-qualified icon containing its monochrome layer. Lint reports the base resource. Both resources are preserved, and no detector is suppressed. |
| `UnusedAttribute` | 1 | 1 | Intentional release upgrade contract. sharedUserMaxSdkVersion=32 retains the historical shared UID for existing installations while newer fresh installations follow Android's shared-user cutoff. Removing it would change the approved identity contract. Debug removes both shared-user attributes. |

The unused `bidding_description` string was removed, and the API33+ adaptive icon now includes a monochrome layer. The final reports contain no `UnusedResources`. Debug has 16 warnings. Release has the same16 plus `MonochromeLauncherIcon` on the unqualified adaptive icon: API33+ uses the separate `mipmap-anydpi-v33/ic_launcher.xml` resource with its monochrome layer. The release warning is retained and documented without suppression.

The AndroidX foundation repaired blocking legacy Back callback and icon-tint errors directly. The UI rebuild removed the owning legacy resources/classes behind the following former warning categories: `ScrollViewSize`, `InflateParams`, `LockedOrientationActivity`, `DiscouragedApi`, `UseCompatLoadingForDrawables`, `ObsoleteSdkInt`, `UseCompoundDrawables`, `ViewHolder`, `Overdraw`, `UselessParent`, `RedundantNamespace`, `UnusedNamespace`, `IconXmlAndPng`, `IconDipSize`, `IconDuplicates`, `ButtonStyle`, `AlwaysShowAction`, `Autofill`, `ContentDescription`, `SetTextI18n`, `HardcodedText`, and `RtlHardcoded`. The foundation PR retains their original detector-by-detector inventory under `tools/ci/legacy-lint-disposition.md`; no legacy Lint baseline is carried forward.
