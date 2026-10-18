# Defect inventory

The 2019 release was surveyed on an Android 15 emulator before changes. Evidence is retained locally under `~/.codex/artifacts/judgement-scoreboard-survey-2026-10-17`.

The named tests below exercise the repaired behavior. JVM tests are under `app/src/test/java`; Room and device UI tests are under `app/src/androidTest/java`. The external lifecycle driver is `tools/ci/process_death.py`. CI retains the reports and failure evidence for each tested commit.

| ID | Confirmed defect | Owning repair | Regression |
|---|---|---|---|
| D01 | Results read detached ListView rows and crash after scrolling | Persist outcomes by participant ID | `eightOffscreenResultsPersistAndCompletionDoesNotDependOnViews`; `scrollingEightPlayerResultsFinishFromBottomWithoutVisibleFirstRow` |
| D02 | Results disappear when returning from the scoreboard | Store partial outcomes in Room | `partialDraftAndActiveRoundSurviveCloseReopenIndependently`; `quickBidsFollowCurrentPlayerAndSelectionsSurviveScoreboardReturn` |
| D03 | Impossible Won/Lost combinations advance the game | Validate feasible trick allocations | `outcomeValidationMatchesIndependentExhaustiveTrickAllocationOracle`; `everyForbiddenTotalVectorIsRejectedAtDealerSubmissionAndEdit` |
| D04 | Quick bids retain references to the previous bidder's views | Submit against current bidder ID | `sequentialBidsDistinguishMissingFromZeroAndRejectStaleQuickBidIds`; `quickBidsFollowCurrentPlayerAndSelectionsSurviveScoreboardReturn` |
| D05 | Empty or oversized score corrections crash; integer totals overflow | Checked nonnegative long totals and inline validation | `blankAndOverflowScoreErrorsKeepCorrectionFormOpen`; `scoreOverflowRollsBackWholeCompletionAndExpectedTotalRejectsStaleEdits` |
| D06 | Renaming resets bid/result fields | Stable participant identity | `renamingDuringResultsPreservesBidOutcomeScoreAndDealerIdentity` |
| D07 | SQL LIKE treats percent and underscore as wildcards in player names | ID-based commands | `wildcardNamesRenameByIdAndCompletedSnapshotsRetainOldNames` |
| D08 | Seat swaps delete and reinsert player records | Separate seat order from IDs | `prospectiveRosterIsAtomicAndConfirmedChangesPreserveDealerAndScores`; `successfulDragCommitsCompleteOrderExactlyOnceAndKeepsDealerIdentity` |
| D09 | Back bypasses minimum roster validation; empty dealer screen traps users | Validate complete prospective rosters atomically | `ascendingRosterPastNewLastFinishesWithoutScoringAndRollsBackOnFailure`; `prospectiveRosterIsAtomicAndConfirmedChangesPreserveDealerAndScores` |
| D10 | Mid-round roster/dealer changes leave incompatible bids and results | Confirmed restart with a fresh round ID | `restartChangesIdentityAndRejectsAllOldRoundCallbacks`; `stagedRosterDiscardAndRestartCancellationLeaveGameUntouched` |
| D11 | Session state and logs exist only in static memory | Persist setup, rounds and immutable history | `partialDraftAndActiveRoundSurviveCloseReopenIndependently`; external background-kill and force-stop scenarios |
| D12 | Exit deletes state/kills process; cold Help exit crashes | Ordinary back navigation and Home Resume | `helpFromEmptyHomeReturnsSafelyAndExplainsThirteenPointBid`; external cold-launch scenarios |
| D13 | Multi-step writes leave partial scoring, roster or dealer changes | Repository transactions | `failedNextRoundInsertRollsBackScoresHistoryDealerAndPhase`; `ascendingRosterPastNewLastFinishesWithoutScoringAndRollsBackOnFailure`; `prospectiveRosterIsAtomicAndConfirmedChangesPreserveDealerAndScores` |
| D14 | Repeated round completion advances dealer/history repeatedly | Idempotent completion by round ID | `concurrentRepeatedCompletionHasExactlyOneEffect` |
| D15 | Dealer/settings choices reset on recreation | Persist draft configuration | `partialDraftAndActiveRoundSurviveCloseReopenIndependently`; `independentConcurrentSetupChoicesMergeWithoutLosingDirectionOrTrump` |
| D16 | Blank-looking names are accepted | Shared Unicode name validation | `rejectsEmptyWhitespaceControlAndInvisibleOnlyNames`; `newGameFormPreservesInvalidInputAndAcceptsFullUnicodeLongName` |
| D17 | Long names overlap controls; controls are tiny or unlabelled | Fixed-height name columns, full-name editing/tooltips, 48dp targets and accessible actions | `ScreenVisualTest` compact/long-name cases; `accessibleMovesDoNotWrapAndRestoreFocusAfterSnapshotRebinding`; keyboard-open UI regression |
| D18 | History numbers players using the round index | Immutable ordered round participant snapshots | `historyNumbersParticipantsWithinEachRound` |
| D19 | Help awards 12 points for a successful bid of three | Explain 10 + bid, including 13-point example | `helpFromEmptyHomeReturnsSafelyAndExplainsThirteenPointBid`; `scoringUsesCheckedNonnegativeLongTotalsAndCorrectBidThreeExample` |
| D20 | Incompatible historical databases all use version 1 | Recover all 4/6/7-column shapes without inventing game state | `fourColumnLegacyRecoversRawValuesThroughFinalSchema`, six/seven equivalents; `interruptedMigrationRollsBackAndRetryPreservesExactlyOneCopy`; unknown-schema and v2 upgrade cases |


