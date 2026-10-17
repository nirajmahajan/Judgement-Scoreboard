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
import static org.junit.Assert.assertNull;
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
        View help = activity.findViewById(R.id.home_help);
        assertFullyVisible(help);
        assertTrue("Home Help stays compact", help.getWidth() < activity.getResources().getDisplayMetrics().widthPixels / 2);
        assertTrue("Home Help stays at the bottom", help.getBottom() >= activity.findViewById(R.id.score_root).getHeight() - dp(16));
        assertEquals(0, contentAdapter((RecyclerView) activity.findViewById(R.id.screen_list)).getItemCount());
        assertFullyVisible(activity.findViewById(R.id.home_cards));
        capture("home_light_320");
    }

    @Test @Config(qualifiers = "w320dp-h740dp-night-mdpi")
    public void homeDarkOnNarrowPhone() throws Exception {
        launch(false, false, false);
        assertFixedControlsReachable();
        capture("home_dark_320");
    }

    @Test public void homeWithUnfinishedSetupHasOneStartActionAndSmallHelp() throws Exception {
        seedEnglishSetup(false); launch(false, false, false);
        assertEquals(0, contentAdapter((RecyclerView) activity.findViewById(R.id.screen_list)).getItemCount());
        assertFullyVisible(activity.findViewById(R.id.home_help));
        assertFullyVisible(activity.findViewById(R.id.primary_action));
        capture("home_unfinished_setup_320");
        long draft = model.state.getValue().draft.id;
        showFooter(); activity.findViewById(R.id.primary_action).performClick(); layout();
        assertEquals("PLAYERS", model.screen());
        assertEquals(draft, model.state.getValue().draft.id);
        assertEquals(6, model.state.getValue().draftPlayers.size());
    }

    @Test public void resumeButtonShowsPersistedLocalLastPlayedAndReopensTheSameGame() throws Exception {
        java.util.TimeZone originalZone = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
        try {
            seedEnglishSetup(false);
            databaseWorker.submit(() -> {
                try (ScoreRepository repository = new ScoreRepository(context)) {
                    repository.startGame(repository.snapshot().draft.id);
                }
            }).get(10, TimeUnit.SECONDS);
            long gameId = databaseWorker.submit(() -> {
                try (ScoreRepository repository = new ScoreRepository(context)) { return repository.snapshot().game.id; }
            }).get(10, TimeUnit.SECONDS);
            android.content.SharedPreferences preferences = context.getSharedPreferences("game_activity", Context.MODE_PRIVATE);
            String key = "last_played_" + gameId;
            long previous = java.time.Instant.parse("2026-10-17T17:35:00Z").toEpochMilli();
            assertTrue(preferences.edit().clear().putLong(key, previous).commit());
            launch(false, false, false);
            TextView resume = activity.findViewById(R.id.resume_game);
            assertEquals("Resume game\n(Last played: 11:05 PM on 17 Oct 2026)", resume.getText().toString());
            assertFullyVisible(resume);
            capture("home_resume_last_played_320");
            controller.recreate(); activity = controller.get();
            model = new ViewModelProvider(activity).get(ScoreViewModel.class);
            await(() -> model.state.getValue() != null); layout();
            assertEquals(previous, preferences.getLong(key, 0));
            long resumedAt = org.robolectric.shadows.ShadowSystem.currentTimeMillis();
            activity.findViewById(R.id.resume_game).performClick();
            await(() -> "GAME".equals(model.screen())); layout();
            assertEquals(gameId, model.state.getValue().game.id);
            assertTrue(preferences.getLong(key, 0) >= resumedAt);
            assertTrue("Recorded time uses the app clock", preferences.getLong(key, 0)
                    <= org.robolectric.shadows.ShadowSystem.currentTimeMillis());
            assertTrue(preferences.getLong(key, 0) != previous);
            activity.getOnBackPressedDispatcher().onBackPressed(); layout();
            long playedAt = preferences.getLong(key, 0);
            assertTrue(playedAt >= resumedAt);
            controller.pause().stop().destroy(); controller = null;
            launch(false, false, false);
            assertEquals(playedAt, preferences.getLong(key, 0));
            assertEquals(gameId, model.state.getValue().game.id);
            assertTrue(((TextView) activity.findViewById(R.id.resume_game)).getText().toString().startsWith("Resume game\n(Last played:"));
        } finally { java.util.TimeZone.setDefault(originalZone); }
    }

    @Test public void launcherShowsFannedPlayingCards() throws Exception {
        launch(false, false, false);
        android.widget.FrameLayout preview = new android.widget.FrameLayout(activity);
        android.widget.ImageView icon = new android.widget.ImageView(activity);
        icon.setImageDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(activity, R.mipmap.ic_launcher));
        preview.addView(icon, new android.widget.FrameLayout.LayoutParams(dp(128), dp(128), android.view.Gravity.CENTER));
        activity.setContentView(preview); layout();
        assertTrue(icon.getDrawable() instanceof android.graphics.drawable.AdaptiveIconDrawable);
        capture("launcher_cards_128", icon);
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
        roundPreview("FIXED", false, "round_dark_320");
    }

    @Test @Config(qualifiers = "w320dp-h740dp-night-mdpi")
    public void customTrumpRoundOmitsTheTrumpLine() throws Exception {
        roundPreview("CUSTOM", false, "round_custom_320");
    }

    @Test public void aggressiveRoundShowsStrategyWithoutFlames() throws Exception {
        roundPreview("FIXED", true, "round_aggressive_320");
    }

    private void roundPreview(String trumpMode, boolean plus, String imageName) throws Exception {
        List<String> names = Arrays.asList("Alice", "Benjamin Alexander", "Charlie", "David", "Emma");
        databaseWorker.submit(() -> {
            try (ScoreRepository repository = new ScoreRepository(context)) {
                List<PlayerSpec> players = new ArrayList<>();
                for (String name : names) players.add(new PlayerSpec(name));
                long draft = repository.saveDraft(new DraftSpec(0, players, "ASCENDING", trumpMode, null, plus ? ScoringStrategy.aggressive() : ScoringStrategy.defaults()));
                repository.startGame(draft);
                Snapshot round = repository.snapshot();
                repository.submitBid(round.round.id, round.roundPlayers.get(0).playerId, 0);
                repository.submitBid(round.round.id, round.roundPlayers.get(1).playerId, 1);
            }
        }).get(10, TimeUnit.SECONDS);
        launch(false, false, false);
        open("GAME", "ROUND");
        if (plus) {
            controller.recreate(); activity = controller.get();
            model = new ViewModelProvider(activity).get(ScoreViewModel.class);
            await(() -> model.state.getValue() != null); layout();
        }
        assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_NO,
                activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
        TextView title = activity.findViewById(R.id.screen_title);
        assertNull("The round heading has no flame", title.getCompoundDrawablesRelative()[0]);
        assertEquals("Judgement", ((com.google.android.material.appbar.MaterialToolbar)
                activity.findViewById(R.id.toolbar)).getTitle().toString());
        RecyclerView list = activity.findViewById(R.id.screen_list);
        assertEquals(model.state.getValue().round.dealerId,
                model.state.getValue().roundPlayers.get(names.size() - 1).playerId);
        ViewGroup columns = activity.findViewById(R.id.table_columns);
        int headerColor = ((android.graphics.drawable.ColorDrawable) columns.getBackground()).getColor();
        for (int index = 0; index < columns.getChildCount(); index++) {
            if (!(columns.getChildAt(index) instanceof TextView)) continue;
            TextView label = (TextView) columns.getChildAt(index);
            assertNull("Table labels have no flame icons", label.getCompoundDrawablesRelative()[0]);
            assertTrue("Header labels remain readable on the dark header", androidx.core.graphics.ColorUtils.calculateContrast(
                    label.getCurrentTextColor(), headerColor) >= 4.5);
        }
        TextView description = activity.findViewById(R.id.screen_description);
        assertFalse(description.getText().toString().contains("Dealer:"));
        assertTrue(description.getText().toString().contains(context.getString(R.string.scoring_selected,
                plus ? "Aggressive" : "Default")));
        assertEquals("FIXED".equals(trumpMode), description.getText().toString().contains("Trump:"));
        assertFalse(description.getText().toString().contains("Trump chosen at table"));
        List<String> bids = Arrays.asList("0", "1", "", "—", "—");
        for (int index = 0; index < names.size(); index++) {
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(index + contentOffset(list));
            assertNotNull("All five players stay visible while predicting", holder);
            TextView dealerTag = holder.itemView.findViewById(R.id.dealer_tag);
            assertEquals(index == names.size() - 1 ? View.VISIBLE : View.GONE, dealerTag.getVisibility());
            if (index == names.size() - 1) {
                assertEquals("Dealer", dealerTag.getText().toString()); assertFullyVisible(dealerTag);
            }
            TextView name = holder.itemView.findViewById(R.id.row_name);
            assertEquals(names.get(index), name.getText().toString());
            assertFullyVisible(name);
            assertSingleLineNameData(name);
            TextView detail = holder.itemView.findViewById(R.id.row_detail);
            if (index == 2) {
                TextView input = holder.itemView.findViewById(R.id.bid_input);
                assertEquals(View.VISIBLE, input.getVisibility());
                assertNull("The column heading labels the compact numeric input", input.getHint());
                assertEquals(dp(48), input.getWidth());
                assertTrue("Two digits fit completely", input.getPaint().measureText("26")
                        <= input.getWidth() - input.getCompoundPaddingLeft() - input.getCompoundPaddingRight());
                assertFullyVisible(input);
                View confirm = holder.itemView.findViewById(R.id.bid_confirm);
                assertFullyVisible(confirm);
                assertEquals(dp(32), confirm.getWidth());
                assertNotNull(((View) confirm.getParent()).getTouchDelegate());
                Rect nameBounds = new Rect(), inputBounds = new Rect();
                name.getGlobalVisibleRect(nameBounds); input.getGlobalVisibleRect(inputBounds);
                assertFalse("The editable bid stays beside the name", Rect.intersects(nameBounds, inputBounds));
            } else {
                assertEquals(bids.get(index), detail.getText().toString());
                assertFullyVisible(detail);
            }
        }
        for (int id : new int[]{R.id.quick_bid_0, R.id.quick_bid_1, R.id.quick_bid_2, R.id.quick_bid_3})
            assertFullyVisible(activity.findViewById(id));
        assertFixedControlsReachable();
        capture(imageName);
    }

    @Test public void gameAtTwoHundredPercentFontKeepsNavigationLabelsComplete() throws Exception {
        RuntimeEnvironment.setFontScale(2f);
        launch(true, true, false);
        open("GAME", "SCOREBOARD");
        assertNavigationText((ViewGroup) activity.findViewById(R.id.bottom_navigation));
        assertFixedControlsReachable();
        capture("scoreboard_navigation_font_200_320");
    }

    @Test public void playersStrategySectionKeepsSixPlayersAndStartTogether() throws Exception {
        seedEnglishSetup(false); launch(false, false, false); open("PLAYERS", "ROUND");
        assertTrue(((android.widget.RadioButton) activity.findViewById(R.id.setup_strategy_default)).isChecked());
        assertFalse(((android.widget.RadioButton) activity.findViewById(R.id.setup_strategy_aggressive)).isChecked());
        RecyclerView list = activity.findViewById(R.id.screen_list);
        for (int index = 0; index < 6; index++) {
            View row = list.findViewHolderForAdapterPosition(contentOffset(list) + index).itemView;
            assertFullyVisible(row.findViewById(R.id.row_name));
        }
        for (int id : new int[]{R.id.setup_strategy_default, R.id.setup_strategy_aggressive}) {
            TextView choice = activity.findViewById(id);
            assertEquals("Preset names stay on one line on a narrow phone", 1, choice.getLineCount());
            assertFullyVisible(choice);
        }
        assertFullyVisible(activity.findViewById(R.id.create_custom_strategy));
        assertFullyVisible(activity.findViewById(R.id.default_strategy_info));
        assertFullyVisible(activity.findViewById(R.id.aggressive_strategy_info));
        assertFullyVisible(activity.findViewById(R.id.primary_action));
        assertEquals(activity.getString(R.string.start_game), ((TextView) activity.findViewById(R.id.primary_action)).getText().toString());
        capture("players_strategy_320");
    }

    @Test public void resultsUseOneCompactRowPerPlayerWithStackedRadioButtons() throws Exception {
        seedEnglishSetup(false);
        databaseWorker.submit(() -> {
            try (ScoreRepository repository = new ScoreRepository(context)) {
                repository.startGame(repository.snapshot().draft.id);
                Snapshot snapshot = repository.snapshot();
                for (Records.RoundPlayer player : snapshot.roundPlayers) repository.submitBid(snapshot.round.id, player.playerId, 0);
                repository.recordBids(snapshot.round.id);
                repository.setOutcome(snapshot.round.id, snapshot.roundPlayers.get(0).playerId, Outcome.LOST);
            }
        }).get(10, TimeUnit.SECONDS);
        launch(false, false, false); open("GAME", "ROUND");
        RecyclerView list = activity.findViewById(R.id.screen_list);
        assertEquals(6, contentAdapter(list).getItemCount());
        for (int index = 0; index < 6; index++) {
            View row = list.findViewHolderForAdapterPosition(contentOffset(list) + index).itemView;
            TextView name = row.findViewById(R.id.row_name);
            View won = row.findViewById(R.id.result_won), lost = row.findViewById(R.id.result_lost);
            assertSingleLineNameData(name); assertFullyVisible(name); assertFullyVisible(won); assertFullyVisible(lost);
            assertTrue("Won is above Lost", won.getBottom() <= lost.getTop());
            assertTrue("Result choices stay beside the name", row.findViewById(R.id.result_choices).getLeft() >= name.getWidth());
            assertEquals(index == 0, ((android.widget.RadioButton) lost).isChecked());
            assertFalse(((android.widget.RadioButton) won).isChecked());
            assertEquals(activity.getColor(R.color.result_won), ((TextView) won).getCurrentTextColor());
            assertEquals(activity.getColor(R.color.result_lost), ((TextView) lost).getCurrentTextColor());
            for (View choice : new View[]{won, lost}) assertTrue("Result labels retain readable contrast",
                    androidx.core.graphics.ColorUtils.calculateContrast(((TextView) choice).getCurrentTextColor(),
                            activity.getColor(R.color.score_background)) >= 4.5);
        }
        assertFullyVisible(activity.findViewById(R.id.primary_action));
        capture("results_compact_320");
    }

    @Test public void impossibleResultsExplainWhichPlayerMustBeMarkedWon() throws Exception {
        seedEnglishSetup(false);
        databaseWorker.submit(() -> {
            try (ScoreRepository repository = new ScoreRepository(context)) {
                repository.startGame(repository.snapshot().draft.id);
                Snapshot snapshot = repository.snapshot();
                for (int index = 0; index < snapshot.roundPlayers.size(); index++) repository.submitBid(
                        snapshot.round.id, snapshot.roundPlayers.get(index).playerId, index == 0 || index == 2 ? 1 : 0);
                repository.recordBids(snapshot.round.id);
                for (int index = 0; index < snapshot.roundPlayers.size(); index++) repository.setOutcome(
                        snapshot.round.id, snapshot.roundPlayers.get(index).playerId, index == 1 || index == 2 ? Outcome.LOST : Outcome.WON);
            }
        }).get(10, TimeUnit.SECONDS);
        launch(false, false, false); open("GAME", "ROUND");
        assertEquals("Deal 1 card", ((TextView) activity.findViewById(R.id.screen_title)).getText().toString());
        activity.findViewById(R.id.primary_action).performClick();
        await(() -> model.error.getValue() != null); layout();
        String error = model.error.getValue();
        assertTrue(error.startsWith("Benjamin Alexander cannot Lose\n(")); assertTrue(error.contains("Won"));
        assertTrue(error.contains("0")); assertTrue(error.contains("1")); assertFalse(error.toLowerCase(java.util.Locale.ROOT).contains("trick"));
        assertTrue(model.state.getValue().history.isEmpty());
        assertEquals("RESULTS", model.state.getValue().round.phase);
        assertFullyVisible(activity.findViewById(R.id.inline_error));
        capture("results_impossible_reason_320");
    }

    @Test public void requiredSetupPromptsKeepSixNamesTogether() throws Exception {
        seedEnglishSetup(false); launch(false, false, false); open("PLAYERS", "ROUND");
        showFooter(); activity.findViewById(R.id.primary_action).performClick(); layout();
        assertEquals("SETUP_DIRECTION", model.screen());
        assertEquals("1 card → 8 cards", ((TextView) activity.findViewById(R.id.deal_order_ascending)).getText().toString());
        assertEquals("8 cards → 1 card", ((TextView) activity.findViewById(R.id.deal_order_descending)).getText().toString());
        capture("setup_deal_order_320");
        activity.findViewById(R.id.deal_order_descending).performClick();
        await(() -> "SETUP_DEALER".equals(model.screen())); layout();
        RecyclerView list = activity.findViewById(R.id.screen_list);
        assertEquals(6, contentAdapter(list).getItemCount());
        for (int index = 0; index < 6; index++) {
            View row = list.findViewHolderForAdapterPosition(contentOffset(list) + index).itemView;
            android.widget.RadioButton choice = row.findViewById(R.id.dealer_choice_radio);
            assertFalse("A dealer must be chosen explicitly", choice.isChecked()); assertFullyVisible(choice);
        }
        View confirm = activity.findViewById(R.id.primary_action);
        assertFullyVisible(confirm); assertFalse(confirm.isEnabled());
        list.findViewHolderForAdapterPosition(contentOffset(list)).itemView.findViewById(R.id.dealer_choice_radio).performClick(); layout();
        assertNull(model.state.getValue().game); assertNull(model.state.getValue().draft.dealerId);
        controller.recreate(); activity = controller.get(); model = new ViewModelProvider(activity).get(ScoreViewModel.class);
        await(() -> model.state.getValue() != null); layout();
        list = activity.findViewById(R.id.screen_list);
        assertTrue(((android.widget.RadioButton) list.findViewHolderForAdapterPosition(contentOffset(list)).itemView
                .findViewById(R.id.dealer_choice_radio)).isChecked());
        assertTrue(activity.findViewById(R.id.primary_action).isEnabled());
        capture("setup_dealer_320");
    }

    @Test public void threeDotsShowGameActionsDirectly() throws Exception {
        seedEnglishSetup(false);
        databaseWorker.submit(() -> {
            try (ScoreRepository repository = new ScoreRepository(context)) {
                repository.startGame(repository.snapshot().draft.id);
            }
        }).get(10, TimeUnit.SECONDS);
        launch(false, false, false); open("GAME", "ROUND");
        com.google.android.material.appbar.MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
        assertNull(toolbar.getMenu().findItem(R.string.options));
        assertEquals(9, toolbar.getMenu().size());
        assertNotNull(toolbar.getMenu().findItem(R.string.manage_players));
        assertNotNull(toolbar.getMenu().findItem(R.string.change_dealer));
        assertTrue(toolbar.showOverflowMenu());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(toolbar.isOverflowMenuShowing());
        android.widget.PopupWindow popup = Shadows.shadowOf((android.app.Application) context).getLatestPopupWindow();
        assertNotNull(popup);
        View popupRoot = popup.getContentView().getRootView();
        popupRoot.measure(View.MeasureSpec.makeMeasureSpec(popup.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(640), View.MeasureSpec.AT_MOST));
        popupRoot.layout(0, 0, popupRoot.getMeasuredWidth(), popupRoot.getMeasuredHeight());
        capture("game_actions_direct_320", popupRoot);
        toolbar.dismissPopupMenus();
        toolbar.getMenu().performIdentifierAction(R.string.manage_players, 0); layout();
        assertEquals("MANAGE", model.screen());
    }

    @Test public void reorderedPlayersReconfirmTheExistingDealer() throws Exception {
        seedEnglishSetup(false);
        databaseWorker.submit(() -> {
            try (ScoreRepository repository = new ScoreRepository(context)) {
                repository.startGame(repository.snapshot().draft.id);
            }
        }).get(10, TimeUnit.SECONDS);
        launch(false, false, false);
        Snapshot snapshot = model.state.getValue();
        List<PlayerSpec> players = new ArrayList<>();
        for (Records.GamePlayer player : snapshot.players)
            players.add(new PlayerSpec(player.id, player.name, player.savedPlayerId));
        model.stage(players); model.stagedDealer(snapshot.round.dealerId); model.stagedRoundId(snapshot.round.id);
        java.util.Collections.rotate(players, 1); model.stage(players);
        open("MANAGE", "ROUND"); showFooter();
        activity.findViewById(R.id.primary_action).performClick(); layout();
        assertEquals("CONFIRM_ROSTER_DEALER", model.screen());
        controller.recreate(); activity = controller.get();
        model = new ViewModelProvider(activity).get(ScoreViewModel.class);
        await(() -> model.state.getValue() != null); layout();
        RecyclerView list = activity.findViewById(R.id.screen_list);
        for (int index = 0; index < players.size(); index++) {
            View row = list.findViewHolderForAdapterPosition(index + contentOffset(list)).itemView;
            android.widget.RadioButton choice = row.findViewById(R.id.dealer_choice_radio);
            assertEquals(players.get(index).id == snapshot.round.dealerId, choice.isChecked());
        }
        View confirm = activity.findViewById(R.id.primary_action);
        assertTrue(confirm.isEnabled()); assertFullyVisible(confirm);
        assertEquals(snapshot.round.id, model.state.getValue().round.id);
        capture("manage_reconfirm_dealer_320");
    }

    @Test public void savedPlayersShowOnlyCompactCheckboxRowsAndBottomAddAction() throws Exception {
        seedEnglishSetup(true); launch(false, false, false); open("SAVED", "ROUND");
        RecyclerView list = activity.findViewById(R.id.screen_list);
        for (int index : new int[]{0, 2}) {
            View row = list.findViewHolderForAdapterPosition(contentOffset(list) + index).itemView;
            row.findViewById(R.id.saved_player_checkbox).performClick(); layout();
        }
        assertFullyVisible(activity.findViewById(R.id.delete_saved_players));
        assertTrue(activity.findViewById(R.id.delete_saved_players).isEnabled());
        View add = activity.findViewById(R.id.primary_action);
        assertTrue(add.isEnabled()); assertFullyVisible(add);
        assertEquals(6, contentAdapter(list).getItemCount());
        View plus = activity.findViewById(R.id.add_saved_player);
        assertFullyVisible(plus);
        assertEquals(activity.getString(R.string.add_saved), plus.getContentDescription());
        assertTrue("Plus stays above the selection action", plus.getBottom() <= list.getHeight());
        assertEquals(0, model.state.getValue().draftPlayers.size());
        capture("saved_players_picker_320");
    }

    private void seedEnglishSetup(boolean profiles) throws Exception {
        databaseWorker.submit(() -> {
            try (ScoreRepository repository = new ScoreRepository(context)) {
                List<PlayerSpec> players = new ArrayList<>();
                for (String name : Arrays.asList("Alice", "Benjamin Alexander", "Charlie", "David", "Emma", "Frank")) {
                    if (profiles) repository.addSavedPlayer(name); else players.add(new PlayerSpec(name));
                }
                repository.saveDraft(new DraftSpec(0, players, "ASCENDING", "CUSTOM", null));
            }
        }).get(10, TimeUnit.SECONDS);
    }

    @Test public void savedLibraryPreservesCommonPrefixesAndUnicode() throws Exception {
        launch(true, false, false);
        open("SAVED", "ROUND");
        RecyclerView list = activity.findViewById(R.id.screen_list);
        assertEquals(3, contentAdapter(list).getItemCount());
        for (int row = 0; row < 3; row++) {
            scrollTo(row);
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(row + contentOffset(list));
            assertNotNull(holder);
            assertSingleLineNameData(holder.itemView.findViewById(R.id.row_name));
            assertFullyVisible(holder.itemView.findViewById(R.id.saved_player_checkbox));
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
        open("SAVED_MANAGE", "ROUND");
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

    @Test public void finalTableShowsPlayerLegendsAndOneCumulativeGraph() throws Exception {
        seedEnglishSetup(false);
        databaseWorker.submit(() -> {
            try (ScoreRepository repository = new ScoreRepository(context)) {
                repository.startGame(repository.snapshot().draft.id);
                while (repository.snapshot().round != null) {
                    Snapshot snapshot = repository.snapshot();
                    int[] actual = new int[snapshot.roundPlayers.size()];
                    for (int hand = 0; hand < snapshot.round.cards; hand++)
                        actual[(hand + snapshot.round.number) % actual.length]++;
                    int missed = 0;
                    while (actual[missed] == 0) missed++;
                    for (int index = 0; index < actual.length; index++) repository.submitBid(
                            snapshot.round.id, snapshot.roundPlayers.get(index).playerId,
                            actual[index] - (index == missed ? 1 : 0));
                    repository.recordBids(snapshot.round.id);
                    for (int index = 0; index < actual.length; index++) repository.setOutcome(
                            snapshot.round.id, snapshot.roundPlayers.get(index).playerId,
                            index == missed ? Outcome.LOST : Outcome.WON);
                    if (snapshot.round.cards == 8) break;
                    repository.completeRound(snapshot.round.id);
                }
            }
        }).get(10, TimeUnit.SECONDS);
        launch(false, false, false); open("GAME", "ROUND");
        assertEquals("Deal 8 cards", ((TextView) activity.findViewById(R.id.screen_title)).getText().toString());
        activity.findViewById(R.id.primary_action).performClick();
        await(() -> "COMPLETE".equals(model.state.getValue().game.status)); layout();
        assertNull(model.state.getValue().round);
        assertEquals(8, model.state.getValue().history.size());
        assertEquals("Game complete", ((TextView) activity.findViewById(R.id.screen_title)).getText().toString());
        assertNull(model.error.getValue());
        View replay = activity.findViewById(R.id.play_again);
        assertFullyVisible(replay);
        com.google.android.material.appbar.MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
        assertNotNull(toolbar.getMenu().findItem(R.id.play_again));
        android.text.Spanned replayLabel = (android.text.Spanned) toolbar.getMenu().findItem(R.id.play_again).getTitle();
        android.text.style.ForegroundColorSpan replayColor = replayLabel.getSpans(0, replayLabel.length(),
                android.text.style.ForegroundColorSpan.class)[0];
        assertTrue("Top-right action remains readable on the dark header",
                androidx.core.graphics.ColorUtils.calculateContrast(replayColor.getForegroundColor(),
                        activity.getColor(R.color.score_table_header)) >= 4.5);
        RecyclerView list = activity.findViewById(R.id.screen_list);
        ConcatAdapter finalContent = (ConcatAdapter) contentAdapter(list);
        RecyclerView.Adapter<?> table = finalContent.getAdapters().get(0);
        assertEquals(6, table.getItemCount());
        assertEquals(7, finalContent.getItemCount());
        for (int index = 0; index < table.getItemCount(); index++) {
            scrollTo(index);
            TextView name = list.findViewHolderForAdapterPosition(index + contentOffset(list)).itemView.findViewById(R.id.row_name);
            android.graphics.drawable.GradientDrawable marker = (android.graphics.drawable.GradientDrawable) name.getCompoundDrawablesRelative()[0];
            assertNotNull(marker);
            assertEquals(ScoreGraphView.colorForPlayer(table.getItemId(index)), marker.getColor().getDefaultColor());
        }
        capture("final_table_cumulative_320");
        scrollTo(6);
        RecyclerView.ViewHolder graphHolder = list.findViewHolderForAdapterPosition(6 + contentOffset(list));
        ScoreGraphView graph = graphIn(graphHolder.itemView);
        assertNotNull(graph);
        assertEquals(R.id.final_score_graph, graph.getId());
        assertTrue(graph.getAccessibilityNodeProvider().createAccessibilityNodeInfo(-1).getChildCount() > 0);
        ((androidx.recyclerview.widget.LinearLayoutManager) list.getLayoutManager())
                .scrollToPositionWithOffset(6 + contentOffset(list), 0); layout();
        capture("final_cumulative_plot_320", activity.getWindow().getDecorView());
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
