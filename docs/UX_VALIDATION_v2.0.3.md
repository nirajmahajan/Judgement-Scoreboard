# v2.0.3 UX verification

created by chatgpt-codex

Executed on the visible API 35 Android emulator, using actual control taps, scrolling and navigation. User app data was archived before fixtures were used.

- 56 distinct native UI cases passed across CriticalUiRegressionTest, FeatureUiRegressionTest, SetupFlowRegressionTest, FormLifecycleTest, StrategyUiRegressionTest, HistoryStatisticsUiRegressionTest, RosterGestureTest and InteractionStabilityTest. Seven stale assertions/flows were corrected and their affected cases rerun successfully.
- Native cases include 8/16-player scrolling, saved-player row deletion and selection stability, dealer/result selection stability, recreation, tabs, staged roster changes, history corrections, complete-game results/replay, drag release/cancellation/auto-scroll, keyboard-open validation, 320dp width and 200% fonts. Drag stress cases use the adapter harness; a separate direct pass exercised dragging on the actual Manage players screen.
- 17 GameRulesTest and 12 ScoreGraphViewTest cases passed.
- All 33 ScreenVisualTest cases and screenshot comparisons passed in the pinned Linux image. Six intentionally changed goldens were inspected and recorded in that same image; CI verifies them.
- Debug and release APKs built with strict dependency verification. Debug Lint passed with zero errors; warning disposition is recorded in LINT.md.
- The signed production APK was installed over v2.0.2 in place. Its application label is Judgement Scoreboard, version 2.0.3/code 6, with the existing v2 release certificate.

Direct production-app checks additionally exercised eight-player setup, saved names and search, blank/duplicate inputs, mandatory descending 6-to-1 order, dealer-name tapping, quick predictions 0/1/2/3, latest-prediction editing, forbidden dealer total, the real keyboard Done key, impossible live outcomes and corrected advancement to the next round, clean Manage Back, dragging and dealer reconfirmation.

This pass does not claim physical-device or TalkBack acceptance. Screenshot evidence and action/instrumentation logs are retained locally with the 2026-10-18 emulator UX run.