## Defects found during implementation

| ID | Confirmed defect | Repair and regression |
|---|---|---|
| D21 | `BigInteger.longValueExact()` is unavailable on API 26 | Check bounds before `longValue()`; both Lint variants and API 26 repository scoring tests |
| D22 | Fixed header/footer consume the list at 200% fonts in landscape | Scroll header/content/footer together; `landscapeAtTwoHundredPercentFontKeepsSetupControlsReachable` |
| D23 | Concurrent setup choices overwrite unrelated draft fields | ID-based partial commands; `independentConcurrentSetupChoicesMergeWithoutLosingDirectionOrTrump`; stale reorder/rename/remove regressions |
| D24 | A row action immediately after a drop targets its former position | Capture the participant ID; gesture and stable-ID regressions |
| D25 | Open forms lose text or command targets after recreation | Saved typed form state; four `FormLifecycleTest` cases and external open-form background death |
| D26 | Roster Apply can target a different round after state changes | Capture the original round ID; stale-command and confirmed-restart regressions |
| D27 | Saving a staged participant writes a profile before Apply | Commit save intent inside the roster transaction; three repository save-intent regressions and managed-player UI cases |
| D28 | Navigation immediately after drop discards an accepted order | Commit on successful release; `releasedDropCommitsBeforeImmediateNavigationDetachesTheRoster` |
| D29 | Removed setup entries become inactive game participants | Exclude draft-only removals at Start; `startingGameExcludesRemovedSetupEntriesButKeepsTheirSavedProfiles` |
| D30 | Narrow large-font roster actions split their final letters | Use labeled 48dp edit/remove icons; assert fixed columns and preserved name data in visual layout tests |
| D31 | Deferred Start and Quick-add commands read a replacement draft from mutable screen state | Capture the original draft ID; `oldStartConfirmationCannotStartAReplacementDraft`; `queuedQuickAddCannotRetargetAReplacementDraft` |
| D32 | Command completion navigates a destroyed Activity after rotation | Observable ViewModel navigation; five `ReviewFlowRegressionTest` cases cover New game, Start, Apply, replay and restore |
| D33 | Restoring an inactive participant twice creates duplicate staged IDs | Filter and guard stable IDs; `restoredPlayerIsNotOfferedAgainInTheStagedRoster` |
| D34 | Scoring and player forms hide validation errors after keyboard resizing; dark Back icon lacks contrast | Reveal errors after viewport changes and use `colorOnSurface`; native strategy and `invalidNameAndSaveRemainReachableWithKeyboardActuallyOpen` regressions |
| D35 | Opening a new screen inherits the previous screen's scroll anchor and hides its heading | Reset the list anchor on screen changes; setup navigation regressions |
| D36 | Device tests delete the shared database before queued connection closure finishes | Await Activity and inspector repository shutdown during test cleanup; complete device suites exercise isolated fixtures |

