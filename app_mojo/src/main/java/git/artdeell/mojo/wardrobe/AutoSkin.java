package git.artdeell.mojo.wardrobe;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import git.artdeell.mojo.CallbackBridge;
import git.artdeell.mojo.LogDispatcher;
import git.artdeell.mojo.LwjglGlfwKeycode;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.game.platform.Platform;
import git.artdeell.mojo.prefs.LauncherPreferences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto Skin: after joining a server the user allowed, types the skin command into chat, once per join.
 * It only ever sends the command with the public skin link. No token, password or account data is involved.
 */
public final class AutoSkin {
    public static final String PREF_ENABLED = "spiritAutoSkin";
    public static final String PREF_SERVERS = "spiritAutoSkinServers";

    /** Time to let the world load before typing, the chat is not usable earlier */
    private static final long JOIN_DELAY_MS = 8000;
    private static final Pattern CONNECTING = Pattern.compile("Connecting to ([^,\\s]+), (\\d+)");

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static boolean sPending;

    private AutoSkin() {}

    public static String commandFor(String skinUrl) {
        return "/skin set " + skinUrl;
    }

    public static Set<String> servers() {
        Set<String> stored = LauncherPreferences.DEFAULT_PREF.getStringSet(PREF_SERVERS, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    public static void setServers(Set<String> servers) {
        LauncherPreferences.DEFAULT_PREF.edit().putStringSet(PREF_SERVERS, new HashSet<>(servers)).apply();
    }

    /** Lower-cases and strips a port or spaces from what the user typed */
    public static String cleanHost(String input) {
        String host = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        int colon = host.indexOf(':');
        if(colon >= 0) host = host.substring(0, colon);
        return host;
    }

    private static boolean isAllowed(Set<String> allowed, String host) {
        for(String entry : allowed) {
            if(host.equals(entry) || host.endsWith("." + entry)) return true;
        }
        return false;
    }

    /** Call when the game starts. Does nothing unless Auto Skin is on and the skin has a public link. */
    public static void startForGame() {
        LogDispatcher.setAutoListener(null);
        if(!LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_ENABLED, false)) return;
        Set<String> allowed = servers();
        if(allowed.isEmpty()) return;
        Account account = Accounts.getCurrent();
        if(account == null) return;
        SkinStore.Active active = SkinStore.loadActive(account);
        if(active == null || active.url == null || active.url.isEmpty()) return;
        String command = commandFor(active.url);

        LogDispatcher.setAutoListener(line -> {
            Matcher matcher = CONNECTING.matcher(line);
            if(!matcher.find()) return;
            String host = cleanHost(matcher.group(1));
            if(!isAllowed(allowed, host)) return;
            schedule(command);
        });
    }

    private static void schedule(String command) {
        synchronized (AutoSkin.class) {
            // One command per join: further connect lines before it was sent are the same join
            if(sPending) return;
            sPending = true;
        }
        HANDLER.postDelayed(() -> send(command), JOIN_DELAY_MS);
    }

    private static void send(String command) {
        // Chat key, then the text, then Enter, spaced out so the game can open the chat screen in between
        CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_T);
        HANDLER.postDelayed(() -> {
            Platform.PLATFORM.sendBulkUnicodeEvent(command, CallbackBridge.getCurrentMods());
            HANDLER.postDelayed(() -> {
                CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ENTER);
                synchronized (AutoSkin.class) {
                    sPending = false;
                }
            }, 200);
        }, 300);
    }

    /** Call when the game ends so nothing stays registered */
    public static void stop() {
        LogDispatcher.setAutoListener(null);
        HANDLER.removeCallbacksAndMessages(null);
        synchronized (AutoSkin.class) {
            sPending = false;
        }
    }

    @Nullable
    public static String hostOf(String input) {
        String host = cleanHost(input);
        return host.isEmpty() ? null : host;
    }
}
