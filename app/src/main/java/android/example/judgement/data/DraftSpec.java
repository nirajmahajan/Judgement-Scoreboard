package android.example.judgement.data;

import java.util.ArrayList;
import java.util.List;

/** A partial setup is valid to save. Start validates the complete configuration. */
public final class DraftSpec {
    public final long id;
    public final List<PlayerSpec> players;
    public final String direction;
    public final String trumpMode;
    public final Long dealerId;
    public DraftSpec(long id, List<PlayerSpec> players, String direction, String trumpMode, Long dealerId) {
        this.id = id; this.players = new ArrayList<>(players); this.direction = direction;
        this.trumpMode = trumpMode; this.dealerId = dealerId;
    }
}
