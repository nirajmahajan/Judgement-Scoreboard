package android.example.judgement.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.data.ScoreRepository;
import android.example.judgement.data.Snapshot;
import android.example.judgement.data.RoundEditPreview;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ScoreViewModel extends AndroidViewModel {
    public final ScoreRepository repository;
    public final LiveData<Snapshot> state;
    public final MutableLiveData<String> error = new MutableLiveData<>();
    private final SavedStateHandle saved;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> submittingForms = new HashSet<>();
    public ScoreViewModel(@NonNull Application application, SavedStateHandle savedState) {
        super(application);
        saved = savedState;
        repository = new ScoreRepository(application);
        state = repository.observe();
    }
    public interface Command { void run(); }
    void beginSetup(long draftId, String roster) {
        saved.set("setupDraft", draftId); saved.set("setupRoster", roster);
        saved.set("setupDirectionChosen", false); saved.set("setupConfirmed", false);
        screen("SETUP_DIRECTION");
    }
    boolean setupConfirmed(long draftId, String roster) {
        Long owner = saved.get("setupDraft");
        return owner != null && owner == draftId && roster.equals(saved.get("setupRoster"))
                && Boolean.TRUE.equals(saved.get("setupConfirmed"));
    }
    void chooseSetupDirection(long draftId, String roster, String direction) {
        setupChoice(draftId, roster, () -> repository.updateDraftSettings(draftId, direction, null), false);
    }
    void chooseSetupDealer(long draftId, String roster, long dealerId) {
        if (!Boolean.TRUE.equals(saved.get("setupDirectionChosen"))) { screen("SETUP_DIRECTION"); return; }
        setupChoice(draftId, roster, () -> repository.chooseDraftDealer(draftId, dealerId), true);
    }
    private void setupChoice(long draftId, String roster, Command command, boolean dealer) {
        Long owner = saved.get("setupDraft");
        if (owner == null || owner != draftId || !roster.equals(saved.get("setupRoster"))) {
            screen("PLAYERS"); error.setValue("The players changed. Finalize the player list again."); return;
        }
        String token = "setup:" + draftId;
        if (!submittingForms.add(token)) return;
        error.setValue(null);
        repository.execute(() -> {
            try {
                command.run();
                boolean started = dealer && repository.snapshot().game == null;
                if (started) repository.startGame(draftId);
                main.post(() -> {
                    submittingForms.remove(token);
                    Long currentOwner = saved.get("setupDraft");
                    if (currentOwner == null || currentOwner != draftId || !roster.equals(saved.get("setupRoster"))) return;
                    if (!started && !(dealer ? "SETUP_DEALER" : "SETUP_DIRECTION").equals(screen())) return;
                    saved.set(dealer ? "setupConfirmed" : "setupDirectionChosen", true);
                    if (started) tab("ROUND");
                    screen(started ? "GAME" : dealer ? "CONFIRM_START" : "SETUP_DEALER");
                });
            } catch (RuntimeException exception) {
                main.post(() -> { submittingForms.remove(token); error.setValue(exception.getMessage() == null ? "Could not save this choice. Try again." : exception.getMessage()); });
            }
        });
    }
    Long confirmedSetupDraft() {
        return Boolean.TRUE.equals(saved.get("setupConfirmed")) ? saved.get("setupDraft") : null;
    }
    void startConfirmedSetup(long draftId) {
        Long owner = confirmedSetupDraft();
        if (owner == null || owner != draftId) { screen("PLAYERS"); return; }
        String token = "start:" + draftId;
        if (!submittingForms.add(token)) return;
        error.setValue(null);
        repository.execute(() -> {
            try {
                repository.startGame(draftId);
                main.post(() -> { submittingForms.remove(token); tab("ROUND"); screen("GAME"); });
            } catch (RuntimeException exception) {
                main.post(() -> { submittingForms.remove(token); error.setValue(exception.getMessage() == null
                        ? "Could not start this game. Try again." : exception.getMessage()); });
            }
        });
    }
    private Bundle bidInputs(long roundId) {
        Bundle inputs = saved.get("bidInputs");
        if (inputs == null || inputs.getLong("roundId") != roundId) {
            inputs = new Bundle(); inputs.putLong("roundId", roundId);
        }
        return inputs;
    }
    String bidInput(long roundId, long playerId, Integer bid) {
        return bidInputs(roundId).getString(Long.toString(playerId), bid == null ? "" : bid.toString());
    }
    void bidInput(long roundId, long playerId, String value) {
        Bundle inputs = new Bundle(bidInputs(roundId));
        inputs.putString(Long.toString(playerId), value); saved.set("bidInputs", inputs);
    }
    boolean editingBid(long roundId, long playerId) {
        Bundle inputs = bidInputs(roundId);
        return inputs.containsKey("editing") && inputs.getLong("editing") == playerId;
    }
    void editBid(long roundId, long playerId) {
        Bundle inputs = new Bundle(bidInputs(roundId));
        inputs.putLong("editing", playerId); saved.set("bidInputs", inputs);
    }
    final MutableLiveData<Long> bidSubmitted = new MutableLiveData<>();
    void submitInlineBid(long roundId, long playerId, String value) {
        final int bid;
        try { bid = Integer.parseInt(value.trim()); }
        catch (NumberFormatException exception) { error.setValue("Enter a valid prediction."); return; }
        error.setValue(null);
        repository.execute(() -> {
            try {
                repository.submitBid(roundId, playerId, bid);
                main.post(() -> {
                    Bundle inputs = new Bundle(bidInputs(roundId));
                    inputs.remove(Long.toString(playerId)); inputs.remove("editing");
                    saved.set("bidInputs", inputs); bidSubmitted.setValue(roundId);
                });
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
                main.post(() -> error.setValue(exception.getMessage()));
            }
        });
    }
    public void run(Command command) { run(command, null); }
    public void run(Command command, String nextScreen) { run(command, nextScreen, null); }
    public void run(Command command, String nextScreen, String nextTab) {
        error.setValue(null);
        repository.execute(() -> {
            try {
                command.run();
                if (nextScreen != null) main.post(() -> {
                    if (nextTab != null) tab(nextTab);
                    screen(nextScreen);
                });
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
                main.post(() -> error.setValue(exception.getMessage()));
            }
        });
    }
    @Override protected void onCleared() { repository.close(); }
    LiveData<String> screenState() { return saved.getLiveData("screen", "HOME"); }
    String screen() { String value = saved.get("screen"); return value == null ? "HOME" : value; }
    void screen(String screen) { saved.set("screen", screen); error.setValue(null); }
    String tab() { String value = saved.get("tab"); return value == null ? "ROUND" : value; }
    void tab(String value) { saved.set("tab", value); }
    LiveData<Bundle> formState() { return saved.getLiveData("form"); }
    FormRequest form() { return FormRequest.fromBundle(saved.get("form")); }
    void beginForm(FormRequest request) { saved.set("form", request.toBundle()); }
    void editForm(String token, String value, boolean saveFuture) {
        FormRequest current = form();
        if (current != null && current.token.equals(token)) beginForm(current.withInput(value, saveFuture));
    }
    void formError(String token, String message, Long profileId) {
        FormRequest current = form();
        if (current != null && current.token.equals(token)) beginForm(current.withError(message, profileId));
    }
    void finishForm(String token) {
        FormRequest current = form();
        if (current != null && current.token.equals(token)) saved.set("form", null);
    }
    boolean submittingForm(String token) { return submittingForms.contains(token); }
    void runForm(FormRequest request, Command command) {
        if (!submittingForms.add(request.token)) return;
        formError(request.token, null, null);
        repository.execute(() -> {
            try {
                command.run();
                main.post(() -> { submittingForms.remove(request.token); finishForm(request.token); });
            } catch (RuntimeException exception) {
                main.post(() -> {
                    submittingForms.remove(request.token);
                    Long existing = exception instanceof ScoreRepository.DuplicateSavedPlayerException
                            && request.kind == FormRequest.Kind.ADD_PLAYER
                            ? ((ScoreRepository.DuplicateSavedPlayerException) exception).existingId : null;
                    formError(request.token, exception.getMessage() == null
                            ? "Could not save. Try again." : exception.getMessage(), existing);
                });
            }
        });
    }
    LiveData<Bundle> strategyState() { return saved.getLiveData("strategyEditor"); }
    StrategyRequest strategyRequest() { return StrategyRequest.fromBundle(saved.get("strategyEditor")); }
    void beginStrategy(StrategyRequest request) {
        saved.set("strategyEditor", request.toBundle()); screen("SCORING");
    }
    void editStrategy(StrategyRequest request) {
        StrategyRequest current = strategyRequest();
        if (current != null && current.token.equals(request.token)) saved.set("strategyEditor", request.toBundle());
    }
    void strategyError(String token, String message) {
        StrategyRequest current = strategyRequest();
        if (current != null && current.token.equals(token)) editStrategy(current.failure(message));
    }
    boolean savingStrategy(String token) { return submittingForms.contains(token); }
    void finishStrategy(String token) {
        StrategyRequest current = strategyRequest();
        if (current != null && current.token.equals(token)) {
            screen(current.returnScreen); saved.set("strategyEditor", null);
        }
    }
    void saveStrategy(StrategyRequest request, android.example.judgement.domain.ScoringStrategy strategy) {
        if (!submittingForms.add(request.token)) return;
        editStrategy(request);
        repository.execute(() -> {
            try {
                if (request.target == StrategyRequest.Target.DRAFT) repository.updateDraftScoring(request.targetId, strategy);
                else repository.changeScoringStrategy(request.targetId, strategy);
                main.post(() -> { submittingForms.remove(request.token); finishStrategy(request.token); });
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
                main.post(() -> {
                    submittingForms.remove(request.token);
                    strategyError(request.token, exception.getMessage());
                });
            }
        });
    }
    void scoringHelp(String config, String returnScreen) {
        saved.set("scoringHelpConfig", config); saved.set("scoringHelpReturn", returnScreen);
    }
    String scoringHelpConfig() { return saved.get("scoringHelpConfig"); }
    String scoringHelpReturn() { String value = saved.get("scoringHelpReturn"); return value == null ? "HOME" : value; }
    static final class RoundPreview {
        final String token; final long generation; final RoundEditPreview value;
        RoundPreview(RoundEditRequest request, RoundEditPreview value) {
            token = request.token; generation = request.generation; this.value = value;
        }
    }
    final MutableLiveData<RoundPreview> roundPreview = new MutableLiveData<>();
    LiveData<Bundle> roundEditorState() { return saved.getLiveData("roundEditor"); }
    RoundEditRequest roundEditor() { return RoundEditRequest.fromBundle(saved.get("roundEditor")); }
    void beginRoundEditor(Snapshot.RoundHistory history) {
        saved.set("roundEditor", new RoundEditRequest(history.round, history.players).toBundle());
        roundPreview.setValue(null);
        screen("ROUND_EDIT");
    }
    void editRoundInput(RoundEditRequest request) {
        RoundEditRequest current = roundEditor();
        if (current == null || !current.token.equals(request.token)) return;
        saved.set("roundEditor", request.toBundle());
        if (current.generation != request.generation) roundPreview.setValue(null);
    }
    RoundEditPreview previewFor(RoundEditRequest request) {
        RoundPreview preview = roundPreview.getValue();
        return preview != null && preview.token.equals(request.token) && preview.generation == request.generation
                ? preview.value : null;
    }
    boolean roundEditorBusy(RoundEditRequest request) { return submittingForms.contains(request.token); }
    private boolean currentRoundInput(RoundEditRequest request) {
        RoundEditRequest current = roundEditor();
        return current != null && current.token.equals(request.token) && current.generation == request.generation;
    }
    void finishRoundEditor() {
        screen("ROUND_DETAILS"); saved.set("roundEditor", null); roundPreview.setValue(null);
    }
    void previewRound(RoundEditRequest request, boolean save) {
        RoundEditRequest validated = request.validated();
        editRoundInput(validated);
        final List<android.example.judgement.data.RoundPlayerEdit> edits;
        try { edits = validated.edits(); }
        catch (IllegalArgumentException exception) { editRoundInput(validated.withError(exception.getMessage())); return; }
        if (!submittingForms.add(request.token)) return;
        // Notify after the busy flag changes; an Activity recreated during this command sees the same request.
        editRoundInput(validated);
        repository.execute(() -> {
            try {
                if (save) repository.editRound(request.roundId, request.expectedRevision, edits);
                else {
                    RoundEditPreview result = repository.previewRoundEdit(request.roundId, request.expectedRevision, edits);
                    main.post(() -> {
                        submittingForms.remove(request.token);
                        if (currentRoundInput(request)) {
                            roundPreview.setValue(new RoundPreview(request, result)); editRoundInput(validated);
                        }
                    });
                    return;
                }
                main.post(() -> {
                    submittingForms.remove(request.token);
                    if (currentRoundInput(request)) finishRoundEditor();
                });
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
                main.post(() -> {
                    submittingForms.remove(request.token);
                    if (currentRoundInput(request)) {
                        roundPreview.setValue(null); editRoundInput(validated.withError(exception.getMessage()));
                    }
                });
            }
        });
    }
    void inspectRound(long id, String backTo) {
        saved.set("historyRoundId", id); saved.set("historyReturn", backTo); screen("ROUND_DETAILS");
    }
    Long historyRoundId() { return saved.get("historyRoundId"); }
    String historyReturn() { String value = saved.get("historyReturn"); return value == null ? "GAME" : value; }
    Set<Long> comparedPlayers(long gameId, List<Long> defaults) {
        long[] ids = saved.get("comparedPlayers"); Set<Long> result = new HashSet<>();
        Long owner = saved.get("comparedGameId");
        if (ids == null || owner == null || owner != gameId) result.addAll(defaults);
        else for (long id : ids) result.add(id);
        return result;
    }
    void comparedPlayers(long gameId, Set<Long> selected) {
        long[] values = new long[selected.size()]; int index = 0;
        for (long id : selected) values[index++] = id;
        saved.set("comparedGameId", gameId); saved.set("comparedPlayers", values);
    }
    Long stagedDealer() { return saved.get("stagedDealer"); }
    void stagedDealer(Long id) { saved.set("stagedDealer", id); }
    Long stagedRoundId() { return saved.get("stagedRoundId"); }
    void stagedRoundId(long id) { saved.set("stagedRoundId", id); }
    void stage(List<PlayerSpec> players) {
        JSONArray array = new JSONArray();
        try {
            for (PlayerSpec player : players) {
                JSONObject row = new JSONObject();
                row.put("id", player.id); row.put("name", player.name);
                row.put("profile", player.savedPlayerId == null ? JSONObject.NULL : player.savedPlayerId);
                row.put("saveForFuture", player.saveForFuture);
                array.put(row);
            }
        } catch (JSONException exception) { throw new IllegalStateException(exception); }
        saved.set("stagedPlayers", array.toString());
    }
    List<PlayerSpec> staged() {
        List<PlayerSpec> players = new ArrayList<>();
        String value = saved.get("stagedPlayers");
        if (value == null) return players;
        try {
            JSONArray array = new JSONArray(value);
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.getJSONObject(i);
                players.add(new PlayerSpec(row.getLong("id"), row.getString("name"),
                        row.isNull("profile") ? null : row.getLong("profile"),
                        row.optBoolean("saveForFuture", false)));
            }
        } catch (JSONException exception) { throw new IllegalStateException("Invalid staged roster", exception); }
        return players;
    }
}
