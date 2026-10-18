package android.example.judgement.ui;

import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.data.DraftSpec;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.data.ScoreDatabase;
import android.example.judgement.data.ScoreRepository;
import android.example.judgement.data.Snapshot;
import android.example.judgement.data.Records;
import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;
import android.os.SystemClock;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.*;

/** Predictions keep the current person in view without moving an already visible table. */
@RunWith(AndroidJUnit4.class)
public final class PredictionInteractionUiTest {
    private Context context;
    private ScoreRepository repository;
    private ActivityScenario<MainActivity> scenario;

    @Before public void prepare() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(ScoreDatabase.NAME);
        repository = new ScoreRepository(context);
    }
    @After public void close() { RepositoryTestCleanup.close(scenario, repository); }

    @Test public void dealerHintTracksEditedPredictionsAndNeverFillsTheInput() {
        start(4, 6, 6, 0); launchGame();
        assertHint(3, "Not 1");
        assertHint(2, "");
        editLatest(2, 2); // Fourteen predicted hands exceeds this thirteen-card deal.
        assertHint(3, "Any");
        editLatest(2, 1); // All thirteen hands are predicted: dealer cannot choose zero.
        assertHint(3, "Not 0");
        scenario.recreate(); awaitGame();
        assertHint(3, "Not 0");
        assertHint(2, "");
        assertNull("A hint never submits a prediction", repository.snapshot().roundPlayers.get(3).bid);
    }

    @Test public void aggressiveDealerHintShowsLegalRestrictionAndRejectsThatPrediction() {
        startWithStrategy(4, ScoringStrategy.aggressive(), 6, 6, 0); launchGame();
        assertHint(3, "Not 1");
        onView(predictionInput(3)).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
        assertFalse(inspect(activity -> activity.findViewById(R.id.quick_bid_1).isEnabled()));
        onView(predictionInput(3)).perform(click(), closeSoftKeyboard());
        assertHint(3, "Not 1");
        scenario.recreate(); awaitGame(); assertHint(3, "Not 1");
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
                new java.io.File(context.getExternalFilesDir(null), "aggressive-dealer-not-one.png")));
        editLatest(2, 2); // The other players already predict fourteen of thirteen hands.
        assertHint(3, "Any");
        for (int id : new int[]{R.id.quick_bid_0,R.id.quick_bid_1,R.id.quick_bid_2,R.id.quick_bid_3})
            assertTrue(inspect(activity -> activity.findViewById(id).isEnabled()));
        editLatest(2, 1); // Exactly thirteen hands are predicted; the dealer cannot choose zero.
        assertHint(3, "Not 0");
        assertFalse(inspect(activity -> activity.findViewById(R.id.quick_bid_0).isEnabled()));
        onView(predictionInput(3)).perform(replaceText("0"), closeSoftKeyboard());
        onView(withContentDescription(context.getString(R.string.enter_bid) + ", " + name(3))).perform(click());
        await(() -> inspect(activity -> model(activity).error.getValue() != null), "forbidden dealer prediction rejected");
        assertNull(repository.snapshot().roundPlayers.get(3).bid);
        onView(predictionInput(3)).perform(replaceText(""), closeSoftKeyboard());
        assertHint(3, "Not 0");
    }

    @Test public void twoCardDealerShowsAnyWhenOtherPredictionsAlreadyExceedTheDeal() {
        List<PlayerSpec> players = new ArrayList<>();
        for (String name : new String[]{"Rajesh","Jayshree","Aai","Niraj","Swati","Raju"}) players.add(new PlayerSpec(name));
        long draft = repository.saveDraft(new DraftSpec(0, players, "ASCENDING", "CUSTOM", null, ScoringStrategy.aggressive()));
        repository.chooseDraftDealer(draft,repository.snapshot().draftPlayers.get(4).id);
        repository.startGame(draft); completeZeroRound();
        Snapshot state = repository.snapshot();
        assertEquals(2, state.round.cards);
        assertEquals("Raju",state.roundPlayers.get(5).nameSnapshot);
        int[] predictions={0,2,1,0,1};
        for (int i=0;i<predictions.length;i++) repository.submitBid(state.round.id,state.roundPlayers.get(i).playerId,predictions[i]);
        launchGame();
        scenario.onActivity(activity -> {
            EditText dealer=input(list(activity),5);
            assertNotNull(dealer); assertEquals("Any",dealer.getHint().toString());
            assertEquals("",dealer.getText().toString());
            for(int id:new int[]{R.id.quick_bid_0,R.id.quick_bid_1,R.id.quick_bid_2}) assertTrue(activity.findViewById(id).isEnabled());
            assertFalse(activity.findViewById(R.id.quick_bid_3).isEnabled());
        });
        scenario.recreate(); awaitGame();
        scenario.onActivity(activity -> assertEquals("Any",input(list(activity),5).getHint().toString()));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
                new java.io.File(context.getExternalFilesDir(null), "dealer-any-two-cards.png")));
    }

    @Test public void advancingToAnAlreadyVisiblePredictionKeepsTheExactScrollOffset() {
        start(24); launchGame();
        await(() -> inspect(activity -> fullyVisible(list(activity), 1)), "both first players visible");
        Anchor before = inspect(activity -> anchor(list(activity)));
        onView(withId(R.id.quick_bid_0)).perform(click()); awaitBid(0, 0);
        await(() -> inspect(activity -> input(list(activity), 1) != null
                && input(list(activity), 1).getVisibility() == View.VISIBLE), "next prediction rendered");
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Anchor after = inspect(activity -> anchor(list(activity)));
        assertSame("Prediction entry keeps the existing player list", before.list, after.list);
        assertEquals("A visible next player keeps the first visible item", before.position, after.position);
        assertEquals("A visible next player must not move the viewport", before.top, after.top);
        assertTrue(inspect(activity -> fullyVisible(list(activity), 1)));
    }

    @Test public void offscreenCurrentAndNextPredictionsBecomeTheLastVisibleRow() {
        startWithZeros(24, 20); launchGame();
        awaitBottomAligned(20);
        Anchor before = inspect(activity -> anchor(list(activity)));
        assertFalse("The next player starts outside the viewport",
                inspect(activity -> fullyVisible(list(activity), 21)));
        onView(withId(R.id.quick_bid_0)).perform(click()); awaitBid(20, 0);
        awaitBottomAligned(21);
        Anchor after = inspect(activity -> anchor(list(activity)));
        assertTrue("Only the required amount of scrolling is added",
                after.position > before.position || after.top < before.top);
        assertEquals("Automatic scrolling finishes", RecyclerView.SCROLL_STATE_IDLE,
                (int) inspect(activity -> list(activity).getScrollState()));
    }

    @Test public void openingTheKeyboardKeepsTheCurrentPredictionFullyVisible() throws Exception {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String previous = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim();
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1");
        try {
            startWithZeros(24, 20); launchGame(); awaitBottomAligned(20);
            int beforeHeight = inspect(activity -> list(activity).getHeight());
            onView(predictionInput(20)).perform(click());
            scenario.onActivity(activity -> {
                EditText current = input(list(activity), 20);
                assertNotNull(current); current.requestFocus();
                ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(current, InputMethodManager.SHOW_IMPLICIT);
            });
            await(() -> inspect(activity -> {
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(activity.findViewById(R.id.score_root));
                return insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())
                        && list(activity).getHeight() < beforeHeight;
            }), "real keyboard reduces the player viewport");
            // Android can reveal the focused input before our pre-draw check. A fully
            // visible row must keep that position instead of receiving another snap.
            await(() -> inspect(activity -> fullyVisible(list(activity), 20)
                    && list(activity).getScrollState() == RecyclerView.SCROLL_STATE_IDLE),
                    "current prediction remains fully visible above the keyboard");
            assertNull("Opening the keyboard preserves the pending prediction",
                    repository.snapshot().roundPlayers.get(20).bid);
        } finally {
            device.executeShellCommand("settings " + ("null".equals(previous) ? "delete" : "put")
                    + " secure show_ime_with_hard_keyboard" + ("null".equals(previous) ? "" : " " + previous));
        }
    }

    @Test public void customTrumpBadgeKeepsTheFirstHighestPredictionAndMovesAfterAnEdit() {
        start(4, 4, 3, 4); launchGame();
        assertTrumpCaller(0);
        editLatest(2, 5); assertTrumpCaller(2);
        editLatest(2, 4); assertTrumpCaller(0);
        onView(withId(R.id.quick_bid_0)).perform(click()); awaitBid(3, 0);
        onView(withId(R.id.primary_action)).perform(click());
        await(() -> inspect(activity -> {
            RecyclerView.Adapter<?> adapter = list(activity).getAdapter();
            return adapter instanceof ConcatAdapter
                    && ((ConcatAdapter) adapter).getAdapters().get(1) instanceof ResultAdapter;
        }), "recorded results retain the custom trump caller");
        assertTrumpCaller(0);
        scenario.recreate();
        await(() -> inspect(activity -> model(activity).state.getValue() != null
                && model(activity).state.getValue().round != null
                && "RESULTS".equals(model(activity).state.getValue().round.phase)), "results restored");
        assertTrumpCaller(0);
    }

    @Test public void defaultScoringAndCyclicTrumpsShowNeitherZeroPenaltyNoteNorTrumpCaller() {
        startAscendingDeal(4, "FIXED", ScoringStrategy.defaults());
        Snapshot state = repository.snapshot();
        repository.submitBid(state.round.id, state.roundPlayers.get(0).playerId, 2);
        launchGame(); assertZeroNote(false); assertTrumpCaller(-1);
    }

    /** Leaves a compact four-card Aggressive fixture for screenshots and manual inspection. */
    @Test public void aggressiveZeroNoteStartsAtFourCardsWithCustomTrumpCallerVisible() {
        startAscendingDeal(3, "CUSTOM", ScoringStrategy.aggressive()); launchGame();
        assertZeroNote(false); assertTrumpCaller(-1);
        completeZeroRound();
        scenario.onActivity(activity -> model(activity).repository.execute(model(activity).repository::refresh));
        await(() -> inspect(activity -> {
            Snapshot state = model(activity).state.getValue();
            return state != null && state.round != null && state.round.cards == 4;
        }), "four-card deal displayed");
        awaitGame(); assertZeroNote(true);
        onView(withId(R.id.quick_bid_2)).perform(click()); awaitBid(0, 2);
        onView(withId(R.id.quick_bid_1)).perform(click()); awaitBid(1, 1);
        assertTrumpCaller(0);
        scenario.onActivity(activity -> {
            View name = row(list(activity), 0).findViewById(R.id.row_name);
            View tag = row(list(activity), 0).findViewById(R.id.round_trump_tag);
            assertTrue("Trump badge follows the player's name", tag.getLeft() >= name.getRight());
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        java.io.File screenshot = new java.io.File(context.getExternalFilesDir(null), "aggressive-four-card-trump.png");
        assertTrue("Prediction screen screenshot captured", UiDevice.getInstance(
                InstrumentationRegistry.getInstrumentation()).takeScreenshot(screenshot));
    }

    private void startAscendingDeal(int cards, String trumpMode, ScoringStrategy strategy) {
        List<PlayerSpec> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) players.add(new PlayerSpec(name(i)));
        long draft = repository.saveDraft(new DraftSpec(0, players, "ASCENDING", trumpMode, null, strategy));
        repository.startGame(draft);
        for (int current = 1; current < cards; current++) completeZeroRound();
    }
    private void completeZeroRound() {
        Snapshot state = repository.snapshot();
        for (Records.RoundPlayer player : state.roundPlayers)
            repository.submitBid(state.round.id, player.playerId, 0);
        repository.recordBids(state.round.id);
        for (int i = 0; i < state.roundPlayers.size(); i++) repository.setOutcome(state.round.id,
                state.roundPlayers.get(i).playerId, i == 0 ? Outcome.LOST : Outcome.WON);
        repository.completeRound(state.round.id);
    }
    private void assertTrumpCaller(int expectedIndex) {
        await(() -> inspect(activity -> {
            for (int i = 0; i < 4; i++) {
                View row = row(list(activity), i);
                if (row == null || (row.findViewById(R.id.round_trump_tag).getVisibility() == View.VISIBLE)
                        != (i == expectedIndex)) return false;
            }
            return true;
        }), "Trump badge belongs only to player " + expectedIndex);
    }
    private void assertZeroNote(boolean visible) {
        await(() -> inspect(activity -> {
            View note = activity.findViewById(R.id.round_scoring_note);
            return note != null && (note.getVisibility() == View.VISIBLE) == visible;
        }), visible ? "four-card successful-zero note visible" : "successful-zero note hidden");
    }

    private void start(int count, int... predictions) {
        startWithStrategy(count, ScoringStrategy.defaults(), predictions);
    }
    private void startWithStrategy(int count, ScoringStrategy strategy, int... predictions) {
        List<PlayerSpec> players = new ArrayList<>();
        for (int i = 0; i < count; i++) players.add(new PlayerSpec(name(i)));
        long draft = repository.saveDraft(new DraftSpec(0, players, "DESCENDING", "CUSTOM", null, strategy));
        repository.startGame(draft);
        Snapshot initial = repository.snapshot();
        for (int i = 0; i < predictions.length; i++)
            repository.submitBid(initial.round.id, initial.roundPlayers.get(i).playerId, predictions[i]);
    }
    private void startWithZeros(int count, int submitted) { start(count, new int[submitted]); }
    private void launchGame() {
        scenario = ActivityScenario.launch(MainActivity.class);
        long expected = repository.snapshot().game.id;
        await(() -> inspect(activity -> {
            Snapshot state = model(activity).state.getValue();
            return state != null && state.game != null && state.game.id == expected;
        }), "game fixture loaded");
        scenario.onActivity(activity -> { model(activity).tab("ROUND"); model(activity).screen("GAME"); });
        awaitGame();
    }
    private void awaitGame() {
        await(() -> inspect(activity -> {
            RecyclerView.Adapter<?> adapter = list(activity).getAdapter();
            return "GAME".equals(model(activity).screen()) && adapter instanceof ConcatAdapter
                    && ((ConcatAdapter) adapter).getAdapters().get(1) instanceof BidAdapter;
        }), "prediction table displayed");
    }
    private void editLatest(int index, int value) {
        onView(withContentDescription(context.getString(R.string.edit_bid) + ", " + name(index))).perform(click());
        onView(predictionInput(index)).perform(replaceText(Integer.toString(value)), closeSoftKeyboard());
        onView(withContentDescription(context.getString(R.string.enter_bid) + ", " + name(index))).perform(click());
        awaitBid(index, value);
    }
    private void awaitBid(int index, int value) {
        await(() -> inspect(activity -> {
            Snapshot state = model(activity).state.getValue();
            return state != null && Integer.valueOf(value).equals(state.roundPlayers.get(index).bid);
        }), "prediction persisted and rendered");
    }
    private void assertHint(int index, String expected) {
        onView(predictionInput(index)).check((view, missing) -> {
            assertNull(missing); EditText input = (EditText) view;
            assertEquals(expected, input.getHint() == null ? "" : input.getHint().toString());
            if (!expected.isEmpty()) {
                assertEquals("Forbidden prediction is a hint, never a prefilled number", "", input.getText().toString());
                assertNotEquals("Hint uses a subdued color distinct from entered numbers",
                        input.getCurrentTextColor(), input.getCurrentHintTextColor());
            }
        });
    }
    private Matcher<View> predictionInput(int index) {
        return allOf(withId(R.id.bid_input), withContentDescription(startsWith(name(index) + ", " + context.getString(R.string.table_bid))));
    }
    private void awaitBottomAligned(int index) {
        try {
            await(() -> inspect(activity -> {
                RecyclerView list = list(activity); View row = row(list, index);
                return row != null && fullyVisible(list, index)
                        && Math.abs(((LinearLayoutManager) list.getLayoutManager()).getDecoratedBottom(row) - list.getHeight()) <= 2
                        && list.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
            }), "current prediction is the last fully visible row");
        } catch (AssertionError failure) {
            String diagnostic = inspect(activity -> {
                RecyclerView list = list(activity); View row = row(list, index);
                LinearLayoutManager layout = (LinearLayoutManager) list.getLayoutManager();
                return "player=" + index + ", row=" + (row == null ? "detached"
                        : layout.getDecoratedTop(row) + ".." + layout.getDecoratedBottom(row))
                        + ", viewportHeight=" + list.getHeight() + ", fullyVisible=" + fullyVisible(list, index)
                        + ", scrollState=" + list.getScrollState() + ", padding=" + list.getPaddingTop()
                        + "/" + list.getPaddingBottom() + ", firstVisible=" + layout.findFirstVisibleItemPosition()
                        + ", inputFocused=" + (row != null && input(list, index).hasFocus());
            });
            throw new AssertionError(failure.getMessage() + "; " + diagnostic, failure);
        }
    }
    private static boolean fullyVisible(RecyclerView list, int index) {
        View row = row(list, index); if (row == null) return false;
        LinearLayoutManager layout = (LinearLayoutManager) list.getLayoutManager();
        return layout.getDecoratedTop(row) >= 0 && layout.getDecoratedBottom(row) <= list.getHeight();
    }
    private static View row(RecyclerView list, int index) {
        if (!(list.getAdapter() instanceof ConcatAdapter)) return null;
        ConcatAdapter adapter = (ConcatAdapter) list.getAdapter();
        int absolute = adapter.getAdapters().get(0).getItemCount() + index;
        return ((LinearLayoutManager) list.getLayoutManager()).findViewByPosition(absolute);
    }
    private static EditText input(RecyclerView list, int index) {
        View row = row(list, index); return row == null ? null : row.findViewById(R.id.bid_input);
    }
    private static Anchor anchor(RecyclerView list) {
        LinearLayoutManager layout = (LinearLayoutManager) list.getLayoutManager();
        int position = layout.findFirstVisibleItemPosition(); View row = layout.findViewByPosition(position);
        assertNotNull(row); return new Anchor(list, position, layout.getDecoratedTop(row));
    }
    private static RecyclerView list(MainActivity activity) { return activity.findViewById(R.id.screen_list); }
    private static ScoreViewModel model(MainActivity activity) { return new ViewModelProvider(activity).get(ScoreViewModel.class); }
    private <T> T inspect(Function<MainActivity, T> check) {
        AtomicReference<T> result = new AtomicReference<>(); scenario.onActivity(activity -> result.set(check.apply(activity))); return result.get();
    }
    private static String name(int index) { return String.format(java.util.Locale.ROOT, "Player %02d", index); }
    private static void await(BooleanSupplier condition, String description) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Timed out waiting for " + description);
    }
    private static final class Anchor {
        final RecyclerView list; final int position, top;
        Anchor(RecyclerView list, int position, int top) { this.list = list; this.position = position; this.top = top; }
    }
}
