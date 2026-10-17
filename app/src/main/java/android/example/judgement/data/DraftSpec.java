package android.example.judgement.data;

import android.example.judgement.domain.ScoringStrategy;
import java.util.ArrayList;
import java.util.List;

/** A partial setup is valid to save. Start validates the complete configuration. */
public final class DraftSpec {
    public final long id;
    public final List<PlayerSpec> players;
    public final String direction;
    public final String trumpMode;
    public final Long dealerId;
    /** Null preserves an existing draft's choice; a new draft defaults to Default. */
    public final ScoringStrategy scoringStrategy;
    public DraftSpec(long id, List<PlayerSpec> players, String direction, String trumpMode, Long dealerId) {
        this(id, players, direction, trumpMode, dealerId, null);
    }
    public DraftSpec(long id, List<PlayerSpec> players, String direction, String trumpMode,
                     Long dealerId, ScoringStrategy scoringStrategy) {
        this.id = id; this.players = new ArrayList<>(players); this.direction = direction;
        this.trumpMode = trumpMode; this.dealerId = dealerId;
        this.scoringStrategy = scoringStrategy;
    }
}
