package git.artdeell.mojo.wardrobe;

import android.graphics.Bitmap;
import android.util.Base64;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import git.artdeell.mojo.authenticator.AuthType;
import git.artdeell.mojo.authenticator.accounts.Account;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/** Network side of the Wardrobe. Everything here blocks, call it off the main thread. */
public final class SkinNet {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final String UA = "SpiritLauncher/1.0";

    private SkinNet() {}

    /** A skin together with where it came from */
    public static final class Skin {
        public Bitmap bitmap;
        @Nullable public String url;
        public boolean slim;
        public String name;
    }

    /** An HTTP answer with a status the caller can act on */
    private static final class Response {
        int code;
        byte[] body;

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    // ---------------------------------------------------------------- http

    private static Response request(String method, String address, @Nullable String bearer,
                                    @Nullable String contentType, @Nullable byte[] payload) throws IOException {
        String current = address;
        for(int hop = 0; hop < 4; hop++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            try {
                conn.setRequestMethod(method);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(20000);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("User-Agent", UA);
                conn.setRequestProperty("Accept", "*/*");
                if(bearer != null) conn.setRequestProperty("Authorization", "Bearer " + bearer);
                if(payload != null) {
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", contentType);
                    conn.setFixedLengthStreamingMode(payload.length);
                    try(OutputStream out = conn.getOutputStream()) {
                        out.write(payload);
                    }
                }
                int code = conn.getResponseCode();
                if(code >= 300 && code < 400 && code != 304) {
                    String location = conn.getHeaderField("Location");
                    if(location == null) throw new IOException("Bad redirect from " + current);
                    current = new URL(new URL(current), location).toString();
                    continue;
                }
                Response response = new Response();
                response.code = code;
                InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                response.body = stream == null ? new byte[0] : readLimited(stream);
                return response;
            } finally {
                conn.disconnect();
            }
        }
        throw new IOException("Too many redirects");
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        try(InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if(out.size() > MAX_BYTES) throw new IOException("The file is too big to be a skin.");
            }
            return out.toByteArray();
        }
    }

    private static Response get(String url, @Nullable String bearer) throws IOException {
        return request("GET", url, bearer, null, null);
    }

    // ---------------------------------------------------------------- fetching

