package git.artdeell.mojo.modding;

import android.app.Activity;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.modding.fabric.FabricVersion;
import git.artdeell.mojo.modding.fabric.FabriclikeUtils;
import git.artdeell.mojo.tasks.mojson.MoJsonExtras;
import git.artdeell.mojo.utils.SpiritVersionInfo;

import java.io.IOException;

/**
 * Changes the Fabric or Quilt loader of an instance without making a new instance: pick the loader and a version,
 * the launcher downloads its version file and the instance points to it. Forge and NeoForge need their own
 * installer and are not handled here.
 */
public final class LoaderSwitcher {
    private LoaderSwitcher() {}

    public interface Callback {
        /** Runs on the UI thread with the id of the version the instance should now use */
        void onSwitched(String versionId);
    }

    /** True when the version is vanilla, Fabric or Quilt (the ones that can be switched here) */
    public static boolean canSwitch(@Nullable String versionId) {
        SpiritVersionInfo info = SpiritVersionInfo.parse(resolve(versionId));
        return info.minecraftVersion != null
                && (info.loader == null || SpiritVersionInfo.FABRIC.equals(info.loader) || SpiritVersionInfo.QUILT.equals(info.loader));
    }

    private static String resolve(@Nullable String versionId) {
        if(versionId == null) return null;
        try { return MoJsonExtras.normalizeVersionId(versionId); } catch (Throwable ignored) { return versionId; }
    }

    // ---------------------------------------------------------------- dialog

    public static void showDialog(@NonNull final Activity activity, @Nullable String versionId, @NonNull final Callback callback) {
        final SpiritVersionInfo info = SpiritVersionInfo.parse(resolve(versionId));
        if(!canSwitch(versionId)) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.spirit_loader_title)
                    .setMessage(R.string.spirit_loader_unsupported)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        final String mc = info.minecraftVersion;
        String[] kinds = {"Fabric", "Quilt"};
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.spirit_loader_pick, mc))
                .setItems(kinds, (d, which) -> loadVersions(activity, which == 0 ? FabriclikeUtils.FABRIC_UTILS : FabriclikeUtils.QUILT_UTILS, mc, callback))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void loadVersions(final Activity activity, final FabriclikeUtils utils, final String mc, final Callback callback) {
        Toast.makeText(activity, R.string.spirit_loader_loading, Toast.LENGTH_SHORT).show();
        MojoApplication.sExecutorService.execute(() -> {
            FabricVersion[] versions = null;
            try { versions = utils.downloadLoaderVersions(mc); } catch (IOException | RuntimeException ignored) { /* shown below */ }
            final FabricVersion[] list = versions;
            activity.runOnUiThread(() -> {
                if(activity.isFinishing()) return;
                if(list == null || list.length == 0) {
                    Toast.makeText(activity, activity.getString(R.string.spirit_loader_none, utils.getName(), mc), Toast.LENGTH_LONG).show();
                    return;
                }
                int n = Math.min(list.length, 40);
                String[] names = new String[n];
                for(int i = 0; i < n; i++) {
                    names[i] = list[i].version + (list[i].stable ? "" : "  (beta)");
                }
                new AlertDialog.Builder(activity)
                        .setTitle(utils.getName() + " · " + mc)
                        .setItems(names, (d, which) -> install(activity, utils, mc, list[which].version, callback))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }

    private static void install(final Activity activity, final FabriclikeUtils utils, final String mc, final String loaderVersion, final Callback callback) {
        Toast.makeText(activity, R.string.spirit_loader_installing, Toast.LENGTH_SHORT).show();
        MojoApplication.sExecutorService.execute(() -> {
            String id = null;
            String error = null;
            try { id = utils.install(mc, loaderVersion); } catch (IOException | RuntimeException e) { error = String.valueOf(e.getMessage()); }
            final String versionId = id, message = error;
            activity.runOnUiThread(() -> {
                if(versionId == null) {
                    Toast.makeText(activity, activity.getString(R.string.spirit_loader_failed, message == null ? "?" : message), Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(activity, activity.getString(R.string.spirit_loader_done, versionId), Toast.LENGTH_LONG).show();
                callback.onSwitched(versionId);
            });
        });
    }

    // ---------------------------------------------------------------- automatic (crash fix)

    /**
     * Installs the newest Fabric or Quilt loader for the same Minecraft version, but only if it is at least minVersion.
     * Blocking: call it off the main thread.
     * @return the new version id
     */
    public static String installNewerThan(@Nullable String currentVersionId, @Nullable String minVersion) throws IOException {
        SpiritVersionInfo info = SpiritVersionInfo.parse(resolve(currentVersionId));
        FabriclikeUtils utils;
        if(SpiritVersionInfo.FABRIC.equals(info.loader)) utils = FabriclikeUtils.FABRIC_UTILS;
        else if(SpiritVersionInfo.QUILT.equals(info.loader)) utils = FabriclikeUtils.QUILT_UTILS;
        else throw new IOException("This instance does not use Fabric or Quilt");
        if(info.minecraftVersion == null) throw new IOException("Unknown Minecraft version");
        FabricVersion[] versions = utils.downloadLoaderVersions(info.minecraftVersion);
        if(versions == null || versions.length == 0) throw new IOException("No loader versions found");
        FabricVersion pick = null;
        for(FabricVersion v : versions) {
            if(v.stable) { pick = v; break; }
        }
        if(pick == null) pick = versions[0];
        if(minVersion != null && compare(pick.version, minVersion) < 0) {
            // The newest stable one is still too old: take the newest of any kind
            pick = versions[0];
            if(compare(pick.version, minVersion) < 0) throw new IOException("No loader " + minVersion + " or later exists for " + info.minecraftVersion);
        }
        String id = utils.install(info.minecraftVersion, pick.version);
        if(id == null) throw new IOException("Could not install " + pick.version);
        return id;
    }

    /** Compares dotted versions numerically: 0.20.1 &gt; 0.19.5 */
    public static int compare(String a, String b) {
        String[] x = a.split("[.+\\-]"), y = b.split("[.+\\-]");
        for(int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? num(x[i]) : 0, yi = i < y.length ? num(y[i]) : 0;
            if(xi != yi) return Integer.compare(xi, yi);
        }
        return 0;
    }

    private static int num(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
