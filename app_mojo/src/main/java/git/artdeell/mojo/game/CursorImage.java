package git.artdeell.mojo.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** A player-chosen cursor image, stored as a small PNG in the launcher's private files. */
public final class CursorImage {
    private static final String NAME = "spirit_cursor.png";
    private static final int HEIGHT = 54; // same height as the built-in pointer

    private CursorImage() {}

    private static File file(Context c) { return new File(c.getFilesDir(), NAME); }

    /** @return the saved cursor bitmap, or null when none is set (or it cannot be read) */
    public static Bitmap load(Context c) {
        File f = file(c);
        if(!f.isFile()) return null;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /** Saves a cropped picture as the cursor. With removeBackground the flat colour around the picture becomes see-through. */
    public static boolean save(Context c, Bitmap src, boolean removeBackground) {
        try {
            if(src == null || src.getWidth() <= 0 || src.getHeight() <= 0) return false;
            Bitmap work = src.copy(Bitmap.Config.ARGB_8888, true);
            if(removeBackground) removeBackground(work);
            int h = HEIGHT, w = Math.max(1, Math.round(work.getWidth() * (HEIGHT / (float) work.getHeight())));
            if(w > 256) w = 256;
            Bitmap scaled = Bitmap.createScaledBitmap(work, w, h, true);
            try(FileOutputStream out = new FileOutputStream(file(c))) {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            return true;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return false;
        }
    }

    /**
     * Makes the plain background transparent: starting from every edge pixel that looks like one of the four corner
     * colours, it spreads inwards over similar colours. Pieces of the same colour inside the picture are not touched,
     * because the spreading cannot reach them. Pictures that already have see-through corners are left alone.
     */
    public static void removeBackground(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int[] corners = { px[0], px[w - 1], px[(h - 1) * w], px[h * w - 1] };
        for(int cc : corners) if((cc >>> 24) < 250) return;
        final int tol = 48 * 48 * 3 / 2;
        boolean[] gone = new boolean[w * h];
        int[] queue = new int[w * h];
        int head = 0, tail = 0;
        for(int x = 0; x < w; x++) {
            for(int y : new int[]{0, h - 1}) {
                int i = y * w + x;
                if(!gone[i] && like(px[i], corners, tol)) { gone[i] = true; queue[tail++] = i; }
            }
        }
        for(int y = 0; y < h; y++) {
            for(int x : new int[]{0, w - 1}) {
                int i = y * w + x;
                if(!gone[i] && like(px[i], corners, tol)) { gone[i] = true; queue[tail++] = i; }
            }
        }
        while(head < tail) {
            int i = queue[head++];
            int x = i % w, y = i / w;
            if(x > 0) tail = push(px, gone, queue, tail, i - 1, corners, tol);
            if(x < w - 1) tail = push(px, gone, queue, tail, i + 1, corners, tol);
            if(y > 0) tail = push(px, gone, queue, tail, i - w, corners, tol);
            if(y < h - 1) tail = push(px, gone, queue, tail, i + w, corners, tol);
        }
        for(int i = 0; i < px.length; i++) if(gone[i]) px[i] = 0;
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static int push(int[] px, boolean[] gone, int[] queue, int tail, int i, int[] corners, int tol) {
        if(!gone[i] && like(px[i], corners, tol)) { gone[i] = true; queue[tail++] = i; }
        return tail;
    }

    private static boolean like(int color, int[] corners, int tol) {
        for(int c : corners) {
            int dr = ((color >> 16) & 255) - ((c >> 16) & 255);
            int dg = ((color >> 8) & 255) - ((c >> 8) & 255);
            int db = (color & 255) - (c & 255);
            if(dr * dr + dg * dg + db * db <= tol) return true;
        }
        return false;
    }

    public static void reset(Context c) {
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
    }
}
