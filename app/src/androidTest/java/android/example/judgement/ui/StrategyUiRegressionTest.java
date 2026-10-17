package android.example.judgement.ui;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.content.Context;
import android.content.res.Configuration;
import android.example.judgement.R;
import android.example.judgement.data.DraftSpec;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.data.Records;
import android.example.judgement.data.ScoreDatabase;
import android.example.judgement.data.ScoreRepository;
import android.example.judgement.data.Snapshot;
import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.google.android.material.color.MaterialColors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.*;

/** Exercises persisted strategies and real Activity/theme recreation through the actual controls. */
@RunWith(AndroidJUnit4.class)
public final class StrategyUiRegressionTest {
    private Context context;
    private ScoreRepository repository;
    private ActivityScenario<MainActivity> scenario;
    @Before public void prepare() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(ScoreDatabase.NAME); repository = new ScoreRepository(context);
    }
    @After public void close() {
        if (scenario != null) scenario.close();
        repository.close();
    }

    @Test public void setupSelectorPreviewsAggressiveWithoutChangingDraftAndCancelRestoresTheme() {
        long draft = seed(false, ScoringStrategy.defaults()); launch("SETTINGS");
        AtomicReference<String> first = new AtomicReference<>();
        scenario.onActivity(activity -> {
            RecyclerView list = activity.findViewById(R.id.screen_list);
            RecyclerView.ViewHolder row = list.findViewHolderForAdapterPosition(1);
            assertNotNull(row); first.set(((TextView) row.itemView.findViewById(R.id.row_name)).getText().toString());
        });
        assertEquals(context.getString(R.string.scoring_selected, "Default"), first.get());
        clickText(R.string.choose_scoring); clickId(R.id.strategy_aggressive);
        await(() -> current() != null && current().preset == ScoringStrategy.Preset.AGGRESSIVE && hasAppearance(true));
        assertEquals(draft, current().targetId);
        assertEquals(ScoringStrategy.defaults().encode(), repository.snapshot().draft.scoringConfig);
        String examples = text(R.id.strategy_examples);
        assertTrue(examples.contains(context.getString(R.string.strategy_example,12,7,27L)));
        assertTrue(examples.contains(context.getString(R.string.strategy_example,12,8,28L)));
        assertTrue(examples.contains(context.getString(R.string.strategy_example,16,9,29L)));
        assertTrue(text(R.id.strategy_rules).contains("total round score is not capped"));
        clickId(R.id.strategy_help);
        await(() -> screen().equals("SCORING_HELP"));
        clickId(R.id.primary_action); await(() -> screen().equals("SCORING"));
        assertEquals(ScoringStrategy.Preset.AGGRESSIVE,current().preset);
        clickId(R.id.strategy_cancel);
        await(() -> screen().equals("SETTINGS") && hasAppearance(false));
        assertEquals(ScoringStrategy.defaults().encode(),repository.snapshot().draft.scoringConfig);
    }

    @Test public void customLiveExamplesAndOverflowErrorSurviveRecreationBeforeSaving() {
        seed(false,ScoringStrategy.defaults()); launch("SETTINGS");
        clickText(R.string.choose_scoring); clickId(R.id.strategy_custom);
        enter(R.id.strategy_zero,"7"); enter(R.id.strategy_base,"10"); enter(R.id.strategy_per_trick,"2");
        clickId(R.id.strategy_bonus);
        assertTrue(text(R.id.strategy_examples).contains(context.getString(R.string.strategy_example,12,7,34L)));
        enter(R.id.strategy_per_trick,"9223372036854775807"); clickId(R.id.primary_action);
        await(() -> current() != null && current().error != null);
        onView(withId(R.id.strategy_error)).check(androidx.test.espresso.assertion.ViewAssertions.matches(
                androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast(90)));
        String token=current().token, message=current().error;
        assertTrue(message.contains("supported points"));
        scenario.recreate(); await(() -> current()!=null && current().token.equals(token));
        assertEquals("9223372036854775807",current().perTrick);
        assertEquals(message,current().error); assertTrue(current().bonus);
        assertEquals("9223372036854775807",text(R.id.strategy_per_trick));
        assertEquals(message,text(R.id.strategy_error));
        enter(R.id.strategy_per_trick,"2"); clickId(R.id.primary_action);
        await(() -> repository.snapshot().draft.scoringConfig.equals(ScoringStrategy.custom(7,10,2,true).encode()));
        await(() -> screen().equals("SETTINGS"));
    }

    @Test public void strategyChangesImmediatelyBeforeBidsAndChangesAppearanceOnlyWhenPendingBecomesActive() {
        seed(true,ScoringStrategy.defaults()); launch("GAME");
        option(R.string.change_scoring); clickId(R.id.strategy_aggressive); clickId(R.id.primary_action);
        await(() -> repository.snapshot().round.scoringConfig.equals(ScoringStrategy.aggressive().encode()));
        await(() -> screen().equals("GAME") && hasAppearance(true));
        Snapshot initial=repository.snapshot();
        onView(withId(R.id.screen_list)).perform(findAndClick(withText(context.getString(R.string.quick_bid,0))));
        await(() -> uiSnapshot().roundPlayers.get(0).bid!=null);
        option(R.string.change_scoring); clickId(R.id.strategy_default);
        await(() -> hasAppearance(false)); // Chooser preview only.
        assertEquals(ScoringStrategy.aggressive().encode(),repository.snapshot().round.scoringConfig);
        clickId(R.id.primary_action);
        await(() -> ScoringStrategy.defaults().encode().equals(repository.snapshot().game.pendingScoringConfig));
        await(() -> screen().equals("GAME") && hasAppearance(true));
        assertTrue(text(R.id.screen_description).contains(context.getString(R.string.strategy_pending,"Default",2)));
        scenario.recreate(); await(() -> hasAppearance(true));
        assertEquals(ScoringStrategy.defaults().encode(),repository.snapshot().game.pendingScoringConfig);
        completeRound(); refreshUi();
        await(() -> uiSnapshot().round.number==2 && hasAppearance(false));
        Snapshot next=repository.snapshot();
        assertNull(next.game.pendingScoringConfig);
        assertEquals(ScoringStrategy.defaults().encode(),next.round.scoringConfig);
        assertEquals(ScoringStrategy.aggressive().encode(),next.history.get(0).round.scoringConfig);
    }

    @Test public void restoredStrategyEditorRejectsOriginalRoundAfterRestart() {
        seed(true,ScoringStrategy.defaults()); launch("GAME");
        option(R.string.change_scoring); long original=current().targetId;
        repository.restartRound(original); long restarted=repository.snapshot().round.id;
        refreshUi();
        scenario.recreate(); await(() -> current()!=null && uiSnapshot().round.id==restarted);
        clickId(R.id.strategy_aggressive); clickId(R.id.primary_action);
        await(() -> current()!=null && current().error!=null);
        onView(withId(R.id.strategy_error)).check(androidx.test.espresso.assertion.ViewAssertions.matches(
                androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast(90)));
        assertEquals(original,current().targetId);
        assertEquals(restarted,repository.snapshot().round.id);
        assertEquals(ScoringStrategy.defaults().encode(),repository.snapshot().round.scoringConfig);
        assertNull(repository.snapshot().game.pendingScoringConfig);
    }

    @Test public void playAgainShowsReusedActiveStrategyAndKeepsCompletedGameUntilConfirmedStart() {
        List<PlayerSpec> players=new ArrayList<>();
        for(int i=0;i<26;i++) players.add(new PlayerSpec("Player "+i+" with a complete long name नीरज 🙂"));
        long draft=repository.saveDraft(new DraftSpec(0,players,"DESCENDING","CUSTOM",null,ScoringStrategy.aggressive()));
        repository.startGame(draft); completeRound();
        Snapshot finalRound=repository.snapshot();
        repository.submitBid(finalRound.round.id,finalRound.roundPlayers.get(0).playerId,0);
        repository.changeScoringStrategy(finalRound.round.id,ScoringStrategy.defaults());
        completeRound(); Snapshot completed=repository.snapshot();
        assertEquals("COMPLETE",completed.game.status);
        launch("GAME");
        assertTrue(text(R.id.screen_description).contains("game ended"));
        clickId(R.id.play_again);
        await(() -> repository.snapshot().draft!=null && screen().equals("SETTINGS"));
        Snapshot replay=repository.snapshot();
        assertEquals(completed.game.id,replay.game.id); assertEquals(completed.totals,replay.totals);
        assertEquals(completed.history.size(),replay.history.size());
        assertEquals("DESCENDING",replay.draft.direction); assertEquals("CUSTOM",replay.draft.trumpMode);
        assertEquals(ScoringStrategy.aggressive().encode(),replay.draft.scoringConfig);
        assertEquals(26,replay.draftPlayers.size());
        for(int i=0;i<26;i++) assertEquals(completed.players.get(i).name,replay.draftPlayers.get(i).name);
        assertTrue(hasAppearance(true));
        clickId(R.id.primary_action); onView(withText(R.string.cancel)).perform(click());
        assertEquals(completed.game.id,repository.snapshot().game.id);
    }

    @Test public void darkNarrowCustomFormAtTwoHundredPercentKeepsLabelsErrorsAndSaveReachableWithKeyboard() throws Exception {
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String font=device.executeShellCommand("settings get system font_scale").trim();
        String hardwareIme=device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim();
        String size=device.executeShellCommand("wm size");
        java.util.regex.Matcher originalOverride=java.util.regex.Pattern.compile("Override size: (\\d+x\\d+)").matcher(size);
        String restoreSize=originalOverride.find()?originalOverride.group(1):"reset";
        java.util.regex.Matcher dimensions=java.util.regex.Pattern.compile("(\\d+)x(\\d+)").matcher(size);
        assertTrue(dimensions.find()); int height=Integer.parseInt(dimensions.group(2));
        String densityOutput=device.executeShellCommand("wm density");
        java.util.regex.Matcher densities=java.util.regex.Pattern.compile("density: (\\d+)").matcher(densityOutput);
        int density=0; while(densities.find())density=Integer.parseInt(densities.group(1));
        assertTrue(density>0);
        try {
            device.executeShellCommand("settings put system font_scale 2.0");
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1");
            device.executeShellCommand("wm size "+Math.round(320*density/160f)+"x"+height);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES));
            seed(false,ScoringStrategy.aggressive()); launch("SETTINGS");
            scenario.onActivity(activity -> {
                assertEquals(320,activity.getResources().getConfiguration().screenWidthDp,1);
                assertEquals(2f,activity.getResources().getConfiguration().fontScale,.01f);
                assertEquals(Configuration.UI_MODE_NIGHT_YES,activity.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK);
            });
            clickText(R.string.choose_scoring); await(() -> current()!=null && hasAppearance(true));
            clickId(R.id.strategy_aggressive);
            onView(withId(R.id.strategy_aggressive)).check((view,noMatch) -> assertCompleteText((TextView)view));
            scenario.onActivity(activity -> {
                com.google.android.material.appbar.MaterialToolbar toolbar=activity.findViewById(R.id.toolbar);
                assertNotNull(toolbar.getNavigationIconTint());
                assertTrue("The back control contrasts with the dark surface",androidx.core.graphics.ColorUtils.calculateContrast(
                        toolbar.getNavigationIconTint(),MaterialColors.getColor(toolbar,com.google.android.material.R.attr.colorSurface))>=4.5);
            });
            device.executeShellCommand("mkdir -p /sdcard/judgement-test-evidence");
            device.executeShellCommand("screencap -p /sdcard/judgement-test-evidence/strategy-aggressive-dark-320-font200.png");
            clickId(R.id.strategy_custom); await(() -> hasAppearance(false));
            for(int label:new int[]{R.string.strategy_zero_reward,R.string.strategy_base_reward,R.string.strategy_per_trick}) {
                onView(withText(label)).perform(reveal()).check((view,noMatch) -> assertCompleteText((TextView)view));
            }
            onView(withId(R.id.strategy_zero)).perform(reveal(),replaceText(""),new ViewAction() {
                @Override public Matcher<View> getConstraints(){return isAssignableFrom(TextView.class);}
                @Override public String getDescription(){return "focus the points field and display the software keyboard";}
                @Override public void perform(UiController controller,View view){
                    view.requestFocus(); ((InputMethodManager)view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE))
                            .showSoftInput(view,InputMethodManager.SHOW_IMPLICIT);controller.loopMainThreadUntilIdle();
                }
            });
            awaitKeyboard(); clickId(R.id.primary_action);
            await(() -> current()!=null && current().error!=null);
            device.executeShellCommand("screencap -p /sdcard/judgement-test-evidence/strategy-custom-error-dark-320-font200-ime.png");
            onView(withId(com.google.android.material.R.id.textinput_error)).perform(reveal());
            onView(withId(com.google.android.material.R.id.textinput_error)).check((view,noMatch) -> {
                assertNotNull(view); assertCompleteText((TextView)view);
                assertTrue("The complete numeric error is reachable while the keyboard is open",
                        androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast(90).matches(view));
            });
            awaitKeyboard();
            device.executeShellCommand("screencap -p /sdcard/judgement-test-evidence/strategy-custom-error-dark-320-font200-ime.png");
            onView(withId(R.id.strategy_zero)).perform(reveal(),replaceText("7"));
            clickId(R.id.primary_action);
            await(() -> repository.snapshot().draft.scoringConfig.equals(ScoringStrategy.custom(7,10,1,true).encode()));
        } finally {
            if(scenario!=null){scenario.close();scenario=null;}
            device.executeShellCommand("settings put system font_scale "+("null".equals(font)?"1.0":font));
            device.executeShellCommand("settings "+("null".equals(hardwareIme)?"delete":"put")+" secure show_ime_with_hard_keyboard"+
                    ("null".equals(hardwareIme)?"":" "+hardwareIme));
            device.executeShellCommand("wm size "+restoreSize);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        }
    }
    private static void assertCompleteText(TextView view) {
        assertNotNull(view.getLayout());
        assertEquals("The complete label remains in its laid-out text",view.getText().length(),
                view.getLayout().getLineEnd(view.getLayout().getLineCount()-1));
        for(int i=0;i<view.getLayout().getLineCount();i++)assertEquals("No label is ellipsized",0,view.getLayout().getEllipsisCount(i));
        assertTrue("All wrapped lines fit vertically",view.getHeight()-view.getCompoundPaddingTop()-view.getCompoundPaddingBottom()>=view.getLayout().getHeight());
    }
    private static void awaitKeyboard() {
        UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
        AccessibilityServiceInfo info=automation.getServiceInfo();
        info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
        await(() -> {
            for(AccessibilityWindowInfo window:automation.getWindows()) {
                Rect bounds=new Rect();window.getBoundsInScreen(bounds);
                if(window.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD&&!bounds.isEmpty())return true;
            }
            return false;
        });
    }

    private long seed(boolean active,ScoringStrategy strategy) {
        long id=repository.saveDraft(new DraftSpec(0,Arrays.asList(new PlayerSpec("Ada with a complete long name"),
                new PlayerSpec("Grace with a complete long name")),"ASCENDING","FIXED",null,strategy));
        if(active) repository.startGame(id); return id;
    }
    private void launch(String target) {
        scenario=ActivityScenario.launch(MainActivity.class);
        await(() -> uiSnapshot()!=null);
        scenario.onActivity(activity -> {
            ScoreViewModel model=new ViewModelProvider(activity).get(ScoreViewModel.class);
            model.screen(target); model.repository.execute(model.repository::refresh);
        });
        Snapshot snapshot = repository.snapshot();
        String heading = "SETTINGS".equals(target) ? context.getString(R.string.setup_settings)
                : snapshot.round == null ? context.getString(R.string.game_complete)
                : context.getResources().getQuantityString(R.plurals.round_heading,
                        snapshot.round.cards,snapshot.round.number,snapshot.round.cards);
        await(() -> screen().equals(target) && text(R.id.screen_title).equals(heading));
    }
    private void completeRound() {
        Snapshot snapshot=repository.snapshot();
        for(Records.RoundPlayer player:snapshot.roundPlayers) if(player.bid==null) repository.submitBid(snapshot.round.id,player.playerId,0);
        repository.recordBids(snapshot.round.id);
        for(int i=0;i<snapshot.roundPlayers.size();i++) repository.setOutcome(snapshot.round.id,
                snapshot.roundPlayers.get(i).playerId,i==0?Outcome.LOST:Outcome.WON);
        repository.completeRound(snapshot.round.id);
    }
    private StrategyRequest current() {
        AtomicReference<StrategyRequest> value=new AtomicReference<>();
        scenario.onActivity(activity -> value.set(new ViewModelProvider(activity).get(ScoreViewModel.class).strategyRequest()));return value.get();
    }
    private Snapshot uiSnapshot() {
        AtomicReference<Snapshot> value=new AtomicReference<>();
        scenario.onActivity(activity -> value.set(new ViewModelProvider(activity).get(ScoreViewModel.class).state.getValue()));return value.get();
    }
    private String screen() {
        AtomicReference<String> value=new AtomicReference<>();
        scenario.onActivity(activity -> value.set(new ViewModelProvider(activity).get(ScoreViewModel.class).screen()));return value.get();
    }
    private boolean hasAppearance(boolean aggressive) {
        AtomicReference<Boolean> value=new AtomicReference<>(false);
        scenario.onActivity(activity -> value.set(MaterialColors.getColor(activity.findViewById(R.id.score_root),
                androidx.appcompat.R.attr.colorPrimary)==activity.getColor(aggressive?R.color.aggressive_accent:R.color.score_accent)));
        return value.get();
    }
    private String text(int id) {
        AtomicReference<String> value=new AtomicReference<>("");
        scenario.onActivity(activity -> { TextView view=activity.findViewById(id); if(view!=null)value.set(view.getText().toString()); });return value.get();
    }
    private void refreshUi() {
        scenario.onActivity(activity -> {
            ScoreViewModel model = new ViewModelProvider(activity).get(ScoreViewModel.class);
            model.repository.execute(model.repository::refresh);
        });
    }
    private void option(int label) {
        Espresso.openActionBarOverflowOrOptionsMenu(context);onView(withText(R.string.options)).perform(click());onView(withText(label)).perform(click());
    }
    private void enter(int id,String value) { onView(withId(id)).perform(reveal(),replaceText(value),closeSoftKeyboard()); }
    private void clickId(int id) { onView(withId(R.id.screen_list)).perform(findAndClick(withId(id))); }
    private void clickText(int label) { onView(withId(R.id.screen_list)).perform(findAndClick(withText(label))); }
    private static ViewAction findAndClick(Matcher<View> target) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints(){return isAssignableFrom(RecyclerView.class);}
            @Override public String getDescription(){return "reveal and click a scoring control";}
            @Override public void perform(UiController controller,View view){
                RecyclerView list=(RecyclerView)view;
                for(int i=0;i<list.getAdapter().getItemCount();i++){
                    list.scrollToPosition(i);controller.loopMainThreadUntilIdle();
                    RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(i);
                    View found=holder==null?null:find(holder.itemView,target);
                    if(found!=null&&found.isClickable()){reveal().perform(controller,found);click().perform(controller,found);return;}
                }
                throw new AssertionError("Scoring control not found: "+target);
            }
        };
    }
    private static View find(View view,Matcher<View> target){
        if(target.matches(view)&&view.isClickable())return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            View found=find(((ViewGroup)view).getChildAt(i),target);if(found!=null)return found;
        }
        return null;
    }
    private static ViewAction reveal(){return new ViewAction(){
        @Override public Matcher<View> getConstraints(){return isAssignableFrom(View.class);}
        @Override public String getDescription(){return "scroll input or action above the keyboard";}
        @Override public void perform(UiController controller,View view){
            view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),true);controller.loopMainThreadUntilIdle();
            assertTrue("Control remains reachable",androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast(90).matches(view));
        }
    };}
    private interface Condition {boolean test();}
    private static void await(Condition condition){
        long end=SystemClock.uptimeMillis()+10_000;
        do{InstrumentationRegistry.getInstrumentation().waitForIdleSync();if(condition.test())return;SystemClock.sleep(25);}while(SystemClock.uptimeMillis()<end);
        assertTrue("Expected strategy state arrived within ten seconds",condition.test());
    }
}
