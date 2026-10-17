package android.example.judgement.ui;

import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.data.*;
import android.example.judgement.data.Records;
import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

/** Native regressions for complete historical proposals, live progress and final graph navigation. */
@RunWith(AndroidJUnit4.class)
public final class HistoryStatisticsUiRegressionTest {
    private Context context;
    private ScoreRepository repository;
    private ActivityScenario<MainActivity> scenario;
    @Before public void prepare() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(ScoreDatabase.NAME); repository = new ScoreRepository(context);
    }
    @After public void close() {
        if (scenario != null) scenario.close(); repository.close();
    }
    @Test public void eightPlayerCorrectionsPreserveInvalidTextAndPartialCurrentResults() {
        start(8); complete();
        Snapshot next = repository.snapshot();
        for (Records.RoundPlayer p : next.roundPlayers) repository.submitBid(next.round.id, p.playerId, 0);
        repository.recordBids(next.round.id);
        repository.setOutcome(next.round.id, next.roundPlayers.get(0).playerId, Outcome.WON);
        Snapshot before = repository.snapshot(); Snapshot.RoundHistory history = before.history.get(0);
        launch(false); inspectFirst(); clickId(R.id.edit_historical_round);
        long first = history.players.get(0).playerId, second = history.players.get(1).playerId;
        String lastName = history.players.get(7).nameSnapshot;
        enter(lastName, "999999999999999999999999"); clickId(R.id.preview_historical_round);
        await(() -> editor().bidError(history.players.get(7).playerId) != null);
        String token = editor().token; scenario.recreate(); await(() -> editor() != null && editor().token.equals(token));
        assertEquals("999999999999999999999999", editor().players.get(7).bidText);
        enter(lastName, "0"); outcome(history.players.get(0).nameSnapshot, Outcome.WON);
        clickId(R.id.preview_historical_round); await(() -> editor().error != null);
        assertEquals(Outcome.WON, editor().players.get(0).outcome);
        assertEquals(0, repository.snapshot().roundEdits.size());
        outcome(history.players.get(1).nameSnapshot, Outcome.LOST);
        clickId(R.id.preview_historical_round); await(() -> preview() != null);
        assertEquals(Long.valueOf(10), preview().scoreDeltas.get(first));
        assertEquals(Long.valueOf(-10), preview().scoreDeltas.get(second));
        clickId(R.id.save_historical_round); await(() -> screen().equals("ROUND_DETAILS"));
        Snapshot after = repository.snapshot();
        assertEquals(before.round.id, after.round.id); assertEquals(before.round.dealerId, after.round.dealerId);
        assertEquals(before.round.cards, after.round.cards); assertEquals("RESULTS", after.round.phase);
        for (int i=0;i<before.roundPlayers.size();i++) {
            assertEquals(before.roundPlayers.get(i).bid, after.roundPlayers.get(i).bid);
            assertEquals(before.roundPlayers.get(i).outcome, after.roundPlayers.get(i).outcome);
        }
        assertEquals(before.total(first)+10, after.total(first));
        assertEquals(before.total(second)-10, after.total(second));
        assertEquals(1, after.roundEdits.size());
        assertTrue(after.roundEdits.get(0).previousValues.contains(history.players.get(0).nameSnapshot));
        assertEquals(1, GameStatistics.from(after).player(first).exactPredictions);
        reveal(withText(lastName)); // The eighth participant's complete native name remains reachable.
    }
    @Test public void stalePreviewCannotOverwriteAConcurrentCorrection() {
        start(2); complete(); Snapshot.RoundHistory round = repository.snapshot().history.get(0);
        launch(false); inspectFirst(); clickId(R.id.edit_historical_round);
        outcome(round.players.get(0).nameSnapshot, Outcome.WON);
        outcome(round.players.get(1).nameSnapshot, Outcome.LOST);
        clickId(R.id.preview_historical_round); await(() -> preview()!=null);
        List<RoundPlayerEdit> correction = new ArrayList<>();
        for(int i=0;i<round.players.size();i++) correction.add(new RoundPlayerEdit(round.players.get(i).playerId,
                0, i==0?Outcome.WON:Outcome.LOST));
        repository.editRound(round.round.id, 0, correction); refresh();
        clickId(R.id.save_historical_round); await(() -> editor()!=null && editor().error!=null);
        assertEquals("ROUND_EDIT", screen()); assertEquals(Outcome.WON, editor().players.get(0).outcome);
        assertEquals(1,repository.snapshot().roundEdits.size());
        assertEquals(1,repository.snapshot().history.get(0).round.revision); assertNull(preview());
    }
    @Test public void denseFinalGraphsKeepSelectionAndOpenEditableRecordedRound() {
        start(2);
        while(repository.snapshot().round!=null) {
            Snapshot s=repository.snapshot();
            if(s.round.number==14) repository.changeScoringStrategy(s.round.id,ScoringStrategy.aggressive());
            complete();
        }
        Snapshot complete=repository.snapshot(); assertEquals(26,complete.history.size());
        launch(true); clickId(R.id.final_statistics); await(() -> screen().equals("STATISTICS"));
        String excluded=complete.players.get(0).name;
        clickDescription(context.getString(R.string.statistics_compare_player,excluded));
        long excludedId=complete.players.get(0).id;
        scenario.recreate(); await(() -> screen().equals("STATISTICS"));
        scenario.onActivity(activity -> {
            ScoreViewModel model=new ViewModelProvider(activity).get(ScoreViewModel.class);
            assertFalse(model.comparedPlayers(complete.game.id,new ArrayList<>()).contains(excludedId));
        });
        // A screen-reader click on the final timeline point uses the same round-inspection path as touch.
        onView(withId(R.id.screen_list)).perform(findAndAct(isAssignableFrom(ScoreGraphView.class),new ViewAction(){
            public Matcher<View> getConstraints(){return isAssignableFrom(ScoreGraphView.class);}
            public String getDescription(){return "inspect final round through its accessible graph point";}
            public void perform(UiController controller,View view){
                assertTrue(view.getAccessibilityNodeProvider().performAction(25,
                        AccessibilityNodeInfo.ACTION_CLICK,null)); controller.loopMainThreadUntilIdle();
            }
        }));
        await(() -> screen().equals("ROUND_DETAILS"));
        Snapshot.RoundHistory last=complete.history.get(25);
        assertEquals(ScoringStrategy.aggressive().encode(),last.round.scoringConfig);
        clickId(R.id.edit_historical_round);
        outcome(last.players.get(0).nameSnapshot,Outcome.WON);
        outcome(last.players.get(1).nameSnapshot,Outcome.LOST);
        clickId(R.id.preview_historical_round); await(() -> preview()!=null);
        clickId(R.id.save_historical_round); await(() -> screen().equals("ROUND_DETAILS"));
        clickId(R.id.history_back); await(() -> screen().equals("STATISTICS"));
        GameStatistics report=GameStatistics.from(repository.snapshot());
        GameStatistics beforeReport=GameStatistics.from(complete);
        assertEquals(beforeReport.player(last.players.get(0).playerId).exactPredictions+1,
                report.player(last.players.get(0).playerId).exactPredictions);
        assertEquals(beforeReport.player(last.players.get(1).playerId).exactPredictions-1,
                report.player(last.players.get(1).playerId).exactPredictions);
        assertEquals(1,repository.snapshot().roundEdits.size());
        scenario.onActivity(activity -> assertFalse(new ViewModelProvider(activity).get(ScoreViewModel.class)
                .comparedPlayers(complete.game.id,new ArrayList<>()).contains(excludedId)));
    }
    private void start(int count) {
        List<PlayerSpec> players=new ArrayList<>();
        for(int i=0;i<count;i++) players.add(new PlayerSpec("Player "+i+" — अलेक्ज़ेंडर with a complete lengthy family name"));
        long draft=repository.saveDraft(new DraftSpec(0,players,"ASCENDING","FIXED",null));
        repository.startGame(draft);
    }
    private void complete() {
        Snapshot s=repository.snapshot();
        for(Records.RoundPlayer p:s.roundPlayers) repository.submitBid(s.round.id,p.playerId,0);
        repository.recordBids(s.round.id);
        for(int i=0;i<s.roundPlayers.size();i++) repository.setOutcome(s.round.id,s.roundPlayers.get(i).playerId,
                i==0?Outcome.LOST:Outcome.WON);
        repository.completeRound(s.round.id);
    }
    private void launch(boolean completed) {
        scenario=ActivityScenario.launch(MainActivity.class); await(() -> snapshot()!=null);
        scenario.onActivity(activity -> {
            ScoreViewModel model=new ViewModelProvider(activity).get(ScoreViewModel.class);
            model.tab(completed?"ROUND":"HISTORY"); model.screen("GAME"); model.repository.execute(model.repository::refresh);
        });
        await(() -> screen().equals("GAME") && title().equals(context.getString(completed?R.string.game_complete:R.string.history_tab)));
    }
    private void inspectFirst(){clickText(R.string.history_inspect_round);await(() -> screen().equals("ROUND_DETAILS"));}
    private void enter(String name,String value){onView(withId(R.id.screen_list)).perform(findAndAct(
            withContentDescription(context.getString(R.string.round_edit_bid_description,name)),replaceText(value))); androidx.test.espresso.Espresso.closeSoftKeyboard();}
    private void outcome(String name,Outcome outcome){clickDescription(context.getString(R.string.round_edit_outcome_description,
            context.getString(outcome==Outcome.WON?R.string.won:R.string.lost),name));}
    private void clickDescription(String text){onView(withId(R.id.screen_list)).perform(findAndAct(withContentDescription(text),click()));}
    private void clickText(int text){onView(withId(R.id.screen_list)).perform(findAndAct(withText(text),click()));}
    private void clickId(int id){onView(withId(R.id.screen_list)).perform(findAndAct(withId(id),click()));}
    private void reveal(Matcher<View> target){onView(withId(R.id.screen_list)).perform(findAndAct(target,new ViewAction(){
        public Matcher<View> getConstraints(){return isAssignableFrom(View.class);} public String getDescription(){return "verify complete name";}
        public void perform(UiController c,View v){assertTrue(isDisplayingAtLeast(90).matches(v));}
    }));}
    private static ViewAction findAndAct(Matcher<View> target,ViewAction action){return new ViewAction(){
        public Matcher<View> getConstraints(){return isAssignableFrom(RecyclerView.class);}
        public String getDescription(){return "reveal historical/statistics control and "+action.getDescription();}
        public void perform(UiController c,View view){RecyclerView list=(RecyclerView)view;
            for(int i=0;i<list.getAdapter().getItemCount();i++){
                list.scrollToPosition(i);c.loopMainThreadUntilIdle();RecyclerView.ViewHolder h=list.findViewHolderForAdapterPosition(i);
                View found=h==null?null:find(h.itemView,target);
                if(found!=null){found.requestRectangleOnScreen(new android.graphics.Rect(0,0,found.getWidth(),found.getHeight()),true);
                    c.loopMainThreadUntilIdle();action.perform(c,found);c.loopMainThreadUntilIdle();return;}
            }throw new AssertionError("Missing control: "+target);
        }
    };}
    private static View find(View v,Matcher<View> target){if(target.matches(v))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View f=find(((ViewGroup)v).getChildAt(i),target);if(f!=null)return f;}return null;}
    private ScoreViewModel model(MainActivity a){return new ViewModelProvider(a).get(ScoreViewModel.class);}
    private <T>T read(java.util.function.Function<ScoreViewModel,T> f){AtomicReference<T> out=new AtomicReference<>();scenario.onActivity(a->out.set(f.apply(model(a))));return out.get();}
    private Snapshot snapshot(){return read(m->m.state.getValue());}
    private String screen(){return read(ScoreViewModel::screen);}
    private RoundEditRequest editor(){return read(ScoreViewModel::roundEditor);}
    private RoundEditPreview preview(){return read(m->m.roundEditor()==null?null:m.previewFor(m.roundEditor()));}
    private String title(){AtomicReference<String> out=new AtomicReference<>("");scenario.onActivity(a->out.set(((android.widget.TextView)a.findViewById(R.id.screen_title)).getText().toString()));return out.get();}
    private void refresh(){scenario.onActivity(a->model(a).repository.execute(model(a).repository::refresh));}
    private interface Condition{boolean test();}
    private static void await(Condition condition){long end=SystemClock.uptimeMillis()+10_000;
        do{InstrumentationRegistry.getInstrumentation().waitForIdleSync();if(condition.test())return;SystemClock.sleep(25);}while(SystemClock.uptimeMillis()<end);
        assertTrue("Expected history/statistics state within ten seconds",condition.test());}
}
