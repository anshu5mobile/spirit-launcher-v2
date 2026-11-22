package git.artdeell.mojo.prefs.screens;

import android.Manifest;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;

import git.artdeell.mojo.LauncherActivity;
import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.tasks.DataMigrator;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class LauncherPreferenceMiscellaneousFragment extends LauncherPreferenceFragment {

    private final ActivityResultLauncher<Uri> mMigrateLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), (uri) -> {
                if(uri != null) {
                    new AlertDialog.Builder(getLauncherActivity())
                            .setTitle(R.string.migration_progress_warning_title)
                            .setMessage(R.string.migration_progress_warning_summary)
                            .setPositiveButton(android.R.string.ok, (d, w) -> new DataMigrator(getLauncherActivity(), uri).migrateData())
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                }
            }
    );

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        mVisibilityUpdater = this::updateVisibility;
        addPreferencesFromResource(R.xml.pref_misc);
        Preference importPreference = requirePreference("runDataMigration");
        importPreference.setOnPreferenceClickListener(preference -> {
            if(!ProgressLayout.ensureNoOngoingTasks(getContext())) return true;
            mMigrateLauncher.launch(null);
            return true;
        });
        setupCacheClearPreference();
        setupMicrophoneRequestPreference();
        requirePreference("spiritCleanup").setOnPreferenceClickListener(preference -> { askCleanup(); return true; });
        requirePreference("spiritOwnNametag").setOnPreferenceClickListener(preference -> { installOwnNametag(); return true; });
        updateVisibility();
    }

    /** Installs the Third Person Nametags mod (and its dependencies) into the selected Fabric instance. */
    private void installOwnNametag() {
        final android.content.Context ctx = getContext();
        if(ctx == null) return;
        git.artdeell.mojo.utils.SpiritToast.makeText(ctx, R.string.spirit_own_nametag_working, Toast.LENGTH_SHORT).show();
        MojoApplication.sExecutorService.submit(() -> {
            String message;
            try {
                git.artdeell.mojo.browse.BrowseTarget target = git.artdeell.mojo.browse.BrowseTarget.forSelectedInstance();
                if(target == null || !(target.loaders.contains("fabric") || target.loaders.contains("quilt"))) {
                    message = ctx.getString(R.string.spirit_own_nametag_fabric_only);
                } else {
                    String file = git.artdeell.mojo.crash.CrashFixer.installMod(target, "third-person-nametags");
                    message = ctx.getString(R.string.spirit_own_nametag_done, file);
                }
            } catch (Exception e) {
                message = ctx.getString(R.string.spirit_own_nametag_fail, e.getMessage());
            }
            final String shown = message;
            Tools.runOnUiThread(() -> git.artdeell.mojo.utils.SpiritToast.makeText(ctx, shown, Toast.LENGTH_LONG).show());
        });
    }

    private void updateVisibility(){
        requirePreference("microphoneAccessRequest").setVisible(!getLauncherActivity().checkForPermissionRationale(33, Manifest.permission.RECORD_AUDIO));
        requirePreference("clearMetadataCache").setVisible(new File(Tools.DIR_CACHE, "string_cache").exists());
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void setupMicrophoneRequestPreference() {
        Preference mRequestMicrophonePermissionPreference = requirePreference("microphoneAccessRequest");
        Activity activity = getActivity();
        if(activity instanceof LauncherActivity) {
            mRequestMicrophonePermissionPreference.setOnPreferenceClickListener(preference -> {
                ((LauncherActivity) activity).askForPermission(23, Manifest.permission.RECORD_AUDIO);
                return true;
            });
        } else {
            mRequestMicrophonePermissionPreference.setVisible(false);
        }
    }
    private void setupCacheClearPreference() {
        Preference clearPreference = requirePreference("clearMetadataCache");
        clearPreference.setOnPreferenceClickListener(preference -> {
            if(!ProgressLayout.ensureNoOngoingTasks(getContext())) return true;
            MojoApplication.sExecutorService.submit(() -> {
                try {
                    FileUtils.deleteDirectory(new File(Tools.DIR_CACHE, "string_cache"));
                } catch (IOException e) {
                    Tools.showErrorRemote(getLauncherActivity(), R.string.preference_metadata_clear_fail, e);
                    return;
                }
                Tools.runOnUiThread(() -> {
                    git.artdeell.mojo.utils.SpiritToast.makeText(getLauncherActivity(), R.string.preference_metadata_clear_complete, Toast.LENGTH_LONG).show();
                    updateVisibility();
                });
            });
            return true;
        });
    }

    // ---- Clean temporary files: downloaded plugin installers, logs and crash reports of the selected instance

    private java.util.List<File> cleanupTargets() {
        java.util.List<File> out = new java.util.ArrayList<>();
        out.add(new File(requireContext().getCacheDir(), "plugins"));
        try {
            git.artdeell.mojo.browse.BrowseTarget target = git.artdeell.mojo.browse.BrowseTarget.forSelectedInstance();
            if(target != null) {
                out.add(new File(target.gameDir, "logs"));
                out.add(new File(target.gameDir, "crash-reports"));
            }
        } catch (Throwable ignored) { /* no instance selected */ }
        return out;
    }

    private static long sizeOf(File f) {
        if(f == null || !f.exists()) return 0;
        if(f.isFile()) return f.length();
        long total = 0;
        File[] kids = f.listFiles();
        if(kids != null) for(File k : kids) total += sizeOf(k);
        return total;
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if(kids != null) for(File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static String human(long bytes) {
        if(bytes >= 1048576L) return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576f);
        return Math.max(1, bytes / 1024) + " KB";
    }

    private void askCleanup() {
        final android.content.Context app = requireContext().getApplicationContext();
        final java.util.List<File> targets = cleanupTargets();
        git.artdeell.mojo.MojoApplication.sExecutorService.execute(() -> {
            long total = 0;
            for(File t : targets) total += sizeOf(t);
            final long size = total;
            Tools.runOnUiThread(() -> {
                if(!isAdded()) return;
                if(size <= 0) {
                    git.artdeell.mojo.utils.SpiritToast.makeText(app, R.string.spirit_cleanup_nothing, android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.spirit_cleanup_title)
                        .setMessage(getString(R.string.spirit_cleanup_confirm, human(size)))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (d, w) -> git.artdeell.mojo.MojoApplication.sExecutorService.execute(() -> {
                            for(File t : targets) if(t.exists()) deleteTree(t);
                            Tools.runOnUiThread(() -> git.artdeell.mojo.utils.SpiritToast.makeText(app,
                                    app.getString(R.string.spirit_cleanup_done, human(size)), android.widget.Toast.LENGTH_LONG).show());
                        }))
                        .show();
            });
        });
    }
}
