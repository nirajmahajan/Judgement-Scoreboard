package android.example.judgement.ui;

import android.example.judgement.R;
import android.example.judgement.data.DraftSpec;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.data.Records;
import android.example.judgement.data.Snapshot;
import android.example.judgement.domain.GameRules;
import android.example.judgement.domain.Names;
import android.example.judgement.domain.Outcome;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
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
import java.util.List;

/** Screens render immutable repository snapshots; UI callbacks always carry record IDs. */
public final class MainActivity extends AppCompatActivity {
    private ScoreViewModel model;
    private Snapshot state;
    private MaterialToolbar toolbar;
    private RecyclerView list;
    private BottomNavigationView navigation;
    private ScoreAdapter adapter;
    private RosterAdapter roster;
    private ChromeAdapter headerAdapter, footerAdapter;
    private RecyclerView.Adapter<?> contentAdapter;
    private String headingText = "", descriptionText = "", errorText;
    private final List<FooterAction> footerActions = new ArrayList<>();
    private String renderedScreen = "";
    private String pickerReturn = "SETTINGS";
    private TextInputLayout inputError;
    private AlertDialog inputDialog;
    private String activeFormToken;
    private boolean destroyingForms;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            pickerReturn = savedInstanceState.getString("pickerReturn", "SETTINGS");
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean light = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(light);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(light);
        setContentView(R.layout.score_activity);
        View root = findViewById(R.id.score_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });
        list = findViewById(R.id.screen_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        headingText = getString(R.string.loading);
        headerAdapter = new ChromeAdapter(R.layout.score_header, this::bindHeader);
        footerAdapter = new ChromeAdapter(R.layout.score_footer, this::bindFooter);
        toolbar = findViewById(R.id.toolbar);
        navigation = findViewById(R.id.bottom_navigation);
        adapter = new ScoreAdapter(); setContent(adapter);
        model = new ViewModelProvider(this).get(ScoreViewModel.class);
        navigation.setOnItemSelectedListener(item -> {
            String next = item.getItemId() == R.id.tab_history ? "HISTORY" : item.getItemId() == R.id.tab_scoreboard ? "SCOREBOARD" : "ROUND";
            if (!next.equals(model.tab())) { model.tab(next); render(); }
            return true;
        });
        toolbar.setNavigationOnClickListener(v -> back());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        model.state.observe(this, snapshot -> { state = snapshot; render(); restoreForm(model.form()); });
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
        outState.putString("pickerReturn", pickerReturn);
        super.onSaveInstanceState(outState);
    }
    @Override protected void onDestroy() {
        destroyingForms = true;
        if (inputDialog != null) inputDialog.dismiss();
        if (roster != null) roster.detach();
        super.onDestroy();
    }

    private void open(String screen) { model.screen(screen); render(); }
    private void back() {
        String screen = model.screen();
        if ("MANAGE".equals(screen)) {
            confirm(R.string.discard_title, R.string.discard_message, R.string.discard, () -> open("GAME"));
        } else if ("SETTINGS".equals(screen)) open("PLAYERS");
        else if ("PICK_DEALER".equals(screen)) open(pickerReturn);
        else if ("RESTORE".equals(screen)) open("MANAGE");
        else if ("PICK_SCORE".equals(screen)) open("GAME");
        else if ("HOME".equals(screen)) finish();
        else open("HOME");
    }
    private void render() {
        if (state == null) return;
        String screen = model.screen();
        if ("GAME".equals(screen) && state.game == null) { model.screen("HOME"); screen = "HOME"; }
        boolean changed = !screen.equals(renderedScreen);
        Parcelable scroll = changed ? null : list.getLayoutManager().onSaveInstanceState();
        if (changed) {
            if (roster != null) { roster.detach(); roster = null; }
            adapter = new ScoreAdapter(); setContent(adapter); renderedScreen = screen;
        }
        footerActions.clear();
        boolean game = "GAME".equals(screen);
        navigation.setVisibility(game ? View.VISIBLE : View.GONE);
        toolbar.getMenu().clear();
        if (game) {
            toolbar.getMenu().add(R.string.options).setOnMenuItemClickListener(item -> { options(); return true; });
            int tab = "HISTORY".equals(model.tab()) ? R.id.tab_history : "SCOREBOARD".equals(model.tab()) ? R.id.tab_scoreboard : R.id.tab_round;
            navigation.getMenu().findItem(tab).setChecked(true);
        }
        toolbar.setNavigationIcon("HOME".equals(screen) ? null : androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_score_back));
        toolbar.setNavigationContentDescription(R.string.back);
        switch (screen) {
            case "PLAYERS": players(false); break;
            case "MANAGE": players(true); break;
            case "SETTINGS": settings(); break;
            case "PICK_DEALER": pickDealer(); break;
            case "PICK_SCORE": pickScore(); break;
            case "RESTORE": restorePlayers(); break;
            case "HELP": header(getString(R.string.help_title), ""); rows(new ScoreAdapter.Row(1, getString(R.string.help_title), getString(R.string.help_body))); break;
            case "RECOVERY": recovery(); break;
            case "GAME": game(); break;
            default: home(); break;
        }
        headerAdapter.notifyItemChanged(0); footerAdapter.notifyItemChanged(0);
        if (scroll != null) list.getLayoutManager().onRestoreInstanceState(scroll);
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
        TextView heading = view.findViewById(R.id.screen_title); heading.setText(headingText); ViewCompat.setAccessibilityHeading(heading, true);
        TextView description = view.findViewById(R.id.screen_description); description.setText(descriptionText); description.setVisibility(descriptionText.isEmpty() ? View.GONE : View.VISIBLE);
        TextView error = view.findViewById(R.id.inline_error); error.setText(errorText); error.setVisibility(errorText == null ? View.GONE : View.VISIBLE);
    }
    private void bindFooter(View view) {
        LinearLayout footer = (LinearLayout) view; footer.removeAllViews();
        for (FooterAction action : footerActions) {
            MaterialButton button = new MaterialButton(this);
            button.setText(action.label); button.setId(action.id); button.setMinHeight(dp(48)); button.setMaxLines(10);
            button.setSingleLine(false); button.setEnabled(action.enabled); button.setOnClickListener(v -> action.run.run());
            footer.addView(button, new LinearLayout.LayoutParams(-1, -2));
        }
    }
    private static final class FooterAction {
        final int label, id; final Runnable run; boolean enabled = true;
        FooterAction(int label, int id, Runnable run) { this.label = label; this.id = id; this.run = run; }
        void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
    private FooterAction action(int label, int id, Runnable run) {
        FooterAction action = new FooterAction(label, id, run); footerActions.add(action); return action;
    }
    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private void home() {
        header(getString(R.string.home_title), getString(R.string.home_description));
        List<ScoreAdapter.Row> rows = new ArrayList<>();
        if (state.game != null) rows.add(new ScoreAdapter.Row(1, getString("COMPLETE".equals(state.game.status) ? R.string.completed_results : R.string.resume_game), "")
                .action(getString("COMPLETE".equals(state.game.status) ? R.string.completed_results : R.string.resume_game), () -> open("GAME")));
        if (state.draft != null) rows.add(new ScoreAdapter.Row(2, getString(R.string.continue_setup), "").action(getString(R.string.continue_setup), () -> open("PLAYERS")));
        rows.add(new ScoreAdapter.Row(4, getString(R.string.help), "").action(getString(R.string.help), () -> open("HELP")));
        if (!state.recoveries.isEmpty()) rows.add(new ScoreAdapter.Row(5, getString(R.string.recovery_title), "").action(getString(R.string.recovery_title), () -> open("RECOVERY")));
        rows(rows);
        action(R.string.new_game, R.id.primary_action, () -> {
            if (state.draft != null) { open("PLAYERS"); return; }
            model.run(() -> model.repository.saveDraft(new DraftSpec(0, new ArrayList<>(), "ASCENDING", "FIXED", null)), () -> open("PLAYERS"));
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
        header(getString(manage ? R.string.manage_players : R.string.setup_players), getString(manage ? R.string.manage_description : R.string.setup_description));
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
        if (manage) {
            action(R.string.choose_dealer, View.generateViewId(), () -> { pickerReturn = "MANAGE"; open("PICK_DEALER"); });
            if (!state.inactivePlayers.isEmpty()) action(R.string.restore_players, View.generateViewId(), () -> open("RESTORE"));
            action(R.string.apply, R.id.primary_action, this::applyRoster);
        } else action(R.string.next_settings, R.id.primary_action, () -> {
            try {
                GameRules.validatePlayerCount(state.draftPlayers.size());
                if (state.draft.dealerId == null) {
                    long draftId = state.draft.id, dealerId = state.draftPlayers.get(state.draftPlayers.size() - 1).id;
                    model.run(() -> model.repository.chooseDraftDealer(draftId, dealerId), () -> open("SETTINGS"));
                } else open("SETTINGS");
            }
            catch (IllegalArgumentException exception) { model.error.setValue(exception.getMessage()); }
        });
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
        rows(new ScoreAdapter.Row(1, getString(R.string.direction), getString("ASCENDING".equals(state.draft.direction) ? R.string.ascending : R.string.descending))
                        .action(getString(R.string.ascending), () -> config("ASCENDING", null))
                        .action(getString(R.string.descending), () -> config("DESCENDING", null)),
                new ScoreAdapter.Row(2, getString(R.string.trump_mode), getString("FIXED".equals(state.draft.trumpMode) ? R.string.fixed_trump : R.string.custom_trump))
                        .action(getString(R.string.fixed_trump), () -> config(null, "FIXED"))
                        .action(getString(R.string.custom_trump), () -> config(null, "CUSTOM")),
                new ScoreAdapter.Row(3, getString(R.string.choose_dealer), playerName(state.draftPlayers, state.draft.dealerId))
                        .action(getString(R.string.choose_dealer), () -> { pickerReturn = "SETTINGS"; open("PICK_DEALER"); }));
        action(R.string.start_game, R.id.primary_action, () -> {
            long draftId = state.draft.id;
            Runnable start = () -> model.run(() -> model.repository.startGame(draftId), () -> { model.tab("ROUND"); open("GAME"); });
            if (state.game != null) confirm(R.string.replace_game_title, R.string.replace_game_message, R.string.start_game, start); else start.run();
        });
    }
    private void config(String direction, String trump) {
        long draftId = state.draft.id;
        model.run(() -> model.repository.updateDraftSettings(draftId, direction, trump));
    }
    private void pickDealer() {
        header(getString(R.string.choose_dealer), "");
        boolean manage = "MANAGE".equals(pickerReturn);
        List<PlayerSpec> players = manage ? model.staged() : draftPlayers();
        List<ScoreAdapter.Row> rows = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            PlayerSpec player = players.get(i);
            rows.add(new ScoreAdapter.Row(player.id, player.name, getString(R.string.seat_format, i + 1)).action(getString(R.string.choose_dealer), () -> {
                if (manage) { model.stagedDealer(player.id); open("MANAGE"); }
                else {
                    long draftId = state.draft.id;
                    model.run(() -> model.repository.chooseDraftDealer(draftId, player.id), () -> open("SETTINGS"));
                }
            }));
        }
        rows(rows);
    }
    private String playerName(List<Records.GamePlayer> players, Long id) {
        if (id != null) for (Records.GamePlayer player : players) if (player.id == id) return player.name;
        return players.isEmpty() ? "" : players.get(players.size() - 1).name;
    }
    private void game() {
        if ("SCOREBOARD".equals(model.tab())) { scoreboard(); return; }
        if ("HISTORY".equals(model.tab())) { history(); return; }
        if (state.round == null || "COMPLETE".equals(state.game.status)) {
            header(getString(R.string.game_complete), getString(R.string.complete_description)); scoreboardRows(); return;
        }
        Records.Round round = state.round;
        String trump = "CUSTOM".equals(state.game.trumpMode) ? getString(R.string.trump_at_table) : getString(R.string.trump_format, getString(new int[]{R.string.spades, R.string.diamonds, R.string.clubs, R.string.hearts}[(round.number - 1) % 4]));
        header(roundHeading(round.number, round.cards),
                getString(R.string.round_description, getString(R.string.dealer_format, playerName(state.players, round.dealerId)), trump));
        if ("BIDDING".equals(round.phase)) bidding(round); else results(round);
    }
    private void bidding(Records.Round round) {
        List<ScoreAdapter.Row> rows = new ArrayList<>();
        int firstMissing = state.roundPlayers.size();
        for (int i = 0; i < state.roundPlayers.size(); i++) if (state.roundPlayers.get(i).bid == null) { firstMissing = i; break; }
        for (int i = 0; i < state.roundPlayers.size(); i++) {
            Records.RoundPlayer player = state.roundPlayers.get(i);
            boolean current = i == firstMissing;
            ScoreAdapter.Row row = new ScoreAdapter.Row(player.playerId, player.nameSnapshot,
                    player.bid == null ? getString(current ? R.string.current_bidder : R.string.bid_pending) : getString(R.string.bid_format, player.bid));
            if (current) {
                row.action(getString(R.string.enter_bid), () -> bidDialog(round, player));
                // Quick bids carry this bidder's stable ID, independent of attached row views.
                for (int bid = 0; bid <= Math.min(round.cards, 3); bid++) {
                    final int value = bid;
                    row.action(getString(R.string.quick_bid, bid), () -> model.run(() -> model.repository.submitBid(round.id, player.playerId, value)));
                }
            } else if (i == firstMissing - 1) row.action(getString(R.string.edit_bid), () -> bidDialog(round, player));
            rows.add(row);
        }
        rows(rows);
        action(R.string.record_bids, R.id.primary_action, () -> model.run(() -> model.repository.recordBids(round.id))).setEnabled(firstMissing == state.roundPlayers.size());
    }
    private void bidDialog(Records.Round round, Records.RoundPlayer player) {
        model.beginForm(new FormRequest(FormRequest.Kind.BID,
                player.bid == null ? "" : player.bid.toString(), false,
                round.gameId, player.playerId, round.id, 0, round.cards));
    }
    private void results(Records.Round round) {
        List<ScoreAdapter.Row> rows = new ArrayList<>();
        for (Records.RoundPlayer player : state.roundPlayers) {
            String selection = player.outcome == null ? getString(R.string.outcome_pending) : getString("WON".equals(player.outcome) ? R.string.won : R.string.lost);
            rows.add(new ScoreAdapter.Row(player.playerId, player.nameSnapshot, getString(R.string.outcome_detail, player.bid, selection))
                    .action(getString(R.string.won), !"WON".equals(player.outcome), () -> model.run(() -> model.repository.setOutcome(round.id, player.playerId, Outcome.WON)))
                    .action(getString(R.string.lost), !"LOST".equals(player.outcome), () -> model.run(() -> model.repository.setOutcome(round.id, player.playerId, Outcome.LOST))));
        }
        rows(rows);
        action(R.string.finish_round, R.id.primary_action, () -> model.run(() -> model.repository.completeRound(round.id)));
    }
    private String points(long total) { return getResources().getQuantityString(R.plurals.score_points, total == 1 ? 1 : 2, total); }
    private String roundHeading(int number, int cards) { return getResources().getQuantityString(R.plurals.round_heading, cards, number, cards); }
    private void scoreboard() { header(getString(R.string.scoreboard_tab), ""); scoreboardRows(); }
    private void scoreboardRows() {
        List<Records.GamePlayer> players = new ArrayList<>(state.players);
        players.addAll(state.inactivePlayers);
        players.sort(Comparator.<Records.GamePlayer>comparingLong(player -> state.total(player.id)).reversed().thenComparing(player -> !player.active).thenComparingInt(player -> player.seat).thenComparingLong(player -> player.id));
        List<ScoreAdapter.Row> rows = new ArrayList<>();
        int rank = 0; Long previous = null;
        for (Records.GamePlayer player : players) {
            long total = state.total(player.id); if (previous == null || previous != total) rank++;
            previous = total;
            rows.add(new ScoreAdapter.Row(player.id, getString(R.string.ranking_format, rank, player.name), player.active ? points(total) : getString(R.string.removed_player_score, points(total))));
        }
        rows(rows);
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
                        getString("WON".equals(player.outcome) ? R.string.won : R.string.lost), GameRules.score(player.bid, Outcome.valueOf(player.outcome))));
            }
            entries.add(new HistoryEntry(history.round.completedAt == null ? history.round.createdAt : history.round.completedAt,
                    new ScoreAdapter.Row(history.round.id, roundHeading(history.round.number, history.round.cards), detail.toString())));
        }
        for (Records.ScoreAdjustment adjustment : state.adjustments) entries.add(new HistoryEntry(adjustment.createdAt, new ScoreAdapter.Row(-adjustment.id - 1,
                getString(R.string.adjustment_title, adjustment.nameSnapshot), getResources().getQuantityString(R.plurals.adjustment_detail, Math.addExact(adjustment.previousTotal, adjustment.delta) == 1 ? 1 : 2, adjustment.previousTotal, Math.addExact(adjustment.previousTotal, adjustment.delta)))));
        entries.sort(Comparator.comparingLong(entry -> entry.time));
        List<ScoreAdapter.Row> rows = new ArrayList<>(); for (HistoryEntry entry : entries) rows.add(entry.row);
        rows(rows);
    }
    private static final class HistoryEntry {
        final long time; final ScoreAdapter.Row row;
        HistoryEntry(long time, ScoreAdapter.Row row) { this.time = time; this.row = row; }
    }
    private void options() {
        if (state.round == null || "COMPLETE".equals(state.game.status)) {
            int[] labels = {R.string.correct_score, R.string.new_game, R.string.help, R.string.home};
            String[] text = new String[labels.length]; for (int i = 0; i < labels.length; i++) text[i] = getString(labels[i]);
            new MaterialAlertDialogBuilder(this).setTitle(R.string.options).setItems(text, (dialog, which) -> {
                if (which == 0) open("PICK_SCORE");
                else if (which == 1) newSetup();
                else open(which == 2 ? "HELP" : "HOME");
            }).show();
            return;
        }
        final int[] labels = {R.string.manage_players, R.string.change_dealer, R.string.correct_score, R.string.restart_round, R.string.new_game, R.string.help, R.string.home};
        String[] text = new String[labels.length]; for (int i = 0; i < labels.length; i++) text[i] = getString(labels[i]);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.options).setItems(text, (dialog, which) -> {
            switch (which) {
                case 0: case 1:
                    if (state.round == null) return;
                    List<PlayerSpec> players = new ArrayList<>(); for (Records.GamePlayer player : state.players) players.add(new PlayerSpec(player.id, player.name, player.savedPlayerId));
                    model.stage(players); model.stagedDealer(state.round.dealerId);
                    model.stagedRoundId(state.round.id);
                    if (which == 1) { pickerReturn = "MANAGE"; open("PICK_DEALER"); } else open("MANAGE");
                    break;
                case 2: open("PICK_SCORE"); break;
                case 3: if (state.round != null) {
                    long id = state.round.id;
                    confirm(R.string.restart_title, R.string.restart_message, R.string.restart, () -> model.run(() -> model.repository.restartRound(id)));
                } break;
                case 4:
                    newSetup(); break;
                case 5: open("HELP"); break;
                default: open("HOME"); break;
            }
        }).show();
    }
    private void newSetup() {
        if (state.draft == null) model.run(() -> model.repository.saveDraft(new DraftSpec(0, new ArrayList<>(), "ASCENDING", "FIXED", null)), () -> open("PLAYERS"));
        else open("PLAYERS");
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
        try { GameRules.validateCards(state.round.cards, players.size()); }
        catch (IllegalArgumentException exception) { model.error.setValue(exception.getMessage()); return; }
        boolean structure = players.size() != state.players.size();
        for (int i = 0; !structure && i < players.size(); i++) structure = players.get(i).id != state.players.get(i).id;
        structure |= dealer == null || dealer != state.round.dealerId;
        boolean started = false; for (Records.RoundPlayer player : state.roundPlayers) started |= player.bid != null;
        long roundId = stagedRoundId; long selected = dealer == null ? 0 : dealer;
        Runnable apply = () -> model.run(() -> model.repository.applyRoster(roundId, players, selected, true), () -> open("GAME"));
        if (structure && started) confirm(R.string.restart_title, R.string.restart_message, R.string.apply, apply); else apply.run();
    }
    private void restorePlayers() {
        header(getString(R.string.restore_players), "");
        List<ScoreAdapter.Row> rows = new ArrayList<>();
        for (Records.GamePlayer player : state.inactivePlayers) rows.add(new ScoreAdapter.Row(player.id, player.name, points(state.total(player.id)))
                .action(getString(R.string.restore), () -> {
                    List<PlayerSpec> players = model.staged(); players.add(new PlayerSpec(player.id, player.name, player.savedPlayerId)); model.stage(players); open("MANAGE");
                }));
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
        if (inputDialog != null && request.token.equals(activeFormToken)) {
            updateFormControls(request);
            return;
        }
        if (inputDialog != null) {
            activeFormToken = null;
            inputDialog.dismiss();
        }
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
        androidx.core.widget.NestedScrollView scrollingForm = new androidx.core.widget.NestedScrollView(this);
        scrollingForm.addView(form);
        input.setMaxLines(number ? 1 : 6);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle(heading).setView(scrollingForm).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> {
            inputError = field;
            inputDialog = dialog;
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                    model.editForm(request.token, text.toString());
                }
                @Override public void afterTextChanged(Editable text) { }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                FormRequest current = model.form();
                if (current == null || !current.token.equals(request.token)) return;
                try { submitForm(current); }
                catch (IllegalArgumentException | ArithmeticException exception) {
                    model.formError(current.token, exception.getMessage());
                    input.requestFocus();
                }
            });
            updateFormControls(request);
        });
        dialog.setOnDismissListener(ignored -> {
            if (!destroyingForms && !isChangingConfigurations()
                    && request.token.equals(activeFormToken)) model.finishForm(request.token);
            if (inputDialog == dialog) { inputError = null; inputDialog = null; activeFormToken = null; }
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private int formHeading(FormRequest.Kind kind) {
        switch (kind) {
            case ADD_PLAYER: return R.string.add_player;
            case RENAME_PLAYER: return R.string.rename;
            case BID: return R.string.enter_bid;
            case SCORE: return R.string.correct_score;
            default: throw new IllegalArgumentException("Unknown input form.");
        }
    }

    private void updateFormControls(FormRequest request) {
        if (inputDialog == null || !request.token.equals(activeFormToken)) return;
        inputError.setError(request.error);
        inputDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!model.submittingForm(request.token));
    }

    private void submitForm(FormRequest request) {
        if (model.submittingForm(request.token)) return;
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
            catch (NumberFormatException exception) { throw new IllegalArgumentException("Enter a bid from 0 to " + request.maximumBid + "."); }
            GameRules.validateBid(request.maximumBid, bid);
            model.runForm(request, () -> model.repository.submitBid(request.roundId, request.playerId, bid));
            return;
        }
        String display = Names.displayName(request.text);
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
                players.set(position, new PlayerSpec(prior.id, display, prior.savedPlayerId));
                model.stage(players); model.finishForm(request.token); render();
            } else model.runForm(request, () -> model.repository.renameDraftPlayer(request.gameId, request.playerId, display));
            return;
        }
        if (players.size() >= GameRules.MAX_PLAYERS) throw new IllegalArgumentException("A game supports at most 52 players.");
        if (request.manage) {
            players.add(new PlayerSpec(-System.nanoTime(), display));
            model.stage(players); model.finishForm(request.token); render();
        } else model.runForm(request, () -> model.repository.addPlayerToDraft(request.gameId, display, false));
    }


    private void confirm(int heading, int message, int positive, Runnable run) {
        new MaterialAlertDialogBuilder(this).setTitle(heading).setMessage(message).setNegativeButton(R.string.cancel, null).setPositiveButton(positive, (dialog, which) -> run.run()).show();
    }
}
