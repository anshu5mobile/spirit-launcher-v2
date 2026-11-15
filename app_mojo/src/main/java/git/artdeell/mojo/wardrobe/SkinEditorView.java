package git.artdeell.mojo.wardrobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/** Pixel editor for a 64x64 skin: pen, eraser, picker, fill, undo/redo, pinch to zoom, two fingers to move. */
public class SkinEditorView extends View {
    public static final int PEN = 0, ERASER = 1, PICKER = 2, FILL = 3;

    public interface Listener {
        void onColorPicked(int color);
        void onHistoryChanged();
    }

    private static final int SIZE = 64;
    private static final int MAX_UNDO = 40;

    private final Bitmap mBitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
    private final Matrix mMatrix = new Matrix();
    private final Matrix mInverse = new Matrix();
    private final Paint mBitmapPaint = new Paint();
    private final Paint mFill = new Paint();
    private final Paint mLine = new Paint();
    private final RectF mRect = new RectF();
    private final Deque<int[]> mUndo = new ArrayDeque<>();
    private final Deque<int[]> mRedo = new ArrayDeque<>();
    private final ScaleGestureDetector mScale;
    private final float[] mPt = new float[2];

    private int mTool = PEN;
    private int mColor = Color.parseColor("#A6F03A");
    private boolean mShowGrid = true;
    private boolean mLaidOut;
    private boolean mStroke;
    private boolean mGesture;
    private float mLastX, mLastY;
    @Nullable private Listener mListener;

    public SkinEditorView(Context context) {
        this(context, null);
    }

