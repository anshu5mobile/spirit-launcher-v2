package git.artdeell.mojo.crash;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.browse.BrowseTarget;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fabric shows its "Incompatible mods found!" window with Java Swing, grey and hard to use on a phone. While the
 * game starts this watches the log and, when mods do not match, shows a Spirit dialog with an Auto fix button.
 * It only reads the log file every few seconds during start up, so it costs nothing while playing.
 */
public final class GameErrorWatcher {
    private static final int CARD = 0xFF141812, LIME = 0xFFA6F03A, TEXT = 0xFFEAF2E0, DIM = 0xFF9FAB96;
    private static final long WATCH_MS = 180_000L, EVERY_MS = 2500L;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static volatile boolean sShown;
    private static volatile int sGeneration;

    private GameErrorWatcher() {}

    public static void start(final Activity activity) {
        final int generation = ++sGeneration;
        sShown = false;
        final long begin = SystemClock.uptimeMillis();
        HANDLER.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(generation != sGeneration || sShown || activity.isFinishing()) return;
                if(SystemClock.uptimeMillis() - begin > WATCH_MS) return;
                final Runnable self = this;
                MojoApplication.sExecutorService.execute(() -> {
                    String tail = readTail(new File(Tools.DIR_GAME_HOME, "latestlog.txt"), 200 * 1024);
                    if(isModResolutionFailure(tail)) {
                        sShown = true;
                        final List<CrashAnalyzer.Finding> findings = CrashAnalyzer.analyze(tail, false);
                        HANDLER.post(() -> { if(!activity.isFinishing()) show(activity, findings); });
                    } else {
                        HANDLER.postDelayed(self, EVERY_MS);
                    }
                });
            }
        }, EVERY_MS);
    }

    static boolean isModResolutionFailure(String log) {
        return log.contains("Some of your mods are incompatible") || log.contains("Incompatible mod set!")
                || log.contains("Mod resolution failed") || log.contains("A mod crashed on startup!")
                || log.contains("failed during PREPARE") || log.contains("InvalidMixinException");
    }

    private static String readTail(File file, int max) {
        if(!file.isFile()) return "";
        try(RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long len = raf.length();
            long start = Math.max(0, len - max);
            raf.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            raf.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static int dp(Activity a, int v) {
        return (int) (v * a.getResources().getDisplayMetrics().density);
    }

    private static TextView text(Activity a, String t, int sp, int color, boolean bold) {
        TextView v = new TextView(a);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        if(bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    private static TextView button(Activity a, String label, boolean primary, View.OnClickListener l) {
        TextView t = text(a, label, 14, primary ? 0xFF0A0C09 : TEXT, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setColor(primary ? LIME : CARD);
        d.setCornerRadius(dp(a, 24));
        d.setStroke(dp(a, 1), primary ? LIME : 0xFF2A3026);
        t.setBackground(d);
        t.setOnClickListener(l);
        return t;
    }

    private static String title(Activity a, CrashAnalyzer.Finding f) {
        switch(f.id) {
            case "loader_old": return a.getString(R.string.spirit_crash_loader, f.arg);
            case "dep_missing": return a.getString(R.string.spirit_crash_dep_missing, f.arg);
            case "mods_old": return a.getString(R.string.spirit_crash_mods_old) + " (" + f.arg + ")";
            case "oom": return a.getString(R.string.spirit_crash_oom);
            case "mod": return a.getString(R.string.spirit_crash_mod, f.arg);
            default: return a.getString(R.string.spirit_crash_dep);
        }
    }

    private static void show(final Activity a, final List<CrashAnalyzer.Finding> findings) {
        boolean hasInstallOrUpdate = false;
        for(CrashAnalyzer.Finding f : findings) {
            if(f.fix == CrashAnalyzer.Fix.INSTALL_MOD || f.fix == CrashAnalyzer.Fix.UPDATE_MODS || f.fix == CrashAnalyzer.Fix.UPDATE_LOADER) hasInstallOrUpdate = true;
        }
        final List<CrashAnalyzer.Finding> todo = new ArrayList<>();
        for(CrashAnalyzer.Finding f : findings) {
            if(f.fix != CrashAnalyzer.Fix.NONE && CrashFixer.isAutomatic(f, hasInstallOrUpdate)) todo.add(f);
        }

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a, 20), dp(a, 18), dp(a, 20), dp(a, 14));
        box.addView(text(a, a.getString(R.string.spirit_game_incompat_title), 20, TEXT, true));
        TextView sub = text(a, a.getString(R.string.spirit_game_incompat_sub), 13, DIM, false);
        sub.setPadding(0, dp(a, 2), 0, dp(a, 10));
        box.addView(sub);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        for(CrashAnalyzer.Finding f : findings) {
            TextView line = text(a, "•  " + title(a, f), 14, TEXT, false);
            line.setPadding(0, dp(a, 3), 0, dp(a, 3));
            list.addView(line);
        }
        ScrollView scroll = new ScrollView(a);
        scroll.addView(list);
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView status = text(a, "", 13, LIME, false);
        status.setPadding(0, dp(a, 8), 0, dp(a, 4));
        box.addView(status);

        final AlertDialog dialog = new AlertDialog.Builder(a).setView(box).setCancelable(false).create();
        final TextView[] fix = new TextView[1];
        final boolean[] fixed = {false};
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(a, 44), 1f);
        half.rightMargin = dp(a, 4);
        half.leftMargin = dp(a, 4);

        fix[0] = button(a, a.getString(R.string.spirit_crash_auto), true, v -> {
            if(fixed[0]) { // finished: restart so the game starts with the fixed mods
                Tools.restartLauncherActivity(a);
                Tools.fullyExit();
                return;
            }
            fix[0].setEnabled(false);
            fix[0].setAlpha(0.7f);
            MojoApplication.sExecutorService.execute(() -> {
                BrowseTarget target = null;
                try { target = BrowseTarget.forSelectedInstance(); } catch (Throwable ignored) { /* none */ }
                int ok = 0;
                StringBuilder errors = new StringBuilder();
                for(final CrashAnalyzer.Finding f : todo) {
                    a.runOnUiThread(() -> status.setText(a.getString(R.string.spirit_crash_auto_working, title(a, f))));
                    try {
                        CrashFixer.apply(a, f, target);
                        ok++;
                    } catch (IOException | RuntimeException e) {
                        if(errors.length() > 0) errors.append('\n');
                        errors.append(e.getMessage());
                    }
                }
                final int done = ok;
                final String err = errors.toString();
                a.runOnUiThread(() -> {
                    fix[0].setEnabled(true);
                    fix[0].setAlpha(1f);
                    if(err.isEmpty() && done > 0) {
                        fixed[0] = true;
                        status.setText(R.string.spirit_crash_auto_ok);
                        fix[0].setText(R.string.spirit_game_incompat_restart);
                    } else {
                        status.setText(a.getString(R.string.spirit_crash_auto_partial, done, err));
                    }
                });
            });
        });
        if(todo.isEmpty()) fix[0].setVisibility(View.GONE);
        row.addView(fix[0], new LinearLayout.LayoutParams(0, dp(a, 44), 1.3f));
        box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row2 = new LinearLayout(a);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 44));
        r2.topMargin = dp(a, 8);
        row2.addView(button(a, a.getString(R.string.spirit_game_incompat_exit), false, v -> {
            Tools.restartLauncherActivity(a);
            Tools.fullyExit();
        }), half);
        row2.addView(button(a, a.getString(R.string.spirit_game_incompat_later), false, v -> dialog.dismiss()), half);
        box.addView(row2, r2);
        dialog.show();
    }
}
