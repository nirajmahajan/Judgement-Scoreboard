package android.example.judgement.ui;

import android.example.judgement.data.PlayerSpec;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Exercises real intercepted motion events, including cancellation and edge scrolling. */
@RunWith(AndroidJUnit4.class)
public class RosterGestureTest {
    private ActivityScenario<MainActivity> scenario;
    private RecyclerView recycler;
    private RosterAdapter adapter;
    private int rosterOffset;
    private final AtomicInteger commits = new AtomicInteger();
    private final AtomicReference<List<PlayerSpec>> committed = new AtomicReference<>();
    private final AtomicReference<PlayerSpec> renamed = new AtomicReference<>();
    private final AtomicReference<PlayerSpec> removed = new AtomicReference<>();

    @After public void close() {
        if (scenario != null) {
            if (adapter != null) {
                scenario.onActivity(activity -> adapter.detach());
            }
            RepositoryTestCleanup.close(scenario, null);
        }
    }

    @Test public void successfulDragCommitsCompleteOrderExactlyOnceAndKeepsDealerIdentity() {
        launch(4);
        float[] start = handleCenter(0);
        float[] destination = handleCenter(1);
        destination[1] += dp(24); // Cross the destination boundary, rather than stopping exactly on it.
        long down = beginDrag(start);
        move(down, start, destination, 12);
        assertEquals("Hovering must not persist an order", 0, commits.get());
        inject(down, MotionEvent.ACTION_UP, destination[0], destination[1]);
        await(() -> commits.get() == 1);
        assertEquals(1, commits.get());
        assertEquals(4, committed.get().size());
        assertEquals(101L, committed.get().get(0).id);
        assertEquals(100L, committed.get().get(1).id);
        assertEquals(102L, committed.get().get(2).id);
        scenario.onActivity(activity -> {
            RosterAdapter.Holder dealer = (RosterAdapter.Holder)
                    recycler.findViewHolderForAdapterPosition(1 + rosterOffset);
            assertNotNull(dealer);
            assertEquals("Player 1", dealer.name.getText().toString());
            assertEquals(View.GONE, dealer.detail.getVisibility());
            RosterAdapter.Holder first = (RosterAdapter.Holder)
                    recycler.findViewHolderForAdapterPosition(rosterOffset);
            assertNotNull(first);
            assertTrue(clickLabel(first.actions, "Rename Player 2"));
            assertTrue(clickLabel(first.actions, "Remove Player 2"));
        });
        assertEquals("Callbacks carry the reordered player's ID before any storage snapshot",
                101L, renamed.get().id);
        assertEquals(101L, removed.get().id);
    }

    @Test public void cancelledDragRestoresTheEntirePriorOrderWithoutCommitting() {
        launch(4);
        float[] start = handleCenter(0);
        float[] destination = handleCenter(1);
        destination[1] += dp(24);
        long originalFirstId = adapterId(0);
        long down = beginDrag(start);
        move(down, start, destination, 12);
        await(() -> adapterId(0) != originalFirstId);
        inject(down, MotionEvent.ACTION_CANCEL, destination[0], destination[1]);
        await(() -> adapterId(0) == originalFirstId);
        assertEquals(0, commits.get());
        scenario.onActivity(activity -> {
            RosterAdapter.Holder first = (RosterAdapter.Holder)
                    recycler.findViewHolderForAdapterPosition(rosterOffset);
            assertNotNull(first);
            assertEquals("Player 1", first.name.getText().toString());
            assertEquals(View.GONE, first.detail.getVisibility());
        });
    }

    @Test public void releasedDropCommitsBeforeImmediateNavigationDetachesTheRoster() {
        launch(4);
        float[] start = handleCenter(0);
        float[] destination = handleCenter(1);
        destination[1] += dp(24);
        long down = beginDrag(start);
        move(down, start, destination, 12);
        inject(down, MotionEvent.ACTION_UP, destination[0], destination[1]);
        scenario.onActivity(activity -> adapter.detach());
        assertEquals("Accepted drop cannot be lost during its recovery animation", 1, commits.get());
        assertEquals(101L, committed.get().get(0).id);
        assertEquals(100L, committed.get().get(1).id);
    }

