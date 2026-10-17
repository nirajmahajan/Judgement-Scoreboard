package android.example.judgement.ui;

import android.example.judgement.R;
import android.example.judgement.domain.ScoringStrategy;
import android.view.View;

/** Setup and game settings share the same persisted strategy library. */
final class SetupStrategyHeader {
    private SetupStrategyHeader() { }

    static void bind(View section, ScoreViewModel model, long draftId, ScoringStrategy strategy) {
        StrategyDropdown.bind(section.findViewById(R.id.setup_strategy_choices), strategy,
                model.state.getValue().savedStrategies, new StrategyDropdown.Listener() {
                    @Override public void select(ScoringStrategy next) {
                        model.run(() -> model.repository.updateDraftScoring(draftId, next));
                    }
                    @Override public void create() {
                        model.beginStrategy(new StrategyRequest(StrategyRequest.Target.DRAFT,
                                draftId, "PLAYERS", ScoringStrategy.defaults()).createCustom());
                    }
                    @Override public void modify(android.example.judgement.data.Records.SavedStrategy saved) {
                        model.beginStrategy(new StrategyRequest(StrategyRequest.Target.DRAFT,
                                draftId, "PLAYERS", strategy).modify(saved));
                    }
                });
    }
}
