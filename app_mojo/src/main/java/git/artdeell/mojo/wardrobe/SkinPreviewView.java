package git.artdeell.mojo.wardrobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Rotatable 3D preview of a Minecraft skin, drawn with plain Canvas maths (no GL).
 * With an orthographic camera every box face turns into a parallelogram, so each face is just the skin
 * region drawn through an affine matrix. Faces are culled when they look away and sorted back to front.
 * Drag to rotate; it turns slowly on its own until the first touch.
 */
public class SkinPreviewView extends View {
    private static final float AUTO_SPIN_DEG_PER_SEC = 24f;

    private final Paint mPaint = new Paint();
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mMatrix = new Matrix();
    private final Rect mSrc = new Rect();
    private final RectF mDst = new RectF();
    private final float[] mSrcPts = new float[6];
    private final float[] mDstPts = new float[6];

    private Bitmap mSkin;
    private boolean mSlim;
    private List<Face> mFaces = new ArrayList<>();
    private String mPlaceholder = "";

    private float mYaw = -28f;
    private float mPitch = 12f;
    private boolean mAutoSpin = true;
    private float mLastX, mLastY;
    private long mLastFrame;

    private static final class Face implements Comparable<Face> {
        // Origin of the face and its two edges in model space (Minecraft pixel units), y up
        float ox, oy, oz, ux, uy, uz, vx, vy, vz;
        float nx, ny, nz;
        int sx, sy, sw, sh;
        // Filled in per frame
        float depth;
        float[] screen = new float[6];

        @Override
        public int compareTo(Face other) {
            return Float.compare(depth, other.depth);
        }
    }

    public SkinPreviewView(Context context) {
        this(context, null);
    }

    public SkinPreviewView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // Pixel art must stay crisp
        mPaint.setFilterBitmap(false);
        mPaint.setAntiAlias(false);
        mTextPaint.setColor(Color.parseColor("#9FAB96"));
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(14f * context.getResources().getDisplayMetrics().scaledDensity);
        // Drawn by the software renderer: skewed sub-rectangles of a bitmap are drawn the same on every GPU
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /** Text shown while there is no skin to draw */
    public void setPlaceholder(String text) {
        mPlaceholder = text == null ? "" : text;
        invalidate();
    }

    /** @param skin a 64x64 (or HD multiple) skin, already converted from the legacy 64x32 layout */
    public void setSkin(@Nullable Bitmap skin, boolean slim) {
        if(skin != null && skin.getWidth() == 64 && skin.getHeight() == 32) {
            // An old 64x32 skin has no lower half: expand it, otherwise legs and left arm would be missing
            try { skin = SkinStore.normalize(skin); } catch (java.io.IOException ignored) {}
        }
        mSkin = skin;
        mSlim = slim;
        buildModel();
        invalidate();
    }

    public void setSlim(boolean slim) {
        if(slim == mSlim) return;
        mSlim = slim;
        buildModel();
        invalidate();
    }

    public boolean isSlim() {
        return mSlim;
    }

    // ---------------------------------------------------------------- model

    private void buildModel() {
        List<Face> faces = new ArrayList<>();
        int armW = mSlim ? 3 : 4;
        // Layer 0 is the base skin, layer 1 the overlay (hat, jacket, sleeves, pants), slightly bigger
        for(int layer = 0; layer < 2; layer++) {
            float g = layer == 0 ? 0f : 0.5f;
            // head
            addBox(faces, -4, 24, -4, 8, 8, 8, layer == 0 ? 0 : 32, 0, g);
            // body
            addBox(faces, -4, 12, -2, 8, 12, 4, 16, layer == 0 ? 16 : 32, g);
            // right arm (on the viewer's left), left arm
            addBox(faces, -4 - armW, 12, -2, armW, 12, 4, 40, layer == 0 ? 16 : 32, g);
            addBox(faces, 4, 12, -2, armW, 12, 4, layer == 0 ? 32 : 48, 48, g);
            // right leg, left leg
            addBox(faces, -4, 0, -2, 4, 12, 4, 0, layer == 0 ? 16 : 32, g);
            addBox(faces, 0, 0, -2, 4, 12, 4, layer == 0 ? 16 : 0, 48, g);
        }
        mFaces = faces;
    }

    /**
     * Adds the six faces of a box. (x,y,z) is its minimum corner, (w,h,d) its size and (u,v) the skin origin
     * of the box, laid out the way Minecraft unfolds it. g inflates the box on every side.
     */
    private void addBox(List<Face> out, float x, float y, float z, int w, int h, int d, int u, int v, float g) {
        float x0 = x - g, x1 = x + w + g, y0 = y - g, y1 = y + h + g, z0 = z - g, z1 = z + d + g;
        // front (+z): u grows with +x, v grows downward
        out.add(face(x0, y1, z1, x1 - x0, 0, 0, 0, -(y1 - y0), 0, 0, 0, 1, u + d, v + d, w, h));
        // back (-z): u grows with -x
        out.add(face(x1, y1, z0, -(x1 - x0), 0, 0, 0, -(y1 - y0), 0, 0, 0, -1, u + d + w + d, v + d, w, h));
        // character's right side (-x): u grows toward the front (+z)
        out.add(face(x0, y1, z0, 0, 0, z1 - z0, 0, -(y1 - y0), 0, -1, 0, 0, u, v + d, d, h));
        // character's left side (+x): u grows toward the back (-z)
        out.add(face(x1, y1, z1, 0, 0, -(z1 - z0), 0, -(y1 - y0), 0, 1, 0, 0, u + d + w, v + d, d, h));
        // top (+y): v grows toward the front (+z)
        out.add(face(x0, y1, z0, x1 - x0, 0, 0, 0, 0, z1 - z0, 0, 1, 0, u + d, v, w, d));
        // bottom (-y): v grows toward the back (-z)
        out.add(face(x0, y0, z1, x1 - x0, 0, 0, 0, 0, -(z1 - z0), 0, -1, 0, u + d + w, v, w, d));
    }

