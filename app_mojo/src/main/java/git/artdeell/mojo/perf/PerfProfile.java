package git.artdeell.mojo.perf;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One-tap profiles. Each one is a table of real Minecraft options (written to options.txt) plus the launcher's
 * resolution scale and RAM. The distances depend on the device tier so a budget phone is never given a value
 * it cannot hold. Nothing here is a flag that "boosts" the device: the gain comes from asking the game for less work
 * (fewer chunks, entities and particles to simulate and draw).
 */
public enum PerfProfile {
    POTATO, BALANCED, PERFORMANCE, QUALITY;

    // index = DeviceInfo.Tier ordinal: LOW, ENTRY, MID, HIGH
    private static final int[][] RENDER = {{3, 4, 5, 6}, {5, 7, 9, 12}, {4, 6, 8, 10}, {7, 10, 14, 18}};
    private static final int[][] SIM = {{5, 5, 5, 6}, {5, 6, 8, 10}, {5, 5, 6, 8}, {6, 8, 10, 12}};
    private static final int[] SCALE = {60, 80, 75, 100};

    public int resolutionPercent(DeviceInfo d) {
        int base = SCALE[ordinal()];
        // Only ever lower the scale on weaker phones, never raise it above the player's panel
        if(this == QUALITY || this == BALANCED) return d.tier == DeviceInfo.Tier.LOW ? Math.min(base, 75) : base;
        return d.tier == DeviceInfo.Tier.HIGH && this == PERFORMANCE ? 85 : base;
    }

    public int ramMb(DeviceInfo d) {
        int rec = d.recommendedRamMb();
        float factor = this == POTATO ? 0.75f : this == QUALITY ? 1.25f : 1f;
        int cap = (int) (d.totalRamMb * 0.45);
        return Math.max(512, Math.min((int) (rec * factor), cap)) / 64 * 64;
    }

    /** options.txt values. Keys the installed game version does not have are skipped when applying. */
    public Map<String, String> options(DeviceInfo d) {
        int t = d.tier.ordinal();
        int p = ordinal();
        Map<String, String> m = new LinkedHashMap<>();
        m.put("renderDistance", String.valueOf(RENDER[p][t]));
        m.put("simulationDistance", String.valueOf(SIM[p][t]));
        int hz = Math.max(30, Math.round(d.refreshHz));
        boolean fast = this == POTATO || this == PERFORMANCE || this == BALANCED;
        m.put("graphicsMode", fast ? "0" : "1");
        m.put("fancyGraphics", fast ? "false" : "true");
        m.put("particles", this == POTATO || this == PERFORMANCE ? "2" : this == BALANCED ? "1" : "0");
        m.put("entityDistanceScaling", this == POTATO || this == PERFORMANCE ? "0.5" : this == BALANCED ? "0.75" : "1.0");
        m.put("entityShadows", this == QUALITY ? "true" : "false");
        m.put("mipmapLevels", this == POTATO || this == PERFORMANCE ? "0" : this == BALANCED ? "2" : "4");
        m.put("biomeBlendRadius", this == QUALITY ? "2" : this == BALANCED ? "1" : "0");
        m.put("cloudStatus", this == QUALITY ? "\"fancy\"" : this == BALANCED ? "\"fast\"" : "\"off\"");
        m.put("renderClouds", this == QUALITY ? "\"true\"" : this == BALANCED ? "\"fast\"" : "\"false\"");
        // Capping at the screen's refresh rate gives even frame times; Performance leaves it open
        m.put("maxFps", this == PERFORMANCE ? "260" : String.valueOf(Math.min(hz, this == POTATO ? 60 : 260)));
        m.put("enableVsync", "false");
        m.put("viewBobbing", this == POTATO ? "false" : "true");
        // More of the video settings. Each key is only written when the game's options.txt already has it, so keys
        // that a game version does not know (newer ones such as cutoutLeaves) are simply skipped.
        boolean low = this == POTATO || this == PERFORMANCE;
        m.put("ao", this == POTATO || this == PERFORMANCE ? "false" : "true");
        m.put("vignette", this == QUALITY ? "true" : "false");
        m.put("cutoutLeaves", this == QUALITY || this == BALANCED ? "true" : "false");
        m.put("weatherRadius", this == POTATO ? "3" : this == PERFORMANCE ? "4" : this == BALANCED ? "6" : "10");
        m.put("chunkSectionFadeInTime", this == QUALITY ? "0.75" : "0.0");
        m.put("menuBackgroundBlurriness", low ? "0" : this == BALANCED ? "2" : "5");
        m.put("hideLightningFlashes", low ? "true" : "false");
        m.put("showAutosaveIndicator", low ? "false" : "true");
        m.put("glintStrength", this == POTATO ? "0.0" : "0.75");
        m.put("glintSpeed", this == POTATO ? "0.0" : "0.5");
        return m;
    }

    /**
     * Settings of Sodium Extra (animations, particles, details), changed in config/sodium-extra-options.json when
     * that file exists. Path "section.key" to value; only existing true/false keys are changed.
     */
    public Map<String, Boolean> sodiumExtra() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        boolean potato = this == POTATO, perf = this == PERFORMANCE, quality = this == QUALITY;
        // animated textures
        m.put("animation_settings.animation", !potato);
        m.put("animation_settings.water", quality || this == BALANCED);
        m.put("animation_settings.lava", quality || this == BALANCED);
        m.put("animation_settings.fire", quality || this == BALANCED);
        m.put("animation_settings.portal", quality || this == BALANCED);
        m.put("animation_settings.block_animations", !potato && !perf);
        m.put("animation_settings.sculk_sensor", quality);
        // particles
        m.put("particle_settings.rain_splash", quality);
        m.put("particle_settings.block_break", !potato);
        m.put("particle_settings.block_breaking", !potato);
        // details
        m.put("detail_settings.rain_snow", !potato);
        m.put("detail_settings.biome_colors", !potato);
        m.put("detail_settings.sky_colors", !potato);
        // things drawn in the world
        m.put("render_settings.enchanting_table_book", quality || this == BALANCED);
        m.put("render_settings.beacon_beam", !potato);
        m.put("render_settings.limit_beacon_beam_height", !quality);
        return m;
    }
}
