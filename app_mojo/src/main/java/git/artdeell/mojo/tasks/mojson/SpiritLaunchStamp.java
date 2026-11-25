package git.artdeell.mojo.tasks.mojson;

import androidx.annotation.Nullable;

import com.google.gson.Gson;

import git.artdeell.mojo.BuildConfig;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.Instances;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * "Fast game startup": after one complete, successful preparation of a version (metadata, libraries, assets, natives)
 * a small stamp is saved. The next launch trusts it and skips the whole check (thousands of asset files, hashes,
 * the json parsing) when nothing it depends on has changed:
 *  - the launcher build (any update re-checks everything), the selected runtime, the version json (size and time)
 *  - every classpath file still exists with the same size, and the natives folder still has the same file count
 *  - the stamp is younger than MAX_AGE_MS, so a full check still happens from time to time
 * Any doubt or error means "no stamp", which simply runs the normal full check.
 */
public final class SpiritLaunchStamp {
    private static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;

    private static final class Data {
        String build, runtime;
        long jsonSize, jsonTime, created;
        int nativesCount;
        List<String> paths = new ArrayList<>();
        List<Long> sizes = new ArrayList<>();
    }

    private SpiritLaunchStamp() {}

    private static File stampFile(String versionName) {
        return new File(new File(Tools.DIR_CACHE, "spirit_launch"), versionName.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
    }

    private static String runtimeName() {
        try { return String.valueOf(Tools.getSelectedRuntime(Instances.loadSelectedInstance())); }
        catch (RuntimeException e) { return "?"; }
    }

    private static int nativesCount(String versionName) {
        String[] list = new File(Tools.DIR_CACHE, "natives/" + versionName).list();
        return list == null ? 0 : list.length;
    }

    /** @return the saved classpath if everything is still as it was, otherwise null */
    @Nullable
    public static LinkedHashSet<File> tryLoad(String versionName) {
        try {
            File stamp = stampFile(versionName);
            File json = MoJsonDownloader.createGameJsonPath(versionName);
            if(!stamp.isFile() || !json.isFile()) return null;
            Data d;
            try(FileReader r = new FileReader(stamp)) { d = new Gson().fromJson(r, Data.class); }
            if(d == null || d.paths == null || d.sizes == null || d.paths.isEmpty() || d.paths.size() != d.sizes.size()) return null;
            if(System.currentTimeMillis() - d.created > MAX_AGE_MS || d.created > System.currentTimeMillis() + 60_000L) return null;
            if(!BuildConfig.VERSION_NAME.equals(d.build) || !runtimeName().equals(d.runtime)) return null;
            if(json.length() != d.jsonSize || json.lastModified() != d.jsonTime) return null;
            if(nativesCount(versionName) != d.nativesCount) return null;
            LinkedHashSet<File> cp = new LinkedHashSet<>();
            for(int i = 0; i < d.paths.size(); i++) {
                File f = new File(d.paths.get(i));
                if(!f.isFile() || f.length() != d.sizes.get(i)) return null;
                cp.add(f);
            }
            return cp;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Call only after a fully successful preparation. Failures are ignored. */
    public static void save(String versionName, LinkedHashSet<File> classPath) {
        try {
            File json = MoJsonDownloader.createGameJsonPath(versionName);
            if(!json.isFile() || classPath == null || classPath.isEmpty()) return;
            Data d = new Data();
            d.build = BuildConfig.VERSION_NAME;
            d.runtime = runtimeName();
            d.jsonSize = json.length();
            d.jsonTime = json.lastModified();
            d.created = System.currentTimeMillis();
            d.nativesCount = nativesCount(versionName);
            for(File f : classPath) {
                if(!f.isFile()) return; // something is not on disk: do not trust this run
                d.paths.add(f.getAbsolutePath());
                d.sizes.add(f.length());
            }
            File stamp = stampFile(versionName);
            //noinspection ResultOfMethodCallIgnored
            stamp.getParentFile().mkdirs();
            try(FileWriter w = new FileWriter(stamp)) { new Gson().toJson(d, w); }
        } catch (IOException | RuntimeException ignored) {
            // no stamp just means a normal check next time
        }
    }

    public static void forget(String versionName) {
        //noinspection ResultOfMethodCallIgnored
        stampFile(versionName).delete();
    }
}