## UX pass for v2.0.3

| ID | Confirmed defect | Repair and regression |
|---|---|---|
| D37 | Saved-player names ignore taps although their checkbox works | Forward name taps to the checkbox; `savedCheckboxAndRowTapsKeepRowsScrollAndActionsStableThroughRecyclingAndRotation` |
| D38 | Choosing a dealer replaces the list, rows and confirmation control | Update only selected rows and read the latest staged choice at Confirm; `dealerRadioAndRowTapsKeepRowsScrollAndConfirmationStableUntilConfirmed` |
| D39 | Result radio changes reset the list and rebuild Finish round | Diff persisted rows and retain screen controls; `resultRadioTapsKeepRowsScrollAndFinishStableAndPersistAcrossTabsAndRotation` |
| D40 | Scrolled custom-scoring validation clips the invalid field and floating label | Reveal the complete field when it fits, prioritizing its error in short viewports; `scrolledCustomValidationRevealsWholeInvalidFieldAndRetainsInput` |
| D41 | Prediction keyboard space is applied twice, collapsing the player list | Let the root own keyboard/system-bar space; `predictionKeyboardKeepsNavigationCompactAndPlayerInputReachable` |

Saved players now have independent per-row delete buttons, including already-added names. `deletingAlreadyAddedSavedProfileLeavesGameParticipantScoresAndDraftUntouched` verifies cancellation/deletion and preservation of participant identity, score, round and draft. The global delete control was removed. User-facing copy consistently uses rounds, cards, hands and predictions.

The broader CI suite also exposed D42: Aggressive secondary text had 4.29:1 contrast on its darkest light-theme container. v2.0.4 darkens that text slightly to meet the existing 4.5:1 minimum. `StrategyAppearanceTest` checks the actual text/container roles in both themes.

The API 36 full-run exposed D43: concurrent lifecycle observers could create duplicate form windows before the asynchronous OnShow callback registered ownership. The Activity now owns its dialog before showing it, rejects stale callbacks, and dismisses only its own window. `replayingSnapshotBeforeFormOnShowKeepsOneWindowAndPreservesEdits` and `replacingFormBeforeOnShowKeepsTheNewOwnerAndClosesEveryOldWindow` both fail against the previous implementation and pass after the repair.

## Named strategy library for v2.0.5

The scoring selector is a dropdown with immutable Default/Aggressive choices, saved custom rules with independent Modify controls, and Create custom last. The custom-only editor requires a name and scoring parameters; persisted entries expose a red Delete action. Names and parameters survive Activity recreation, and custom rules persist in Room v5. Catalog changes never rewrite copied game/round rules. Saving a profile and applying it to the chosen setup/round commit atomically. Name validation rejects blank-looking, reserved and duplicate names without losing entered values.

`CustomStrategyUiTest` covers dropdown order, immutable built-ins, name validation, recreate/reopen, modify/delete cancellation, and active-game isolation. `SavedStrategyRepositoryTest`, `ScoringMigrationTest`, and `ScoringStrategyTest` cover atomic rollback, durable catalog changes, legacy snapshots and safe named-rule encoding. Existing setup and strategy tests now use dropdown controls; the test helper skips hidden duplicate header controls.

## Roster progression and participation for v2.0.6

Roster changes preserve the original starting deal. Ascending games derive their last deal from the new active count and finish atomically if the unfinished deal exceeds it. Descending games keep last=1 and step the current deal down only when the new count exceeds deck capacity. Completed history/scores remain; changed unfinished rounds receive new IDs, cleared predictions/results, and the same chronological position and scoring rules. A removed dealer advances to the next surviving seat; inserted seats enter the normal cycle. Saved-player intent and roster/round changes remain one transaction.

