package android.example.judgement.ui;

import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.data.DraftSpec;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.data.ScoreDatabase;
import android.example.judgement.data.ScoreRepository;
import android.example.judgement.data.Snapshot;
import android.example.judgement.domain.ScoringStrategy;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Arrays;
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
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

/** Real controls cover the reusable strategy library independently of active game snapshots. */
@RunWith(AndroidJUnit4.class)
public final class CustomStrategyUiTest {
    private Context context;
    private ScoreRepository repository;
    private ActivityScenario<MainActivity> scenario;

    @Before public void prepare() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(ScoreDatabase.NAME);
        repository = new ScoreRepository(context);
        seedDraft();
        launchPlayers();
    }

    @After public void close() { RepositoryTestCleanup.close(scenario, repository); }

    @Test public void dropdownKeepsBuiltinsImmutableAndCreateLast() {
        openDropdown();
        assertAbove(R.id.strategy_default, R.id.strategy_aggressive);
        assertAbove(R.id.strategy_aggressive, R.id.create_custom_strategy);
        onView(withContentDescription("Modify Default")).check(doesNotExist());
        onView(withContentDescription("Modify Aggressive")).check(doesNotExist());
        onView(withId(R.id.strategy_aggressive)).perform(click());
        await(() -> repository.snapshot().draft.scoringConfig.equals(ScoringStrategy.aggressive().encode()));
        openDropdown();
        onView(withId(R.id.strategy_default)).perform(click());
        await(() -> repository.snapshot().draft.scoringConfig.equals(ScoringStrategy.defaults().encode()));
        createEditor();
        onView(withId(R.id.strategy_default)).check(doesNotExist());
        onView(withId(R.id.strategy_aggressive)).check(doesNotExist());
        onView(withId(R.id.delete_strategy)).check(doesNotExist());
        onView(withId(R.id.strategy_name)).check(matches(isDisplayed()));
    }

    @Test public void emptyReservedAndDuplicateNamesKeepInputsAndNeverCreateAnotherStrategy() {
        createEditor();
        enter(R.id.strategy_zero, "7");
        for (String invalid : new String[]{"", "  ", "Default", "AGGRESSIVE"}) {
            enter(R.id.strategy_name, invalid);
            clickId(R.id.primary_action);
            awaitValidation();
            assertEquals(invalid, text(R.id.strategy_name));
            assertEquals("7", text(R.id.strategy_zero));
            assertEquals(ScoringStrategy.defaults().encode(), repository.snapshot().draft.scoringConfig);
        }
        enter(R.id.strategy_name, "Evening");
        clickId(R.id.primary_action);
        await(() -> screen().equals("PLAYERS"));
        String saved = repository.snapshot().draft.scoringConfig;
        createEditor();
        enter(R.id.strategy_name, " evening ");
        enter(R.id.strategy_zero, "9");
        clickId(R.id.primary_action);
        awaitValidation();
        assertEquals(" evening ", text(R.id.strategy_name));
        assertEquals("9", text(R.id.strategy_zero));
        assertEquals("Failed duplicate creation preserves the selected strategy", saved,
                repository.snapshot().draft.scoringConfig);
    }

    @Test public void namedCustomStrategySurvivesReopenAndSupportsModifyAndConfirmedDelete() {
        create("Evening", "7", "12", "2");
        reopen();
        openDropdown();
        onView(withContentDescription("Modify Evening")).check(matches(isDisplayed())).perform(click());
        await(() -> screen().equals("SCORING"));
        assertEquals("Evening", text(R.id.strategy_name));
        assertEquals("7", text(R.id.strategy_zero));
        assertEquals("12", text(R.id.strategy_base));
        assertEquals("2", text(R.id.strategy_per_trick));
        onView(withId(R.id.delete_strategy)).check(matches(isDisplayed()));
        enter(R.id.strategy_name, "Weekend");
        enter(R.id.strategy_zero, "8");
        clickId(R.id.primary_action);
        await(() -> screen().equals("PLAYERS"));
        reopen();
        openDropdown();
        onView(withContentDescription("Modify Evening")).check(doesNotExist());
        onView(withContentDescription("Modify Weekend")).perform(click());
        await(() -> screen().equals("SCORING"));
        assertEquals("8", text(R.id.strategy_zero));
        String selected = repository.snapshot().draft.scoringConfig;
        clickId(R.id.delete_strategy);
        onView(withText(R.string.cancel)).perform(click());
        assertEquals("Weekend", text(R.id.strategy_name));
        clickId(R.id.delete_strategy);
        onView(withId(android.R.id.button1)).perform(click());
        await(() -> screen().equals("PLAYERS"));
        assertEquals("Deleting a library item preserves the setup's copied rules", selected,
                repository.snapshot().draft.scoringConfig);
        reopen();
        openDropdown();
        onView(withContentDescription("Modify Weekend")).check(doesNotExist());
        onView(withId(R.id.create_custom_strategy)).check(matches(isDisplayed()));
    }

    @Test public void recreationRetainsUnsavedNameParametersAndValidation() {
        createEditor();
        enter(R.id.strategy_name, "Long weekend rules");
        enter(R.id.strategy_zero, "17");
        enter(R.id.strategy_base, "23");
        enter(R.id.strategy_per_trick, "999999999999999999999999999");
        clickId(R.id.primary_action);
        awaitValidation();
        scenario.recreate();
        await(() -> screen().equals("SCORING") && "Long weekend rules".equals(text(R.id.strategy_name)));
        assertEquals("17", text(R.id.strategy_zero));
        assertEquals("23", text(R.id.strategy_base));
        assertEquals("999999999999999999999999999", text(R.id.strategy_per_trick));
        assertTrue("The invalid input still has an inline explanation", hasValidation());
        onView(withId(R.id.delete_strategy)).check(doesNotExist());
        assertEquals(ScoringStrategy.defaults().encode(), repository.snapshot().draft.scoringConfig);
        enter(R.id.strategy_per_trick, "3");
        clickId(R.id.primary_action);
        await(() -> screen().equals("PLAYERS"));
        openDropdown();
        onView(withContentDescription("Modify Long weekend rules")).check(matches(isDisplayed()));
    }

    @Test public void editingAndDeletingLibraryRulesCannotRewriteAnAlreadyStartedGame() {
        create("Table rules", "7", "12", "2");
        repository.startGame(repository.snapshot().draft.id);
        Snapshot original = repository.snapshot();
        seedDraft();
        reopen();
        openDropdown();
        onView(withContentDescription("Modify Table rules")).perform(click());
        await(() -> screen().equals("SCORING"));
        enter(R.id.strategy_zero, "30");
        clickId(R.id.primary_action);
        await(() -> screen().equals("PLAYERS"));
        assertGameRulesUnchanged(original);
        openDropdown();
        onView(withContentDescription("Modify Table rules")).perform(click());
        await(() -> screen().equals("SCORING"));
        clickId(R.id.delete_strategy);
        onView(withId(android.R.id.button1)).perform(click());
        await(() -> screen().equals("PLAYERS"));
        reopen();
        assertGameRulesUnchanged(original);
        openDropdown();
        onView(withContentDescription("Modify Table rules")).check(doesNotExist());
    }

    private void assertGameRulesUnchanged(Snapshot original) {
        Snapshot actual = repository.snapshot();
        assertEquals(original.game.id, actual.game.id);
        assertEquals(original.game.scoringConfig, actual.game.scoringConfig);
        assertEquals(original.round.id, actual.round.id);
        assertEquals(original.round.scoringConfig, actual.round.scoringConfig);
        assertEquals(original.game.pendingScoringConfig, actual.game.pendingScoringConfig);
        assertEquals(original.history.size(), actual.history.size());
    }

    private void create(String name, String zero, String base, String perHand) {
        createEditor();
        enter(R.id.strategy_name, name);
        enter(R.id.strategy_zero, zero);
        enter(R.id.strategy_base, base);
        enter(R.id.strategy_per_trick, perHand);
        clickId(R.id.primary_action);
        await(() -> screen().equals("PLAYERS"));
    }

    private void createEditor() {
        openDropdown();
        onView(withId(R.id.create_custom_strategy)).perform(click());
        await(() -> screen().equals("SCORING"));
    }

    private void openDropdown() { clickId(R.id.setup_strategy_choices); }

    private void seedDraft() {
        repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("Alice"), new PlayerSpec("Ben")),
                "ASCENDING", "CUSTOM", null, ScoringStrategy.defaults()));
    }

    private void launchPlayers() {
        long draft = repository.snapshot().draft.id;
        scenario = ActivityScenario.launch(MainActivity.class);
        await(() -> {
            AtomicReference<Snapshot> state = new AtomicReference<>();
            scenario.onActivity(activity -> state.set(new ViewModelProvider(activity).get(ScoreViewModel.class).state.getValue()));
            return state.get() != null && state.get().draft != null && state.get().draft.id == draft;
        });
        scenario.onActivity(activity -> new ViewModelProvider(activity).get(ScoreViewModel.class).screen("PLAYERS"));
        await(() -> screen().equals("PLAYERS"));
    }

    private void reopen() {
        RepositoryTestCleanup.close(scenario, repository);
        scenario = null;
        repository = new ScoreRepository(context);
        launchPlayers();
    }

    private String screen() {
        AtomicReference<String> value = new AtomicReference<>();
        scenario.onActivity(activity -> value.set(new ViewModelProvider(activity).get(ScoreViewModel.class).screen()));
        return value.get();
    }

    private String text(int id) {
        AtomicReference<String> value = new AtomicReference<>("");
        scenario.onActivity(activity -> {
            TextView field = activity.findViewById(id);
            if (field != null) value.set(field.getText().toString());
        });
        return value.get();
    }

    private boolean hasValidation() {
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        scenario.onActivity(activity -> found.set(hasValidation(activity.findViewById(R.id.screen_list))));
        return found.get();
    }

    private static boolean hasValidation(View view) {
        if (view instanceof TextInputLayout) {
            CharSequence error = ((TextInputLayout) view).getError();
            if (error != null && error.length() > 0) return true;
        }
        if (view != null && view.getId() == R.id.strategy_error && view instanceof TextView
                && ((TextView) view).getText().length() > 0) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (hasValidation(((ViewGroup) view).getChildAt(i))) return true;
        return false;
    }

    private void awaitValidation() {
        await(() -> screen().equals("SCORING") && hasValidation());
    }

    private void assertAbove(int top, int bottom) {
        AtomicReference<Integer> bottomEdge = new AtomicReference<>();
        onView(withId(top)).check((view, missing) -> {
            assertNotNull(view);
            int[] position = new int[2]; view.getLocationOnScreen(position);
            bottomEdge.set(position[1] + view.getHeight());
        });
        onView(withId(bottom)).check((view, missing) -> {
            assertNotNull(view);
            int[] position = new int[2]; view.getLocationOnScreen(position);
            assertTrue("Dropdown rows retain their documented order", bottomEdge.get() <= position[1]);
        });
    }

    private void enter(int id, String value) {
        revealId(id);
        onView(withId(id)).perform(reveal(), replaceText(value), closeSoftKeyboard());
    }

    private void clickId(int id) {
        revealId(id);
        onView(allOf(withId(id), isDisplayed())).perform(reveal(), click());
    }

    private void revealId(int id) {
        AtomicReference<Boolean> visible = new AtomicReference<>(false);
        scenario.onActivity(activity -> {
            View target = activity.findViewById(id);
            visible.set(target != null && isDisplayed().matches(target));
        });
        if (visible.get()) return;
        onView(withId(R.id.screen_list)).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(RecyclerView.class); }
            @Override public String getDescription() { return "reveal strategy control " + id; }
            @Override public void perform(UiController controller, View view) {
                RecyclerView list = (RecyclerView) view;
                for (int i = 0; i < list.getAdapter().getItemCount(); i++) {
                    list.scrollToPosition(i); controller.loopMainThreadUntilIdle();
                    RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(i);
                    if (holder != null && holder.itemView.findViewById(id) != null) return;
                }
                throw new AssertionError("Missing strategy control " + id);
            }
        });
    }

    private static ViewAction reveal() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            @Override public String getDescription() { return "reveal the complete scoring input or action"; }
            @Override public void perform(UiController controller, View view) {
                view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
                controller.loopMainThreadUntilIdle();
            }
        };
    }

    private interface Condition { boolean test(); }
    private static void await(Condition condition) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            if (condition.test()) return;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        assertTrue("Expected scoring state arrived within ten seconds", condition.test());
    }
}
