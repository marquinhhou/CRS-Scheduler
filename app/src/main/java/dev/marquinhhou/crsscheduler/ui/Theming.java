package dev.marquinhhou.crsscheduler.ui;

import android.content.Context;
import android.os.Build;

import androidx.core.content.ContextCompat;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore.ThemeFamily;

/**
 * Resolves the active ThemeFamily into concrete resource ids/colors. RemoteViews can't
 * apply a runtime Activity theme, so every screen and widget picks resources this way
 * instead of ?attr/. Each themed asset exists as 3 files (_ge/_ne/_adaptive).
 */
public final class Theming {

    private Theming() {}

    public static ThemeFamily family(Context context) {
        return SettingsStore.getThemeFamily(context);
    }

    public static boolean supportsAdaptive() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static int pick(Context context, int geRes, int neRes, int adaptiveRes) {
        switch (family(context)) {
            case NE: return neRes;
            case ADAPTIVE: return adaptiveRes;
            case GE:
            default: return geRes;
        }
    }

    public static int color(Context context, int geColorRes, int neColorRes, int adaptiveColorRes) {
        return ContextCompat.getColor(context, pick(context, geColorRes, neColorRes, adaptiveColorRes));
    }

    public static int activityThemeRes(Context context) {
        switch (family(context)) {
            case NE: return R.style.Theme_CRSScheduler_NE;
            case ADAPTIVE: return R.style.Theme_CRSScheduler_Adaptive;
            case GE:
            default: return R.style.Theme_CRSScheduler_GE;
        }
    }

    public static int dialogThemeRes(Context context) {
        switch (family(context)) {
            case NE: return R.style.Theme_CRSScheduler_NE_Dialog;
            case ADAPTIVE: return R.style.Theme_CRSScheduler_Adaptive_Dialog;
            case GE:
            default: return R.style.Theme_CRSScheduler_GE_Dialog;
        }
    }

    /** Call in onCreate() before super.onCreate()/setContentView(). */
    public static void applyActivityTheme(android.app.Activity activity) {
        activity.setTheme(activityThemeRes(activity));
    }

    public static void applyDialogTheme(android.app.Activity activity) {
        activity.setTheme(dialogThemeRes(activity));
    }

    /** GE/Adaptive use a smooth stroked arc ring; NE keeps the original 28-dot glyph ring. */
    public static boolean usesDotRing(Context context) {
        return family(context) == ThemeFamily.NE;
    }

    /** GE/Adaptive use the system default font; NE keeps JetBrains Mono (see res/font). */
    public static boolean usesMonoFont(Context context) {
        return family(context) == ThemeFamily.NE;
    }
}
