package android.example.judgement.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.domain.Names;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** A staged roster. A drag changes storage only once the user releases the handle. */
final class RosterAdapter extends RecyclerView.Adapter<RosterAdapter.Holder> {
    interface Listener {
        void onChanged(List<PlayerSpec> players);
        void rename(PlayerSpec player);
        void remove(PlayerSpec player);
    }

    private static final AtomicLong NEXT_TEMPORARY_ID = new AtomicLong(-2L);
    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;
    private final Context context;
    private final ItemTouchHelper touchHelper;
    private RecyclerView recyclerView;
    private Long dealerId;
    private long nextStableId = 1L;
    private List<Row> beforeDrag;
    private boolean releasedDrag;
    private boolean cancelledDrag;
    private boolean committedDrag;
    private long movingStableId = RecyclerView.NO_ID;
    private long pendingFocusId = RecyclerView.NO_ID;
    private ViewTreeObserver.OnGlobalLayoutListener pendingFocus;
    private boolean pendingAccessibilityFocus;
    private boolean movedAccessibilityFocus;

    RosterAdapter(Context context, List<PlayerSpec> players, Long dealerId, Listener listener) {
        if (context == null || listener == null) {
            throw new IllegalArgumentException("Roster context and callbacks are required.");
        }
        this.listener = listener;
        this.context = context;
        this.dealerId = dealerId;
        setHasStableIds(true);
        touchHelper = new ItemTouchHelper(new DragCallback());
        update(players, dealerId);
    }

    @SuppressLint("ClickableViewAccessibility") // Observes cancellation; never consumes or replaces a click.
    void attachTo(RecyclerView view) {
        attachTo(view, true);
    }

