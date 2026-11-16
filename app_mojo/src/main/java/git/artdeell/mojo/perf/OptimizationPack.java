package git.artdeell.mojo.perf;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Optimization mods worth installing, per loader. Availability for the exact Minecraft version is checked on Modrinth first. */
public final class OptimizationPack {
    private OptimizationPack() {}

    public static final class Entry {
        public final String slug;
        public final String what;
        /** The mod needs a renderer with modern OpenGL (Zink); it will not start on GL4ES */
        public final boolean needsModernGl;

        Entry(String slug, String what, boolean needsModernGl) {
            this.slug = slug;
            this.what = what;
            this.needsModernGl = needsModernGl;
        }
    }

    public static List<Entry> forLoaders(List<String> loaders) {
        List<Entry> list = new ArrayList<>();
        boolean fabric = loaders.contains("fabric") || loaders.contains("quilt");
        boolean neo = loaders.contains("neoforge");
        boolean forge = loaders.contains("forge");
        if(fabric) list.add(new Entry("lithium", "Game logic: entities, TNT, minecarts, collisions", false));
        if(fabric || neo || forge) list.add(new Entry("entityculling", "Skips drawing entities you cannot see", false));
        if(fabric || neo || forge) list.add(new Entry("ferrite-core", "Uses much less RAM", false));
        if(fabric || neo || forge) list.add(new Entry("modernfix", "Faster start, less RAM, fewer stutters", false));
        if(fabric || neo) list.add(new Entry("moreculling", "Draws fewer hidden block faces", false));
        if(fabric) list.add(new Entry("dynamic-fps", "Saves battery when the game is in the background", false));
        if(fabric || neo || forge) list.add(new Entry("immediatelyfast", "Faster drawing of the UI and entities", true));
        if(fabric || neo) list.add(new Entry("sodium", "Much faster world rendering", true));
        if(forge) list.add(new Entry("embeddium", "Much faster world rendering (Sodium for Forge)", true));
        return list;
    }

    /** True for renderers that expose modern OpenGL through Vulkan, which Sodium-like mods need */
    public static boolean rendererSupportsModernGl(@Nullable String renderer) {
        if(renderer == null) return false;
        String r = renderer.toLowerCase(java.util.Locale.ROOT);
        return r.contains("zink") || r.contains("vulkan");
    }
}