    @Test public void handleAtTheBottomAutoScrollsBeyondInitiallyVisiblePlayers() {
        launch(14);
        float[] start = handleCenter(0);
        AtomicInteger initiallyVisibleLast = new AtomicInteger();
        AtomicReference<float[]> edge = new AtomicReference<>();
        scenario.onActivity(activity -> {
            initiallyVisibleLast.set(((LinearLayoutManager) recycler.getLayoutManager())
                    .findLastVisibleItemPosition() - rosterOffset);
            int[] location = new int[2];
            recycler.getLocationOnScreen(location);
            edge.set(new float[] {start[0], location[1] + recycler.getHeight() - 12});
        });
        long down = beginDrag(start);
        move(down, start, edge.get(), 18);
        long until = SystemClock.uptimeMillis() + 2_200;
        while (SystemClock.uptimeMillis() < until) {
            inject(down, MotionEvent.ACTION_MOVE, edge.get()[0], edge.get()[1]);
            SystemClock.sleep(32);
        }
        AtomicInteger offset = new AtomicInteger();
        scenario.onActivity(activity -> offset.set(recycler.computeVerticalScrollOffset()));
        assertTrue("A held edge drag must scroll the roster", offset.get() > 0);
        assertEquals(0, commits.get());
        inject(down, MotionEvent.ACTION_UP, edge.get()[0], edge.get()[1]);
        await(() -> commits.get() == 1);
        int destination = -1;
        for (int index = 0; index < committed.get().size(); index++) {
            if (committed.get().get(index).id == 100L) {
                destination = index;
            }
        }
        assertTrue("Dragged player must pass initially hidden rows", destination > initiallyVisibleLast.get());
        assertEquals(14, committed.get().size());
    }

    @Test public void accessibleMovesDoNotWrapAndRestoreFocusAfterSnapshotRebinding() {
        launch(3);
        AtomicInteger moveDown = new AtomicInteger();
        scenario.onActivity(activity -> {
            RosterAdapter.Holder first = (RosterAdapter.Holder)
                    recycler.findViewHolderForAdapterPosition(rosterOffset);
            assertNotNull(first);
            AccessibilityNodeInfo info = first.handle.createAccessibilityNodeInfo();
            boolean hasUp = false;
            for (AccessibilityNodeInfo.AccessibilityAction action : info.getActionList()) {
                if ("Move up".equals(action.getLabel() == null ? null : action.getLabel().toString())) {
                    hasUp = true;
                }
                if ("Move down".equals(action.getLabel() == null ? null : action.getLabel().toString())) {
                    moveDown.set(action.getId());
                }
            }
            assertFalse("First seat cannot wrap to last", hasUp);
            assertTrue(moveDown.get() != 0);
            assertTrue(first.handle.performAccessibilityAction(moveDown.get(), new Bundle()));
        });
        await(() -> commits.get() == 1);
        assertEquals(101L, committed.get().get(0).id);
        assertEquals(100L, committed.get().get(1).id);
        scenario.onActivity(activity -> adapter.update(committed.get(), 100L));
        await(() -> {
            AtomicReference<Boolean> focused = new AtomicReference<>(false);
            scenario.onActivity(activity -> {
                RosterAdapter.Holder moved = (RosterAdapter.Holder)
                        recycler.findViewHolderForAdapterPosition(1 + rosterOffset);
                focused.set(moved != null && moved.handle.hasFocus());
            });
            return focused.get();
        });
        scenario.onActivity(activity -> {
            RosterAdapter.Holder last = (RosterAdapter.Holder)
                    recycler.findViewHolderForAdapterPosition(2 + rosterOffset);
            assertNotNull(last);
            for (AccessibilityNodeInfo.AccessibilityAction action
                    : last.handle.createAccessibilityNodeInfo().getActionList()) {
                assertFalse("Last seat cannot wrap to first",
                        "Move down".equals(action.getLabel() == null ? null : action.getLabel().toString()));
            }
        });
    }

    @Test public void isolatedStableIdsKeepHeaderOutsideRosterAndRestoreMovedPlayerFocus() {
        launch(3, true);
        float[] start = handleCenter(0);
        float[] header = start.clone();
        scenario.onActivity(activity -> {
            int[] location = new int[2];
            recycler.getLocationOnScreen(location);
            header[1] = location[1] + dp(24);
        });
        long firstId = adapterId(0);
        long down = beginDrag(start);
        move(down, start, header, 12);
        inject(down, MotionEvent.ACTION_UP, header[0], header[1]);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals("A header is never a destination seat", 0, commits.get());
        assertEquals(firstId, adapterId(0));
        scenario.onActivity(activity -> {
            RosterAdapter.Holder first = (RosterAdapter.Holder)
                    recycler.findViewHolderForAdapterPosition(rosterOffset);
            assertNotNull(first);
            for (AccessibilityNodeInfo.AccessibilityAction action
                    : first.handle.createAccessibilityNodeInfo().getActionList()) {
                if ("Move down".equals(action.getLabel() == null ? null : action.getLabel().toString())) {
                    assertTrue(first.handle.performAccessibilityAction(action.getId(), new Bundle()));
                    return;
                }
            }
            throw new AssertionError("Move down action is available");
        });
        await(() -> commits.get() == 1);
        assertEquals(101L, committed.get().get(0).id);
        assertEquals(100L, committed.get().get(1).id);
        scenario.onActivity(activity -> adapter.update(committed.get(), 100L));
        await(() -> {
            AtomicReference<Boolean> focused = new AtomicReference<>(false);
            scenario.onActivity(activity -> {
                RosterAdapter.Holder moved = (RosterAdapter.Holder)
                        recycler.findViewHolderForAdapterPosition(1 + rosterOffset);
                focused.set(moved != null && moved.handle.hasFocus());
            });
            return focused.get();
        });
    }

