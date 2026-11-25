package git.artdeell.mojo.perf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Changes true/false settings inside a mod's json config ("section.key" paths). Only keys that already exist and are
 * booleans are touched, anything else in the file stays exactly as it was. A copy is kept as .spirit-backup.
 */
public final class ModConfigFile {
    private ModConfigFile() {}

    /** @return "key: old -> new" lines for what changed; empty when the file does not exist or nothing differed */
    public static List<String> applyBooleans(File file, Map<String, Boolean> values) {
        List<String> changes = new ArrayList<>();
        if(!file.isFile()) return changes;
        try {
            JsonObject root;
            try(InputStreamReader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                JsonElement e = new Gson().fromJson(r, JsonElement.class);
                if(e == null || !e.isJsonObject()) return changes;
                root = e.getAsJsonObject();
            }
            for(Map.Entry<String, Boolean> en : values.entrySet()) {
                String path = en.getKey();
                int dot = path.lastIndexOf('.');
                if(dot < 0) continue;
                JsonElement section = root.get(path.substring(0, dot));
                if(section == null || !section.isJsonObject()) continue;
                JsonObject obj = section.getAsJsonObject();
                String key = path.substring(dot + 1);
                JsonElement cur = obj.get(key);
                if(cur == null || !cur.isJsonPrimitive() || !cur.getAsJsonPrimitive().isBoolean()) continue;
                if(cur.getAsBoolean() != en.getValue()) {
                    obj.addProperty(key, en.getValue());
                    changes.add(path + ": " + cur.getAsBoolean() + " -> " + en.getValue());
                }
            }
            if(changes.isEmpty()) return changes;
            File backup = new File(file.getParentFile(), file.getName() + ".spirit-backup");
            if(!backup.exists()) java.nio.file.Files.copy(file.toPath(), backup.toPath());
            try(OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                w.write(new GsonBuilder().setPrettyPrinting().create().toJson(root));
            }
        } catch (IOException | RuntimeException e) {
            changes.clear(); // unreadable or odd file: leave the mod alone
        }
        return changes;
    }

    /** Puts the saved copy back. @return true when a copy existed */
    public static boolean restore(File file) {
        File backup = new File(file.getParentFile(), file.getName() + ".spirit-backup");
        if(!backup.isFile()) return false;
        try {
            java.nio.file.Files.copy(backup.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            //noinspection ResultOfMethodCallIgnored
            backup.delete();
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
