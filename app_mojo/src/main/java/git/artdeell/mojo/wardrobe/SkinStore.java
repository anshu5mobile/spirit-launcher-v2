package git.artdeell.mojo.wardrobe;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.google.gson.reflect.TypeToken;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.accounts.Account;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Skin library plus "the skin currently worn" per account, kept in the launcher's private folder.
 * Everything here touches the disk, so call it off the main thread.
 */
public final class SkinStore {
    private static final Object LOCK = new Object();
    private static final Type ENTRY_LIST = new TypeToken<List<Entry>>() {}.getType();

    private SkinStore() {}

    /** One skin saved in the library */
    @Keep
    public static final class Entry {
        public String id;
        public String name;
        /** Where it came from when that is a public link (needed by Auto Skin), else null */
        @Nullable public String url;
        public boolean slim;
        public long added;
    }

    @Keep
    private static final class ActiveMeta {
        String name;
        String url;
        boolean slim;
    }

    /** The skin an account wears right now, as far as the launcher knows */
    public static final class Active {
        public Bitmap bitmap;
        @Nullable public String name;
        @Nullable public String url;
        public boolean slim;
    }

    // ---------------------------------------------------------------- paths

    private static File root() {
        File dir = new File(Tools.DIR_DATA, "spirit_skins");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private static File activeDir() {
        File dir = new File(root(), "active");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private static File indexFile() {
        return new File(root(), "index.json");
    }

    private static File pngFor(String id) {
        return new File(root(), id + ".png");
    }

    /** File-name safe key of an account */
    public static String accountKey(Account account) {
        String id = account.isLocal() ? account.username : account.profileId;
        return (account.authType.name() + "_" + id).replaceAll("[^A-Za-z0-9_-]", "_");
    }

    // ---------------------------------------------------------------- library

    public static List<Entry> list() {
        synchronized (LOCK) {
            File index = indexFile();
            if(!index.isFile()) return new ArrayList<>();
            try {
                List<Entry> entries = Tools.GLOBAL_GSON.fromJson(readText(index), ENTRY_LIST);
                if(entries == null) return new ArrayList<>();
                // Drop entries whose picture is gone
                List<Entry> valid = new ArrayList<>(entries.size());
                for(Entry e : entries) {
                    if(e != null && e.id != null && pngFor(e.id).isFile()) valid.add(e);
                }
                return valid;
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }
    }

    public static Entry add(String name, Bitmap skin, @Nullable String url, boolean slim) throws IOException {
        synchronized (LOCK) {
            Entry entry = new Entry();
            entry.id = UUID.randomUUID().toString();
            entry.name = name;
            entry.url = url;
            entry.slim = slim;
            entry.added = System.currentTimeMillis();
            writePng(pngFor(entry.id), skin);
            List<Entry> entries = list();
            entries.add(0, entry);
            writeIndex(entries);
            return entry;
        }
    }

    public static void remove(String id) throws IOException {
        synchronized (LOCK) {
            List<Entry> entries = list();
            List<Entry> kept = new ArrayList<>(entries.size());
            for(Entry e : entries) if(!e.id.equals(id)) kept.add(e);
            writeIndex(kept);
            //noinspection ResultOfMethodCallIgnored
            pngFor(id).delete();
        }
    }

    @Nullable
    public static Bitmap load(Entry entry) {
        return BitmapFactory.decodeFile(pngFor(entry.id).getAbsolutePath());
    }

    private static void writeIndex(List<Entry> entries) throws IOException {
        try(Writer writer = new OutputStreamWriter(new FileOutputStream(indexFile()), StandardCharsets.UTF_8)) {
            writer.write(Tools.GLOBAL_GSON.toJson(entries, ENTRY_LIST));
        }
    }

    // ---------------------------------------------------------------- worn skin

    public static void saveActive(Account account, Bitmap skin, @Nullable String name, @Nullable String url, boolean slim) throws IOException {
        synchronized (LOCK) {
            String key = accountKey(account);
            writePng(new File(activeDir(), key + ".png"), skin);
            ActiveMeta meta = new ActiveMeta();
            meta.name = name;
            meta.url = url;
            meta.slim = slim;
            try(Writer writer = new OutputStreamWriter(new FileOutputStream(new File(activeDir(), key + ".json")), StandardCharsets.UTF_8)) {
                writer.write(Tools.GLOBAL_GSON.toJson(meta));
            }
        }
    }

    @Nullable
    public static Active loadActive(Account account) {
        synchronized (LOCK) {
            String key = accountKey(account);
            File png = new File(activeDir(), key + ".png");
            if(!png.isFile()) return null;
            Bitmap bitmap = BitmapFactory.decodeFile(png.getAbsolutePath());
            if(bitmap == null) return null;
            Active active = new Active();
            active.bitmap = bitmap;
            File json = new File(activeDir(), key + ".json");
            if(json.isFile()) {
                try {
                    ActiveMeta meta = Tools.GLOBAL_GSON.fromJson(readText(json), ActiveMeta.class);
                    if(meta != null) {
                        active.name = meta.name;
                        active.url = meta.url;
                        active.slim = meta.slim;
                    }
                } catch (Exception ignored) {
                    active.slim = detectSlim(bitmap);
                }
            } else {
                active.slim = detectSlim(bitmap);
            }
            return active;
        }
    }

    // ---------------------------------------------------------------- image rules

    /**
     * Checks that a picture is a skin (64x64 or the old 64x32) and returns it as a 64x64 ARGB bitmap.
     * @throws IOException with a message meant for the user when it is not a valid skin
     */
    public static Bitmap normalize(@Nullable Bitmap source) throws IOException {
        if(source == null) throw new IOException("This file is not a picture.");
        int w = source.getWidth(), h = source.getHeight();
        boolean modern = w == 64 && h == 64;
        boolean legacy = w == 64 && h == 32;
        if(!modern && !legacy) {
            throw new IOException("A skin must be 64x64 or 64x32 pixels, this one is " + w + "x" + h + ".");
        }
        Bitmap out = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[64 * h];
        source.getPixels(pixels, 0, 64, 0, 0, 64, h);
        out.setPixels(pixels, 0, 64, 0, 0, 64, h);
        if(legacy) upgradeLegacy(out);
        return out;
    }

    /** Fills the lower half of an old 64x32 skin the way the game does: left limbs are mirrored right limbs */
    private static void upgradeLegacy(Bitmap image) {
        copyRect(image, 4, 16, 16, 32, 4, 4);
        copyRect(image, 8, 16, 24, 32, 4, 4);
        copyRect(image, 0, 20, 24, 32, 4, 12);
        copyRect(image, 4, 20, 16, 32, 4, 12);
        copyRect(image, 8, 20, 8, 32, 4, 12);
        copyRect(image, 12, 20, 16, 32, 4, 12);
        copyRect(image, 44, 16, -8, 32, 4, 4);
        copyRect(image, 48, 16, -8, 32, 4, 4);
        copyRect(image, 40, 20, 0, 32, 4, 12);
        copyRect(image, 44, 20, -8, 32, 4, 12);
        copyRect(image, 48, 20, -16, 32, 4, 12);
        copyRect(image, 52, 20, -8, 32, 4, 12);
    }

    private static void copyRect(Bitmap image, int x, int y, int xOffset, int yOffset, int width, int height) {
        for(int dy = 0; dy < height; dy++) {
            for(int dx = 0; dx < width; dx++) {
                int mirrored = width - 1 - dx;
                image.setPixel(x + xOffset + mirrored, y + yOffset + dy, image.getPixel(x + dx, y + dy));
            }
        }
    }

    /** Slim (3 pixel) arms leave the last column of the arm region empty */
    public static boolean detectSlim(Bitmap skin) {
        if(skin.getWidth() < 64 || skin.getHeight() < 64) return false;
        for(int y = 20; y < 32; y++) {
            if(Color.alpha(skin.getPixel(55, y)) != 0) return false;
        }
        return true;
    }

    /** Decodes bytes as a picture after checking its size, so a huge image cannot eat the memory */
    public static Bitmap decodeBytes(byte[] data) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if(bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("This file is not a picture.");
        if(bounds.outWidth > 512 || bounds.outHeight > 512) {
            throw new IOException("A skin must be 64x64 or 64x32 pixels, this one is " + bounds.outWidth + "x" + bounds.outHeight + ".");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        return normalize(BitmapFactory.decodeByteArray(data, 0, data.length, options));
    }

    public static byte[] encodePng(Bitmap skin) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if(!skin.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("Could not encode the skin.");
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- io helpers

    private static void writePng(File file, Bitmap skin) throws IOException {
        try(FileOutputStream out = new FileOutputStream(file)) {
            if(!skin.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("Could not save the skin.");
        }
    }

    private static String readText(File file) throws IOException {
        try(InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
