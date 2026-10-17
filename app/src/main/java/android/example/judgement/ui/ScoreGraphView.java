package android.example.judgement.ui;

import android.content.Context;
import android.example.judgement.data.GameStatistics;
import android.example.judgement.domain.Outcome;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.google.android.material.color.MaterialColors;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** A scroll-friendly graph whose selectable events also work with TalkBack and a keyboard. */
public final class ScoreGraphView extends View {
    public enum Mode { CUMULATIVE, POINTS_PER_ROUND }

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF plot = new RectF();
    private final List<GameStatistics.TimelineEvent> events = new ArrayList<>();
    private final Set<Long> selectedIds = new HashSet<>();
    private final Map<Long, GameStatistics.PlayerStatistics> players = new LinkedHashMap<>();
    private final EventAccessibility accessibility = new EventAccessibility();
    private final int touchSlop;
    private Mode mode = Mode.CUMULATIVE;
    private Consumer<GameStatistics.TimelineEvent> selection;
    private StaticLayout strategyCaption;
    private String strategyText = "";
    private BigInteger minimum = BigInteger.ZERO;
    private BigInteger maximum = BigInteger.ONE;
    private int selectedEvent = -1;
    private float downX;
    private float downY;
    private boolean touching;

    public ScoreGraphView(Context context) {
        this(context, null);
    }

    public ScoreGraphView(Context context, @Nullable AttributeSet attributes) {
        super(context, attributes);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        text.setTextSize(sp(12));
        setFocusable(true);
        setFocusableInTouchMode(true);
        ViewCompat.setAccessibilityDelegate(this, accessibility);
        ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateDescription();
    }