    /** Looks a premium player up by name and returns the skin they wear */
    public static Skin fetchByUsername(String rawName) throws IOException {
        String name = rawName == null ? "" : rawName.trim();
        if(!USERNAME.matcher(name).matches()) {
            throw new IOException("Usernames have 1 to 16 letters, digits or underscores.");
        }
        Response profile = get("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + name, null);
        if(profile.code == 404 || profile.code == 204) throw new IOException("No premium player is called " + name + ".");
        if(profile.code == 429) throw new IOException("Mojang says too many requests. Try again in a minute.");
        if(profile.code != 200) {
            // The older endpoint is the fallback
            profile = get("https://api.mojang.com/users/profiles/minecraft/" + name, null);
            if(profile.code == 404 || profile.code == 204) throw new IOException("No premium player is called " + name + ".");
            if(profile.code != 200) throw new IOException("Could not look " + name + " up (error " + profile.code + ").");
        }
        JsonObject profileJson = new JsonParser().parse(profile.text()).getAsJsonObject();
        String id = profileJson.get("id").getAsString();
        String exactName = profileJson.has("name") ? profileJson.get("name").getAsString() : name;

        Response session = get("https://sessionserver.mojang.com/session/minecraft/profile/" + id, null);
        if(session.code == 429) throw new IOException("Mojang says too many requests. Try again in a minute.");
        if(session.code != 200) throw new IOException("Could not read the skin of " + exactName + " (error " + session.code + ").");

        JsonObject sessionJson = new JsonParser().parse(session.text()).getAsJsonObject();
        JsonArray properties = sessionJson.getAsJsonArray("properties");
        if(properties != null) {
            for(JsonElement element : properties) {
                JsonObject property = element.getAsJsonObject();
                if(!"textures".equals(property.get("name").getAsString())) continue;
                String decoded = new String(Base64.decode(property.get("value").getAsString(), Base64.DEFAULT), StandardCharsets.UTF_8);
                JsonObject textures = new JsonParser().parse(decoded).getAsJsonObject().getAsJsonObject("textures");
                if(textures == null || !textures.has("SKIN")) break;
                JsonObject skinInfo = textures.getAsJsonObject("SKIN");
                String url = skinInfo.get("url").getAsString();
                boolean slim = skinInfo.has("metadata")
                        && skinInfo.getAsJsonObject("metadata").has("model")
                        && "slim".equals(skinInfo.getAsJsonObject("metadata").get("model").getAsString());
                Skin skin = download(url);
                skin.slim = slim;
                skin.name = exactName;
                return skin;
            }
        }
        throw new IOException(exactName + " wears a default skin, there is nothing to copy.");
    }

    /** Downloads a skin from a direct link to a PNG */
    public static Skin fetchByUrl(String rawUrl) throws IOException {
        String url = rawUrl == null ? "" : rawUrl.trim();
        String lower = url.toLowerCase(Locale.ROOT);
        if(!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IOException("Paste a link that starts with http:// or https://");
        }
        Skin skin = download(url);
        String host = new URL(url).getHost();
        skin.name = host != null ? host : "Skin";
        return skin;
    }

    private static Skin download(String url) throws IOException {
        Response response;
        try {
            response = get(url, null);
        } catch (FileNotFoundException e) {
            throw new IOException("That link does not exist.");
        }
        if(response.code != 200) throw new IOException("The link answered with error " + response.code + ".");
        Skin skin = new Skin();
        skin.bitmap = SkinStore.decodeBytes(response.body);
        skin.url = url;
        skin.slim = SkinStore.detectSlim(skin.bitmap);
        skin.name = "Skin";
        return skin;
    }

    /**
     * The skin an account shows right now. Microsoft asks the profile service (it also knows the arm model),
     * Ely.by uses its skin system. Local accounts have no skin on any server, so this returns null for them.
     */
    @Nullable
    public static Skin fetchAccountSkin(Account account) {
        try {
            if(account.authType == AuthType.MICROSOFT) {
                Skin fromProfile = fetchMicrosoftProfileSkin(account);
                if(fromProfile != null) return fromProfile;
            }
            if(account.authType.skinUrl == null) return null;
            String template = account.authType.skinUrl;
            String address = String.format(template, account.username);
            Skin skin;
            try {
                skin = download(address.startsWith("http://") ? "https://" + address.substring(7) : address);
            } catch (IOException e) {
                skin = download(address);
            }
            skin.name = account.username;
            return skin;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Skin fetchMicrosoftProfileSkin(Account account) {
        try {
            Response response = get("https://api.minecraftservices.com/minecraft/profile", account.accessToken);
            if(response.code != 200) return null;
            JsonArray skins = new JsonParser().parse(response.text()).getAsJsonObject().getAsJsonArray("skins");
            if(skins == null) return null;
            for(JsonElement element : skins) {
                JsonObject entry = element.getAsJsonObject();
                if(entry.has("state") && !"ACTIVE".equals(entry.get("state").getAsString())) continue;
                Skin skin = download(entry.get("url").getAsString());
                skin.slim = entry.has("variant") && "SLIM".equalsIgnoreCase(entry.get("variant").getAsString());
                skin.name = account.username;
                return skin;
            }
        } catch (IOException | RuntimeException ignored) {
            // Falls back to the public skin url
        }
        return null;
    }

    // ---------------------------------------------------------------- applying

    /** Uploads a skin to a Microsoft account through the official profile API. The token must be fresh. */
    public static void applyMicrosoft(Account account, Bitmap skin, boolean slim) throws IOException {
        byte[] png = SkinStore.encodePng(skin);
        String boundary = "----SpiritSkin" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"variant\"\r\n\r\n"
                + (slim ? "slim" : "classic") + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n";
        body.write(head.getBytes(StandardCharsets.UTF_8));
        body.write(png);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        Response response = request("POST", "https://api.minecraftservices.com/minecraft/profile/skins",
                account.accessToken, "multipart/form-data; boundary=" + boundary, body.toByteArray());
        if(response.code == 200 || response.code == 204) return;
        if(response.code == 401 || response.code == 403) throw new IOException("TOKEN_EXPIRED");
        if(response.code == 429) throw new IOException("Mojang says too many requests. Try again in a minute.");
        throw new IOException("Microsoft refused the skin (error " + response.code + ").");
    }

    // ---------------------------------------------------------------- public link

    /** Uploads the skin PNG to a free public file host and returns a direct https link to it. */
    public static String uploadPublic(Bitmap skin) throws IOException {
        byte[] png = SkinStore.encodePng(skin);
        String boundary = "----SpiritUp" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"fileToUpload\"; filename=\"skin.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(png);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        Response response = request("POST", "https://catbox.moe/user/api.php", null,
                "multipart/form-data; boundary=" + boundary, body.toByteArray());
        String link = response.text().trim();
        if(response.code != 200 || !link.startsWith("https://")) {
            throw new IOException("Could not create a link for this skin (error " + response.code + ").");
        }
        return link;
    }
}
