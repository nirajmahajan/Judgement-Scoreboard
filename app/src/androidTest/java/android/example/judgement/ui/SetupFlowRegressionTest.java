package android.example.judgement.ui;

import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.data.ScoreDatabase;
import android.example.judgement.data.ScoreRepository;
import android.example.judgement.data.Snapshot;
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

    @Test public void playersNextRequiresDirectionThenExplicitDealerBeforeSettings() {
        newGame();
        assertEquals("CUSTOM", repository.snapshot().draft.trumpMode);
        addPlayer("Alice", 1); addPlayer("Ben", 2);
        clickId(R.id.primary_action);
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.choose_deal_order)));
        onView(withId(R.id.deal_order_descending)).perform(click());
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.choose_dealer)));
        scenario.recreate();
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.choose_dealer)));
        onView(allOf(withId(R.id.dealer_choice_radio), hasSibling(withText("Ben")))).perform(click());
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.setup_settings)));
        Snapshot state = repository.snapshot();
        assertEquals("DESCENDING", state.draft.direction);
        assertEquals(Long.valueOf(state.draftPlayers.get(1).id), state.draft.dealerId);
        assertEquals("CUSTOM", state.draft.trumpMode);
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
