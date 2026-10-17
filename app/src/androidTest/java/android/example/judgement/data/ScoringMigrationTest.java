package android.example.judgement.data;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ScoringMigrationTest {
    @Test public void versionThreeUpgradeKeepsCompletedDefaultScoresManualAdjustmentsAndPartialCurrentRound() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "scoring-migration-" + System.nanoTime();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                        .callback(new SupportSQLiteOpenHelper.Callback(3) {
                            @Override public void onCreate(SupportSQLiteDatabase db) {
                                ScoreDatabase.MIGRATION_1_2.migrate(db);
                                ScoreDatabase.MIGRATION_2_3.migrate(db);
                                db.execSQL("INSERT INTO games VALUES(1,'ACTIVE','ASCENDING','FIXED',26,11,100)");
                                db.execSQL("INSERT INTO games VALUES(2,'DRAFT','DESCENDING','CUSTOM',0,13,400)");
                                db.execSQL("INSERT INTO saved_players VALUES(41,'Beta','beta')");
                                db.execSQL("INSERT INTO game_players VALUES(11,1,'Alpha','alpha',0,1,NULL)");
                                db.execSQL("INSERT INTO game_players VALUES(12,1,'Beta','beta',1,1,41)");
                                db.execSQL("INSERT INTO game_players VALUES(13,2,'Partial setup','partial setup',0,1,NULL)");
                                db.execSQL("INSERT INTO rounds VALUES(21,1,1,1,12,'COMPLETE',100,200)");
                                db.execSQL("INSERT INTO round_players VALUES(21,11,0,'Original Alpha',0,'LOST')");
                                db.execSQL("INSERT INTO round_players VALUES(21,12,1,'Original Beta',0,'WON')");
                                db.execSQL("INSERT INTO rounds VALUES(22,1,2,2,11,'BIDDING',300,NULL)");
                                db.execSQL("INSERT INTO round_players VALUES(22,12,0,'Beta',0,NULL)");
                                db.execSQL("INSERT INTO round_players VALUES(22,11,1,'Alpha',NULL,NULL)");
                                db.execSQL("INSERT INTO score_adjustments VALUES(31,1,12,10,5,'Beta before rename',250)");
                                db.execSQL("INSERT INTO legacy_recovery VALUES(51,'players',1,'raw original shape','[{\"column\":\"Name\",\"value\":\" %_legacy \"}]','Recovered legacy scorecard')");
                            }
                            @Override public void onUpgrade(SupportSQLiteDatabase db, int old, int target) { throw new AssertionError(); }
                        }).build());
        helper.getWritableDatabase(); helper.close();
        ScoreDatabase database = ScoreDatabase.open(context, name);
        ScoreRepository repository = new ScoreRepository(database);
        try {
            Snapshot snapshot = repository.snapshot();
            assertEquals(4, database.getOpenHelper().getReadableDatabase().getVersion());
            assertEquals(15, snapshot.total(12)); assertEquals(0, snapshot.total(11));
            assertEquals(Records.DEFAULT_SCORING, snapshot.game.scoringConfig);
            assertNull(snapshot.game.pendingScoringConfig);
            assertEquals(Records.DEFAULT_SCORING, snapshot.history.get(0).round.scoringConfig);
            assertEquals(Records.DEFAULT_SCORING, snapshot.round.scoringConfig);
            assertEquals(0, snapshot.history.get(0).round.revision);
            assertEquals("Original Beta", snapshot.history.get(0).players.get(1).nameSnapshot);
            assertEquals(22, snapshot.round.id); assertEquals(2, snapshot.round.cards); assertEquals(11, snapshot.round.dealerId);
            assertEquals(Integer.valueOf(0), snapshot.roundPlayers.get(0).bid); assertNull(snapshot.roundPlayers.get(1).bid);
            assertEquals(5, snapshot.adjustments.get(0).delta); assertEquals("Beta before rename", snapshot.adjustments.get(0).nameSnapshot);
            assertEquals(Long.valueOf(41), snapshot.players.get(1).savedPlayerId);
            assertEquals(2, snapshot.draft.id); assertEquals("DESCENDING", snapshot.draft.direction);
            assertEquals(Records.DEFAULT_SCORING, snapshot.draft.scoringConfig);
            assertEquals("Partial setup", snapshot.draftPlayers.get(0).name);
            assertTrue(snapshot.recoveries.get(0).rawValues.contains(" %_legacy "));
            assertTrue(snapshot.roundEdits.isEmpty());
            assertEquals(snapshot.total(12), GameStatistics.from(snapshot).player(12).total);
        } finally { repository.close(); context.deleteDatabase(name); }
    }
}
