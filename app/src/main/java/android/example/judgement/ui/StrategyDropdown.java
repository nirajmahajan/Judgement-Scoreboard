package android.example.judgement.ui;

import android.example.judgement.R;
import android.example.judgement.data.Records;
import android.example.judgement.domain.ScoringStrategy;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.view.ViewCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;

/** A dropdown with separate select and modify targets; built-in rules never expose editing. */
final class StrategyDropdown {
    interface Listener {
        void select(ScoringStrategy strategy);
        void create();
        void modify(Records.SavedStrategy strategy);
    }
    private StrategyDropdown() { }
    static void bind(MaterialButton anchor, ScoringStrategy selected,
            List<Records.SavedStrategy> saved, Listener listener) {
        anchor.setText(StrategyEditor.label(anchor.getContext(), selected));
        anchor.setSingleLine(false); anchor.setMaxLines(10);
        anchor.setIconResource(R.drawable.ic_score_dropdown);
        anchor.setIconGravity(MaterialButton.ICON_GRAVITY_END);
        anchor.setContentDescription(anchor.getContext().getString(R.string.strategy_dropdown_description,
                StrategyEditor.label(anchor.getContext(), selected)));
        anchor.setOnClickListener(view -> show(anchor, selected, saved, listener));
    }
    private static void show(MaterialButton anchor, ScoringStrategy selected,
            List<Records.SavedStrategy> saved, Listener listener) {
        LinearLayout rows = new LinearLayout(anchor.getContext()); rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(anchor.getContext()); scroll.setFillViewport(false); scroll.addView(rows);
        int width = Math.min(Math.max(anchor.getWidth(), dp(anchor, 280)), anchor.getResources().getDisplayMetrics().widthPixels - dp(anchor,32));
        PopupWindow popup = new PopupWindow(scroll, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(MaterialColors.getColor(anchor,com.google.android.material.R.attr.colorSurface));
        background.setCornerRadius(dp(anchor,8)); popup.setBackgroundDrawable(background);
        popup.setElevation(dp(anchor,8)); popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        addChoice(rows, popup, ScoringStrategy.defaults(), R.id.strategy_default, selected, null, listener);
        addChoice(rows, popup, ScoringStrategy.aggressive(), R.id.strategy_aggressive, selected, null, listener);
        for (Records.SavedStrategy item : saved) addChoice(rows, popup,
                ScoringStrategy.decode(item.scoringConfig), View.generateViewId(), selected, item, listener);
        MaterialButton create = new MaterialButton(anchor.getContext(),null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        create.setId(R.id.create_custom_strategy); create.setText(R.string.create_custom_strategy); create.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
        create.setSingleLine(false); create.setMaxLines(10);
        create.setMinHeight(dp(anchor,48)); create.setOnClickListener(view -> { popup.dismiss(); listener.create(); });
        rows.addView(create,new LinearLayout.LayoutParams(-1,-2));
        scroll.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        popup.setHeight(Math.min(scroll.getMeasuredHeight(),anchor.getResources().getDisplayMetrics().heightPixels / 2));
        View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) { popup.dismiss(); }
        };
        anchor.addOnAttachStateChangeListener(attachment);
        popup.setOnDismissListener(() -> anchor.removeOnAttachStateChangeListener(attachment));
        popup.showAsDropDown(anchor);
    }
    private static void addChoice(LinearLayout rows, PopupWindow popup, ScoringStrategy rules,
            int id, ScoringStrategy selected, Records.SavedStrategy profile, Listener listener) {
        LinearLayout row = new LinearLayout(rows.getContext()); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView name = new TextView(rows.getContext()); name.setId(id);
        name.setText(StrategyEditor.label(rows.getContext(), rules)); name.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        name.setTextColor(MaterialColors.getColor(rows,com.google.android.material.R.attr.colorOnSurface));
        name.setPadding(dp(rows,16),dp(rows,12),dp(rows,8),dp(rows,12)); name.setMinHeight(dp(rows,48));
        name.setGravity(android.view.Gravity.CENTER_VERTICAL); name.setClickable(true); name.setFocusable(true);
        android.util.TypedValue background = new android.util.TypedValue(); rows.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground,background,true); name.setBackgroundResource(background.resourceId);
        ViewCompat.setStateDescription(name, rows.getContext().getString(rules.equals(selected)?R.string.strategy_selected:R.string.strategy_not_selected));
        name.setOnClickListener(view -> { popup.dismiss(); listener.select(rules); });
        row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        if (profile == null) {
            AppCompatImageButton info = new AppCompatImageButton(rows.getContext()); info.setImageResource(R.drawable.ic_score_info);
            info.setPadding(dp(rows,12),dp(rows,12),dp(rows,12),dp(rows,12));
            info.setBackgroundResource(background.resourceId); info.setContentDescription(rows.getContext().getString(
                    rules.preset==ScoringStrategy.Preset.DEFAULT?R.string.default_strategy_info:R.string.aggressive_strategy_info));
            info.setOnClickListener(view -> { popup.dismiss(); new MaterialAlertDialogBuilder(rows.getContext())
                    .setTitle(StrategyEditor.label(rows.getContext(),rules)).setMessage(StrategyEditor.rules(rows.getContext(),rules))
                    .setPositiveButton(android.R.string.ok,null).show(); });
            row.addView(info,new LinearLayout.LayoutParams(dp(rows,48),dp(rows,48)));
        } else {
            MaterialButton modify = new MaterialButton(rows.getContext(),null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
            modify.setText(R.string.modify_strategy); modify.setContentDescription(rows.getContext().getString(R.string.modify_named_strategy,profile.name));
            modify.setMinWidth(0); modify.setMinHeight(dp(rows,48));
            modify.setOnClickListener(view -> { popup.dismiss(); listener.modify(profile); });
            row.addView(modify,new LinearLayout.LayoutParams(-2,-2));
        }
        rows.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    private static int dp(View view,int value) { return Math.round(value*view.getResources().getDisplayMetrics().density); }
}