Final rows add Rounds and Pts/round only when someone missed completed rounds. The denominator counts actual completed participation, including zero-point losses, excludes absences, and resumes the same identity after restoration. Zero rounds displays an em dash. Decimal arithmetic retains exact 64-bit score precision. Cumulative graph lines break for absent rounds and retain real totals when players return; unrelated score corrections do not fabricate participation.

`RosterProgressionUiTest`, `RepositoryRegressionTest`, `GameRulesTest`, `GameStatisticsTest`, `ScoreGraphViewTest`, and the normal/200%-font participation screenshot cases cover the changes, cancellation, dealer cycle, stale callbacks, durable state, graph gaps, and injected transaction failures. The final header uses a wider round-count column; large-font Rds retains the full Rounds played accessibility label. On narrow screens at200%font size, metrics move below each name so participants remain identifiable.

## Final participation ranking correction

When the final table displays Pts/round, its order and dense ranks use the displayed points-per-round metric descending. Equal averages retain stable seat order and share a rank; participants with no completed rounds sort last. The owning statistics model computes these standings once and the UI renders them directly. Active-game and uniform-participation tables retain total-point standings. Focused model cases and the final native table regression cover conflicting totals, ties, zero-round entrants, and recreation.

## Accepted legacy limitation

A malformed v1 database with mixed-case reserved table names such as `Game_Players` can fail recovery. The owner chose fresh installation over further migration edge-case work. The implemented and tested original 4/6/7-column recovery paths remain available.

## Delivery gates

The owner approved a new signing key and fresh installation instead of the original key. Releases v2.0.0 onward reuse that new certificate; v2.0.3 updates those releases in place. The original app must be uninstalled first. The release workflow verifies the production package, version, signature and runtime payload against its source build. Debug installs retain a separate identity.

created by chatgpt-codex

### Display zoom and enlarged text
- Replaced cramped roster action labels with accessible icon headers; kept names and touch controls compact.
- Allowed text rows, prediction inputs, result choices, strategy selectors, and fixed actions to measure their full text rather than clip inside fixed dimensions.
- Adapted final standings on enlarged-text phones and used a labelled Play again icon to preserve toolbar space.
- Regression: `ScreenVisualTest.zoomed*` checks 320dp/360dp layouts at 200% text, including glyph bounds, two-digit input, result labels, and reachable actions.

### Aggressive zero predictions and history order
- Successful zero predictions earn 10 on deals of 1–3 cards and 7 from 4 cards onward, independently of player count. Completion, history, edits, and statistics use this fixed threshold.
- History cards show newest event time first, including score corrections among completed rounds.
- Regression: scoring boundary matrix, repository completion/edit/reopen coverage, recorded-roster statistics, and mixed-event history ordering.

### Prediction entry guidance and visibility
- Dealer input shows a muted Not X hint for the legal prediction that would make total predictions equal the cards dealt; the hint never becomes a recorded value.
- Advancing predictions preserves the viewport when the current row is fully visible. Hidden rows move to the bottom of the viewport, including after the keyboard reduces available space.
- Regression: dealer hint recalculation/recycling, visible-row scroll stability, hidden-row bottom alignment, keyboard resize, and normal/200% text screenshots.

### Custom trump caller and zero-prediction note
- Custom trumps mark the first player to place the highest prediction, updating after edits and retaining the first caller on ties. Cyclic trumps show no player badge.
- Aggressive prediction entry shows the 7-point zero reward below the deal heading from four cards onward.
- Regression: `TrumpCallerTest`, scoring boundary matrix, and completion/historical-edit roster changes.

### Strategy selection layout
- Heading and single-select strategy buttons stay above the scrollable explanation and compact bulleted examples. Saved strategies remain selectable and editable; the add-players dropdown and custom-only editor remain available.
- Focused native checks exercise selection/theme preview, cancellation, heading/button order, fixed controls, and custom editing.
