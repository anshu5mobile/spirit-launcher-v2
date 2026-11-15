package git.artdeell.mojo.browse;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;
import git.artdeell.mojo.progresskeeper.DownloaderProgressWrapper;
import git.artdeell.mojo.utils.DownloadUtils;
import git.artdeell.mojo.utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Downloads, verifies and manages the files Browse puts into an instance. Blocking: call off the main thread. */
public final class BrowseInstaller {
    private static final int MAX_DEPENDENCY_DEPTH = 4;
    private static final String DISABLED_SUFFIX = ".disabled";
    private static final String BACKUP_DIR = ".spirit_backup";

    private BrowseInstaller() {}

    /** What an install did, to tell the user */
    public static final class Result {
        public final List<String> added = new ArrayList<>();
        public final List<String> alreadyThere = new ArrayList<>();
        public final List<String> skippedDependencies = new ArrayList<>();
    }

    public static File folderFor(BrowseTarget target, BrowseKind kind) {
        return new File(target.gameDir, kind.folder);
    }

    // ---------------------------------------------------------------- install

    /** Installs the version plus every required dependency that can be resolved for this instance. */
    public static Result install(BrowseTarget target, BrowseKind kind, ModrinthClient.Version version) throws IOException {
        Result result = new Result();
        Set<String> seenProjects = new HashSet<>();
        if(version.projectId != null) seenProjects.add(version.projectId);
        try {
            installOne(target, kind, version, result, seenProjects, 0);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
        return result;
    }

    private static void installOne(BrowseTarget target, BrowseKind kind, ModrinthClient.Version version,
                                   Result result, Set<String> seenProjects, int depth) throws IOException {
        ModrinthClient.FileInfo file = version.primaryFile();
        if(file == null) throw new IOException("This version has no downloadable file");
        File destination = new File(folderFor(target, kind), safeName(file.name));
        File disabled = new File(destination.getParentFile(), destination.getName() + DISABLED_SUFFIX);

        if(destination.exists() || disabled.exists()) {
            result.alreadyThere.add(file.name);
        } else {
            download(file, destination);
            result.added.add(file.name);
        }

        if(depth >= MAX_DEPENDENCY_DEPTH) return;
        for(ModrinthClient.Dependency dep : version.dependencies) {
            if(!"required".equals(dep.type)) continue;
            if(dep.projectId == null && dep.versionId == null) continue;
            if(dep.projectId != null && !seenProjects.add(dep.projectId)) continue;
            try {
                ModrinthClient.Version depVersion = null;
                if(dep.versionId != null) {
                    depVersion = ModrinthClient.version(dep.versionId);
                } else {
                    List<ModrinthClient.Version> options = ModrinthClient.versions(dep.projectId, target.mcVersion,
                            kind == BrowseKind.MODS ? target.loaders : null);
                    if(!options.isEmpty()) depVersion = options.get(0);
                }
                if(depVersion == null) {
                    result.skippedDependencies.add(dep.projectId != null ? dep.projectId : dep.versionId);
                    continue;
                }
                installOne(target, kind, depVersion, result, seenProjects, depth + 1);
            } catch (IOException e) {
                // One missing dependency must not undo the main install, the user is told instead
                result.skippedDependencies.add(dep.projectId != null ? dep.projectId : dep.versionId);
            }
        }
    }

    private static void download(ModrinthClient.FileInfo file, File destination) throws IOException {
        FileUtils.ensureParentDirectory(destination);
        DownloaderProgressWrapper wrapper = new DownloaderProgressWrapper(
                R.string.mcl_launch_downloading_progress, ProgressLayout.INSTALL_MODPACK);
        wrapper.extraString = file.name;
        byte[] buffer = new byte[16384];
        try {
            DownloadUtils.ensureSha1(destination, file.sha1, () ->
                    DownloadUtils.downloadFileMonitored(file.url, destination, buffer, wrapper));
        } catch (IOException e) {
            // Never leave a half downloaded file behind: the game would try to load it
            //noinspection ResultOfMethodCallIgnored
            destination.delete();
            throw e;
        }
    }

    /** Strips any path from a file name coming from the network. */
    private static String safeName(String name) {
        String n = name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        if(n.isEmpty() || n.equals("..") || n.equals(".")) return "download.bin";
        return n;
    }

    // ---------------------------------------------------------------- installed list

    public static final class Installed {
        public final File file;
        public final boolean enabled;
        public final String displayName;

        Installed(File file, boolean enabled, String displayName) {
            this.file = file;
            this.enabled = enabled;
            this.displayName = displayName;
        }
    }

    public static List<Installed> listInstalled(BrowseTarget target, BrowseKind kind) {
        File[] files = folderFor(target, kind).listFiles();
        List<Installed> list = new ArrayList<>();
        if(files == null) return list;
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for(File f : files) {
            String name = f.getName();
            if(name.startsWith(".")) continue;
            boolean enabled = !name.endsWith(DISABLED_SUFFIX);
            String shown = enabled ? name : name.substring(0, name.length() - DISABLED_SUFFIX.length());
            if(kind != BrowseKind.WORLDS && f.isDirectory()) continue;
            list.add(new Installed(f, enabled, shown));
        }
        return list;
    }

    /** Mods can be switched off without deleting them: the game only loads *.jar */
    public static boolean setEnabled(Installed item, boolean enable) {
        if(item.enabled == enable) return true;
        File file = item.file;
        File renamed = enable
                ? new File(file.getParentFile(), file.getName().substring(0, file.getName().length() - DISABLED_SUFFIX.length()))
                : new File(file.getParentFile(), file.getName() + DISABLED_SUFFIX);
        return file.renameTo(renamed);
    }

    public static void remove(Installed item) throws IOException {
        if(item.file.isDirectory()) {
            org.apache.commons.io.FileUtils.deleteDirectory(item.file);
        } else if(!item.file.delete()) {
            throw new IOException("Could not delete " + item.file.getName());
        }
    }

    // ---------------------------------------------------------------- update all

    /**
     * Looks for newer versions of every enabled mod and replaces the ones that have one.
     * The old file is moved into a backup folder inside the instance, so it can be put back.
     * @return names of the updated files
     */
    public static List<String> updateAllMods(BrowseTarget target) throws IOException {
        List<String> updated = new ArrayList<>();
        File modsDir = folderFor(target, BrowseKind.MODS);
        File[] files = modsDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if(files == null) return updated;
        try {
            int index = 0;
            for(File old : files) {
                index++;
                ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, index * 100 / files.length,
                        R.string.spirit_browse_checking_updates, old.getName());
                String hash = sha1(old);
                ModrinthClient.Version latest;
                try {
                    latest = ModrinthClient.latestForHash(hash, target.loaders, target.mcVersion);
                } catch (IOException e) {
                    continue; // one failed lookup should not stop the rest
                }
                if(latest == null) continue;
                ModrinthClient.FileInfo file = latest.primaryFile();
                if(file == null || (file.sha1 != null && file.sha1.equalsIgnoreCase(hash))) continue;
                File fresh = new File(modsDir, safeName(file.name));
                if(fresh.exists()) continue;
                download(file, fresh);
                File backupDir = new File(target.gameDir, BACKUP_DIR + "/" + BrowseKind.MODS.folder);
                FileUtils.ensureDirectory(backupDir);
                File backup = new File(backupDir, old.getName());
                if(backup.exists()) //noinspection ResultOfMethodCallIgnored
                    backup.delete();
                if(!old.renameTo(backup)) {
                    // Without a backup the old jar would load next to the new one, so drop the new file again
                    //noinspection ResultOfMethodCallIgnored
                    fresh.delete();
                    continue;
                }
                updated.add(file.name);
            }
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
        return updated;
    }

    private static String sha1(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[16384];
            int n;
            while((n = in.read(buf)) != -1) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for(byte b : md.digest()) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 missing", e);
        }
    }
}
