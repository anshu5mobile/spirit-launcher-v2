package git.artdeell.mojo.game;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import git.artdeell.mojo.prefs.LauncherPreferences;

/** Makes a view draggable inside its parent; position is saved (as fractions) in the preferences. */
public final class OverlayDrag {
    private OverlayDrag() {}

    public static void attach(final View v, final String key) {
        final int slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
        final float[] st = new float[4]; // downRawX, downRawY, startX, startY
        final boolean[] moved = {false};
        v.setOnTouchListener((view, e) -> {
            View parent = (View) view.getParent();
            if(parent == null) return false;
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    st[0] = e.getRawX(); st[1] = e.getRawY();
                    st[2] = view.getX(); st[3] = view.getY();
                    moved[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - st[0], dy = e.getRawY() - st[1];
                    if(!moved[0] && Math.hypot(dx, dy) < slop) return true;
                    moved[0] = true;
                    float nx = Math.max(0, Math.min(parent.getWidth() - view.getWidth(), st[2] + dx));
                    float ny = Math.max(0, Math.min(parent.getHeight() - view.getHeight(), st[3] + dy));
                    view.setX(nx); view.setY(ny);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if(moved[0]) {
                        float pw = Math.max(1, parent.getWidth() - view.getWidth());
                        float ph = Math.max(1, parent.getHeight() - view.getHeight());
                        LauncherPreferences.DEFAULT_PREF.edit()
                                .putFloat(key + "X", view.getX() / pw)
                                .putFloat(key + "Y", view.getY() / ph).apply();
                    } else {
                        view.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
            }
            return false;
        });
    }

    /** Restores a saved position once the parent is laid out. */
    public static void restore(final View v, final String key) {
        v.post(() -> {
            View parent = (View) v.getParent();
            if(parent == null || !LauncherPreferences.DEFAULT_PREF.contains(key + "X")) return;
            float fx = LauncherPreferences.DEFAULT_PREF.getFloat(key + "X", 0f);
            float fy = LauncherPreferences.DEFAULT_PREF.getFloat(key + "Y", 0f);
            v.setX(fx * Math.max(0, parent.getWidth() - v.getWidth()));
            v.setY(fy * Math.max(0, parent.getHeight() - v.getHeight()));
        });
    }
}
