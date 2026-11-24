package git.artdeell.mojo.game.hud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import git.artdeell.mojo.prefs.LauncherPreferences;

/**
 * Angular red-black readout ("FPS: 120", "RAM: 1.2/3.0 GB") with chevrons on both sides.
 * Tap: compact mode (value only). Pinch (see OverlayDrag): own size. Global size: HudStyle.
 */
public class HudPill extends View implements HudStyle.Pinchable {
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mChevron = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mRect = new RectF();
    private final float mDensity;

    private String mLabel = "";
    private String mValue = "--";
    private int mValueColor = HudStyle.GOOD;
    private float mBarFraction = -1f; // < 0: no bar
    private boolean mCompact = false;
    private float mUserScale = 1f;
    private String mPrefKey;

    public HudPill(Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        mFill.setStyle(Paint.Style.FILL);
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setColor(HudStyle.ACCENT);
        mGlow.setStyle(Paint.Style.STROKE);
        mGlow.setColor(HudStyle.ACCENT);
        mChevron.setStyle(Paint.Style.STROKE);
        mChevron.setStrokeCap(Paint.Cap.ROUND);
        mChevron.setStrokeJoin(Paint.Join.ROUND);
        mLabelPaint.setColor(0xFFFFFFFF);
        mLabelPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        mValuePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        mBar.setStyle(Paint.Style.FILL);
        setTranslationZ(5);
    }

    /** Loads the saved size and compact state for this overlay (key is the one used by OverlayDrag). */
    public void bindPrefs(String key) {
        mPrefKey = key;
        try {
            mUserScale = Math.max(0.5f, Math.min(2.5f, LauncherPreferences.DEFAULT_PREF.getFloat(key + "Scale", 1f)));
            mCompact = LauncherPreferences.DEFAULT_PREF.getBoolean(key + "Compact", false);
        } catch (ClassCastException ignored) {}
        requestLayout();
        invalidate();
    }

    public void toggleCompact() {
        mCompact = !mCompact;
        if(mPrefKey != null) LauncherPreferences.DEFAULT_PREF.edit().putBoolean(mPrefKey + "Compact", mCompact).apply();
        requestLayout();
        invalidate();
    }

    public void setLabel(String label) {
        if(label.equals(mLabel)) return;
        mLabel = label;
        requestLayout();
        invalidate();
    }

    public void setValue(String value, int color, float barFraction) {
        boolean relayout = !value.equals(mValue);
        mValue = value;
        mValueColor = color;
        mBarFraction = barFraction;
        if(relayout) requestLayout();
        invalidate();
    }

    @Override public float getUserScale() { return mUserScale; }

    @Override
    public void setUserScale(float scale) {
        mUserScale = Math.max(0.5f, Math.min(2.5f, scale));
        requestLayout();
        invalidate();
    }

    /** Re-reads the global size (after the Quick settings slider moved). */
    public void refreshScale() {
        requestLayout();
        invalidate();
    }

    private float unit() {
        return mDensity * HudStyle.globalScale() * mUserScale;
    }

    private void configurePaints(float u) {
        mLabelPaint.setTextSize(15 * u);
        mValuePaint.setTextSize(17 * u);
        mStroke.setStrokeWidth(Math.max(1f, 1.4f * u));
        mGlow.setStrokeWidth(Math.max(2f, 4f * u));
        mChevron.setStrokeWidth(Math.max(1.5f, 2.2f * u));
    }

    private float chevronSpace(float u) {
        return 0f; // no side arrows
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float u = unit();
        configurePaints(u);
        float text = mValuePaint.measureText(mValue);
        if(!mCompact) text += mLabelPaint.measureText(mLabel + " ") ;
        float padX = 14 * u;
        float body = text + padX * 2;
        float w = body + chevronSpace(u) * 2;
        float h = (mBarFraction >= 0 && !mCompact ? 42 : 34) * u;
        setMeasuredDimension((int) Math.ceil(w), (int) Math.ceil(h));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float u = unit();
        configurePaints(u);
        float w = getWidth(), h = getHeight();
        float cs = chevronSpace(u);
        float left = cs + mStroke.getStrokeWidth(), right = w - cs - mStroke.getStrokeWidth();
        float top = mGlow.getStrokeWidth() / 2, bottom = h - mGlow.getStrokeWidth() / 2;
        float cut = Math.min((bottom - top) * 0.3f, 10 * u);

        mPath.reset();
        mPath.moveTo(left + cut, top);
        mPath.lineTo(right - cut, top);
        mPath.lineTo(right, top + cut);
        mPath.lineTo(right, bottom - cut);
        mPath.lineTo(right - cut, bottom);
        mPath.lineTo(left + cut, bottom);
        mPath.lineTo(left, bottom - cut);
        mPath.lineTo(left, top + cut);
        mPath.close();

        mFill.setShader(new LinearGradient(0, top, 0, bottom, HudStyle.PANEL_TOP, HudStyle.PANEL_BOTTOM, Shader.TileMode.CLAMP));
        canvas.drawPath(mPath, mFill);
        mGlow.setAlpha(46);
        canvas.drawPath(mPath, mGlow);
        mStroke.setAlpha(255);
        canvas.drawPath(mPath, mStroke);

        // text, centred in the body
        float labelW = mCompact ? 0 : mLabelPaint.measureText(mLabel + " ");
        float valueW = mValuePaint.measureText(mValue);
        float x = (left + right) / 2f - (labelW + valueW) / 2f;
        float textBox = (mBarFraction >= 0 && !mCompact) ? (bottom - top) * 0.78f : (bottom - top);
        float base = top + textBox / 2f - (mValuePaint.ascent() + mValuePaint.descent()) / 2f;
        if(!mCompact) canvas.drawText(mLabel + " ", x, base, mLabelPaint);
        mValuePaint.setColor(mValueColor);
        canvas.drawText(mValue, x + labelW, base, mValuePaint);

        if(mBarFraction >= 0 && !mCompact) {
            float bx0 = left + cut + 2 * u, bx1 = right - cut - 2 * u, by1 = bottom - 5 * u, by0 = by1 - 3 * u;
            mBar.setColor(0x44FFFFFF);
            mRect.set(bx0, by0, bx1, by1);
            canvas.drawRect(mRect, mBar);
            mBar.setColor(mValueColor);
            mRect.set(bx0, by0, bx0 + (bx1 - bx0) * Math.max(0f, Math.min(1f, mBarFraction)), by1);
            canvas.drawRect(mRect, mBar);
        }
    }

    /** One "<" or ">" whose tip is at (x, cy) and whose arms open away from the tip. */
    private void drawChevron(Canvas canvas, float x, float cy, float half, boolean pointRight, int alpha) {
        float dx = half * 0.55f;
        mPath.reset();
        if(pointRight) {
            mPath.moveTo(x - dx, cy - half);
            mPath.lineTo(x, cy);
            mPath.lineTo(x - dx, cy + half);
        } else {
            mPath.moveTo(x + dx, cy - half);
            mPath.lineTo(x, cy);
            mPath.lineTo(x + dx, cy + half);
        }
        mChevron.setColor(HudStyle.ACCENT);
        mChevron.setAlpha(alpha);
        canvas.drawPath(mPath, mChevron);
    }
}
