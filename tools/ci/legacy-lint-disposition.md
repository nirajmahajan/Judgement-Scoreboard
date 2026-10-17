# Legacy Lint disposition

The foundation build reports zero errors in debug and release. These are all remaining warnings from the original screens and assets. No baseline, blanket suppression, or disabled detector is used. Counts below are from the generated variant XML reports; those full reports are retained as CI evidence. The following UI and UX changes own the replacements described here.

| Detector | Debug | Release | Disposition |
|---|---:|---:|---|
| `AlwaysShowAction` | 1 | 1 | Legacy toolbar forces an action into the bar. The new navigation replaces this menu. |
| `AndroidGradlePluginVersion` | 1 | 1 | Retained the approved AGP 8.13.2 / Gradle 8.13 / JDK 21 combination. Automatic upgrade to another toolchain is outside this change. |
| `Autofill` | 2 | 2 | Legacy player/score forms omit autofill policy. New labelled validation forms own explicit input behavior. |
| `ButtonStyle` | 3 | 3 | Legacy Button styling. Material button styling is owned by the UI and theme changes. |
| `ContentDescription` | 21 | 21 | Legacy icon controls omit descriptions. New UI supplies labelled controls and 48dp targets. |
| `DiscouragedApi` | 18 | 18 | Legacy Java uses resource-name lookup. New ID-based screens replace these call sites in the UI change. |
| `GradleDependency` | 15 | 15 | Dependencies are pinned to the approved compatible versions and verified by checksums. Suggested version changes require a separate reviewed dependency update. |
| `HardcodedText` | 123 | 123 | Legacy XML/Java embeds English text. New UI resources replace these strings. |
| `IconDipSize` | 1 | 1 | Legacy icon-density dimensions disagree. The UX change replaces these launcher assets. |
| `IconDuplicates` | 1 | 1 | Legacy density variants duplicate pixels. The UX change replaces these launcher assets. |
| `IconXmlAndPng` | 7 | 7 | Legacy launcher assets contain both raster and vector variants. The UX change supplies the final coordinated launcher assets. |
| `InflateParams` | 13 | 13 | Legacy adapter rows inflate without their parent. RecyclerView adapters in the UI change own correct parent-aware inflation. |
| `LockedOrientationActivity` | 18 | 18 | Legacy activities lock orientation. The new single activity and responsive layouts remove these declarations in the UI change. |
| `MonochromeLauncherIcon` | 0 | 2 | Legacy launcher has no monochrome icon. The UX change supplies the themed launcher icon. |
| `ObsoleteSdkInt` | 13 | 13 | Pre-26 legacy compatibility branches. Owning legacy screen replacement removes them; no detector is disabled. |
| `Overdraw` | 16 | 16 | Legacy overlapping background surfaces. The replacement themed layouts own these surface changes. |
| `RedundantNamespace` | 1 | 1 | Legacy XML namespace. The replacement resource removes this declaration. |
| `RtlHardcoded` | 67 | 67 | Legacy XML uses left/right positioning. New UI uses start/end positions and RTL layout regression coverage. |
| `ScrollViewSize` | 1 | 1 | Legacy scroll-container geometry. The replacement scrolling screen owns this fix in the UI change. |
| `SetTextI18n` | 18 | 18 | Legacy Java concatenates display text. New screen resources own these labels. |
| `UnusedAttribute` | 1 | 1 | Legacy API-qualified XML. The replacement screen removes this obsolete attribute. |
| `UnusedNamespace` | 1 | 1 | Legacy XML namespace. The replacement resource removes this declaration. |
| `UnusedResources` | 23 | 23 | Legacy assets remain while their owning screens remain. The UI change removes obsolete resources together with those screens. |
| `UseCompatLoadingForDrawables` | 1 | 1 | Legacy drawable loading. The replacement Material UI removes this call site. |
| `UseCompoundDrawables` | 1 | 1 | Legacy row composition. The replacement accessible row layout owns this change. |
| `UselessParent` | 1 | 1 | Legacy layout nesting. The replacement screen removes this container. |
| `ViewHolder` | 10 | 10 | Legacy ListView adapters do not recycle rows. Their replacement RecyclerView adapters own this change. |

Total: 378 debug warnings; 380 release warnings.

The foundation repaired the blocking findings directly: deprecated Back overrides became AndroidX Back callbacks, and layout icon tint attributes use the supported AndroidX attribute. The changes preserve existing Back behavior until the owning screens are replaced.
