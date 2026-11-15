package git.artdeell.mojo.browse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Small Modrinth v2 client (public API, no key). All calls block, so run them off the main thread.
 * Parsing is defensive: a missing field never throws, the entry is just left out.
 */
public final class ModrinthClient {
    private static final String BASE = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "SpiritLauncher/1.0 (Android)";
    public static final int PAGE_SIZE = 20;

    private ModrinthClient() {}

    public static final class Project {
        public String id, slug, title, description, iconUrl, author;
        public int downloads;
    }

    public static final class FileInfo {
        public String url, name, sha1;
        public long size;
        public boolean primary;
    }

    public static final class Dependency {
        @Nullable public String projectId, versionId;
        public String type;
    }

    public static final class Version {
        public String id, projectId, name, number;
        public List<String> gameVersions = new ArrayList<>();
        public List<String> loaders = new ArrayList<>();
        public List<FileInfo> files = new ArrayList<>();
        public List<Dependency> dependencies = new ArrayList<>();

        @Nullable
        public FileInfo primaryFile() {
            if(files.isEmpty()) return null;
            for(FileInfo f : files) if(f.primary) return f;
            return files.get(0);
        }

        /** Short label for a version picker, e.g. "1.2.3 (1.21.11)" */
        @NonNull
        public String label() {
            String base = number != null && !number.isEmpty() ? number : (name != null ? name : id);
            if(gameVersions.isEmpty()) return base;
            return base + " (" + gameVersions.get(gameVersions.size() - 1) + ")";
        }
    }

    // ---------------------------------------------------------------- queries

