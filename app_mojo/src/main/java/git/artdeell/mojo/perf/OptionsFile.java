package git.artdeell.mojo.perf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Edits Minecraft's options.txt (key:value per line). Keys the file does not have are added, other lines stay as they are. */
public final class OptionsFile {
    private OptionsFile() {}

    /** @return the keys that were actually changed, as "key: old -> new" lines */
    public static List<String> apply(File file, Map<String, String> values) throws IOException {
        if(!file.isFile()) throw new IOException("options.txt does not exist yet");
        List<String> lines = new ArrayList<>();
        try(InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while((r = in.read(buf)) != -1) out.write(buf, 0, r);
            for(String l : new String(out.toByteArray(), StandardCharsets.UTF_8).split("\r?\n")) lines.add(l);
        }
        List<String> changes = new ArrayList<>();
        for(Map.Entry<String, String> e : values.entrySet()) {
            String key = e.getKey();
            boolean found = false;
            for(int i = 0; i < lines.size(); i++) {
                String l = lines.get(i);
                if(l.startsWith(key + ":")) {
                    found = true;
                    String old = l.substring(key.length() + 1);
                    if(!old.equals(e.getValue())) {
                        lines.set(i, key + ":" + e.getValue());
                        changes.add(key + ": " + old + " -> " + e.getValue());
                    }
                    break;
                }
            }
            // Only touch keys the game version already knows about: a new key could be a different type in another version
        }
        if(changes.isEmpty()) return changes;
        // Keep a copy so the player can go back
        File backup = new File(file.getParentFile(), "options.txt.spirit-backup");
        if(!backup.exists()) {
            try(InputStream in = new FileInputStream(file); FileOutputStream out = new FileOutputStream(backup)) {
                byte[] buf = new byte[8192];
                int r;
                while((r = in.read(buf)) != -1) out.write(buf, 0, r);
            }
        }
        StringBuilder sb = new StringBuilder();
        for(String l : lines) if(!l.isEmpty()) sb.append(l).append('\n');
        try(FileOutputStream out = new FileOutputStream(file)) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        return changes;
    }
}