    private static Face face(float ox, float oy, float oz, float ux, float uy, float uz,
                             float vx, float vy, float vz, float nx, float ny, float nz,
                             int sx, int sy, int sw, int sh) {
        Face f = new Face();
        f.ox = ox; f.oy = oy; f.oz = oz;
        f.ux = ux; f.uy = uy; f.uz = uz;
        f.vx = vx; f.vy = vy; f.vz = vz;
        f.nx = nx; f.ny = ny; f.nz = nz;
        f.sx = sx; f.sy = sy; f.sw = sw; f.sh = sh;
        return f;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float dt = mLastFrame == 0 ? 0 : (now - mLastFrame) / 1_000_000_000f;
        mLastFrame = now;
        if(dt > 0.1f) dt = 0.1f;

        if(mSkin == null || mSkin.isRecycled()) {
            canvas.drawText(mPlaceholder, getWidth() / 2f, getHeight() / 2f, mTextPaint);
            return;
        }

        if(mAutoSpin) {
            mYaw += AUTO_SPIN_DEG_PER_SEC * dt;
            if(mYaw > 360f) mYaw -= 360f;
        }

        float scaleTex = mSkin.getWidth() / 64f;
        // The model is 32 units tall and 16 wide; fit it with a little margin
        float unit = Math.min(getHeight() / 36f, getWidth() / 24f);
        float cx = getWidth() / 2f, cy = getHeight() / 2f + 16f * unit;

        double yaw = Math.toRadians(mYaw), pitch = Math.toRadians(mPitch);
        float cosY = (float) Math.cos(yaw), sinY = (float) Math.sin(yaw);
        float cosP = (float) Math.cos(pitch), sinP = (float) Math.sin(pitch);

        List<Face> visible = new ArrayList<>(mFaces.size());
        for(Face f : mFaces) {
            // Rotate the normal first, faces that look away are skipped
            float nz = rotZ(f.nx, f.ny, f.nz, cosY, sinY, cosP, sinP);
            if(nz <= 0.0001f) continue;
            project(f.ox, f.oy, f.oz, f.screen, 0, cosY, sinY, cosP, sinP, unit, cx, cy);
            project(f.ox + f.ux, f.oy + f.uy, f.oz + f.uz, f.screen, 2, cosY, sinY, cosP, sinP, unit, cx, cy);
            project(f.ox + f.vx, f.oy + f.vy, f.oz + f.vz, f.screen, 4, cosY, sinY, cosP, sinP, unit, cx, cy);
            f.depth = rotZ(f.ox + (f.ux + f.vx) / 2f, f.oy + (f.uy + f.vy) / 2f, f.oz + (f.uz + f.vz) / 2f,
                    cosY, sinY, cosP, sinP);
            visible.add(f);
        }
        Collections.sort(visible, DEPTH_ORDER);

        for(Face f : visible) {
            int sw = Math.max(1, Math.round(f.sw * scaleTex)), sh = Math.max(1, Math.round(f.sh * scaleTex));
            mSrc.set(Math.round(f.sx * scaleTex), Math.round(f.sy * scaleTex),
                    Math.round(f.sx * scaleTex) + sw, Math.round(f.sy * scaleTex) + sh);
            if(mSrc.right > mSkin.getWidth() || mSrc.bottom > mSkin.getHeight()) continue;
            mSrcPts[0] = 0;  mSrcPts[1] = 0;
            mSrcPts[2] = sw; mSrcPts[3] = 0;
            mSrcPts[4] = 0;  mSrcPts[5] = sh;
            System.arraycopy(f.screen, 0, mDstPts, 0, 6);
            if(!mMatrix.setPolyToPoly(mSrcPts, 0, mDstPts, 0, 3)) continue;
            mDst.set(0, 0, sw, sh);
            canvas.save();
            canvas.concat(mMatrix);
            canvas.drawBitmap(mSkin, mSrc, mDst, mPaint);
            canvas.restore();
        }

        // Keep turning while nobody is dragging
        if(mAutoSpin) postInvalidateOnAnimation();
    }

    private static final Comparator<Face> DEPTH_ORDER = (a, b) -> Float.compare(a.depth, b.depth);

    private static float rotZ(float x, float y, float z, float cosY, float sinY, float cosP, float sinP) {
        // yaw around the vertical axis, then pitch around the horizontal one
        float z1 = -x * sinY + z * cosY;
        return y * sinP + z1 * cosP;
    }

    private static void project(float x, float y, float z, float[] out, int at,
                                float cosY, float sinY, float cosP, float sinP,
                                float unit, float cx, float cy) {
        float x1 = x * cosY + z * sinY;
        float z1 = -x * sinY + z * cosY;
        float y2 = y * cosP - z1 * sinP;
        out[at] = cx + x1 * unit;
        out[at + 1] = cy - y2 * unit;
    }

    // ---------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mAutoSpin = false;
                mLastX = event.getX();
                mLastY = event.getY();
                // Inside a ScrollView: keep the drag for the model
                if(getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                mYaw += (event.getX() - mLastX) * 0.6f;
                mPitch = Math.max(-50f, Math.min(50f, mPitch + (event.getY() - mLastY) * 0.4f));
                mLastX = event.getX();
                mLastY = event.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mLastFrame = 0;
    }
}
