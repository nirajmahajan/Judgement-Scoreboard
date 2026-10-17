package android.example.judgement.ui;

import android.example.judgement.domain.ScoringStrategy;
import android.example.judgement.data.Records;
import android.os.Bundle;
import java.util.UUID;

/** Saved editing state and original command target, independent of an Activity or row view. */
final class StrategyRequest {
    enum Target { DRAFT, ROUND }
    final String token;
    final Target target;
    final long targetId;
    final String returnScreen;
    final ScoringStrategy.Preset preset;
    final String zero, base, perTrick;
    final String name;
    final long profileId;
    final boolean customEditor;
    final boolean bonus, validate;
    final String error;

    StrategyRequest(Target target, long id, String returnScreen, ScoringStrategy strategy) {
        this(UUID.randomUUID().toString(), target, id, returnScreen, strategy.preset,
                Long.toString(strategy.zeroReward), Long.toString(strategy.exactBase),
                Long.toString(strategy.perTrick), strategy.majorityBonus, false, null, strategy.name == null ? "" : strategy.name, 0, false);
    }
    private StrategyRequest(String token, Target target, long id, String returnScreen,
            ScoringStrategy.Preset preset, String zero, String base, String perTrick,
            boolean bonus, boolean validate, String error, String name, long profileId, boolean customEditor) {
        this.token = token; this.target = target; this.targetId = id;
        this.returnScreen = returnScreen; this.preset = preset;
        this.zero = zero; this.base = base; this.perTrick = perTrick;
        this.bonus = bonus; this.validate = validate; this.error = error;
        this.name = name; this.profileId = profileId; this.customEditor = customEditor;
    }
    StrategyRequest choose(ScoringStrategy.Preset next) {
        return new StrategyRequest(token, target, targetId, returnScreen, next,
                zero, base, perTrick, bonus, false, null, "", 0, next == ScoringStrategy.Preset.CUSTOM);
    }
    StrategyRequest edit(String zero, String base, String perTrick, boolean bonus) {
        return new StrategyRequest(token, target, targetId, returnScreen, preset,
                zero, base, perTrick, bonus, validate, null, name, profileId, customEditor);
    }
    StrategyRequest failure(String message) {
        return new StrategyRequest(token, target, targetId, returnScreen, preset,
                zero, base, perTrick, bonus, true, message, name, profileId, customEditor);
    }
    StrategyRequest select(ScoringStrategy strategy) {
        return new StrategyRequest(token, target, targetId, returnScreen, strategy.preset,
                Long.toString(strategy.zeroReward), Long.toString(strategy.exactBase),
                Long.toString(strategy.perTrick), strategy.majorityBonus, false, null,
                strategy.name == null ? "" : strategy.name, 0, false);
    }
    StrategyRequest createCustom() {
        return new StrategyRequest(token, target, targetId, returnScreen, ScoringStrategy.Preset.CUSTOM,
                "10", "10", "1", false, false, null, "", 0, true);
    }
    StrategyRequest modify(Records.SavedStrategy profile) {
        ScoringStrategy strategy = ScoringStrategy.decode(profile.scoringConfig);
        return new StrategyRequest(token, target, targetId, returnScreen, ScoringStrategy.Preset.CUSTOM,
                Long.toString(strategy.zeroReward), Long.toString(strategy.exactBase),
                Long.toString(strategy.perTrick), strategy.majorityBonus, false, null,
                profile.name, profile.id, true);
    }
    StrategyRequest named(String value) {
        return new StrategyRequest(token, target, targetId, returnScreen, preset,
                zero, base, perTrick, bonus, validate, null, value, profileId, customEditor);
    }
    ScoringStrategy validatedStrategy() {
        ScoringStrategy rules = strategy();
        return customEditor ? rules.withName(name) : rules;
    }
    ScoringStrategy strategy() {
        if (preset == ScoringStrategy.Preset.DEFAULT) return ScoringStrategy.defaults();
        if (preset == ScoringStrategy.Preset.AGGRESSIVE) return ScoringStrategy.aggressive();
        ScoringStrategy rules = ScoringStrategy.custom(points(zero), points(base), points(perTrick), bonus);
        return customEditor || name.isEmpty() ? rules : rules.withName(name);
    }
    static long points(String value) {
        try {
            String trimmed = value.trim();
            if (!trimmed.matches("[0-9]+")) throw new NumberFormatException();
            return Long.parseLong(trimmed);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Enter a whole number from 0 to 9223372036854775807.");
        }
    }
    Bundle toBundle() {
        Bundle value = new Bundle();
        value.putString("token", token); value.putString("target", target.name());
        value.putLong("targetId", targetId); value.putString("return", returnScreen);
        value.putString("preset", preset.name()); value.putString("zero", zero);
        value.putString("base", base); value.putString("perTrick", perTrick);
        value.putBoolean("bonus", bonus); value.putBoolean("validate", validate);
        value.putString("error", error); value.putString("name", name);
        value.putLong("profileId", profileId); value.putBoolean("customEditor", customEditor);
        return value;
    }
    static StrategyRequest fromBundle(Bundle value) {
        if (value == null) return null;
        return new StrategyRequest(value.getString("token"), Target.valueOf(value.getString("target")),
                value.getLong("targetId"), value.getString("return"),
                ScoringStrategy.Preset.valueOf(value.getString("preset")), value.getString("zero"),
                value.getString("base"), value.getString("perTrick"), value.getBoolean("bonus"),
                value.getBoolean("validate"), value.getString("error"), value.getString("name", ""),
                value.getLong("profileId"), value.getBoolean("customEditor"));
    }
}
