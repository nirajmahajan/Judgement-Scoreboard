package android.example.judgement.ui;

import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.data.DraftSpec;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.data.ScoreDatabase;
import android.example.judgement.data.ScoreRepository;
import android.example.judgement.data.Snapshot;
import android.os.SystemClock;
import android.widget.CheckBox;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Platform recreation verifies saved form state and the identities used on a later submission. */
@RunWith(AndroidJUnit4.class)
public class FormLifecycleTest {
    private Context context;
    private ScoreRepository repository;
    private ActivityScenario<MainActivity> scenario;

    @Before public void prepare() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(ScoreDatabase.NAME);
        repository = new ScoreRepository(context);
    }

    @After public void close() {
        android.example.judgement.ui.RepositoryTestCleanup.close(scenario, repository);
    }

    @Test public void longNameAndUncheckedOptInSurviveActivityRecreation() {
        seed(false);
        launch("PLAYERS");
        AtomicReference<Integer> last = new AtomicReference<>();
        scenario.onActivity(activity -> last.set(((RecyclerView) activity.findViewById(R.id.screen_list))
                .getAdapter().getItemCount() - 1));
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.scrollToPosition(last.get()));
        onView(withId(R.id.add_player)).perform(click());
        onView(withText(R.string.save_future)).check(matches(not(isChecked())));
        String value = "  Alexandra नीरज with a long name and a shared prefix %_ 🙂  ";
        onView(withId(R.id.edit_value)).perform(replaceText(value));
        onView(withText(R.string.save_future)).perform(click());
        scenario.recreate();
        awaitForm();
        onView(withId(R.id.edit_value)).check(matches(withText(value)));
        onView(withText(R.string.save_future)).check(matches(isChecked()));
        onView(withText(R.string.save)).perform(click());
        await(() -> repository.snapshot().draftPlayers.size() == 3);
        Snapshot snapshot = repository.snapshot();
        assertEquals(value.trim(), snapshot.draftPlayers.get(2).name);
        assertEquals(1, snapshot.savedPlayers.size());
    }

    @Test public void invalidScoreTextAndInlineErrorSurviveActivityRecreation() {
        Snapshot snapshot = seed(true);
        launch("PICK_SCORE");
        begin(new FormRequest(FormRequest.Kind.SCORE, "0", false, snapshot.game.id,
                snapshot.players.get(0).id, 0, 0, 0));
        String invalid = "999999999999999999999999999";
        onView(withId(R.id.edit_value)).perform(replaceText(invalid));
        onView(withText(R.string.save)).perform(click());
        onView(withText(R.string.score_invalid)).check(matches(isDisplayed()));
        scenario.recreate();
        awaitForm();
        onView(withId(R.id.edit_value)).check(matches(withText(invalid)));
        onView(withText(R.string.score_invalid)).check(matches(isDisplayed()));
        assertEquals(0L, repository.snapshot().total(snapshot.players.get(0).id));
    }

    @Test public void restoredBidFormRejectsItsOriginalRoundAfterRestart() {
        Snapshot snapshot = seed(true);
        long oldRound = snapshot.round.id;
        long player = snapshot.roundPlayers.get(0).playerId;
        launch("GAME");
        begin(new FormRequest(FormRequest.Kind.BID, "0", false, snapshot.game.id,
                player, oldRound, 0, snapshot.round.cards));
        repository.restartRound(oldRound);
        long restartedRound = repository.snapshot().round.id;
        scenario.recreate();
        awaitForm();
        onView(withText(R.string.save)).perform(click());
        await(() -> currentForm() != null && currentForm().error != null);
        assertEquals(oldRound, currentForm().roundId);
        Snapshot after = repository.snapshot();
        assertEquals(restartedRound, after.round.id);
        assertEquals(null, after.roundPlayers.get(0).bid);
    }

    @Test public void restoredScoreFormRejectsItsOriginalExpectedTotalAfterAnotherEdit() {
        Snapshot snapshot = seed(true);
        long player = snapshot.players.get(0).id;
        launch("PICK_SCORE");
        begin(new FormRequest(FormRequest.Kind.SCORE, "7", false, snapshot.game.id,
                player, 0, 0, 0));
        repository.correctScore(player, 0, 8);
        scenario.recreate();
        awaitForm();
        onView(withText(R.string.save)).perform(click());
        await(() -> currentForm() != null && currentForm().error != null);
        assertEquals(0L, currentForm().expectedScore);
        assertEquals("7", currentForm().text);
        assertEquals(8L, repository.snapshot().total(player));
        assertTrue(currentForm().error.contains("score changed"));
    }

    private Snapshot seed(boolean active) {
        long draftId = repository.saveDraft(new DraftSpec(0,
                Arrays.asList(new PlayerSpec("Ada"), new PlayerSpec("Grace")),
                "ASCENDING", "FIXED", null));
        if (active) repository.startGame(draftId);
        return repository.snapshot();
    }

    private void launch(String screen) {
        scenario = ActivityScenario.launch(MainActivity.class);
        await(() -> {
            AtomicReference<Boolean> loaded = new AtomicReference<>(false);
            scenario.onActivity(activity -> loaded.set(new ViewModelProvider(activity)
                    .get(ScoreViewModel.class).state.getValue() != null));
            return loaded.get();
        });
        scenario.onActivity(activity -> {
            ScoreViewModel model = new ViewModelProvider(activity).get(ScoreViewModel.class);
            model.screen(screen);
            model.repository.execute(model.repository::refresh);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void begin(FormRequest request) {
        scenario.onActivity(activity -> new ViewModelProvider(activity).get(ScoreViewModel.class)
                .beginForm(request));
        awaitForm();
    }

    private FormRequest currentForm() {
        AtomicReference<FormRequest> form = new AtomicReference<>();
        scenario.onActivity(activity -> form.set(new ViewModelProvider(activity)
                .get(ScoreViewModel.class).form()));
        return form.get();
    }

    private void awaitForm() {
        await(() -> currentForm() != null);
        onView(withId(R.id.edit_value)).check(matches(isDisplayed()));
        assertNotNull(currentForm());
    }

    private interface Condition { boolean test(); }

    private static void await(Condition condition) {
        long deadline = SystemClock.uptimeMillis() + 8_000;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            if (condition.test()) return;
            SystemClock.sleep(16);
        }
        assertTrue("Expected state arrived within eight seconds", condition.test());
    }
}
