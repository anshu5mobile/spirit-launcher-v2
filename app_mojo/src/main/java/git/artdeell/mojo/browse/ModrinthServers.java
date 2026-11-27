package git.artdeell.mojo.browse;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Server listings of Modrinth (the "Servers" page of modrinth.com). They come from the v3 search with the filter
 * "project_types = minecraft_java_server". Every field is read defensively, a missing one just stays empty.
 * "Online" and language filters are applied here on the result page, because Modrinth's own filter field names for
 * them are not documented.
 */
public final class ModrinthServers {
    public static final int PAGE_SIZE = 20;
    private static final String USER_AGENT = "SpiritLauncher/1.0 (Android)";

    private ModrinthServers() {}

    public static final class Server {
        public String id, name, summary, iconUrl, address, country, version;
        public boolean online;
        public int players, max;
        public long plays;
        public final List<String> languages = new ArrayList<>();
    }

    public static final class Page {
        /** How many results Modrinth returned, before the local filters (this is what the next offset is based on) */
        public int raw;
        public final List<Server> servers = new ArrayList<>();
    }

    /** @param index "relevance", "minecraft_java_server.ping.data.players_online" or "newest" */
    public static Page search(String query, String index, int offset, boolean onlineOnly, @Nullable String language) throws IOException {
        String url = "https://api.modrinth.com/v3/search?query=" + enc(query)
                + "&limit=" + PAGE_SIZE + "&offset=" + offset
                + "&index=" + enc(index)
                + "&new_filters=" + enc("project_types = minecraft_java_server");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", USER_AGENT);
            int code = c.getResponseCode();
            if(code < 200 || code >= 300) throw new IOException("Modrinth answered HTTP " + code);
            String body;
            try(InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while((n = in.read(buf)) > 0) out.write(buf, 0, n);
                body = out.toString("UTF-8");
            }
            JsonObject root = new Gson().fromJson(body, JsonObject.class);
            Page page = new Page();
            JsonArray hits = root.has("hits") && root.get("hits").isJsonArray() ? root.getAsJsonArray("hits") : new JsonArray();
            page.raw = hits.size();
            for(JsonElement e : hits) {
                if(!e.isJsonObject()) continue;
                Server s = parse(e.getAsJsonObject());
                if(s == null) continue;
                if(onlineOnly && !s.online) continue;
                if(language != null && !s.languages.contains(language)) continue;
                page.servers.add(s);
            }
            return page;
        } catch (RuntimeException ex) {
            throw new IOException("Unexpected answer from Modrinth", ex);
        } finally {
            c.disconnect();
        }
    }

    @Nullable
    private static Server parse(JsonObject h) {
        JsonObject java = obj(h, "minecraft_java_server");
        String address = str(java, "address");
        if(address.isEmpty()) return null;
        Server s = new Server();
        s.id = str(h, "project_id");
        s.name = str(h, "name");
        s.summary = str(h, "summary");
        s.iconUrl = str(h, "icon_url");
        s.address = address;
        s.plays = java != null && java.has("verified_plays_2w") && java.get("verified_plays_2w").isJsonPrimitive()
                ? java.get("verified_plays_2w").getAsLong() : 0;
        JsonObject data = obj(obj(java, "ping"), "data");
        if(data != null) {
            s.online = true;
            s.players = data.has("players_online") && data.get("players_online").isJsonPrimitive() ? data.get("players_online").getAsInt() : 0;
            s.max = data.has("players_max") && data.get("players_max").isJsonPrimitive() ? data.get("players_max").getAsInt() : 0;
            s.version = str(data, "version_name");
        }
        JsonObject meta = obj(h, "minecraft_server");
        s.country = str(meta, "country");
        if(meta != null && meta.has("languages") && meta.get("languages").isJsonArray()) {
            for(JsonElement l : meta.getAsJsonArray("languages")) if(l.isJsonPrimitive()) s.languages.add(l.getAsString());
        }
        return s;
    }

    @Nullable
    private static JsonObject obj(@Nullable JsonObject o, String key) {
        if(o == null || !o.has(key) || !o.get(key).isJsonObject()) return null;
        return o.getAsJsonObject(key);
    }

    private static String str(@Nullable JsonObject o, String key) {
        if(o == null || !o.has(key) || !o.get(key).isJsonPrimitive()) return "";
        return o.get(key).getAsString();
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }
}
