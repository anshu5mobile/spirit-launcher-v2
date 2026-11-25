package git.artdeell.mojo.browse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.NonNull;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.input.ControlData;
import git.artdeell.mojo.input.ControlDrawerData;
import git.artdeell.mojo.input.ControlJoystickData;
import git.artdeell.mojo.input.CustomControls;

/**
 * Flat picture of a control layout: every button as a rounded box with its name, drawn the way the game would place
 * it on this phone's screen (landscape) and scaled down to fit. It does not run anything, it is only for looking.
 */
public final class ControlPreviewView extends View {
    private final CustomControls mControls;
    private final int mRefW, mRefH;
    private final float mDensity;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG), mStroke = new Paint(Paint.ANTI_ALIAS_FLAG), mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    public ControlPreviewView(Context ctx, @NonNull CustomControls controls) {
        super(ctx);
        mControls = controls;
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        mDensity = dm.density;
        mRefW = Math.max(dm.widthPixels, dm.heightPixels);
        mRefH = Math.min(dm.widthPixels, dm.heightPixels);
        if(Tools.currentDisplayMetrics == null) Tools.currentDisplayMetrics = dm; // positions use it for ${margin}
        mStroke.setStyle(Paint.Style.STROKE);
        mText.setTextAlign(Paint.Align.CENTER);
        mText.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, (int) (w * (float) mRefH / mRefW));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float scale = getWidth() / (float) mRefW;
        canvas.drawRect(0, 0, getWidth(), getHeight(), mFillBg());
        
        if(mControls.mControlDataList != null) for(ControlData d : mControls.mControlDataList) draw(canvas, d, scale, false);
        if(mControls.mJoystickDataList != null) for(ControlJoystickData d : mControls.mJoystickDataList) draw(canvas, d, scale, true);
        if(mControls.mDrawerDataList != null) for(ControlDrawerData d : mControls.mDrawerDataList) if(d != null) draw(canvas, d.properties, scale, false);
    }

    private Paint mBgPaint;
    private Paint mFillBg() {
        if(mBgPaint == null) { mBgPaint = new Paint(); mBgPaint.setColor(0xFF10160A); }
        return mBgPaint;
    }

    private void draw(Canvas canvas, ControlData d, float scale, boolean round) {
        if(d == null) return;
        try {
            float x = d.insertDynamicPos(d.dynamicX, mRefW, mRefH) * scale;
            float y = d.insertDynamicPos(d.dynamicY, mRefW, mRefH) * scale;
            float w = d.getWidth() * scale, h = d.getHeight() * scale;
            if(w <= 0 || h <= 0) return;
            float alpha = d.opacity <= 0 ? 0.55f : Math.min(1f, d.opacity);
            mFill.setColor(0xFFA6F03A);
            mFill.setAlpha((int) (40 + 60 * alpha));
            mStroke.setColor(0xFFA6F03A);
            mStroke.setAlpha((int) (120 + 135 * alpha));
            mStroke.setStrokeWidth(Math.max(1f, 1.2f * mDensity * scale * 2));
            mRect.set(x, y, x + w, y + h);
            float r = round ? Math.min(w, h) / 2 : Math.min(w, h) * Math.min(0.5f, Math.max(0.08f, d.cornerRadius / 100f));
            canvas.drawRoundRect(mRect, r, r, mFill);
            canvas.drawRoundRect(mRect, r, r, mStroke);
            if(d.name != null && !d.name.isEmpty() && d.name.charAt(0) != '\u0000') {
                mText.setTextSize(Math.max(6f * mDensity * scale * 2, Math.min(h * 0.35f, 11f * mDensity * scale * 2)));
                String label = d.name.length() > 8 ? d.name.substring(0, 8) : d.name;
                canvas.drawText(label, mRect.centerX(), mRect.centerY() - (mText.ascent() + mText.descent()) / 2f, mText);
            }
        } catch (RuntimeException ignored) {
            // a button with a formula this preview cannot evaluate is just left out
        }
    }
}