    @SuppressLint("ClickableViewAccessibility") // Observes cancellation; never consumes or replaces a click.
    void attachTo(RecyclerView view, boolean installAdapter) {
        if (recyclerView != null && recyclerView != view) {
            touchHelper.attachToRecyclerView(null);
            recyclerView.setOnTouchListener(null);
        }
        recyclerView = view;
        if (installAdapter) {
            view.setAdapter(this);
        }
        // ItemTouchHelper owns intercepted motion events. Observe their terminal event before
        // its clearView callback so a system cancellation cannot accidentally commit an order.
        view.setOnTouchListener((target, event) -> {
            if (beforeDrag != null) {
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL && !releasedDrag) {
                    cancelledDrag = true;
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    releasedDrag = true;
                    commitReleasedDrag();
                }
            }
            return false;
        });
        touchHelper.attachToRecyclerView(view);
    }

    @SuppressLint("ClickableViewAccessibility") // Removes the non-consuming terminal-event observer.
    void detach() {
        if (beforeDrag != null) {
            if (!committedDrag) {
                rows.clear();
                rows.addAll(beforeDrag);
            }
            beforeDrag = null;
        }
        cancelledDrag = true;
        touchHelper.attachToRecyclerView(null);
        if (recyclerView != null) {
            clearPendingFocus();
            recyclerView.setOnTouchListener(null);
            recyclerView = null;
        }
    }

    @SuppressLint("NotifyDataSetChanged") // At most 52 rows; rebinds names and available move actions together.
    void update(List<PlayerSpec> players, Long newDealerId) {
        if (players == null) {
            throw new IllegalArgumentException("The staged roster is required.");
        }
        long focusedId = pendingFocusId;
        boolean accessibilityFocused = pendingAccessibilityFocus;
        if (recyclerView != null && recyclerView.findFocus() != null) {
            RecyclerView.ViewHolder focused = recyclerView.findContainingViewHolder(
                    recyclerView.findFocus());
            if (focused instanceof Holder && focused.getBindingAdapter() == this
                    && focused.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                focusedId = rows.get(focused.getBindingAdapterPosition()).stableId;
                accessibilityFocused = ((Holder) focused).handle.isAccessibilityFocused();
            }
        }
        if (beforeDrag != null) {
            // A fresh repository snapshot wins over a gesture that began on older state.
            beforeDrag = null;
            cancelledDrag = true;
            if (recyclerView != null) {
                touchHelper.attachToRecyclerView(null);
                touchHelper.attachToRecyclerView(recyclerView);
            }
        }
        List<Row> previous = new ArrayList<>(rows);
        Set<Long> occupiedIds = new HashSet<>();
        for (PlayerSpec player : players) {
            if (player.id != 0L) {
                occupiedIds.add(player.id);
            }
        }
        rows.clear();
        for (PlayerSpec player : players) {
            Row prior = findPrior(previous, player);
            if (prior != null) {
                previous.remove(prior);
            }
            PlayerSpec staged = player;
            if (staged.id == 0L) {
                long temporaryId = prior != null ? prior.player.id : nextTemporaryId(occupiedIds);
                staged = new PlayerSpec(temporaryId, player.name, player.savedPlayerId, player.saveForFuture);
                occupiedIds.add(temporaryId);
            }
            rows.add(new Row(prior == null ? nextStableId++ : prior.stableId, staged));
        }
        dealerId = newDealerId;
        notifyDataSetChanged();
        int focusedPosition = indexOfStableId(focusedId);
        if (focusedPosition >= 0) {
            focusAfterLayout(focusedId, focusedPosition, false, accessibilityFocused);
        }
    }

    @Override public long getItemId(int position) {
        return rows.get(position).stableId;
    }

    @Override public int getItemCount() {
        return rows.size();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.roster_row, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        PlayerSpec player = rows.get(position).player;
        holder.name.setText(player.name);
        holder.name.setTooltipText(player.name);
        holder.detail.setText("");
        holder.detail.setVisibility(View.GONE);
        holder.handle.setContentDescription(context.getString(R.string.drag_player, player.name));
        holder.handle.setFocusableInTouchMode(true);
        holder.handle.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    && holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION
                    && rows.size() > 1 && beforeDrag == null) {
                view.performClick();
                touchHelper.startDrag(holder);
            }
            return false;
        });
        holder.handle.setOnClickListener(view -> {
            // Accessibility users use the labelled Move up / Move down actions below.
        });
        holder.handle.setEnabled(rows.size() > 1);
        MaterialButton rename = holder.rename;
        rename.setContentDescription(context.getString(R.string.rename) + " " + player.name);
        rename.setOnClickListener(view -> {
            int current = holder.getBindingAdapterPosition();
            if (current != RecyclerView.NO_POSITION && beforeDrag == null) {
                listener.rename(rows.get(current).player);
            }
        });
        MaterialButton remove = holder.remove;
        remove.setContentDescription(context.getString(R.string.remove) + " " + player.name);
        remove.setOnClickListener(view -> {
            int current = holder.getBindingAdapterPosition();
            if (current != RecyclerView.NO_POSITION && beforeDrag == null) {
                listener.remove(rows.get(current).player);
            }
        });
        bindMoveActions(holder, position);
    }

    private void bindMoveActions(Holder holder, int position) {
        if (holder.moveUpAction != -1) {
            ViewCompat.removeAccessibilityAction(holder.handle, holder.moveUpAction);
        }
        if (holder.moveDownAction != -1) {
            ViewCompat.removeAccessibilityAction(holder.handle, holder.moveDownAction);
        }
        holder.moveUpAction = -1;
        holder.moveDownAction = -1;
        if (position > 0) {
            holder.moveUpAction = ViewCompat.addAccessibilityAction(holder.handle,
                    context.getString(R.string.move_up),
                    (view, args) -> moveAccessibly(holder, -1));
        }
        if (position < rows.size() - 1) {
            holder.moveDownAction = ViewCompat.addAccessibilityAction(holder.handle,
                    context.getString(R.string.move_down),
                    (view, args) -> moveAccessibly(holder, 1));
        }
    }

    private boolean moveAccessibly(Holder holder, int delta) {
        int from = holder.getBindingAdapterPosition();
        int to = from + delta;
        if (beforeDrag != null || from == RecyclerView.NO_POSITION || to < 0 || to >= rows.size()) {
            return false;
        }
        Row moved = rows.remove(from);
        movedAccessibilityFocus = holder.handle.isAccessibilityFocused();
        rows.add(to, moved);
        notifyItemMoved(from, to);
        notifyItemRangeChanged(Math.min(from, to), Math.abs(to - from) + 1);
        listener.onChanged(snapshot());
        focusAndAnnounce(moved.stableId, to);
        return true;
    }

    private void updateVisibleSeats() {
        if (recyclerView == null) {
            return;
        }
        for (int position = 0; position < rows.size(); position++) {
            RecyclerView.ViewHolder visible = recyclerView.findViewHolderForAdapterPosition(globalPosition(position));
            if (visible instanceof Holder) {
                Holder holder = (Holder) visible;
                bindMoveActions(holder, position);
            }
        }
    }

    private void focusAndAnnounce(long stableId, int position) {
        focusAfterLayout(stableId, position, true, movedAccessibilityFocus);
    }

    @SuppressLint("AccessibilityFocus") // Restores existing focus to the same player after its handle is rebound.
    private void focusAfterLayout(long stableId, int position, boolean announce,
            boolean restoreAccessibilityFocus) {
        if (recyclerView == null) {
            return;
        }
        clearPendingFocus();
        RecyclerView view = recyclerView;
        pendingFocusId = stableId;
        pendingAccessibilityFocus = restoreAccessibilityFocus;
        view.scrollToPosition(globalPosition(position));
        pendingFocus = () -> {
            if (view != recyclerView || view.hasPendingAdapterUpdates()) {
                return;
            }
            RecyclerView.ViewHolder visible = visibleRosterHolder(stableId);
            if (visible instanceof Holder) {
                Holder holder = (Holder) visible;
                clearPendingFocus();
                holder.handle.requestFocus();
                if (restoreAccessibilityFocus) {
                    holder.handle.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
                }
                if (announce) {
                    holder.handle.announceForAccessibility(context.getString(R.string.move_announcement,
                            rows.get(position).player.name, position + 1));
                }
            }
        };
        view.getViewTreeObserver().addOnGlobalLayoutListener(pendingFocus);
        view.post(() -> {
            if (pendingFocus != null) {
                pendingFocus.onGlobalLayout();
            }
        });
    }

    private void clearPendingFocus() {
        if (pendingFocus != null && recyclerView != null
                && recyclerView.getViewTreeObserver().isAlive()) {
            recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(pendingFocus);
        }
        pendingFocus = null;
        pendingFocusId = RecyclerView.NO_ID;
        pendingAccessibilityFocus = false;
    }

    private List<PlayerSpec> snapshot() {
        List<PlayerSpec> players = new ArrayList<>();
        for (Row row : rows) {
            players.add(row.player);
        }
        return Collections.unmodifiableList(players);
    }

    private Row findPrior(List<Row> previous, PlayerSpec incoming) {
        for (Row row : previous) {
            if (incoming.id != 0L && incoming.id == row.player.id) {
                return row;
            }
        }
        if (incoming.id == 0L) {
            for (Row row : previous) {
                if (row.player == incoming || Names.key(row.player.name).equals(Names.key(incoming.name))) {
                    return row;
                }
            }
        }
        return null;
    }

    private static long nextTemporaryId(Set<Long> occupied) {
        long candidate;
        do {
            candidate = NEXT_TEMPORARY_ID.getAndDecrement();
        } while (occupied.contains(candidate) || candidate == RecyclerView.NO_ID);
        return candidate;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private final class DragCallback extends ItemTouchHelper.Callback {
        @Override public int getMovementFlags(@NonNull RecyclerView view,
                @NonNull RecyclerView.ViewHolder holder) {
            boolean rosterRow = holder instanceof Holder && holder.getBindingAdapter() == RosterAdapter.this;
            return makeMovementFlags(rosterRow && rows.size() > 1
                    ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0, 0);
        }

        @Override public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override public void onSelectedChanged(RecyclerView.ViewHolder holder, int state) {
            if (state == ItemTouchHelper.ACTION_STATE_DRAG && holder instanceof Holder
                    && holder.getBindingAdapter() == RosterAdapter.this) {
                beforeDrag = new ArrayList<>(rows);
                releasedDrag = false;
                cancelledDrag = false;
                committedDrag = false;
                movingStableId = rows.get(holder.getBindingAdapterPosition()).stableId;
                movedAccessibilityFocus = ((Holder) holder).handle.isAccessibilityFocused();
            }
            super.onSelectedChanged(holder, state);
        }

        @Override public boolean onMove(@NonNull RecyclerView view,
                @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
            int from = source.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            if (source.getBindingAdapter() != RosterAdapter.this
                    || target.getBindingAdapter() != RosterAdapter.this
                    || !(source instanceof Holder) || !(target instanceof Holder)
                    || from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || from == to) {
                return false;
            }
            rows.add(to, rows.remove(from));
            notifyItemMoved(from, to);
            updateVisibleSeats();
            return true;
        }

        @Override public int interpolateOutOfBoundsScroll(@NonNull RecyclerView view,
                int viewSize, int outOfBounds, int totalSize, long elapsed) {
            int base = super.interpolateOutOfBoundsScroll(view, viewSize, outOfBounds,
                    totalSize, elapsed);
            return Math.max(-dp(24), Math.min(dp(24), base));
        }

        @SuppressLint("NotifyDataSetChanged") // Cancellation restores the complete bounded roster without committing it.
        @Override public void clearView(@NonNull RecyclerView view,
                @NonNull RecyclerView.ViewHolder holder) {
            super.clearView(view, holder);
            if (beforeDrag == null) {
                return;
            }
            List<Row> original = beforeDrag;
            beforeDrag = null;
            boolean changed = !sameOrder(original, rows);
            if (cancelledDrag || !releasedDrag) {
                rows.clear();
                rows.addAll(original);
                notifyDataSetChanged();
                return;
            }
            if (changed) {
                if (!committedDrag) {
                    committedDrag = true;
                    listener.onChanged(snapshot());
                }
                int position = indexOfStableId(movingStableId);
                if (position >= 0) {
                    focusAndAnnounce(movingStableId, position);
                }
            }
        }

        @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
            // Roster removal always uses its explicit labelled action.
        }
    }

    private int indexOfStableId(long id) {
        for (int position = 0; position < rows.size(); position++) {
            if (rows.get(position).stableId == id) {
                return position;
            }
        }
        return -1;
    }

    private void commitReleasedDrag() {
        if (beforeDrag != null && !committedDrag && !cancelledDrag
                && !sameOrder(beforeDrag, rows)) {
            committedDrag = true;
            // A released drop is accepted now; navigation must not cancel it while the
            // ItemTouchHelper recovery animation is still returning the card to its seat.
            listener.onChanged(snapshot());
        }
    }

    private Holder visibleRosterHolder(long stableId) {
        if (recyclerView == null) return null;
        for (int child = 0; child < recyclerView.getChildCount(); child++) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(child));
            int position = holder.getBindingAdapterPosition();
            if (holder instanceof Holder && holder.getBindingAdapter() == this
                    && position != RecyclerView.NO_POSITION && position < rows.size()
                    && rows.get(position).stableId == stableId) {
                return (Holder) holder;
            }
        }
        return null;
    }

    private int globalPosition(int localPosition) {
        if (recyclerView == null) return localPosition;
        int offset = adapterOffset(recyclerView.getAdapter(), this, 0);
        return offset < 0 ? localPosition : offset + localPosition;
    }

    private static int adapterOffset(RecyclerView.Adapter<?> parent,
            RecyclerView.Adapter<?> child, int offset) {
        if (parent == child) return offset;
        if (parent instanceof ConcatAdapter) {
            int position = offset;
            for (RecyclerView.Adapter<?> current : ((ConcatAdapter) parent).getAdapters()) {
                int found = adapterOffset(current, child, position);
                if (found >= 0) return found;
                position += current.getItemCount();
            }
        }
        return -1;
    }

    private static boolean sameOrder(List<Row> first, List<Row> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int position = 0; position < first.size(); position++) {
            if (first.get(position).stableId != second.get(position).stableId) {
                return false;
            }
        }
        return true;
    }

    private static final class Row {
        final long stableId;
        final PlayerSpec player;
        Row(long stableId, PlayerSpec player) {
            this.stableId = stableId;
            this.player = player;
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView detail;
        final LinearLayout actions;
        final MaterialButton handle, rename, remove;
        int moveUpAction = -1;
        int moveDownAction = -1;
        Holder(View item) {
            super(item);
            name = item.findViewById(R.id.row_name);
            detail = item.findViewById(R.id.row_detail);
            actions = item.findViewById(R.id.row_actions);
            handle = item.findViewById(R.id.row_drag_handle);
            rename = item.findViewById(R.id.roster_rename);
            remove = item.findViewById(R.id.roster_remove);
        }
    }
}
