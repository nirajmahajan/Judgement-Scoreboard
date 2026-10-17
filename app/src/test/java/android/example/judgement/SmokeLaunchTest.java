package android.example.judgement;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.takahirom.roborazzi.RoborazziKt;
import com.github.takahirom.roborazzi.RoborazziOptions;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** A permanent launcher contract independent of activity class names or navigation implementation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h740dp-notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class SmokeLaunchTest {
    @Test public void launcherOpensAnInteractiveHomeAndRendersIt() throws Throwable {
        Context context = RuntimeEnvironment.getApplication();
        for (String database : context.databaseList()) context.deleteDatabase(database);
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull("Installed application exposes a launcher", launch);
        assertNotNull(launch.getComponent());
        Class<? extends Activity> activityClass = Class.forName(launch.getComponent().getClassName()).asSubclass(Activity.class);
        try (ActivityController<? extends Activity> controller = Robolectric.buildActivity(activityClass, launch).setup().visible()) {
            Activity activity = controller.get();
            View decor = activity.getWindow().getDecorView();
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            int height = activity.getResources().getDisplayMetrics().heightPixels;
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            do {
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                decor.layout(0, 0, width, height);
                if (hasLabelledAction(activity.findViewById(android.R.id.content))) break;
                Thread.sleep(10);
            } while (System.nanoTime() < deadline);
            assertFalse(activity.isFinishing());
            assertTrue("Home has a visible, enabled, labelled action", hasLabelledAction(activity.findViewById(android.R.id.content)));
            RoborazziOptions options = new RoborazziOptions();
            if (options.getTaskType().isEnabled()) {
                assertEquals("Record and verify in the pinned Linux image", "Linux", System.getProperty("os.name"));
                File output = new File("src/test/screenshots/smoke_home_320.png").getAbsoluteFile();
                MethodHandles.publicLookup().findStatic(RoborazziKt.class, "captureRoboImage",
                        MethodType.methodType(void.class, View.class, File.class, RoborazziOptions.class))
                        .invoke(decor, output, options);
            }
        }
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
