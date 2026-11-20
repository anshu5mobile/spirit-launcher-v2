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
import git.artdeell.mojo.crash.CrashFixer;
import git.artdeell.mojo.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Keep
public class ExitActivity extends AppCompatActivity {
    private static final int BG = 0xFF0A0C09, CARD = 0xFF141812, LIME = 0xFFA6F03A, TEXT = 0xFFEAF2E0, DIM = 0xFF9FAB96, RED = 0xFFFF6B5E;

    private LinearLayout mFindings;
    private TextView mAuto;
    private boolean mAutoRunning, mHasInstallOrUpdate;
    private List<CrashAnalyzer.Finding> mFindingList = new java.util.ArrayList<>();
    private final java.util.Map<CrashAnalyzer.Finding, TextView> mButtons = new java.util.HashMap<>();
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

        // Landscape, one screen: summary and buttons on the left, what the Crash Doctor found on the right
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);
        int pad = dp(16);
        root.setPadding(pad, dp(12), pad, dp(12));

        ScrollView leftScroll = new ScrollView(this);
        leftScroll.setFillViewport(true);
        leftScroll.setVerticalScrollBarEnabled(false);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        leftScroll.addView(left);
        root.addView(leftScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.9f));

        left.addView(text(getString(R.string.spirit_crash_badge), 12, RED, true));
        TextView title = text(getString(R.string.spirit_crash_title), 24, TEXT, true);
        title.setPadding(0, dp(2), 0, dp(2));
        left.addView(title);
        String message = isSignal ? getString(R.string.mcn_abort_title) : getString(R.string.mcn_exit_title, code);
        left.addView(text(message, 12, DIM, false));

        View grow = new View(this);
        left.addView(grow, new LinearLayout.LayoutParams(1, 0, 1f));

        mAuto = button(getString(R.string.spirit_crash_auto), true, v -> runAutoFix());
        mAuto.setVisibility(View.GONE);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        ap.topMargin = dp(10);
        left.addView(mAuto, ap);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        bp.topMargin = dp(8);
        TextView play = button(getString(R.string.spirit_crash_back), true, v -> {
            Tools.restartLauncherActivity(this);
            finish();
        });
        left.addView(play, bp);
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        rp.topMargin = dp(8);
        row1.addView(button(getString(R.string.main_share_logs), false, v -> shareLog(this)), weight());
        row1.addView(button(getString(R.string.spirit_crash_copy), false, v -> copyLog()), weight());
        left.addView(row1, rp);
        left.addView(button(getString(R.string.spirit_crash_close), false, v -> finish()), bp);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightP = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.1f);
        rightP.leftMargin = dp(16);
        root.addView(right, rightP);
        TextView doctor = text(getString(R.string.spirit_crash_doctor), 13, LIME, true);
        doctor.setPadding(0, 0, 0, dp(8));
        right.addView(doctor);
        ScrollView rightScroll = new ScrollView(this);
        rightScroll.setVerticalScrollBarEnabled(false);
        mFindings = new LinearLayout(this);
        mFindings.setOrientation(LinearLayout.VERTICAL);
        rightScroll.addView(mFindings);
        right.addView(rightScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        mFindings.addView(card(getString(R.string.spirit_crash_reading), null, null));

        setContentView(root);

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
        git.artdeell.mojo.utils.SpiritToast.makeText(this, R.string.spirit_crash_copied, Toast.LENGTH_SHORT).show();
    }

    private void showFindings(List<CrashAnalyzer.Finding> findings) {
        mFindings.removeAllViews();
        mFindingList = findings;
        mButtons.clear();
        boolean hasInstallOrUpdate = false;
        for(CrashAnalyzer.Finding f : findings) {
            if(f.fix == CrashAnalyzer.Fix.INSTALL_MOD || f.fix == CrashAnalyzer.Fix.UPDATE_MODS || f.fix == CrashAnalyzer.Fix.UPDATE_LOADER) hasInstallOrUpdate = true;
        }
        mHasInstallOrUpdate = hasInstallOrUpdate;
        boolean anyAuto = false;
        for(final CrashAnalyzer.Finding f : findings) {
            String title, detail;
            switch(f.id) {
                case "oom": title = getString(R.string.spirit_crash_oom); detail = getString(R.string.spirit_crash_oom_d); break;
                case "java_version": title = getString(R.string.spirit_crash_java); detail = getString(R.string.spirit_crash_java_d); break;
                case "storage": title = getString(R.string.spirit_crash_storage); detail = getString(R.string.spirit_crash_storage_d); break;
                case "dependency": title = getString(R.string.spirit_crash_dep); detail = getString(R.string.spirit_crash_dep_d); break;
                case "graphics": title = getString(R.string.spirit_crash_gl); detail = getString(R.string.spirit_crash_gl_d); break;
                case "loader_old": title = getString(R.string.spirit_crash_loader, f.arg); detail = getString(R.string.spirit_crash_loader_d); break;
                case "dep_missing": title = getString(R.string.spirit_crash_dep_missing, f.arg); detail = getString(R.string.spirit_crash_dep_missing_d); break;
                case "mods_old": title = getString(R.string.spirit_crash_mods_old); detail = getString(R.string.spirit_crash_mods_old_d, f.arg); break;
                case "mod": title = getString(R.string.spirit_crash_mod, f.arg); detail = getString(R.string.spirit_crash_mod_d); break;
                case "signal": title = getString(R.string.spirit_crash_signal); detail = getString(R.string.spirit_crash_signal_d); break;
                default: title = getString(R.string.spirit_crash_unknown); detail = getString(R.string.spirit_crash_unknown_d);
            }
            TextView action = null;
            switch(f.fix) {
                case MORE_RAM: action = button(getString(R.string.spirit_crash_fix_ram), true, v -> runOne(f)); break;
                case UPDATE_LOADER: action = button(getString(R.string.spirit_crash_fix_loader), true, v -> runOne(f)); break;
                case INSTALL_MOD:
                    if(mTarget != null) action = button(getString(R.string.spirit_crash_fix_install), true, v -> runOne(f));
                    break;
                case UPDATE_MODS:
                    if(mTarget != null) action = button(getString(R.string.spirit_crash_fix_update), true, v -> runOne(f));
                    break;
                case DISABLE_MOD:
                    if(mTarget != null) action = button(getString(R.string.spirit_crash_fix_mod), !hasInstallOrUpdate, v -> runOne(f));
                    break;
                case DISABLE_ALL_MODS:
                    if(mTarget != null && !mTarget.isVanilla()) action = button(getString(R.string.spirit_crash_fix_all), false, v -> runOne(f));
                    break;
                case OPEN_SETTINGS:
                    action = button(getString(R.string.spirit_crash_fix_settings), false, v -> {
                        Tools.restartLauncherActivity(this);
                        finish();
                    });
                    break;
                default: break;
            }
            if(action != null && f.fix != CrashAnalyzer.Fix.OPEN_SETTINGS) {
                mButtons.put(f, action);
                if(CrashFixer.isAutomatic(f, hasInstallOrUpdate) && (mTarget != null || f.fix == CrashAnalyzer.Fix.MORE_RAM || f.fix == CrashAnalyzer.Fix.UPDATE_LOADER)) anyAuto = true;
            }
            mFindings.addView(card(title, detail, action));
        }
        mAuto.setVisibility(anyAuto ? View.VISIBLE : View.GONE);
    }

    private void markDone(@Nullable TextView button) {
        if(button == null) return;
        button.setText(R.string.spirit_crash_fixed);
        button.setEnabled(false);
        button.setAlpha(0.6f);
    }

    /** One fix from its own button */
    private void runOne(final CrashAnalyzer.Finding f) {
        final TextView button = mButtons.get(f);
        if(button == null || !button.isEnabled()) return;
        final CharSequence label = button.getText();
        button.setEnabled(false);
        button.setText(R.string.spirit_crash_working);
        MojoApplication.sExecutorService.execute(() -> {
            String result;
            boolean ok = true;
            try {
                result = CrashFixer.apply(this, f, mTarget);
            } catch (IOException | RuntimeException e) {
                ok = false;
                result = getString(R.string.spirit_crash_failed, String.valueOf(e.getMessage()));
            }
            final String message = result;
            final boolean success = ok;
            runOnUiThread(() -> {
                git.artdeell.mojo.utils.SpiritToast.makeText(this, message, Toast.LENGTH_LONG).show();
                if(success) markDone(button);
                else { button.setEnabled(true); button.setText(label); }
            });
        });
    }

    /** "Auto fix everything": loader, missing mods, outdated mods and RAM, in that order. Mods are updated, not turned off. */
    private void runAutoFix() {
        if(mAutoRunning) return;
        mAutoRunning = true;
        mAuto.setEnabled(false);
        mAuto.setAlpha(0.7f);
        final List<CrashAnalyzer.Finding> todo = new java.util.ArrayList<>();
        for(CrashAnalyzer.Finding f : mFindingList) {
            if(mButtons.containsKey(f) && mButtons.get(f).isEnabled() && CrashFixer.isAutomatic(f, mHasInstallOrUpdate)) todo.add(f);
        }
        MojoApplication.sExecutorService.execute(() -> {
            int ok = 0;
            StringBuilder errors = new StringBuilder();
            for(final CrashAnalyzer.Finding f : todo) {
                final TextView button = mButtons.get(f);
                runOnUiThread(() -> {
                    mAuto.setText(getString(R.string.spirit_crash_auto_working, workLabel(f)));
                    button.setEnabled(false);
                    button.setText(R.string.spirit_crash_working);
                });
                try {
                    CrashFixer.apply(this, f, mTarget);
                    ok++;
                    runOnUiThread(() -> markDone(button));
                } catch (IOException | RuntimeException e) {
                    if(errors.length() > 0) errors.append('\n');
                    errors.append(e.getMessage());
                    runOnUiThread(() -> { button.setEnabled(true); button.setText(R.string.spirit_crash_retry); });
                }
            }
            final int done = ok;
            final String err = errors.toString();
            runOnUiThread(() -> {
                mAutoRunning = false;
                mAuto.setEnabled(true);
                mAuto.setAlpha(1f);
                if(err.isEmpty()) {
                    mAuto.setText(R.string.spirit_crash_auto_done);
                    git.artdeell.mojo.utils.SpiritToast.makeText(this, R.string.spirit_crash_auto_ok, Toast.LENGTH_LONG).show();
                } else {
                    mAuto.setText(R.string.spirit_crash_auto);
                    git.artdeell.mojo.utils.SpiritToast.makeText(this, getString(R.string.spirit_crash_auto_partial, done, err), Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private String workLabel(CrashAnalyzer.Finding f) {
        switch(f.fix) {
            case UPDATE_LOADER: return getString(R.string.spirit_crash_w_loader);
            case INSTALL_MOD: return f.arg;
            case UPDATE_MODS: return getString(R.string.spirit_crash_w_mods);
            case MORE_RAM: return "RAM";
            default: return "";
        }
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
