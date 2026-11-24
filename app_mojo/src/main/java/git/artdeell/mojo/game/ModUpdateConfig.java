package git.artdeell.mojo.game;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import git.artdeell.mojo.Tools;

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
        silenceModMenu(gameDir);
        silenceWalksyLib(gameDir);
    }

    /**
     * The "X is out / Open the WalksyLib panel to install it" toast is WalksyLib's own update checker. Its switch is
     * categories.Appearance.groups.Notifications.options."Notify For New Updates" in walksylib.json. That file lives in
     * user.home/.config/walksylib/ (shared) and is seeded from config/WalksyLib/, so both get the value.
     */
    private static void silenceWalksyLib(File gameDir) {
        try {
            File[] jars = new File(gameDir, "mods").listFiles((d, n) -> n.toLowerCase(Locale.ROOT).contains("walksylib") && n.endsWith(".jar"));
            boolean has = jars != null && jars.length > 0;
            File local = new File(new File(gameDir, "config/WalksyLib"), "walksylib.json");
            File home = new File(new File(Tools.DIR_GAME_HOME, ".config/walksylib"), "walksylib.json");
            if(!has && !local.isFile() && !home.isFile()) return;
            writeWalksyOff(local);
            writeWalksyOff(home);
        } catch (RuntimeException ignored) {
            // never stop the game because of a popup setting
        }
    }

    private static void writeWalksyOff(File f) {
        try {
            JsonObject root = new JsonObject();
            if(f.isFile()) {
                try(FileReader r = new FileReader(f)) {
                    JsonObject parsed = new Gson().fromJson(r, JsonObject.class);
                    if(parsed != null) root = parsed;
                } catch (RuntimeException e) {
                    return;
                }
            }
            JsonObject cats = child(root, "categories");
            JsonObject app = child(cats, "Appearance");
            JsonObject groups = child(app, "groups");
            JsonObject notif = child(groups, "Notifications");
            JsonObject opts = child(notif, "options");
            if(opts.has("Notify For New Updates") && !opts.get("Notify For New Updates").getAsBoolean()) return;
            opts.addProperty("Notify For New Updates", false);
            if(!notif.has("expanded")) notif.addProperty("expanded", true);
            if(!root.has("format")) root.addProperty("format", 2);
            File dir = f.getParentFile();
            if(dir != null) //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            try(FileWriter w = new FileWriter(f)) {
                w.write(new Gson().toJson(root));
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static JsonObject child(JsonObject parent, String key) {
        if(parent.has(key) && parent.get(key).isJsonObject()) return parent.getAsJsonObject(key);
        JsonObject o = new JsonObject();
        parent.add(key, o);
        return o;
    }

    private static void silenceModMenu(File gameDir) {
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
