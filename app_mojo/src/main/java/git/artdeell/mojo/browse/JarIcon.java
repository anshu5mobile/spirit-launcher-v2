package git.artdeell.mojo.browse;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads the picture a mod ships inside its own jar (Fabric, Quilt, Forge/NeoForge, or pack.png). Never throws. */
public final class JarIcon {
    private JarIcon() {}

    private static final Pattern LOGO = Pattern.compile("logoFile\\s*=\\s*\"([^\"]+)\"");

    /** @return a small bitmap or null when the jar has no usable icon */
    public static Bitmap load(File jar, int maxPx) {
        try(ZipFile zip = new ZipFile(jar)) {
            String path = iconPath(zip);
            if(path == null) path = "pack.png";
            byte[] data = read(zip, path);
            if(data == null && !"pack.png".equals(path)) data = read(zip, "pack.png");
            if(data == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            int sample = 1;
            while(bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private static String iconPath(ZipFile zip) throws IOException {
        byte[] fabric = read(zip, "fabric.mod.json");
        if(fabric != null) {
            JsonObject o = parse(fabric);
            if(o != null && o.has("icon")) {
                JsonElement icon = o.get("icon");
                if(icon.isJsonPrimitive()) return icon.getAsString();
                if(icon.isJsonObject()) { // size -> path, take the biggest
                    String best = null;
                    int bestSize = -1;
                    for(Map.Entry<String, JsonElement> e : icon.getAsJsonObject().entrySet()) {
                        int size;
                        try { size = Integer.parseInt(e.getKey()); } catch (NumberFormatException ex) { size = 0; }
                        if(size > bestSize && e.getValue().isJsonPrimitive()) { bestSize = size; best = e.getValue().getAsString(); }
                    }
                    if(best != null) return best;
                }
            }
        }
        byte[] quilt = read(zip, "quilt.mod.json");
        if(quilt != null) {
            JsonObject o = parse(quilt);
            try {
                JsonElement icon = o.getAsJsonObject("quilt_loader").getAsJsonObject("metadata").get("icon");
                if(icon != null && icon.isJsonPrimitive()) return icon.getAsString();
            } catch (RuntimeException ignored) { /* no icon in quilt metadata */ }
        }
        for(String toml : new String[]{"META-INF/mods.toml", "META-INF/neoforge.mods.toml"}) {
            byte[] t = read(zip, toml);
            if(t == null) continue;
            Matcher m = LOGO.matcher(new String(t, StandardCharsets.UTF_8));
            if(m.find()) return m.group(1);
        }
        return null;
    }

    private static JsonObject parse(byte[] data) {
        try {
            JsonElement e = new com.google.gson.Gson().fromJson(new String(data, StandardCharsets.UTF_8), JsonElement.class);
            return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static byte[] read(ZipFile zip, String name) throws IOException {
        if(name.startsWith("/")) name = name.substring(1);
        ZipEntry entry = zip.getEntry(name);
        if(entry == null || entry.isDirectory() || entry.getSize() > 4 * 1024 * 1024) return null;
        try(InputStream in = zip.getInputStream(entry)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
