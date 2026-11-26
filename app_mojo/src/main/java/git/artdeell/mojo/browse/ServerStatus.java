package git.artdeell.mojo.browse;

import com.google.gson.JsonObject;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Asks the public mcsrvstat.us service whether a server is up and how many players it has. Short timeouts, never throws. */
public final class ServerStatus {
    public final boolean known;
    public final boolean online;
    public final int players;
    public final int max;
    public final String version;

    private ServerStatus(boolean known, boolean online, int players, int max, String version) {
        this.known = known; this.online = online; this.players = players; this.max = max; this.version = version;
    }

    public static ServerStatus fetch(String address) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://api.mcsrvstat.us/3/" + URLEncoder.encode(address, "UTF-8")).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(6000);
            c.setRequestProperty("User-Agent", "SpiritLauncher");
            if(c.getResponseCode() != 200) return new ServerStatus(false, false, 0, 0, "");
            try(InputStreamReader r = new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject o = new com.google.gson.Gson().fromJson(r, JsonObject.class);
                boolean online = o.has("online") && o.get("online").getAsBoolean();
                int players = 0, max = 0;
                if(online && o.has("players") && o.get("players").isJsonObject()) {
                    JsonObject p = o.getAsJsonObject("players");
                    if(p.has("online")) players = p.get("online").getAsInt();
                    if(p.has("max")) max = p.get("max").getAsInt();
                }
                String version = online && o.has("version") && !o.get("version").isJsonNull() ? o.get("version").getAsString() : "";
                return new ServerStatus(true, online, players, max, version);
            }
        } catch (Exception e) {
            return new ServerStatus(false, false, 0, 0, "");
        } finally {
            if(c != null) c.disconnect();
        }
    }
}
