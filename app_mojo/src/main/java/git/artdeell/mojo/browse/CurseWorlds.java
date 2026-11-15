package git.artdeell.mojo.browse;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.modding.modpacks.api.ApiHandler;
import git.artdeell.mojo.progresskeeper.DownloaderProgressWrapper;
import git.artdeell.mojo.utils.DownloadUtils;
import git.artdeell.mojo.utils.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Worlds come from CurseForge, which needs an API key. Without one the Worlds tab says so instead of failing.
 * Reuses {@link ModrinthClient}'s model classes so the screen treats both sources alike.
 */
public final class CurseWorlds {
    private static final int GAME_ID_MINECRAFT = 432;
    private static final int CLASS_ID_WORLDS = 17;

    private final ApiHandler api;

    public CurseWorlds(String apiKey) {
        api = new ApiHandler("https://api.curseforge.com/v1", apiKey);
    }

    public static boolean isAvailable(@Nullable String apiKey) {
        return apiKey != null && !apiKey.trim().isEmpty() && !"DUMMY".equals(apiKey);
    }

    /** @param sort "relevance", "downloads" or "updated" */
    public List<ModrinthClient.Project> search(String query, @Nullable String mcVersion, String sort, int offset) throws IOException {
        HashMap<String, Object> params = new HashMap<>();
        params.put("gameId", GAME_ID_MINECRAFT);
        params.put("classId", CLASS_ID_WORLDS);
        params.put("searchFilter", query == null ? "" : query);
        params.put("sortField", "downloads".equals(sort) ? 6 : "updated".equals(sort) ? 3 : 1);
        params.put("sortOrder", "desc");
        params.put("pageSize", ModrinthClient.PAGE_SIZE);
        params.put("index", offset);
        if(mcVersion != null && !mcVersion.isEmpty()) params.put("gameVersion", mcVersion);
        JsonObject response = api.get("mods/search", params, JsonObject.class);
        if(response == null) throw new IOException("CurseForge did not answer");
        JsonArray data = response.getAsJsonArray("data");
        List<ModrinthClient.Project> list = new ArrayList<>();
        if(data == null) return list;
        for(JsonElement e : data) {
            if(!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            // Some authors forbid distribution outside CurseForge, those cannot be downloaded here
            JsonElement allow = o.get("allowModDistribution");
            if(allow != null && !allow.isJsonNull() && !allow.getAsBoolean()) continue;
            ModrinthClient.Project p = new ModrinthClient.Project();
            p.id = string(o, "id");
            if(p.id == null) continue;
            p.title = string(o, "name");
            p.description = string(o, "summary");
            p.slug = string(o, "slug");
            JsonObject logo = o.has("logo") && o.get("logo").isJsonObject() ? o.getAsJsonObject("logo") : null;
            p.iconUrl = logo != null ? string(logo, "thumbnailUrl") : null;
            JsonArray authors = o.getAsJsonArray("authors");
            if(authors != null && authors.size() > 0 && authors.get(0).isJsonObject()) p.author = string(authors.get(0).getAsJsonObject(), "name");
            p.downloads = o.has("downloadCount") && o.get("downloadCount").isJsonPrimitive() ? (int) Math.min(Integer.MAX_VALUE, o.get("downloadCount").getAsLong()) : 0;
            if(p.title == null) p.title = p.id;
            list.add(p);
        }
        return list;
    }

    public List<ModrinthClient.Version> versions(String projectId, @Nullable String mcVersion) throws IOException {
        HashMap<String, Object> params = new HashMap<>();
        params.put("pageSize", 30);
        if(mcVersion != null && !mcVersion.isEmpty()) params.put("gameVersion", mcVersion);
        JsonObject response = api.get("mods/" + projectId + "/files", params, JsonObject.class);
        if(response == null) throw new IOException("CurseForge did not answer");
        JsonArray data = response.getAsJsonArray("data");
        List<ModrinthClient.Version> list = new ArrayList<>();
        if(data == null) return list;
        for(JsonElement e : data) {
            if(!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            ModrinthClient.Version v = new ModrinthClient.Version();
            v.id = string(o, "id");
            String fileName = string(o, "fileName");
            if(v.id == null || fileName == null) continue;
            v.projectId = projectId;
            v.name = string(o, "displayName");
            v.number = v.name;
            JsonArray gv = o.getAsJsonArray("gameVersions");
            if(gv != null) for(JsonElement g : gv) if(g.isJsonPrimitive()) v.gameVersions.add(g.getAsString());
            ModrinthClient.FileInfo f = new ModrinthClient.FileInfo();
            f.name = fileName;
            f.primary = true;
            f.size = o.has("fileLength") && o.get("fileLength").isJsonPrimitive() ? o.get("fileLength").getAsLong() : 0;
            String url = string(o, "downloadUrl");
            if(url == null) {
                // Some files hide their link; the CDN path is built from the file id
                long id = Long.parseLong(v.id);
                url = "https://edge.forgecdn.net/files/" + (id / 1000) + "/" + (id % 1000) + "/" + fileName;
            }
            f.url = url;
            JsonArray hashes = o.getAsJsonArray("hashes");
            if(hashes != null) {
                for(JsonElement h : hashes) {
                    if(!h.isJsonObject()) continue;
                    JsonObject ho = h.getAsJsonObject();
                    if(ho.has("algo") && ho.get("algo").getAsInt() == 1) f.sha1 = string(ho, "value");
                }
            }
            v.files.add(f);
            list.add(v);
        }
        return list;
    }

    /** Downloads the world zip and unpacks it into the instance's saves folder. */
    public static String installWorld(BrowseTarget target, ModrinthClient.FileInfo file) throws IOException {
        File zip = new File(Tools.DIR_CACHE, "world_" + System.nanoTime() + ".zip");
        DownloaderProgressWrapper wrapper = new DownloaderProgressWrapper(
                R.string.mcl_launch_downloading_progress, ProgressLayout.INSTALL_MODPACK);
        wrapper.extraString = file.name;
        try {
            FileUtils.ensureParentDirectory(zip);
            DownloadUtils.ensureSha1(zip, file.sha1, () ->
                    DownloadUtils.downloadFileMonitored(file.url, zip, new byte[16384], wrapper));
            File saves = new File(target.gameDir, BrowseKind.WORLDS.folder);
            FileUtils.ensureDirectory(saves);
            return unpack(zip, saves, file.name);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            zip.delete();
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    /** Puts the world in saves/. A zip with one top folder keeps it, otherwise a folder named after the zip is made. */
    private static String unpack(File zipFile, File saves, String zipName) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Set<String> roots = new HashSet<>();
            Enumeration<? extends ZipEntry> en = zip.entries();
            while(en.hasMoreElements()) {
                String name = en.nextElement().getName().replace('\\', '/');
                int slash = name.indexOf('/');
                roots.add(slash < 0 ? name : name.substring(0, slash));
            }
            String base = zipName.replaceAll("(?i)\\.zip$", "");
            boolean singleRoot = roots.size() == 1 && !roots.iterator().next().contains(".");
            String worldName = singleRoot ? roots.iterator().next() : base;
            File worldDir = new File(saves, worldName);
            if(worldDir.exists()) {
                int n = 2;
                while(new File(saves, worldName + " (" + n + ")").exists()) n++;
                worldName = worldName + " (" + n + ")";
                worldDir = new File(saves, worldName);
            }
            String canonicalRoot = worldDir.getCanonicalPath() + File.separator;
            en = zip.entries();
            while(en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                String name = entry.getName().replace('\\', '/');
                if(singleRoot) name = name.substring(name.indexOf('/') + 1);
                if(name.isEmpty()) continue;
                File out = new File(worldDir, name);
                // Zip-slip guard: nothing may land outside the world folder
                if(!(out.getCanonicalPath() + File.separator).startsWith(canonicalRoot)
                        && !out.getCanonicalPath().equals(worldDir.getCanonicalPath())) {
                    throw new IOException("Bad path in world zip: " + entry.getName());
                }
                if(entry.isDirectory()) {
                    FileUtils.ensureDirectory(out);
                    continue;
                }
                FileUtils.ensureParentDirectory(out);
                try (InputStream in = zip.getInputStream(entry); FileOutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while((n = in.read(buf)) != -1) os.write(buf, 0, n);
                }
            }
            return worldName;
        }
    }

    @Nullable
    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
