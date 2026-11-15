package git.artdeell.mojo.game;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple Voice Chat keeps its client options in config/voicechat/voicechat-client.properties and ships with the
 * denoiser off. This switches it on before the game starts. It only touches an existing config file
 * (the mod creates it on its first run), so instances without the mod are left alone.
 */
public final class VoiceChatConfig {
    private VoiceChatConfig() {}

    public static void applyDenoiser(File gameDir, boolean enabled) {
        try {
            File file = new File(gameDir, "config/voicechat/voicechat-client.properties");
            if(!file.isFile()) return;
            List<String> lines = new ArrayList<>();
            try(InputStream in = new FileInputStream(file)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int r;
                while((r = in.read(buf)) != -1) out.write(buf, 0, r);
                for(String l : new String(out.toByteArray(), StandardCharsets.UTF_8).split("\r?\n")) lines.add(l);
            }
            boolean changed = false, found = false;
            String want = "denoiser=" + enabled;
            for(int i = 0; i < lines.size(); i++) {
                String l = lines.get(i).trim();
                if(l.startsWith("denoiser=") || l.startsWith("denoiser ")) {
                    found = true;
                    if(!l.replace(" ", "").equals(want)) { lines.set(i, want); changed = true; }
                }
            }
            if(!found) { lines.add(want); changed = true; }
            if(!changed) return;
            StringBuilder sb = new StringBuilder();
            for(String l : lines) sb.append(l).append('\n');
            try(FileOutputStream out = new FileOutputStream(file)) {
                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException ignored) {
            // A config we cannot edit must never stop the game from starting
        }
    }
}
