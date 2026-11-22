package git.artdeell.mojo.input.handleview;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import git.artdeell.mojo.R;
import git.artdeell.mojo.game.hud.HudStyle;
import git.artdeell.mojo.prefs.LauncherPreferences;

/** The in-game menu (gear) button: just the white gear icon. */
public class DrawerPullButton extends View implements HudStyle.Pinchable {
    public DrawerPullButton(Context context) {super(context); init();}
    public DrawerPullButton(Context context, @Nullable AttributeSet attrs) {super(context, attrs); init();}

    private VectorDrawableCompat mDrawable;
    private float mDensity = 1f;
    /** Set by the game activity: the button then follows the HUD size. In the control editor it keeps its xml size. */
    private String mSizeKey;
    private float mUserScale = 1f;

    private void init(){
        mDensity = getResources().getDisplayMetrics().density;
        mDrawable = VectorDrawableCompat.create(getContext().getResources(), R.drawable.ic_sharp_settings_24, null);
        setAlpha(0.92f);
    }

    /** In game: size follows the HUD size slider and pinch. */
    public void enableHudSizing(String key) {
        mSizeKey = key;
        try {
            mUserScale = Math.max(0.5f, Math.min(2.5f, LauncherPreferences.DEFAULT_PREF.getFloat(key + "Scale", 1f)));
        } catch (ClassCastException ignored) {}
        applySize();
    }

    public void refreshScale() {
        applySize();
    }

    private void applySize() {
        if(mSizeKey == null) return;
        ViewGroup.LayoutParams lp = getLayoutParams();
        if(lp == null) return;
        int px = Math.round(42 * mDensity * HudStyle.globalScale() * mUserScale);
        if(lp.width != px || lp.height != px) {
            lp.width = px;
            lp.height = px;
            setLayoutParams(lp);
        }
    }

    @Override public float getUserScale() { return mUserScale; }

    @Override
    public void setUserScale(float scale) {
        mUserScale = Math.max(0.5f, Math.min(2.5f, scale));
        applySize();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Only the gear: no outline, no background. A soft dark copy under it keeps it readable on bright scenes.
        float size = Math.min(getWidth(), getHeight());
        int icon = Math.round(size * 0.78f);
        float x = (getWidth() - icon) / 2f, y = (getHeight() - icon) / 2f;
        mDrawable.setBounds(0, 0, icon, icon);
        float shadow = Math.max(1f, mDensity * 1.2f);
        canvas.save();
        canvas.translate(x + shadow, y + shadow);
        mDrawable.setTint(0x99000000);
        mDrawable.draw(canvas);
        canvas.restore();
        canvas.save();
        canvas.translate(x, y);
        mDrawable.setTint(0xFFFFFFFF);
        mDrawable.draw(canvas);
        canvas.restore();
    }
}
