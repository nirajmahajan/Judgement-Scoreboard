package android.example.judgement.ui;

import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.data.DraftSpec;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.data.Records;
import android.example.judgement.data.ScoreDatabase;
import android.example.judgement.data.ScoreRepository;
import android.example.judgement.data.Snapshot;
import android.example.judgement.domain.Outcome;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

/** Real roster controls cover changing deal limits and participation-aware final results. */
@RunWith(AndroidJUnit4.class)
public final class RosterProgressionUiTest {
    private static final String[] NAMES = {"Alice", "Ben", "Charlie", "Dana", "Emma", "Frank", "Grace", "Harry"};
    private Context context;
    private ScoreRepository repository;
    private ActivityScenario<MainActivity> scenario;

    @Before public void prepare() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(ScoreDatabase.NAME);
        repository = new ScoreRepository(context);
    }

    @After public void close() { RepositoryTestCleanup.close(scenario, repository); }

    @Test public void ascendingAdditionConfirmsFinishAndNeverScoresTheUnfinishedDeal() {
        start(8, "ASCENDING");
        for (int i = 0; i < 5; i++) completeRound();
        Snapshot pending = repository.snapshot();
        repository.submitBid(pending.round.id, pending.roundPlayers.get(0).playerId, 0);
        Snapshot before = repository.snapshot();
        assertEquals(6, before.round.cards);
        launchGame(); openManage(); addPlayer("Ian");
        clickId(R.id.primary_action);
        await(() -> screen().equals("CONFIRM_ROSTER_DEALER"));
        assertDealerChecked(playerName(before, before.round.dealerId));
        clickId(R.id.primary_action);
        onView(withText("Finish game?")).check(matches(isDisplayed()));
        onView(withId(android.R.id.button2)).perform(click());
        assertOriginalRound(before);
        clickId(R.id.primary_action);
        onView(withText("Finish game?")).check(matches(isDisplayed()));
        onView(withId(android.R.id.button1)).perform(click());
        await(() -> screen().equals("GAME") && repository.snapshot().round == null);
        Snapshot finished = repository.snapshot();
        assertEquals("COMPLETE", finished.game.status);
        assertEquals(5, finished.history.size());
        assertEquals(9, finished.players.size());
        assertEquals("No prediction from the discarded deal earns points", before.totals.size() + 1, finished.totals.size());
        for (Records.GamePlayer player : before.players) assertEquals(before.total(player.id), finished.total(player.id));
        long added = playerId(finished, "Ian");
        assertEquals(0, finished.total(added));
        for (Snapshot.RoundHistory history : finished.history)
            for (Records.RoundPlayer player : history.players) assertNotEquals(added, player.playerId);
        onView(withId(R.id.screen_list)).perform(findAndAct(withText(R.string.game_complete), visible()));
        verifyFinalRows(finished, participation(finished, 5, added, 0), true);
    }

    @Test public void descendingOversizedDealStepsDownAfterDealerAndRestartConfirmation() {
        start(8, "DESCENDING");
        Snapshot initial = repository.snapshot();
        repository.submitBid(initial.round.id, initial.roundPlayers.get(0).playerId, 0);
        Snapshot before = repository.snapshot();
        assertEquals(6, before.round.cards);
        String removedDealer = playerName(before, before.round.dealerId);
        int oldSeat = -1;
        for (int i = 0; i < before.players.size(); i++) if (before.players.get(i).id == before.round.dealerId) oldSeat = i;
        Records.GamePlayer nextDealer = before.players.get((oldSeat + 1) % before.players.size());
        launchGame(); openManage();
        onView(withId(R.id.screen_list)).perform(findAndAct(withContentDescription("Remove " + removedDealer), click()));
        await(() -> staged().size() == 7);
        addPlayer("Ian"); addPlayer("Jill");
        clickId(R.id.primary_action);
        await(() -> screen().equals("CONFIRM_ROSTER_DEALER"));
        assertDealerChecked(nextDealer.name);
        clickId(R.id.primary_action);
        onView(withText(R.string.restart_title)).check(matches(isDisplayed()));
        onView(withText(containsString("5 cards"))).check(matches(isDisplayed()));
        onView(withId(android.R.id.button2)).perform(click());
        assertOriginalRound(before);
        clickId(R.id.primary_action);
        onView(withText(containsString("5 cards"))).check(matches(isDisplayed()));
        onView(withId(android.R.id.button1)).perform(click());
        await(() -> screen().equals("GAME") && repository.snapshot().round.id != before.round.id);
        Snapshot after = repository.snapshot();
        assertEquals(5, after.round.cards);
        assertEquals(before.round.number, after.round.number);
        assertEquals("The game's original starting maximum remains recorded", 6, after.game.maximumCards);
        assertEquals(nextDealer.id, after.round.dealerId);
        assertEquals(9, after.players.size());
        assertEquals(before.totals.get(before.round.dealerId), after.totals.get(before.round.dealerId));
        for (Records.RoundPlayer player : after.roundPlayers) { assertNull(player.bid); assertNull(player.outcome); }
        assertEquals("The removed dealer remains available for restoration", removedDealer, after.inactivePlayers.get(0).name);
        // The reduced deal continues down to one, rather than ending or resuming the old six-card deal.
        while (repository.snapshot().round != null) completeRound();
        Snapshot finished = repository.snapshot();
        assertEquals(5, finished.history.size());
        assertEquals(1, finished.history.get(finished.history.size() - 1).round.cards);
        refresh(); await(() -> uiSnapshot().round == null);
        onView(withId(R.id.screen_list)).perform(findAndAct(withText(R.string.game_complete), visible()));
    }

    @Test public void finalParticipationColumnsAppearOnlyForMissedRoundsAndPreserveReturnedIdentity() {
        start(6, "ASCENDING");
        while (repository.snapshot().round != null) completeRound();
        launchGame();
        verifyFinalRows(repository.snapshot(), participation(repository.snapshot(), 8, -1, 0), false);
        RepositoryTestCleanup.close(scenario, repository); scenario = null;
        context.deleteDatabase(ScoreDatabase.NAME); repository = new ScoreRepository(context);

        start(6, "ASCENDING"); completeRound();
        Snapshot beforeAbsence = repository.snapshot(); long ben = playerId(beforeAbsence, "Ben");
        List<PlayerSpec> remaining = roster(beforeAbsence); remaining.removeIf(player -> player.id == ben);
        repository.applyRoster(beforeAbsence.round.id, remaining, beforeAbsence.round.dealerId, true);
        completeRound();
        Snapshot returning = repository.snapshot(); List<PlayerSpec> restored = roster(returning);
        restored.add(new PlayerSpec(ben, "Ben")); restored.add(new PlayerSpec("Grace"));
        repository.applyRoster(returning.round.id, restored, returning.round.dealerId, true);
        long grace = playerId(repository.snapshot(), "Grace");
        for (int i = 0; i < 4; i++) completeRound();
        Snapshot lastDeal = repository.snapshot(); assertEquals(7, lastDeal.round.cards);
        List<PlayerSpec> finalRoster = roster(lastDeal); finalRoster.add(new PlayerSpec("Hannah"));
        repository.applyRoster(lastDeal.round.id, finalRoster, lastDeal.round.dealerId, true);
        Snapshot finished = repository.snapshot();
        assertNull(finished.round); assertEquals(6, finished.history.size());
        long hannah = playerId(finished, "Hannah");
        Map<Long, Integer> played = participation(finished, 6, ben, 5);
        played.put(grace, 4); played.put(hannah, 0);
        launchGame(); verifyFinalRows(finished, played, true);
        assertAverageRanking();
        scenario.recreate(); await(() -> screen().equals("GAME") && uiSnapshot() != null && uiSnapshot().round == null);
        verifyFinalRows(finished, played, true); assertAverageRanking();
    }
    private void assertAverageRanking() {
        String[] expected={"1 · Grace","2 · Alice","2 · Charlie","2 · Dana","2 · Emma","2 · Frank","3 · Ben","4 · Hannah"};
        onView(withId(R.id.screen_list)).perform(new ViewAction() {
            public Matcher<View> getConstraints(){return isAssignableFrom(RecyclerView.class);}
            public String getDescription(){return "verify average order and dense ranks independently of total score";}
            public void perform(UiController controller,View view){
                RecyclerView list=(RecyclerView)view;int row=0;
                for(int i=0;i<list.getAdapter().getItemCount();i++) {
                    list.scrollToPosition(i);controller.loopMainThreadUntilIdle();
                    TextView name=list.findViewHolderForAdapterPosition(i).itemView.findViewById(R.id.row_name);
                    if(name!=null)assertEquals(expected[row++],name.getText().toString());
                }
                assertEquals(expected.length,row);
            }
        });
    }

    private void verifyFinalRows(Snapshot snapshot, Map<Long, Integer> played, boolean metrics) {
        onView(withId(R.id.screen_list)).perform(new ViewAction() {
            public Matcher<View> getConstraints() { return isAssignableFrom(RecyclerView.class); }
            public String getDescription() { return "inspect every final result and its completed-round metric"; }
            public void perform(UiController controller, View view) {
                RecyclerView list = (RecyclerView) view; int rows = 0;
                for (int i = 0; i < list.getAdapter().getItemCount(); i++) {
                    list.scrollToPosition(i); controller.loopMainThreadUntilIdle();
                    RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(i);
                    assertNotNull(holder);
                    TextView name = holder.itemView.findViewById(R.id.row_name);
                    if (name == null) continue;
                    Records.GamePlayer player = null;
                    List<Records.GamePlayer> all = new ArrayList<>(snapshot.players); all.addAll(snapshot.inactivePlayers);
                    for (Records.GamePlayer candidate : all) if (name.getText().toString().endsWith(candidate.name)) { player = candidate; break; }
                    assertNotNull("Every final row belongs to one participant", player); rows++;
                    TextView rounds = holder.itemView.findViewById(R.id.row_played_rounds);
                    TextView average = holder.itemView.findViewById(R.id.row_points_per_round);
                    if (!metrics) {
                        assertTrue(rounds == null || rounds.getVisibility() != View.VISIBLE);
                        assertTrue(average == null || average.getVisibility() != View.VISIBLE);
                        continue;
                    }
                    assertNotNull(rounds); assertNotNull(average);
                    assertEquals(View.VISIBLE, rounds.getVisibility()); assertEquals(View.VISIBLE, average.getVisibility());
                    int count = played.get(player.id); assertEquals(Integer.toString(count), rounds.getText().toString());
                    if (count == 0) assertEquals("—", average.getText().toString());
                    else try {
                        double rendered = NumberFormat.getNumberInstance().parse(average.getText().toString()).doubleValue();
                        assertEquals((double) snapshot.total(player.id) / count, rendered, 0.0051);
                    } catch (ParseException failure) { throw new AssertionError("Points per round must be numeric", failure); }
                }
                assertEquals(snapshot.players.size() + snapshot.inactivePlayers.size(), rows);
            }
        });
        if (metrics) {
            onView(withId(R.id.screen_list)).perform(findAndAct(withText("Rounds"), visible()));
            onView(withId(R.id.screen_list)).perform(findAndAct(withText("Pts/round"), visible()));
        }
    }

    private void start(int count, String direction) {
        List<PlayerSpec> players = new ArrayList<>();
        for (int i = 0; i < count; i++) players.add(new PlayerSpec(NAMES[i]));
        long draft = repository.saveDraft(new DraftSpec(0, players, direction, "CUSTOM", null));
        repository.startGame(draft);
    }
    private void completeRound() {
        Snapshot state = repository.snapshot();
        for (Records.RoundPlayer player : state.roundPlayers) if (player.bid == null) repository.submitBid(state.round.id, player.playerId, 0);
        repository.recordBids(state.round.id);
        for (int i = 0; i < state.roundPlayers.size(); i++) repository.setOutcome(state.round.id,
                state.roundPlayers.get(i).playerId, i == 0 ? Outcome.LOST : Outcome.WON);
        repository.completeRound(state.round.id);
    }
    private void launchGame() {
        long gameId = repository.snapshot().game.id;
        scenario = ActivityScenario.launch(MainActivity.class);
        await(() -> uiSnapshot() != null && uiSnapshot().game != null && uiSnapshot().game.id == gameId);
        scenario.onActivity(activity -> { ScoreViewModel model = model(activity); model.tab("ROUND"); model.screen("GAME"); });
        await(() -> screen().equals("GAME"));
    }
    private void openManage() {
        Espresso.openActionBarOverflowOrOptionsMenu(context);
        onView(withText(R.string.manage_players)).perform(click());
        await(() -> screen().equals("MANAGE"));
    }
    private void addPlayer(String name) {
        int count = staged().size(); clickId(R.id.add_player);
        onView(withId(R.id.edit_value)).perform(replaceText(name), closeSoftKeyboard());
        onView(withId(android.R.id.button1)).perform(click()); await(() -> staged().size() == count + 1);
    }
    private void assertOriginalRound(Snapshot expected) {
        Snapshot actual = repository.snapshot(); assertEquals(expected.round.id, actual.round.id);
        assertEquals(expected.round.cards, actual.round.cards); assertEquals(expected.round.dealerId, actual.round.dealerId);
        assertEquals(expected.players.size(), actual.players.size()); assertEquals(expected.totals, actual.totals);
        for (int i = 0; i < expected.roundPlayers.size(); i++) assertEquals(expected.roundPlayers.get(i).bid, actual.roundPlayers.get(i).bid);
    }
    private void assertDealerChecked(String name) {
        onView(withId(R.id.screen_list)).perform(findAndAct(allOf(withId(R.id.dealer_choice_radio), withContentDescription(name)), new ViewAction() {
            public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            public String getDescription() { return "verify the proposed dealer is already checked"; }
            public void perform(UiController controller, View view) { assertTrue(isChecked().matches(view)); }
        }));
    }
    private void clickId(int id) {
        AtomicReference<Boolean> displayed = new AtomicReference<>(false);
        scenario.onActivity(activity -> { View view = activity.findViewById(id); displayed.set(view != null && view.isShown() && view.getGlobalVisibleRect(new Rect())); });
        if (displayed.get()) onView(allOf(withId(id), isDisplayed())).perform(click());
        else onView(withId(R.id.screen_list)).perform(findAndAct(withId(id), click()));
    }
    private static ViewAction visible() { return new ViewAction() {
        public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
        public String getDescription() { return "verify the displayed text"; }
        public void perform(UiController controller, View view) { assertTrue(isDisplayingAtLeast(90).matches(view)); }
    }; }
    private static ViewAction findAndAct(Matcher<View> target, ViewAction action) { return new ViewAction() {
        public Matcher<View> getConstraints() { return isAssignableFrom(RecyclerView.class); }
        public String getDescription() { return "reveal roster/result control and " + action.getDescription(); }
        public void perform(UiController controller, View view) {
            RecyclerView list = (RecyclerView) view;
            for (int i = 0; i < list.getAdapter().getItemCount(); i++) {
                list.scrollToPosition(i); controller.loopMainThreadUntilIdle();
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(i);
                View found = holder == null ? null : find(holder.itemView, target);
                if (found != null) { found.requestRectangleOnScreen(new Rect(0, 0, found.getWidth(), found.getHeight()), true);
                    controller.loopMainThreadUntilIdle(); action.perform(controller, found); controller.loopMainThreadUntilIdle(); return; }
            }
            throw new AssertionError("Missing roster/result control: " + target);
        }
    }; }
    private static View find(View view, Matcher<View> target) {
        if (target.matches(view)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = find(((ViewGroup) view).getChildAt(i), target); if (found != null) return found;
        }
        return null;
    }
    private static List<PlayerSpec> roster(Snapshot state) {
        List<PlayerSpec> players = new ArrayList<>();
        for (Records.GamePlayer player : state.players) players.add(new PlayerSpec(player.id, player.name, player.savedPlayerId));
        return players;
    }
    private static Map<Long, Integer> participation(Snapshot state, int common, long exception, int exceptional) {
        Map<Long, Integer> counts = new HashMap<>(); List<Records.GamePlayer> all = new ArrayList<>(state.players); all.addAll(state.inactivePlayers);
        for (Records.GamePlayer player : all) counts.put(player.id, player.id == exception ? exceptional : common);
        return counts;
    }
    private static long playerId(Snapshot state, String name) {
        for (Records.GamePlayer player : state.players) if (player.name.equals(name)) return player.id;
        throw new AssertionError("Missing active participant " + name);
    }
    private static String playerName(Snapshot state, long id) {
        for (Records.GamePlayer player : state.players) if (player.id == id) return player.name;
        throw new AssertionError("Missing participant " + id);
    }
    private ScoreViewModel model(MainActivity activity) { return new ViewModelProvider(activity).get(ScoreViewModel.class); }
    private <T> T read(java.util.function.Function<ScoreViewModel, T> operation) {
        AtomicReference<T> value = new AtomicReference<>(); scenario.onActivity(activity -> value.set(operation.apply(model(activity)))); return value.get();
    }
    private String screen() { return read(ScoreViewModel::screen); }
    private List<PlayerSpec> staged() { return read(ScoreViewModel::staged); }
    private Snapshot uiSnapshot() { return read(model -> model.state.getValue()); }
    private void refresh() { scenario.onActivity(activity -> model(activity).repository.execute(model(activity).repository::refresh)); }
    private interface Condition { boolean test(); }
    private static void await(Condition condition) {
        long until = SystemClock.uptimeMillis() + 10_000;
        do { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); if (condition.test()) return; SystemClock.sleep(25); }
        while (SystemClock.uptimeMillis() < until);
        assertTrue("Expected roster/result state within ten seconds", condition.test());
    }
}