    public SkinEditorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mBitmapPaint.setFilterBitmap(false);
        mLine.setColor(0x33FFFFFF);
        mLine.setStrokeWidth(0f);
        mScale = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                mMatrix.postScale(d.getScaleFactor(), d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                clampScale();
                invalidate();
                return true;
            }
        });
    }

    public void setListener(@Nullable Listener l) { mListener = l; }
    public void setTool(int tool) { mTool = tool; }
    public void setColor(int color) { mColor = color | 0xFF000000; }
    public int getColor() { return mColor; }
    public void setShowGrid(boolean show) { mShowGrid = show; invalidate(); }
    public boolean isShowGrid() { return mShowGrid; }
    public boolean canUndo() { return !mUndo.isEmpty(); }
    public boolean canRedo() { return !mRedo.isEmpty(); }

    public void setSkin(Bitmap skin) {
        int[] px = new int[SIZE * SIZE];
        skin.getPixels(px, 0, SIZE, 0, 0, Math.min(SIZE, skin.getWidth()), Math.min(SIZE, skin.getHeight()));
        mBitmap.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
        mUndo.clear();
        mRedo.clear();
        invalidate();
    }

    public Bitmap getSkin() {
        return Bitmap.createBitmap(mBitmap);
    }

    private int[] snapshot() {
        int[] px = new int[SIZE * SIZE];
        mBitmap.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
        return px;
    }

    public void undo() {
        if(mUndo.isEmpty()) return;
        mRedo.push(snapshot());
        int[] px = mUndo.pop();
        mBitmap.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
        changed();
    }

    public void redo() {
        if(mRedo.isEmpty()) return;
        mUndo.push(snapshot());
        int[] px = mRedo.pop();
        mBitmap.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
        changed();
    }

    private void changed() {
        invalidate();
        if(mListener != null) mListener.onHistoryChanged();
    }

    private void pushUndo() {
        mUndo.push(snapshot());
        while(mUndo.size() > MAX_UNDO) mUndo.removeLast();
        mRedo.clear();
        if(mListener != null) mListener.onHistoryChanged();
    }

    // ---------------------------------------------------------------- view

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float s = Math.min(w, h) / (float) SIZE;
        mMatrix.reset();
        mMatrix.postScale(s, s);
        mMatrix.postTranslate((w - s * SIZE) / 2f, (h - s * SIZE) / 2f);
        mLaidOut = true;
    }

    private float currentScale() {
        float[] v = new float[9];
        mMatrix.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    private void clampScale() {
        float s = currentScale();
        float min = Math.min(getWidth(), getHeight()) / (float) SIZE * 0.8f;
        float max = 60f;
        float target = Math.max(min, Math.min(max, s));
        if(target != s) mMatrix.postScale(target / s, target / s, getWidth() / 2f, getHeight() / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(!mLaidOut) return;
        canvas.save();
        canvas.concat(mMatrix);
        // checkerboard so transparent pixels are visible
        for(int y = 0; y < SIZE; y += 2) {
            for(int x = 0; x < SIZE; x += 2) {
                mFill.setColor(((x + y) / 2 % 2 == 0) ? 0xFF2A2F28 : 0xFF343A31);
                mRect.set(x, y, x + 2, y + 2);
                canvas.drawRect(mRect, mFill);
            }
        }
        canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
        if(mShowGrid && currentScale() >= 7f) {
            for(int i = 0; i <= SIZE; i++) {
                canvas.drawLine(i, 0, i, SIZE, mLine);
                canvas.drawLine(0, i, SIZE, i, mLine);
            }
        }
        canvas.restore();
    }

    // ---------------------------------------------------------------- touch

    private boolean toPixel(float sx, float sy, int[] out) {
        mMatrix.invert(mInverse);
        mPt[0] = sx; mPt[1] = sy;
        mInverse.mapPoints(mPt);
        int x = (int) Math.floor(mPt[0]), y = (int) Math.floor(mPt[1]);
        if(x < 0 || y < 0 || x >= SIZE || y >= SIZE) return false;
        out[0] = x; out[1] = y;
        return true;
    }

    private final int[] mXY = new int[2];

    private void apply(float sx, float sy) {
        if(!toPixel(sx, sy, mXY)) return;
        int x = mXY[0], y = mXY[1];
        switch(mTool) {
            case PEN: mBitmap.setPixel(x, y, mColor); break;
            case ERASER: mBitmap.setPixel(x, y, Color.TRANSPARENT); break;
            case PICKER: {
                int c = mBitmap.getPixel(x, y);
                if(Color.alpha(c) != 0) {
                    mColor = c | 0xFF000000;
                    if(mListener != null) mListener.onColorPicked(mColor);
                }
                break;
            }
            case FILL: floodFill(x, y); break;
        }
        invalidate();
    }

    private void floodFill(int sx, int sy) {
        int target = mBitmap.getPixel(sx, sy);
        if(target == mColor) return;
        int[] px = snapshot();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(sy * SIZE + sx);
        while(!stack.isEmpty()) {
            int i = stack.pop();
            if(px[i] != target) continue;
            px[i] = mColor;
            int x = i % SIZE, y = i / SIZE;
            if(x > 0) stack.push(i - 1);
            if(x < SIZE - 1) stack.push(i + 1);
            if(y > 0) stack.push(i - SIZE);
            if(y < SIZE - 1) stack.push(i + SIZE);
        }
        mBitmap.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        mScale.onTouchEvent(e);
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                mGesture = false;
                mStroke = true;
                if(mTool == PEN || mTool == ERASER || mTool == FILL) pushUndo();
                mLastX = e.getX(); mLastY = e.getY();
                apply(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                // A second finger turns this into a zoom/move gesture; the pixel from the first touch stays
                mGesture = true;
                mStroke = false;
                mLastX = avgX(e); mLastY = avgY(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                if(mGesture && e.getPointerCount() >= 2) {
                    float ax = avgX(e), ay = avgY(e);
                    mMatrix.postTranslate(ax - mLastX, ay - mLastY);
                    mLastX = ax; mLastY = ay;
                    invalidate();
                } else if(mStroke && mTool != FILL && mTool != PICKER) {
                    // Fill in the gaps when the finger moves fast
                    float dx = e.getX() - mLastX, dy = e.getY() - mLastY;
                    int steps = (int) Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)) / 4f);
                    for(int i = 1; i <= steps; i++) apply(mLastX + dx * i / steps, mLastY + dy * i / steps);
                    mLastX = e.getX(); mLastY = e.getY();
                } else if(mStroke && mTool == PICKER) {
                    apply(e.getX(), e.getY());
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP: {
                // Continue the move with the finger that stays down
                int up = e.getActionIndex();
                int keep = up == 0 ? 1 : 0;
                if(e.getPointerCount() > keep) { mLastX = e.getX(keep); mLastY = e.getY(keep); }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mStroke = false;
                mGesture = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                if(mListener != null) mListener.onHistoryChanged();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private static float avgX(MotionEvent e) {
        float s = 0; for(int i = 0; i < e.getPointerCount(); i++) s += e.getX(i); return s / e.getPointerCount();
    }

    private static float avgY(MotionEvent e) {
        float s = 0; for(int i = 0; i < e.getPointerCount(); i++) s += e.getY(i); return s / e.getPointerCount();
    }
}
