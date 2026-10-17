package android.example.judgement;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.hamcrest.Matchers.allOf;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import android.content.Context;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.view.accessibility.AccessibilityWindowInfo;
import android.example.judgement.data.DraftSpec;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.data.Records;
import android.example.judgement.data.ScoreDatabase;
import android.example.judgement.data.ScoreRepository;
import android.example.judgement.data.Snapshot;
import android.example.judgement.domain.Outcome;
import android.example.judgement.ui.MainActivity;
import android.example.judgement.ui.ScoreViewModel;
import android.os.SystemClock;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises the actual library, setup, confirmation, and staged-roster screens. */
@RunWith(AndroidJUnit4.class)
public class FeatureUiRegressionTest {
    private Context context;
    private ScoreRepository inspector;
    private ActivityScenario<MainActivity> scenario;

    @Before public void clearDatabase() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(ScoreDatabase.NAME);
        inspector = new ScoreRepository(context);
    }
    @After public void closeDatabase() {
        android.example.judgement.ui.RepositoryTestCleanup.close(scenario, inspector);
    }

    @Test public void savedLibraryCrudRejectsCanonicalDuplicateAndKeepsInvalidText() {
        launch(); openSavedManagement();
        addSaved("Élodie");
        long profileId = inspector.snapshot().savedPlayers.get(0).id;
        clickId(R.id.primary_action);
        String duplicate = "  E\u0301LODIE\u2003";
        enter(duplicate); save();
        awaitView(withId(R.id.edit_value));
        onView(withId(R.id.edit_value)).check(matches(withText(duplicate)));
        awaitView(withText("This name already has a saved player. Use the existing profile."));
        assertEquals(1, inspector.snapshot().savedPlayers.size());
        clickLabel(R.string.cancel);
        rowAction(0, text(R.string.rename));
        enter("Élodie with a longer complete name"); save();
        awaitState(s -> s.savedPlayers.get(0).name.equals("Élodie with a longer complete name"));
        assertEquals(profileId, inspector.snapshot().savedPlayers.get(0).id);
        rowAction(0, text(R.string.remove)); clickLabel(R.string.cancel);
        assertEquals(1, inspector.snapshot().savedPlayers.size());
        rowAction(0, text(R.string.remove)); clickDialogPositive();
        awaitState(s -> s.savedPlayers.isEmpty());
        try (ScoreRepository reopened = new ScoreRepository(context)) {
            assertTrue(reopened.snapshot().savedPlayers.isEmpty());
        }
    }

    @Test public void saveForFutureStartsUncheckedAndOffersCanonicalExistingProfile() {
        long profileId = inspector.addSavedPlayer("Élodie");
        launch(); clickId(R.id.primary_action);
        awaitView(withText(R.string.setup_players));
        clickId(R.id.add_player);
        onView(withText(R.string.save_future)).check(matches(not(isChecked())));
        String entered = "\u2003E\u0301LODIE  ";
        enter(entered);
        clickLabel(R.string.save_future); save();
        awaitView(withText(R.string.saved_exists));
        onView(withId(R.id.edit_value)).check(matches(withText(entered)));
        assertEquals(1, inspector.snapshot().savedPlayers.size());
        clickLabel(R.string.use_saved_player);
        awaitState(s -> s.draftPlayers.size() == 1);
        Records.GamePlayer player = inspector.snapshot().draftPlayers.get(0);
        assertEquals(Long.valueOf(profileId), player.savedPlayerId);
        assertEquals("Élodie", player.name);
        assertEquals("Élodie", inspector.snapshot().savedPlayers.get(0).name);
    }

    @Test public void savingNewPlayersRequiresOptInAndNeverPrefillsAnotherSetup() {
        launch(); clickId(R.id.primary_action);
        awaitState(s -> s.draft != null && s.draftPlayers.isEmpty());
        addGamePlayer("Session only"); awaitState(s -> s.draftPlayers.size() == 1);
        assertTrue(inspector.snapshot().savedPlayers.isEmpty());
        clickId(R.id.add_player); enter("Reusable player");
        clickLabel(R.string.save_future); save();
        awaitState(s -> s.draftPlayers.size() == 2 && s.savedPlayers.size() == 1);
        assertNull(inspector.snapshot().draftPlayers.get(0).savedPlayerId);
        assertEquals(Long.valueOf(inspector.snapshot().savedPlayers.get(0).id),
                inspector.snapshot().draftPlayers.get(1).savedPlayerId);
        completeSetupChoices(); assertScreenStartsAtTop();
        awaitView(withId(R.id.bottom_navigation));
        option(R.string.new_game);
        awaitState(s -> s.draft != null);
        assertTrue(inspector.snapshot().draftPlayers.isEmpty());
        assertEquals(1, inspector.snapshot().savedPlayers.size());
    }

    @Test public void invalidNameAndSaveRemainReachableWithKeyboardActuallyOpen() throws Exception {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String previousKeyboardSetting = device.executeShellCommand(
                "settings get secure show_ime_with_hard_keyboard").trim();
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1");
        try {
            launch(); clickId(R.id.primary_action); awaitView(withText(R.string.setup_players));
            clickId(R.id.add_player);
            onView(withId(R.id.edit_value)).perform(replaceText("   "), showKeyboard());
            awaitKeyboard("before-validation");
            onView(withId(android.R.id.button1)).perform(revealAndClick());
            onView(withId(R.id.edit_value)).check(matches(withText("   ")));
            awaitView(withText("Enter a player name with a visible character."));
            awaitKeyboard("after-validation");
            onView(withId(R.id.edit_value)).perform(replaceText("Keyboard player"));
            onView(withId(android.R.id.button1)).perform(revealAndClick());
            awaitState(s -> s.draftPlayers.size() == 1);
            assertEquals("Keyboard player", inspector.snapshot().draftPlayers.get(0).name);
        } finally {
            if ("null".equals(previousKeyboardSetting)) {
                device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard");
            } else {
                assertTrue(previousKeyboardSetting.matches("[01]"));
                device.executeShellCommand("settings put secure show_ime_with_hard_keyboard " + previousKeyboardSetting);
            }
        }
    }

    @Test public void quickAddSearchKeepsMultipleSelectionsAndDisablesProfileAndNameConflicts() {
        long alice = inspector.addSavedPlayer("Alice");
        long bob = inspector.addSavedPlayer("Bob");
        long carol = inspector.addSavedPlayer("Carol");
        inspector.addSavedPlayer("Table conflict");
        inspector.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec(0, "Alice", alice),
                new PlayerSpec("TABLE CONFLICT")), "ASCENDING", "FIXED", null));
        launch(); clickId(R.id.primary_action);
        awaitView(withText(R.string.setup_players));
        clickId(R.id.quick_add);
        awaitView(allOf(withId(R.id.saved_player_checkbox), hasSibling(withText("Alice"))));
        onView(allOf(withId(R.id.saved_player_checkbox), hasSibling(withText("Alice"))))
                .check(matches(allOf(not(isEnabled()), withContentDescription("Alice, " + text(R.string.already_added)))));
        onView(allOf(withId(R.id.saved_player_checkbox), hasSibling(withText("Table conflict"))))
                .check(matches(allOf(not(isEnabled()), withContentDescription("Table conflict, " + text(R.string.name_conflict)))));
        searchSaved("car");
        awaitRowCount(1);
        toggleSaved("Carol");
        searchSaved("");
        awaitRowCount(4);
        onView(allOf(withId(R.id.saved_player_checkbox), hasSibling(withText("Carol")))).check(matches(isChecked()));
        toggleSaved("Bob");
        clickId(R.id.primary_action);
        awaitState(s -> s.draftPlayers.size() == 4);
        List<Long> linked = new ArrayList<>();
        for (Records.GamePlayer player : inspector.snapshot().draftPlayers) linked.add(player.savedPlayerId);
        assertTrue(linked.containsAll(Arrays.asList(alice, bob, carol)));
        assertEquals(4, inspector.snapshot().savedPlayers.size());
        assertEquals("Bob", inspector.snapshot().draftPlayers.get(2).name);
        assertEquals("Carol", inspector.snapshot().draftPlayers.get(3).name);
    }

    @Test public void stagedRosterDiscardAndRestartCancellationLeaveGameUntouched() {
        seedActive(true, false); launchGame();
        Snapshot original = inspector.snapshot();
        option(R.string.manage_players);
        addGamePlayer("Dana");
        awaitRowCount(4);
        assertUnchangedRound(original);
        pressBack(); awaitView(withText(R.string.discard_title)); clickLabel(R.string.cancel);
        awaitRowCount(4);
        pressBack(); clickLabel(R.string.discard);
        awaitView(withId(R.id.bottom_navigation));
        assertUnchangedRound(original);
        option(R.string.manage_players); addGamePlayer("Dana");
        clickId(R.id.primary_action); confirmCurrentRosterDealer(original);
        awaitView(withText(R.string.restart_title)); clickLabel(R.string.cancel);
        assertUnchangedRound(original);
        awaitRowCount(4);
        clickId(R.id.primary_action); clickDialogPositive();
        awaitState(s -> s.players.size() == 4 && s.round.id != original.round.id);
        Snapshot applied = inspector.snapshot();
        assertEquals(original.round.number, applied.round.number);
        assertEquals(original.round.cards, applied.round.cards);
        assertEquals(original.round.dealerId, applied.round.dealerId);
        assertEquals(original.history.size(), applied.history.size());
        assertEquals(original.totals.get(original.players.get(0).id), applied.totals.get(original.players.get(0).id));
        for (Records.RoundPlayer player : applied.roundPlayers) { assertNull(player.bid); assertNull(player.outcome); }
    }

    @Test public void managePlayerSaveIntentCommitsOnlyAfterConfirmedApply() {
        seedActive(true, false); launchGame();
        Snapshot original = inspector.snapshot();
        option(R.string.manage_players);
        addStagedSavedPlayer("Reusable at this table");
        awaitRowCount(4);
        assertTrue(inspector.snapshot().savedPlayers.isEmpty());
        assertUnchangedRound(original);
        pressBack(); clickLabel(R.string.discard);
        assertTrue(inspector.snapshot().savedPlayers.isEmpty());
        assertUnchangedRound(original);
        option(R.string.manage_players);
        addStagedSavedPlayer("Reusable at this table");
        clickId(R.id.primary_action); confirmCurrentRosterDealer(original);
        awaitView(withText(R.string.restart_title));
        clickDialogPositive();
        awaitState(s -> s.players.size() == 4 && s.savedPlayers.size() == 1);
        Snapshot applied = inspector.snapshot();
        assertEquals("Reusable at this table", applied.savedPlayers.get(0).name);
        assertEquals(Long.valueOf(applied.savedPlayers.get(0).id), applied.players.get(3).savedPlayerId);
        assertNotEquals(original.round.id, applied.round.id);
        assertEquals(original.history.size(), applied.history.size());
    }

    @Test public void existingSavedProfileIsOfferedInManageWithoutWritingBeforeApply() {
        long savedId = inspector.addSavedPlayer("Élodie");
        seedActive(false, false); launchGame();
        Snapshot original = inspector.snapshot();
        option(R.string.manage_players);
        clickId(R.id.add_player); enter("E\u0301LODIE");
        clickLabel(R.string.save_future); save();
        awaitView(withText(R.string.saved_exists));
        clickLabel(R.string.use_saved_player);
        awaitRowCount(4);
        assertRowText(3, "Élodie");
        assertUnchangedRound(original);
        assertEquals(1, inspector.snapshot().savedPlayers.size());
        clickId(R.id.primary_action); confirmCurrentRosterDealer(original);
        awaitView(withText(R.string.restart_title)); clickDialogPositive();
        awaitState(s -> s.players.size() == 4);
        Snapshot applied = inspector.snapshot();
        assertEquals("Élodie", applied.players.get(3).name);
        assertEquals(Long.valueOf(savedId), applied.players.get(3).savedPlayerId);
        assertEquals(1, applied.savedPlayers.size());
    }

    @Test public void removingDealerPreselectsNextSeatAndAllowsAnExplicitReplacement() {
        seedActive(false, true); launchGame();
        Snapshot original = inspector.snapshot();
        long first = original.players.get(0).id;
        long removed = original.players.get(1).id;
        long next = original.players.get(2).id;
        assertEquals(removed, original.round.dealerId);
        option(R.string.manage_players);
        rowAction(1, text(R.string.remove));
        awaitRowCount(2);
        assertEquals(removed, inspector.snapshot().round.dealerId);
        clickLabel(R.string.choose_dealer);
        onView(allOf(withId(R.id.dealer_choice_radio), hasSibling(withText("Player C")))).check(matches(isChecked()));
        onView(allOf(withId(R.id.dealer_choice_radio), hasSibling(withText("Player A")))).perform(click());
        assertEquals(removed, inspector.snapshot().round.dealerId);
        clickId(R.id.primary_action);
        awaitView(withText(R.string.manage_players));
        clickId(R.id.primary_action); clickDialogPositive();
        awaitState(s -> s.round.id != original.round.id && s.players.size() == 2);
        Snapshot applied = inspector.snapshot();
        assertEquals(first, applied.round.dealerId);
        assertEquals(next, applied.players.get(1).id);
        assertEquals(removed, applied.inactivePlayers.get(0).id);
    }

    @Test public void openingNewGamePreservesCurrentGameUntilConfirmedStart() {
        inspector.addSavedPlayer("Keep for future games");
        seedActive(true, false); launchGame();
        Snapshot original = inspector.snapshot();
        option(R.string.new_game);
        awaitView(withText(R.string.setup_players));
        assertUnchangedRound(original);
        addGamePlayer("New player A"); awaitState(s -> s.draftPlayers.size() == 1);
        addGamePlayer("New player B"); awaitState(s -> s.draftPlayers.size() == 2);
        completeSetupChoices(); assertScreenStartsAtTop();
        awaitView(withText(R.string.replace_game_title)); clickLabel(R.string.cancel);
        assertUnchangedRound(original);
        completeSetupChoices(); clickId(R.id.primary_action);
        awaitState(s -> s.game.id != original.game.id);
        Snapshot replacement = inspector.snapshot();
        assertEquals(2, replacement.players.size());
        assertEquals(1, replacement.round.number);
        assertTrue(replacement.history.isEmpty());
        for (long total : replacement.totals.values()) assertEquals(0L, total);
        assertEquals(1, replacement.savedPlayers.size());
        assertEquals("Keep for future games", replacement.savedPlayers.get(0).name);
    }

    @Test public void oldStartConfirmationCannotStartAReplacementDraft() throws Exception {
        seedActive(false, false);
        long originalDraft = inspector.saveDraft(new DraftSpec(0, Arrays.asList(
                new PlayerSpec("Original setup A"), new PlayerSpec("Original setup B")),
                "ASCENDING", "FIXED", null));
        launch(); clickId(R.id.primary_action);
        completeSetupChoices(); assertScreenStartsAtTop();
        awaitView(withText(R.string.replace_game_title));
        ScoreViewModel model = activityModel();
        AtomicLong replacementDraft = new AtomicLong();
        model.repository.execute(() -> {
            model.repository.startGame(originalDraft);
            replacementDraft.set(model.repository.saveDraft(new DraftSpec(0, Arrays.asList(
                    new PlayerSpec("Replacement setup A"), new PlayerSpec("Replacement setup B")),
                    "ASCENDING", "FIXED", null)));
        });
        awaitExecutor(model.repository);
        awaitActivityDraft(model, replacementDraft.get());
        awaitListUpdates();
        Snapshot expected = inspector.snapshot();
        assertEquals(originalDraft, expected.game.id);
        clickId(R.id.primary_action);
        awaitExecutor(model.repository);
        Snapshot after = inspector.snapshot();
        assertEquals("The old confirmation must keep the independently started game", originalDraft, after.game.id);
        assertNotNull("The replacement setup must remain a draft", after.draft);
        assertEquals(replacementDraft.get(), after.draft.id);
        assertEquals(expected.round.id, after.round.id);
        assertEquals(expected.totals, after.totals);
        awaitView(withText("This setup has already started."));
    }

    @Test public void queuedQuickAddCannotRetargetAReplacementDraft() throws Exception {
        inspector.addSavedPlayer("Selected saved profile");
        inspector.saveDraft(new DraftSpec(0, new ArrayList<>(), "ASCENDING", "FIXED", null));
        launch(); clickId(R.id.primary_action); clickId(R.id.quick_add);
        toggleSaved("Selected saved profile");
        ScoreViewModel model = activityModel();
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        model.repository.execute(() -> {
            blocked.countDown();
            try {
                if (!release.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Test command barrier timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted);
            }
        });
        assertTrue("The actual Activity command executor is blocked", blocked.await(10, TimeUnit.SECONDS));
        long replacementDraft;
        try {
            clickId(R.id.primary_action);
            replacementDraft = inspector.saveDraft(new DraftSpec(0, new ArrayList<>(), "ASCENDING", "FIXED", null));
            model.repository.refresh();
            awaitActivityDraft(model, replacementDraft);
        } finally { release.countDown(); }
        awaitExecutor(model.repository);
        Snapshot after = inspector.snapshot();
        assertNotNull(after.draft);
        assertEquals(replacementDraft, after.draft.id);
        assertTrue("Queued selection must not add anyone to a later setup", after.draftPlayers.isEmpty());
        assertEquals(1, after.savedPlayers.size());
        awaitView(withText("Open setup before adding saved players."));
    }

    @Test public void editingAndDeletingSavedProfileNeverChangesItsActiveParticipantOrHistory() {
        long profile = inspector.addSavedPlayer("Original saved name");
        long draft = inspector.saveDraft(new DraftSpec(0, Arrays.asList(
                new PlayerSpec(0, "Original saved name", profile), new PlayerSpec("Other player")),
                "ASCENDING", "FIXED", null));
        inspector.startGame(draft); completeRound();
        Snapshot original = inspector.snapshot();
        launch(); openSavedManagement();
        rowAction(0, text(R.string.rename)); enter("Renamed saved profile"); save();
        awaitState(s -> s.savedPlayers.get(0).name.equals("Renamed saved profile"));
        rowAction(0, text(R.string.remove)); clickDialogPositive();
        awaitState(s -> s.savedPlayers.isEmpty());
        Snapshot after = inspector.snapshot();
        assertEquals(original.players.get(0).id, after.players.get(0).id);
        assertEquals("Original saved name", after.players.get(0).name);
        assertEquals(original.totals, after.totals);
        assertEquals(original.history.get(0).players.get(0).nameSnapshot,
                after.history.get(0).players.get(0).nameSnapshot);
    }

    @Test public void historyNumbersParticipantsWithinEachRound() {
        seedActive(false, false);
        completeRound(); completeRound();
        Snapshot completed = inspector.snapshot();
        assertEquals(2, completed.history.size());
        launchGame(); onView(withId(R.id.tab_history)).perform(click());
        for (int roundIndex = 0; roundIndex < 2; roundIndex++) {
            final Snapshot.RoundHistory history = completed.history.get(roundIndex);
            onView(withId(R.id.screen_list)).perform(RecyclerViewActions.actionOnItemAtPosition(roundIndex + 1,
                    new ViewAction() {
                        @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
                        @Override public String getDescription() { return "check independent participant numbering for round " + history.round.number; }
                        @Override public void perform(UiController controller, View row) {
                            String detail = ((TextView) row.findViewById(R.id.row_detail)).getText().toString();
                            for (int seat = 0; seat < history.players.size(); seat++) {
                                assertTrue("Each round numbers every recorded participant from1: " + detail,
                                        detail.contains((seat + 1) + " · " + history.players.get(seat).nameSnapshot));
                            }
                        }
                    }));
        }
    }

    @Test public void helpFromEmptyHomeReturnsSafelyAndExplainsThirteenPointBid() {
        launch(); clickId(R.id.home_help);
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.help_title)));
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.actionOnItemAtPosition(1,
                new ViewAction() {
                    @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
                    @Override public String getDescription() { return "verify scoring instructions"; }
                    @Override public void perform(UiController controller, View row) {
                        TextView detail = row.findViewById(R.id.row_detail);
                        assertTrue(detail.getText().toString().contains("Predict 3 and win 3 → 13 points"));
                        assertTrue(detail.getText().toString().contains(
                                "mark Won if the hands a player won matched their prediction, otherwise Lost"));
                    }
                }));
        pressBack(); awaitView(withText(R.string.home_title));
        scenario.recreate(); awaitView(withText(R.string.home_title));
        assertNull(inspector.snapshot().game); assertNull(inspector.snapshot().draft);
    }

    private ScoreViewModel activityModel() {
        AtomicReference<ScoreViewModel> actual = new AtomicReference<>();
        scenario.onActivity(activity -> {
            try {
                java.lang.reflect.Field field = MainActivity.class.getDeclaredField("model");
                field.setAccessible(true);
                actual.set((ScoreViewModel) field.get(activity));
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        assertNotNull(actual.get());
        return actual.get();
    }
    private static void awaitExecutor(ScoreRepository repository) throws InterruptedException {
        CountDownLatch complete = new CountDownLatch(1);
        repository.execute(complete::countDown);
        assertTrue("Queued UI commands finish", complete.await(10, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void awaitActivityDraft(ScoreViewModel model, long draftId) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        do {
            AtomicReference<Snapshot> observed = new AtomicReference<>();
            scenario.onActivity(activity -> observed.set(model.state.getValue()));
            Snapshot current = observed.get();
            if (current != null && current.draft != null && current.draft.id == draftId) return;
            SystemClock.sleep(20);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Activity did not observe replacement draft " + draftId);
    }

    private void awaitListUpdates() {
        AtomicReference<Boolean> settled = new AtomicReference<>(false);
        long deadline = SystemClock.uptimeMillis() + 10_000;
        do {
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.screen_list);
                RecyclerView.ItemAnimator animator = list.getItemAnimator();
                settled.set(!list.hasPendingAdapterUpdates() && !list.isComputingLayout()
                        && (animator == null || !animator.isRunning()));
            });
            if (settled.get()) return;
            SystemClock.sleep(20);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("RecyclerView snapshot updates did not settle");
    }

    private void launch() {
        scenario = ActivityScenario.launch(MainActivity.class);
        awaitView(withText(R.string.home_title));
    }
    private void launchGame() { launch(); clickId(R.id.resume_game); awaitView(withId(R.id.bottom_navigation)); }
    private void option(int label) {
        Espresso.openActionBarOverflowOrOptionsMenu(context);
        onView(withText(label)).perform(click());
    }
    private void addSaved(String name) {
        int count = inspector.snapshot().savedPlayers.size();
        clickId(R.id.primary_action); enter(name); save();
        awaitState(s -> s.savedPlayers.size() == count + 1);
    }
    private void addGamePlayer(String name) { clickId(R.id.add_player); enter(name); save(); }
    private void addStagedSavedPlayer(String name) {
        clickId(R.id.add_player); enter(name);
        onView(withText(R.string.save_future)).check(matches(not(isChecked())));
        clickLabel(R.string.save_future); save();
    }
    private void enter(String value) { onView(withId(R.id.edit_value)).perform(replaceText(value), closeSoftKeyboard()); }
    private void save() { onView(withId(android.R.id.button1)).perform(click()); }
    private void clickDialogPositive() { onView(withId(android.R.id.button1)).perform(click()); }
    private String text(int id) { return context.getString(id); }
    private void clickLabel(int id) {
        if (id == R.string.cancel || id == R.string.discard || id == R.string.save_future
                || id == R.string.use_saved_player) {
            onView(allOf(withText(id), isClickable())).perform(revealAndClick());
        } else {
            onView(withId(R.id.screen_list)).perform(clickInList(allOf(withText(id), isClickable())));
        }
    }
    private static void clickId(int id) {
        Matcher<View> visible = allOf(withId(id), isDisplayed());
        try { onView(visible).check(matches(isDisplayed())); }
        catch (androidx.test.espresso.NoMatchingViewException missing) {
            onView(withId(R.id.screen_list)).perform(clickInList(withId(id))); return;
        }
        onView(visible).perform(click());
    }
    private void openSavedManagement() {
        clickId(R.id.primary_action);
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.setup_players)));
        clickId(R.id.quick_add);
        Espresso.openActionBarOverflowOrOptionsMenu(context);
        onView(withText(R.string.manage_saved_players)).perform(click());
    }
    private void searchSaved(String query) {
        Espresso.openActionBarOverflowOrOptionsMenu(context);
        onView(withText(R.string.search_saved)).perform(click()); enter(query); save();
    }
    private static void toggleSaved(String name) {
        onView(allOf(withId(R.id.saved_player_checkbox), hasSibling(withText(name)))).perform(click());
    }
    private void confirmCurrentRosterDealer(Snapshot original) {
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.choose_dealer)));
        String dealer = null;
        for (Records.GamePlayer player : original.players)
            if (player.id == original.round.dealerId) dealer = player.name;
        assertNotNull("The existing dealer stays available for confirmation", dealer);
        onView(allOf(withId(R.id.dealer_choice_radio), hasSibling(withText(dealer))))
                .check(matches(isChecked()));
        assertUnchangedRound(original);
        clickId(R.id.primary_action);
    }

    private void completeSetupChoices() {
        clickId(R.id.primary_action);
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.choose_deal_order)));
        clickId(R.id.deal_order_ascending);
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.choose_dealer)));
        List<Records.GamePlayer> players = inspector.snapshot().draftPlayers;
        onView(allOf(withId(R.id.dealer_choice_radio), hasSibling(withText(players.get(players.size() - 1).name))))
                .perform(click());
        clickId(R.id.primary_action);
        if (inspector.snapshot().game != null && inspector.snapshot().draft != null) {
            awaitView(allOf(withId(R.id.screen_title), withText(R.string.replace_game_title)));
        } else awaitView(withId(R.id.bottom_navigation));
    }
    private static ViewAction clickInList(Matcher<View> target) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() {
                return isAssignableFrom(androidx.recyclerview.widget.RecyclerView.class);
            }
            @Override public String getDescription() { return "scroll actual rows to a reachable control"; }
            @Override public void perform(UiController controller, View view) {
                androidx.recyclerview.widget.RecyclerView list = (androidx.recyclerview.widget.RecyclerView) view;
                for (int position = 0; position < list.getAdapter().getItemCount(); position++) {
                    list.scrollToPosition(position);
                    controller.loopMainThreadUntilIdle();
                    androidx.recyclerview.widget.RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
                    View control = holder == null ? null : findMatching(holder.itemView, target);
                    if (control != null) {
                        revealAndClick().perform(controller, control);
                        return;
                    }
                }
                throw new AssertionError("No row contains the requested control: " + target);
            }
        };
    }
    private static View findMatching(View view, Matcher<View> matcher) {
        if (matcher.matches(view)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findMatching(group.getChildAt(i), matcher);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void seedActive(boolean completedRound, boolean middleDealer) {
        long draft = inspector.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("Player A"),
                new PlayerSpec("Player B"), new PlayerSpec("Player C")), "ASCENDING", "FIXED", null));
        if (middleDealer) {
            List<PlayerSpec> players = new ArrayList<>();
            Snapshot state = inspector.snapshot();
            for (Records.GamePlayer player : state.draftPlayers) players.add(new PlayerSpec(player.id, player.name));
            inspector.saveDraft(new DraftSpec(draft, players, "ASCENDING", "FIXED", state.draftPlayers.get(1).id));
        }
        inspector.startGame(draft);
        if (completedRound) completeRound();
        Snapshot active = inspector.snapshot();
        inspector.submitBid(active.round.id, active.roundPlayers.get(0).playerId, 0);
    }
    private void completeRound() {
        Snapshot state = inspector.snapshot();
        for (Records.RoundPlayer player : state.roundPlayers) inspector.submitBid(state.round.id, player.playerId, 0);
        inspector.recordBids(state.round.id);
        for (int i = 0; i < state.roundPlayers.size(); i++) inspector.setOutcome(state.round.id,
                state.roundPlayers.get(i).playerId, i == 0 ? Outcome.LOST : Outcome.WON);
        inspector.completeRound(state.round.id);
    }
    private void assertUnchangedRound(Snapshot original) {
        Snapshot current = inspector.snapshot();
        assertEquals(original.game.id, current.game.id); assertEquals(original.round.id, current.round.id);
        assertEquals(original.players.size(), current.players.size());
        assertEquals(original.roundPlayers.get(0).bid, current.roundPlayers.get(0).bid);
        assertEquals(original.totals, current.totals); assertEquals(original.history.size(), current.history.size());
    }
    private void awaitState(Predicate<Snapshot> expected) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        do {
            if (expected.test(inspector.snapshot())) return;
            SystemClock.sleep(30);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Persisted UI command did not complete within10seconds");
    }
    private static void awaitView(Matcher<View> matcher) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        AssertionError last = null;
        do {
            try { onView(matcher).check(matches(isDisplayed())); return; }
            catch (androidx.test.espresso.NoMatchingViewException | AssertionError failure) {
                last = new AssertionError(failure); SystemClock.sleep(30);
            }
        } while (SystemClock.uptimeMillis() < deadline);
        throw last == null ? new AssertionError("Visible screen did not arrive") : last;
    }
    private static void assertScreenStartsAtTop() {
        onView(withId(R.id.screen_list)).check((view, missing) -> {
            if (missing != null) throw missing;
            androidx.recyclerview.widget.RecyclerView list = (androidx.recyclerview.widget.RecyclerView) view;
            androidx.recyclerview.widget.LinearLayoutManager layout =
                    (androidx.recyclerview.widget.LinearLayoutManager) list.getLayoutManager();
            assertEquals("Footer navigation must show the new screen from its heading", 0,
                    layout.findFirstVisibleItemPosition());
            View header = layout.findViewByPosition(0);
            assertNotNull("The new screen heading is attached", header);
            assertEquals("The heading starts at the top of the viewport", list.getPaddingTop(),
                    layout.getDecoratedTop(header));
        });
    }
    private static void awaitRowCount(int count) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        do {
            final boolean[] ready = {false};
            onView(withId(R.id.screen_list)).check((view, missing) -> {
                if (missing != null) throw missing;
                ready[0] = ((androidx.recyclerview.widget.RecyclerView) view).getAdapter().getItemCount() == count + 2;
            });
            if (ready[0]) return;
            SystemClock.sleep(30);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Expected " + count + " roster rows");
    }
    private static void rowAction(int position, String label) {
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.actionOnItemAtPosition(position + 1, new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            @Override public String getDescription() { return "click row action " + label; }
            @Override public void perform(UiController controller, View row) {
                View button = findText(row, label, true);
                assertNotNull("Missing row action " + label, button);
                revealAndClick().perform(controller, button);
            }
        }));
    }
    private static void assertRowText(int position, String label) {
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.actionOnItemAtPosition(position + 1, new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            @Override public String getDescription() { return "check roster row text " + label; }
            @Override public void perform(UiController controller, View row) { assertNotNull(findText(row, label, false)); }
        }));
    }
    private static ViewAction showKeyboard() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(TextView.class); }
            @Override public String getDescription() { return "focus the name and open the software keyboard"; }
            @Override public void perform(UiController controller, View view) {
                view.requestFocus();
                InputMethodManager keyboard = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                keyboard.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
                controller.loopMainThreadUntilIdle();
            }
        };
    }
    private static void awaitKeyboard(String phase) throws Exception {
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        AccessibilityServiceInfo service = automation.getServiceInfo();
        service.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        automation.setServiceInfo(service);
        long deadline = SystemClock.uptimeMillis() + 10_000;
        do {
            for (AccessibilityWindowInfo window : automation.getWindows()) {
                Rect bounds = new Rect();
                window.getBoundsInScreen(bounds);
                if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD && !bounds.isEmpty()) {
                    android.util.Log.i("KeyboardRegression", "Visible IME window " + phase + ": " + bounds);
                    UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
                    device.executeShellCommand("mkdir -p /sdcard/judgement-test-evidence");
                    device.executeShellCommand("screencap -p /sdcard/judgement-test-evidence/ime-" + phase + ".png");
                    for (AccessibilityWindowInfo visibleWindow : automation.getWindows()) {
                        android.util.Log.i("KeyboardRegression", phase + " window dump: " + visibleWindow);
                    }
                    return;
                }
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("A visible input-method window did not appear");
    }
    private static ViewAction revealAndClick() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            @Override public String getDescription() { return "reveal and click a reachable control"; }
            @Override public void perform(UiController controller, View view) {
                view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
                controller.loopMainThreadUntilIdle();
                assertTrue("Control is visible and reachable", androidx.test.espresso.matcher.ViewMatchers
                        .isDisplayingAtLeast(90).matches(view));
                click().perform(controller, view);
            }
        };
    }
    private static View findText(View view, String label, boolean clickable) {
        if (view instanceof TextView && (!clickable || view.isClickable()) && label.contentEquals(((TextView) view).getText())) return view;
        if (clickable && view.isClickable() && view.getContentDescription() != null
                && view.getContentDescription().toString().startsWith(label + " ")) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), label, clickable); if (found != null) return found;
            }
        }
        return null;
    }
}