    /** @param sort one of "relevance", "downloads", "updated" */
    public static List<Project> search(String projectType, String query, @Nullable String mcVersion,
                                       @Nullable List<String> loaders, String sort, int offset) throws IOException {
        JsonArray facets = new JsonArray();
        facets.add(group("project_type:" + projectType));
        if(mcVersion != null && !mcVersion.isEmpty()) facets.add(group("versions:" + mcVersion));
        if(loaders != null && !loaders.isEmpty()) {
            JsonArray any = new JsonArray();
            for(String l : loaders) any.add("categories:" + l);
            facets.add(any);
        }
        String url = BASE + "/search?query=" + enc(query == null ? "" : query)
                + "&facets=" + enc(facets.toString())
                + "&index=" + enc(sort)
                + "&limit=" + PAGE_SIZE
                + "&offset=" + offset;
        JsonObject response = new JsonParser().parse(get(url)).getAsJsonObject();
        JsonArray hits = response.getAsJsonArray("hits");
        List<Project> result = new ArrayList<>();
        if(hits == null) return result;
        for(JsonElement e : hits) {
            if(!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            Project p = new Project();
            p.id = str(o, "project_id");
            if(p.id == null) continue;
            p.slug = str(o, "slug");
            p.title = str(o, "title");
            p.description = str(o, "description");
            p.iconUrl = str(o, "icon_url");
            p.author = str(o, "author");
            p.downloads = o.has("downloads") && o.get("downloads").isJsonPrimitive() ? o.get("downloads").getAsInt() : 0;
            if(p.title == null) p.title = p.slug != null ? p.slug : p.id;
            result.add(p);
        }
        return result;
    }

    /** Versions of a project that run on the given Minecraft version and loader, newest first. */
    public static List<Version> versions(String projectId, @Nullable String mcVersion,
                                         @Nullable List<String> loaders) throws IOException {
        StringBuilder url = new StringBuilder(BASE + "/project/" + enc(projectId) + "/version");
        char sep = '?';
        if(loaders != null && !loaders.isEmpty()) {
            JsonArray a = new JsonArray();
            for(String l : loaders) a.add(l);
            url.append(sep).append("loaders=").append(enc(a.toString()));
            sep = '&';
        }
        if(mcVersion != null && !mcVersion.isEmpty()) {
            JsonArray a = new JsonArray();
            a.add(mcVersion);
            url.append(sep).append("game_versions=").append(enc(a.toString()));
        }
        return parseVersions(new JsonParser().parse(get(url.toString())).getAsJsonArray());
    }

    @Nullable
    public static Version version(String versionId) throws IOException {
        JsonObject o = new JsonParser().parse(get(BASE + "/version/" + enc(versionId))).getAsJsonObject();
        return parseVersion(o);
    }

    /** Asks Modrinth for a newer version of the file with this SHA-1, or null when it is the newest or unknown. */
    @Nullable
    public static Version latestForHash(String sha1, @Nullable List<String> loaders, @Nullable String mcVersion) throws IOException {
        JsonObject body = new JsonObject();
        JsonArray l = new JsonArray();
        if(loaders != null) for(String s : loaders) l.add(s);
        JsonArray g = new JsonArray();
        if(mcVersion != null && !mcVersion.isEmpty()) g.add(mcVersion);
        body.add("loaders", l);
        body.add("game_versions", g);
        String raw = post(BASE + "/version_file/" + enc(sha1) + "/update?algorithm=sha1", body.toString());
        if(raw == null) return null;
        return parseVersion(new JsonParser().parse(raw).getAsJsonObject());
    }

    // ---------------------------------------------------------------- parsing

    private static List<Version> parseVersions(JsonArray array) {
        List<Version> out = new ArrayList<>();
        for(JsonElement e : array) {
            if(!e.isJsonObject()) continue;
            Version v = parseVersion(e.getAsJsonObject());
            if(v != null) out.add(v);
        }
        return out;
    }

    @Nullable
    private static Version parseVersion(JsonObject o) {
        Version v = new Version();
        v.id = str(o, "id");
        if(v.id == null) return null;
        v.projectId = str(o, "project_id");
        v.name = str(o, "name");
        v.number = str(o, "version_number");
        addStrings(o.getAsJsonArray("game_versions"), v.gameVersions);
        addStrings(o.getAsJsonArray("loaders"), v.loaders);
        JsonArray files = o.getAsJsonArray("files");
        if(files != null) {
            for(JsonElement fe : files) {
                if(!fe.isJsonObject()) continue;
                JsonObject fo = fe.getAsJsonObject();
                FileInfo f = new FileInfo();
                f.url = str(fo, "url");
                f.name = str(fo, "filename");
                if(f.url == null || f.name == null) continue;
                f.primary = fo.has("primary") && fo.get("primary").isJsonPrimitive() && fo.get("primary").getAsBoolean();
                f.size = fo.has("size") && fo.get("size").isJsonPrimitive() ? fo.get("size").getAsLong() : 0;
                JsonObject hashes = fo.getAsJsonObject("hashes");
                f.sha1 = hashes != null ? str(hashes, "sha1") : null;
                v.files.add(f);
            }
        }
        JsonArray deps = o.getAsJsonArray("dependencies");
        if(deps != null) {
            for(JsonElement de : deps) {
                if(!de.isJsonObject()) continue;
                JsonObject d = de.getAsJsonObject();
                Dependency dep = new Dependency();
                dep.projectId = str(d, "project_id");
                dep.versionId = str(d, "version_id");
                dep.type = str(d, "dependency_type");
                v.dependencies.add(dep);
            }
        }
        return v;
    }

    private static void addStrings(@Nullable JsonArray src, List<String> dst) {
        if(src == null) return;
        for(JsonElement e : src) if(e.isJsonPrimitive()) dst.add(e.getAsString());
    }

    @Nullable
    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static JsonArray group(String facet) {
        JsonArray a = new JsonArray();
        a.add(facet);
        return a;
    }

    // ---------------------------------------------------------------- http

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", USER_AGENT);
        c.setRequestProperty("Accept", "application/json");
        return c;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = open(url);
        try {
            int code = c.getResponseCode();
            if(code < 200 || code >= 300) throw new IOException("Modrinth answered HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                return read(in);
            }
        } finally {
            c.disconnect();
        }
    }

    /** @return the response body, or null for HTTP 404 (nothing found) */
    @Nullable
    private static String post(String url, String json) throws IOException {
        HttpURLConnection c = open(url);
        try {
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            try (OutputStream out = c.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            if(code == 404) return null;
            if(code < 200 || code >= 300) throw new IOException("Modrinth answered HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                return read(in);
            }
        } finally {
            c.disconnect();
        }
    }
}
