package git.artdeell.mojo.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls a human friendly Minecraft version and mod loader out of a version id such as
 * "fabric-loader-0.19.3-1.21.11" or "1.20.1-forge-47.2.0". Pure Java on purpose, so it can be unit tested.
 */
public final class SpiritVersionInfo {
    /** Loader names; {@link #loader} is null for vanilla. */
    public static final String FABRIC = "Fabric";
    public static final String QUILT = "Quilt";
    public static final String FORGE = "Forge";
    public static final String NEOFORGE = "NeoForge";
    public static final String OPTIFINE = "OptiFine";

    // Release style versions: 1.21.11, 1.7.10, 26.1
    private static final Pattern PLAIN_VERSION = Pattern.compile("^\\d{1,2}\\.\\d{1,2}(\\.\\d{1,2})?$");
    // First "1.x(.y)" that is not glued to other digits or dots, used where the loader version comes later
    private static final Pattern LEADING_MC = Pattern.compile("(?<![\\d.])(1\\.\\d{1,2}(?:\\.\\d{1,2})?)(?![\\d.])");

    @Nullable public final String minecraftVersion;
    @Nullable public final String loader;

    private SpiritVersionInfo(@Nullable String minecraftVersion, @Nullable String loader) {
        this.minecraftVersion = minecraftVersion;
        this.loader = loader;
    }

    @NonNull
    public static SpiritVersionInfo parse(@Nullable String versionId) {
        if(versionId == null || versionId.trim().isEmpty()) return new SpiritVersionInfo(null, null);
        String id = versionId.trim();
        String lower = id.toLowerCase(Locale.ROOT);

        if(lower.startsWith("fabric-loader") || lower.startsWith("quilt-loader")) {
            String loader = lower.startsWith("fabric") ? FABRIC : QUILT;
            // fabric-loader-<loader version>-<minecraft version>
            String last = id.substring(id.lastIndexOf('-') + 1);
            return new SpiritVersionInfo(looksLikeVersion(last) ? last : null, loader);
        }
        if(lower.contains("neoforge")) return new SpiritVersionInfo(firstMc(id), NEOFORGE);
        if(lower.contains("forge")) return new SpiritVersionInfo(firstMc(id), FORGE);
        if(lower.contains("optifine")) return new SpiritVersionInfo(firstMc(id), OPTIFINE);

        return new SpiritVersionInfo(id, null);
    }

    private static boolean looksLikeVersion(String s) {
        // Snapshots like 24w14a or 1.21-pre1 are accepted too: anything starting with a digit
        return PLAIN_VERSION.matcher(s).matches() || (!s.isEmpty() && Character.isDigit(s.charAt(0)));
    }

    @Nullable
    private static String firstMc(String id) {
        Matcher m = LEADING_MC.matcher(id);
        return m.find() ? m.group(1) : null;
    }

    /** 1536 -> "1.5 GB", 2048 -> "2 GB". */
    @NonNull
    public static String formatRam(int megabytes) {
        double gb = megabytes / 1024.0;
        if(Math.abs(gb - Math.rint(gb)) < 0.05) return String.format(Locale.US, "%d GB", Math.round(gb));
        return String.format(Locale.US, "%.1f GB", gb);
    }
}
