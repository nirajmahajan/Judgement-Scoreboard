package android.example.judgement.data;

/** Staged roster row. A zero ID creates a participant; nonzero IDs preserve existing identity. */
public final class PlayerSpec {
    public final long id;
    public final String name;
    public final Long savedPlayerId;
    /** Deferred intent: created only when a new staged participant is successfully applied. */
    public final boolean saveForFuture;
    public PlayerSpec(long id, String name, Long savedPlayerId, boolean saveForFuture) {
        this.id = id; this.name = name; this.savedPlayerId = savedPlayerId; this.saveForFuture = saveForFuture;
    }
    public PlayerSpec(long id, String name, Long savedPlayerId) { this(id, name, savedPlayerId, false); }
    public PlayerSpec(long id, String name) { this(id, name, null); }
    public PlayerSpec(String name) { this(0, name, null); }
}
