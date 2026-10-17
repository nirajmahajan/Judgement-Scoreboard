package android.example.judgement.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ScoreDao {
    @Query("SELECT * FROM games ORDER BY id DESC") List<Records.Game> games();
    @Query("SELECT * FROM games WHERE id=:id") Records.Game game(long id);
    @Insert long insertGame(Records.Game game);
    @Update void updateGame(Records.Game game);
    @Query("SELECT * FROM game_players WHERE gameId=:gameId ORDER BY seat,id") List<Records.GamePlayer> players(long gameId);
    @Query("SELECT * FROM game_players WHERE id=:id") Records.GamePlayer player(long id);
    @Insert long insertPlayer(Records.GamePlayer player);
    @Update void updatePlayer(Records.GamePlayer player);
    @Query("DELETE FROM game_players WHERE gameId=:gameId AND active=0") void deleteInactiveDraftPlayers(long gameId);
    @Query("SELECT * FROM rounds WHERE gameId=:gameId ORDER BY number,id") List<Records.Round> rounds(long gameId);
    @Query("SELECT * FROM rounds WHERE id=:id") Records.Round round(long id);
    @Insert long insertRound(Records.Round round);
    @Update void updateRound(Records.Round round);
    @Query("SELECT * FROM round_players WHERE roundId=:roundId ORDER BY bidPosition,playerId") List<Records.RoundPlayer> roundPlayers(long roundId);
    @Insert void insertRoundPlayer(Records.RoundPlayer player);
    @Update void updateRoundPlayer(Records.RoundPlayer player);
    @Query("SELECT * FROM score_adjustments WHERE gameId=:gameId ORDER BY id") List<Records.ScoreAdjustment> adjustments(long gameId);
    @Insert long insertAdjustment(Records.ScoreAdjustment adjustment);
    @Query("SELECT * FROM saved_players ORDER BY nameKey,id") List<Records.SavedPlayer> savedPlayers();
    @Query("SELECT * FROM saved_players WHERE id=:id") Records.SavedPlayer savedPlayer(long id);
    @Query("SELECT * FROM saved_players WHERE nameKey=:key LIMIT 1") Records.SavedPlayer savedPlayerByKey(String key);
    @Insert long insertSavedPlayer(Records.SavedPlayer player);
    @Update void updateSavedPlayer(Records.SavedPlayer player);
    @Query("DELETE FROM saved_players WHERE id=:id") void deleteSavedPlayer(long id);
    @Query("SELECT * FROM legacy_recovery ORDER BY id") List<Records.RecoveryRow> recovery();
}
