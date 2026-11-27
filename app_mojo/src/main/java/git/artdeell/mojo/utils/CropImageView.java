package git.artdeell.mojo.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Shows a picture with a square crop frame. Drag inside the frame to move it, drag a corner to resize it.
 * Used to cut the cursor image out of a bigger picture.
 */
public class CropImageView extends View {
    private Bitmap mBitmap;
    private final RectF mImageRect = new RectF();
    private final RectF mCrop = new RectF();
    private final Paint mShade = new Paint();
    private final Paint mLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHandle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mChecker = new Paint();
    private float mDownX, mDownY;
    private RectF mDownCrop = new RectF();
    private int mMode; // 0 none, 1 move, 2..5 corners (tl, tr, bl, br)
    private final float mTouch;

    public CropImageView(Context c) { this(c, null); }

    public CropImageView(Context c, AttributeSet a) {
        super(c, a);
        float d = c.getResources().getDisplayMetrics().density;
        mTouch = 28 * d;
        mShade.setColor(Color.argb(150, 0, 0, 0));
        mLine.setColor(Color.rgb(166, 240, 58));
        mLine.setStyle(Paint.Style.STROKE);
        mLine.setStrokeWidth(2 * d);
        mHandle.setColor(Color.rgb(166, 240, 58));
        mChecker.setColor(Color.rgb(60, 60, 60));
    }

    public void setBitmap(Bitmap b) {
        mBitmap = b;
        mCrop.setEmpty();
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        super.onMeasure(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutImage();
    }

    private void layoutImage() {
        if(mBitmap == null || getWidth() == 0) return;
        float scale = Math.min(getWidth() / (float) mBitmap.getWidth(), getHeight() / (float) mBitmap.getHeight());
        float iw = mBitmap.getWidth() * scale, ih = mBitmap.getHeight() * scale;
        mImageRect.set((getWidth() - iw) / 2f, (getHeight() - ih) / 2f, (getWidth() + iw) / 2f, (getHeight() + ih) / 2f);
        if(mCrop.isEmpty()) {
            float side = Math.min(iw, ih) * 0.8f;
            mCrop.set(mImageRect.centerX() - side / 2, mImageRect.centerY() - side / 2,
                    mImageRect.centerX() + side / 2, mImageRect.centerY() + side / 2);
        }
    }

    /** @return the part of the picture inside the frame, in the picture's own pixels */
    public Bitmap getCropped() {
        if(mBitmap == null) return null;
        float scale = mBitmap.getWidth() / mImageRect.width();
        int x = Math.max(0, Math.round((mCrop.left - mImageRect.left) * scale));
        int y = Math.max(0, Math.round((mCrop.top - mImageRect.top) * scale));
        int s = Math.round(mCrop.width() * scale);
        s = Math.max(1, Math.min(s, Math.min(mBitmap.getWidth() - x, mBitmap.getHeight() - y)));
        return Bitmap.createBitmap(mBitmap, x, y, s, s);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(mBitmap == null) return;
        layoutImage();
        canvas.drawBitmap(mBitmap, null, mImageRect, null);
        // dim everything outside the frame
        canvas.drawRect(mImageRect.left, mImageRect.top, mImageRect.right, mCrop.top, mShade);
        canvas.drawRect(mImageRect.left, mCrop.bottom, mImageRect.right, mImageRect.bottom, mShade);
        canvas.drawRect(mImageRect.left, mCrop.top, mCrop.left, mCrop.bottom, mShade);
        canvas.drawRect(mCrop.right, mCrop.top, mImageRect.right, mCrop.bottom, mShade);
        canvas.drawRect(mCrop, mLine);
        float r = mTouch / 3f;
        canvas.drawCircle(mCrop.left, mCrop.top, r, mHandle);
        canvas.drawCircle(mCrop.right, mCrop.top, r, mHandle);
        canvas.drawCircle(mCrop.left, mCrop.bottom, r, mHandle);
        canvas.drawCircle(mCrop.right, mCrop.bottom, r, mHandle);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = x; mDownY = y;
                mDownCrop.set(mCrop);
                if(near(x, y, mCrop.left, mCrop.top)) mMode = 2;
                else if(near(x, y, mCrop.right, mCrop.top)) mMode = 3;
                else if(near(x, y, mCrop.left, mCrop.bottom)) mMode = 4;
                else if(near(x, y, mCrop.right, mCrop.bottom)) mMode = 5;
                else if(mCrop.contains(x, y)) mMode = 1;
                else mMode = 0;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if(mMode == 1) move(x - mDownX, y - mDownY);
                else if(mMode >= 2) resize(x, y);
                invalidate();
                return true;
            default:
                mMode = 0;
                return true;
        }
    }

    private boolean near(float x, float y, float cx, float cy) {
        return Math.abs(x - cx) < mTouch && Math.abs(y - cy) < mTouch;
    }

    private void move(float dx, float dy) {
        float w = mDownCrop.width(), h = mDownCrop.height();
        float l = Math.max(mImageRect.left, Math.min(mImageRect.right - w, mDownCrop.left + dx));
        float t = Math.max(mImageRect.top, Math.min(mImageRect.bottom - h, mDownCrop.top + dy));
        mCrop.set(l, t, l + w, t + h);
    }

    private void resize(float x, float y) {
        // the opposite corner stays where it is, the frame stays square
        float ax = (mMode == 2 || mMode == 4) ? mDownCrop.right : mDownCrop.left;
        float ay = (mMode == 2 || mMode == 3) ? mDownCrop.bottom : mDownCrop.top;
        float sx = (mMode == 2 || mMode == 4) ? -1 : 1;
        float sy = (mMode == 2 || mMode == 3) ? -1 : 1;
        float side = Math.max((x - ax) * sx, (y - ay) * sy);
        float maxX = sx > 0 ? mImageRect.right - ax : ax - mImageRect.left;
        float maxY = sy > 0 ? mImageRect.bottom - ay : ay - mImageRect.top;
        side = Math.max(mTouch, Math.min(side, Math.min(maxX, maxY)));
        float nx = ax + sx * side, ny = ay + sy * side;
        mCrop.set(Math.min(ax, nx), Math.min(ay, ny), Math.max(ax, nx), Math.max(ay, ny));
    }
}
