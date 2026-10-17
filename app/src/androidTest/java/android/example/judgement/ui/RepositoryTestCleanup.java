package android.example.judgement.ui;

import android.app.Activity;
import android.example.judgement.data.ScoreRepository;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.assertTrue;

/** A later fixture can delete the database only after every prior connection has closed. */
public final class RepositoryTestCleanup {
    private RepositoryTestCleanup() { }

    public static void close(ActivityScenario<? extends Activity> scenario, ScoreRepository inspector) {
        AtomicReference<ScoreRepository> activityRepository = new AtomicReference<>();
        if (scenario != null) {
            scenario.onActivity(activity -> activityRepository.set(new ViewModelProvider((MainActivity) activity)
                    .get(ScoreViewModel.class).repository));
            scenario.close();
        }
        if (inspector != null) inspector.close();
        if (activityRepository.get() != null) awaitClosed(activityRepository.get());
        if (inspector != null) awaitClosed(inspector);
    }

    private static void awaitClosed(ScoreRepository repository) {
        try {
            java.lang.reflect.Field field = ScoreRepository.class.getDeclaredField("executor");
            field.setAccessible(true);
            ExecutorService executor = (ExecutorService) field.get(repository);
            assertTrue("Queued saves and database closure finish before the next fixture",
                    executor.awaitTermination(10, TimeUnit.SECONDS));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not observe repository closure", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Repository cleanup was interrupted", interrupted);
        }
    }
}
