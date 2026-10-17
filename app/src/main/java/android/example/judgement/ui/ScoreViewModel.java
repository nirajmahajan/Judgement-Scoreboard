package android.example.judgement.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.data.ScoreRepository;
import android.example.judgement.data.Snapshot;
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
    public void run(Command command) { run(command, null); }
    public void run(Command command, Runnable success) {
        error.setValue(null);
        repository.execute(() -> {
            try {
                command.run();
                if (success != null) main.post(success);
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
                main.post(() -> error.setValue(exception.getMessage()));
            }
        });
    }
    @Override protected void onCleared() { repository.close(); }
    String screen() { String value = saved.get("screen"); return value == null ? "HOME" : value; }
    void screen(String screen) { saved.set("screen", screen); error.setValue(null); }
    String tab() { String value = saved.get("tab"); return value == null ? "ROUND" : value; }
    void tab(String value) { saved.set("tab", value); }
    LiveData<Bundle> formState() { return saved.getLiveData("form"); }
    FormRequest form() { return FormRequest.fromBundle(saved.get("form")); }
    void beginForm(FormRequest request) { saved.set("form", request.toBundle()); }
    void editForm(String token, String value) {
        FormRequest current = form();
        if (current != null && current.token.equals(token)) beginForm(current.withInput(value));
    }
    void formError(String token, String message) {
        FormRequest current = form();
        if (current != null && current.token.equals(token)) beginForm(current.withError(message));
    }
    void finishForm(String token) {
        FormRequest current = form();
        if (current != null && current.token.equals(token)) saved.set("form", null);
    }
    boolean submittingForm(String token) { return submittingForms.contains(token); }
    void runForm(FormRequest request, Command command) {
        if (!submittingForms.add(request.token)) return;
        formError(request.token, null);
        repository.execute(() -> {
            try {
                command.run();
                main.post(() -> { submittingForms.remove(request.token); finishForm(request.token); });
            } catch (RuntimeException exception) {
                main.post(() -> {
                    submittingForms.remove(request.token);
                    formError(request.token, exception.getMessage() == null
                            ? "Could not save. Try again." : exception.getMessage());
                });
            }
        });
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
                        row.isNull("profile") ? null : row.getLong("profile")));
            }
        } catch (JSONException exception) { throw new IllegalStateException("Invalid staged roster", exception); }
        return players;
    }
}
