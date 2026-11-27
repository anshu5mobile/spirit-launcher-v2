package git.artdeell.mojo.wardrobe;

import android.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.accounts.Account;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * A tiny skin server that lives inside the launcher (nothing is installed in the game folder).
 * For an offline account the game is started with the bundled authlib-injector pointed at this server, so the game
 * asks it "what does this player look like" and gets the skin and cape the player chose in the Wardrobe.
 * It only listens on the phone's loopback address and only knows the one account the game was started with.
 */
public final class LocalSkinServer {
    private static ServerSocket sSocket;
    private static Account sAccount;
    /** When set, everything except the player's own textures is passed on to this real server (Ely.by) */
    private static String sUpstream;

    private LocalSkinServer() {}

    /** @return the port the server listens on, or -1 when it could not be started */
    public static synchronized int start(Account account) {
        return start(account, null);
    }

    /** @param upstream the real authlib-injector API (no trailing slash), or null for a purely local server */
    public static synchronized int start(Account account, String upstream) {
        sAccount = account;
        sUpstream = upstream;
        if(sSocket != null && !sSocket.isClosed()) return sSocket.getLocalPort();
        try {
            final ServerSocket socket = new ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"));
            sSocket = socket;
            Thread acceptor = new Thread(() -> {
                while(!socket.isClosed()) {
                    try {
                        final Socket client = socket.accept();
                        Thread t = new Thread(() -> handle(client), "SpiritSkinServer-conn");
                        t.setDaemon(true);
                        t.start();
                    } catch (IOException e) {
                        return;
                    }
                }
            }, "SpiritSkinServer");
            acceptor.setDaemon(true);
            acceptor.start();
            return socket.getLocalPort();
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- http

    private static void handle(Socket client) {
        try(Socket c = client) {
            c.setSoTimeout(10000);
            InputStream in = c.getInputStream();
            String requestLine = readLine(in);
            if(requestLine == null) return;
            String[] parts = requestLine.split(" ");
            if(parts.length < 2) return;
            String method = parts[0];
            String path = parts[1];
            String query = "";
            int q = path.indexOf('?');
            if(q >= 0) { query = path.substring(q); path = path.substring(0, q); }
            int length = 0;
            String contentType = null, authorization = null;
            String line;
            while((line = readLine(in)) != null && !line.isEmpty()) {
                if(line.regionMatches(true, 0, "content-length:", 0, 15)) {
                    try { length = Integer.parseInt(line.substring(15).trim()); } catch (NumberFormatException ignored) { /* 0 */ }
                } else if(line.regionMatches(true, 0, "content-type:", 0, 13)) {
                    contentType = line.substring(13).trim();
                } else if(line.regionMatches(true, 0, "authorization:", 0, 14)) {
                    authorization = line.substring(14).trim();
                }
            }
            byte[] body = new byte[Math.min(Math.max(length, 0), 65536)];
            int read = 0;
            while(read < body.length) {
                int n = in.read(body, read, body.length - read);
                if(n < 0) break;
                read += n;
            }
            route(c.getOutputStream(), method, path, query, body, contentType, authorization);
        } catch (IOException | RuntimeException ignored) { /* the game closed the connection */ }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while((b = in.read()) >= 0) {
            if(b == '\n') break;
            if(b != '\r') out.write(b);
            if(out.size() > 8192) break;
        }
        if(b < 0 && out.size() == 0) return null;
        return out.toString("UTF-8");
    }

    private static void respond(OutputStream out, int code, String type, byte[] data) throws IOException {
        String status = code == 200 ? "OK" : code == 204 ? "No Content" : code == 401 ? "Unauthorized" : code == 404 ? "Not Found" : "Status";
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(code).append(' ').append(status).append("\r\n");
        if(data != null && code != 204) head.append("Content-Type: ").append(type).append("\r\nContent-Length: ").append(data.length).append("\r\n");
        else head.append("Content-Length: 0\r\n");
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        if(data != null && code != 204) out.write(data);
        out.flush();
    }

    private static void json(OutputStream out, int code, String json) throws IOException {
        respond(out, code, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- api

    private static void route(OutputStream out, String method, String path, String query, byte[] body,
                              String contentType, String authorization) throws IOException {
        Account account = sAccount;
        if(account == null) { json(out, 404, "{}"); return; }
        String id = noDashes(account.profileId);
        String upstream = sUpstream;
        if(upstream != null) {
            routeProxy(out, upstream, account, id, method, path, query, body, contentType, authorization);
            return;
        }

        if(path.equals("/") || path.isEmpty()) {
            JsonObject meta = new JsonObject();
            meta.addProperty("serverName", "Spirit Launcher");
            meta.addProperty("implementationName", "spirit-launcher");
            meta.addProperty("implementationVersion", "1");
            JsonObject root = new JsonObject();
            root.add("meta", meta);
            JsonArray domains = new JsonArray();
            domains.add("127.0.0.1");
            root.add("skinDomains", domains);
            json(out, 200, root.toString());
            return;
        }
        if(path.startsWith("/sessionserver/session/minecraft/profile/")) {
            String asked = noDashes(path.substring("/sessionserver/session/minecraft/profile/".length()));
            if(!asked.equalsIgnoreCase(id)) { respond(out, 204, null, null); return; }
            json(out, 200, profileJson(account, id, true));
            return;
        }
        if(method.equals("POST") && path.equals("/api/profiles/minecraft")) {
            // name -> id lookup: only this player is known
            boolean asked = new String(body, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).contains("\"" + account.username.toLowerCase(Locale.ROOT) + "\"");
            JsonArray array = new JsonArray();
            if(asked) {
                JsonObject o = new JsonObject();
                o.addProperty("id", id);
                o.addProperty("name", account.username);
                array.add(o);
            }
            json(out, 200, array.toString());
            return;
        }
        if(path.startsWith("/api/users/profiles/minecraft/")) {
            String name = path.substring("/api/users/profiles/minecraft/".length());
            if(!name.equalsIgnoreCase(account.username)) { respond(out, 204, null, null); return; }
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("name", account.username);
            json(out, 200, o.toString());
            return;
        }
        if(path.startsWith("/textures/")) {
            String file = path.substring("/textures/".length());
            File png = file.startsWith("cape-") ? SkinStore.capeFile(account) : SkinStore.activeFile(account);
            if(!png.isFile()) { json(out, 404, "{}"); return; }
            respond(out, 200, "image/png", readAll(png));
            return;
        }
        if(path.startsWith("/authserver") || path.startsWith("/minecraftservices")) {
            // no real account behind an offline player: the game treats this like being offline
            json(out, 401, "{\"error\":\"UnauthorizedOperationException\",\"errorMessage\":\"Offline account\"}");
            return;
        }
        json(out, 404, "{\"error\":\"NotFound\"}");
    }

    // ---------------------------------------------------------------- proxy mode (Ely.by)

    private static final class Reply {
        int code = 502;
        String type = "application/json";
        byte[] data = new byte[0];
    }

    private static Reply forward(String upstream, String method, String path, String query, byte[] body,
                                 String contentType, String authorization) {
        Reply r = new Reply();
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(upstream + path + query).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            c.setRequestMethod(method);
            c.setRequestProperty("User-Agent", "SpiritLauncher");
            if(authorization != null) c.setRequestProperty("Authorization", authorization);
            if(body != null && body.length > 0 && !method.equals("GET")) {
                if(contentType != null) c.setRequestProperty("Content-Type", contentType);
                c.setDoOutput(true);
                try(OutputStream o = c.getOutputStream()) { o.write(body); }
            }
            r.code = c.getResponseCode();
            String t = c.getContentType();
            if(t != null) r.type = t;
            InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
            if(in != null) {
                try(InputStream i = in) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while((n = i.read(buf)) > 0) bo.write(buf, 0, n);
                    r.data = bo.toByteArray();
                }
            }
        } catch (IOException | RuntimeException e) {
            r.code = 502;
            r.data = "{\"error\":\"BadGateway\"}".getBytes(StandardCharsets.UTF_8);
        } finally {
            if(c != null) c.disconnect();
        }
        return r;
    }

    private static void routeProxy(OutputStream out, String upstream, Account account, String id, String method,
                                   String path, String query, byte[] body, String contentType, String authorization) throws IOException {
        if(path.startsWith("/textures/")) {
            String file = path.substring("/textures/".length());
            File png = file.startsWith("cape-") ? SkinStore.capeFile(account) : SkinStore.activeFile(account);
            if(!png.isFile()) { json(out, 404, "{}"); return; }
            respond(out, 200, "image/png", readAll(png));
            return;
        }
        Reply r = forward(upstream, method, path.isEmpty() ? "/" : path, query, body, contentType, authorization);
        try {
            if(path.equals("/") && r.code == 200) {
                JsonObject root = new com.google.gson.Gson().fromJson(new String(r.data, StandardCharsets.UTF_8), JsonObject.class);
                JsonArray domains = root.has("skinDomains") && root.get("skinDomains").isJsonArray() ? root.getAsJsonArray("skinDomains") : new JsonArray();
                domains.add("127.0.0.1");
                root.add("skinDomains", domains);
                json(out, 200, root.toString());
                return;
            }
            if(path.startsWith("/sessionserver/session/minecraft/profile/") && r.code == 200
                    && noDashes(path.substring("/sessionserver/session/minecraft/profile/".length())).equalsIgnoreCase(id)) {
                json(out, 200, withLocalTextures(new String(r.data, StandardCharsets.UTF_8), account, id));
                return;
            }
        } catch (RuntimeException ignored) { /* send the real answer unchanged */ }
        respond(out, r.code, r.type, r.data);
    }

    /** Puts the player's local skin and cape into the real server's profile answer (only for the player's own profile) */
    private static String withLocalTextures(String profileJson, Account account, String id) {
        JsonObject profile = new com.google.gson.Gson().fromJson(profileJson, JsonObject.class);
        JsonArray props = profile.has("properties") && profile.get("properties").isJsonArray() ? profile.getAsJsonArray("properties") : new JsonArray();
        JsonObject textureProp = null;
        for(com.google.gson.JsonElement e : props) {
            if(e.isJsonObject() && e.getAsJsonObject().has("name") && "textures".equals(e.getAsJsonObject().get("name").getAsString())) {
                textureProp = e.getAsJsonObject();
            }
        }
        JsonObject value;
        if(textureProp != null) {
            value = new com.google.gson.Gson().fromJson(new String(Base64.decode(textureProp.get("value").getAsString(), Base64.DEFAULT), StandardCharsets.UTF_8), JsonObject.class);
        } else {
            value = new JsonObject();
            value.addProperty("timestamp", System.currentTimeMillis());
            value.addProperty("profileId", id);
            value.addProperty("profileName", account.username);
        }
        JsonObject textures = value.has("textures") && value.get("textures").isJsonObject() ? value.getAsJsonObject("textures") : new JsonObject();
        File skin = SkinStore.activeFile(account);
        File cape = SkinStore.capeFile(account);
        if(skin.isFile()) {
            SkinStore.Active active = SkinStore.loadActive(account);
            JsonObject s = new JsonObject();
            s.addProperty("url", "http://127.0.0.1:" + port() + "/textures/skin-" + hash(skin));
            if(active != null && active.slim) {
                JsonObject md = new JsonObject();
                md.addProperty("model", "slim");
                s.add("metadata", md);
            }
            textures.add("SKIN", s);
        }
        if(cape.isFile()) {
            JsonObject c = new JsonObject();
            c.addProperty("url", "http://127.0.0.1:" + port() + "/textures/cape-" + hash(cape));
            textures.add("CAPE", c);
        }
        value.add("textures", textures);
        if(textureProp == null) {
            textureProp = new JsonObject();
            textureProp.addProperty("name", "textures");
            props.add(textureProp);
        }
        textureProp.addProperty("value", Base64.encodeToString(value.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        textureProp.remove("signature"); // the picture was changed, so the real server's signature no longer fits
        profile.add("properties", props);
        return profile.toString();
    }

    /** Finds the real API address behind a site such as ely.by (the header authlib-injector itself looks for) */
    public static String resolveUpstream(String site, String fallback) {
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL("https://" + site).openConnection();
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setInstanceFollowRedirects(true);
            String loc = c.getHeaderField("X-Authlib-Injector-API-Location");
            if(loc != null && !loc.isEmpty()) {
                String url = new java.net.URL(c.getURL(), loc).toString();
                return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            }
        } catch (IOException | RuntimeException ignored) { /* use the fallback */ }
        finally { if(c != null) c.disconnect(); }
        return fallback;
    }

    private static String profileJson(Account account, String id, boolean withTextures) {
        JsonObject profile = new JsonObject();
        profile.addProperty("id", id);
        profile.addProperty("name", account.username);
        JsonArray props = new JsonArray();
        SkinStore.Active active = null;
        File skin = SkinStore.activeFile(account);
        File cape = SkinStore.capeFile(account);
        if(withTextures && (skin.isFile() || cape.isFile())) {
            JsonObject textures = new JsonObject();
            if(skin.isFile()) {
                active = SkinStore.loadActive(account);
                JsonObject s = new JsonObject();
                s.addProperty("url", "http://127.0.0.1:" + port() + "/textures/skin-" + hash(skin));
                if(active != null && active.slim) {
                    JsonObject md = new JsonObject();
                    md.addProperty("model", "slim");
                    s.add("metadata", md);
                }
                textures.add("SKIN", s);
            }
            if(cape.isFile()) {
                JsonObject c = new JsonObject();
                c.addProperty("url", "http://127.0.0.1:" + port() + "/textures/cape-" + hash(cape));
                textures.add("CAPE", c);
            }
            JsonObject value = new JsonObject();
            value.addProperty("timestamp", System.currentTimeMillis());
            value.addProperty("profileId", id);
            value.addProperty("profileName", account.username);
            value.add("textures", textures);
            JsonObject prop = new JsonObject();
            prop.addProperty("name", "textures");
            prop.addProperty("value", Base64.encodeToString(value.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
            props.add(prop);
        }
        profile.add("properties", props);
        return profile.toString();
    }

    private static int port() {
        ServerSocket s = sSocket;
        return s == null ? 0 : s.getLocalPort();
    }

    /** The picture's hash is part of its address, so the game downloads it again when the player picks another one */
    private static String hash(File file) {
        try {
            byte[] sum = MessageDigest.getInstance("SHA-1").digest(readAll(file));
            StringBuilder sb = new StringBuilder();
            for(byte b : sum) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(file.lastModified());
        }
    }

    private static byte[] readAll(File file) throws IOException {
        return readRaw(file);
    }

    private static byte[] readRaw(File file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try(InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static String noDashes(String s) {
        return s == null ? "" : s.replace("-", "");
    }
}
