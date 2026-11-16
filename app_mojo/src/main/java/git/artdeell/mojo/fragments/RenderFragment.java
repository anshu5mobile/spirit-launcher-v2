package git.artdeell.mojo.fragments;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.browse.BrowseTarget;
import git.artdeell.mojo.utils.SpiritScreen;
import git.artdeell.mojo.utils.plugins.LibraryPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * Render screen for video makers: shows whether the FFmpeg plugin (needed by Replay Mod to export) is installed,
 * lists the videos Replay Mod exported in the selected instance and saves them to the phone gallery.
 */
public class RenderFragment extends Fragment {
    public static final String TAG = "SpiritRenderFragment";

    private static final int BG = 0xFF0A0C09, CARD = 0xFF141812, LIME = 0xFFA6F03A, TEXT = 0xFFEAF2E0, DIM = 0xFF9FAB96, WARN = 0xFFFFC857;

    private LinearLayout mBody;

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
        header.addView(text(getString(R.string.spirit_render_title), 22, TEXT, true));
        root.addView(header);

        mBody = new LinearLayout(requireContext());
        mBody.setOrientation(LinearLayout.VERTICAL);
        root.addView(mBody);
        SpiritScreen.bindBack(root, requireActivity());
        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();
        final Context app = requireContext().getApplicationContext();
        MojoApplication.sExecutorService.execute(() -> {
            final boolean plugin = LibraryPlugin.discoverPlugin(app, LibraryPlugin.ID_FFMPEG_PLUGIN) != null;
            BrowseTarget target = null;
            try { target = BrowseTarget.forSelectedInstance(); } catch (Throwable ignored) { /* none selected */ }
            final File dir = target == null ? null : new File(target.gameDir, "replay_videos");
            File[] found = dir == null ? null : dir.listFiles((d, n) -> {
                String l = n.toLowerCase(Locale.ROOT);
                return l.endsWith(".mp4") || l.endsWith(".mkv") || l.endsWith(".mov") || l.endsWith(".webm") || l.endsWith(".avi");
            });
            final File[] videos = found == null ? new File[0] : found;
            Arrays.sort(videos, Comparator.comparingLong(File::lastModified).reversed());
            Tools.runOnUiThread(() -> { if(isAdded()) render(plugin, videos); });
        });
    }

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
        TextView t = text(title, 12, LIME, true);
        t.setPadding(0, 0, 0, dp(6));
        c.addView(t);
        return c;
    }

    private TextView button(String label, boolean primary, int color, View.OnClickListener l) {
        TextView t = text(label, 13, primary ? 0xFF0A0C09 : color, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(primary ? LIME : 0xFF1B2018, primary ? LIME : 0xFF2A3026, 18));
        t.setOnClickListener(l);
        t.setPadding(dp(14), 0, dp(14), 0);
        return t;
    }

    private void render(boolean plugin, File[] videos) {
        mBody.removeAllViews();

        LinearLayout status = card(getString(R.string.spirit_render_plugin));
        status.addView(text(plugin ? getString(R.string.spirit_render_plugin_ok) : getString(R.string.spirit_render_plugin_missing),
                17, plugin ? LIME : WARN, true));
        status.addView(text(plugin ? getString(R.string.spirit_render_plugin_ok_d) : getString(R.string.spirit_render_plugin_missing_d), 13, DIM, false));
        mBody.addView(status);

        LinearLayout how = card(getString(R.string.spirit_render_how));
        how.addView(text(getString(R.string.spirit_render_how_body), 13, TEXT, false));
        mBody.addView(how);

        LinearLayout list = card(getString(R.string.spirit_render_videos));
        if(videos.length == 0) {
            list.addView(text(getString(R.string.spirit_render_none), 13, DIM, false));
        }
        for(final File video : videos) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(6));
            row.addView(text(video.getName(), 14, TEXT, true));
            row.addView(text(String.format(Locale.US, "%.1f MB", video.length() / 1048576f), 12, DIM, false));
            LinearLayout actions = new LinearLayout(requireContext());
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
            ap.topMargin = dp(6);
            actions.setLayoutParams(ap);
            LinearLayout.LayoutParams w = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            w.rightMargin = dp(6);
            actions.addView(button(getString(R.string.spirit_render_save), true, LIME, v -> saveToGallery(video)), w);
            actions.addView(button(getString(R.string.spirit_browse_delete), false, 0xFFFF6B5E, v -> confirmDelete(video)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            row.addView(actions);
            list.addView(row);
        }
        mBody.addView(list);
    }

    private void confirmDelete(final File video) {
        new AlertDialog.Builder(requireContext())
                .setTitle(video.getName())
                .setMessage(R.string.spirit_render_confirm_delete)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.spirit_browse_delete, (d, w) -> {
                    //noinspection ResultOfMethodCallIgnored
                    video.delete();
                    onResume();
                })
                .show();
    }

    private void saveToGallery(final File video) {
        final Context app = requireContext().getApplicationContext();
        Toast.makeText(app, R.string.spirit_render_saving, Toast.LENGTH_SHORT).show();
        MojoApplication.sExecutorService.execute(() -> {
            String message;
            try {
                copyToMovies(app, video);
                message = app.getString(R.string.spirit_render_saved);
            } catch (IOException | RuntimeException e) {
                message = app.getString(R.string.spirit_render_save_failed, String.valueOf(e.getMessage()));
            }
            final String m = message;
            Tools.runOnUiThread(() -> Toast.makeText(app, m, Toast.LENGTH_LONG).show());
        });
    }

    private static String mimeOf(String name) {
        String l = name.toLowerCase(Locale.ROOT);
        if(l.endsWith(".mkv")) return "video/x-matroska";
        if(l.endsWith(".webm")) return "video/webm";
        if(l.endsWith(".mov")) return "video/quicktime";
        if(l.endsWith(".avi")) return "video/x-msvideo";
        return "video/mp4";
    }

    private static void copyToMovies(Context app, File video) throws IOException {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, video.getName());
            values.put(MediaStore.Video.Media.MIME_TYPE, mimeOf(video.getName()));
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SpiritLauncher");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
            Uri uri = app.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if(uri == null) throw new IOException("The gallery refused the file");
            try(InputStream in = new FileInputStream(video); OutputStream out = app.getContentResolver().openOutputStream(uri)) {
                if(out == null) throw new IOException("Could not open the gallery file");
                byte[] buf = new byte[1 << 16];
                int r;
                while((r = in.read(buf)) != -1) out.write(buf, 0, r);
            }
            values.clear();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            app.getContentResolver().update(uri, values, null, null);
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SpiritLauncher");
            if(!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create the Movies folder");
            File target = new File(dir, video.getName());
            try(InputStream in = new FileInputStream(video); OutputStream out = new java.io.FileOutputStream(target)) {
                byte[] buf = new byte[1 << 16];
                int r;
                while((r = in.read(buf)) != -1) out.write(buf, 0, r);
            }
            android.media.MediaScannerConnection.scanFile(app, new String[]{target.getAbsolutePath()}, null, null);
        }
    }
}
