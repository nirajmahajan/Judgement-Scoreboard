package android.example.judgement.ui;

import android.example.judgement.domain.ScoringStrategy;
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
    final boolean bonus, validate;
    final String error;

    StrategyRequest(Target target, long id, String returnScreen, ScoringStrategy strategy) {
        this(UUID.randomUUID().toString(), target, id, returnScreen, strategy.preset,
                Long.toString(strategy.zeroReward), Long.toString(strategy.exactBase),
                Long.toString(strategy.perTrick), strategy.majorityBonus, false, null);
    }
    private StrategyRequest(String token, Target target, long id, String returnScreen,
            ScoringStrategy.Preset preset, String zero, String base, String perTrick,
            boolean bonus, boolean validate, String error) {
        this.token = token; this.target = target; this.targetId = id;
        this.returnScreen = returnScreen; this.preset = preset;
        this.zero = zero; this.base = base; this.perTrick = perTrick;
        this.bonus = bonus; this.validate = validate; this.error = error;
    }
    StrategyRequest choose(ScoringStrategy.Preset next) {
        return new StrategyRequest(token, target, targetId, returnScreen, next,
                zero, base, perTrick, bonus, false, null);
    }
    StrategyRequest edit(String zero, String base, String perTrick, boolean bonus) {
        return new StrategyRequest(token, target, targetId, returnScreen, preset,
                zero, base, perTrick, bonus, validate, null);
    }
    StrategyRequest failure(String message) {
        return new StrategyRequest(token, target, targetId, returnScreen, preset,
                zero, base, perTrick, bonus, true, message);
    }
    ScoringStrategy strategy() {
        if (preset == ScoringStrategy.Preset.DEFAULT) return ScoringStrategy.defaults();
        if (preset == ScoringStrategy.Preset.AGGRESSIVE) return ScoringStrategy.aggressive();
        return ScoringStrategy.custom(points(zero), points(base), points(perTrick), bonus);
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
        value.putString("error", error);
        return value;
    }
    static StrategyRequest fromBundle(Bundle value) {
        if (value == null) return null;
        return new StrategyRequest(value.getString("token"), Target.valueOf(value.getString("target")),
                value.getLong("targetId"), value.getString("return"),
                ScoringStrategy.Preset.valueOf(value.getString("preset")), value.getString("zero"),
                value.getString("base"), value.getString("perTrick"), value.getBoolean("bonus"),
                value.getBoolean("validate"), value.getString("error"));
    }
}
