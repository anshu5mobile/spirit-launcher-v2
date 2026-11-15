package git.artdeell.mojo.browse;

import androidx.annotation.Nullable;

import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.tasks.mojson.MoJsonExtras;
import git.artdeell.mojo.utils.SpiritVersionInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The instance Browse installs into, plus the Minecraft version and loader used to filter results.
 * Reads from disk, so build it off the main thread.
 */
public final class BrowseTarget {
    public final File gameDir;
    public final String instanceName;
    /** Real Minecraft version, or null when it could not be worked out (then results are not version filtered) */
    @Nullable public final String mcVersion;
    /** Loader display name, or null for vanilla */
    @Nullable public final String loaderName;
    /** Modrinth loader ids that can run mods on this instance (empty for vanilla) */
    public final List<String> loaders;

    private BrowseTarget(File gameDir, String instanceName, @Nullable String mcVersion, @Nullable String loaderName, List<String> loaders) {
        this.gameDir = gameDir;
        this.instanceName = instanceName;
        this.mcVersion = mcVersion;
        this.loaderName = loaderName;
        this.loaders = loaders;
    }

    @Nullable
    public static BrowseTarget forSelectedInstance() {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) return null;
        String versionId = instance.versionId;
        try {
            versionId = MoJsonExtras.normalizeVersionId(versionId);
        } catch (Throwable ignored) {
            // The version list may not be loaded yet; the alias then just stays unresolved
        }
        SpiritVersionInfo info = SpiritVersionInfo.parse(versionId);
        String mc = info.minecraftVersion;
        // Aliases that could not be resolved are not real versions
        if(Instance.VERSION_LATEST_RELEASE.equals(mc) || Instance.VERSION_LATEST_SNAPSHOT.equals(mc)) mc = null;

        List<String> loaders = new ArrayList<>();
        if(SpiritVersionInfo.FABRIC.equals(info.loader)) {
            loaders.add("fabric");
        } else if(SpiritVersionInfo.QUILT.equals(info.loader)) {
            loaders.add("quilt");
            loaders.add("fabric"); // Quilt runs Fabric mods
        } else if(SpiritVersionInfo.FORGE.equals(info.loader)) {
            loaders.add("forge");
        } else if(SpiritVersionInfo.NEOFORGE.equals(info.loader)) {
            loaders.add("neoforge");
        }
        String name = instance.name != null && !instance.name.trim().isEmpty() ? instance.name : instance.getGameDirectory().getName();
        return new BrowseTarget(instance.getGameDirectory(), name, mc, info.loader, loaders);
    }

    /** True when mods cannot run on this instance (no loader) */
    public boolean isVanilla() {
        return loaders.isEmpty();
    }
}
