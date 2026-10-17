package android.example.judgement;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.example.judgement.data.*;
import android.example.judgement.domain.Outcome;
import android.example.judgement.ui.MainActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CriticalUiRegressionTest {
    private Context context;
    private ActivityScenario<MainActivity> scenario;
    private ScoreRepository inspector;
    @Before public void clear() {
        context = ApplicationProvider.getApplicationContext(); context.deleteDatabase(ScoreDatabase.NAME);
    }
    @After public void close() {
        android.example.judgement.ui.RepositoryTestCleanup.close(scenario, inspector);
    }

    @Test public void newGameFormPreservesInvalidInputAndAcceptsFullUnicodeLongName() {
        launch();
        clickFooter(R.id.primary_action);
        awaitView(withText(R.string.setup_players));
        clickFooter(R.id.add_player);
        onView(withId(R.id.edit_value)).perform(replaceText("   "), closeSoftKeyboard());
        onView(withText(R.string.save)).perform(click());
        onView(withId(R.id.edit_value)).check(matches(allOf(isDisplayed(), withText("   "))));
        String full = "Élodie %_ " + repeat("a very long complete player name ", 12) + " العربية";
        onView(withId(R.id.edit_value)).perform(replaceText(full), closeSoftKeyboard());
        onView(withText(R.string.save)).perform(click());
        awaitState(s -> s.draftPlayers.size() == 1);
        assertEquals(full, inspector.snapshot().draftPlayers.get(0).name);
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.scrollTo(hasDescendant(withText(full))));
        onView(withText(full)).check(matches(isDisplayed()));
    }

    @Test public void quickBidsFollowCurrentPlayerAndSelectionsSurviveScoreboardReturn() {
        seed(3, false, 0); launchGame();
        for (int player = 0; player < 3; player++) {
            rowAction(player, "0"); final int filled = player + 1;
            awaitState(s -> s.roundPlayers.stream().filter(row -> row.bid != null).count() == filled);
        }
        clickFooter(R.id.primary_action);
        awaitState(s -> "RESULTS".equals(s.round.phase));
        rowAction(0, "Lost"); awaitState(s -> "LOST".equals(s.roundPlayers.get(0).outcome));
        onView(withId(R.id.tab_scoreboard)).perform(click());
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.scrollToPosition(0));
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.scoreboard_tab)));
        onView(withId(R.id.tab_round)).perform(click());
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.scrollToPosition(1));
        awaitView(withText("0 · Lost"));
        assertEquals("LOST", inspector.snapshot().roundPlayers.get(0).outcome);
        scenario.recreate();
        awaitView(withText("0 · Lost"));
    }

    @Test public void scrollingEightPlayerResultsFinishFromBottomWithoutVisibleFirstRow() {
        seed(8, true, 0); launchGame();
        for (int player = 0; player < 8; player++) {
            rowAction(player, player == 0 ? "Lost" : "Won"); final int index = player;
            awaitState(s -> s.roundPlayers.get(index).outcome != null);
        }
        clickFooter(R.id.primary_action);
        awaitState(s -> s.history.size() == 1);
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.scrollToPosition(0));
        awaitView(withText("Round 2 · 2 cards"));
        assertEquals(10, inspector.snapshot().total(inspector.snapshot().players.get(7).id));
    }

    @Test public void blankAndOverflowScoreErrorsKeepCorrectionFormOpen() {
        seed(2, false, 0); launchGame();
        androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu(context);
        onView(withText(R.string.options)).perform(click());
        onView(withText(R.string.correct_score)).perform(click());
        rowAction(0, context.getString(R.string.correct_score));
        onView(withId(R.id.edit_value)).perform(replaceText(""), closeSoftKeyboard());
        onView(withText(R.string.save)).perform(click());
        onView(withId(R.id.edit_value)).check(matches(isDisplayed()));
        onView(withId(R.id.edit_value)).perform(replaceText("9223372036854775808"), closeSoftKeyboard());
        onView(withText(R.string.save)).perform(click());
        onView(withId(R.id.edit_value)).check(matches(allOf(isDisplayed(), withText("9223372036854775808"))));
        for (long total : inspector.snapshot().totals.values()) assertEquals(0, total);
    }

    @Test public void finishingFinalHandDisplaysCompletedGameAndHistory() {
        seed(2, true, 25); launchGame();
        rowAction(0, "Lost"); awaitState(s -> s.roundPlayers.get(0).outcome != null);
        rowAction(1, "Won"); awaitState(s -> s.roundPlayers.get(1).outcome != null);
        clickFooter(R.id.primary_action);
        awaitState(s -> "COMPLETE".equals(s.game.status));
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.scrollToPosition(0));
        awaitView(withText(R.string.game_complete));
        assertEquals("COMPLETE", inspector.snapshot().game.status);
        assertEquals(26, inspector.snapshot().history.size());
        onView(withId(R.id.tab_history)).perform(click());
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.scrollToPosition(0));
        awaitView(allOf(withId(R.id.screen_title), withText(R.string.history_tab)));
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.scrollToPosition(26));
        onView(withText("Round 26 · 26 cards")).check(matches(isDisplayed()));
    }

    private void launch() {
        inspector = new ScoreRepository(context);
        scenario = ActivityScenario.launch(MainActivity.class);
        awaitView(withText(R.string.home_title));
    }
    private void launchGame() {
        launch();
        onView(allOf(withText(R.string.resume_game), isClickable())).perform(click());
        awaitView(withId(R.id.bottom_navigation));
    }
    private void seed(int count, boolean results, int completed) {
        ScoreRepository repository = new ScoreRepository(context);
        List<PlayerSpec> players = new ArrayList<>();
        for (int i = 0; i < count; i++) players.add(new PlayerSpec("UI player " + i));
        long draft = repository.saveDraft(new DraftSpec(0, players, "ASCENDING", "FIXED", null)); repository.startGame(draft);
        for (int round = 0; round < completed; round++) {
            Snapshot state = repository.snapshot();
            for (Records.RoundPlayer row : state.roundPlayers) repository.submitBid(state.round.id, row.playerId, 0);
            repository.recordBids(state.round.id);
            for (int i = 0; i < state.roundPlayers.size(); i++) repository.setOutcome(state.round.id, state.roundPlayers.get(i).playerId, i == 0 ? Outcome.LOST : Outcome.WON);
            repository.completeRound(state.round.id);
        }
        if (results) {
            Snapshot state = repository.snapshot();
            for (Records.RoundPlayer row : state.roundPlayers) repository.submitBid(state.round.id, row.playerId, 0);
            repository.recordBids(state.round.id);
        }
        android.example.judgement.ui.RepositoryTestCleanup.close(null, repository);
    }
    private void awaitState(Predicate<Snapshot> expected) {
        long until = SystemClock.uptimeMillis() + 10_000;
        do { if (expected.test(inspector.snapshot())) return; SystemClock.sleep(30); } while (SystemClock.uptimeMillis() < until);
        fail("Timed out waiting for persisted UI command");
    }
    private static void awaitView(Matcher<View> matcher) {
        long until = SystemClock.uptimeMillis() + 10_000;
        AssertionError last = null;
        do {
            try { onView(matcher).check(matches(isDisplayed())); return; }
            catch (androidx.test.espresso.NoMatchingViewException | AssertionError error) { last = new AssertionError(error); SystemClock.sleep(30); }
        } while (SystemClock.uptimeMillis() < until);
        throw last == null ? new AssertionError("Missing view") : last;
    }
    private static void clickFooter(int id) {
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.scrollTo(hasDescendant(withId(id))));
        onView(withId(id)).perform(click());
    }
    private static void rowAction(int position, String label) {
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.actionOnItemAtPosition(position + 1, new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            @Override public String getDescription() { return "click row action " + label; }
            @Override public void perform(UiController controller, View row) {
                View target = findTextButton(row, label);
                if (target == null) throw new AssertionError("Missing action " + label);
                click().perform(controller, target);
            }
        }));
    }
    private static View findTextButton(View view, String label) {
        if (view instanceof TextView && view.isClickable() && label.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = findTextButton(((ViewGroup) view).getChildAt(i), label); if (found != null) return found;
        }
        return null;
    }
    private static String repeat(String text, int count) { StringBuilder full = new StringBuilder(); for (int i = 0; i < count; i++) full.append(text); return full.toString(); }
}
