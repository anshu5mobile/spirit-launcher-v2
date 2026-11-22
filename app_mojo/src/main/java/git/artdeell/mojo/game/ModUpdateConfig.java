package git.artdeell.mojo.game;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

/**
 * Best effort: the "X is out / You have Y" popup in the corner comes from the mods' own update checker (Mod Menu style).
 * Mod Menu keeps its user options in config/modmenu.json; this sets "update_checker": false there before the game starts.
 * Only done when Mod Menu is installed, and any problem is ignored: the game must never fail to start because of it.
 */
public final class ModUpdateConfig {
    private ModUpdateConfig() {}

    public static void silenceUpdatePopups(File gameDir) {
        try {
            File mods = new File(gameDir, "mods");
            File[] jars = mods.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).contains("modmenu") && n.endsWith(".jar"));
            File cfgDir = new File(gameDir, "config");
            File cfg = new File(cfgDir, "modmenu.json");
            if((jars == null || jars.length == 0) && !cfg.isFile()) return;

            JsonObject obj = new JsonObject();
            if(cfg.isFile()) {
                try(FileReader r = new FileReader(cfg)) {
                    JsonObject parsed = new Gson().fromJson(r, JsonObject.class);
                    if(parsed != null) obj = parsed;
                } catch (RuntimeException e) {
                    return; // not valid json: leave the user's file alone
                }
            }
            if(obj.has("update_checker") && !obj.get("update_checker").getAsBoolean()) return; // already off
            obj.addProperty("update_checker", false);
            //noinspection ResultOfMethodCallIgnored
            cfgDir.mkdirs();
            try(FileWriter w = new FileWriter(cfg)) {
                w.write(new Gson().toJson(obj));
            }
        } catch (IOException | RuntimeException ignored) {
            // never stop the game because of a popup setting
        }
    }
}
