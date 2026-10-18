package android.example.judgement.data;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TrumpCallerTest {
    @Test public void tiedLargestPredictionUsesFirstPredictionPositionRegardlessOfListOrder() {
        assertEquals(Long.valueOf(30), TrumpCaller.playerId("CUSTOM", Arrays.asList(
                player(20, 3, 4), player(30, 1, 4), player(10, 0, 2), player(40, 2, null))));
    }

    @Test public void editingPredictionMovesTrumpToCurrentLargestThenEarliestTie() {
        Records.RoundPlayer first = player(10, 0, 3), second = player(20, 1, 4);
        assertEquals(Long.valueOf(20), TrumpCaller.playerId("CUSTOM", Arrays.asList(first, second)));
        first.bid = 4;
        assertEquals(Long.valueOf(10), TrumpCaller.playerId("CUSTOM", Arrays.asList(first, second)));
        first.bid = 2;
        assertEquals(Long.valueOf(20), TrumpCaller.playerId("CUSTOM", Arrays.asList(first, second)));
    }

    @Test public void noPredictionsAndCyclicTrumpsHaveNoCaller() {
        assertNull(TrumpCaller.playerId("CUSTOM", Collections.emptyList()));
        assertNull(TrumpCaller.playerId("CUSTOM", Arrays.asList(player(10, 0, null))));
        assertNull(TrumpCaller.playerId("FIXED", Arrays.asList(player(10, 0, 4))));
        assertNull(TrumpCaller.playerId(null, Arrays.asList(player(10, 0, 4))));
    }

    @Test public void tiedZeroPredictionsStillSelectTheFirstCaller() {
        assertEquals(Long.valueOf(10), TrumpCaller.playerId("CUSTOM", Arrays.asList(
                player(20, 1, 0), player(10, 0, 0))));
    }

    private static Records.RoundPlayer player(long id, int position, Integer prediction) {
        Records.RoundPlayer player = new Records.RoundPlayer();
        player.playerId = id; player.bidPosition = position; player.bid = prediction;
        return player;
    }
}
