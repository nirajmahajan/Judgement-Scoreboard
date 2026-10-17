package android.example.judgement.ui;

import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.data.ScoreDatabase;
import android.example.judgement.data.ScoreRepository;
import android.example.judgement.data.Snapshot;
import android.example.judgement.domain.ScoringStrategy;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.function.Predicate;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

/** Essential coverage for the compact setup wizard and saved-player checkboxes. */
@RunWith(AndroidJUnit4.class)
public final class SetupFlowRegressionTest {
    private Context context;
    private ScoreRepository repository;
    private ActivityScenario<MainActivity> scenario;

    @Before public void clearDatabase() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(ScoreDatabase.NAME);
        repository = new ScoreRepository(context);
    }

    @After public void close() { RepositoryTestCleanup.close(scenario, repository); }

    @Test public void startRequiresDirectionThenExplicitDealerAndOpensGame() {
        newGame();
        assertEquals("CUSTOM", repository.snapshot().draft.trumpMode);
        addPlayer("Alice", 1); addPlayer("Ben", 2);
        clickId(R.id.primary_action);
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.choose_deal_order)));
        onView(withId(R.id.deal_order_ascending)).check(matches(withText("1 card → 26 cards")));
        onView(withId(R.id.deal_order_descending)).check(matches(withText("26 cards → 1 card"))).perform(click());
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.choose_dealer)));
        scenario.recreate();
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.choose_dealer)));
        onView(allOf(withId(R.id.dealer_choice_radio), hasSibling(withText("Ben")))).perform(click());
        awaitView(withId(R.id.bottom_navigation));
        awaitState(state -> state.game != null && state.draft == null);
        Snapshot state = repository.snapshot();
        assertEquals("DESCENDING", state.game.direction);
        assertEquals(Long.valueOf(state.players.get(1).id), state.game.dealerId);
        assertEquals("CUSTOM", state.game.trumpMode);
    }

    @Test public void playerStrategyChoicesShowDetailsPersistAndReturnFromCustomEditor() {
        newGame();
        onView(withId(R.id.setup_strategy_default)).check(matches(isChecked()));
        onView(withId(R.id.default_strategy_info)).perform(click());
        awaitView(withText(org.hamcrest.Matchers.containsString(context.getString(R.string.strategy_default_rules))));
        androidx.test.espresso.Espresso.pressBack();
        onView(withId(R.id.setup_strategy_aggressive)).perform(click());
        awaitState(state -> ScoringStrategy.decode(state.draft.scoringConfig).preset == ScoringStrategy.Preset.AGGRESSIVE);
        awaitView(allOf(withId(R.id.setup_strategy_aggressive), isChecked()));
        scenario.recreate();
        awaitView(allOf(withId(R.id.setup_strategy_aggressive), isChecked()));
        onView(withId(R.id.create_custom_strategy)).perform(click());
        awaitView(allOf(withId(R.id.strategy_custom), isChecked()));
        onView(withId(R.id.strategy_zero)).perform(replaceText("7"), closeSoftKeyboard());
        clickId(R.id.primary_action);
        awaitState(state -> ScoringStrategy.decode(state.draft.scoringConfig).preset == ScoringStrategy.Preset.CUSTOM);
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.setup_players)));
        assertEquals(7L, ScoringStrategy.decode(repository.snapshot().draft.scoringConfig).zeroReward);
        onView(withId(R.id.setup_strategy_default)).check(matches(org.hamcrest.Matchers.not(isChecked())));
        onView(withId(R.id.setup_strategy_aggressive)).check(matches(org.hamcrest.Matchers.not(isChecked())));
    }

    @Test public void savedPlayerPlusCreatesProfileWithoutAddingItToTheGame() {
        newGame(); clickId(R.id.quick_add);
        onView(withId(R.id.add_saved_player)).perform(click());
        onView(withId(R.id.edit_value)).perform(replaceText("Charlotte"), closeSoftKeyboard());
        onView(withText(R.string.save)).perform(click());
        awaitState(state -> state.savedPlayers.size() == 1);
        assertTrue("Saving a profile does not add it to the game", repository.snapshot().draftPlayers.isEmpty());
        scenario.recreate();
        awaitView(allOf(withId(R.id.saved_player_checkbox), hasSibling(withText("Charlotte"))));
        onView(allOf(withId(R.id.saved_player_checkbox), hasSibling(withText("Charlotte")))).perform(click());
        onView(allOf(withId(R.id.primary_action), isDisplayed())).perform(click());
        awaitState(state -> state.draftPlayers.size() == 1);
        assertEquals("Charlotte", repository.snapshot().draftPlayers.get(0).name);
    }

    @Test public void savedPlayerBinDeletesSelectionAndPreservesExistingRoster() {
        long alice = repository.addSavedPlayer("Alice"); repository.addSavedPlayer("Ben");
        repository.saveDraft(new android.example.judgement.data.DraftSpec(0, java.util.Arrays.asList(
                new android.example.judgement.data.PlayerSpec(0, "Alice", alice)), "ASCENDING", "CUSTOM", null));
        long participantId = repository.snapshot().draftPlayers.get(0).id;
        scenario = ActivityScenario.launch(MainActivity.class);
        awaitView(withText(R.string.home_title));
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.scrollTo(hasDescendant(withText(R.string.saved_players))));
        onView(allOf(withText(R.string.saved_players), isClickable())).perform(click());
        onView(withId(R.id.delete_saved_players)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
        onView(allOf(withId(R.id.saved_player_checkbox), hasSibling(withText("Alice")))).perform(click());
        onView(allOf(withId(R.id.saved_player_checkbox), hasSibling(withText("Ben")))).perform(click());
        onView(withId(R.id.delete_saved_players)).perform(click());
        onView(withText(R.string.cancel)).perform(click());
        assertEquals(2, repository.snapshot().savedPlayers.size());
        onView(withId(R.id.delete_saved_players)).perform(click());
        onView(withId(android.R.id.button1)).perform(click());
        awaitState(state -> state.savedPlayers.isEmpty());
        assertEquals(participantId, repository.snapshot().draftPlayers.get(0).id);
        assertEquals("Alice", repository.snapshot().draftPlayers.get(0).name);
        onView(withId(R.id.primary_action)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
    }

    @Test public void savedPlayerCheckboxesAddTheWholeSelectionOnExplicitBottomAction() {
        long alice = repository.addSavedPlayer("Alice"), ben = repository.addSavedPlayer("Ben");
        newGame(); clickId(R.id.quick_add);
        awaitView(allOf(withId(R.id.saved_player_checkbox), hasSibling(withText("Alice"))));
        onView(allOf(withId(R.id.saved_player_checkbox), hasSibling(withText("Alice")))).perform(click());
        onView(allOf(withId(R.id.saved_player_checkbox), hasSibling(withText("Ben")))).perform(click());
        assertTrue("Checking profiles does not prematurely change the roster", repository.snapshot().draftPlayers.isEmpty());
        onView(allOf(withId(R.id.primary_action), isDisplayed())).perform(click());
        awaitState(state -> state.draftPlayers.size() == 2);
        assertEquals(Long.valueOf(alice), repository.snapshot().draftPlayers.get(0).savedPlayerId);
        assertEquals(Long.valueOf(ben), repository.snapshot().draftPlayers.get(1).savedPlayerId);
        assertEquals(2, repository.snapshot().savedPlayers.size());
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.setup_players)));
    }

    private void newGame() {
        scenario = ActivityScenario.launch(MainActivity.class);
        awaitView(withText(R.string.home_title)); clickId(R.id.primary_action);
        awaitState(state -> state.draft != null);
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.setup_players)));
    }

    private void addPlayer(String name, int expectedCount) {
        clickId(R.id.add_player);
        onView(withId(R.id.edit_value)).perform(replaceText(name), closeSoftKeyboard());
        onView(withText(R.string.save)).perform(click());
        awaitState(state -> state.draftPlayers.size() == expectedCount);
    }

    private void clickId(int id) {
        Matcher<View> visible = allOf(withId(id), isDisplayed());
        try { onView(visible).check(matches(isDisplayed())); }
        catch (androidx.test.espresso.NoMatchingViewException missing) {
            onView(withId(R.id.screen_list)).perform(RecyclerViewActions.scrollTo(hasDescendant(withId(id))));
        }
        onView(visible).perform(click());
    }

    private void awaitState(Predicate<Snapshot> expected) {
        long until = SystemClock.uptimeMillis() + 10_000;
        do {
            if (expected.test(repository.snapshot())) return;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < until);
        fail("Timed out waiting for persisted setup state");
    }

    private static void awaitView(Matcher<View> expected) {
        long until = SystemClock.uptimeMillis() + 10_000;
        AssertionError last = null;
        do {
            try { onView(expected).check(matches(isDisplayed())); return; }
            catch (androidx.test.espresso.NoMatchingViewException | AssertionError error) {
                last = new AssertionError(error); SystemClock.sleep(25);
            }
        } while (SystemClock.uptimeMillis() < until);
        throw last == null ? new AssertionError("Setup view did not appear") : last;
    }
}