    private void launch(int playerCount) {
        launch(playerCount, false);
    }

    private void launch(int playerCount, boolean wrapped) {
        rosterOffset = wrapped ? 2 : 0;
        scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(activity -> {
            FrameLayout root = new FrameLayout(activity);
            ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
                androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
            recycler = new RecyclerView(activity);
            recycler.setLayoutManager(new LinearLayoutManager(activity));
            recycler.setItemAnimator(null);
            root.addView(recycler, new FrameLayout.LayoutParams(-1,
                    Math.round(480 * activity.getResources().getDisplayMetrics().density)));
            activity.setContentView(root);
            ViewCompat.requestApplyInsets(root);
            List<PlayerSpec> players = new ArrayList<>();
            for (int index = 0; index < playerCount; index++) {
                players.add(new PlayerSpec(100L + index, "Player " + (index + 1)));
            }
            adapter = new RosterAdapter(activity, players, 100L, new RosterAdapter.Listener() {
                @Override public void onChanged(List<PlayerSpec> order) {
                    committed.set(new ArrayList<>(order));
                    commits.incrementAndGet();
                }
                @Override public void rename(PlayerSpec player) { renamed.set(player); }
                @Override public void remove(PlayerSpec player) { removed.set(player); }
            });
            if (wrapped) {
                ConcatAdapter.Config config = new ConcatAdapter.Config.Builder()
                        .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS).build();
                recycler.setAdapter(new ConcatAdapter(config,
                        new SurroundingAdapter("Header", 2), adapter,
                        new SurroundingAdapter("Footer", 1)));
            }
            adapter.attachTo(recycler, !wrapped);
        });
        await(() -> {
            AtomicReference<Boolean> laidOut = new AtomicReference<>(false);
            scenario.onActivity(activity -> laidOut.set(
                    recycler.findViewHolderForAdapterPosition(1 + rosterOffset) != null));
            return laidOut.get();
        });
    }

    private float[] handleCenter(int position) {
        AtomicReference<float[]> center = new AtomicReference<>();
        scenario.onActivity(activity -> {
            RosterAdapter.Holder holder = (RosterAdapter.Holder)
                    recycler.findViewHolderForAdapterPosition(position + rosterOffset);
            assertNotNull("Required drag row is visible", holder);
            int[] location = new int[2];
            holder.handle.getLocationOnScreen(location);
            center.set(new float[] {location[0] + holder.handle.getWidth() / 2f,
                    location[1] + holder.handle.getHeight() / 2f});
        });
        return center.get();
    }

    private long adapterId(int index) {
        AtomicReference<Long> value = new AtomicReference<>();
        scenario.onActivity(activity -> value.set(adapter.getItemId(index)));
        return value.get();
    }

    private float dp(int value) {
        return value * InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getResources().getDisplayMetrics().density;
    }

    private static long beginDrag(float[] start) {
        long down = SystemClock.uptimeMillis();
        inject(down, MotionEvent.ACTION_DOWN, start[0], start[1]);
        return down;
    }

    private static void move(long down, float[] from, float[] to, int steps) {
        for (int step = 1; step <= steps; step++) {
            float fraction = (float) step / steps;
            inject(down, MotionEvent.ACTION_MOVE, from[0] + (to[0] - from[0]) * fraction,
                    from[1] + (to[1] - from[1]) * fraction);
            SystemClock.sleep(16);
        }
    }

    private static void inject(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try {
            assertTrue("Input dispatcher accepted the gesture", InstrumentationRegistry
                    .getInstrumentation().getUiAutomation().injectInputEvent(event, true));
        } finally {
            event.recycle();
        }
    }

    private interface Condition { boolean test(); }

    private static boolean clickLabel(ViewGroup parent, String label) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            if (child.getContentDescription() != null && label.contentEquals(child.getContentDescription())) {
                return child.performClick();
            }
            if (child instanceof ViewGroup && clickLabel((ViewGroup) child, label)) return true;
        }
        return false;
    }

    private static final class SurroundingAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final String label;
        private final int count;
        SurroundingAdapter(String label, int count) {
            this.label = label;
            this.count = count;
            setHasStableIds(true);
        }
        @Override public long getItemId(int position) { return 500L + position; }
        @Override public int getItemCount() { return count; }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType) {
            TextView text = new TextView(parent.getContext());
            text.setMinHeight(Math.round(48 * parent.getResources().getDisplayMetrics().density));
            return new RecyclerView.ViewHolder(text) { };
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((TextView) holder.itemView).setText(label + " " + (position + 1));
        }
    }

    private static void await(Condition condition) {
        long deadline = SystemClock.uptimeMillis() + 5_000;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            if (condition.test()) {
                return;
            }
            SystemClock.sleep(16);
        }
        assertTrue("Condition became true within five seconds", condition.test());
    }
}
