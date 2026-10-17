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
| D09 | Back bypasses minimum roster validation; empty dealer screen traps users | Validate complete prospective rosters atomically | `validatesBothRosterBoundariesAndCurrentHandBeforeAnEdit`; `prospectiveRosterIsAtomicAndConfirmedChangesPreserveDealerAndScores` |
| D10 | Mid-round roster/dealer changes leave incompatible bids and results | Confirmed restart with a fresh round ID | `restartChangesIdentityAndRejectsAllOldRoundCallbacks`; `stagedRosterDiscardAndRestartCancellationLeaveGameUntouched` |
| D11 | Session state and logs exist only in static memory | Persist setup, rounds and immutable history | `partialDraftAndActiveRoundSurviveCloseReopenIndependently`; external background-kill and force-stop scenarios |
| D12 | Exit deletes state/kills process; cold Help exit crashes | Ordinary back navigation and Home Resume | `helpFromEmptyHomeReturnsSafelyAndExplainsThirteenPointBid`; external cold-launch scenarios |
| D13 | Multi-step writes leave partial scoring, roster or dealer changes | Repository transactions | `failedNextRoundInsertRollsBackScoresHistoryDealerAndPhase`; `validatesBothRosterBoundariesAndCurrentHandBeforeAnEdit`; `prospectiveRosterIsAtomicAndConfirmedChangesPreserveDealerAndScores` |
| D14 | Repeated round completion advances dealer/history repeatedly | Idempotent completion by round ID | `concurrentRepeatedCompletionHasExactlyOneEffect` |
| D15 | Dealer/settings choices reset on recreation | Persist draft configuration | `partialDraftAndActiveRoundSurviveCloseReopenIndependently`; `independentConcurrentSetupChoicesMergeWithoutLosingDirectionOrTrump` |
| D16 | Blank-looking names are accepted | Shared Unicode name validation | `rejectsEmptyWhitespaceControlAndInvisibleOnlyNames`; `newGameFormPreservesInvalidInputAndAcceptsFullUnicodeLongName` |
| D17 | Long names overlap controls; controls are tiny or unlabelled | Wrapping layouts, 48dp targets and accessible actions | 13 `ScreenVisualTest` cases; `accessibleMovesDoNotWrapAndRestoreFocusAfterSnapshotRebinding`; keyboard-open UI regression |
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
| D30 | Narrow large-font roster actions split their final letters | Stack actions when space is constrained; assert complete single-line labels in visual layout tests |
| D31 | Deferred Start and Quick-add commands read a replacement draft from mutable screen state | Capture the original draft ID; `oldStartConfirmationCannotStartAReplacementDraft`; `queuedQuickAddCannotRetargetAReplacementDraft` |
| D32 | Command completion navigates a destroyed Activity after rotation | Observable ViewModel navigation; five `ReviewFlowRegressionTest` cases cover New game, Start, Apply, replay and restore |
| D33 | Restoring an inactive participant twice creates duplicate staged IDs | Filter and guard stable IDs; `restoringTheSameParticipantTwiceDoesNotDuplicateTheStagedRoster` |
| D34 | Scoring validation scroll restoration hides numeric errors; dark Back icon lacks contrast | Preserve reachable input errors and use `colorOnSurface`; native 320dp/200% font/dark/keyboard strategy regression |

## Accepted legacy limitation

A malformed v1 database with mixed-case reserved table names such as `Game_Players` can fail recovery. The owner chose fresh installation over further migration edge-case work. The implemented and tested original 4/6/7-column recovery paths remain available.

## Delivery gates

Production signing requires the original private signing key. Its certificate SHA-256 is `93:E3:66:B9:3C:CD:1C:53:1E:9A:28:2A:09:6C:CA:A9:BB:D7:19:D9:B8:C1:27:1E:7E:53:84:F7:48:4B:DA:50`.

The release application ID and legacy shared-user contract remain compatible. Debug installs have a separate identity. A release is published only after owner approval of the exact tested commit.

created by chatgpt-codex
