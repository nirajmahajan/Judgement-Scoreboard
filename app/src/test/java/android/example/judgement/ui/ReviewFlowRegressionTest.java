package android.example.judgement.ui;

import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.data.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h740dp-notnight-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class ReviewFlowRegressionTest {
    Context context;
    ActivityController<MainActivity> controller;
    MainActivity activity;
    ScoreViewModel model;
    ExecutorService worker;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(ScoreDatabase.NAME);
        worker = Executors.newSingleThreadExecutor();
    }
    @After public void cleanup() throws Exception {
        if (controller != null) controller.pause().stop().destroy();
        for (android.app.Dialog dialog : org.robolectric.shadows.ShadowDialog.getShownDialogs())
            if (dialog.isShowing()) dialog.dismiss();
        worker.shutdown(); worker.awaitTermination(10, TimeUnit.SECONDS);
    }
    void launch() throws Exception {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        model = new ViewModelProvider(activity).get(ScoreViewModel.class);
        await(() -> model.state.getValue() != null);
        layout();
    }
    void layout() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(740, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 320, 740);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    void invoke(String name, String screen) throws Exception {
        Method m = MainActivity.class.getDeclaredMethod(name, String.class);
        m.setAccessible(true); m.invoke(activity, screen); layout();
    }
    void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do { Shadows.shadowOf(Looper.getMainLooper()).idle(); if (condition.getAsBoolean()) return; Thread.sleep(5); } while (System.nanoTime() < end);
        assertTrue("Awaited condition", condition.getAsBoolean());
    }
    Object field(String name) throws Exception {
        Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity);
    }
    RecyclerView.Adapter content() {
        return ((ConcatAdapter)((RecyclerView)activity.findViewById(R.id.screen_list)).getAdapter()).getAdapters().get(1);
    }
    @Test public void replayingSnapshotBeforeFormOnShowKeepsOneWindowAndPreservesEdits() throws Exception {
        launch();
        org.robolectric.shadows.ShadowDialog.reset();
        FormRequest request = new FormRequest(FormRequest.Kind.ADD_SAVED,
                "Alexandra Catherine Montgomery", false, 0, 0, 0, 0, 0);
        model.beginForm(request);
        android.app.Dialog original = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(original);
        // No looper drain here: Dialog.OnShow is still queued while the second observer runs.
        replaySnapshot(); replaySnapshot();
        assertEquals("Concurrent observers share one form window", 1, showingDialogCount());
        assertSame(original, org.robolectric.shadows.ShadowDialog.getLatestDialog());
        layout();
        EditText input = original.findViewById(R.id.edit_value);
        assertEquals(request.text, input.getText().toString());
        input.setText("Alexandra updated before saving");
        replaySnapshot(); layout();
        assertEquals(1, showingDialogCount());
        assertEquals("Alexandra updated before saving", input.getText().toString());
        assertEquals("Alexandra updated before saving", model.form().text);
        controller.pause().stop().destroy(); controller = null;
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Activity destruction dismisses every form window", 0, showingDialogCount());
    }

    @Test public void replacingFormBeforeOnShowKeepsTheNewOwnerAndClosesEveryOldWindow() throws Exception {
        launch();
        org.robolectric.shadows.ShadowDialog.reset();
        FormRequest old = new FormRequest(FormRequest.Kind.ADD_SAVED, "First request", false, 0, 0, 0, 0, 0);
        FormRequest replacement = new FormRequest(FormRequest.Kind.SEARCH, "Second request", false, 0, 0, 0, 0, 0);
        model.beginForm(old);
        android.app.Dialog previous = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        model.beginForm(replacement); replaySnapshot();
        android.app.Dialog current = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotSame(previous, current);
        assertFalse("The old form closes before its queued OnShow callback", previous.isShowing());
        assertEquals(1, showingDialogCount());
        layout();
        assertEquals(replacement.token, model.form().token);
        EditText input = current.findViewById(R.id.edit_value);
        assertEquals(replacement.text, input.getText().toString());
        input.setText("New owner keeps this query");
        replaySnapshot(); layout();
        assertEquals(replacement.token, model.form().token);
        assertEquals("New owner keeps this query", model.form().text);
        assertEquals(1, showingDialogCount());
        controller.pause().stop().destroy(); controller = null;
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, showingDialogCount());
    }

    @SuppressWarnings("unchecked")
    private void replaySnapshot() {
        ((androidx.lifecycle.MutableLiveData<Snapshot>) model.state).setValue(model.state.getValue());
    }
    private static long showingDialogCount() {
        return org.robolectric.shadows.ShadowDialog.getShownDialogs().stream()
                .filter(android.app.Dialog::isShowing).count();
    }

    @Test public void newGameCompletionNavigatesTheRecreatedActivity() throws Exception {
        launch();
        completeAfterRecreation(() -> clickFooter(R.id.primary_action));
        assertNotNull(model.state.getValue().draft);
        assertEquals("Successful New game must display setup in the live Activity", "PLAYERS", field("renderedScreen"));
    }
    @Test public void restoredPlayerIsNotOfferedAgainInTheStagedRoster() throws Exception {
        worker.submit(() -> {
            try (ScoreRepository r = new ScoreRepository(context)) {
                long id = r.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("A"), new PlayerSpec("B"), new PlayerSpec("C")), "ASCENDING", "FIXED", null));
                r.startGame(id); Snapshot s = r.snapshot();
                r.applyRoster(s.round.id, Arrays.asList(new PlayerSpec(s.players.get(0).id, "A"),new PlayerSpec(s.players.get(1).id, "B")), s.players.get(1).id, true);
            }
        }).get(10, TimeUnit.SECONDS);
        launch(); Snapshot s = model.state.getValue();
        List<PlayerSpec> staged = new ArrayList<>();
        for (Records.GamePlayer p : s.players) staged.add(new PlayerSpec(p.id, p.name));
        model.stage(staged); model.stagedDealer(s.round.dealerId); model.stagedRoundId(s.round.id);
        invoke("open", "MANAGE"); invoke("open", "RESTORE");
        RecyclerView list = activity.findViewById(R.id.screen_list);
        RecyclerView.ViewHolder row = list.findViewHolderForAdapterPosition(1);
        assertNotNull(row);
        View restore = ((LinearLayout)row.itemView.findViewById(R.id.row_actions)).getChildAt(0);
        restore.performClick(); layout();
        restore.performClick(); // A queued click from the previous row must also preserve identity.
        assertEquals(3, model.staged().size());
        invoke("open", "RESTORE");
        assertEquals("A participant already staged for restoration is excluded", 0, content().getItemCount());
        invoke("open", "MANAGE");
        Method apply = MainActivity.class.getDeclaredMethod("applyRoster"); apply.setAccessible(true); apply.invoke(activity);
        assertEquals("CONFIRM_ROSTER_DEALER", model.screen()); layout();
        clickFooter(R.id.primary_action);
        CountDownLatch drained = new CountDownLatch(1); model.repository.execute(drained::countDown);
        assertTrue(drained.await(10, TimeUnit.SECONDS)); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNull("Restoring participants must allow Apply", model.error.getValue());
        assertEquals(3, model.state.getValue().players.size());
    }

    @Test public void startingGameNavigatesTheRecreatedActivity() throws Exception {
        prepareGame(false, false);
        launch(); invoke("open", "PLAYERS");
        clickFooter(R.id.primary_action); layout();
        clickFooter(R.id.deal_order_ascending);
        await(() -> "SETUP_DEALER".equals(model.screen())); layout();
        RecyclerView list = activity.findViewById(R.id.screen_list);
        View dealer = list.findViewHolderForAdapterPosition(2).itemView.findViewById(R.id.dealer_choice_radio);
        dealer.performClick(); layout();
        model.tab("HISTORY");
        completeAfterRecreation(() -> clickFooter(R.id.primary_action));
        assertNotNull(model.state.getValue().game);
        assertEquals("GAME", field("renderedScreen"));
        assertEquals("ROUND", model.tab());
    }

    @Test public void replacementWaitsForConfirmationAndStartsAfterRecreation() throws Exception {
        prepareGame(true, false);
        worker.submit(() -> {
            try (ScoreRepository repository = new ScoreRepository(context)) {
                repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("New A"),
                        new PlayerSpec("New B")), "ASCENDING", "CUSTOM", null));
            }
        }).get(10, TimeUnit.SECONDS);
        launch(); long originalGame = model.state.getValue().game.id;
        invoke("open", "PLAYERS"); clickFooter(R.id.primary_action); layout();
        clickFooter(R.id.deal_order_descending);
        await(() -> "SETUP_DEALER".equals(model.screen())); layout();
        RecyclerView list = activity.findViewById(R.id.screen_list);
        list.findViewHolderForAdapterPosition(2).itemView.findViewById(R.id.dealer_choice_radio).performClick(); layout();
        clickFooter(R.id.primary_action);
        await(() -> "CONFIRM_START".equals(model.screen())); layout();
        assertEquals(originalGame, model.state.getValue().game.id);
        controller.recreate(); activity = controller.get(); layout();
        assertEquals("CONFIRM_START", field("renderedScreen"));
        completeAfterRecreation(() -> clickFooter(R.id.primary_action));
        assertEquals("GAME", field("renderedScreen"));
        assertNotEquals(originalGame, model.state.getValue().game.id);
        assertEquals("DESCENDING", model.state.getValue().game.direction);
    }

    @Test public void applyingRosterNavigatesTheRecreatedActivity() throws Exception {
        prepareGame(true, false);
        launch(); Snapshot snapshot = model.state.getValue();
        List<PlayerSpec> players = new ArrayList<>();
        for (Records.GamePlayer player : snapshot.players) players.add(new PlayerSpec(player.id,
                player.seat == 0 ? "Renamed player" : player.name));
        model.stage(players); model.stagedDealer(snapshot.round.dealerId); model.stagedRoundId(snapshot.round.id);
        invoke("open", "MANAGE");
        completeAfterRecreation(() -> clickFooter(R.id.primary_action));
        assertEquals("Renamed player", model.state.getValue().players.get(0).name);
        assertEquals("GAME", field("renderedScreen"));
        assertNull(model.error.getValue());
    }

    @Test public void playAgainNavigatesTheRecreatedActivityWithoutReplacingResults() throws Exception {
        prepareGame(true, true);
        launch(); long completedGame = model.state.getValue().game.id;
        invoke("open", "GAME");
        completeAfterRecreation(() -> clickFooter(R.id.play_again));
        assertEquals("PLAYERS", field("renderedScreen"));
        assertEquals(completedGame, model.state.getValue().game.id);
        assertEquals("COMPLETE", model.state.getValue().game.status);
        assertEquals(2, model.state.getValue().draftPlayers.size());
    }

    private void prepareGame(boolean start, boolean complete) throws Exception {
        worker.submit(() -> {
            try (ScoreRepository repository = new ScoreRepository(context)) {
                long draft = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("A"),
                        new PlayerSpec("B")), "ASCENDING", "FIXED", null));
                Snapshot snapshot = repository.snapshot();
                repository.chooseDraftDealer(draft, snapshot.draftPlayers.get(1).id);
                if (!start) return;
                repository.startGame(draft);
                while (complete && (snapshot = repository.snapshot()).round != null) {
                    for (Records.RoundPlayer player : snapshot.roundPlayers) repository.submitBid(snapshot.round.id, player.playerId, 0);
                    repository.recordBids(snapshot.round.id);
                    for (int index = 0; index < snapshot.roundPlayers.size(); index++) repository.setOutcome(
                            snapshot.round.id, snapshot.roundPlayers.get(index).playerId,
                            index == 0 ? android.example.judgement.domain.Outcome.LOST : android.example.judgement.domain.Outcome.WON);
                    repository.completeRound(snapshot.round.id);
                }
            }
        }).get(15, TimeUnit.SECONDS);
    }

    private void clickFooter(int id) {
        RecyclerView list = activity.findViewById(R.id.screen_list);
        list.scrollToPosition(list.getAdapter().getItemCount() - 1); layout();
        View button = activity.findViewById(id);
        assertNotNull("Requested footer action exists", button);
        assertTrue("Requested footer action is enabled", button.isEnabled());
        button.performClick();
    }

    private void completeAfterRecreation(Runnable action) throws Exception {
        CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1);
        model.repository.execute(() -> {
            blocked.countDown();
            try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new RuntimeException(exception); }
        });
        assertTrue(blocked.await(10, TimeUnit.SECONDS));
        try {
            action.run();
            controller.recreate(); activity = controller.get();
            assertSame(model, new ViewModelProvider(activity).get(ScoreViewModel.class));
        } finally { release.countDown(); }
        CountDownLatch drained = new CountDownLatch(1); model.repository.execute(drained::countDown);
        assertTrue(drained.await(10, TimeUnit.SECONDS)); layout();
    }
}
