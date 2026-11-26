package git.artdeell.mojo.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

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

    public static boolean importFrom(Context c, Uri uri) {
        try(InputStream in = c.getContentResolver().openInputStream(uri)) {
            if(in == null) return false;
            Bitmap src = BitmapFactory.decodeStream(in);
            if(src == null || src.getWidth() <= 0 || src.getHeight() <= 0) return false;
            int h = HEIGHT, w = Math.max(1, Math.round(src.getWidth() * (HEIGHT / (float) src.getHeight())));
            if(w > 256) { w = 256; }
            Bitmap scaled = Bitmap.createScaledBitmap(src, w, h, false);
            try(FileOutputStream out = new FileOutputStream(file(c))) {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            return true;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return false;
        }
    }

    public static void reset(Context c) {
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
    }
}
