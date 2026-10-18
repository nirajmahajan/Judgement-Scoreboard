package android.example.judgement.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.example.judgement.R;
import android.example.judgement.data.Records;
import android.example.judgement.domain.ScoringStrategy;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.view.ViewCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import java.util.List;
import java.util.function.Consumer;

/** Keeps the selected rules visible while their explanation scrolls independently below. */
final class StrategySelection {
    private StrategySelection() { }

    static void bind(LinearLayout panel, StrategyRequest request,
            List<Records.SavedStrategy> saved, Consumer<StrategyRequest> changed) {
        panel.removeAllViews();
        panel.setVisibility(request.customEditor ? View.GONE : View.VISIBLE);
        if (request.customEditor) return;
        Context context = panel.getContext();
        panel.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context); title.setId(R.id.strategy_selection_title);
        title.setText(R.string.choose_scoring);
        title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
        title.setPadding(0,dp(context,12),0,dp(context,8));
        ViewCompat.setAccessibilityHeading(title,true);
        panel.addView(title,new LinearLayout.LayoutParams(-1,-2));

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setId(R.id.strategy_selection_choices); scroll.setHorizontalScrollBarEnabled(true);
        LinearLayout choices = new LinearLayout(context);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(choices,new HorizontalScrollView.LayoutParams(-2,-2));
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        ScoringStrategy selected = request.strategy();
        MaterialButton selectedButton = addChoice(choices,ScoringStrategy.defaults(),selected,
                R.id.strategy_default,() -> changed.accept(request.select(ScoringStrategy.defaults())));
        MaterialButton aggressive = addChoice(choices,ScoringStrategy.aggressive(),selected,
                R.id.strategy_aggressive,() -> changed.accept(request.select(ScoringStrategy.aggressive())));
        if (aggressive.isChecked()) selectedButton = aggressive;
        Records.SavedStrategy selectedProfile = null;
        for (Records.SavedStrategy profile : saved) {
            ScoringStrategy rules = ScoringStrategy.decode(profile.scoringConfig);
            MaterialButton choice = addChoice(choices,rules,selected,View.generateViewId(),
                    () -> changed.accept(request.select(rules)));
            if (choice.isChecked()) { selectedButton = choice; selectedProfile = profile; }
        }
        final MaterialButton reveal = selectedButton;
        scroll.post(() -> {
            if (!reveal.isChecked()) return;
            int right = reveal.getRight(), left = reveal.getLeft();
            int destination = left < scroll.getScrollX() ? left
                    : right > scroll.getScrollX() + scroll.getWidth() ? right-scroll.getWidth()
                    : scroll.getScrollX();
            scroll.scrollTo(Math.max(0,destination),0);
        });
        LinearLayout actions = new LinearLayout(context);
        MaterialButton create = link(context,R.string.create_custom_strategy);
        create.setId(R.id.create_custom_strategy);
        create.setOnClickListener(view -> changed.accept(request.createCustom()));
        actions.addView(create,new LinearLayout.LayoutParams(0,-2,1));
        if (selectedProfile != null) {
            Records.SavedStrategy profile = selectedProfile;
            MaterialButton modify = link(context,R.string.modify_strategy);
            modify.setId(R.id.modify_selected_strategy);
            modify.setContentDescription(context.getString(R.string.modify_named_strategy,profile.name));
            modify.setOnClickListener(view -> changed.accept(request.modify(profile)));
            actions.addView(modify,new LinearLayout.LayoutParams(0,-2,1));
        }
        panel.addView(actions,new LinearLayout.LayoutParams(-1,-2));
    }

    private static MaterialButton addChoice(LinearLayout row, ScoringStrategy rules,
            ScoringStrategy selected, int id, Runnable select) {
        Context context = row.getContext();
        MaterialButton button = new MaterialButton(context,null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setId(id); button.setText(StrategyEditor.label(context,rules));
        button.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        button.setAllCaps(false); button.setSingleLine(false); button.setMaxLines(2);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setMinWidth(dp(context,112)); button.setMaxWidth(dp(context,216));
        button.setMinHeight(dp(context,48)); button.setInsetTop(0); button.setInsetBottom(0);
        button.setCheckable(true); button.setChecked(rules.equals(selected));
        int primary = MaterialColors.getColor(row,androidx.appcompat.R.attr.colorPrimary);
        button.setStrokeColor(ColorStateList.valueOf(primary));
        button.setBackgroundTintList(ColorStateList.valueOf(button.isChecked() ? primary : Color.TRANSPARENT));
        button.setTextColor(button.isChecked()
                ? MaterialColors.getColor(row,com.google.android.material.R.attr.colorOnPrimary) : primary);
        ViewCompat.setStateDescription(button,context.getString(button.isChecked()
                ? R.string.strategy_selected : R.string.strategy_not_selected));
        TooltipCompat.setTooltipText(button,StrategyEditor.label(context,rules));
        button.setOnClickListener(view -> select.run());
        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-2,-2);
        if (row.getChildCount() > 0) spacing.setMarginStart(dp(context,8));
        row.addView(button,spacing); return button;
    }

    private static MaterialButton link(Context context,int label) {
        MaterialButton button = new MaterialButton(context,null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label); button.setAllCaps(false); button.setSingleLine(false);
        button.setMinWidth(0); button.setMinHeight(dp(context,48)); button.setStrokeWidth(0);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setTextColor(MaterialColors.getColor(button,androidx.appcompat.R.attr.colorPrimary));
        return button;
    }

    private static int dp(Context context,int value) {
        return Math.round(value*context.getResources().getDisplayMetrics().density);
    }
}
