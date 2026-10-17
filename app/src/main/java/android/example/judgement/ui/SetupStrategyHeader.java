package android.example.judgement.ui;

import android.example.judgement.R;
import android.example.judgement.domain.ScoringStrategy;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import com.google.android.material.color.MaterialColors;

/** Compact setup choices write the same draft rules used by the optional full editor. */
final class SetupStrategyHeader {
    private SetupStrategyHeader() { }

    static void bind(View section, ScoreViewModel model, long draftId, ScoringStrategy strategy) {
        RadioGroup choices = section.findViewById(R.id.setup_strategy_choices);
        choices.setOnCheckedChangeListener(null);
        choices.check(strategy.preset == ScoringStrategy.Preset.DEFAULT ? R.id.setup_strategy_default
                : strategy.preset == ScoringStrategy.Preset.AGGRESSIVE ? R.id.setup_strategy_aggressive : -1);
        choices.setOnCheckedChangeListener((group, selected) -> {
            if (selected != R.id.setup_strategy_default && selected != R.id.setup_strategy_aggressive) return;
            ScoringStrategy next = selected == R.id.setup_strategy_default
                    ? ScoringStrategy.defaults() : ScoringStrategy.aggressive();
            model.run(() -> model.repository.updateDraftScoring(draftId, next));
        });
        TextView custom = section.findViewById(R.id.create_custom_strategy);
        custom.setText(strategy.preset == ScoringStrategy.Preset.CUSTOM
                ? R.string.edit_custom_strategy : R.string.create_custom_strategy);
        ViewCompat.setStateDescription(custom, strategy.preset == ScoringStrategy.Preset.CUSTOM
                ? section.getContext().getString(R.string.strategy_selected) : null);
        custom.setOnClickListener(view -> model.beginStrategy(new StrategyRequest(
                StrategyRequest.Target.DRAFT, draftId, "PLAYERS", strategy).choose(ScoringStrategy.Preset.CUSTOM)));
        info(section.findViewById(R.id.default_strategy_info), ScoringStrategy.defaults());
        info(section.findViewById(R.id.aggressive_strategy_info), ScoringStrategy.aggressive());
    }

    private static void info(View anchor, ScoringStrategy strategy) {
        String details = StrategyEditor.rules(anchor.getContext(), strategy);
        anchor.setTooltipText(details);
        anchor.setOnClickListener(view -> {
            float density = view.getResources().getDisplayMetrics().density;
            int width = Math.min((int) (360 * density), view.getResources().getDisplayMetrics().widthPixels - (int) (32 * density));
            TextView text = new TextView(view.getContext());
            text.setText(StrategyEditor.label(view.getContext(), strategy) + "\n\n" + details);
            text.setTextSize(14);
            text.setTextColor(MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface));
            int padding = (int) (16 * density);
            text.setPadding(padding, padding, padding, padding);
            ScrollView scroll = new ScrollView(view.getContext());
            scroll.addView(text);
            scroll.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int height = Math.min(scroll.getMeasuredHeight(), view.getResources().getDisplayMetrics().heightPixels * 2 / 3);
            PopupWindow popup = new PopupWindow(scroll, width, height, true);
            GradientDrawable background = new GradientDrawable();
            background.setColor(MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface));
            background.setCornerRadius(8 * density);
            popup.setBackgroundDrawable(background);
            popup.setElevation(8 * density);
            popup.setOutsideTouchable(true);
            popup.showAsDropDown(view);
            text.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
        });
    }
}