    public void setData(GameStatistics report, Set<Long> selectedPlayers, Mode mode,
            @Nullable Consumer<GameStatistics.TimelineEvent> selection) {
        int keyboardFocus = accessibility.getKeyboardFocusedVirtualViewId();
        if (keyboardFocus != ExploreByTouchHelper.INVALID_ID) {
            accessibility.clearKeyboardFocusForVirtualView(keyboardFocus);
        }
        int spokenFocus = accessibility.getAccessibilityFocusedVirtualViewId();
        if (spokenFocus != ExploreByTouchHelper.INVALID_ID) {
            accessibility.getAccessibilityNodeProvider(this).performAction(spokenFocus,
                    AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
        }
        this.mode = java.util.Objects.requireNonNull(mode);
        this.selection = selection;
        this.selectedEvent = -1;
        selectedIds.clear();
        selectedIds.addAll(selectedPlayers);
        players.clear();
        for (GameStatistics.PlayerStatistics player : report.players) {
            players.put(player.playerId, player);
        }
        selectedIds.retainAll(players.keySet());
        events.clear();
        if (mode == Mode.CUMULATIVE) {
            events.addAll(report.timeline);
        } else {
            Map<Long, GameStatistics.TimelineEvent> byRound = new LinkedHashMap<>();
            for (GameStatistics.TimelineEvent event : report.timeline) {
                if (event.round != null) byRound.put(event.round.roundId, event);
            }
            for (GameStatistics.RoundStatistics round : report.rounds) {
                events.add(byRound.get(round.roundId));
            }
        }
        StringBuilder changes = new StringBuilder();
        for (GameStatistics.RoundStatistics round : report.rounds) {
            if (!round.strategyChanged) continue;
            if (changes.length() > 0) changes.append("; ");
            changes.append("round ").append(round.number).append(": ")
                    .append(strategyName(round));
        }
        strategyText = changes.length() == 0 ? ""
                : "Strategy changes: " + changes + ". Dashed markers show these rounds.";
        updateRange();
        updateDescription();
        accessibility.invalidateRoot();
        requestLayout();
        invalidate();
    }

    /** Fixed colors work on both theme surfaces and stay unchanged when a strategy changes. */
    public static int colorForPlayer(long playerId) {
        float hue = (float) ((Math.floorMod(playerId, 1_000_003L) * 137.50776405003785) % 360.0);
        float lower = 0f;
        float upper = 1f;
        int color = Color.BLACK;
        // A shared luminance near0.22 maintains useful contrast on ivory and deep slate alike.
        for (int iteration = 0; iteration < 20; iteration++) {
            float lightness = (lower + upper) / 2f;
            color = ColorUtils.HSLToColor(new float[] {hue, 0.72f, lightness});
            if (ColorUtils.calculateLuminance(color) < 0.22) lower = lightness;
            else upper = lightness;
        }
        return color;
    }

    public static String playerPattern(long playerId) {
        switch ((int) Math.floorMod(playerId, 3L)) {
            case 0: return "circle markers";
            case 1: return "square markers";
            default: return "triangle markers";
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(dp(320), widthSpec);
        text.setTextSize(sp(12));
        strategyCaption = caption(width);
        int captionHeight = strategyCaption == null ? 0 : strategyCaption.getHeight() + dp(12);
        int height = dp(260) + captionHeight + Math.round(sp(24));
        setMeasuredDimension(width, resolveSize(height, heightSpec));
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        geometry();
        int foreground = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurface, Color.DKGRAY);
        int secondary = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        text.setTextSize(sp(12));
        text.setColor(secondary);
        text.setTextAlign(Paint.Align.RIGHT);
        line.setPathEffect(null);
        line.setStrokeWidth(dp(1));
        line.setStyle(Paint.Style.STROKE);
        for (int tick = 0; tick <= 4; tick++) {
            float y = plot.bottom - plot.height() * tick / 4f;
            BigInteger value = minimum.add(maximum.subtract(minimum)
                    .multiply(BigInteger.valueOf(tick)).divide(BigInteger.valueOf(4)));
            line.setColor(secondary);
            line.setAlpha(50);
            canvas.drawLine(plot.left, y, plot.right, y, line);
            canvas.drawText(axisValue(value), plot.left - dp(8), y + text.getTextSize() / 3f, text);
        }
        line.setAlpha(255);
        if (events.isEmpty() || selectedIds.isEmpty()) {
            text.setColor(foreground);
            text.setTextAlign(Paint.Align.CENTER);
            String message = events.isEmpty() ? "Complete a round to see this graph."
                    : "Select players to compare.";
            // Empty-state text is also exposed in the native view description.
            drawWrapped(canvas, message, plot.left, plot.top + dp(28), plot.width());
            return;
        }

        for (int index = 0; index < events.size(); index++) {
            GameStatistics.TimelineEvent event = events.get(index);
            float x = x(index);
            if (event.round != null && event.round.strategyChanged) {
                line.setColor(secondary);
                line.setStrokeWidth(dp(1));
                line.setPathEffect(new DashPathEffect(new float[] {dp(4), dp(4)}, 0));
                canvas.drawLine(x, plot.top, x, plot.bottom, line);
                line.setPathEffect(null);
            }
            if (index == selectedEvent) {
                line.setColor(foreground);
                line.setStrokeWidth(dp(2));
                canvas.drawLine(x, plot.top, x, plot.bottom, line);
            }
        }
        for (Long playerId : players.keySet()) {
            if (!selectedIds.contains(playerId)) continue;
            line.setColor(colorForPlayer(playerId));
            line.setAlpha(255);
            line.setStrokeWidth(dp(2));
            line.setPathEffect(null);
            boolean hasPrevious = false;
            float previousX = 0f;
            float previousY = 0f;
            for (int index = 0; index < events.size(); index++) {
                BigInteger value = value(events.get(index), playerId);
                if (value == null) {
                    // Absence is a genuine gap, never a fabricated zero-point performance.
                    hasPrevious = false;
                    continue;
                }
                float pointX = x(index);
                float pointY = y(value);
                if (hasPrevious) canvas.drawLine(previousX, previousY, pointX, pointY, line);
                marker(canvas, playerId, pointX, pointY);
                previousX = pointX;
                previousY = pointY;
                hasPrevious = true;
            }
        }
        text.setColor(secondary);
        text.setTextAlign(Paint.Align.CENTER);
        int labelEvery = Math.max(1, (int) Math.ceil(events.size() * sp(40) / plot.width()));
        for (int index = 0; index < events.size(); index++) {
            if (index % labelEvery != 0 && index != events.size() - 1) continue;
            GameStatistics.TimelineEvent event = events.get(index);
            String label = event.round == null ? "Adjust" : "R" + event.round.number;
            canvas.drawText(label, x(index), plot.bottom + sp(20), text);
        }
        if (strategyCaption != null) {
            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(secondary);
            canvas.save();
            canvas.translate(dp(12), plot.bottom + sp(32));
            strategyCaption.draw(canvas);
            canvas.restore();
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        geometry();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touching = !events.isEmpty() && !selectedIds.isEmpty()
                    && inEventArea(event.getX(), event.getY());
            downX = event.getX();
            downY = event.getY();
            return touching;
        }
        if (!touching) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float horizontal = Math.abs(event.getX() - downX);
            float vertical = Math.abs(event.getY() - downY);
            if (horizontal > touchSlop && horizontal > vertical && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            touching = false;
            if (Math.abs(event.getX() - downX) <= touchSlop
                    && Math.abs(event.getY() - downY) <= touchSlop) {
                select(nearest(event.getX()));
                performClick();
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            touching = false;
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override public boolean dispatchHoverEvent(MotionEvent event) {
        return accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return accessibility.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override protected void onFocusChanged(boolean gainFocus, int direction,
            @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        accessibility.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }

    private void updateRange() {
        minimum = BigInteger.ZERO;
        maximum = BigInteger.ZERO;
        for (GameStatistics.TimelineEvent event : events) {
            for (Long playerId : selectedIds) {
                BigInteger value = value(event, playerId);
                if (value == null) continue;
                minimum = minimum.min(value);
                maximum = maximum.max(value);
            }
        }
        if (maximum.equals(minimum)) maximum = minimum.add(BigInteger.ONE);
    }

    private BigInteger value(GameStatistics.TimelineEvent event, long playerId) {
        if (mode == Mode.CUMULATIVE) return event.cumulative.get(playerId);
        GameStatistics.Performance performance = event.round.player(playerId);
        return performance == null ? null : BigInteger.valueOf(performance.points);
    }

    private void geometry() {
        text.setTextSize(sp(12));
        strategyCaption = caption(getWidth());
        float labels = Math.max(text.measureText(axisValue(minimum)),
                text.measureText(axisValue(maximum)));
        float left = Math.max(dp(48), labels + dp(12));
        float caption = strategyCaption == null ? 0 : strategyCaption.getHeight() + dp(12);
        plot.set(left, dp(20), Math.max(left + dp(1), getWidth() - dp(24)),
                Math.max(dp(68), getHeight() - caption - sp(40)));
    }

    private StaticLayout caption(int width) {
        if (strategyText.isEmpty() || width <= dp(24)) return null;
        return StaticLayout.Builder.obtain(strategyText, 0, strategyText.length(), text,
                        Math.max(1, width - dp(24)))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build();
    }

    private void drawWrapped(Canvas canvas, String value, float left, float top, float width) {
        Paint.Align previous = text.getTextAlign();
        text.setTextAlign(Paint.Align.LEFT);
        StaticLayout layout = StaticLayout.Builder.obtain(value, 0, value.length(), text,
                Math.max(1, Math.round(width))).setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false).build();
        canvas.save();
        canvas.translate(left, top);
        layout.draw(canvas);
        canvas.restore();
        text.setTextAlign(previous);
    }

    private void marker(Canvas canvas, long playerId, float x, float y) {
        float radius = dp(4);
        line.setStyle(Paint.Style.FILL);
        int shape = (int) Math.floorMod(playerId, 3L);
        if (shape == 0) {
            canvas.drawCircle(x, y, radius, line);
        } else if (shape == 1) {
            canvas.drawRect(x - radius, y - radius, x + radius, y + radius, line);
        } else {
            Path path = new Path();
            path.moveTo(x, y - radius);
            path.lineTo(x + radius, y + radius);
            path.lineTo(x - radius, y + radius);
            path.close();
            canvas.drawPath(path, line);
        }
        line.setStyle(Paint.Style.STROKE);
    }

    private float x(int index) {
        return events.size() <= 1 ? plot.centerX()
                : plot.left + plot.width() * index / (events.size() - 1f);
    }

    private float y(BigInteger value) {
        double fraction = new BigDecimal(value.subtract(minimum)).divide(
                new BigDecimal(maximum.subtract(minimum)), MathContext.DECIMAL64).doubleValue();
        return plot.bottom - (float) fraction * plot.height();
    }

    private boolean inEventArea(float x, float y) {
        // The first and last points sit on the plot edges; include their full48dp hit areas.
        return x >= Math.max(0f, plot.left - dp(24))
                && x <= Math.min(getWidth(), plot.right + dp(24))
                && y >= plot.top && y <= plot.bottom;
    }

    private int nearest(float x) {
        if (events.size() <= 1) return 0;
        float position = (x - plot.left) / plot.width() * (events.size() - 1);
        return Math.max(0, Math.min(events.size() - 1, Math.round(position)));
    }

    private boolean select(int index) {
        if (index < 0 || index >= events.size()) return false;
        selectedEvent = index;
        if (selection != null) selection.accept(events.get(index));
        accessibility.invalidateVirtualView(index);
        accessibility.sendEventForVirtualView(index, AccessibilityEvent.TYPE_VIEW_CLICKED);
        invalidate();
        return true;
    }

    private void updateDescription() {
        String title = mode == Mode.CUMULATIVE ? "Cumulative score graph" : "Points per round graph";
        String state = events.isEmpty() ? ". Complete a round to see this graph."
                : selectedIds.isEmpty() ? ". Select players to compare."
                : ". Select a round or score adjustment to inspect its recorded details.";
        setContentDescription(title + state);
    }

    private String eventDescription(int index) {
        GameStatistics.TimelineEvent event = events.get(index);
        StringBuilder description = new StringBuilder();
        if (event.round == null) {
            description.append("Manual score adjustment for ").append(event.adjustment.name)
                    .append(": ").append(event.adjustment.delta >= 0 ? "+" : "")
                    .append(event.adjustment.delta).append(" points.");
        } else {
            description.append("Round ").append(event.round.number).append(", ")
                    .append(event.round.cards).append(" cards, ")
                    .append(strategyName(event.round)).append(" scoring.");
            if (event.round.strategyChanged) description.append(" Strategy changed here.");
        }
        for (GameStatistics.PlayerStatistics player : players.values()) {
            if (!selectedIds.contains(player.playerId)) continue;
            description.append(' ').append(player.name).append(": ");
            if (event.round != null) {
                GameStatistics.Performance performance = event.round.player(player.playerId);
                if (performance == null) {
                    description.append("did not participate");
                } else {
                    description.append("bid ").append(performance.bid).append(", ")
                            .append(performance.outcome == Outcome.WON ? "Won" : "Lost")
                            .append(", earned ").append(performance.points).append(" points");
                }
                description.append("; ");
            }
            description.append("cumulative ").append(event.cumulative.get(player.playerId))
                    .append(" points.");
        }
        return description.toString();
    }

    private static String strategyName(GameStatistics.RoundStatistics round) {
        switch (round.strategy.preset) {
            case AGGRESSIVE: return "Aggressive";
            case CUSTOM: return "Custom";
            default: return "Default";
        }
    }

    private static String axisValue(BigInteger value) {
        BigInteger magnitude = value.abs();
        if (magnitude.compareTo(BigInteger.valueOf(1_000L)) < 0) return value.toString();
        String[] units = {"", "k", "m", "b", "t", "q", "Q"};
        BigDecimal scaled = new BigDecimal(value);
        int unit = 0;
        while (scaled.abs().compareTo(BigDecimal.valueOf(1_000L)) >= 0
                && unit < units.length - 1) {
            scaled = scaled.divide(BigDecimal.valueOf(1_000L));
            unit++;
        }
        NumberFormat format = NumberFormat.getNumberInstance(Locale.getDefault());
        format.setMaximumFractionDigits(1);
        return format.format(scaled) + units[unit];
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private final class EventAccessibility extends ExploreByTouchHelper {
        EventAccessibility() { super(ScoreGraphView.this); }

        @Override protected int getVirtualViewAt(float x, float y) {
            geometry();
            return !events.isEmpty() && !selectedIds.isEmpty() && inEventArea(x, y)
                    ? nearest(x) : INVALID_ID;
        }

        @Override protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
            if (getWidth() <= 0 || getHeight() <= 0 || selectedIds.isEmpty()) return;
            for (int index = 0; index < events.size(); index++) virtualViewIds.add(index);
        }

        @Override protected void onPopulateNodeForVirtualView(int id,
                @NonNull AccessibilityNodeInfoCompat node) {
            geometry();
            node.setContentDescription(eventDescription(id));
            node.setClassName("android.widget.Button");
            node.setClickable(true);
            node.setFocusable(true);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            node.setSelected(id == selectedEvent);
            int center = Math.round(x(id));
            int minimumWidth = dp(48);
            int left = Math.max(0, Math.min(getWidth() - minimumWidth,
                    center - minimumWidth / 2));
            node.setBoundsInParent(new Rect(left, Math.round(plot.top),
                    Math.min(getWidth(), left + minimumWidth), Math.round(plot.bottom)));
        }

        @Override protected boolean onPerformActionForVirtualView(int id, int action,
                @Nullable Bundle arguments) {
            return action == AccessibilityNodeInfoCompat.ACTION_CLICK && select(id);
        }
    }
}
