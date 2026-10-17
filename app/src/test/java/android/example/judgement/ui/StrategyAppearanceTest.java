package android.example.judgement.ui;

import android.content.Context;
import android.example.judgement.R;
import android.view.ContextThemeWrapper;
import androidx.core.graphics.ColorUtils;
import com.google.android.material.color.MaterialColors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertTrue;

/** Resolves the real theme roles so palette or inherited Material-role regressions are visible. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="notnight")
public final class StrategyAppearanceTest {
    @Test public void lightThemesKeepTextAndActionsReadable() { checkBothThemes(); }
    @Test @Config(qualifiers="night") public void darkThemesKeepTextAndActionsReadable() { checkBothThemes(); }
    private static void checkBothThemes() {
        for(int theme:new int[]{R.style.Theme_Score,R.style.Theme_Score_Aggressive}) {
            Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),theme);
            contrast(context,com.google.android.material.R.attr.colorOnPrimary,androidx.appcompat.R.attr.colorPrimary);
            contrast(context,com.google.android.material.R.attr.colorOnPrimaryContainer,com.google.android.material.R.attr.colorPrimaryContainer);
            for(int surface:new int[]{com.google.android.material.R.attr.colorSurface,
                    com.google.android.material.R.attr.colorSurfaceContainerLow,
                    com.google.android.material.R.attr.colorSurfaceContainer,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh,
                    com.google.android.material.R.attr.colorSurfaceContainerHighest}) {
                contrast(context,com.google.android.material.R.attr.colorOnSurface,surface);
                contrast(context,com.google.android.material.R.attr.colorOnSurfaceVariant,surface);
            }
        }
    }
    private static void contrast(Context context,int foreground,int background) {
        int front=MaterialColors.getColor(context,foreground,"StrategyAppearanceTest");
        int back=MaterialColors.getColor(context,background,"StrategyAppearanceTest");
        double contrast=ColorUtils.calculateContrast(front,back);
        assertTrue("Text contrast for "+context.getResources().getResourceEntryName(foreground)+" on "+
                context.getResources().getResourceEntryName(background)+" is "+contrast,contrast>=4.5);
    }
}
