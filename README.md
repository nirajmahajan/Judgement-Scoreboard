# Judgement Scoreboard

A native Android scorekeeper for Judgement. Android 8.0 or later is required.

## Play

Start a new game from Home, add players in seating order, then choose the dealer, deal direction and trump mode. Players bid sequentially, with the dealer last. After play, record Won for an exact bid or Lost for a missed bid. An exact bid earns **10 + bid** points; a missed bid earns zero. The app validates that the results can share the hand's tricks.

The active game and an unfinished setup are stored separately. Leaving the app preserves both. Starting a replacement game requires confirmation and does not remove saved players.

Saved players form an optional library. Add, search and select profiles to quick-add them to setup or a staged in-game roster. Profile edits affect future additions. Game participants retain their own names, identities, scores and history.

Drag a player's handle to change seating, or use its accessible Move up / Move down actions. In-game changes are staged until Apply. Structural changes after bidding starts require restarting the current round; completed rounds stay intact.

Choose **Default**, **Aggressive**, or **Custom** scoring before starting. Aggressive rewards an exact zero with 7 points and adds a majority bonus only for an exact bid strictly above half the hand. The bonus is capped at 10; total points are not capped. Custom scoring provides live examples. Strategy changes apply immediately before bidding or at the next round once bidding starts. Each completed round retains its own rules.

Open a completed round from History to correct bids and outcomes, preview score changes, and save an audited correction. Final statistics include exact-bid rates, successful bids, streaks, significant table awards and selectable cumulative/per-round graphs. Play again prepares the same table and settings for review before starting a new game.

## Develop

Use JDK 21, the committed Gradle wrapper and Android SDK 36/build tools 36.0.0. Set `ANDROID_HOME` to your SDK directory or create an untracked `local.properties` containing `sdk.dir=/absolute/sdk/path`.

```sh
python3 tools/ci/verify_wrapper.py
./gradlew --no-daemon --dependency-verification=strict assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug lintRelease
./gradlew --no-daemon --dependency-verification=strict connectedDebugAndroidTest
```

Debug uses `android.example.judgement.debug` and can coexist with the original app. The release application ID remains `android.example.judgement`. The checked-in original APK is retained solely for migration, certificate and install-over verification.

The domain package owns names, scoring, progression, bidding and mathematical outcome validation. The Room repository owns transactional ID-based commands and coherent snapshots. ViewModels dispatch commands off the main thread; RecyclerView row binding performs no database access. Room migrations preserve legacy player values as a labelled recovery scorecard because the old app never persisted a resumable round position.

See [the defect inventory](docs/DEFECTS.md), [Lint dispositions](docs/LINT.md), and [CI, screenshot and release instructions](tools/ci/README.md). Screenshot baselines are recorded and verified in the same pinned Linux container. CI verifies reviewed baselines and does not update them automatically.

Optional proposals are ranked separately in [follow-up features](docs/FOLLOW_UPS.md).

## Release

CI retains downloadable debug and unsigned release APKs. Production-compatible signing requires the original signing key, a higher version code, exact-commit automated verification, owner acceptance and approval through the protected release workflow. No push or pull request automatically publishes a release.

created by chatgpt-codex
