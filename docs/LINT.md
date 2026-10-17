# Lint disposition

created by chatgpt-codex

The debug and release Lint tasks pass with zero errors. No baseline or disabled detector conceals findings. Counts below identify every warning in the saved reports from the final strict build, including the completed UI resource repairs. CI retains the complete XML, HTML, and SARIF reports.

| Detector | Debug | Release | Disposition |
|---|---:|---:|---|
| `AndroidGradlePluginVersion` | 1 | 1 | Accepted advisory. Gradle 8.13, AGP 8.13.2, and JDK 21 are the explicitly approved compatible build foundation. A newer available version does not establish a defect in this pinned combination. |
| `GradleDependency` | 14 | 14 | Accepted version advisories. Direct and transitive dependencies use fixed coordinates, strict dependency locks, and SHA256 verification. Dependency updates require a reviewed toolchain/library change and the full regression gates. |
| `MonochromeLauncherIcon` | 0 | 1 | The unqualified adaptive icon serves API26–32; API33+ selects the version-qualified icon containing its monochrome layer. Lint reports the base resource. Both resources are preserved, and no detector is suppressed. |
| `UnusedAttribute` | 1 | 1 | Intentional release upgrade contract. sharedUserMaxSdkVersion=32 retains the historical shared UID for existing installations while newer fresh installations follow Android's shared-user cutoff. Removing it would change the approved identity contract. Debug removes both shared-user attributes. |
| `PluralsCandidate` | 4 | 4 | English scoring formulas and signed correction deltas. The real score totals already use plural resources. These template advisories do not alter arithmetic or control layout; broader localization is outside this personal-app delivery. |
| `DrawAllocation` | 2 | 2 | The bounded score graph formats its timeline labels while drawing. No storage access or model mutation occurs during drawing; allocations are accepted without speculative rendering optimization. |
| `UnusedResources` | 1 | 1 | An unused historical back-label string has no runtime effect. Retained as a documented resource advisory rather than expanding this delivery into cleanup. |

The unused legacy `bidding_description` string was removed, and the API33+ adaptive icon includes a monochrome layer. The completed feature build has 23 debug warnings and 24 release warnings, all itemized above. API33+ uses the separate `mipmap-anydpi-v33/ic_launcher.xml` resource with its monochrome layer. No warning detector is suppressed.

The AndroidX foundation repaired blocking legacy Back callback and icon-tint errors directly. The UI rebuild removed the owning legacy resources/classes behind the following former warning categories: `ScrollViewSize`, `InflateParams`, `LockedOrientationActivity`, `DiscouragedApi`, `UseCompatLoadingForDrawables`, `ObsoleteSdkInt`, `UseCompoundDrawables`, `ViewHolder`, `Overdraw`, `UselessParent`, `RedundantNamespace`, `UnusedNamespace`, `IconXmlAndPng`, `IconDipSize`, `IconDuplicates`, `ButtonStyle`, `AlwaysShowAction`, `Autofill`, `ContentDescription`, `SetTextI18n`, `HardcodedText`, and `RtlHardcoded`. The foundation PR retains their original detector-by-detector inventory under `tools/ci/legacy-lint-disposition.md`; no legacy Lint baseline is carried forward.

## v2.0.3 focused UX pass

Debug Lint passed with zero errors and 83 warnings, without a baseline. Current detector counts replace the older foundation counts above: AndroidGradlePluginVersion 1, GradleDependency 14, UnusedAttribute 2, PluralsCandidate 5, DrawAllocation 2, UnusedResources 51, SetTextI18n 3, RtlSymmetry 2, NotifyDataSetChanged 1, Overdraw 1, AlwaysShowAction 1.

Version advisories, upgrade attributes, English arithmetic templates and bounded graph allocations retain the dispositions above. Unused resources are retained historical labels/icons/layouts; removing them is outside this UX repair. SetTextI18n covers bounded numeric/score labels in this English app. RtlSymmetry concerns existing compact name-column end padding; RTL layout/name cases remain covered. The remaining full-list notification is a bounded roster snapshot replacement, not a selection update. The screen's background draw and always-visible replay action are deliberate. No detector is suppressed to hide these findings. New row-delete controls have static and player-specific runtime accessibility labels.
