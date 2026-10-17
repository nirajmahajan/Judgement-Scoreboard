package android.example.judgement.data;

import android.app.Activity;
import android.example.judgement.R;
import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;
import android.example.judgement.ui.ScoreGraphView;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.accessibility.AccessibilityNodeInfo;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h740dp-notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScoreGraphViewTest {
    private ActivityController<Activity> controller;

    @After public void close() {
        if (controller != null) controller.pause().stop().destroy();
        RuntimeEnvironment.setFontScale(1f);
    }

    @Test
    public void cumulativeGraphRendersBeyondLongRangeAndSelectsTheSeparateAdjustment() {
        Fixture fixture = new Fixture(2);
        ScoringStrategy hugeZero = ScoringStrategy.custom(Long.MAX_VALUE, 0L, 0L, false);
        fixture.round(1, hugeZero, true);
        fixture.round(2, hugeZero, true);
        fixture.adjust(-Long.MAX_VALUE, 250L);
        GameStatistics report = GameStatistics.from(fixture.snapshot());
        AtomicReference<GameStatistics.TimelineEvent> selected = new AtomicReference<>();
        ScoreGraphView graph = graph(report, set(1L), ScoreGraphView.Mode.CUMULATIVE, selected);
        Bitmap bitmap = render(graph);
        assertTrue(pixels(bitmap, ScoreGraphView.colorForPlayer(1L)) > 20);
        AccessibilityNodeProvider provider = graph.getAccessibilityNodeProvider();
        String second = provider.createAccessibilityNodeInfo(1).getContentDescription().toString();
        assertTrue(second.contains(BigInteger.valueOf(Long.MAX_VALUE)
                .multiply(BigInteger.TWO).toString()));
        assertTrue(provider.performAction(2, AccessibilityNodeInfoCompat.ACTION_CLICK, null));
        assertEquals(GameStatistics.EventKind.ADJUSTMENT, selected.get().kind);
        assertEquals(-Long.MAX_VALUE, selected.get().adjustment.delta);
        assertTrue(provider.createAccessibilityNodeInfo(2).getContentDescription().toString()
                .contains("Manual score adjustment"));
    }

    @Test
    public void negativePrefixRendersAndAccessibilityReadsTheExactSignedValue() {
        Fixture fixture = new Fixture(2);
        fixture.round(1, ScoringStrategy.custom(0L, 0L, 0L, false), true);
        fixture.adjust(-10L, 150L);
        fixture.round(2, ScoringStrategy.defaults(), true);
        ScoreGraphView graph = graph(GameStatistics.from(fixture.snapshot()), set(1L),
                ScoreGraphView.Mode.CUMULATIVE, new AtomicReference<>());
        Bitmap bitmap = render(graph);
        assertTrue(pixels(bitmap, ScoreGraphView.colorForPlayer(1L)) > 20);
        String correction = graph.getAccessibilityNodeProvider()
                .createAccessibilityNodeInfo(1).getContentDescription().toString();
        assertTrue(correction.contains("cumulative -10 points"));
    }

    @Test
    public void perRoundGraphLeavesAnAbsenceGapAndDoesNotTurnManualCorrectionsIntoPerformance() {
        Fixture fixture = new Fixture(2);
        fixture.round(1, ScoringStrategy.defaults(), true);
        fixture.round(2, ScoringStrategy.aggressive(), false);
        fixture.round(3, ScoringStrategy.aggressive(), true);
        fixture.adjust(5L, 350L);
        GameStatistics report = GameStatistics.from(fixture.snapshot());
        AtomicReference<GameStatistics.TimelineEvent> selected = new AtomicReference<>();
        ScoreGraphView graph = graph(report, set(2L), ScoreGraphView.Mode.POINTS_PER_ROUND,
                selected);
        Bitmap bitmap = render(graph);
        AccessibilityNodeProvider provider = graph.getAccessibilityNodeProvider();
        AccessibilityNodeInfo middle = provider.createAccessibilityNodeInfo(1);
        assertTrue(middle.getContentDescription().toString().contains("did not participate"));
        assertTrue(middle.getContentDescription().toString().contains("Judgement+ scoring"));
        assertTrue(middle.getContentDescription().toString().contains("Strategy changed here"));
        Rect bounds = new Rect();
        middle.getBoundsInParent(bounds);
        // With no middle participation there is no series segment crossing this round's column.
        for (int y = bounds.top + 8; y < bounds.bottom - 8; y++) {
            assertFalse(bitmap.getPixel(bounds.centerX(), y) == ScoreGraphView.colorForPlayer(2L));
        }
        assertEquals(3, provider.createAccessibilityNodeInfo(View.NO_ID).getChildCount());
        assertTrue(provider.performAction(2, AccessibilityNodeInfoCompat.ACTION_CLICK, null));
        assertEquals(3, selected.get().round.number);
    }

    @Test
    public void fullNamesAndRecordedResultsRemainAccessibleWithFortyEightDpTargets() {
        Fixture fixture = new Fixture(52);
        String name = "निरज 🎴 — ";
        StringBuilder fullName = new StringBuilder(name);
        for (int i = 0; i < 60; i++) fullName.append("Long unbrokenplayername ");
        fixture.players.get(0).name = fullName.toString();
        fixture.round(1, ScoringStrategy.defaults(), true);
        Set<Long> selectedIds = new HashSet<>();
        for (long id = 1; id <= 52; id++) selectedIds.add(id);
        ScoreGraphView graph = graph(GameStatistics.from(fixture.snapshot()), selectedIds,
                ScoreGraphView.Mode.CUMULATIVE, new AtomicReference<>());
        render(graph);
        AccessibilityNodeInfo node = graph.getAccessibilityNodeProvider()
                .createAccessibilityNodeInfo(0);
        String description = node.getContentDescription().toString();
        assertTrue(description.contains(fullName));
        assertTrue(description.contains("prediction 0, Won, earned 10 points"));
        assertFalse(description.contains("actual tricks"));
        Rect bounds = new Rect();
        node.getBoundsInParent(bounds);
        assertTrue(bounds.width() >= 48);
        assertTrue(bounds.height() >= 48);
        assertTrue(node.isClickable());
        assertTrue(node.isFocusable());
    }

    @Test
    public void touchAndKeyboardOpenTheSelectedRoundAndCancelledTouchesDoNothing() {
        Fixture fixture = new Fixture(2);
        fixture.round(1, ScoringStrategy.defaults(), true);
        fixture.round(2, ScoringStrategy.aggressive(), true);
        AtomicReference<GameStatistics.TimelineEvent> selected = new AtomicReference<>();
        ScoreGraphView graph = graph(GameStatistics.from(fixture.snapshot()), set(1L),
                ScoreGraphView.Mode.CUMULATIVE, selected);
        render(graph);
        AccessibilityNodeProvider provider = graph.getAccessibilityNodeProvider();
        Rect target = new Rect();
        provider.createAccessibilityNodeInfo(1).getBoundsInParent(target);
        touch(graph, MotionEvent.ACTION_DOWN, target.centerX(), target.centerY());
        touch(graph, MotionEvent.ACTION_CANCEL, target.centerX(), target.centerY());
        assertEquals(null, selected.get());
        touch(graph, MotionEvent.ACTION_DOWN, target.centerX(), target.centerY());
        touch(graph, MotionEvent.ACTION_UP, target.centerX(), target.centerY());
        assertEquals(2, selected.get().round.number);
        provider.performAction(0, AccessibilityNodeInfoCompat.ACTION_FOCUS, null);
        assertTrue(provider.createAccessibilityNodeInfo(0).isFocused());
        graph.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        assertEquals(1, selected.get().round.number);
    }

    @Test
    @Config(qualifiers = "w320dp-h740dp-night-mdpi")
    public void fixedPlayerColorsStayReadableOnBothSurfacesAndDistinctForFiftyTwoPlayers() {
        Set<Integer> colors = new HashSet<>();
        for (long id = 1; id <= 52; id++) {
            int color = ScoreGraphView.colorForPlayer(id);
            assertTrue(ColorUtils.calculateContrast(color, Color.rgb(248, 247, 242)) >= 3.0);
            assertTrue(ColorUtils.calculateContrast(color, Color.rgb(23, 32, 38)) >= 3.0);
            colors.add(color);
        }
        assertEquals(52, colors.size());
        Fixture fixture = new Fixture(2);
        fixture.round(1, ScoringStrategy.aggressive(), true);
        ScoreGraphView graph = graph(GameStatistics.from(fixture.snapshot()), set(1L, 2L),
                ScoreGraphView.Mode.POINTS_PER_ROUND, new AtomicReference<>());
        assertTrue(pixels(render(graph), ScoreGraphView.colorForPlayer(1L)) > 0);
        assertFalse(ScoreGraphView.playerPattern(1L).equals(ScoreGraphView.playerPattern(2L)));
    }

    @Test
    @Config(qualifiers = "w320dp-h740dp-notnight-mdpi")
    public void largeFontsKeepWrappedStrategyCaptionsInsideMeasuredGraph() {
        RuntimeEnvironment.setFontScale(2f);
        Fixture fixture = new Fixture(2);
        for (int round = 1; round <= 10; round++) {
            fixture.round(round, round % 2 == 0 ? ScoringStrategy.aggressive()
                    : ScoringStrategy.defaults(), true);
        }
        ScoreGraphView graph = graph(GameStatistics.from(fixture.snapshot()), set(1L),
                ScoreGraphView.Mode.POINTS_PER_ROUND, new AtomicReference<>());
        Bitmap bitmap = render(graph);
        assertTrue(graph.getHeight() > 400);
        assertTrue(pixels(bitmap, ScoreGraphView.colorForPlayer(1L)) > 20);
        AccessibilityNodeInfo tenth = graph.getAccessibilityNodeProvider()
                .createAccessibilityNodeInfo(9);
        Rect bounds = new Rect();
        tenth.getBoundsInParent(bounds);
        assertTrue(bounds.bottom < graph.getHeight());
        assertTrue(tenth.getContentDescription().toString().contains("Judgement+ scoring"));
    }

    @Test
    public void emptyComparisonHasAnExplanationAndNoInaccessibleClickableEvents() {
        Fixture fixture = new Fixture(2);
        fixture.round(1, ScoringStrategy.defaults(), true);
        ScoreGraphView graph = graph(GameStatistics.from(fixture.snapshot()), Collections.emptySet(),
                ScoreGraphView.Mode.CUMULATIVE, new AtomicReference<>());
        render(graph);
        assertTrue(graph.getContentDescription().toString().contains("Select players to compare"));
        assertEquals(0, graph.getAccessibilityNodeProvider()
                .createAccessibilityNodeInfo(View.NO_ID).getChildCount());
        assertFalse(graph.getAccessibilityNodeProvider()
                .createAccessibilityNodeInfo(View.NO_ID).isClickable());
        assertFalse(touch(graph, MotionEvent.ACTION_DOWN, 160f, 100f));
    }

    @Test
    public void rebindingAShorterTimelineClearsFocusOnTheRemovedVirtualPoint() {
        Fixture fixture = new Fixture(2);
        for (int number = 1; number <= 4; number++) {
            fixture.round(number, ScoringStrategy.defaults(), true);
        }
        ScoreGraphView graph = graph(GameStatistics.from(fixture.snapshot()), set(1L),
                ScoreGraphView.Mode.CUMULATIVE, new AtomicReference<>());
        render(graph);
        AccessibilityNodeProvider provider = graph.getAccessibilityNodeProvider();
        assertTrue(provider.performAction(3, AccessibilityNodeInfoCompat.ACTION_FOCUS, null));
        fixture.history.subList(1, 4).clear();
        graph.setData(GameStatistics.from(fixture.snapshot()), set(1L),
                ScoreGraphView.Mode.CUMULATIVE, event -> { });
        render(graph);
        assertEquals(1, provider.createAccessibilityNodeInfo(View.NO_ID).getChildCount());
        assertTrue(provider.performAction(0, AccessibilityNodeInfoCompat.ACTION_FOCUS, null));
    }

    private ScoreGraphView graph(GameStatistics report, Set<Long> selected,
            ScoreGraphView.Mode mode, AtomicReference<GameStatistics.TimelineEvent> selection) {
        controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        activity.setTheme(R.style.Theme_Score);
        ScoreGraphView graph = new ScoreGraphView(activity);
        graph.setData(report, selected, mode, selection::set);
        activity.setContentView(graph, new ViewGroup.LayoutParams(-1, -2));
        graph.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        graph.layout(0, 0, graph.getMeasuredWidth(), graph.getMeasuredHeight());
        return graph;
    }

    private static Bitmap render(ScoreGraphView graph) {
        Bitmap bitmap = Bitmap.createBitmap(graph.getWidth(), graph.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(248, 247, 242));
        graph.draw(canvas);
        return bitmap;
    }

    private static int pixels(Bitmap bitmap, int color) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (bitmap.getPixel(x, y) == color) count++;
            }
        }
        return count;
    }

    private static boolean touch(ScoreGraphView graph, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 10L, action, x, y, 0);
        try { return graph.onTouchEvent(event); }
        finally { event.recycle(); }
    }

    private static Set<Long> set(Long... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private static final class Fixture {
        final List<Records.GamePlayer> players = new ArrayList<>();
        final List<Snapshot.RoundHistory> history = new ArrayList<>();
        final List<Records.ScoreAdjustment> adjustments = new ArrayList<>();

        Fixture(int count) {
            for (int index = 0; index < count; index++) {
                Records.GamePlayer player = new Records.GamePlayer();
                player.id = index + 1L; player.seat = index; player.name = "Complete name " + player.id;
                players.add(player);
            }
        }

        void round(int number, ScoringStrategy scoring, boolean includeSecond) {
            Records.Round round = new Records.Round();
            round.id = number; round.number = number; round.cards = number; round.phase = "COMPLETE";
            round.completedAt = number * 100L; round.scoringConfig = scoring.encode();
            List<Records.RoundPlayer> rows = new ArrayList<>();
            for (int index = 0; index < (includeSecond ? 2 : 1); index++) {
                Records.RoundPlayer row = new Records.RoundPlayer();
                row.roundId = round.id; row.playerId = index + 1L; row.bid = 0;
                row.nameSnapshot = players.get(index).name; row.bidPosition = index;
                row.outcome = (index == 0 ? Outcome.WON : Outcome.LOST).name();
                rows.add(row);
            }
            history.add(new Snapshot.RoundHistory(round, rows));
        }

        void adjust(long delta, long at) {
            Records.ScoreAdjustment adjustment = new Records.ScoreAdjustment();
            adjustment.id = 1L; adjustment.playerId = 1L; adjustment.delta = delta;
            adjustment.nameSnapshot = players.get(0).name; adjustment.createdAt = at;
            adjustments.add(adjustment);
        }

        Snapshot snapshot() {
            return new Snapshot(null, Collections.emptyList(), new Records.Game(), players,
                    Collections.emptyList(), Collections.emptyMap(), null, Collections.emptyList(),
                    history, adjustments, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
    }
}
