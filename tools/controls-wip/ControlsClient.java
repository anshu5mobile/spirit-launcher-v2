package git.artdeell.mojo.browse;

import android.util.Base64;

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
 * Shared controls, kept in a Supabase table (see tools/supabase/controls.sql). Only the launcher talks to it.
 * The public "anon" key is meant to be shipped in apps; what it may do is decided by the row level security
 * policies in that SQL file. All calls block, so run them off the main thread.
 */
public final class ControlsClient {
    public static final int PAGE_SIZE = 20;
    /** Control files are small; this keeps one upload from filling the database. */
    public static final int MAX_FILE_BYTES = 300 * 1024;
    /** Tags offered when filtering and uploading. */
    public static final String[] TAGS = {
            "pvp", "uhc", "survival", "mace", "cpvp", "crystal", "bedwars", "building",
            "2 finger", "3 finger", "4 finger", "5 finger", "6 finger",
            "minimal", "tablet", "transparent", "gamepad"
    };
    public static final int MAX_TAGS = 6;

    private final String baseUrl, key;

    public static final class Entry {
        public String id, name, description, author;
        public List<String> tags = new ArrayList<>();
        public int downloads;
    }

    public ControlsClient(String url, String key) {
        this.baseUrl = url.trim().replaceAll("/+$", "");
        this.key = key.trim();
    }

    public static boolean isConfigured(@Nullable String url, @Nullable String key) {
        return url != null && key != null && url.trim().startsWith("http") && !key.trim().isEmpty()
                && !"DUMMY".equals(url.trim()) && !"DUMMY".equals(key.trim());
    }

    /** @param sort "new", "downloads" */
    public List<Entry> list(@Nullable String query, List<String> tags, String sort, int offset) throws IOException {
        StringBuilder q = new StringBuilder("/rest/v1/controls?select=id,name,description,author,tags,downloads&hidden=eq.false");
        if(query != null && !query.trim().isEmpty()) {
            String clean = query.trim().replaceAll("[*,()%]", " ");
            q.append("&name=ilike.*").append(enc(clean)).append('*');
        }
        if(!tags.isEmpty()) {
            StringBuilder arr = new StringBuilder("{");
            for(int i = 0; i < tags.size(); i++) {
                if(i > 0) arr.append(',');
                arr.append('"').append(tags.get(i).replace("\"", "")).append('"');
            }
            q.append("&tags=cs.").append(enc(arr.append('}').toString()));
        }
        q.append("&order=").append("downloads".equals(sort) ? "downloads.desc" : "created_at.desc");
        q.append("&limit=").append(PAGE_SIZE).append("&offset=").append(offset);
        JsonElement root = new JsonParser().parse(request("GET", q.toString(), null, false));
        List<Entry> out = new ArrayList<>();
        if(!root.isJsonArray()) return out;
        for(JsonElement e : root.getAsJsonArray()) {
            if(!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            Entry en = new Entry();
            en.id = str(o, "id");
            if(en.id == null) continue;
            en.name = str(o, "name");
            en.description = str(o, "description");
            en.author = str(o, "author");
            if(o.has("downloads") && o.get("downloads").isJsonPrimitive()) en.downloads = o.get("downloads").getAsInt();
            JsonElement t = o.get("tags");
            if(t != null && t.isJsonArray()) for(JsonElement x : t.getAsJsonArray()) if(x.isJsonPrimitive()) en.tags.add(x.getAsString());
            out.add(en);
        }
        return out;
    }

    /** The raw control file (json or the zip variant with bitmaps). */
    public byte[] download(String id) throws IOException {
        JsonElement root = new JsonParser().parse(request("GET", "/rest/v1/controls?select=data&hidden=eq.false&id=eq." + enc(id), null, false));
        if(!root.isJsonArray() || ((JsonArray) root).size() == 0) throw new IOException("Control not found");
        String data = str(root.getAsJsonArray().get(0).getAsJsonObject(), "data");
        if(data == null) throw new IOException("Control is empty");
        return Base64.decode(data, Base64.DEFAULT);
    }

    /** Best effort, a failure here must never block an install. */
    public void countDownload(String id) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("cid", id);
            request("POST", "/rest/v1/rpc/increment_downloads", body.toString(), true);
        } catch (IOException | RuntimeException ignored) {}
    }

    public void upload(String name, String description, String author, List<String> tags, byte[] file) throws IOException {
        if(file.length > MAX_FILE_BYTES) throw new IOException("File is too big");
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("description", description);
        body.addProperty("author", author);
        JsonArray arr = new JsonArray();
        for(String t : tags) arr.add(t);
        body.add("tags", arr);
        body.addProperty("data", Base64.encodeToString(file, Base64.NO_WRAP));
        request("POST", "/rest/v1/controls", body.toString(), true);
    }

    private String request(String method, String path, @Nullable String body, boolean minimal) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(12000);
            c.setReadTimeout(20000);
            c.setRequestProperty("apikey", key);
            c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("User-Agent", "SpiritLauncher/1.0 (Android)");
            c.setRequestProperty("Accept", "application/json");
            if(minimal) c.setRequestProperty("Prefer", "return=minimal");
            if(body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try(OutputStream os = c.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : readAll(in);
            if(code >= 400) throw new IOException("HTTP " + code + (text.isEmpty() ? "" : ": " + text));
            return text;
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try(InputStream is = in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (java.io.UnsupportedEncodingException e) { return s; }
    }

    @Nullable
    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
