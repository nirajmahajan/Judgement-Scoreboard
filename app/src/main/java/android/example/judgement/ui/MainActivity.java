package android.example.judgement.ui;

import android.example.judgement.R;
import android.example.judgement.data.DraftSpec;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.data.Records;
import android.example.judgement.data.Snapshot;
import android.example.judgement.data.GameStatistics;
import android.example.judgement.data.RoundEditPreview;
import android.example.judgement.domain.GameRules;
import android.example.judgement.domain.Names;
import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ConcatAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Screens render immutable repository snapshots; UI callbacks always carry record IDs. */
public final class MainActivity extends AppCompatActivity {
    private ScoreViewModel model;
    private Snapshot state;
    private MaterialToolbar toolbar;
    private RecyclerView list;
    private BottomNavigationView navigation;
    private ScoreAdapter adapter;
    private BidAdapter bids;
    private ResultAdapter outcomes;
    private LinearLayout fixedActions;
    private View addSavedPlayer, homeHelp;
    private boolean biddingColumns;
    private boolean predictionRevealPending;
    private long autoScrollingPrediction = -1;
    private RosterAdapter roster;
    private StrategyEditor strategyEditor;
    private RoundEditorAdapter roundEditor;
    private ConcatAdapter finalResultsContent;
    private ChromeAdapter finalGraphAdapter;
    private boolean aggressiveAppearance, changingAppearance;
    private ChromeAdapter headerAdapter, footerAdapter;
    private RecyclerView.Adapter<?> contentAdapter;
    private String headingText = "", descriptionText = "", errorText;
    private int tableDetailLabel;
    private boolean rosterColumns, participationColumns, stackedParticipationColumns;
    private final List<FooterAction> footerActions = new ArrayList<>();
    private String renderedScreen = "";
    private String pickerReturn = "SETTINGS";
    private String savedManageReturn = "SAVED";
    private TextInputLayout inputError;
    private AlertDialog inputDialog;
    private String activeFormToken;
    private boolean destroyingForms;
    private final Set<Long> selectedProfiles = new HashSet<>();
    private String search = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        aggressiveAppearance = savedInstanceState != null && savedInstanceState.getBoolean("aggressiveAppearance");
        setTheme(aggressiveAppearance ? R.style.Theme_Score_Aggressive : R.style.Theme_Score);
        getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            pickerReturn = savedInstanceState.getString("pickerReturn", "SETTINGS");
            savedManageReturn = savedInstanceState.getString("savedManageReturn", "SAVED");
            search = savedInstanceState.getString("search", "");
            long[] selected = savedInstanceState.getLongArray("selectedProfiles");
            if (selected != null) for (long id : selected) selectedProfiles.add(id);
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean light = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(light);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        setContentView(R.layout.score_activity);
        View root = findViewById(R.id.score_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, keyboard.bottom));
            View statusBackground = view.findViewById(R.id.status_bar_background);
            if (statusBackground.getLayoutParams().height != bars.top) {
                android.view.ViewGroup.LayoutParams layout = statusBackground.getLayoutParams();
                layout.height = bars.top; statusBackground.setLayoutParams(layout);
            }
            return insets;
        });
        list = findViewById(R.id.screen_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top != oldBottom - oldTop) requestPredictionVisibility();
        });
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(RecyclerView view, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) autoScrollingPrediction = -1;
            }
        });
        headingText = getString(R.string.loading);
        headerAdapter = new ChromeAdapter(R.layout.score_header, this::bindHeader);
        footerAdapter = new ChromeAdapter(R.layout.score_footer, this::bindFooter);
        toolbar = findViewById(R.id.toolbar);
        android.graphics.drawable.Drawable overflow = toolbar.getOverflowIcon();
        if (overflow != null) {
            overflow = overflow.mutate(); overflow.setTint(getColor(R.color.score_table_on_header));
            toolbar.setOverflowIcon(overflow);
        }
        navigation = findViewById(R.id.bottom_navigation);
        // The root owns system-bar and keyboard space. Material's legacy inset listener
        // otherwise adds the keyboard height to the navigation bar a second time.
        ViewCompat.setOnApplyWindowInsetsListener(navigation, (view, insets) -> insets);
        navigation.setPaddingRelative(0, 0, 0, 0);
        fixedActions = findViewById(R.id.fixed_actions);
        adapter = new ScoreAdapter(); setContent(adapter);
        model = new ViewModelProvider(this).get(ScoreViewModel.class);
        homeHelp = findViewById(R.id.home_help);
        homeHelp.setOnClickListener(view -> open("HELP"));
        addSavedPlayer = findViewById(R.id.add_saved_player);
        addSavedPlayer.setOnClickListener(view -> model.beginForm(new FormRequest(
                FormRequest.Kind.ADD_SAVED, "", false, 0, 0, 0, 0, 0)));
        navigation.setOnItemSelectedListener(item -> {
            String next = item.getItemId() == R.id.tab_history ? "HISTORY" : item.getItemId() == R.id.tab_scoreboard ? "SCOREBOARD" : "ROUND";
            if (!next.equals(model.tab())) { model.tab(next); render(); }
            return true;
        });
        toolbar.setNavigationOnClickListener(v -> back());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        model.bidSubmitted.observe(this, roundId -> { if (state != null) render(); });
        model.state.observe(this, snapshot -> {
            Snapshot previous = state;
            state = snapshot;
            if (canUpdateResultSelections(previous))
                outcomes.submit(state.round.id, state.round.dealerId, state.roundPlayers);
            else render();
            restoreForm(model.form());
        });
        model.screenState().observe(this, screen -> { if (state != null) render(); });
        model.strategyState().observe(this, request -> { if (state != null) render(); });
        model.roundEditorState().observe(this, request -> {
            if (state != null) {
                render();
                RoundEditRequest current = model.roundEditor();
                if (current != null && current.error != null) {
                    list.smoothScrollToPosition(0); list.announceForAccessibility(current.error);
                }
            }
        });
        model.roundPreview.observe(this, preview -> { if (state != null) render(); });
        model.formState().observe(this, bundle -> {
            if (state != null) restoreForm(FormRequest.fromBundle(bundle));
        });
        model.error.observe(this, message -> {
            errorText = message; headerAdapter.notifyItemChanged(0);
            if (message != null && inputError == null) { list.smoothScrollToPosition(0); list.announceForAccessibility(message); }
        });
        model.repository.failures().observe(this, message -> { if (message != null) model.error.setValue(message); });
    }
    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("aggressiveAppearance", aggressiveAppearance);
        outState.putString("savedManageReturn", savedManageReturn); outState.putString("pickerReturn", pickerReturn); outState.putString("search", search);
        long[] selected = new long[selectedProfiles.size()]; int i = 0; for (long id : selectedProfiles) selected[i++] = id;
        outState.putLongArray("selectedProfiles", selected);
        super.onSaveInstanceState(outState);
    }
    @Override protected void onDestroy() {
        destroyingForms = true;
        if (inputDialog != null) inputDialog.dismiss();
        if (roster != null) roster.detach();
        super.onDestroy();
    }

    @Override protected void onPause() {
        rememberLastPlayed();
        super.onPause();
    }
    private void rememberLastPlayed() {
        if (state == null || state.game == null || !"ACTIVE".equals(state.game.status)
                || !"GAME".equals(model.screen())) return;
        getSharedPreferences("game_activity", MODE_PRIVATE).edit()
                .putLong("last_played_" + state.game.id, System.currentTimeMillis()).apply();
    }
    private void open(String screen) { rememberLastPlayed(); model.screen(screen); }
    private void back() {
        String screen = model.screen();
        if ("SCORING".equals(screen)) {
            StrategyRequest request = model.strategyRequest();
            if (request != null) model.finishStrategy(request.token); else open("HOME");
        } else if ("ROUND_EDIT".equals(screen)) {
            confirm(R.string.discard_title, R.string.discard_message, R.string.discard, model::finishRoundEditor);
        } else if ("ROUND_DETAILS".equals(screen)) open(model.historyReturn());
        else if ("SCORING_HELP".equals(screen)) open(model.scoringHelpReturn());
        else if ("MANAGE".equals(screen)) {
            if (hasRosterChanges()) confirm(R.string.discard_title, R.string.discard_message,
                    R.string.discard, () -> open("GAME"));
            else open("GAME");
        } else if ("SETTINGS".equals(screen) || "SETUP_DIRECTION".equals(screen)) open("PLAYERS");
        else if ("SETUP_DEALER".equals(screen)) open("SETUP_DIRECTION");
        else if ("CONFIRM_START".equals(screen)) open("PLAYERS");
        else if ("SAVED_MANAGE".equals(screen)) open(savedManageReturn);
        else if ("PICK_DEALER".equals(screen) || "CONFIRM_ROSTER_DEALER".equals(screen)) open(pickerReturn);
        else if ("QUICK_ADD".equals(screen)) open(pickerReturn);
        else if ("RESTORE".equals(screen)) open("MANAGE");
        else if ("PICK_SCORE".equals(screen)) open("GAME");
        else if ("HOME".equals(screen)) finish();
        else open("HOME");
    }
    /** Persisted radio changes update their rows without rebuilding the screen chrome. */
    private boolean canUpdateResultSelections(Snapshot previous) {
        if (previous == null || state == null || outcomes == null || contentAdapter != outcomes
                || !"GAME".equals(model.screen()) || !"ROUND".equals(model.tab())
                || previous.game == null || state.game == null || previous.game.id != state.game.id
                || !"ACTIVE".equals(state.game.status) || previous.round == null || state.round == null)
            return false;
        Records.Round old = previous.round, current = state.round;
        return "RESULTS".equals(old.phase) && "RESULTS".equals(current.phase)
                && old.id == current.id && old.number == current.number && old.cards == current.cards
                && old.dealerId == current.dealerId && old.scoringConfig.equals(current.scoringConfig)
                && previous.game.trumpMode.equals(state.game.trumpMode);
    }
    private void render() {
        if (state == null || changingAppearance) return;
        if ("STATISTICS".equals(model.screen())) model.screen("GAME");
        rememberLastPlayed();
        if (updateAppearance()) return;
        String screen = model.screen();
        if ("GAME".equals(screen) && state.game == null) { model.screen("HOME"); screen = "HOME"; }
        boolean changed = !screen.equals(renderedScreen);
        Parcelable scroll = changed ? null : list.getLayoutManager().onSaveInstanceState();
        if (changed) {
            if (roster != null) { roster.detach(); roster = null; }
            strategyEditor = null;
            roundEditor = null; finalResultsContent = null; finalGraphAdapter = null;
            adapter = new ScoreAdapter(); setContent(adapter); renderedScreen = screen;
        }
        footerActions.clear();
        tableDetailLabel = 0; rosterColumns = false; biddingColumns = false; participationColumns = false; stackedParticipationColumns = false;
        fixedActions.setVisibility(View.GONE);
        homeHelp.setVisibility("HOME".equals(screen) ? View.VISIBLE : View.GONE);
        boolean savedPicker = "SAVED".equals(screen) || "QUICK_ADD".equals(screen);
        addSavedPlayer.setVisibility(savedPicker ? View.VISIBLE : View.GONE);
        list.setPaddingRelative(dp(16), 0, dp(16), dp(savedPicker ? 88 : 16));
        boolean game = "GAME".equals(screen);
        navigation.setVisibility(game ? View.VISIBLE : View.GONE);
        toolbar.getMenu().clear();
        toolbar.setTitle(R.string.toolbar_title);
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView && toolbar.getTitle().equals(((TextView) child).getText()))
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                        (TextView) child, 12, 20, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        }
        if (game) {
            options();
            int tab = "HISTORY".equals(model.tab()) ? R.id.tab_history : "SCOREBOARD".equals(model.tab()) ? R.id.tab_scoreboard : R.id.tab_round;
            navigation.getMenu().findItem(tab).setChecked(true);
        }
        toolbar.setNavigationIcon("HOME".equals(screen) ? null : androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_score_back));
        toolbar.setNavigationContentDescription(R.string.back);
        switch (screen) {
            case "PLAYERS": players(false); break;
            case "MANAGE": players(true); break;
            case "SETUP_DIRECTION": setupDirection(); break;
            case "SETUP_DEALER": pickDealer(true); break;
            case "SETTINGS": settings(); break;
            case "CONFIRM_START": confirmStart(); break;
            case "SCORING": scoring(); break;
            case "SCORING_HELP": scoringHelp(); break;
            case "ROUND_DETAILS": roundDetails(); break;
            case "ROUND_EDIT": editHistoricalRound(); break;
            case "PICK_DEALER": case "CONFIRM_ROSTER_DEALER": pickDealer(false); break;
            case "PICK_SCORE": pickScore(); break;
            case "RESTORE": restorePlayers(); break;
            case "SAVED": savedPlayers(false); break;
            case "SAVED_MANAGE": manageSavedPlayers(); break;
            case "QUICK_ADD": savedPlayers(true); break;
            case "HELP": header(getString(R.string.help_title), ""); rows(new ScoreAdapter.Row(1, getString(R.string.help_title), getString(R.string.help_body))); break;
            case "RECOVERY": recovery(); break;
            case "GAME": game(); break;
            default: home(); break;
        }
        headerAdapter.notifyItemChanged(0); footerAdapter.notifyItemChanged(0);
        boolean revealScoringError = "SCORING".equals(screen) && model.strategyRequest() != null
                && model.strategyRequest().error != null;
        if (changed) {
            list.stopScroll();
            ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(0, 0);
        } else if (scroll != null && !revealScoringError) list.getLayoutManager().onRestoreInstanceState(scroll);
        if (biddingColumns) requestPredictionVisibility();
        else if (autoScrollingPrediction != -1) list.stopScroll();
    }
    private void requestPredictionVisibility() {
        if (predictionRevealPending) return;
        predictionRevealPending = true;
        androidx.core.view.OneShotPreDrawListener.add(list, () -> {
            predictionRevealPending = false;
            revealCurrentPrediction();
        });
    }
    private void revealCurrentPrediction() {
        if (state == null || state.round == null || model == null || contentAdapter != bids
                || !"GAME".equals(model.screen()) || !"ROUND".equals(model.tab())
                || !"BIDDING".equals(state.round.phase) || list.getHeight() == 0) return;
        int current = 0;
        while (current < state.roundPlayers.size() && state.roundPlayers.get(current).bid != null) current++;
        if (current > 0 && model.editingBid(state.round.id, state.roundPlayers.get(current - 1).playerId)) current--;
        if (current == state.roundPlayers.size()) return;
        long playerId = state.roundPlayers.get(current).playerId;
        if (autoScrollingPrediction != -1 && autoScrollingPrediction != playerId) list.stopScroll();
        LinearLayoutManager layout = (LinearLayoutManager) list.getLayoutManager();
        int position = headerAdapter.getItemCount() + current;
        View row = layout.findViewByPosition(position);
        if (row != null && layout.getDecoratedTop(row) >= 0 && layout.getDecoratedBottom(row) <= list.getHeight()) return;
        if (autoScrollingPrediction == playerId && list.getScrollState() == RecyclerView.SCROLL_STATE_SETTLING) return;
        autoScrollingPrediction = playerId;
        androidx.recyclerview.widget.LinearSmoothScroller reveal = new androidx.recyclerview.widget.LinearSmoothScroller(this) {
            @Override protected int getVerticalSnapPreference() { return SNAP_TO_END; }
            @Override public int calculateDyToMakeVisible(View view, int snapPreference) {
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                return list.getHeight() - getLayoutManager().getDecoratedBottom(view) - params.bottomMargin;
            }
        };
        reveal.setTargetPosition(position); layout.startSmoothScroll(reveal);
    }
    private void header(String heading, String subtitle) {
        headingText = heading; descriptionText = subtitle;
    }
    private void rows(ScoreAdapter.Row... rows) { List<ScoreAdapter.Row> data = new ArrayList<>(); java.util.Collections.addAll(data, rows); rows(data); }
    private void rows(List<ScoreAdapter.Row> rows) {
        setContent(adapter);
        adapter.submit(rows);
    }
    private void setContent(RecyclerView.Adapter<?> content) {
        if (contentAdapter == content) return;
        contentAdapter = content;
        ConcatAdapter.Config config = new ConcatAdapter.Config.Builder().setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS).build();
        list.setAdapter(new ConcatAdapter(config, headerAdapter, content, footerAdapter));
    }
    private static final class ChromeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final int layout;
        private final java.util.function.Consumer<View> bind;
        ChromeAdapter(int layout, java.util.function.Consumer<View> bind) { this.layout = layout; this.bind = bind; setHasStableIds(true); }
        @Override public long getItemId(int position) { return 0; }
        @Override public int getItemCount() { return 1; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int type) {
            return new RecyclerView.ViewHolder(android.view.LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) { };
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) { bind.accept(holder.itemView); }
    }
    private void bindHeader(View view) {
        view.findViewById(R.id.home_cards).setVisibility("HOME".equals(model.screen()) ? View.VISIBLE : View.GONE);
        View strategy = view.findViewById(R.id.setup_strategy);
        boolean choosingPlayers = "PLAYERS".equals(model.screen()) && state.draft != null;
        strategy.setVisibility(choosingPlayers ? View.VISIBLE : View.GONE);
        if (choosingPlayers) SetupStrategyHeader.bind(strategy, model, state.draft.id,
                ScoringStrategy.decode(state.draft.scoringConfig));
        TextView heading = view.findViewById(R.id.screen_title); heading.setText(headingText); ViewCompat.setAccessibilityHeading(heading, true);
        TextView description = view.findViewById(R.id.screen_description); description.setText(descriptionText); description.setVisibility(descriptionText.isEmpty() ? View.GONE : View.VISIBLE);
        TextView error = view.findViewById(R.id.inline_error); error.setText(errorText); error.setVisibility(errorText == null ? View.GONE : View.VISIBLE);
        LinearLayout columns = view.findViewById(R.id.table_columns);
        columns.removeAllViews();
        columns.setBackgroundColor(getColor(R.color.score_table_header));
        columns.setVisibility(rosterColumns || tableDetailLabel != 0 ? View.VISIBLE : View.GONE);
        columns.setPaddingRelative(rosterColumns ? 0 : dp(8), 0, rosterColumns ? 0 : dp(8), 0);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        if (stackedParticipationColumns) {
            columns.setOrientation(LinearLayout.VERTICAL);
            columns.addView(columnLabel(R.string.table_player,false),new LinearLayout.LayoutParams(-1,-2));
            LinearLayout metrics=new LinearLayout(this); metrics.setOrientation(LinearLayout.HORIZONTAL);
            metrics.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
            addParticipationLabels(metrics); columns.addView(metrics,new LinearLayout.LayoutParams(-1,-2));
            return;
        }
        if (rosterColumns) {
            View move = columnIcon(R.string.table_move, R.drawable.ic_roster_drag);
            columns.addView(move, new LinearLayout.LayoutParams(dp(48), -2));
        }
        if (rosterColumns || tableDetailLabel != 0) {
            columns.addView(columnLabel(R.string.table_player, false), new LinearLayout.LayoutParams(0, -2, 1));
            if (rosterColumns) {
                View edit = columnIcon(R.string.table_edit, R.drawable.ic_roster_edit);
                View delete = columnIcon(R.string.table_delete, R.drawable.ic_roster_remove);
                columns.addView(edit, new LinearLayout.LayoutParams(dp(48), -2));
                columns.addView(delete, new LinearLayout.LayoutParams(dp(48), -2));
            } else if (biddingColumns) {
                TextView bid = columnLabel(tableDetailLabel, false);
                bid.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                int predictionWidth = BidAdapter.inputWidth(this) + dp(32);
                if (bid.getPaint().measureText(bid.getText().toString()) > predictionWidth) bid.setText(R.string.table_prediction_short);
                bid.setContentDescription(getString(tableDetailLabel)); bid.setTooltipText(getString(tableDetailLabel));
                columns.addView(bid, new LinearLayout.LayoutParams(predictionWidth, -2));
            } else if (participationColumns) {
                addParticipationLabels(columns);
            } else columns.addView(columnLabel(tableDetailLabel, true), new LinearLayout.LayoutParams(dp(88), -2));
        }
    }
    private void addParticipationLabels(LinearLayout columns) {
        TextView score=columnLabel(R.string.table_score,true);
        int scoreWidth=getResources().getDimensionPixelSize(stackedParticipationColumns ? R.dimen.final_large_number_width : R.dimen.final_score_width);
        if(score.getPaint().measureText(score.getText().toString())>scoreWidth) score.setText(R.string.table_score_short);
        score.setContentDescription(getString(R.string.table_score)); score.setTooltipText(getString(R.string.table_score));
        columns.addView(score,new LinearLayout.LayoutParams(scoreWidth,-2));
        TextView rounds=columnLabel(R.string.table_rounds,true);
        int roundsWidth=getResources().getDimensionPixelSize(R.dimen.final_rounds_width);
        if(rounds.getPaint().measureText(rounds.getText().toString())>roundsWidth) rounds.setText(R.string.table_rounds_short);
        rounds.setContentDescription(getString(R.string.rounds_played_label)); rounds.setTooltipText(getString(R.string.rounds_played_label));
        columns.addView(rounds,new LinearLayout.LayoutParams(roundsWidth,-2));
        columns.addView(columnLabel(R.string.table_points_per_round,true),new LinearLayout.LayoutParams(getResources().getDimensionPixelSize(stackedParticipationColumns ? R.dimen.final_large_number_width : R.dimen.final_average_width),-2));
    }
    private View columnIcon(int label, int drawable) {
        androidx.appcompat.widget.AppCompatImageView icon = new androidx.appcompat.widget.AppCompatImageView(this);
        icon.setImageResource(drawable); icon.setColorFilter(getColor(R.color.score_table_on_header));
        icon.setPadding(dp(15), dp(4), dp(15), dp(4)); icon.setMinimumHeight(dp(32));
        icon.setContentDescription(getString(label)); icon.setTooltipText(getString(label));
        ViewCompat.setAccessibilityHeading(icon, true);
        return icon;
    }
    private TextView columnLabel(int label, boolean endAligned) {
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        text.setTextColor(getColor(R.color.score_table_on_header));
        text.setTextAlignment(endAligned ? View.TEXT_ALIGNMENT_VIEW_END : View.TEXT_ALIGNMENT_VIEW_START);
        ViewCompat.setAccessibilityHeading(text, true);
        return text;
    }
    private void bindFooter(View view) {
        LinearLayout footer = (LinearLayout) view; footer.removeAllViews();
        for (int index = 0; index < footerActions.size(); index++) {
            FooterAction action = footerActions.get(index);
            if (action.id == R.id.add_player && index + 1 < footerActions.size()
                    && footerActions.get(index + 1).id == R.id.quick_add) {
                LinearLayout addActions = new LinearLayout(this);
                addActions.setOrientation(LinearLayout.HORIZONTAL);
                addActions.setBaselineAligned(false);
                LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1);
                first.setMarginEnd(dp(4));
                addActions.addView(footerButton(action, true), first);
                LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1);
                second.setMarginStart(dp(4));
                addActions.addView(footerButton(footerActions.get(++index), true), second);
                footer.addView(addActions, new LinearLayout.LayoutParams(-1, -2));
            } else footer.addView(footerButton(action, false), new LinearLayout.LayoutParams(-1, -2));
        }
    }
    private MaterialButton footerButton(FooterAction action, boolean outlined) {
        MaterialButton button = outlined ? new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle) : new MaterialButton(this);
        button.setText(action.label); button.setId(action.id); button.setMinHeight(dp(48)); button.setMaxLines(10);
        if (action.id == R.id.resume_game) {
            android.text.SpannableString label = new android.text.SpannableString(action.label);
            label.setSpan(new android.text.style.RelativeSizeSpan(0.8f), action.label.indexOf('\n') + 1,
                    label.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            button.setText(label);
        }
        button.setSingleLine(false); button.setEnabled(action.enabled); button.setOnClickListener(v -> action.run.run());
        return button;
    }
    private static final class FooterAction {
        final String label; final int id; final Runnable run; boolean enabled = true;
        FooterAction(String label, int id, Runnable run) { this.label = label; this.id = id; this.run = run; }
        void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
    private FooterAction action(int label, int id, Runnable run) {
        return action(getString(label), id, run);
    }
    private FooterAction action(String label, int id, Runnable run) {
        FooterAction action = new FooterAction(label, id, run); footerActions.add(action); return action;
    }
    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private void home() {
        header(getString(R.string.home_title), "");
        List<ScoreAdapter.Row> rows = new ArrayList<>();
        if (state.game != null && "COMPLETE".equals(state.game.status))
            rows.add(new ScoreAdapter.Row(1, getString(R.string.completed_results), "")
                    .action(getString(R.string.completed_results), () -> open("GAME")));
        else if (state.game != null) {
            long knownPlayedAt = state.round == null ? state.game.createdAt : state.round.createdAt;
            for (Records.ScoreAdjustment adjustment : state.adjustments)
                knownPlayedAt = Math.max(knownPlayedAt, adjustment.createdAt);
            for (Records.RoundEdit edit : state.roundEdits)
                knownPlayedAt = Math.max(knownPlayedAt, edit.createdAt);
            long playedAt = getSharedPreferences("game_activity", MODE_PRIVATE)
                    .getLong("last_played_" + state.game.id, knownPlayedAt);
            java.util.Date date = new java.util.Date(playedAt);
            String time = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH).format(date);
            String day = new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH).format(date);
            action(getString(R.string.resume_last_played, time, day), R.id.resume_game, () -> open("GAME"));
        }
        if (!state.recoveries.isEmpty()) rows.add(new ScoreAdapter.Row(5, getString(R.string.recovery_title), "").action(getString(R.string.recovery_title), () -> open("RECOVERY")));
        rows(rows);
        action(R.string.new_game, R.id.primary_action, () -> {
            if (state.draft != null) { open("PLAYERS"); return; }
            model.run(() -> model.repository.saveDraft(new DraftSpec(0, new ArrayList<>(), "ASCENDING", "CUSTOM", null)), "PLAYERS");
        });
    }
    private List<PlayerSpec> draftPlayers() {
        List<PlayerSpec> players = new ArrayList<>();
        for (Records.GamePlayer player : state.draftPlayers) players.add(new PlayerSpec(player.id, player.name, player.savedPlayerId));
        return players;
    }
    private void saveDraft(List<PlayerSpec> players) {
        if (state.draft == null) return;
        long draftId = state.draft.id;
        List<Long> order = new ArrayList<>(); for (PlayerSpec player : players) order.add(player.id);
        model.run(() -> model.repository.reorderDraft(draftId, order));
    }
    private void players(boolean manage) {
        if (!manage && state.draft == null) { open("HOME"); return; }
        header(getString(manage ? R.string.manage_players : R.string.setup_players), "");
        rosterColumns = true;
        List<PlayerSpec> players = manage ? model.staged() : draftPlayers();
        Long dealer = manage ? model.stagedDealer() : state.draft.dealerId;
        if (roster == null) {
            roster = new RosterAdapter(this, players, dealer, new RosterAdapter.Listener() {
                @Override public void onChanged(List<PlayerSpec> next) {
                    if (manage) model.stage(next); else saveDraft(next);
                }
                @Override public void rename(PlayerSpec player) { editPlayer(manage, player); }
                @Override public void remove(PlayerSpec player) {
                    List<PlayerSpec> next = manage ? model.staged() : draftPlayers();
                    int position = -1; for (int i = 0; i < next.size(); i++) if (next.get(i).id == player.id) position = i;
                    if (position < 0) return;
                    PlayerSpec removed = next.remove(position);
                    if (manage) {
                        Long selected = model.stagedDealer();
                        if (selected != null && selected == removed.id) {
                            model.stagedDealer(next.isEmpty() ? null : next.get(position % next.size()).id);
                        }
                        model.stage(next); render();
                    } else {
                        long draftId = state.draft.id;
                        model.run(() -> model.repository.removeDraftPlayer(draftId, removed.id));
                    }
                }
            });
            setContent(roster);
            roster.attachTo(list, false);
        } else roster.update(players, dealer);
        action(R.string.add_player, R.id.add_player, () -> addPlayer(manage));
        action(R.string.quick_add, R.id.quick_add, () -> { pickerReturn = manage ? "MANAGE" : "PLAYERS"; selectedProfiles.clear(); search = ""; open("QUICK_ADD"); });
        if (manage) {
            action(R.string.choose_dealer, View.generateViewId(), () -> { pickerReturn = "MANAGE"; open("PICK_DEALER"); });
            if (!state.inactivePlayers.isEmpty()) action(R.string.restore_players, View.generateViewId(), () -> open("RESTORE"));
            action(R.string.apply, R.id.primary_action, this::applyRoster);
        } else {
            toolbar.getMenu().add(R.string.setup_settings).setOnMenuItemClickListener(item -> { open("SETTINGS"); return true; });
            action(R.string.start_game, R.id.primary_action, () -> {
            try {
                GameRules.validatePlayerCount(state.draftPlayers.size());
                model.beginSetup(state.draft.id, setupRosterSignature());
            }
            catch (IllegalArgumentException exception) { model.error.setValue(exception.getMessage()); }
            });
        }
    }
    private void editPlayer(boolean manage, PlayerSpec selected) {
        List<PlayerSpec> players = manage ? model.staged() : draftPlayers();
        int found = -1; for (int i = 0; i < players.size(); i++) if (players.get(i).id == selected.id) found = i;
        if (found < 0) return;
        PlayerSpec player = players.get(found);
        model.beginForm(new FormRequest(FormRequest.Kind.RENAME_PLAYER, player.name, manage,
                manage ? state.game.id : state.draft.id, player.id,
                manage && model.stagedRoundId() != null ? model.stagedRoundId() : 0, 0, 0));
    }
    private void addPlayer(boolean manage) {
        model.beginForm(new FormRequest(FormRequest.Kind.ADD_PLAYER, "", manage,
                manage ? state.game.id : state.draft.id, 0,
                manage && model.stagedRoundId() != null ? model.stagedRoundId() : 0, 0, 0));
    }
    private void settings() {
        if (state.draft == null) { open("HOME"); return; }
        header(getString(R.string.setup_settings), "");
        ScoringStrategy scoring = ScoringStrategy.decode(state.draft.scoringConfig);
        long scoringDraftId = state.draft.id;
        rows(new ScoreAdapter.Row(4, getString(R.string.scoring_selected, StrategyEditor.label(this, scoring)),
                        StrategyEditor.rules(this, scoring))
                        .action(getString(R.string.choose_scoring), () -> model.beginStrategy(new StrategyRequest(
                                StrategyRequest.Target.DRAFT, scoringDraftId, "SETTINGS", scoring)))
                        .action(getString(R.string.how_scoring_works), () -> showScoringHelp(scoring, "SETTINGS")),
                new ScoreAdapter.Row(1, getString(R.string.direction), dealOrderLabel("ASCENDING".equals(state.draft.direction)))
                        .action(dealOrderLabel(true), () -> config("ASCENDING", null))
                        .action(dealOrderLabel(false), () -> config("DESCENDING", null)),
                new ScoreAdapter.Row(2, getString(R.string.trump_mode), getString("FIXED".equals(state.draft.trumpMode) ? R.string.fixed_trump : R.string.custom_trump))
                        .action(getString(R.string.fixed_trump), () -> config(null, "FIXED"))
                        .action(getString(R.string.custom_trump), () -> config(null, "CUSTOM")),
                new ScoreAdapter.Row(3, getString(R.string.choose_dealer), playerName(state.draftPlayers, state.draft.dealerId))
                        .action(getString(R.string.choose_dealer), () -> { pickerReturn = "SETTINGS"; open("PICK_DEALER"); }));
        action(R.string.start_game, R.id.primary_action, () -> {
            long draftId = state.draft.id;
            if (!model.setupConfirmed(draftId, setupRosterSignature())) {
                model.beginSetup(draftId, setupRosterSignature()); return;
            }
            if (state.game != null) open("CONFIRM_START"); else model.startConfirmedSetup(draftId);
        });
    }
    private void confirmStart() {
        Long draftId = model.confirmedSetupDraft();
        if (draftId == null) { open("PLAYERS"); return; }
        header(getString(R.string.replace_game_title), getString(R.string.replace_game_message));
        rows(new ArrayList<ScoreAdapter.Row>());
        action(R.string.start_game, R.id.primary_action, () -> model.startConfirmedSetup(draftId));
        action(R.string.cancel, View.generateViewId(), () -> open("PLAYERS"));
    }
    private void scoring() {
        StrategyRequest request = model.strategyRequest();
        if (request == null) { open("HOME"); return; }
        String effect;
        if (request.target == StrategyRequest.Target.DRAFT) effect = getString(R.string.strategy_for_next_game);
        else if (state.round == null || state.round.id != request.targetId) effect = getString(R.string.strategy_stale_round);
        else {
            boolean started = false;
            for (Records.RoundPlayer player : state.roundPlayers) started |= player.bid != null;
            effect = started ? getString(R.string.strategy_from_round, state.round.number + 1)
                    : getString(R.string.strategy_this_round, state.round.number);
            if (started) {
                try {
                    if (request.strategy().encode().equals(state.round.scoringConfig)) effect = getString(
                            state.game.pendingScoringConfig == null ? R.string.strategy_unchanged : R.string.strategy_cancel_pending,
                            StrategyEditor.label(this, request.strategy()));
                } catch (IllegalArgumentException invalidCustom) { /* The form keeps its raw values. */ }
            }
        }
        header(request.customEditor ? getString(request.profileId == 0 ? R.string.create_custom_strategy : R.string.modify_strategy)
                : getString(R.string.choose_scoring), effect);
        if (request.customEditor && request.profileId != 0) {
            android.view.MenuItem delete = toolbar.getMenu().add(0, R.id.delete_strategy, 0, R.string.delete_strategy);
            android.graphics.drawable.Drawable icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(this,R.drawable.ic_roster_remove).mutate();
            icon.setTint(getColor(R.color.strategy_delete)); delete.setIcon(icon);
            delete.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
            delete.setOnMenuItemClickListener(item -> {
                new MaterialAlertDialogBuilder(this).setTitle(R.string.delete_strategy)
                        .setMessage(getString(R.string.delete_strategy_message,request.name))
                        .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.table_delete,
                                (dialog,which) -> model.deleteStrategy(request)).show();
                return true;
            });
        }
        if (strategyEditor == null) strategyEditor = new StrategyEditor(request, new StrategyEditor.Listener() {
            @Override public void onChanged(StrategyRequest changed) { model.editStrategy(changed); }
            @Override public void onHelp() {
                StrategyRequest current = model.strategyRequest();
                if (current == null) return;
                String config = null;
                try { config = current.strategy().encode(); } catch (IllegalArgumentException invalid) { }
                model.scoringHelp(config, "SCORING"); open("SCORING_HELP");
            }
        });
        strategyEditor.savedStrategies(state.savedStrategies); strategyEditor.update(request); setContent(strategyEditor);
        action(R.string.save_scoring, R.id.primary_action, () -> {
            StrategyRequest current = model.strategyRequest();
            if (current == null) return;
            try { model.saveStrategy(current, current.validatedStrategy()); }
            catch (IllegalArgumentException invalid) {
                model.strategyError(current.token, invalid.getMessage());
                if (strategyEditor != null) strategyEditor.focusFirstError();
            }
        }).setEnabled(!model.savingStrategy(request.token));
        action(R.string.cancel, R.id.strategy_cancel, () -> model.finishStrategy(request.token));
    }
    private void showScoringHelp(ScoringStrategy strategy, String backTo) {
        model.scoringHelp(strategy.encode(), backTo); open("SCORING_HELP");
    }
    private void scoringHelp() {
        header(getString(R.string.how_scoring_works), "");
        String encoded = model.scoringHelpConfig();
        if (encoded == null) rows(new ScoreAdapter.Row(1, getString(R.string.strategy_custom),
                getString(R.string.strategy_custom_explanation) + "\n\n" + getString(R.string.strategy_complete_fields)));
        else {
            ScoringStrategy strategy = ScoringStrategy.decode(encoded);
            rows(new ScoreAdapter.Row(1, StrategyEditor.label(this, strategy), StrategyEditor.rules(this, strategy)),
                    new ScoreAdapter.Row(2, getString(R.string.strategy_examples_title), StrategyEditor.examples(this, strategy)));
        }
        action(R.string.back, R.id.primary_action, () -> open(model.scoringHelpReturn()));
    }
    private boolean updateAppearance() {
        String screen = model.screen();
        ScoringStrategy strategy = ScoringStrategy.defaults();
        StrategyRequest editing = model.strategyRequest();
        if (("SCORING".equals(screen) || ("SCORING_HELP".equals(screen) && "SCORING".equals(model.scoringHelpReturn()))) && editing != null) {
            if (editing.preset == ScoringStrategy.Preset.AGGRESSIVE) strategy = ScoringStrategy.aggressive();
        } else if ("SCORING_HELP".equals(screen) && model.scoringHelpConfig() != null) strategy = ScoringStrategy.decode(model.scoringHelpConfig());
        else if (state.draft != null && ("PLAYERS".equals(screen) || "SETTINGS".equals(screen) || "SETUP_DIRECTION".equals(screen) || "SETUP_DEALER".equals(screen) || "CONFIRM_START".equals(screen)
                || (("PICK_DEALER".equals(screen) || "QUICK_ADD".equals(screen)) && !"MANAGE".equals(pickerReturn)))) {
            strategy = ScoringStrategy.decode(state.draft.scoringConfig);
        } else if (state.game != null && ("GAME".equals(screen) || "MANAGE".equals(screen)
                || "ROUND_DETAILS".equals(screen) || "ROUND_EDIT".equals(screen)
                || "PICK_SCORE".equals(screen) || "RESTORE".equals(screen) || "PICK_DEALER".equals(screen) || "CONFIRM_ROSTER_DEALER".equals(screen)
                || ("QUICK_ADD".equals(screen) && "MANAGE".equals(pickerReturn)))) {
            strategy = ScoringStrategy.decode(state.round == null ? state.game.scoringConfig : state.round.scoringConfig);
        }
        boolean aggressive = strategy.preset == ScoringStrategy.Preset.AGGRESSIVE;
        if (aggressive == aggressiveAppearance) return false;
        aggressiveAppearance = aggressive; changingAppearance = true; recreate(); return true;
    }
    private void config(String direction, String trump) {
        long draftId = state.draft.id;
        model.run(() -> model.repository.updateDraftSettings(draftId, direction, trump));
    }
    private String setupRosterSignature() {
        StringBuilder signature = new StringBuilder();
        for (Records.GamePlayer player : state.draftPlayers) signature.append(player.id).append(',');
        return signature.toString();
    }
    private void setupDirection() {
        if (state.draft == null) { open("PLAYERS"); return; }
        header(getString(R.string.choose_deal_order), ""); rows(new ArrayList<ScoreAdapter.Row>());
        long draftId = state.draft.id; String signature = setupRosterSignature();
        action(dealOrderLabel(true), R.id.deal_order_ascending,
                () -> model.chooseSetupDirection(draftId, signature, "ASCENDING"));
        action(dealOrderLabel(false), R.id.deal_order_descending,
                () -> model.chooseSetupDirection(draftId, signature, "DESCENDING"));
    }
    private String dealOrderLabel(boolean ascending) {
        if (state.draftPlayers.size() < GameRules.MIN_PLAYERS)
            return getString(ascending ? R.string.ascending : R.string.descending);
        int maximum = GameRules.maximumCards(state.draftPlayers.size());
        String oneCard = getResources().getQuantityString(R.plurals.card_count, 1, 1);
        String maximumCards = getResources().getQuantityString(R.plurals.card_count, maximum, maximum);
        return getString(ascending ? R.string.start_one_card : R.string.start_max_cards,
                ascending ? oneCard : maximumCards, ascending ? maximumCards : oneCard);
    }
    private void pickDealer(boolean required) {
        header(getString(R.string.choose_dealer), "");
        boolean manage = !required && "MANAGE".equals(pickerReturn);
        if (!manage && state.draft == null) { open("PLAYERS"); return; }
        List<PlayerSpec> players = manage ? model.staged() : draftPlayers();
        Long initial = required ? null : manage ? model.stagedDealer() : state.draft.dealerId;
        long draftId = manage ? 0 : state.draft.id;
        String signature = manage ? "" : setupRosterSignature();
        StringBuilder owner = new StringBuilder(required ? "SETUP:" : manage ? "MANAGE:" : "DRAFT:");
        owner.append(manage ? state.game.id : draftId);
        if (manage) owner.append(':').append(model.stagedRoundId());
        for (PlayerSpec player : players) owner.append(':').append(player.id);
        String choiceOwner = owner.toString();
        Long selected = model.pendingDealer(choiceOwner, initial);
        boolean valid = false;
        for (PlayerSpec player : players) if (selected != null && player.id == selected) valid = true;
        fixedActions.removeAllViews(); fixedActions.setVisibility(View.VISIBLE);
        MaterialButton confirm = new MaterialButton(this);
        confirm.setId(R.id.primary_action); confirm.setText(R.string.confirm_dealer); confirm.setEnabled(valid);
        setContent(new DealerChoiceAdapter(players, selected, playerId -> {
            model.selectDealer(choiceOwner, playerId); confirm.setEnabled(true);
        }));
        confirm.setOnClickListener(view -> {
            Long confirmed = model.pendingDealer(choiceOwner, initial);
            if (manage) {
                boolean applying = "CONFIRM_ROSTER_DEALER".equals(model.screen());
                model.stagedDealer(confirmed); model.confirmStagedDealer(); open("MANAGE");
                if (applying) applyRoster();
            }
            else if (required) model.chooseSetupDealer(draftId, signature, confirmed);
            else model.run(() -> model.repository.chooseDraftDealer(draftId, confirmed), "SETTINGS");
        });
        addFixedAction(confirm);
    }
    private String playerName(List<Records.GamePlayer> players, Long id) {
        if (id != null) for (Records.GamePlayer player : players) if (player.id == id) return player.name;
        return getString(R.string.dealer_not_selected);
    }
    private void game() {
        boolean completed = "COMPLETE".equals(state.game.status);
        if (completed) {
            long gameId = state.game.id;
            android.view.MenuItem replay = toolbar.getMenu().add(android.view.Menu.NONE,
                    R.id.play_again, 0, R.string.play_again);
            android.text.SpannableString replayLabel = new android.text.SpannableString(getString(R.string.play_again));
            replayLabel.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.score_table_on_header)),
                    0, replayLabel.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            replay.setTitle(replayLabel);
            if (getResources().getConfiguration().fontScale > 1.2f || getResources().getConfiguration().screenWidthDp < 320) {
                replay.setIcon(R.drawable.ic_score_replay);
                replay.setContentDescription(getString(R.string.play_again));
                replay.setTooltipText(getString(R.string.play_again));
            }
            replay.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
            replay.setOnMenuItemClickListener(item -> {
                model.run(() -> model.repository.playAgain(gameId), "PLAYERS"); return true;
            });
        }
        if ("SCOREBOARD".equals(model.tab())) { scoreboard(); return; }
        if ("HISTORY".equals(model.tab())) { history(); return; }
        if (state.round == null || "COMPLETE".equals(state.game.status)) {
            header(getString(R.string.game_complete), ""); scoreboardRows(); return;
        }
        Records.Round round = state.round;
        String trump = "CUSTOM".equals(state.game.trumpMode) ? "" : getString(R.string.trump_format, getString(new int[]{R.string.spades, R.string.diamonds, R.string.clubs, R.string.hearts}[(round.number - 1) % 4]));
        header(getResources().getQuantityString(R.plurals.deal_heading, round.cards, round.cards), (trump.isEmpty() ? "" : trump + "\n")
                + getString(R.string.scoring_selected, StrategyEditor.label(this, ScoringStrategy.decode(round.scoringConfig))));
        if ("BIDDING".equals(round.phase)) bidding(round); else results(round);

    }
    private void bidding(Records.Round round) {
        tableDetailLabel = R.string.table_bid; biddingColumns = true;
        int firstMissing = state.roundPlayers.size(), submittedTotal = 0;
        for (int i = 0; i < state.roundPlayers.size(); i++) {
            Integer bid = state.roundPlayers.get(i).bid;
            if (bid != null) submittedTotal += bid;
            else if (firstMissing == state.roundPlayers.size()) firstMissing = i;
        }
        if (bids == null) bids = new BidAdapter(model, this::render);
        bids.submit(round.id, round.dealerId, round.cards, state.roundPlayers, firstMissing); setContent(bids);
        fixedActions.removeAllViews(); fixedActions.setVisibility(View.VISIBLE);
        Records.RoundPlayer current = firstMissing < state.roundPlayers.size() ? state.roundPlayers.get(firstMissing) : null;
        int[] ids = {R.id.quick_bid_0, R.id.quick_bid_1, R.id.quick_bid_2, R.id.quick_bid_3};
        for (int value = 0; current != null && value <= 3; value++) {
            final int bid = value;
            MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setId(ids[value]); button.setText(Integer.toString(value));
            button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(0, 0, 0, 0);
            button.setMinHeight(dp(48));
            button.setContentDescription(getString(R.string.quick_bid, value) + (current == null ? "" : ", " + current.nameSnapshot));
            button.setEnabled(current != null && value <= round.cards
                    && (current.playerId != round.dealerId || submittedTotal + value != round.cards));
            if (current != null) button.setOnClickListener(view -> model.submitInlineBid(round.id, current.playerId, Integer.toString(bid)));
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, dp(56), 1);
            if (value < 3) layout.setMarginEnd(dp(6));
            fixedActions.addView(button, layout);
        }
        if (current == null) {
            MaterialButton record = new MaterialButton(this);
            record.setId(R.id.primary_action); record.setText(R.string.record_bids);
            record.setOnClickListener(view -> model.run(() -> model.repository.recordBids(round.id)));
            addFixedAction(record);
        }
    }
    private void bidDialog(Records.Round round, Records.RoundPlayer player) {
        model.beginForm(new FormRequest(FormRequest.Kind.BID,
                player.bid == null ? "" : player.bid.toString(), false,
                round.gameId, player.playerId, round.id, 0, round.cards));
    }
    private void results(Records.Round round) {
        tableDetailLabel = R.string.table_result;
        if (outcomes == null) outcomes = new ResultAdapter(model);
        outcomes.submit(round.id, round.dealerId, state.roundPlayers); setContent(outcomes);
        fixedActions.removeAllViews(); fixedActions.setVisibility(View.VISIBLE);
        MaterialButton finish = new MaterialButton(this);
        finish.setId(R.id.primary_action); finish.setText(R.string.finish_round);
        finish.setOnClickListener(view -> model.run(() -> model.repository.completeRound(round.id)));
        addFixedAction(finish);
    }
    private void addFixedAction(MaterialButton button) {
        button.setSingleLine(false); button.setMaxLines(10); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(56));
        fixedActions.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }
    private String points(long total) { return getResources().getQuantityString(R.plurals.score_points, total == 1 ? 1 : 2, total); }
    private String roundHeading(int number, int cards) { return getResources().getQuantityString(R.plurals.round_heading, cards, number, cards); }
    private void scoreboard() { header(getString(R.string.scoreboard_tab), ""); scoreboardRows(); }
    private void scoreboardRows() {
        tableDetailLabel = R.string.table_score;
        boolean completed="COMPLETE".equals(state.game.status);
        GameStatistics report=GameStatistics.from(state);
        participationColumns = completed && report.hasUnequalParticipation;
        stackedParticipationColumns = participationColumns && getResources().getConfiguration().fontScale>=1.3f
                && getResources().getConfiguration().screenWidthDp<600;
        List<ScoreAdapter.Row> rows = new ArrayList<>();
        for(GameStatistics.PlayerStatistics player:report.players) {
            ScoreAdapter.Row row = new ScoreAdapter.Row(player.playerId,getString(R.string.ranking_format,player.rank,player.name),
                    player.active ? points(player.total) : getString(R.string.removed_player_score,points(player.total)))
                    .compactName().score(player.total).legend(completed);
            if(participationColumns) row.participation(player.playedRounds,player.pointsPerRound,stackedParticipationColumns);
            rows.add(row);
        }
        adapter.submit(rows);
        if (!"COMPLETE".equals(state.game.status)) { setContent(adapter); return; }
        if (finalResultsContent == null) {
            finalGraphAdapter = new ChromeAdapter(R.layout.final_score_graph, this::bindFinalGraph);
            ConcatAdapter.Config config = new ConcatAdapter.Config.Builder()
                    .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS).build();
            finalResultsContent = new ConcatAdapter(config, adapter, finalGraphAdapter);
        }
        setContent(finalResultsContent);
        finalGraphAdapter.notifyItemChanged(0);
    }
    private void bindFinalGraph(View view) {
        GameStatistics report = GameStatistics.from(state);
        Set<Long> players = new HashSet<>();
        for (GameStatistics.PlayerStatistics player : report.players) players.add(player.playerId);
        ScoreGraphView graph = view.findViewById(R.id.final_score_graph);
        graph.setData(report, players, ScoreGraphView.Mode.CUMULATIVE, event -> {
            if (event.kind == GameStatistics.EventKind.ROUND) inspectRound(event.round.roundId, "GAME");
        });
    }
    private void history() {
        header(getString(R.string.history_tab), state.history.isEmpty() ? getString(R.string.no_history) : "");
        List<HistoryEntry> entries = new ArrayList<>();
        for (Snapshot.RoundHistory history : state.history) {
            StringBuilder detail = new StringBuilder();
            for (int i = 0; i < history.players.size(); i++) {
                Records.RoundPlayer player = history.players.get(i);
                if (detail.length() > 0) detail.append("\n\n");
                detail.append(getString(R.string.history_player, i + 1, player.nameSnapshot, player.bid,
                        getString("WON".equals(player.outcome) ? R.string.won : R.string.lost), ScoringStrategy.decode(history.round.scoringConfig).points(history.round.cards, player.bid, Outcome.valueOf(player.outcome), history.players.size())));
            }
            entries.add(new HistoryEntry(history.round.completedAt == null ? history.round.createdAt : history.round.completedAt,
                    new ScoreAdapter.Row(history.round.id, roundHeading(history.round.number, history.round.cards),
                            StrategyEditor.label(this, ScoringStrategy.decode(history.round.scoringConfig)) + "\n\n" + detail)
                            .action(getString(R.string.history_inspect_round), () -> inspectRound(history.round.id, "GAME"))));
        }
        for (Records.ScoreAdjustment adjustment : state.adjustments) entries.add(new HistoryEntry(adjustment.createdAt, new ScoreAdapter.Row(-adjustment.id - 1,
                getString(R.string.adjustment_title, adjustment.nameSnapshot), getResources().getQuantityString(R.plurals.adjustment_detail, Math.addExact(adjustment.previousTotal, adjustment.delta) == 1 ? 1 : 2, adjustment.previousTotal, Math.addExact(adjustment.previousTotal, adjustment.delta)))));
        entries.sort(Comparator.comparingLong((HistoryEntry entry) -> entry.time).reversed());
        List<ScoreAdapter.Row> rows = new ArrayList<>(); for (HistoryEntry entry : entries) rows.add(entry.row);
        rows(rows);
    }
    private void inspectRound(long roundId, String returnScreen) {
        model.inspectRound(roundId, returnScreen);
    }
    private Snapshot.RoundHistory selectedHistory() {
        Long selected = model.historyRoundId();
        if (selected != null) for (Snapshot.RoundHistory history : state.history)
            if (history.round.id == selected) return history;
        return null;
    }
    private void roundDetails() {
        Snapshot.RoundHistory history = selectedHistory();
        if (history == null) {
            header(getString(R.string.history_tab), getString(R.string.history_missing_round)); rows();
            action(R.string.back, R.id.primary_action, () -> open(model.historyReturn())); return;
        }
        ScoringStrategy scoring = ScoringStrategy.decode(history.round.scoringConfig);
        header(roundHeading(history.round.number, history.round.cards), getString(R.string.history_detail_description,
                StrategyEditor.label(this, scoring)));
        rows(RoundDetails.rows(this, history, state.roundEdits));
        action(R.string.history_edit_round, R.id.edit_historical_round, () -> model.beginRoundEditor(history));
        action(R.string.how_scoring_works, R.id.round_scoring_help, () -> showScoringHelp(scoring, "ROUND_DETAILS"));
        action(R.string.back, R.id.history_back, () -> open(model.historyReturn()));
    }
    private void editHistoricalRound() {
        RoundEditRequest request = model.roundEditor();
        if (request == null) { model.screen("ROUND_DETAILS"); roundDetails(); return; }
        RoundEditPreview preview = model.previewFor(request);
        boolean busy = model.roundEditorBusy(request);
        String description = getString(R.string.history_edit_description) + "\n\n"
                + StrategyEditor.label(this, request.scoring);
        if (busy) description += "\n" + getString(R.string.history_working);
        else if (preview != null) description += "\n" + getString(R.string.history_preview_ready);
        if (request.error != null) description += "\n\n" + request.error;
        header(getString(R.string.history_edit_round) + " · " + roundHeading(request.roundNumber, request.cards), description);
        if (roundEditor == null) roundEditor = new RoundEditorAdapter(model::editRoundInput);
        roundEditor.submit(request, preview, busy); setContent(roundEditor);
        action(R.string.history_preview, R.id.preview_historical_round, () -> {
            RoundEditRequest current = model.roundEditor();
            if (current != null) model.previewRound(current, false);
        }).setEnabled(!busy);
        action(R.string.history_save, R.id.save_historical_round, () -> {
            RoundEditRequest current = model.roundEditor();
            if (current != null && model.previewFor(current) != null) model.previewRound(current, true);
        }).setEnabled(!busy && preview != null);
        action(R.string.cancel, R.id.cancel_historical_round, this::back).setEnabled(!busy);
    }
    private static final class HistoryEntry {
        final long time; final ScoreAdapter.Row row;
        HistoryEntry(long time, ScoreAdapter.Row row) { this.time = time; this.row = row; }
    }
    private void options() {
        boolean active = state.round != null && !"COMPLETE".equals(state.game.status);
        int[] labels = active
                ? new int[]{R.string.manage_players, R.string.change_dealer, R.string.correct_score,
                    R.string.restart_round, R.string.change_scoring, R.string.new_game,
                    R.string.how_scoring_works, R.string.help, R.string.home}
                : new int[]{R.string.correct_score, R.string.new_game, R.string.help, R.string.home};
        for (int label : labels) toolbar.getMenu().add(android.view.Menu.NONE, label, 0, label)
                .setOnMenuItemClickListener(item -> { gameOption(label); return true; });
    }
    private void gameOption(int label) {
        rememberLastPlayed();
        if (label == R.string.manage_players || label == R.string.change_dealer) {
            if (state.round == null) return;
            List<PlayerSpec> players = new ArrayList<>();
            for (Records.GamePlayer player : state.players)
                players.add(new PlayerSpec(player.id, player.name, player.savedPlayerId));
            model.stage(players); model.stagedDealer(state.round.dealerId); model.stagedRoundId(state.round.id);
            if (label == R.string.change_dealer) { pickerReturn = "MANAGE"; open("PICK_DEALER"); }
            else open("MANAGE");
        } else if (label == R.string.correct_score) open("PICK_SCORE");
        else if (label == R.string.restart_round && state.round != null) {
            long id = state.round.id;
            confirm(R.string.restart_title, R.string.restart_message, R.string.restart,
                    () -> model.run(() -> model.repository.restartRound(id)));
        } else if (label == R.string.change_scoring && state.round != null)
            model.beginStrategy(new StrategyRequest(StrategyRequest.Target.ROUND,
                    state.round.id, "GAME", ScoringStrategy.decode(state.game.pendingScoringConfig == null
                            ? state.round.scoringConfig : state.game.pendingScoringConfig)));
        else if (label == R.string.new_game) newSetup();
        else if (label == R.string.how_scoring_works && state.round != null)
            showScoringHelp(ScoringStrategy.decode(state.round.scoringConfig), "GAME");
        else if (label == R.string.help || label == R.string.home)
            open(label == R.string.help ? "HELP" : "HOME");
    }
    private void newSetup() {
        if (state.draft == null) model.run(() -> model.repository.saveDraft(new DraftSpec(0, new ArrayList<>(), "ASCENDING", "CUSTOM", null)), "PLAYERS");
        else open("PLAYERS");
    }
    private boolean hasSeatingChanges(List<PlayerSpec> players) {
        if (players.size() != state.players.size()) return true;
        for (int index = 0; index < players.size(); index++)
            if (players.get(index).id != state.players.get(index).id) return true;
        return false;
    }
    private boolean hasRosterChanges() {
        List<PlayerSpec> players = model.staged();
        if (state.round == null || hasSeatingChanges(players)
                || !java.util.Objects.equals(model.stagedDealer(), state.round.dealerId)) return true;
        for (int index = 0; index < players.size(); index++) {
            PlayerSpec staged = players.get(index);
            Records.GamePlayer current = state.players.get(index);
            if (!staged.name.equals(current.name) || staged.saveForFuture
                    || !java.util.Objects.equals(staged.savedPlayerId, current.savedPlayerId)) return true;
        }
        return false;
    }
    private void applyRoster() {
        if (state.round == null) return;
        Long stagedRoundId = model.stagedRoundId();
        if (stagedRoundId == null || stagedRoundId != state.round.id) {
            model.error.setValue("This player editor belongs to an earlier round. Reopen Manage players.");
            return;
        }
        List<PlayerSpec> players = model.staged();
        Long dealer = model.stagedDealer();
        final Integer adjustedCards;
        try { adjustedCards=GameRules.cardsAfterRosterChange(state.round.cards,players.size(),
                android.example.judgement.domain.Direction.valueOf(state.game.direction)); }
        catch (IllegalArgumentException exception) { model.error.setValue(exception.getMessage()); return; }
        boolean seatingChanged = hasSeatingChanges(players);
        if (seatingChanged && !model.confirmedRosterDealer()) {
            pickerReturn = "MANAGE"; open("CONFIRM_ROSTER_DEALER"); return;
        }
        boolean structure = seatingChanged || dealer == null || dealer != state.round.dealerId;
        boolean started = false; for (Records.RoundPlayer player : state.roundPlayers) started |= player.bid != null;
        long roundId = stagedRoundId; long selected = dealer == null ? 0 : dealer;
        Runnable apply = () -> model.run(() -> model.repository.applyRoster(roundId, players, selected, true), "GAME");
        if (adjustedCards == null) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.roster_finish_title)
                    .setMessage(getString(R.string.roster_finish_message,state.round.cards,GameRules.maximumCards(players.size())))
                    .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.finish_game,(dialog,which)->apply.run()).show();
        } else if (structure && started && adjustedCards != state.round.cards) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.restart_title)
                    .setMessage(getString(R.string.roster_step_down_message,adjustedCards))
                    .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.apply,(dialog,which)->apply.run()).show();
        } else if (structure && started) confirm(R.string.restart_title,R.string.restart_message,R.string.apply,apply);
        else apply.run();
    }
    private void restorePlayers() {
        header(getString(R.string.restore_players), "");
        List<ScoreAdapter.Row> rows = new ArrayList<>();
        Set<Long> stagedIds = new HashSet<>();
        for (PlayerSpec player : model.staged()) stagedIds.add(player.id);
        for (Records.GamePlayer player : state.inactivePlayers) {
            if (stagedIds.contains(player.id)) continue;
            rows.add(new ScoreAdapter.Row(player.id, player.name, points(state.total(player.id)))
                    .action(getString(R.string.restore), () -> {
                        List<PlayerSpec> players = model.staged();
                        for (PlayerSpec staged : players) if (staged.id == player.id) return;
                        players.add(new PlayerSpec(player.id, player.name, player.savedPlayerId));
                        model.stage(players); open("MANAGE");
                    }));
        }
        rows(rows);
    }
    private void pickScore() {
        header(getString(R.string.correct_score), "");
        List<ScoreAdapter.Row> rows = new ArrayList<>();
        List<Records.GamePlayer> players = new ArrayList<>(state.players); players.addAll(state.inactivePlayers);
        for (Records.GamePlayer player : players) {
            long expected = state.total(player.id);
            rows.add(new ScoreAdapter.Row(player.id, player.name, player.active ? points(expected) : getString(R.string.removed_player_score, points(expected)))
                    .action(getString(R.string.correct_score), () -> model.beginForm(
                            new FormRequest(FormRequest.Kind.SCORE, Long.toString(expected), false,
                                    player.gameId, player.id, 0, expected, 0))));
        }
        rows(rows);
    }
    private void savedPlayers(boolean picker) {
        header(getString(R.string.saved_players), state.savedPlayers.isEmpty() ? getString(R.string.no_saved_players) : "");
        boolean stagedPicker = picker && "MANAGE".equals(pickerReturn);
        List<PlayerSpec> currentPlayers = stagedPicker ? model.staged() : draftPlayers();
        String query;
        try { query = Names.key(search); } catch (IllegalArgumentException blank) { query = ""; }
        List<SavedPlayerPickerAdapter.Item> items = new ArrayList<>();
        Set<Long> eligible = new HashSet<>();
        Set<Long> addable = new HashSet<>();
        for (Records.SavedPlayer profile : state.savedPlayers) {
            boolean present = false, conflict = false;
            for (PlayerSpec current : currentPlayers) {
                present |= current.savedPlayerId != null && current.savedPlayerId == profile.id;
                conflict |= Names.key(current.name).equals(profile.nameKey);
            }
            boolean canAdd = !present && !conflict;
            boolean enabled = !picker || canAdd;
            if (canAdd) addable.add(profile.id);
            if (enabled) eligible.add(profile.id);
            if (profile.nameKey.contains(query)) items.add(new SavedPlayerPickerAdapter.Item(profile.id, profile.name,
                    selectedProfiles.contains(profile.id) && enabled, enabled,
                    canAdd ? "" : getString(present ? R.string.already_added : R.string.name_conflict)));
        }
        selectedProfiles.retainAll(eligible);
        toolbar.getMenu().add(android.view.Menu.NONE, R.id.search_saved_action, 0, R.string.search_saved)
                .setOnMenuItemClickListener(item -> {
                    model.beginForm(new FormRequest(FormRequest.Kind.SEARCH, search, false, 0, 0, 0, 0, 0)); return true;
                });
        toolbar.getMenu().add(android.view.Menu.NONE, R.id.manage_saved_action, 1, R.string.manage_saved_players)
                .setOnMenuItemClickListener(item -> { savedManageReturn = picker ? "QUICK_ADD" : "SAVED"; open("SAVED_MANAGE"); return true; });
        fixedActions.removeAllViews(); fixedActions.setVisibility(View.VISIBLE);
        MaterialButton add = new MaterialButton(this); add.setId(R.id.primary_action); add.setText(R.string.add_selected);
        add.setEnabled(!selectedProfiles.isEmpty() && addable.containsAll(selectedProfiles));
        add.setOnClickListener(view -> {
            List<Long> selected = new ArrayList<>(selectedProfiles);
            if (stagedPicker) {
                List<PlayerSpec> players = model.staged();
                for (Records.SavedPlayer profile : state.savedPlayers) if (selectedProfiles.contains(profile.id))
                    players.add(new PlayerSpec(-System.nanoTime(), profile.name, profile.id));
                if (players.size() > 52) { model.error.setValue("A game supports at most 52 players."); return; }
                model.stage(players); open("MANAGE");
            } else if (state.draft != null) {
                long draftId = state.draft.id;
                model.run(() -> model.repository.addSavedPlayersToDraft(draftId, selected), "PLAYERS");
            } else {
                List<PlayerSpec> players = new ArrayList<>();
                for (Records.SavedPlayer profile : state.savedPlayers) if (selectedProfiles.contains(profile.id))
                    players.add(new PlayerSpec(0, profile.name, profile.id));
                model.run(() -> model.repository.saveDraft(new DraftSpec(0, players, "ASCENDING", "CUSTOM", null)), "PLAYERS");
            }
        });
        addFixedAction(add);
        SavedPlayerPickerAdapter pickerAdapter = new SavedPlayerPickerAdapter((profileId, checked) -> {
            if (checked) selectedProfiles.add(profileId); else selectedProfiles.remove(profileId);
            add.setEnabled(!selectedProfiles.isEmpty() && addable.containsAll(selectedProfiles));
        }, profileId -> confirm(R.string.delete_profile_title, R.string.delete_profile_message,
                R.string.remove, () -> model.run(() -> model.repository.removeSavedPlayer(profileId))));
        pickerAdapter.submit(items); setContent(pickerAdapter);
    }
    private void manageSavedPlayers() {
        header(getString(R.string.manage_saved_players), state.savedPlayers.isEmpty() ? getString(R.string.no_saved_players) : "");
        toolbar.getMenu().add(android.view.Menu.NONE, R.id.search_saved_action, 0, R.string.search_saved)
                .setOnMenuItemClickListener(item -> {
                    model.beginForm(new FormRequest(FormRequest.Kind.SEARCH, search, false, 0, 0, 0, 0, 0)); return true;
                });
        String query;
        try { query = Names.key(search); } catch (IllegalArgumentException blank) { query = ""; }
        List<ScoreAdapter.Row> profiles = new ArrayList<>();
        for (Records.SavedPlayer profile : state.savedPlayers) {
            if (!profile.nameKey.contains(query)) continue;
            profiles.add(new ScoreAdapter.Row(profile.id, profile.name, "")
                    .action(getString(R.string.rename), () -> model.beginForm(new FormRequest(
                            FormRequest.Kind.RENAME_SAVED, profile.name, false, 0, profile.id, 0, 0, 0)))
                    .action(getString(R.string.remove), () -> confirm(R.string.delete_profile_title, R.string.delete_profile_message,
                            R.string.remove, () -> model.run(() -> model.repository.removeSavedPlayer(profile.id)))));
        }
        rows(profiles);
        action(R.string.add_saved, R.id.primary_action, () -> model.beginForm(new FormRequest(
                FormRequest.Kind.ADD_SAVED, "", false, 0, 0, 0, 0, 0)));
    }
    private void recovery() {
        header(getString(R.string.recovery_title), getString(R.string.recovery_description));
        List<ScoreAdapter.Row> rows = new ArrayList<>();
        for (Records.RecoveryRow row : state.recoveries) {
            String heading = row.label;
            String detail = row.rawValues;
            try {
                org.json.JSONArray cells = new org.json.JSONArray(row.rawValues);
                StringBuilder original = new StringBuilder();
                for (int i = 0; i < cells.length(); i++) {
                    org.json.JSONObject cell = cells.getJSONObject(i);
                    String column = cell.getString("column");
                    String value = cell.isNull("value") ? getString(R.string.original_null) : cell.getString("value");
                    if ("name".equalsIgnoreCase(column)) heading = value.isEmpty() ? getString(R.string.original_empty_name) : value;
                    if (original.length() > 0) original.append('\n');
                    original.append(column).append(": ").append(value);
                }
                detail = original.toString();
            } catch (org.json.JSONException malformedRecovery) {
                // The raw copy remains visible even when an unknown legacy shape cannot be formatted.
            }
            rows.add(new ScoreAdapter.Row(row.id, heading, detail));
        }
        rows(rows);
    }
    private void restoreForm(FormRequest request) {
        if (request == null) {
            AlertDialog previous = inputDialog;
            inputDialog = null; inputError = null; activeFormToken = null;
            if (previous != null) previous.dismiss();
            return;
        }
        if (destroyingForms || isFinishing() || isDestroyed()) return;
        if (inputDialog != null && request.token.equals(activeFormToken)) {
            updateFormControls(request);
            return;
        }
        AlertDialog previous = inputDialog;
        inputDialog = null; inputError = null; activeFormToken = null;
        if (previous != null) previous.dismiss();
        activeFormToken = request.token;
        String heading = getString(formHeading(request.kind));
        boolean number = request.kind == FormRequest.Kind.BID || request.kind == FormRequest.Kind.SCORE;
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(24), dp(8), dp(24), 0);
        TextInputLayout field = new TextInputLayout(this);
        field.setHint(heading);
        TextInputEditText input = new TextInputEditText(field.getContext()); input.setId(R.id.edit_value);
        input.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinHeight(dp(48)); input.setSingleLine(number); input.setText(request.text); input.setSelectAllOnFocus(number);
        field.addView(input, new LinearLayout.LayoutParams(-1, -2)); form.addView(field, new LinearLayout.LayoutParams(-1, -2));
        CheckBox check = new CheckBox(this); check.setText(R.string.save_future); check.setMinHeight(dp(48)); check.setChecked(request.saveFuture);
        if (request.kind == FormRequest.Kind.ADD_PLAYER) form.addView(check);
        androidx.core.widget.NestedScrollView scrollingForm = new androidx.core.widget.NestedScrollView(this);
        scrollingForm.addView(form);
        View.OnLayoutChangeListener revealError = (view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                revealFormError(field);
            }
        };
        field.addOnLayoutChangeListener(revealError);
        scrollingForm.addOnLayoutChangeListener(revealError);
        input.setMaxLines(number ? 1 : 6);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle(heading).setView(scrollingForm).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).setNeutralButton(R.string.use_saved_player, null).create();
        // Register ownership before show(): several lifecycle observers can restore the
        // same form before Android delivers the asynchronous OnShow callback.
        inputDialog = dialog; inputError = field;
        dialog.setOnShowListener(ignored -> {
            FormRequest shown = model.form();
            if (inputDialog != dialog || destroyingForms || shown == null
                    || !request.token.equals(shown.token)) {
                dialog.dismiss(); return;
            }
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                    model.editForm(request.token, text.toString(), check.isChecked());
                }
                @Override public void afterTextChanged(Editable text) { }
            });
            check.setOnCheckedChangeListener((button, checked) -> model.editForm(request.token,
                    input.getText() == null ? "" : input.getText().toString(), checked));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                FormRequest current = model.form();
                if (current == null || !current.token.equals(request.token)) return;
                try { submitForm(current); }
                catch (IllegalArgumentException | ArithmeticException exception) {
                    model.formError(current.token, exception.getMessage(), null);
                    input.requestFocus();
                }
            });
            updateFormControls(shown);
        });
        dialog.setOnDismissListener(ignored -> {
            field.removeOnLayoutChangeListener(revealError);
            scrollingForm.removeOnLayoutChangeListener(revealError);
            if (inputDialog != dialog) return;
            boolean owned = request.token.equals(activeFormToken);
            inputError = null; inputDialog = null; activeFormToken = null;
            if (owned && !destroyingForms && !isChangingConfigurations()) model.finishForm(request.token);
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private int formHeading(FormRequest.Kind kind) {
        switch (kind) {
            case ADD_PLAYER: return R.string.add_player;
            case RENAME_PLAYER: case RENAME_SAVED: return R.string.rename;
            case BID: return R.string.enter_bid;
            case SCORE: return R.string.correct_score;
            case SEARCH: return R.string.search_saved;
            default: return R.string.add_saved;
        }
    }

    private void updateFormControls(FormRequest request) {
        if (inputDialog == null || !inputDialog.isShowing() || inputError == null
                || !request.token.equals(activeFormToken)) return;
        inputError.setError(request.error);
        if (request.error != null) revealFormError(inputError);
        inputDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!model.submittingForm(request.token));
        android.widget.Button existing = inputDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        existing.setVisibility(request.kind == FormRequest.Kind.ADD_PLAYER
                && request.existingProfileId != null ? View.VISIBLE : View.GONE);
        existing.setOnClickListener(view -> {
            FormRequest current = model.form();
            if (current == null || !current.token.equals(request.token)) return;
            try { useExistingProfile(current); }
            catch (IllegalArgumentException exception) { model.formError(current.token, exception.getMessage(), null); }
        });
    }

    private void revealFormError(TextInputLayout field) {
        if (field.getError() == null) return;
        androidx.core.view.OneShotPreDrawListener.add(field, () -> field.post(() -> {
            if (field != inputError || inputDialog == null || !inputDialog.isShowing()
                    || field.getError() == null) return;
            View error = field.findViewById(com.google.android.material.R.id.textinput_error);
            if (error == null) return;
            android.graphics.Rect bounds = new android.graphics.Rect();
            error.getDrawingRect(bounds);
            error.requestRectangleOnScreen(bounds, true);
        }));
    }

    private void submitForm(FormRequest request) {
        if (model.submittingForm(request.token)) return;
        if (request.kind == FormRequest.Kind.SEARCH) {
            search = request.text; model.finishForm(request.token); render(); return;
        }
        if (request.kind == FormRequest.Kind.SCORE) {
            long total;
            try { total = Long.parseLong(request.text); }
            catch (NumberFormatException exception) { throw new IllegalArgumentException(getString(R.string.score_invalid)); }
            GameRules.validateScoreTotal(total);
            model.runForm(request, () -> model.repository.correctScore(request.playerId, request.expectedScore, total));
            return;
        }
        if (request.kind == FormRequest.Kind.BID) {
            int bid;
            try { bid = Integer.parseInt(request.text); }
            catch (NumberFormatException exception) { throw new IllegalArgumentException("Enter a prediction from 0 to " + request.maximumBid + "."); }
            GameRules.validateBid(request.maximumBid, bid);
            model.runForm(request, () -> model.repository.submitBid(request.roundId, request.playerId, bid));
            return;
        }
        String display = Names.displayName(request.text);
        if (request.kind == FormRequest.Kind.ADD_SAVED) {
            model.runForm(request, () -> model.repository.addSavedPlayer(display)); return;
        }
        if (request.kind == FormRequest.Kind.RENAME_SAVED) {
            model.runForm(request, () -> model.repository.renameSavedPlayer(request.playerId, display)); return;
        }
        List<PlayerSpec> players = request.manage ? model.staged() : draftPlayers();
        if (request.manage && (model.stagedRoundId() == null || model.stagedRoundId() != request.roundId)) {
            throw new IllegalArgumentException("This player editor is no longer current. Reopen Manage players.");
        }
        for (PlayerSpec player : players) {
            if ((request.kind == FormRequest.Kind.ADD_PLAYER || player.id != request.playerId)
                    && Names.key(player.name).equals(Names.key(display))) {
                throw new IllegalArgumentException(getString(R.string.name_conflict));
            }
        }
        if (request.kind == FormRequest.Kind.RENAME_PLAYER) {
            if (request.manage) {
                int position = -1;
                for (int index = 0; index < players.size(); index++) if (players.get(index).id == request.playerId) position = index;
                if (position < 0) throw new IllegalArgumentException("This player is no longer in this roster.");
                PlayerSpec prior = players.get(position);
                players.set(position, new PlayerSpec(prior.id, display, prior.savedPlayerId, prior.saveForFuture));
                model.stage(players); model.finishForm(request.token); render();
            } else model.runForm(request, () -> model.repository.renameDraftPlayer(request.gameId, request.playerId, display));
            return;
        }
        if (players.size() >= GameRules.MAX_PLAYERS) throw new IllegalArgumentException("A game supports at most 52 players.");
        if (request.saveFuture) {
            for (Records.SavedPlayer profile : state.savedPlayers) {
                if (profile.nameKey.equals(Names.key(display))) {
                    model.formError(request.token, getString(R.string.saved_exists), profile.id);
                    return;
                }
            }
        }
        if (request.manage) {
            players.add(new PlayerSpec(-System.nanoTime(), display, null, request.saveFuture));
            model.stage(players); model.finishForm(request.token); render();
        } else model.runForm(request, () -> model.repository.addPlayerToDraft(request.gameId, display, request.saveFuture));
    }

    private void useExistingProfile(FormRequest request) {
        if (request.existingProfileId == null) return;
        if (!request.manage) {
            long profileId = request.existingProfileId;
            model.runForm(request, () -> model.repository.addSavedPlayersToDraft(request.gameId,
                    java.util.Collections.singletonList(profileId)));
            return;
        }
        List<PlayerSpec> players = model.staged();
        Records.SavedPlayer selected = null;
        for (Records.SavedPlayer profile : state.savedPlayers) if (profile.id == request.existingProfileId) selected = profile;
        if (selected == null) throw new IllegalArgumentException("This saved player is no longer available.");
        for (PlayerSpec player : players) if (Names.key(player.name).equals(selected.nameKey)) {
            throw new IllegalArgumentException(getString(R.string.name_conflict));
        }
        if (players.size() >= GameRules.MAX_PLAYERS) throw new IllegalArgumentException("A game supports at most 52 players.");
        players.add(new PlayerSpec(-System.nanoTime(), selected.name, selected.id));
        model.stage(players); model.finishForm(request.token); render();
    }
    private void confirm(int heading, int message, int positive, Runnable run) {
        new MaterialAlertDialogBuilder(this).setTitle(heading).setMessage(message).setNegativeButton(R.string.cancel, null).setPositiveButton(positive, (dialog, which) -> run.run()).show();
    }
}
