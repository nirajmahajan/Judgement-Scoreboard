package android.example.judgement.data;

import static org.junit.Assert.*;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LegacyMigrationTest {
    @Test public void fourColumnLegacyRecoversRawValuesThroughFinalSchema() throws Exception { recoverShape(4); }
    @Test public void sixColumnLegacyRecoversRawValuesThroughFinalSchema() throws Exception { recoverShape(6); }
    @Test public void sevenColumnLegacyRecoversRawValuesThroughFinalSchema() throws Exception { recoverShape(7); }

    private void recoverShape(int columns) throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "legacy-" + columns + "-" + System.nanoTime();
        createLegacy(context, name, columns);
        try {
            ScoreDatabase db = ScoreDatabase.open(context, name);
            List<Records.RecoveryRow> rows = db.dao().recovery();
            assertEquals(2, rows.size());
            assertTrue(rows.get(0).label.contains("round position unavailable"));
            JSONArray first = new JSONArray(rows.get(0).rawValues);
            assertEquals(columns, first.length());
            assertEquals(" %_e\u0301 ", cell(first, "Name").getString("value"));
            assertEquals("-2147483648", cell(first, "Score").getString("value"));
            JSONArray malformed = new JSONArray(rows.get(1).rawValues);
            assertEquals("not a number", cell(malformed, "Score").getString("value"));
            assertEquals(Cursor.FIELD_TYPE_STRING, cell(malformed, "Score").getInt("type"));
            assertTrue(db.dao().games().isEmpty());
            assertTrue(db.dao().savedPlayers().isEmpty());
            try (Cursor original = db.getOpenHelper().getReadableDatabase().query("SELECT COUNT(*) FROM players")) {
                assertTrue(original.moveToFirst()); assertEquals(2, original.getInt(0));
            }
            db.close();
            db = ScoreDatabase.open(context, name);
            assertEquals(2, db.dao().recovery().size());
            db.close();
        } finally { context.deleteDatabase(name); }
    }

    @Test public void interruptedMigrationRollsBackAndRetryPreservesExactlyOneCopy() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "migration-retry-" + System.nanoTime();
        createLegacy(context, name, 7);
        ScoreDatabase failed = Room.databaseBuilder(context, ScoreDatabase.class, name)
                .addMigrations(new Migration(1, 3) {
                    @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
                        ScoreDatabase.MIGRATION_1_2.migrate(database);
                        ScoreDatabase.MIGRATION_2_3.migrate(database);
                        throw new IllegalStateException("Injected interruption before commit");
                    }
                }).build();
        try {
            try { failed.getOpenHelper().getWritableDatabase(); fail("Expected injected failure"); }
            catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("Injected interruption")); }
            failed.close();
            ScoreDatabase retried = ScoreDatabase.open(context, name);
            assertEquals(2, retried.dao().recovery().size());
            retried.close();
        } finally { failed.close(); context.deleteDatabase(name); }
    }

    @Test public void unknownLegacySchemaRetainsTypedRawDataAndDoesNotInventResume() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "legacy-unknown-" + System.nanoTime();
        SQLiteDatabase raw = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null);
        raw.execSQL("CREATE TABLE mystery (label TEXT, payload BLOB, missing TEXT)");
        raw.execSQL("INSERT INTO mystery VALUES(?, ?, NULL)", new Object[]{"unusual", new byte[]{0, 1, -1}});
        raw.setVersion(1); raw.close();
        try {
            ScoreDatabase db = ScoreDatabase.open(context, name);
            assertTrue(db.dao().games().isEmpty());
            Records.RecoveryRow row = db.dao().recovery().get(0);
            assertTrue(row.label.contains("unknown legacy schema"));
            JSONArray values = new JSONArray(row.rawValues);
            assertEquals(Cursor.FIELD_TYPE_BLOB, cell(values, "payload").getInt("type"));
            assertEquals("AAH/", cell(values, "payload").getString("value"));
            assertTrue(cell(values, "missing").isNull("value"));
            db.close();
        } finally { context.deleteDatabase(name); }
    }

    @Test public void unknownTablesWithNewModelNamesAreIsolatedBeforeMigration() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "legacy-reserved-" + System.nanoTime();
        SQLiteDatabase raw = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null);
        raw.execSQL("CREATE TABLE games (old TEXT)");
        raw.execSQL("INSERT INTO games VALUES('original game data')");
        raw.execSQL("CREATE TABLE legacy_recovery (old TEXT)");
        raw.execSQL("INSERT INTO legacy_recovery VALUES('previous unknown data')");
        raw.setVersion(1); raw.close();
        try {
            ScoreDatabase db = ScoreDatabase.open(context, name);
            assertTrue(db.dao().games().isEmpty());
            assertEquals(2, db.dao().recovery().size());
            assertTrue(db.dao().recovery().get(0).rawValues.contains("original game data"));
            assertTrue(db.dao().recovery().get(1).rawValues.contains("previous unknown data"));
            db.close();
        } finally { context.deleteDatabase(name); }
    }

    @Test public void coreVersionTwoUpgradePreservesSetupBidsAndNamesWhenAddingSavedPlayers() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "core-two-" + System.nanoTime();
        androidx.sqlite.db.SupportSQLiteOpenHelper helper = new androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                        .callback(new androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                            @Override public void onCreate(SupportSQLiteDatabase db) {
                                ScoreDatabase.MIGRATION_1_2.migrate(db);
                                db.execSQL("INSERT INTO games VALUES(4,'ACTIVE','DESCENDING','CUSTOM',26,8,100)");
                                db.execSQL("INSERT INTO game_players VALUES(7,4,'Full old participant','full old participant',0,1)");
                                db.execSQL("INSERT INTO game_players VALUES(8,4,'Second participant','second participant',1,1)");
                                db.execSQL("INSERT INTO rounds VALUES(9,4,1,26,8,'BIDDING',101,NULL)");
                                db.execSQL("INSERT INTO round_players VALUES(9,7,0,'Full old participant',0,NULL)");
                                db.execSQL("INSERT INTO round_players VALUES(9,8,1,'Second participant',NULL,NULL)");
                            }
                            @Override public void onUpgrade(SupportSQLiteDatabase db, int old, int target) { throw new AssertionError(); }
                        }).build());
        helper.getWritableDatabase(); helper.close();
        try {
            ScoreDatabase db = ScoreDatabase.open(context, name);
            assertEquals(3, db.getOpenHelper().getReadableDatabase().getVersion());
            assertEquals("DESCENDING", db.dao().game(4).direction);
            assertEquals("CUSTOM", db.dao().game(4).trumpMode);
            assertEquals("Full old participant", db.dao().player(7).name);
            assertNull(db.dao().player(7).savedPlayerId);
            assertEquals(Integer.valueOf(0), db.dao().roundPlayers(9).get(0).bid);
            assertNull(db.dao().roundPlayers(9).get(1).bid);
            assertTrue(db.dao().savedPlayers().isEmpty());
            db.close();
        } finally { context.deleteDatabase(name); }
    }

    private static JSONObject cell(JSONArray row, String column) throws Exception {
        for (int i = 0; i < row.length(); i++) if (column.equals(row.getJSONObject(i).getString("column"))) return row.getJSONObject(i);
        throw new AssertionError("Missing column " + column);
    }
    private static void createLegacy(Context context, String name, int columns) {
        SQLiteDatabase db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null);
        String extra = columns >= 6 ? ", Prediction INTEGER NOT NULL, Result INTEGER NOT NULL" : "";
        if (columns == 7) extra += ", Cement INTEGER NOT NULL";
        db.execSQL("CREATE TABLE players (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, Name TEXT, Score INTEGER NOT NULL, dealer INTEGER NOT NULL" + extra + ")");
        String values = columns == 4 ? "" : columns == 6 ? ", -1, 0" : ", -1, 0, 1";
        db.execSQL("INSERT INTO players VALUES(7, ?, -2147483648, 1" + values + ")", new Object[]{" %_e\u0301 "});
        db.execSQL("INSERT INTO players VALUES(19, ?, ?, 0" + values + ")", new Object[]{"\u200b", "not a number"});
        db.setVersion(1); db.close();
    }
}
