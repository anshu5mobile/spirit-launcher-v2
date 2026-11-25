package git.artdeell.mojo.crash;

import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import git.artdeell.mojo.R;
import git.artdeell.mojo.browse.BrowseInstaller;
import git.artdeell.mojo.browse.BrowseKind;
import git.artdeell.mojo.browse.BrowseTarget;
import git.artdeell.mojo.browse.ModrinthClient;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.modding.LoaderSwitcher;
import git.artdeell.mojo.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Applies the fixes the {@link CrashAnalyzer} suggests. Everything here blocks (network, disk): run it off the
 * main thread. Both the crash screen and the in-game "incompatible mods" dialog use it.
 */
public final class CrashFixer {
    private CrashFixer() {}

    /** True for fixes that are safe to run without asking, used by "Auto fix everything" */
    public static boolean isAutomatic(CrashAnalyzer.Finding f, boolean hasInstallOrUpdateFix) {
        switch(f.fix) {
            case MORE_RAM:
            case UPDATE_LOADER:
            case INSTALL_MOD:
            case UPDATE_MODS:
                return true;
            case DISABLE_MOD:
                // Turning a mod off is the last resort: when it can be installed or updated instead, do that
                return !hasInstallOrUpdateFix;
            default:
                return false;
        }
    }

    /** @return a short message saying what was done */
    @NonNull
    public static String apply(@NonNull Context ctx, @NonNull CrashAnalyzer.Finding f, @Nullable BrowseTarget target) throws IOException {
        switch(f.fix) {
            case MORE_RAM:
                return moreRam(ctx);
            case UPDATE_LOADER: {
                Instance instance = Instances.loadSelectedInstance();
                if(instance == null) throw new IOException("No instance selected");
                String id = LoaderSwitcher.installNewerThan(instance.versionId, f.arg);
                instance.versionId = id;
                instance.write();
                return ctx.getString(R.string.spirit_crash_loader_done, id);
            }
            case INSTALL_MOD:
                if(target == null) throw new IOException("No instance selected");
                return ctx.getString(R.string.spirit_crash_installed, installMod(target, f.arg));
            case UPDATE_MODS: {
                if(target == null) throw new IOException("No instance selected");
                List<String> updated = BrowseInstaller.updateAllMods(target);
                return updated.isEmpty() ? ctx.getString(R.string.spirit_crash_updated_none)
                        : ctx.getString(R.string.spirit_crash_updated, updated.size());
            }
            case DISABLE_MOD:
                if(target == null) throw new IOException("No instance selected");
                return ctx.getString(R.string.spirit_crash_mod_done, disable(target, f.arg));
            case DISABLE_ALL_MODS:
                if(target == null) throw new IOException("No instance selected");
                return ctx.getString(R.string.spirit_crash_mod_done, disable(target, null));
            default:
                return "";
        }
    }

    private static String moreRam(Context ctx) throws IOException {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        int totalMb = (int) (mi.totalMem / (1024 * 1024));
        int cap = (int) (totalMb * 0.65f);
        int current = LauncherPreferences.DEFAULT_PREF.getInt("allocation", 1024);
        int next = Math.min(cap, current + 1024);
        if(next <= current) throw new IOException(ctx.getString(R.string.spirit_crash_ram_max));
        LauncherPreferences.DEFAULT_PREF.edit().putInt("allocation", next).apply();
        return ctx.getString(R.string.spirit_crash_ram_done, next);
    }

    /**
     * Installs the newest version of a mod that runs on this instance. The mod id is tried as the Modrinth
     * project name first, then as a search.
     * @return the file name that was installed
     */
    public static String installMod(BrowseTarget target, String modId) throws IOException {
        List<ModrinthClient.Version> versions = null;
        try {
            versions = ModrinthClient.versions(modId, target.mcVersion, target.loaders);
        } catch (IOException ignored) { /* the id is not a project name, search below */ }
        if(versions == null || versions.isEmpty()) {
            List<ModrinthClient.Project> found = ModrinthClient.search("mod", modId, target.mcVersion, target.loaders, "relevance", 0);
            for(ModrinthClient.Project p : found) {
                if(p.slug != null && CrashAnalyzer.squash(p.slug).equals(CrashAnalyzer.squash(modId))
                        || p.title != null && CrashAnalyzer.squash(p.title).equals(CrashAnalyzer.squash(modId))) {
                    versions = ModrinthClient.versions(p.id, target.mcVersion, target.loaders);
                    break;
                }
            }
        }
        if(versions == null || versions.isEmpty()) {
            throw new IOException("\"" + modId + "\" was not found on Modrinth for " + target.mcVersion);
        }
        BrowseInstaller.install(target, BrowseKind.MODS, versions.get(0));
        ModrinthClient.FileInfo file = versions.get(0).primaryFile();
        return file != null && file.name != null ? file.name : modId;
    }

    /** Renames mod jars to .jar.disabled; modId null means all of them. */
    private static int disable(BrowseTarget target, @Nullable String modId) {
        int renamed = 0;
        File[] jars = new File(target.gameDir, "mods").listFiles((d, n) -> n.endsWith(".jar"));
        String key = modId == null ? "" : CrashAnalyzer.squash(modId);
        if(jars != null && (modId == null || !key.isEmpty())) {
            for(File jar : jars) {
                if((modId == null || CrashAnalyzer.squash(jar.getName()).contains(key) || jarHasModId(jar, modId))
                        && jar.renameTo(new File(jar.getParentFile(), jar.getName() + ".disabled"))) renamed++;
            }
        }
        return renamed;
    }

    /** True when the jar's fabric.mod.json says this is the mod with that id (the file name may look different). */
    private static boolean jarHasModId(File jar, String modId) {
        try(java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar)) {
            java.util.zip.ZipEntry e = zip.getEntry("fabric.mod.json");
            if(e == null || e.getSize() > 512 * 1024) return false;
            try(java.io.InputStream in = zip.getInputStream(e)) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(bos.toString("UTF-8"));
                return m.find() && m.group(1).equals(modId);
            }
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }
}
