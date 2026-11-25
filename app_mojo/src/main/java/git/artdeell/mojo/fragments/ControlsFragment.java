package git.artdeell.mojo.fragments;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.browse.ControlPreviewView;
import git.artdeell.mojo.browse.ControlsClient;
import git.artdeell.mojo.input.CustomControls;
import git.artdeell.mojo.input.LayoutConverter;
import git.artdeell.mojo.utils.SpiritScreen;
import git.artdeell.mojo.utils.SpiritToast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Controls shared by players: search, filter by tags (pvp, uhc, 3 finger, ...), preview before installing, install
 * into the launcher's controlmap folder, and upload your own. Everything lives in the launcher (Supabase table), not on a website.
 */
public class ControlsFragment extends Fragment {
    public static final String TAG = "SpiritControlsFragment";
    private static final String[] SORTS = {"new", "downloads"};
    private static final String[] SORT_LABELS = {"Newest", "Most downloaded"};

    @Nullable private ControlsClient mClient;
    private final Set<String> mTags = new LinkedHashSet<>();
    private LinearLayout mList;
    private TextView mEmpty, mMore, mSortView;
    private EditText mInput;
    private int mSort = 0, mOffset = 0, mGeneration = 0;

    private final ActivityResultLauncher<String> mPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> { if(uri != null) onFilePicked(uri); });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        Context ctx = requireContext();
        String url = getString(R.string.supabase_url), key = getString(R.string.supabase_anon_key);
        mClient = ControlsClient.isConfigured(url, key) ? new ControlsClient(url, key) : null;

        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ContextCompat.getColor(ctx, R.color.background_app));
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // header: back, title, upload
        LinearLayout header = new LinearLayout(ctx);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(ctx);
        back.setId(R.id.spirit_back);
        back.setImageResource(R.drawable.ic_spirit_back);
        back.setColorFilter(ContextCompat.getColor(ctx, R.color.spirit_text));
        back.setPadding(dp(10), dp(10), dp(10), dp(10));
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = text(ctx, "Controls", 22, R.color.spirit_text, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView upload = chip(ctx, "Upload your control", true);
        upload.setOnClickListener(v -> startUpload());
        header.addView(upload);
        root.addView(header);
        SpiritScreen.bindBack(root, requireActivity());

        // search
        mInput = new EditText(ctx);
        mInput.setHint("Search controls");
        mInput.setSingleLine(true);
        mInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        mInput.setTextColor(ContextCompat.getColor(ctx, R.color.spirit_text));
        mInput.setHintTextColor(ContextCompat.getColor(ctx, R.color.spirit_text_dim));
        mInput.setBackgroundResource(R.drawable.spirit_input_bg);
        mInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        mInput.setOnEditorActionListener((v, a, e) -> { reload(); return true; });
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = dp(8);
        root.addView(mInput, ip);

        // tag filter row + sort
        HorizontalScrollView tagScroll = new HorizontalScrollView(ctx);
        tagScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tagRow = new LinearLayout(ctx);
        for(String t : ControlsClient.TAGS) {
            TextView c = chip(ctx, t, false);
            c.setOnClickListener(v -> {
                if(!mTags.remove(t)) mTags.add(t);
                style(c, mTags.contains(t));
                reload();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            tagRow.addView(c, lp);
        }
        tagScroll.addView(tagRow);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(10);
        root.addView(tagScroll, tp);

        mSortView = text(ctx, "Sort: " + SORT_LABELS[0], 13, R.color.spirit_lime, true);
        mSortView.setPadding(0, dp(10), 0, dp(6));
        mSortView.setOnClickListener(v -> {
            mSort = (mSort + 1) % SORTS.length;
            mSortView.setText("Sort: " + SORT_LABELS[mSort]);
            reload();
        });
        root.addView(mSortView);

        mList = new LinearLayout(ctx);
        mList.setOrientation(LinearLayout.VERTICAL);
        root.addView(mList);
        mEmpty = text(ctx, "", 14, R.color.spirit_text_dim, false);
        mEmpty.setGravity(Gravity.CENTER);
        mEmpty.setPadding(0, dp(32), 0, dp(32));
        root.addView(mEmpty);
        mMore = chip(ctx, "Load more", false);
        mMore.setVisibility(View.GONE);
        mMore.setOnClickListener(v -> load(false));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(mMore, mp);

        if(mClient == null) {
            mEmpty.setText("Controls server is not set up in this build.\nAdd SUPABASE_URL and SUPABASE_ANON_KEY as GitHub secrets and rebuild.");
            upload.setVisibility(View.GONE);
        } else {
            reload();
        }
        return scroll;
    }

    // ---------- listing ----------

    private void reload() {
        mOffset = 0;
        mGeneration++;
        mList.removeAllViews();
        load(true);
    }

    private void load(boolean fresh) {
        final ControlsClient client = mClient;
        if(client == null) return;
        final int gen = mGeneration, offset = mOffset;
        final String q = mInput.getText().toString();
        final List<String> tags = new ArrayList<>(mTags);
        final String sort = SORTS[mSort];
        mMore.setVisibility(View.GONE);
        mEmpty.setText(fresh ? "Loading..." : "");
        MojoApplication.sExecutorService.execute(() -> {
            List<ControlsClient.Entry> res = null;
            IOException err = null;
            try { res = client.list(q, tags, sort, offset); } catch (IOException e) { err = e; }
            final List<ControlsClient.Entry> fRes = res;
            final IOException fErr = err;
            Tools.runOnUiThread(() -> {
                if(!isAdded() || gen != mGeneration) return;
                if(fErr != null) { mEmpty.setText("Could not load controls. Check your internet and try again."); return; }
                mOffset += fRes.size();
                for(ControlsClient.Entry e : fRes) mList.addView(card(e));
                mEmpty.setText(mList.getChildCount() == 0 ? "No controls found for this filter." : "");
                mMore.setVisibility(fRes.size() >= ControlsClient.PAGE_SIZE ? View.VISIBLE : View.GONE);
            });
        });
    }

    private View card(ControlsClient.Entry e) {
        Context ctx = requireContext();
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.spirit_card_bg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        card.addView(text(ctx, e.name == null ? "?" : e.name, 16, R.color.spirit_text, true));
        String meta = (TextUtils.isEmpty(e.author) ? "unknown" : e.author) + "  ·  " + e.downloads + " downloads";
        card.addView(text(ctx, meta, 12, R.color.spirit_text_dim, false));
        if(!e.tags.isEmpty()) card.addView(text(ctx, TextUtils.join("  ·  ", e.tags), 12, R.color.spirit_lime, true));
        if(!TextUtils.isEmpty(e.description)) {
            TextView d = text(ctx, e.description, 13, R.color.spirit_text, false);
            d.setPadding(0, dp(6), 0, 0);
            card.addView(d);
        }
        LinearLayout row = new LinearLayout(ctx);
        row.setPadding(0, dp(10), 0, 0);
        TextView prev = chip(ctx, "Preview", false);
        TextView inst = chip(ctx, "Install", true);
        prev.setOnClickListener(v -> fetch(e, false));
        inst.setOnClickListener(v -> fetch(e, true));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMarginEnd(dp(8));
        row.addView(prev, bp);
        row.addView(inst);
        card.addView(row);
        return card;
    }

    // ---------- preview / install ----------

    private void fetch(ControlsClient.Entry e, boolean install) {
        final ControlsClient client = mClient;
        if(client == null) return;
        SpiritToast.makeText(requireContext(), install ? "Downloading..." : "Loading preview...", Toast.LENGTH_SHORT).show();
        MojoApplication.sExecutorService.execute(() -> {
            byte[] data = null;
            try { data = client.download(e.id); } catch (IOException ex) { /* shown below */ }
            final byte[] fData = data;
            CustomControls parsed = fData == null ? null : parse(fData);
            String savedAs = null;
            if(install && parsed != null) {
                savedAs = save(e.name, fData);
                if(savedAs != null) client.countDownload(e.id);
            }
            final String fSaved = savedAs;
            Tools.runOnUiThread(() -> {
                if(!isAdded()) return;
                if(fData == null || parsed == null) {
                    SpiritToast.makeText(requireContext(), "This control could not be loaded (file is damaged or from a newer version).", Toast.LENGTH_LONG).show();
                } else if(install) {
                    SpiritToast.makeText(requireContext(), fSaved != null ? "Installed as \"" + fSaved + "\". Pick it in the control settings." : "Could not save the control.", Toast.LENGTH_LONG).show();
                } else {
                    showPreview(e, parsed, fData);
                }
            });
        });
    }

    private void showPreview(ControlsClient.Entry e, CustomControls controls, byte[] data) {
        ControlPreviewView view = new ControlPreviewView(requireContext(), controls);
        FrameWrap wrap = new FrameWrap(requireContext(), view);
        new AlertDialog.Builder(requireContext())
                .setTitle(e.name)
                .setView(wrap)
                .setPositiveButton("Install", (d, w) -> fetch(e, true))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Padding around the preview inside the dialog */
    private static final class FrameWrap extends LinearLayout {
        FrameWrap(Context c, View child) {
            super(c);
            int p = (int) (16 * c.getResources().getDisplayMetrics().density);
            setPadding(p, p / 2, p, 0);
            addView(child, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    /** null when the bytes are not a control layout this launcher understands */
    @Nullable
    private CustomControls parse(byte[] data) {
        try {
            File tmp = new File(requireContext().getCacheDir(), "ctl_check_" + Thread.currentThread().getId() + ".json");
            try(FileOutputStream os = new FileOutputStream(tmp)) { os.write(data); }
            DisplayMetrics dm = requireContext().getResources().getDisplayMetrics();
            if(Tools.currentDisplayMetrics == null) Tools.currentDisplayMetrics = dm;
            try {
                return LayoutConverter.loadAndConvertIfNecessary(new Point(Math.max(dm.widthPixels, dm.heightPixels), Math.min(dm.widthPixels, dm.heightPixels)), tmp.getAbsolutePath());
            } finally {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (IOException | RuntimeException | IllegalStateException e) {
            return null;
        }
    }

    /** @return the saved name, or null on failure */
    @Nullable
    private String save(@Nullable String name, byte[] data) {
        try {
            String base = (name == null ? "control" : name).replaceAll("[^A-Za-z0-9 _.-]", "_").trim();
            if(base.isEmpty()) base = "control";
            File dir = new File(Tools.CTRLMAP_PATH);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            String finalName = base;
            for(int i = 2; new File(dir, finalName + ".json").exists(); i++) finalName = base + " (" + i + ")";
            try(FileOutputStream os = new FileOutputStream(new File(dir, finalName + ".json"))) { os.write(data); }
            return finalName;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ---------- upload ----------

    private void startUpload() {
        mPicker.launch("*/*");
    }

    private void onFilePicked(Uri uri) {
        final Context ctx = requireContext().getApplicationContext();
        MojoApplication.sExecutorService.execute(() -> {
            byte[] data = null;
            try(InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if(in != null) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while((n = in.read(buf)) > 0) {
                        bos.write(buf, 0, n);
                        if(bos.size() > ControlsClient.MAX_FILE_BYTES) { bos = null; break; }
                    }
                    if(bos != null) data = bos.toByteArray();
                }
            } catch (IOException | RuntimeException ignored) {}
            final byte[] fData = data;
            final CustomControls parsed = fData == null ? null : parse(fData);
            final String guess = Tools.getFileName(ctx, uri);
            Tools.runOnUiThread(() -> {
                if(!isAdded()) return;
                if(fData == null) SpiritToast.makeText(requireContext(), "File is too big (max " + ControlsClient.MAX_FILE_BYTES / 1024 + " KB) or could not be read.", Toast.LENGTH_LONG).show();
                else if(parsed == null) SpiritToast.makeText(requireContext(), "That is not a valid control file.", Toast.LENGTH_LONG).show();
                else showUploadForm(fData, guess);
            });
        });
    }

    private void showUploadForm(byte[] data, @Nullable String guessedName) {
        final Context ctx = requireContext();
        final ControlsClient client = mClient;
        if(client == null) return;
        LinearLayout form = new LinearLayout(ctx);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        String base = guessedName == null ? "" : guessedName.replaceAll("\\.json$", "");
        EditText name = field(ctx, "Name (2-40 letters)", base, 40);
        EditText desc = field(ctx, "Description: who is it for, how many fingers, what is special", "", 300);
        String savedAuthor = git.artdeell.mojo.prefs.LauncherPreferences.DEFAULT_PREF.getString("spiritControlAuthor", "");
        EditText author = field(ctx, "Your name (shown to others)", savedAuthor, 32);
        form.addView(name);
        form.addView(desc);
        form.addView(author);
        TextView tagsTitle = text(ctx, "Tags (pick up to " + ControlsClient.MAX_TAGS + ")", 13, R.color.spirit_text_dim, true);
        tagsTitle.setPadding(0, dp(10), 0, dp(6));
        form.addView(tagsTitle);
        GridLayout grid = new GridLayout(ctx);
        grid.setColumnCount(3);
        final Set<String> picked = new LinkedHashSet<>();
        for(String t : ControlsClient.TAGS) {
            TextView c = chip(ctx, t, false);
            c.setOnClickListener(v -> {
                if(picked.contains(t)) picked.remove(t);
                else if(picked.size() < ControlsClient.MAX_TAGS) picked.add(t);
                else SpiritToast.makeText(ctx, "Max " + ControlsClient.MAX_TAGS + " tags", Toast.LENGTH_SHORT).show();
                style(c, picked.contains(t));
            });
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.setMargins(0, 0, dp(6), dp(6));
            grid.addView(c, gp);
        }
        form.addView(grid);
        ScrollView sv = new ScrollView(ctx);
        sv.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle("Upload your control")
                .setView(sv)
                .setPositiveButton("Upload", null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if(n.length() < 2) { name.setError("At least 2 letters"); return; }
            if(picked.isEmpty()) { SpiritToast.makeText(ctx, "Pick at least one tag so others can find it", Toast.LENGTH_SHORT).show(); return; }
            String a = author.getText().toString().trim();
            git.artdeell.mojo.prefs.LauncherPreferences.DEFAULT_PREF.edit().putString("spiritControlAuthor", a).apply();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            final List<String> tags = new ArrayList<>(picked);
            final String d = desc.getText().toString().trim();
            MojoApplication.sExecutorService.execute(() -> {
                String error = null;
                try { client.upload(n, d, a, tags, data); } catch (IOException e) { error = e.getMessage(); }
                final String fErr = error;
                Tools.runOnUiThread(() -> {
                    if(fErr == null) {
                        dialog.dismiss();
                        SpiritToast.makeText(ctx, "Uploaded! Everyone using Spirit Launcher can find it now.", Toast.LENGTH_LONG).show();
                        if(isAdded()) reload();
                    } else {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        SpiritToast.makeText(ctx, "Upload failed: " + fErr, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
    }

    // ---------- small view helpers ----------

    private EditText field(Context ctx, String hint, String value, int max) {
        EditText e = new EditText(ctx);
        e.setHint(hint);
        e.setText(value);
        e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});
        return e;
    }

    private TextView text(Context ctx, String s, float sp, int colorRes, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(ContextCompat.getColor(ctx, colorRes));
        if(bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        return t;
    }

    private TextView chip(Context ctx, String label, boolean on) {
        TextView c = new TextView(ctx);
        c.setText(label);
        c.setTextSize(13);
        c.setTypeface(c.getTypeface(), Typeface.BOLD);
        c.setPadding(dp(14), dp(8), dp(14), dp(8));
        c.setClickable(true);
        c.setFocusable(true);
        style(c, on);
        return c;
    }

    private void style(TextView c, boolean on) {
        c.setBackgroundResource(on ? R.drawable.spirit_badge_fill : R.drawable.spirit_card_bg);
        c.setTextColor(ContextCompat.getColor(c.getContext(), on ? R.color.spirit_on_lime : R.color.spirit_text));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
