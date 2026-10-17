package android.example.judgement.data;

import android.content.Context;
import android.database.Cursor;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

@Database(entities = {Records.Game.class, Records.GamePlayer.class, Records.Round.class,
        Records.RoundPlayer.class, Records.ScoreAdjustment.class, Records.SavedPlayer.class,
        Records.RecoveryRow.class}, version = 3, exportSchema = true)
public abstract class ScoreDatabase extends RoomDatabase {
    public static final String NAME = "player-database";
    public abstract ScoreDao dao();

    public static ScoreDatabase open(Context context) { return open(context, NAME); }
    public static ScoreDatabase open(Context context, String name) {
        return Room.databaseBuilder(context.getApplicationContext(), ScoreDatabase.class, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build();
    }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            isolateConflictingLegacyTables(db);
            createCoreTables(db);
            preserveLegacy(db);
        }
    };
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE game_players ADD COLUMN savedPlayerId INTEGER");
            db.execSQL("CREATE TABLE IF NOT EXISTS saved_players (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_saved_players_nameKey ON saved_players(nameKey)");
        }
    };

    private static void isolateConflictingLegacyTables(SupportSQLiteDatabase db) {
        List<String> names = new ArrayList<>();
        try (Cursor cursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (cursor.moveToNext()) names.add(cursor.getString(0));
        }
        String[] reserved = {"games", "game_players", "rounds", "round_players", "score_adjustments", "saved_players", "legacy_recovery"};
        for (String original : reserved) {
            if (!names.contains(original)) continue;
            String destination = "legacy_source_" + original;
            while (names.contains(destination)) destination += "_";
            db.execSQL("ALTER TABLE \"" + original + "\" RENAME TO \"" + destination + "\"");
            names.add(destination);
        }
    }

    private static void createCoreTables(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS games (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, status TEXT NOT NULL, direction TEXT NOT NULL, trumpMode TEXT NOT NULL, maximumCards INTEGER NOT NULL, dealerId INTEGER, createdAt INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS game_players (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, gameId INTEGER NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL, seat INTEGER NOT NULL, active INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_game_players_gameId ON game_players(gameId)");
        db.execSQL("CREATE TABLE IF NOT EXISTS rounds (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, gameId INTEGER NOT NULL, number INTEGER NOT NULL, cards INTEGER NOT NULL, dealerId INTEGER NOT NULL, phase TEXT NOT NULL, createdAt INTEGER NOT NULL, completedAt INTEGER)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_rounds_gameId ON rounds(gameId)");
        db.execSQL("CREATE TABLE IF NOT EXISTS round_players (roundId INTEGER NOT NULL, playerId INTEGER NOT NULL, bidPosition INTEGER NOT NULL, nameSnapshot TEXT NOT NULL, bid INTEGER, outcome TEXT, PRIMARY KEY(roundId,playerId))");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_round_players_playerId ON round_players(playerId)");
        db.execSQL("CREATE TABLE IF NOT EXISTS score_adjustments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, gameId INTEGER NOT NULL, playerId INTEGER NOT NULL, previousTotal INTEGER NOT NULL, delta INTEGER NOT NULL, nameSnapshot TEXT NOT NULL, createdAt INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_score_adjustments_gameId ON score_adjustments(gameId)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_score_adjustments_playerId ON score_adjustments(playerId)");
        db.execSQL("CREATE TABLE IF NOT EXISTS legacy_recovery (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceTable TEXT NOT NULL, sourceRow INTEGER NOT NULL, sourceSchema TEXT NOT NULL, rawValues TEXT NOT NULL, label TEXT NOT NULL)");
    }

    /** Keep original tables intact as well as a typed, lossless recovery copy. Never guess a phase. */
    private static void preserveLegacy(SupportSQLiteDatabase db) {
        List<String[]> tables = new ArrayList<>();
        try (Cursor cursor = db.query("SELECT name,sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table','games','game_players','rounds','round_players','score_adjustments','legacy_recovery') ORDER BY name")) {
            while (cursor.moveToNext()) tables.add(new String[]{cursor.getString(0), cursor.getString(1)});
        }
        for (String[] table : tables) {
            String quoted = "\"" + table[0].replace("\"", "\"\"") + "\"";
            try (Cursor cursor = db.query("SELECT * FROM " + quoted)) {
                int count = cursor.getColumnCount();
                java.util.Set<String> columns = new java.util.HashSet<>();
                for (String column : cursor.getColumnNames()) columns.add(column.toLowerCase(java.util.Locale.ROOT));
                java.util.Set<String> expected = new java.util.HashSet<>(java.util.Arrays.asList("id", "name", "score", "dealer"));
                if (count >= 6) { expected.add("prediction"); expected.add("result"); }
                if (count == 7) expected.add("cement");
                boolean known = "players".equals(table[0]) && (count == 4 || count == 6 || count == 7) && columns.equals(expected);
                long row = 0;
                while (cursor.moveToNext()) {
                    JSONArray values = new JSONArray();
                    for (int column = 0; column < count; column++) {
                        try {
                            JSONObject cell = new JSONObject();
                            cell.put("column", cursor.getColumnName(column));
                            cell.put("type", cursor.getType(column));
                            switch (cursor.getType(column)) {
                                case Cursor.FIELD_TYPE_NULL: cell.put("value", JSONObject.NULL); break;
                                case Cursor.FIELD_TYPE_INTEGER: cell.put("value", Long.toString(cursor.getLong(column))); break;
                                case Cursor.FIELD_TYPE_FLOAT: cell.put("value", Double.toString(cursor.getDouble(column))); break;
                                case Cursor.FIELD_TYPE_BLOB: cell.put("value", Base64.encodeToString(cursor.getBlob(column), Base64.NO_WRAP)); break;
                                default: cell.put("value", cursor.getString(column));
                            }
                            values.put(cell);
                        } catch (JSONException error) { throw new IllegalStateException("Cannot preserve legacy value", error); }
                    }
                    db.execSQL("INSERT INTO legacy_recovery(sourceTable,sourceRow,sourceSchema,rawValues,label) VALUES(?,?,?,?,?)",
                            new Object[]{table[0], ++row, table[1] == null ? "" : table[1], values.toString(),
                                    known ? "Recovered legacy scorecard — round position unavailable" : "Recovered unknown legacy schema — raw data retained"});
                }
            }
        }
    }
}
