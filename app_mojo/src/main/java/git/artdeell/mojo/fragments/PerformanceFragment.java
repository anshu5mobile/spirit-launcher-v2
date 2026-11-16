package git.artdeell.mojo.fragments;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.browse.BrowseInstaller;
import git.artdeell.mojo.browse.BrowseKind;
import git.artdeell.mojo.browse.BrowseTarget;
import git.artdeell.mojo.browse.ModrinthClient;
import git.artdeell.mojo.perf.DeviceInfo;
import git.artdeell.mojo.perf.OptimizationPack;
import git.artdeell.mojo.perf.OptionsFile;
import git.artdeell.mojo.perf.PerfProfile;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.utils.SpiritScreen;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Performance Center: reads the phone, recommends a profile, applies it to the selected instance and can install
 * the optimization mods that fit that instance. Everything it changes is listed to the player and can be restored.
 */
public class PerformanceFragment extends Fragment {
    public static final String TAG = "SpiritPerformanceFragment";

    private static final int BG = 0xFF0A0C09, CARD = 0xFF141812, LIME = 0xFFA6F03A, TEXT = 0xFFEAF2E0, DIM = 0xFF9FAB96, WARN = 0xFFFFC857;

    private LinearLayout mBody;
    @Nullable private DeviceInfo mDevice;
    @Nullable private BrowseTarget mTarget;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(requireContext());
        back.setId(R.id.spirit_back);
        back.setImageResource(R.drawable.ic_spirit_back);
        back.setColorFilter(TEXT);
        back.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(44), dp(44));
        bp.leftMargin = -dp(10);
        header.addView(back, bp);
        header.addView(text(getString(R.string.spirit_perf_title), 22, TEXT, true));
        root.addView(header);

        mBody = new LinearLayout(requireContext());
        mBody.setOrientation(LinearLayout.VERTICAL);
        root.addView(mBody);
        SpiritScreen.bindBack(root, requireActivity());
        return scroll;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mBody.addView(text(getString(R.string.spirit_perf_reading), 13, DIM, false));
        final android.content.Context app = requireContext().getApplicationContext();
        MojoApplication.sExecutorService.execute(() -> {
            final DeviceInfo info = DeviceInfo.read(app);
            BrowseTarget target = null;
            try { target = BrowseTarget.forSelectedInstance(); } catch (Throwable ignored) { /* no instance selected */ }
            final BrowseTarget t = target;
            Tools.runOnUiThread(() -> {
                if(!isAdded()) return;
                mDevice = info;
                mTarget = t;
                render();
            });
        });
    }

    // ---------------------------------------------------------------- ui helpers

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String t, int sp, int color, boolean bold) {
        TextView v = new TextView(requireContext());
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        if(bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(requireContext());
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(CARD, 0xFF2A3026, 18));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);
        if(title != null) {
            TextView t = text(title, 12, LIME, true);
            t.setPadding(0, 0, 0, dp(6));
            c.addView(t);
        }
        return c;
    }

    private TextView button(String label, boolean primary, View.OnClickListener l) {
        TextView t = text(label, 14, primary ? 0xFF0A0C09 : TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(primary ? LIME : 0xFF1B2018, primary ? LIME : 0xFF2A3026, 22));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void line(LinearLayout card, String label, String value) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setPadding(0, dp(3), 0, dp(3));
        TextView l = text(label, 13, DIM, false);
        TextView v = text(value, 13, TEXT, false);
        v.setGravity(Gravity.END);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f));
        row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        card.addView(row);
    }

    // ---------------------------------------------------------------- screen

    private String tierName(DeviceInfo.Tier t) {
        switch(t) {
            case LOW: return getString(R.string.spirit_perf_tier_low);
            case ENTRY: return getString(R.string.spirit_perf_tier_entry);
            case MID: return getString(R.string.spirit_perf_tier_mid);
            default: return getString(R.string.spirit_perf_tier_high);
        }
    }

    private PerfProfile recommended(DeviceInfo d) {
        switch(d.tier) {
            case LOW: return PerfProfile.POTATO;
            case ENTRY: return PerfProfile.PERFORMANCE;
            case MID: return PerfProfile.BALANCED;
            default: return PerfProfile.QUALITY;
        }
    }

    private String profileName(PerfProfile p) {
        switch(p) {
            case POTATO: return getString(R.string.spirit_perf_potato);
            case BALANCED: return getString(R.string.spirit_perf_balanced);
            case PERFORMANCE: return getString(R.string.spirit_perf_performance);
            default: return getString(R.string.spirit_perf_quality);
        }
    }

    private String profileDesc(PerfProfile p) {
        switch(p) {
            case POTATO: return getString(R.string.spirit_perf_potato_d);
            case BALANCED: return getString(R.string.spirit_perf_balanced_d);
            case PERFORMANCE: return getString(R.string.spirit_perf_performance_d);
            default: return getString(R.string.spirit_perf_quality_d);
        }
    }

    private void render() {
        if(!isAdded() || mDevice == null) return;
        final DeviceInfo d = mDevice;
        mBody.removeAllViews();
        final PerfProfile rec = recommended(d);

        // --- device
        LinearLayout dev = card(getString(R.string.spirit_perf_device));
        TextView tier = text(tierName(d.tier), 22, TEXT, true);
        dev.addView(tier);
        line(dev, getString(R.string.spirit_perf_soc), d.soc + " · " + d.abi);
        line(dev, getString(R.string.spirit_perf_cpu), d.cores + " cores" + (d.maxMhz > 0 ? String.format(Locale.US, " · up to %.1f GHz", d.maxMhz / 1000f) : ""));
        line(dev, getString(R.string.spirit_perf_ram), String.format(Locale.US, "%.1f GB total · %.1f GB free", d.totalRamMb / 1024f, d.freeRamMb / 1024f));
        line(dev, getString(R.string.spirit_perf_screen), d.widthPx + "x" + d.heightPx + " · " + Math.round(d.refreshHz) + " Hz");
        line(dev, "Android", String.valueOf(d.sdk));
        line(dev, getString(R.string.spirit_perf_storage), d.freeStorageMb < 0 ? "?" : String.format(Locale.US, "%.1f GB free", d.freeStorageMb / 1024f));
        if(d.freeStorageMb >= 0 && d.freeStorageMb < 2048) {
            TextView warn = text(getString(R.string.spirit_perf_low_storage), 12, WARN, false);
            warn.setPadding(0, dp(6), 0, 0);
            dev.addView(warn);
        }
        TextView note = text(getString(R.string.spirit_perf_gpu_note), 11, DIM, false);
        note.setPadding(0, dp(8), 0, 0);
        dev.addView(note);
        mBody.addView(dev);

        // --- quick optimize
        LinearLayout quick = card(getString(R.string.spirit_perf_recommended));
        quick.addView(text(profileName(rec), 20, TEXT, true));
        quick.addView(text(profileDesc(rec), 13, DIM, false));
        quick.addView(button(getString(R.string.spirit_perf_quick), true, v -> confirmApply(rec)));
        mBody.addView(quick);

        // --- profiles
        LinearLayout profiles = card(getString(R.string.spirit_perf_profiles));
        for(final PerfProfile p : PerfProfile.values()) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(4));
            row.addView(text(profileName(p) + (p == rec ? "  ★" : ""), 15, TEXT, true));
            row.addView(text(profileDesc(p), 12, DIM, false));
            row.addView(button(getString(R.string.spirit_perf_apply), false, v -> confirmApply(p)));
            profiles.addView(row);
        }
        TextView custom = text(getString(R.string.spirit_perf_custom), 12, DIM, false);
        custom.setPadding(0, dp(8), 0, 0);
        profiles.addView(custom);
        mBody.addView(profiles);

        // --- current setup
        LinearLayout cur = card(getString(R.string.spirit_perf_current));
        int alloc = LauncherPreferences.PREF_RAM_ALLOCATION;
        line(cur, getString(R.string.spirit_perf_ram_alloc), alloc + " MB  (" + getString(R.string.spirit_perf_recommended_short, d.recommendedRamMb()) + ")");
        if(alloc > d.totalRamMb * 0.55) {
            TextView warn = text(getString(R.string.spirit_perf_ram_warn), 12, WARN, false);
            warn.setPadding(0, dp(4), 0, dp(4));
            cur.addView(warn);
        }
        line(cur, getString(R.string.spirit_perf_renderer), LauncherPreferences.PREF_RENDERER);
        line(cur, getString(R.string.spirit_perf_scale), Math.round(LauncherPreferences.PREF_SCALE_FACTOR * 100) + " %");
        line(cur, getString(R.string.spirit_perf_instance), mTarget != null ? mTarget.instanceName + (mTarget.mcVersion != null ? " · " + mTarget.mcVersion : "") : "-");
        mBody.addView(cur);

        // --- mods
        LinearLayout mods = card(getString(R.string.spirit_perf_mods));
        mods.addView(text(getString(R.string.spirit_perf_mods_d), 12, DIM, false));
        mods.addView(button(getString(R.string.spirit_perf_mods_install), false, v -> openPack()));
        mBody.addView(mods);

        // --- tips
        LinearLayout tips = card(getString(R.string.spirit_perf_tips));
        tips.addView(text(getString(R.string.spirit_perf_tips_body), 13, TEXT, false));
        mBody.addView(tips);

        // --- restore
        LinearLayout restore = card(getString(R.string.spirit_perf_restore_title));
        restore.addView(text(getString(R.string.spirit_perf_restore_d), 12, DIM, false));
        restore.addView(button(getString(R.string.spirit_perf_restore), false, v -> confirmRestore()));
        mBody.addView(restore);
    }

    // ---------------------------------------------------------------- apply

    private void confirmApply(final PerfProfile profile) {
        final DeviceInfo d = mDevice;
        if(d == null) return;
        if(mTarget == null) {
            Toast.makeText(requireContext(), R.string.spirit_browse_no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        String msg = getString(R.string.spirit_perf_confirm, mTarget.instanceName, profile.ramMb(d), profile.resolutionPercent(d));
        new AlertDialog.Builder(requireContext())
                .setTitle(profileName(profile))
                .setMessage(msg)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.spirit_perf_apply, (dialog, w) -> apply(profile))
                .show();
    }

    private void apply(final PerfProfile profile) {
        final DeviceInfo d = mDevice;
        final BrowseTarget target = mTarget;
        if(d == null || target == null) return;
        final android.content.Context app = requireContext().getApplicationContext();
        MojoApplication.sExecutorService.execute(() -> {
            List<String> changes;
            try {
                changes = OptionsFile.apply(new File(target.gameDir, "options.txt"), profile.options(d));
            } catch (IOException e) {
                Tools.runOnUiThread(() -> { if(isAdded()) new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.spirit_perf_title)
                        .setMessage(R.string.spirit_perf_no_options)
                        .setPositiveButton(android.R.string.ok, null).show(); });
                return;
            }
            int ram = profile.ramMb(d);
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putInt("allocation", ram)
                    .putInt("resolutionRatio", profile.resolutionPercent(d))
                    .apply();
            LauncherPreferences.loadPreferences(app);
            final String summary = changes.isEmpty() ? getString(R.string.spirit_perf_nothing)
                    : android.text.TextUtils.join("\n", changes);
            Tools.runOnUiThread(() -> {
                if(!isAdded()) return;
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.spirit_perf_applied)
                        .setMessage(getString(R.string.spirit_perf_applied_d, profile.ramMb(d), profile.resolutionPercent(d)) + "\n\n" + summary)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                render();
            });
        });
    }

    private void confirmRestore() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.spirit_perf_restore_title)
                .setMessage(R.string.spirit_perf_restore_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.spirit_perf_restore, (dialog, w) -> restore())
                .show();
    }

    private void restore() {
        final BrowseTarget target = mTarget;
        final android.content.Context app = requireContext().getApplicationContext();
        MojoApplication.sExecutorService.execute(() -> {
            boolean optionsBack = false;
            if(target != null) {
                File backup = new File(target.gameDir, "options.txt.spirit-backup");
                File options = new File(target.gameDir, "options.txt");
                if(backup.isFile()) {
                    try {
                        java.nio.file.Files.copy(backup.toPath(), options.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        optionsBack = backup.delete() || true;
                    } catch (IOException | RuntimeException ignored) { /* keep the current file */ }
                }
            }
            LauncherPreferences.DEFAULT_PREF.edit().remove("allocation").remove("resolutionRatio").remove("javaArgs").apply();
            LauncherPreferences.loadPreferences(app);
            final boolean restored = optionsBack;
            Tools.runOnUiThread(() -> {
                if(!isAdded()) return;
                Toast.makeText(requireContext(), restored ? R.string.spirit_perf_restored_all : R.string.spirit_perf_restored, Toast.LENGTH_LONG).show();
                render();
            });
        });
    }

    // ---------------------------------------------------------------- optimization mods

    private void openPack() {
        final BrowseTarget target = mTarget;
        if(target == null) {
            Toast.makeText(requireContext(), R.string.spirit_browse_no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        if(target.isVanilla() || target.mcVersion == null) {
            Toast.makeText(requireContext(), R.string.spirit_perf_mods_vanilla, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(requireContext(), R.string.spirit_perf_mods_checking, Toast.LENGTH_SHORT).show();
        MojoApplication.sExecutorService.execute(() -> {
            final List<OptimizationPack.Entry> entries = new ArrayList<>();
            final List<ModrinthClient.Version> versions = new ArrayList<>();
            File[] installed = new File(target.gameDir, "mods").listFiles((dir, n) -> n.endsWith(".jar"));
            for(OptimizationPack.Entry e : OptimizationPack.forLoaders(target.loaders)) {
                String key = e.slug.replaceAll("[^a-z0-9]", "");
                boolean have = false;
                if(installed != null) for(File f : installed) {
                    if(f.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").contains(key)) have = true;
                }
                if(have) continue;
                try {
                    List<ModrinthClient.Version> v = ModrinthClient.versions(e.slug, target.mcVersion, target.loaders);
                    if(!v.isEmpty()) { entries.add(e); versions.add(v.get(0)); }
                } catch (IOException | RuntimeException ignored) {
                    // Not available for this version: simply not offered
                }
            }
            Tools.runOnUiThread(() -> { if(isAdded()) showPack(target, entries, versions); });
        });
    }

    private void showPack(final BrowseTarget target, final List<OptimizationPack.Entry> entries, final List<ModrinthClient.Version> versions) {
        if(entries.isEmpty()) {
            Toast.makeText(requireContext(), R.string.spirit_perf_mods_none, Toast.LENGTH_LONG).show();
            return;
        }
        final boolean modernGl = OptimizationPack.rendererSupportsModernGl(LauncherPreferences.PREF_RENDERER);
        final String[] labels = new String[entries.size()];
        final boolean[] checked = new boolean[entries.size()];
        for(int i = 0; i < labels.length; i++) {
            OptimizationPack.Entry e = entries.get(i);
            labels[i] = e.slug + " - " + e.what + (e.needsModernGl && !modernGl ? "  (needs Zink)" : "");
            checked[i] = !e.needsModernGl || modernGl;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.spirit_perf_mods)
                .setMultiChoiceItems(labels, checked, (d, which, on) -> checked[which] = on)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.spirit_perf_install, (d, w) -> {
                    List<ModrinthClient.Version> chosen = new ArrayList<>();
                    for(int i = 0; i < checked.length; i++) if(checked[i]) chosen.add(versions.get(i));
                    installPack(target, chosen);
                })
                .show();
    }

    private void installPack(final BrowseTarget target, final List<ModrinthClient.Version> chosen) {
        if(chosen.isEmpty()) return;
        if(!ProgressLayout.ensureNoOngoingTasks(requireContext())) return;
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        final android.content.Context app = requireContext().getApplicationContext();
        MojoApplication.sExecutorService.execute(() -> {
            int added = 0;
            try {
                for(ModrinthClient.Version v : chosen) {
                    added += BrowseInstaller.install(target, BrowseKind.MODS, v).added.size();
                }
            } catch (IOException | RuntimeException e) {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                Tools.showErrorRemote(app, R.string.spirit_browse_install_failed, e);
                return;
            }
            final int count = added;
            Tools.runOnUiThread(() -> Toast.makeText(app, app.getString(R.string.spirit_perf_mods_done, count), Toast.LENGTH_LONG).show());
        });
    }
}
