package git.artdeell.mojo.input.handleview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import git.artdeell.mojo.R;
import git.artdeell.mojo.game.hud.HudStyle;
import git.artdeell.mojo.prefs.LauncherPreferences;

/** The in-game menu (gear) button: an angular red-black hexagon with a white gear. */
public class DrawerPullButton extends View implements HudStyle.Pinchable {
    public DrawerPullButton(Context context) {super(context); init();}
    public DrawerPullButton(Context context, @Nullable AttributeSet attrs) {super(context, attrs); init();}

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private VectorDrawableCompat mDrawable;
    private float mDensity = 1f;
    /** Set by the game activity: the button then follows the HUD size. In the control editor it keeps its xml size. */
    private String mSizeKey;
    private float mUserScale = 1f;

    private void init(){
        mDensity = getResources().getDisplayMetrics().density;
        mDrawable = VectorDrawableCompat.create(getContext().getResources(), R.drawable.ic_sharp_settings_24, null);
        mFill.setStyle(Paint.Style.FILL);
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setColor(HudStyle.ACCENT);
        mGlow.setStyle(Paint.Style.STROKE);
        mGlow.setColor(HudStyle.ACCENT);
        mGlow.setAlpha(46);
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
        float size = Math.min(getWidth(), getHeight());
        float stroke = Math.max(1f, size * 0.04f);
        mStroke.setStrokeWidth(stroke);
        mGlow.setStrokeWidth(stroke * 3f);
        float pad = stroke * 2.2f;
        float l = (getWidth() - size) / 2f + pad, t = (getHeight() - size) / 2f + pad;
        float r = l + size - pad * 2, b = t + size - pad * 2;
        float cx = (l + r) / 2f, h = r - l;
        float cut = h * 0.27f;

        // hexagon with the points left and right
        mPath.reset();
        mPath.moveTo(l + cut, t);
        mPath.lineTo(r - cut, t);
        mPath.lineTo(r, (t + b) / 2f);
        mPath.lineTo(r - cut, b);
        mPath.lineTo(l + cut, b);
        mPath.lineTo(l, (t + b) / 2f);
        mPath.close();
        mFill.setShader(new LinearGradient(0, t, 0, b, HudStyle.PANEL_TOP, HudStyle.PANEL_BOTTOM, Shader.TileMode.CLAMP));
        canvas.drawPath(mPath, mFill);
        canvas.drawPath(mPath, mGlow);
        canvas.drawPath(mPath, mStroke);

        float icon = h * 0.56f;
        mDrawable.setBounds(0, 0, Math.round(icon), Math.round(icon));
        canvas.save();
        canvas.translate(cx - icon / 2f, (t + b) / 2f - icon / 2f);
        mDrawable.draw(canvas);
        canvas.restore();
    }
}
