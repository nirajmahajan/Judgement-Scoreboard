package android.example.judgement.Utils.database;

import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@androidx.room.Dao
public interface Dao {

    @Query("Select * from players")
    List<Player> getAllPlayers();

    @Query("Select * from players where name LIKE :Name")
    Player findByName(String Name);

    @Query("Select * from players where id LIKE :Id")
    Player findById(int Id);

    @Query("Select * from players where dealer LIKE :TRUE")
    Player findDealer(boolean TRUE);

    @Query("Select COUNT(*) from players")
    int countPlayers();

    @Query("DELETE From players")
    void purge();

    @Insert
    void insertAll(Player... players);

    @Delete
    void delete(Player... players);
}
