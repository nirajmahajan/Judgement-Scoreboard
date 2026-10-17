package android.example.judgement.ui;

import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** The chooser renders saved state; keystrokes update examples without replacing focused inputs. */
final class StrategyEditor extends RecyclerView.Adapter<StrategyEditor.Holder> {
    interface Listener {
        void onChanged(StrategyRequest request);
        void onHelp();
    }
    private final Listener listener;
    private StrategyRequest request;
    private Holder bound;
    private final View.OnLayoutChangeListener viewportChanged = (view, left, top, right, bottom,
            oldLeft, oldTop, oldRight, oldBottom) -> {
        if ((right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
                && request.error != null) focusFirstError();
    };
    StrategyEditor(StrategyRequest request, Listener listener) {
        this.request = request; this.listener = listener; setHasStableIds(true);
    }
    @Override public void onAttachedToRecyclerView(@NonNull RecyclerView list) {
        super.onAttachedToRecyclerView(list); list.addOnLayoutChangeListener(viewportChanged);
    }
    @Override public void onDetachedFromRecyclerView(@NonNull RecyclerView list) {
        list.removeOnLayoutChangeListener(viewportChanged); super.onDetachedFromRecyclerView(list);
    }
    @Override public long getItemId(int position) { return 0; }
    @Override public int getItemCount() { return 1; }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(parent.getContext());
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        bound = holder; holder.bind(request);
        if (request.error != null) focusFirstError();
    }
    @Override public void onViewRecycled(@NonNull Holder holder) {
        if (bound == holder) bound = null;
    }
    void update(StrategyRequest next) {
        boolean newError = next.error != null && !next.error.equals(request.error);
        request = next;
        if (bound != null) {
            bound.bind(next);
            if (newError) focusFirstError();
        }
    }
    void focusFirstError() {
        if (bound == null) return;
        for (int i = 0; i < bound.fields.length; i++) {
            if (bound.fields[i].getError() != null) {
                bound.inputs[i].requestFocus();
                View error = bound.fields[i].findViewById(com.google.android.material.R.id.textinput_error);
                revealAfterLayout(bound.fields[i], error == null ? bound.fields[i] : error); return;
            }
        }
        revealAfterLayout(bound.error);
    }
    private static void revealAfterLayout(View target) {
        revealAfterLayout(target, target);
    }
    private static void revealAfterLayout(View field, View error) {
        androidx.core.view.OneShotPreDrawListener.add(field, () -> field.post(() -> {
            android.view.ViewParent ancestor = field.getParent();
            while (ancestor != null && !(ancestor instanceof RecyclerView)) ancestor = ancestor.getParent();
            if (!(ancestor instanceof RecyclerView)) {
                android.graphics.Rect rect = new android.graphics.Rect(); field.getDrawingRect(rect);
                field.requestRectangleOnScreen(rect, true); return;
            }
            RecyclerView list = (RecyclerView) ancestor;
            list.stopScroll();
            int top = list.getPaddingTop(), bottom = list.getHeight() - list.getPaddingBottom();
            // Keep the floating label, entered value and error together whenever they fit.
            // On a short viewport, the error remains the priority rather than being hidden.
            View target = field.getHeight() <= bottom - top ? field : error;
            android.graphics.Rect rect = new android.graphics.Rect(); target.getDrawingRect(rect);
            list.offsetDescendantRectToMyCoords(target, rect);
            int distance = rect.bottom > bottom ? rect.bottom - bottom : rect.top < top ? rect.top - top : 0;
            if (distance != 0) list.scrollBy(0, distance);
        }));
    }
    static String label(Context context, ScoringStrategy strategy) {
        return context.getString(strategy.preset == ScoringStrategy.Preset.AGGRESSIVE
                ? R.string.strategy_aggressive : strategy.preset == ScoringStrategy.Preset.CUSTOM
                ? R.string.strategy_custom : R.string.strategy_default);
    }
    static String rules(Context context, ScoringStrategy strategy) {
        if (strategy.preset == ScoringStrategy.Preset.DEFAULT) return context.getString(R.string.strategy_default_rules);
        if (strategy.preset == ScoringStrategy.Preset.AGGRESSIVE) return context.getString(R.string.strategy_aggressive_rules);
        return context.getString(R.string.strategy_custom_rules, strategy.zeroReward,
                strategy.exactBase, strategy.perTrick) + "\n\n" + context.getString(strategy.majorityBonus
                ? R.string.strategy_bonus_rules : R.string.strategy_no_bonus);
    }
    static String examples(Context context, ScoringStrategy strategy) {
        int[][] pairs = {{1,1},{2,1},{2,2},{12,0},{12,6},{12,7},{12,8},{16,8},{16,9}};
        StringBuilder text = new StringBuilder();
        for (int[] pair : pairs) {
            int cards = pair[0], bid = pair[1];
            if (text.length() > 0) text.append("\n\n");
            long total = strategy.points(cards, bid, Outcome.WON);
            text.append(context.getString(R.string.strategy_example, cards, bid, total));
            if (bid == 0) text.append('\n').append(context.getString(R.string.strategy_exact_zero));
            else if (strategy.majorityBonus && bid > cards / 2) text.append('\n').append(
                    context.getString(R.string.strategy_bonus_calculation, strategy.exactBase,
                            bid, strategy.perTrick, Math.min(cards,10)));
            else if (strategy.majorityBonus && bid * 2 == cards) text.append('\n').append(context.getString(R.string.strategy_exactly_half));
        }
        return text.append("\n\n").append(context.getString(R.string.strategy_missed_example)).toString();
    }
    final class Holder extends RecyclerView.ViewHolder {
        final LinearLayout custom;
        final TextInputLayout[] fields = new TextInputLayout[3];
        final TextInputEditText[] inputs = new TextInputEditText[3];
        final MaterialButton[] presets = new MaterialButton[3];
        final CheckBox bonus;
        final TextView rules, examples, error;
        boolean binding;
        Holder(Context context) {
            super(new LinearLayout(context));
            LinearLayout root = (LinearLayout) itemView; root.setOrientation(LinearLayout.VERTICAL);
            root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
            int[] names = {R.string.strategy_default,R.string.strategy_aggressive,R.string.strategy_custom};
            int[] ids = {R.id.strategy_default,R.id.strategy_aggressive,R.id.strategy_custom};
            for (int i = 0; i < presets.length; i++) {
                final ScoringStrategy.Preset preset = ScoringStrategy.Preset.values()[i];
                MaterialButton button = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                button.setText(names[i]); button.setId(ids[i]); button.setMinHeight(dp(context,48));
                button.setSingleLine(false); button.setCheckable(true);
                button.setOnClickListener(view -> listener.onChanged(request.choose(preset)));
                root.addView(button,new LinearLayout.LayoutParams(-1,-2)); presets[i] = button;
            }
            rules = text(context); rules.setId(R.id.strategy_rules); root.addView(rules);
            custom = new LinearLayout(context); custom.setOrientation(LinearLayout.VERTICAL); root.addView(custom);
            int[] labels = {R.string.strategy_zero_reward,R.string.strategy_base_reward,R.string.strategy_per_trick};
            int[] inputIds = {R.id.strategy_zero,R.id.strategy_base,R.id.strategy_per_trick};
            int[] shortLabels = {R.string.strategy_zero_short,R.string.strategy_base_short,R.string.strategy_trick_short};
            for (int i = 0; i < inputs.length; i++) {
                TextView label = text(context); label.setText(labels[i]); label.setLabelFor(inputIds[i]); custom.addView(label);
                fields[i] = new TextInputLayout(context); fields[i].setHint(shortLabels[i]);
                inputs[i] = new TextInputEditText(fields[i].getContext()); inputs[i].setId(inputIds[i]);
                inputs[i].setInputType(InputType.TYPE_CLASS_NUMBER); inputs[i].setSingleLine(true);
                inputs[i].setMinHeight(dp(context,48));
                fields[i].addView(inputs[i],new LinearLayout.LayoutParams(-1,-2));
                LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1,-2); spacing.topMargin=dp(context,12);
                custom.addView(fields[i],spacing);
                inputs[i].addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence text,int start,int count,int after) { }
                    @Override public void onTextChanged(CharSequence text,int start,int before,int count) { changed(); }
                    @Override public void afterTextChanged(Editable text) { }
                });
            }
            bonus = new CheckBox(context); bonus.setId(R.id.strategy_bonus); bonus.setText(R.string.strategy_enable_bonus);
            bonus.setMinHeight(dp(context,48)); custom.addView(bonus);
            bonus.setOnCheckedChangeListener((button,checked) -> changed());
            TextView bonusExplanation = text(context); bonusExplanation.setText(R.string.strategy_bonus_rules); custom.addView(bonusExplanation);
            error=text(context); error.setId(R.id.strategy_error); error.setTextColor(com.google.android.material.color.MaterialColors.getColor(root,androidx.appcompat.R.attr.colorError));
            ViewCompat.setAccessibilityLiveRegion(error,ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE); root.addView(error);
            MaterialButton help = new MaterialButton(context,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
            help.setId(R.id.strategy_help); help.setText(R.string.how_scoring_works); help.setMinHeight(dp(context,48));
            help.setSingleLine(false); help.setOnClickListener(view -> listener.onHelp()); root.addView(help,new LinearLayout.LayoutParams(-1,-2));
            TextView tableHeading=text(context); tableHeading.setText(R.string.strategy_examples_title);
            ViewCompat.setAccessibilityHeading(tableHeading,true); root.addView(tableHeading);
            examples=text(context); examples.setId(R.id.strategy_examples); root.addView(examples);
        }
        void changed() {
            if (!binding) listener.onChanged(request.edit(inputs[0].getText().toString(),
                    inputs[1].getText().toString(),inputs[2].getText().toString(),bonus.isChecked()));
        }
        void bind(StrategyRequest next) {
            binding=true;
            for (int i=0;i<presets.length;i++) {
                boolean selected = next.preset.ordinal()==i;
                presets[i].setChecked(selected); ViewCompat.setStateDescription(presets[i],itemView.getContext().getString(selected?R.string.strategy_selected:R.string.strategy_not_selected));
            }
            custom.setVisibility(next.preset==ScoringStrategy.Preset.CUSTOM?View.VISIBLE:View.GONE);
            String[] values={next.zero,next.base,next.perTrick};
            for(int i=0;i<inputs.length;i++) {
                if(!inputs[i].getText().toString().equals(values[i])) inputs[i].setText(values[i]);
                String message=null;
                if(next.validate && next.preset==ScoringStrategy.Preset.CUSTOM) {
                    try { StrategyRequest.points(values[i]); } catch(IllegalArgumentException invalid) { message=invalid.getMessage(); }
                }
                fields[i].setError(message);
            }
            bonus.setChecked(next.bonus);
            error.setText(next.error); error.setVisibility(next.error==null?View.GONE:View.VISIBLE);
            try {
                ScoringStrategy strategy=next.strategy(); rules.setText(rules(itemView.getContext(),strategy));
                examples.setText(examples(itemView.getContext(),strategy));
            } catch(IllegalArgumentException invalid) {
                rules.setText(R.string.strategy_custom_explanation); examples.setText(R.string.strategy_complete_fields);
            }
            binding=false;
        }
    }
    private static TextView text(Context context) {
        TextView text=new TextView(context); text.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        text.setPadding(0,dp(context,12),0,dp(context,12));
        text.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); return text;
    }
    private static int dp(Context context,int value) {return Math.round(value*context.getResources().getDisplayMetrics().density);}
}
