package android.example.judgement;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Proves that Android can resolve and open this installed build, including after recreation. */
@RunWith(AndroidJUnit4.class)
public class LauncherSmokeTest {
    private Context context;
    @Before public void resetFixture() {
        context = ApplicationProvider.getApplicationContext();
        for (String database : context.databaseList()) context.deleteDatabase(database);
    }
    @Test public void installedLauncherOpensAnInteractiveHome() {
        ActivityScenario<Activity> scenario = ActivityScenario.launch(launcher());
        try { awaitInteractiveHome(scenario); }
        finally { android.example.judgement.ui.RepositoryTestCleanup.close(scenario, null); }
    }
    @Test public void launcherRemainsInteractiveAfterActivityRecreation() {
        ActivityScenario<Activity> scenario = ActivityScenario.launch(launcher());
        try {
            awaitInteractiveHome(scenario); scenario.recreate(); awaitInteractiveHome(scenario);
        } finally { android.example.judgement.ui.RepositoryTestCleanup.close(scenario, null); }
    }
    private Intent launcher() {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull("Installed application exposes a launcher", launch);
        assertNotNull(launch.getComponent());
        assertEquals(context.getPackageName(), launch.getComponent().getPackageName());
        return launch;
    }
    private static void awaitInteractiveHome(ActivityScenario<Activity> scenario) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        do {
            boolean[] ready = {false};
            scenario.onActivity(activity -> {
                assertFalse(activity.isFinishing()); assertFalse(activity.isDestroyed());
                ready[0] = hasLabelledAction(activity.findViewById(android.R.id.content));
            });
            if (ready[0]) return;
            SystemClock.sleep(30);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Launcher did not expose a visible, enabled, labelled action within10seconds");
    }
    private static boolean hasLabelledAction(View view) {
        if (view.isShown() && view.isEnabled() && view.isClickable()
                && ((view instanceof TextView && ((TextView) view).getText().length() > 0)
                || (view.getContentDescription() != null && view.getContentDescription().length() > 0))) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int child = 0; child < group.getChildCount(); child++) if (hasLabelledAction(group.getChildAt(child))) return true;
        }
        return false;
    }
}
