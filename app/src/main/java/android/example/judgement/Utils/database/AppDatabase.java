package android.example.judgement.Utils.database;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;
import android.example.judgement.Utils.Utils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Database(entities = {Player.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase INSTANCE;

    public abstract Dao dao();

    public static AppDatabase getAppDatabase(Context context) {
        if (INSTANCE == null) {
            INSTANCE =
                    Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "player-database")
                            // allow queries on the main thread.
                            // Don't do this on a real app! See PersistenceBasicSample for an example.
                            .allowMainThreadQueries()
                            .build();
        }
        return INSTANCE;
    }

    public static void destroyInstance(Context context) {
        INSTANCE.dao().purge();
        File db = new File(context.getDatabasePath("player-database").getPath());
        Utils.DeleteRecursive(db);
        INSTANCE = null;
    }

    public static void verifiedAddPlayer(Context ctx, String Name) {
        if(Name.equals("")){
            Toast.makeText(ctx, "Enter a Valid Name", Toast.LENGTH_SHORT).show();
        }
        else {
            AppDatabase db = getAppDatabase(ctx);

            try {
                Player player = db.dao().findByName(Name);
                if (!String.valueOf(player).equals("null")) {
                    Toast.makeText(ctx, "Name '" + Name + "' is Already Taken!", Toast.LENGTH_SHORT).show();
                } else {
                    player.setName(Name);
                    player.setDealer(false);
                    player.setScore(0);
                    db.dao().insertAll(player);
                    Toast.makeText(ctx, "Name Added Succesfully!", Toast.LENGTH_SHORT).show();

                }
            } catch (Exception e){
                Player player = new Player();
                player.setName(Name);
                player.setDealer(false);
                player.setScore(0);
                db.dao().insertAll(player);
                Toast.makeText(ctx, "Name Added Succesfully!", Toast.LENGTH_SHORT).show();

            }
        }
    }

    public static void moveDealer(Context ctx) {
        AppDatabase db = getAppDatabase(ctx);
        Player dealer = db.dao().findDealer(true);
        Player player = nextPlayer(ctx, dealer);
        AppDatabase.getAppDatabase(ctx).dao().delete(player, dealer);
        player.setDealer(true);
        dealer.setDealer(false);
        AppDatabase.getAppDatabase(ctx).dao().insertAll(player, dealer);
    }

    public static void verifiedRename(Context ctx, Player player,String Name) {
        int id = player.getId();
        int score = player.getScore();
        boolean dealer = player.getDealer();

        if(Name.equals("")){
            Toast.makeText(ctx, "Not a Valid Name", Toast.LENGTH_SHORT).show();
        }
        else {
            AppDatabase db = getAppDatabase(ctx);

            try {
                Player player2 = db.dao().findByName(Name);
                if (!String.valueOf(player2).equals("null")) {
                    Toast.makeText(ctx, "Name '" + Name + "' is Already Taken!", Toast.LENGTH_SHORT).show();
                } else {
                    db.dao().delete(player);
                    player2.setName(Name);
                    player2.setDealer(dealer);
                    player2.setScore(score);
                    player2.setId(id);

                    db.dao().insertAll(player2);
                    Toast.makeText(ctx, "Name Renamed Succesfully!", Toast.LENGTH_SHORT).show();

                }
            } catch (Exception e){
                db.dao().delete(player);
                Player player2 = new Player();
                player2.setName(Name);
                player2.setId(id);
                player2.setDealer(dealer);
                player2.setScore(score);
                db.dao().insertAll(player2);
                Toast.makeText(ctx, "Name Renamed Succesfully!", Toast.LENGTH_SHORT).show();

            }
        }
    }
    
    public static List<String> getAllNames(Context context){
        List<Player> players = getAppDatabase(context).dao().getAllPlayers();
        List<String> names = new ArrayList <String>();
        for (Player player: players) {
            names.add(player.getName());
        }
        return names;
    }

    public static void makeDealerByName(Context context, String name){
        try{
            Player dealer = getAppDatabase(context).dao().findDealer(true);
            if (dealer.getName().equals(name)){
                Toast.makeText(context, name + " is already the dealer!", Toast.LENGTH_SHORT).show();
            }
            else {
                dealer.setDealer(false);
                Player newDealer = getAppDatabase(context).dao().findByName(name);
                getAppDatabase(context).dao().delete(newDealer, dealer);
                newDealer.setDealer(true);
                getAppDatabase(context).dao().insertAll(dealer, newDealer);
                Toast.makeText(context, name + " is the new dealer!", Toast.LENGTH_SHORT).show();
            }
        }
        catch (Exception e) {
            Player newDealer = getAppDatabase(context).dao().findByName(name);
            getAppDatabase(context).dao().delete(newDealer);
            newDealer.setDealer(true);
            getAppDatabase(context).dao().insertAll(newDealer);
            Toast.makeText(context, name + " is the new dealer!", Toast.LENGTH_SHORT).show();
        }
    }

    public static Player nextPlayer(Context context, Player player){
        AppDatabase db = getAppDatabase(context);
        int count = db.dao().countPlayers();
        int old_id = player.getId();
        if (old_id == count) {
            return db.dao().findById(1);
        }
        else {
            return db.dao().findById(old_id + 1);
        }
    }
    public static Player previousPlayer(Context context, Player player){
        AppDatabase db = getAppDatabase(context);
        int count = db.dao().countPlayers();
        int old_id = player.getId();
        if (old_id == 1) {
            return db.dao().findById(count);
        }
        else {
            return db.dao().findById(old_id - 1);
        }
    }

    public static void normalizeIDs(Context context){
        AppDatabase db = getAppDatabase(context);
        List<Player> players = db.dao().getAllPlayers();
        int count = players.size();
        for(int i = 0; i < count; i++){
            players.get(i).setId(i+1);
        }
        db.dao().purge();
        db.dao().insertAll(players.toArray(new Player[count]));
    }

    public static void addPrediction(Context context, String playerName, int prediction){
        AppDatabase db = getAppDatabase(context);
        Player player = db.dao().findByName(playerName);
        db.dao().delete(player);
        player.setPrediction(prediction);
        db.dao().insertAll(player);
    }

    public static void addCement(Context context, String playerName, boolean cement){
        AppDatabase db = getAppDatabase(context);
        Player player = db.dao().findByName(playerName);
        db.dao().delete(player);
        player.setCement(cement);
        db.dao().insertAll(player);
    }

    public static void addResult(Context context, String playerName, boolean result){
        AppDatabase db = getAppDatabase(context);
        Player player = db.dao().findByName(playerName);
        db.dao().delete(player);
        player.setResult(result);
        db.dao().insertAll(player);
    }

    public static void addAllScores(Context context){
        AppDatabase db = getAppDatabase(context);
        List<Player> players = db.dao().getAllPlayers();
        int count = players.size();
        for(int i = 0; i < count; i++){
            boolean result = players.get(i).getResult();
            int prediction = players.get(i).getPrediction();
            if(result && prediction != -1){
                players.get(i).addScore(10 + prediction);
            }
        }
        db.dao().purge();
        db.dao().insertAll(players.toArray(new Player[count]));
    }

    public static void setAllPredictionsToReset(Context context){
        AppDatabase db = getAppDatabase(context);
        List<Player> players = db.dao().getAllPlayers();
        int count = players.size();
        for(int i = 0; i < count; i++){
            players.get(i).setPrediction(-1);
            players.get(i).setCement(false);
        }
        db.dao().purge();
        db.dao().insertAll(players.toArray(new Player[count]));
    }

    public static void setAllResultsToFalse(Context context) {
        AppDatabase db = getAppDatabase(context);
        List<Player> players = db.dao().getAllPlayers();
        int count = players.size();
        for(int i = 0; i < count; i++){
            players.get(i).setResult(false);
        }
        db.dao().purge();
        db.dao().insertAll(players.toArray(new Player[count]));
    }

    public static void setAllScoresToZero(Context context) {
        AppDatabase db = getAppDatabase(context);
        List<Player> players = db.dao().getAllPlayers();
        int count = players.size();
        for(int i = 0; i < count; i++){
            players.get(i).setScore(0);
        }
        db.dao().purge();
        db.dao().insertAll(players.toArray(new Player[count]));
    }
}
