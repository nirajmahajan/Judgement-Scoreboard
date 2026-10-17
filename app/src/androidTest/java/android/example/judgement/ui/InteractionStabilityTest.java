package android.example.judgement.ui;

import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.data.DraftSpec;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.data.Records;
import android.example.judgement.data.ScoreDatabase;
import android.example.judgement.data.ScoreRepository;
import android.example.judgement.data.Snapshot;
import android.os.SystemClock;
import android.view.View;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

/** Real selection taps must not rebuild a scrolled screen or replace its action button. */
@RunWith(AndroidJUnit4.class)
public final class InteractionStabilityTest {
    private Context context;
    private ScoreRepository repository;
    private ActivityScenario<MainActivity> scenario;

    @Before public void prepare() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(ScoreDatabase.NAME);
        repository = new ScoreRepository(context);
    }

    @After public void close() { RepositoryTestCleanup.close(scenario, repository); }

    @Test public void savedCheckboxAndRowTapsKeepRowsScrollAndActionsStableThroughRecyclingAndRotation() {
        for (int i = 0; i < 16; i++) repository.addSavedPlayer(name(i));
        repository.saveDraft(new DraftSpec(0, new ArrayList<>(), "ASCENDING", "CUSTOM", null));
        launch("QUICK_ADD", SavedPlayerPickerAdapter.class);
        scrollTo(name(10));
        Stability checkboxTap = observe(name(10));
        checkbox(name(10)).perform(click()).check(matches(isChecked()));
        assertStable(checkboxTap, name(10));
        assertTrue("Selection alone does not add game participants", repository.snapshot().draftPlayers.isEmpty());

        scrollTo(name(11));
        Stability rowTap = observe(name(11));
        onView(allOf(withId(R.id.saved_player_picker_body), hasDescendant(withText(name(11)))))
                .perform(click());
        checkbox(name(11)).check(matches(isChecked()));
        assertStable(rowTap, name(11));
        Stability nameTap = observe(name(11));
        onView(allOf(withId(R.id.row_name), withText(name(11)))).perform(click());
        checkbox(name(11)).check(matches(org.hamcrest.Matchers.not(isChecked())));
        assertStable(nameTap, name(11));
        onView(allOf(withId(R.id.row_name), withText(name(11)))).perform(click());
        checkbox(name(11)).check(matches(isChecked()));
        scrollTo(name(0)); scrollTo(name(10));
        checkbox(name(10)).check(matches(isChecked()));
        scrollTo(name(11)); checkbox(name(11)).check(matches(isChecked()));
        scenario.recreate();
        awaitContent(SavedPlayerPickerAdapter.class);
        scrollTo(name(10)); checkbox(name(10)).check(matches(isChecked()));
        scrollTo(name(11)); checkbox(name(11)).check(matches(isChecked()));
        onView(withId(R.id.primary_action)).check(matches(isEnabled())).perform(click());
        await(() -> repository.snapshot().draftPlayers.size() == 2, "selected profiles added once");
        List<Records.GamePlayer> added = repository.snapshot().draftPlayers;
        assertEquals(name(10), added.get(0).name); assertEquals(name(11), added.get(1).name);
    }

    @Test public void deletingAlreadyAddedSavedProfileLeavesGameParticipantScoresAndDraftUntouched() {
        long profile = repository.addSavedPlayer("Alice"); repository.addSavedPlayer("Ben");
        List<PlayerSpec> players = new ArrayList<>();
        players.add(new PlayerSpec(0, "Alice", profile)); players.add(new PlayerSpec("Other player"));
        long active = repository.saveDraft(new DraftSpec(0, players, "ASCENDING", "CUSTOM", null));
        repository.startGame(active);
        long participant = repository.snapshot().players.get(0).id;
        repository.correctScore(participant, 0, 42);
        long round = repository.snapshot().round.id;
        repository.saveDraft(new DraftSpec(0, players, "ASCENDING", "CUSTOM", null));
        long draftPlayer = repository.snapshot().draftPlayers.get(0).id;
        launch("QUICK_ADD", SavedPlayerPickerAdapter.class);
        checkbox("Alice").check(matches(org.hamcrest.Matchers.not(isEnabled())));
        onView(allOf(withId(R.id.row_name), withText("Alice")))
                .check(matches(org.hamcrest.Matchers.not(isEnabled())));
        onView(withId(R.id.delete_saved_players)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
        onView(allOf(withId(R.id.delete_saved_player), hasSibling(withText("Alice"))))
                .check(matches(isEnabled())).perform(click());
        onView(withId(android.R.id.button2)).perform(click());
        assertEquals(2, repository.snapshot().savedPlayers.size());
        onView(allOf(withId(R.id.delete_saved_player), hasSibling(withText("Alice")))).perform(click());
        onView(withId(android.R.id.button1)).perform(click());
        await(() -> repository.snapshot().savedPlayers.size() == 1, "only the chosen saved profile removed");
        Snapshot after = repository.snapshot();
        assertEquals("Ben", after.savedPlayers.get(0).name);
        assertEquals(active, after.game.id); assertEquals(round, after.round.id);
        assertEquals(participant, after.players.get(0).id); assertEquals("Alice", after.players.get(0).name);
        assertEquals(42, after.total(participant));
        assertEquals(draftPlayer, after.draftPlayers.get(0).id); assertEquals("Alice", after.draftPlayers.get(0).name);
        assertEquals(2, after.draftPlayers.size()); assertTrue(after.history.isEmpty());
        onView(withId(R.id.primary_action)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
    }

    @Test public void resultRadioTapsKeepRowsScrollAndFinishStableAndPersistAcrossTabsAndRotation() {
        long draft = seedPlayers(); repository.startGame(draft);
        Snapshot started = repository.snapshot();
        for (Records.RoundPlayer player : started.roundPlayers)
            repository.submitBid(started.round.id, player.playerId, 0);
        repository.recordBids(started.round.id);
        launch("GAME", ResultAdapter.class);
        scrollTo(name(10));
        Stability won = observe(name(10));
        outcome(name(10), R.id.result_won).perform(click());
        awaitOutcome(name(10), "WON");
        assertStable(won, name(10));
        outcome(name(10), R.id.result_won).check(matches(isChecked()));
        Stability lost = observe(name(10));
        outcome(name(10), R.id.result_lost).perform(click());
        awaitOutcome(name(10), "LOST");
        assertStable(lost, name(10));
        outcome(name(10), R.id.result_lost).check(matches(isChecked()));
        scrollTo(name(0)); scrollTo(name(10));
        outcome(name(10), R.id.result_lost).check(matches(isChecked()));
        onView(withId(R.id.tab_scoreboard)).perform(click());
        onView(withId(R.id.tab_round)).perform(click());
        awaitContent(ResultAdapter.class); scrollTo(name(10));
        outcome(name(10), R.id.result_lost).check(matches(isChecked()));
        scenario.recreate(); awaitContent(ResultAdapter.class); scrollTo(name(10));
        outcome(name(10), R.id.result_lost).check(matches(isChecked()));
        assertEquals(started.round.id, repository.snapshot().round.id);
        assertEquals("RESULTS", repository.snapshot().round.phase);
    }

    @Test public void dealerRadioAndRowTapsKeepRowsScrollAndConfirmationStableUntilConfirmed() {
        seedPlayers(); launch("SETUP_DEALER", DealerChoiceAdapter.class);
        onView(withId(R.id.primary_action)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
        scrollTo(name(10));
        Stability radio = observe(name(10));
        dealer(name(10)).perform(click()).check(matches(isChecked()));
        assertStable(radio, name(10));
        onView(withId(R.id.primary_action)).check(matches(isEnabled()));
        assertNull("Choice stays staged until Confirm", repository.snapshot().draft.dealerId);
        scrollTo(name(11));
        Stability row = observe(name(11));
        onView(allOf(withParent(isAssignableFrom(RecyclerView.class)),
                hasDescendant(withId(R.id.dealer_choice_radio)), hasDescendant(withText(name(11)))))
                .perform(click());
        dealer(name(11)).check(matches(isChecked()));
        assertStable(row, name(11));
        scrollTo(name(0)); scrollTo(name(10));
        dealer(name(10)).check(matches(org.hamcrest.Matchers.not(isChecked())));
        scrollTo(name(11)); dealer(name(11)).check(matches(isChecked()));
        scenario.recreate(); awaitContent(DealerChoiceAdapter.class); scrollTo(name(11));
        dealer(name(11)).check(matches(isChecked()));
        onView(withId(R.id.primary_action)).check(matches(isEnabled()));
        assertNull(repository.snapshot().draft.dealerId);
        assertNull(repository.snapshot().game);
    }

    private long seedPlayers() {
        List<PlayerSpec> players = new ArrayList<>();
        for (int i = 0; i < 16; i++) players.add(new PlayerSpec(name(i)));
        return repository.saveDraft(new DraftSpec(0, players, "ASCENDING", "CUSTOM", null));
    }
    private static String name(int index) { return String.format(java.util.Locale.ROOT, "Player %02d", index); }
    private androidx.test.espresso.ViewInteraction checkbox(String name) {
        return onView(allOf(withId(R.id.saved_player_checkbox), hasSibling(withText(name))));
    }
    private androidx.test.espresso.ViewInteraction dealer(String name) {
        return onView(allOf(withId(R.id.dealer_choice_radio), hasSibling(withText(name))));
    }
    private androidx.test.espresso.ViewInteraction outcome(String name, int choice) {
        String label = context.getString(choice == R.id.result_won ? R.string.won : R.string.lost);
        return onView(allOf(withId(choice), withContentDescription(context.getString(
                R.string.result_choice_description, label, name, 0))));
    }
    private void launch(String screen, Class<?> contentType) {
        Snapshot expected = repository.snapshot();
        scenario = ActivityScenario.launch(MainActivity.class);
        await(() -> {
            AtomicReference<Snapshot> actual = new AtomicReference<>();
            scenario.onActivity(activity -> actual.set(model(activity).state.getValue()));
            Snapshot state = actual.get();
            return state != null && (expected.game == null ? state.game == null
                    : state.game != null && state.game.id == expected.game.id)
                    && (expected.draft == null ? state.draft == null
                    : state.draft != null && state.draft.id == expected.draft.id);
        }, "initial fixture loaded");
        scenario.onActivity(activity -> model(activity).screen(screen));
        awaitContent(contentType);
    }
    private void awaitContent(Class<?> type) {
        await(() -> {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.screen_list);
                RecyclerView.Adapter<?> adapter = list.getAdapter();
                ready.set(adapter instanceof ConcatAdapter
                        && ((ConcatAdapter) adapter).getAdapters().size() == 3
                        && type.isInstance(((ConcatAdapter) adapter).getAdapters().get(1)));
            });
            return ready.get();
        }, "expected selection screen");
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void scrollTo(String name) {
        onView(withId(R.id.screen_list)).perform(RecyclerViewActions.scrollTo(
                hasDescendant(allOf(withId(R.id.row_name), withText(name)))));
    }
    private Stability observe(String name) {
        AtomicReference<Stability> observed = new AtomicReference<>();
        scenario.onActivity(activity -> {
            RecyclerView list = activity.findViewById(R.id.screen_list);
            Stability stability = new Stability();
            stability.list = list; stability.outer = list.getAdapter();
            stability.content = ((ConcatAdapter) stability.outer).getAdapters().get(1);
            stability.button = activity.findViewById(R.id.primary_action);
            stability.row = visibleRow(list, name);
            LinearLayoutManager layout = (LinearLayoutManager) list.getLayoutManager();
            View first = layout.findViewByPosition(layout.findFirstVisibleItemPosition());
            assertNotNull(first);
            stability.anchor = list.getChildViewHolder(first).getItemId();
            stability.top = first.getTop();
            stability.content.registerAdapterDataObserver(stability.observer);
            observed.set(stability);
        });
        return observed.get();
    }
    private void assertStable(Stability before, String name) {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        onView(withId(R.id.screen_list)).check(matches(isDisplayed()));
        scenario.onActivity(activity -> {
            RecyclerView list = activity.findViewById(R.id.screen_list);
            assertSame("Selection keeps the same RecyclerView", before.list, list);
            assertSame("Selection does not replace the screen adapter", before.outer, list.getAdapter());
            assertSame("Selection does not replace the content adapter", before.content,
                    ((ConcatAdapter) list.getAdapter()).getAdapters().get(1));
            assertSame("Selection keeps the fixed action button", before.button,
                    activity.findViewById(R.id.primary_action));
            assertSame("Selection keeps the visible player row", before.row, visibleRow(list, name));
            LinearLayoutManager layout = (LinearLayoutManager) list.getLayoutManager();
            View first = layout.findViewByPosition(layout.findFirstVisibleItemPosition());
            assertNotNull(first);
            assertEquals("Selection keeps the first visible item", before.anchor, list.getChildViewHolder(first).getItemId());
            assertEquals("Selection keeps the scroll offset", before.top, first.getTop());
            assertEquals("Selection must not issue a full-list reset", 0, before.fullResets);
            before.content.unregisterAdapterDataObserver(before.observer);
        });
    }
    private static View visibleRow(RecyclerView list, String name) {
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            android.widget.TextView label = child.findViewById(R.id.row_name);
            if (label != null && name.contentEquals(label.getText())) return child;
        }
        throw new AssertionError("Expected visible row: " + name);
    }
    private void awaitOutcome(String name, String expected) {
        await(() -> {
            for (Records.RoundPlayer player : repository.snapshot().roundPlayers)
                if (player.nameSnapshot.equals(name)) return expected.equals(player.outcome);
            return false;
        }, "persisted result selection");
        await(() -> {
            AtomicReference<Boolean> selected = new AtomicReference<>(false);
            scenario.onActivity(activity -> {
                Snapshot state = model(activity).state.getValue();
                if (state != null) for (Records.RoundPlayer player : state.roundPlayers)
                    if (player.nameSnapshot.equals(name)) selected.set(expected.equals(player.outcome));
            });
            return selected.get();
        }, "rendered result selection");
    }
    private static ScoreViewModel model(MainActivity activity) {
        return new ViewModelProvider(activity).get(ScoreViewModel.class);
    }
    private static void await(BooleanSupplier condition, String description) {
        long until = SystemClock.uptimeMillis() + 10_000;
        do { if (condition.getAsBoolean()) return; SystemClock.sleep(25); }
        while (SystemClock.uptimeMillis() < until);
        fail("Timed out waiting for " + description);
    }
    private static final class Stability {
        RecyclerView list;
        RecyclerView.Adapter<?> outer, content;
        View row, button;
        long anchor;
        int top, fullResets;
        final RecyclerView.AdapterDataObserver observer = new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { fullResets++; }
        };
    }
}
