package git.artdeell.mojo.game.hud;

import git.artdeell.mojo.prefs.LauncherPreferences;

/** Shared look and size of the in-game overlays (FPS, RAM, menu button). */
public final class HudStyle {
    private HudStyle() {}

    public static final String PREF_SCALE = "spiritHudScale";
    public static final int ACCENT = 0xFFA6F03A;      // Spirit lime
    public static final int PANEL_TOP = 0xE61C2B0A;
    public static final int PANEL_BOTTOM = 0xE60A1204;
    public static final int GOOD = 0xFF39FF5A;
    public static final int WARN = 0xFFFFC233;
    public static final int BAD = 0xFFFF4D4D;

    /** 0.6 .. 1.6, set from the Quick settings slider. */
    public static float globalScale() {
        try {
            return Math.max(0.6f, Math.min(1.6f, LauncherPreferences.DEFAULT_PREF.getFloat(PREF_SCALE, 1f)));
        } catch (ClassCastException e) {
            return 1f;
        }
    }

    public static void setGlobalScale(float s) {
        LauncherPreferences.DEFAULT_PREF.edit().putFloat(PREF_SCALE, Math.max(0.6f, Math.min(1.6f, s))).apply();
    }

    /** Implemented by overlays that can be pinched to a size of their own. */
    public interface Pinchable {
        float getUserScale();
        void setUserScale(float scale);
    }
}
