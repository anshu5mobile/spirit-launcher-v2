package git.artdeell.mojo;

import static git.artdeell.mojo.Tools.shareLog;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import git.artdeell.mojo.browse.BrowseTarget;
import git.artdeell.mojo.crash.CrashAnalyzer;
import git.artdeell.mojo.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Keep
public class ExitActivity extends AppCompatActivity {
    private static final int BG = 0xFF0A0C09, CARD = 0xFF141812, LIME = 0xFFA6F03A, TEXT = 0xFFEAF2E0, DIM = 0xFF9FAB96, RED = 0xFFFF6B5E;

    private LinearLayout mFindings;
    private String mLogText = "";
    @Nullable private BrowseTarget mTarget;

    @SuppressLint("StringFormatInvalid") //invalid on some translations but valid on most, cant fix that atm
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int code = -1;
        boolean isSignal = false;
        Bundle extras = getIntent().getExtras();
        if(extras != null) {
            code = extras.getInt("code",-1);
            isSignal = extras.getBoolean("isSignal", false);
        }
        final boolean signal = isSignal;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, dp(28), pad, pad);
        scroll.addView(root);

        TextView badge = text(getString(R.string.spirit_crash_badge), 12, RED, true);
        root.addView(badge);
        TextView title = text(getString(R.string.spirit_crash_title), 24, TEXT, true);
        title.setPadding(0, dp(4), 0, dp(4));
        root.addView(title);
        String message = isSignal ? getString(R.string.mcn_abort_title) : getString(R.string.mcn_exit_title, code);
        root.addView(text(message, 13, DIM, false));

        TextView doctor = text(getString(R.string.spirit_crash_doctor), 13, LIME, true);
        doctor.setPadding(0, dp(20), 0, dp(8));
        root.addView(doctor);
        mFindings = new LinearLayout(this);
        mFindings.setOrientation(LinearLayout.VERTICAL);
        root.addView(mFindings);
        mFindings.addView(card(getString(R.string.spirit_crash_reading), null, null));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(16);
        row1.setLayoutParams(rp);
        row1.addView(button(getString(R.string.main_share_logs), false, v -> shareLog(this)), weight());
        row1.addView(button(getString(R.string.spirit_crash_copy), false, v -> copyLog()), weight());
        root.addView(row1);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        pp.topMargin = dp(8);
        TextView play = button(getString(R.string.spirit_crash_back), true, v -> {
            Tools.restartLauncherActivity(this);
            finish();
        });
        root.addView(play, pp);
        TextView close = button(getString(R.string.spirit_crash_close), false, v -> finish());
        root.addView(close, pp);

        setContentView(scroll);

        MojoApplication.sExecutorService.execute(() -> {
            String log = readTail(new File(Tools.DIR_GAME_HOME, "latestlog.txt"), 600 * 1024);
            try { mTarget = BrowseTarget.forSelectedInstance(); } catch (Throwable ignored) { /* no instance: fixes that need one are hidden */ }
            if(mTarget != null) {
                File reports = new File(mTarget.gameDir, "crash-reports");
                File[] list = reports.listFiles();
                if(list != null && list.length > 0) {
                    File newest = list[0];
                    for(File f : list) if(f.lastModified() > newest.lastModified()) newest = f;
                    if(System.currentTimeMillis() - newest.lastModified() < 10 * 60 * 1000L) {
                        log = log + "\n" + readTail(newest, 200 * 1024);
                    }
                }
            }
            mLogText = log;
            final List<CrashAnalyzer.Finding> findings = CrashAnalyzer.analyze(log, signal);
            runOnUiThread(() -> showFindings(findings));
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
        p.rightMargin = dp(4);
        p.leftMargin = dp(4);
        return p;
    }

    private TextView text(String t, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
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

    private TextView button(String label, boolean primary, View.OnClickListener l) {
        TextView t = text(label, 14, primary ? 0xFF0A0C09 : TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(primary ? LIME : CARD, primary ? LIME : 0xFF2A3026, 26));
        t.setOnClickListener(l);
        return t;
    }

    private View card(String title, @Nullable String detail, @Nullable View action) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(CARD, 0xFF2A3026, 16));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        c.addView(text(title, 15, TEXT, true));
        if(detail != null) {
            TextView d = text(detail, 13, DIM, false);
            d.setPadding(0, dp(4), 0, 0);
            c.addView(d);
        }
        if(action != null) {
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            ap.topMargin = dp(10);
            action.setLayoutParams(ap);
            action.setPadding(dp(18), 0, dp(18), 0);
            c.addView(action);
        }
        return c;
    }

    private static String readTail(File file, int max) {
        if(!file.isFile()) return "";
        try(java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            long len = raf.length();
            long start = Math.max(0, len - max);
            raf.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            raf.readFully(buf);
            return new String(buf, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private void copyLog() {
        ClipboardManager mgr = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        String tail = mLogText.length() > 100000 ? mLogText.substring(mLogText.length() - 100000) : mLogText;
        if(mgr != null) mgr.setPrimaryClip(ClipData.newPlainText("log", tail));
        Toast.makeText(this, R.string.spirit_crash_copied, Toast.LENGTH_SHORT).show();
    }

    private void showFindings(List<CrashAnalyzer.Finding> findings) {
        mFindings.removeAllViews();
        final List<View> fixers = new java.util.ArrayList<>();
        for(CrashAnalyzer.Finding f : findings) {
            String title, detail;
            switch(f.id) {
                case "oom": title = getString(R.string.spirit_crash_oom); detail = getString(R.string.spirit_crash_oom_d); break;
                case "java_version": title = getString(R.string.spirit_crash_java); detail = getString(R.string.spirit_crash_java_d); break;
                case "storage": title = getString(R.string.spirit_crash_storage); detail = getString(R.string.spirit_crash_storage_d); break;
                case "dependency": title = getString(R.string.spirit_crash_dep); detail = getString(R.string.spirit_crash_dep_d); break;
                case "graphics": title = getString(R.string.spirit_crash_gl); detail = getString(R.string.spirit_crash_gl_d); break;
                case "loader_old": title = getString(R.string.spirit_crash_loader, f.arg); detail = getString(R.string.spirit_crash_loader_d); break;
                case "mod": title = getString(R.string.spirit_crash_mod, f.arg); detail = getString(R.string.spirit_crash_mod_d); break;
                case "signal": title = getString(R.string.spirit_crash_signal); detail = getString(R.string.spirit_crash_signal_d); break;
                default: title = getString(R.string.spirit_crash_unknown); detail = getString(R.string.spirit_crash_unknown_d);
            }
            View action = null;
            switch(f.fix) {
                case MORE_RAM: action = button(getString(R.string.spirit_crash_fix_ram), true, v -> fixRam((TextView) v)); break;
                case UPDATE_LOADER:
                    action = button(getString(R.string.spirit_crash_fix_loader), true, v -> fixLoader(f.arg, (TextView) v));
                    break;
                case DISABLE_MOD:
                    if(mTarget != null) action = button(getString(R.string.spirit_crash_fix_mod), true, v -> fixMod(f.arg, (TextView) v));
                    break;
                case DISABLE_ALL_MODS:
                    if(mTarget != null && !mTarget.isVanilla()) action = button(getString(R.string.spirit_crash_fix_all), false, v -> fixAllMods((TextView) v));
                    break;
                case OPEN_SETTINGS:
                    action = button(getString(R.string.spirit_crash_fix_settings), false, v -> {
                        Tools.restartLauncherActivity(this);
                        finish();
                    });
                    break;
                default: break;
            }
            if(action != null && f.fix != CrashAnalyzer.Fix.OPEN_SETTINGS) fixers.add(action);
            mFindings.addView(card(title, detail, action));
        }
        if(!fixers.isEmpty()) {
            // One tap runs every fix found above
            TextView all = button(getString(R.string.spirit_crash_auto), true, v -> {
                for(View fixer : fixers) if(fixer.isEnabled()) fixer.performClick();
                done((TextView) v, R.string.spirit_crash_auto_done);
            });
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            ap.bottomMargin = dp(8);
            mFindings.addView(all, 0, ap);
        }
    }

    private void done(TextView button, int labelRes) {
        button.setText(labelRes);
        button.setEnabled(false);
        button.setAlpha(0.6f);
    }

    private void fixLoader(final String minVersion, final TextView button) {
        button.setEnabled(false);
        button.setText(R.string.spirit_crash_loader_working);
        MojoApplication.sExecutorService.execute(() -> {
            String message;
            boolean ok = false;
            try {
                git.artdeell.mojo.instances.Instance instance = git.artdeell.mojo.instances.Instances.loadSelectedInstance();
                if(instance == null) throw new java.io.IOException("No instance selected");
                String id = git.artdeell.mojo.modding.LoaderSwitcher.installNewerThan(instance.versionId, minVersion);
                instance.versionId = id;
                instance.write();
                message = getString(R.string.spirit_crash_loader_done, id);
                ok = true;
            } catch (java.io.IOException | RuntimeException e) {
                message = getString(R.string.spirit_crash_loader_failed, String.valueOf(e.getMessage()));
            }
            final String m = message;
            final boolean success = ok;
            runOnUiThread(() -> {
                Toast.makeText(this, m, Toast.LENGTH_LONG).show();
                if(success) done(button, R.string.spirit_crash_fixed);
                else { button.setEnabled(true); button.setText(R.string.spirit_crash_fix_loader); }
            });
        });
    }

    private void fixRam(TextView button) {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            int totalMb = (int) (mi.totalMem / (1024 * 1024));
            int cap = (int) (totalMb * 0.65f);
            int current = LauncherPreferences.DEFAULT_PREF.getInt("allocation", 1024);
            int next = Math.min(cap, current + 1024);
            if(next <= current) {
                Toast.makeText(this, R.string.spirit_crash_ram_max, Toast.LENGTH_LONG).show();
                return;
            }
            LauncherPreferences.DEFAULT_PREF.edit().putInt("allocation", next).apply();
            Toast.makeText(this, getString(R.string.spirit_crash_ram_done, next), Toast.LENGTH_LONG).show();
            done(button, R.string.spirit_crash_fixed);
        } catch (RuntimeException e) {
            Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void fixMod(String modId, TextView button) {
        final BrowseTarget target = mTarget;
        if(target == null) return;
        MojoApplication.sExecutorService.execute(() -> {
            int renamed = 0;
            File[] jars = new File(target.gameDir, "mods").listFiles((d, n) -> n.endsWith(".jar"));
            String key = CrashAnalyzer.squash(modId);
            if(jars != null && !key.isEmpty()) {
                for(File jar : jars) {
                    if(CrashAnalyzer.squash(jar.getName()).contains(key)
                            && jar.renameTo(new File(jar.getParentFile(), jar.getName() + ".disabled"))) renamed++;
                }
            }
            final int count = renamed;
            runOnUiThread(() -> {
                if(count > 0) {
                    Toast.makeText(this, getString(R.string.spirit_crash_mod_done, count), Toast.LENGTH_LONG).show();
                    done(button, R.string.spirit_crash_fixed);
                } else {
                    Toast.makeText(this, R.string.spirit_crash_mod_notfound, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void fixAllMods(TextView button) {
        final BrowseTarget target = mTarget;
        if(target == null) return;
        MojoApplication.sExecutorService.execute(() -> {
            int renamed = 0;
            File[] jars = new File(target.gameDir, "mods").listFiles((d, n) -> n.endsWith(".jar"));
            if(jars != null) for(File jar : jars) {
                if(jar.renameTo(new File(jar.getParentFile(), jar.getName() + ".disabled"))) renamed++;
            }
            final int count = renamed;
            runOnUiThread(() -> {
                Toast.makeText(this, getString(R.string.spirit_crash_mod_done, count), Toast.LENGTH_LONG).show();
                if(count > 0) done(button, R.string.spirit_crash_fixed);
            });
        });
    }

    @SuppressWarnings("unused") //used by native jre_launcher_new
    public static void showExitMessage(Context ctx, int code, boolean isSignal) {
        if((!isSignal && code == 0)) {
            if(ctx != null) Tools.restartLauncherActivity(ctx);
            System.exit(0);
            return;
        }

        Object lock = new Object();
        Tools.runOnUiThread(()->{
            Intent i = new Intent(ctx,ExitActivity.class);
            i.putExtra("code",code);
            i.putExtra("isSignal", isSignal);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            synchronized (lock) {
                lock.notify();
            }
        });
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                Log.e("ExitActivity", "Waiting on lock failed: "+e);
            }
        }
        System.exit(0);
    }

}
