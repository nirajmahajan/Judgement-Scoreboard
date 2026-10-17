package android.example.judgement.ui;

import android.app.Dialog;
import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.data.DraftSpec;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.data.Records;
import android.example.judgement.data.ScoreDatabase;
import android.example.judgement.data.ScoreRepository;
import android.example.judgement.data.Snapshot;
import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;
import android.graphics.Rect;
import android.os.Looper;
import android.text.Layout;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.matcher.RootMatchers;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.takahirom.roborazzi.RoborazziOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.hamcrest.Matchers.is;

/** Pixel checks run only in the pinned Linux image; layout assertions also run with normal tests. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h740dp-notnight-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenVisualTest {
    private static final String LONG_NAME = "Alexandra Catherine Montgomery with a complete player name";
    private static final String UNBROKEN_NAME = repeated("Alexandria", 30);
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private ScoreViewModel model;
    private Context context;
    private final ExecutorService databaseWorker = Executors.newSingleThreadExecutor();

    @Before public void prepare() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(ScoreDatabase.NAME);
    }

    @After public void close() throws Exception {
        if (controller != null) {
            controller.pause().stop().destroy();
        }
        databaseWorker.shutdown();
        assertTrue(databaseWorker.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test public void homeLightOnNarrowPhone() throws Exception {
        launch(false, false, false);
        assertFixedControlsReachable();
        capture("home_light_320");
    }

    @Test @Config(qualifiers = "w320dp-h740dp-night-mdpi")
    public void homeDarkOnNarrowPhone() throws Exception {
        launch(false, false, false);
        assertFixedControlsReachable();
        capture("home_dark_320");
    }

    @Test public void setupEllipsizesLongNamesWhileRetainingFullTextAndControls() throws Exception {
        launch(true, false, false);
        open("PLAYERS", "ROUND");
        assertEachNameAndRowLayout(Arrays.asList(LONG_NAME, UNBROKEN_NAME, "محمد नीरज 🙂"));
        capture("setup_long_names_320");
    }

    @Test public void setupShowsEightPlayersTogetherWithCompactAccessibleControls() throws Exception {
        List<String> names = Arrays.asList("Alice", "Ben", "Charlie", LONG_NAME, "Elena", "Farah", "George", "Hana");
        databaseWorker.submit(() -> {
            try (ScoreRepository repository = new ScoreRepository(context)) {
                List<PlayerSpec> players = new ArrayList<>();
                for (String name : names) players.add(new PlayerSpec(name));
                repository.saveDraft(new DraftSpec(0, players, "ASCENDING", "FIXED", null));
            }
        }).get(10, TimeUnit.SECONDS);
        launch(false, false, false);
        open("PLAYERS", "ROUND");
        RecyclerView list = activity.findViewById(R.id.screen_list);
        assertEquals(8, contentAdapter(list).getItemCount());
        int rowHeight = -1;
        for (int index = 0; index < names.size(); index++) {
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(index + contentOffset(list));
            assertNotNull("All eight players fit together without scrolling", holder);
            TextView name = holder.itemView.findViewById(R.id.row_name);
            assertEquals(names.get(index), name.getText().toString());
            assertCompactRosterName(name);
            if (index == 3) assertTrue("Very long names use ellipsis within the fixed row", name.getLayout().getEllipsisCount(0) > 0);
            if (rowHeight < 0) rowHeight = holder.itemView.getHeight();
            assertEquals("Long names never increase roster row height", rowHeight, holder.itemView.getHeight());
            assertFullyVisible(name);
            assertRowLayout(holder.itemView);
            View handle = holder.itemView.findViewById(R.id.row_drag_handle);
            assertRosterTarget(handle);
            ViewGroup actions = holder.itemView.findViewById(R.id.row_actions);
            assertEquals("Rename and Remove remain available for each player", 2, actions.getChildCount());
            for (int action = 0; action < actions.getChildCount(); action++) {
                View button = actions.getChildAt(action);
                assertRosterTarget(button);
                assertEquals((action == 0 ? "Rename " : "Remove ") + names.get(index), button.getContentDescription());
            }
        }
        capture("setup_compact_320");
    }

    @Test public void setupAtTwoHundredPercentFontRetainsFullNameDataInFixedRows() throws Exception {
        RuntimeEnvironment.setFontScale(2f);
        launch(true, false, false);
        open("PLAYERS", "ROUND");
        assertEachNameAndRowLayout(Arrays.asList(LONG_NAME, UNBROKEN_NAME, "محمد नीरज 🙂"));
        assertFixedControlsReachable();
        capture("setup_font_200_320");
    }

    @Test @Config(qualifiers = "ar-rEG-ldrtl-w320dp-h740dp-notnight-mdpi")
    public void rtlRosterRetainsFullNameDataAndLabelledControls() throws Exception {
        launch(true, false, false);
        open("PLAYERS", "ROUND");
        assertEquals(View.LAYOUT_DIRECTION_RTL, activity.findViewById(R.id.score_root).getLayoutDirection());
        assertEachNameAndRowLayout(Arrays.asList(LONG_NAME, UNBROKEN_NAME, "محمد नीरज 🙂"));
        capture("setup_rtl_320");
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-notnight-mdpi")
    public void tabletScoreboardUsesSeparateRowsForTiesAndRetainsFullNameData() throws Exception {
        launch(true, true, false);
        open("GAME", "SCOREBOARD");
        assertEachNameAndRowLayout(Arrays.asList(activity.getString(R.string.ranking_format, 1, LONG_NAME),
                activity.getString(R.string.ranking_format, 1, UNBROKEN_NAME),
                activity.getString(R.string.ranking_format, 1, "محمد नीरज 🙂")), true);
        assertFixedControlsReachable();
        capture("scoreboard_tablet_800");
    }

    @Test @Config(qualifiers = "w960dp-h480dp-land-notnight-mdpi")
    public void landscapeHistoryKeepsEveryCompletedPlayerName() throws Exception {
        launch(true, true, true);
        open("GAME", "HISTORY");
        RecyclerView list = activity.findViewById(R.id.screen_list);
        assertTrue(contentAdapter(list).getItemCount() > 0);
        RecyclerView.ViewHolder row = list.findViewHolderForAdapterPosition(contentOffset(list));
        assertNotNull(row);
        TextView detail = row.itemView.findViewById(R.id.row_detail);
        assertTrue(detail.getText().toString().contains(LONG_NAME));
        assertTrue(detail.getText().toString().contains(UNBROKEN_NAME));
        assertTrue(detail.getText().toString().contains("محمد नीरज 🙂"));
        assertCompleteText(detail);
        assertFixedControlsReachable();
        capture("history_landscape_960");
    }

    @Test @Config(qualifiers = "w320dp-h740dp-night-mdpi")
    public void roundDarkShowsNamedTrumpAndDealer() throws Exception {
        List<String> names = Arrays.asList("Alice", "Ben", "Charlie", "David", "Emma");
        databaseWorker.submit(() -> {
            try (ScoreRepository repository = new ScoreRepository(context)) {
                List<PlayerSpec> players = new ArrayList<>();
                for (String name : names) players.add(new PlayerSpec(name));
                long draft = repository.saveDraft(new DraftSpec(0, players, "ASCENDING", "FIXED", null));
                repository.startGame(draft);
                Snapshot round = repository.snapshot();
                repository.submitBid(round.round.id, round.roundPlayers.get(0).playerId, 0);
                repository.submitBid(round.round.id, round.roundPlayers.get(1).playerId, 1);
            }
        }).get(10, TimeUnit.SECONDS);
        launch(false, false, false);
        open("GAME", "ROUND");
        RecyclerView list = activity.findViewById(R.id.screen_list);
        List<String> bids = Arrays.asList("0", "1", context.getString(R.string.table_bidding), "—", "—");
        for (int index = 0; index < names.size(); index++) {
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(index + contentOffset(list));
            assertNotNull("All five players stay visible while predicting", holder);
            TextView name = holder.itemView.findViewById(R.id.row_name);
            assertEquals(names.get(index), name.getText().toString());
            assertFullyVisible(name);
            assertEquals(bids.get(index), ((TextView) holder.itemView.findViewById(R.id.row_detail)).getText().toString());
        }
        assertFixedControlsReachable();
        assertEachNameAndRowLayout(names, true);
        capture("round_dark_320");
    }

    @Test public void gameAtTwoHundredPercentFontKeepsNavigationLabelsComplete() throws Exception {
        RuntimeEnvironment.setFontScale(2f);
        launch(true, true, false);
        open("GAME", "SCOREBOARD");
        assertNavigationText((ViewGroup) activity.findViewById(R.id.bottom_navigation));
        assertFixedControlsReachable();
        capture("scoreboard_navigation_font_200_320");
    }

    @Test public void savedLibraryPreservesCommonPrefixesAndUnicode() throws Exception {
        launch(true, false, false);
        open("SAVED", "ROUND");
        RecyclerView list = activity.findViewById(R.id.screen_list);
        assertEquals(4, contentAdapter(list).getItemCount());
        for (int row = 1; row < 4; row++) {
            scrollTo(row);
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(row + contentOffset(list));
            assertNotNull(holder);
            assertCompleteText(holder.itemView.findViewById(R.id.row_name));
            assertRowLayout(holder.itemView);
        }
        capture("saved_players_common_prefix_320");
    }

    @Test public void tenThousandCharacterNameRemainsCompleteInItsScrollableRow() throws Exception {
        String stressName = repeated("LongPlayer", 1_000);
        launch(false, false, false);
        RecyclerView list = activity.findViewById(R.id.screen_list);
        ScoreAdapter adapter = new ScoreAdapter();
        adapter.submit(Arrays.asList(new ScoreAdapter.Row(1, stressName, "Score: 9223372036854775807")
                .action("Correct score", () -> { })));
        list.setAdapter(adapter);
        layout();
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0);
        assertNotNull(holder);
        TextView name = holder.itemView.findViewById(R.id.row_name);
        assertEquals(stressName, name.getText().toString());
        assertCompleteText(name);
        assertRowLayout(holder.itemView);
    }

    @Test @Config(qualifiers = "w640dp-h320dp-land-notnight-mdpi")
    public void landscapeAtTwoHundredPercentFontKeepsSetupControlsReachable() throws Exception {
        RuntimeEnvironment.setFontScale(2f);
        launch(true, false, false);
        open("PLAYERS", "ROUND");
        assertFixedControlsReachable();
        capture("setup_landscape_font_200");
    }

    @Test public void keyboardSizedDialogKeepsErrorTextAndSaveReachable() throws Exception {
        launch(true, false, false);
        open("SAVED", "ROUND");
        showFooter();
        activity.findViewById(R.id.primary_action).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Dialog latest = ShadowDialog.getLatestDialog();
        assertTrue(latest instanceof AlertDialog);
        AlertDialog dialog = (AlertDialog) latest;
        TextInputEditText input = dialog.findViewById(R.id.edit_value);
        assertNotNull(input);
        input.setText("\u00a0\u2003");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue("Input validation leaves its dialog open", dialog.isShowing());
        assertEquals("\u00a0\u2003", input.getText().toString());
        View parent = (View) input.getParent();
        while (!(parent instanceof TextInputLayout)) parent = (View) parent.getParent();
        assertNotNull(((TextInputLayout) parent).getError());
        input.setText(repeated("LongPlayer", 1_000));
        View decor = dialog.getWindow().getDecorView();
        int availableHeight = dp(420); // 740dp phone with a 320dp keyboard.
        dialog.getWindow().setLayout(dp(320), availableHeight);
        decor.measure(View.MeasureSpec.makeMeasureSpec(dp(320), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(availableHeight, View.MeasureSpec.AT_MOST));
        decor.layout(0, 0, decor.getMeasuredWidth(), decor.getMeasuredHeight());
        View save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Rect saveBounds = new Rect(0, 0, save.getWidth(), save.getHeight());
        ((ViewGroup) decor).offsetDescendantRectToMyCoords(save, saveBounds);
        assertTrue("Save stays inside the keyboard-reduced dialog", saveBounds.bottom <= availableHeight);
        assertTrue(save.getHeight() >= dp(48));
        assertEquals(10_000, input.getText().length());
        input.setText("\u00a0\u2003");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        decor.measure(View.MeasureSpec.makeMeasureSpec(dp(320), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(availableHeight, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, decor.getMeasuredWidth(), decor.getMeasuredHeight());
        capture("name_dialog_keyboard_error_320", decor);
        dialog.dismiss();
    }

    @Test public void historyEditorKeepsCompleteNamesAndControlsAtLargeFonts() throws Exception {
        RuntimeEnvironment.setFontScale(2f);
        launch(true, true, true);
        Snapshot.RoundHistory history = model.state.getValue().history.get(0);
        model.inspectRound(history.round.id, "GAME");
        model.beginRoundEditor(history); layout();
        assertEachNameAndRowLayout(Arrays.asList(LONG_NAME, UNBROKEN_NAME, "محمد नीरज 🙂"));
        assertFixedControlsReachable(); capture("history_editor_font_200_320");
    }

    @Test public void finalStatisticsKeepNamesAndBothGraphsReachable() throws Exception {
        launch(true, true, false);
        databaseWorker.submit(() -> {
            while (model.repository.snapshot().round != null) {
                Snapshot state = model.repository.snapshot();
                for (Records.RoundPlayer player : state.roundPlayers)
                    model.repository.submitBid(state.round.id, player.playerId, 0);
                model.repository.recordBids(state.round.id);
                for (int i=0;i<state.roundPlayers.size();i++) model.repository.setOutcome(state.round.id,
                        state.roundPlayers.get(i).playerId, i==0 ? Outcome.LOST : Outcome.WON);
                model.repository.completeRound(state.round.id);
            }
        }).get(10, TimeUnit.SECONDS);
        open("STATISTICS", "ROUND");
        RecyclerView list = activity.findViewById(R.id.screen_list);
        int graphs = 0;
        for (int i=0;i<contentAdapter(list).getItemCount();i++) {
            scrollTo(i);
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(i+contentOffset(list));
            assertNotNull(holder);
            TextView name = holder.itemView.findViewById(R.id.row_name);
            if (name != null) { assertCompleteText(name); assertRowLayout(holder.itemView); }
            ScoreGraphView graph = graphIn(holder.itemView);
            if (graph != null) {
                graphs++; assertTrue(graph.getHeight() >= dp(280));
                assertTrue(graph.getAccessibilityNodeProvider().createAccessibilityNodeInfo(-1).getChildCount() > 0);
                if (graphs==1) capture("final_cumulative_graph_320", activity.getWindow().getDecorView());
            }
        }
        assertEquals(2, graphs); assertFixedControlsReachable(); capture("final_statistics_long_names_320");
    }

    @Test public void aggressiveChooserLightUsesTheActualThemeRoles() throws Exception {
        aggressiveChooser("aggressive_chooser_light_320");
    }

    @Test @Config(qualifiers = "w320dp-h740dp-night-mdpi")
    public void aggressiveChooserDarkUsesTheActualThemeRoles() throws Exception {
        aggressiveChooser("aggressive_chooser_dark_320");
    }

    private void aggressiveChooser(String name) throws Exception {
        launch(false, false, false);
        Context themed = new android.view.ContextThemeWrapper(activity, R.style.Theme_Score_Aggressive);
        RecyclerView chooser = new RecyclerView(themed);
        chooser.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(themed));
        chooser.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(themed,
                com.google.android.material.R.attr.colorSurface, "chooser"));
        chooser.setAdapter(new StrategyEditor(new StrategyRequest(StrategyRequest.Target.DRAFT, 1,
                "SETTINGS", ScoringStrategy.aggressive()), new StrategyEditor.Listener() {
            @Override public void onChanged(StrategyRequest request) { }
            @Override public void onHelp() { }
        }));
        activity.setContentView(chooser); layout();
        assertButtons(chooser); capture(name, activity.getWindow().getDecorView());
    }

    private static ScoreGraphView graphIn(View view) {
        if (view instanceof ScoreGraphView) return (ScoreGraphView) view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
            ScoreGraphView found=graphIn(((ViewGroup)view).getChildAt(i)); if (found!=null) return found;
        }
        return null;
    }

    private void launch(boolean draft, boolean game, boolean completedRound) throws Exception {
        if (draft || game) {
            databaseWorker.submit(() -> {
                try (ScoreRepository repository = new ScoreRepository(context)) {
                    List<PlayerSpec> players = Arrays.asList(new PlayerSpec(LONG_NAME),
                            new PlayerSpec(UNBROKEN_NAME), new PlayerSpec("محمد नीरज 🙂"));
                    long draftId = repository.saveDraft(new DraftSpec(0, players,
                            "ASCENDING", "FIXED", null));
                    repository.addSavedPlayer("Alex Smith");
                    repository.addSavedPlayer("Alex Smith Junior with a longer complete profile name");
                    repository.addSavedPlayer("محمد نीरज 🙂");
                    if (game) {
                        repository.startGame(draftId);
                        if (completedRound) {
                            Snapshot snapshot = repository.snapshot();
                            for (Records.RoundPlayer player : snapshot.roundPlayers) {
                                repository.submitBid(snapshot.round.id, player.playerId, 0);
                            }
                            repository.recordBids(snapshot.round.id);
                            boolean first = true;
                            for (Records.RoundPlayer player : snapshot.roundPlayers) {
                                repository.setOutcome(snapshot.round.id, player.playerId,
                                        first ? Outcome.LOST : Outcome.WON);
                                first = false;
                            }
                            repository.completeRound(snapshot.round.id);
                        }
                    }
                }
            }).get(10, TimeUnit.SECONDS);
        }
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        model = new ViewModelProvider(activity).get(ScoreViewModel.class);
        await(() -> model.state.getValue() != null);
        layout();
    }

    private void open(String screen, String tab) throws Exception {
        model.screen(screen);
        model.tab(tab);
        databaseWorker.submit(model.repository::refresh).get(10, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout();
    }

    private void layout() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        View decor = activity.getWindow().getDecorView();
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int height = activity.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private void scrollTo(int position) {
        RecyclerView list = activity.findViewById(R.id.screen_list);
        list.scrollToPosition(position + contentOffset(list));
        layout();
    }

    private void assertEachNameAndRowLayout(List<String> expectedNames) {
        assertEachNameAndRowLayout(expectedNames, false);
    }

    private void assertEachNameAndRowLayout(List<String> expectedNames, boolean compactGameNames) {
        RecyclerView list = activity.findViewById(R.id.screen_list);
        assertEquals(expectedNames.size(), contentAdapter(list).getItemCount());
        for (int position = 0; position < expectedNames.size(); position++) {
            scrollTo(position);
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position + contentOffset(list));
            assertNotNull("Player row " + position + " remains reachable by scrolling", holder);
            TextView name = holder.itemView.findViewById(R.id.row_name);
            assertEquals(expectedNames.get(position), name.getText().toString());
            if (holder.itemView.findViewById(R.id.row_drag_handle) != null) assertCompactRosterName(name);
            else if (compactGameNames) assertSingleLineNameData(name);
            else assertCompleteText(name);
            assertRowLayout(holder.itemView);
        }
        scrollTo(0);
    }

    private void assertRowLayout(View row) {
        TextView name = row.findViewById(R.id.row_name);
        View detail = row.findViewById(R.id.row_detail);
        View actions = row.findViewById(R.id.row_actions);
        ViewGroup content = (ViewGroup) row;
        Rect nameBounds = new Rect(); name.getDrawingRect(nameBounds);
        content.offsetDescendantRectToMyCoords(name, nameBounds);
        View handle = row.findViewById(R.id.row_drag_handle);
        Rect actionBounds = new Rect(0, 0, actions.getWidth(), actions.getHeight());
        content.offsetDescendantRectToMyCoords(actions, actionBounds);
        if (handle != null) {
            Rect handleBounds = new Rect(0, 0, handle.getWidth(), handle.getHeight());
            content.offsetDescendantRectToMyCoords(handle, handleBounds);
            assertFalse("Displayed name never overlaps its drag handle", Rect.intersects(nameBounds, handleBounds));
            assertFalse("Displayed name never overlaps Rename or Remove", Rect.intersects(nameBounds, actionBounds));
            assertEquals("Compact roster omits redundant seat text", View.GONE, detail.getVisibility());
            assertTrue("Drag handle has a 48dp touch target", handle.getWidth() >= dp(48) && handle.getHeight() >= dp(48));
            assertButtons((ViewGroup) actions);
            return;
        }
        View tableBody = row.findViewById(R.id.compact_score_body);
        if (tableBody != null) {
            assertEquals("Compact table rows have a 48dp body", dp(48), tableBody.getHeight());
            assertSingleLineNameData(name);
            if (detail.getVisibility() == View.VISIBLE) {
                Rect detailBounds = new Rect(); detail.getDrawingRect(detailBounds);
                content.offsetDescendantRectToMyCoords(detail, detailBounds);
                assertFalse("Player and bid/score columns never overlap: " + nameBounds + " / " + detailBounds, Rect.intersects(nameBounds, detailBounds));
                assertEquals("Bid/score column stays aligned across rows", dp(88), detail.getWidth());
                assertSingleLineNameData((TextView) detail);
            }
            if (actions.getHeight() > 0) {
                assertTrue("Bid/result controls stay below the table body", actionBounds.top >= tableBody.getBottom());
            }
            assertButtons((ViewGroup) actions);
            return;
        }
        if (detail.getVisibility() == View.VISIBLE) {
            Rect detailBounds = new Rect(); detail.getDrawingRect(detailBounds);
            content.offsetDescendantRectToMyCoords(detail, detailBounds);
            assertTrue("Full name never overlaps score or dealer text", nameBounds.bottom <= detailBounds.top);
            if (actions.getHeight() > 0) {
                assertTrue("Controls stay below score/dealer text", detailBounds.bottom <= actionBounds.top);
            }
        } else if (actions.getHeight() > 0) {
            assertTrue("Controls stay below the full name", nameBounds.bottom <= actionBounds.top);
        }
        assertButtons((ViewGroup) actions);
    }

    private void assertButtons(ViewGroup parent) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            if (child instanceof MaterialButton) {
                MaterialButton button = (MaterialButton) child;
                assertTrue("Every button has a 48dp touch target", button.getHeight() >= dp(48));
                assertTrue("Button label or content description is available",
                        button.getText().length() > 0 || (button.getContentDescription() != null
                                && button.getContentDescription().length() > 0));
                if (button.getText().length() > 0) assertCompleteText(button);
                if ("Rename".contentEquals(button.getText()) || "Remove".contentEquals(button.getText())) {
                    assertEquals("Single-word roster actions stay intact at large fonts", 1,
                            button.getLayout().getLineCount());
                }
                assertTrue("Button stays inside its control row", button.getRight() <= parent.getWidth());
            } else if (child instanceof ViewGroup) {
                assertButtons((ViewGroup) child);
            }
        }
    }

    private void assertRosterTarget(View control) {
        assertNotNull("Compact row retains every control", control);
        assertTrue("Compact controls retain 48dp touch targets", control.getWidth() >= dp(48) && control.getHeight() >= dp(48));
        assertTrue("Icon-only control is labelled", control.getContentDescription() != null && control.getContentDescription().length() > 0);
        assertFullyVisible(control);
    }

    private void assertCompactRosterName(TextView name) {
        assertSingleLineNameData(name);
        assertEquals("Roster row content stays 48dp for every name", dp(48), ((View) name.getParent()).getHeight());
        assertEquals("Name stays inside its fixed row", dp(48), name.getHeight());
    }

    private void assertSingleLineNameData(TextView name) {
        assertNotNull("Full player name remains available in a tooltip", name.getTooltipText());
        assertEquals("Tooltip preserves the full player name", name.getText().toString(), name.getTooltipText().toString());
        assertEquals("Compact player names use a single line", 1, name.getMaxLines());
        assertEquals("Long compact names end with ellipsis", TextUtils.TruncateAt.END, name.getEllipsize());
        assertNotNull("Compact player name has a measured layout", name.getLayout());
        assertEquals(1, name.getLayout().getLineCount());
    }

    private void assertFullyVisible(View view) {
        Rect visible = new Rect();
        assertTrue("Every compact row name and control is visible together", view.getGlobalVisibleRect(visible));
        assertTrue("Compact row content is fully visible", visible.width() >= view.getWidth() && visible.height() >= view.getHeight());
    }

    private void assertNavigationText(ViewGroup parent) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            if (child.getVisibility() != View.VISIBLE) continue;
            if (child instanceof TextView && ((TextView) child).getText().length() > 0) {
                assertCompleteText((TextView) child);
            } else if (child instanceof ViewGroup) {
                assertNavigationText((ViewGroup) child);
            }
        }
    }

    private void assertFixedControlsReachable() {
        View root = activity.findViewById(R.id.score_root);
        RecyclerView list = activity.findViewById(R.id.screen_list);
        assertTrue("Scrollable player area retains positive height", list.getHeight() > 0);
        View footer = showFooter();
        assertNotNull("All footer actions remain reachable by scrolling", footer);
        assertButtons((ViewGroup) footer);
        if (footer instanceof ViewGroup && ((ViewGroup) footer).getChildCount() > 0) {
            View lastAction = ((ViewGroup) footer).getChildAt(((ViewGroup) footer).getChildCount() - 1);
            list.scrollBy(0, footer.getHeight());
            layout();
            Rect visible = new Rect();
            assertTrue("The final action is visible after scrolling", lastAction.getGlobalVisibleRect(visible));
            assertTrue("The complete final action can be reached", visible.height() >= lastAction.getHeight());
        }
        assertTrue("Recycler viewport stays inside screen", list.getBottom() <= root.getHeight() - root.getPaddingBottom());
        list.scrollToPosition(0);
        layout();
    }

    private View showFooter() {
        RecyclerView list = activity.findViewById(R.id.screen_list);
        if (list.getAdapter() instanceof ConcatAdapter) {
            list.scrollToPosition(list.getAdapter().getItemCount() - 1);
            layout();
        }
        return activity.findViewById(R.id.footer_actions);
    }

    private static int contentOffset(RecyclerView list) {
        return list.getAdapter() instanceof ConcatAdapter ? 1 : 0;
    }

    private static RecyclerView.Adapter<?> contentAdapter(RecyclerView list) {
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        return adapter instanceof ConcatAdapter ? ((ConcatAdapter) adapter).getAdapters().get(1) : adapter;
    }

    private static void assertCompleteText(TextView view) {
        Layout text = view.getLayout();
        assertNotNull("Visible text has a measured layout", text);
        if (view.getText().length() == 0) {
            return;
        }
        assertTrue("Complete text fits inside its own view", text.getHeight()
                <= view.getHeight() - view.getCompoundPaddingTop() - view.getCompoundPaddingBottom());
        assertEquals("No suffix is hidden", view.getText().length(), text.getLineEnd(text.getLineCount() - 1));
        for (int line = 0; line < text.getLineCount(); line++) {
            assertEquals("Full names never use ellipsis", 0, text.getEllipsisCount(line));
        }
    }

    private void capture(String name) {
        RecyclerView list = activity.findViewById(R.id.screen_list);
        list.scrollToPosition(0);
        layout();
        capture(name, activity.getWindow().getDecorView());
    }

    private void capture(String name, View view) {
        RoborazziOptions options = new RoborazziOptions();
        if (!options.getTaskType().isEnabled()) {
            return;
        }
        assertEquals("Record and verify screenshot goldens in tools/ci/screenshots.sh", "Linux",
                System.getProperty("os.name"));
        File directory = new File(System.getProperty("roborazzi.output.dir", "src/test/screenshots"));
        File output = new File(directory, name + ".png").getAbsoluteFile();
        try {
            // Roborazzi's Kotlin facade also declares optional Compose overloads. Looking up
            // this exact Views signature avoids adding Compose solely for Java overload resolution.
            MethodHandle capture = MethodHandles.publicLookup().findStatic(
                    Class.forName("com.github.takahirom.roborazzi.RoborazziKt"), "captureRoboImage",
                    MethodType.methodType(void.class, ViewInteraction.class, File.class, RoborazziOptions.class));
            ViewInteraction target = Espresso.onView(is(view))
                    .inRoot(RootMatchers.withDecorView(is(view.getRootView())));
            capture.invokeExact(target, output, options);
        } catch (Throwable failure) {
            throw new AssertionError("Screenshot " + name + " failed", failure);
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static String repeated(String text, int count) {
        StringBuilder result = new StringBuilder(text.length() * count);
        for (int index = 0; index < count; index++) result.append(text);
        return result.toString();
    }

    private interface Condition { boolean test(); }

    private static void await(Condition condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (condition.test()) return;
            Thread.sleep(1);
        }
        assertTrue("Repository state arrives within ten seconds", condition.test());
    }
}
