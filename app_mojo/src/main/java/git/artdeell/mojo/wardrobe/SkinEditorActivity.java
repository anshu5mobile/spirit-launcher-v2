package git.artdeell.mojo.wardrobe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Full screen skin editor. Reads the skin from EXTRA_IN, writes the result to EXTRA_OUT and finishes with RESULT_OK. */
public class SkinEditorActivity extends AppCompatActivity {
    public static final String EXTRA_IN = "in";
    public static final String EXTRA_OUT = "out";
    public static final String EXTRA_SLIM = "slim";

    private static final int BG = 0xFF0A0C09, CARD = 0xFF141812, LIME = 0xFFA6F03A, TEXT = 0xFFEAF2E0, DIM = 0xFF9FAB96;
    private static final String[] PALETTE = {
            "#000000", "#FFFFFF", "#7F7F7F", "#4A3728", "#8B5A2B", "#E0B48A", "#F2D2B6", "#C0392B",
            "#E67E22", "#F1C40F", "#A6F03A", "#27AE60", "#16A085", "#2980B9", "#8E44AD", "#E84393"};

    private SkinEditorView mEditor;
    private TextView mUndoButton, mRedoButton;
    private final TextView[] mTools = new TextView[4];
    private View mColorPreview;
    private SkinPreviewView mPreview;
    private LinearLayout mPanel3d;
    private TextView mMode2d, mMode3d, mLayerChip;
    private final TextView[] mPartChips = new TextView[6];
    private String mOut;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        mOut = getIntent().getStringExtra(EXTRA_OUT);
        String in = getIntent().getStringExtra(EXTRA_IN);
        Bitmap skin = in == null ? null : BitmapFactory.decodeFile(in);
        if(skin == null || mOut == null) {
            git.artdeell.mojo.utils.SpiritToast.makeText(this, R.string.spirit_editor_nofile, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int pad = dp(8);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.spirit_editor_title);
        title.setTextColor(TEXT);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(6), dp(4), dp(6), dp(8));
        root.addView(title);

        // mode row: 2D pixel editor or 3D editing on the player model
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        mMode2d = chip(getString(R.string.spirit_editor_2d), v -> setMode3d(false));
        mMode3d = chip(getString(R.string.spirit_editor_3d), v -> setMode3d(true));
        modes.addView(mMode2d);
        modes.addView(mMode3d);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.bottomMargin = dp(6);
        root.addView(modes, mp);

        // tools row
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(tools);
        root.addView(scroll);
        int[] toolIds = {SkinEditorView.PEN, SkinEditorView.ERASER, SkinEditorView.PICKER, SkinEditorView.FILL};
        int[] toolNames = {R.string.spirit_editor_pen, R.string.spirit_editor_eraser, R.string.spirit_editor_picker, R.string.spirit_editor_fill};
        for(int i = 0; i < 4; i++) {
            final int tool = toolIds[i];
            mTools[i] = chip(getString(toolNames[i]), v -> selectTool(tool));
            tools.addView(mTools[i]);
        }
        mUndoButton = chip(getString(R.string.spirit_editor_undo), v -> mEditor.undo());
        mRedoButton = chip(getString(R.string.spirit_editor_redo), v -> mEditor.redo());
        tools.addView(mUndoButton);
        tools.addView(mRedoButton);
        final TextView grid = chip(getString(R.string.spirit_editor_grid), null);
        grid.setOnClickListener(v -> {
            mEditor.setShowGrid(!mEditor.isShowGrid());
            style(grid, mEditor.isShowGrid());
        });
        tools.addView(grid);

        mEditor = new SkinEditorView(this);
        mEditor.setSkin(skin);
        mEditor.setListener(new SkinEditorView.Listener() {
            @Override public void onColorPicked(int color) { updateColor(); }
            @Override public void onHistoryChanged() { updateHistoryButtons(); }
        });
        mEditor.setOnChange(() -> { if(mPreview != null) mPreview.invalidate(); });
        FrameLayout stage = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = dp(8); lp.bottomMargin = dp(8);
        stage.setLayoutParams(lp);
        stage.addView(mEditor, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        stage.addView(build3dPanel(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(stage);

        // palette
        HorizontalScrollView palScroll = new HorizontalScrollView(this);
        palScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout pal = new LinearLayout(this);
        pal.setOrientation(LinearLayout.HORIZONTAL);
        pal.setGravity(Gravity.CENTER_VERTICAL);
        palScroll.addView(pal);
        mColorPreview = new View(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(40), dp(40));
        cp.rightMargin = dp(10);
        mColorPreview.setLayoutParams(cp);
        mColorPreview.setOnClickListener(v -> askColor());
        pal.addView(mColorPreview);
        for(String hex : PALETTE) {
            final int color = Color.parseColor(hex);
            View sw = new View(this);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(32), dp(32));
            sp.rightMargin = dp(6);
            sw.setLayoutParams(sp);
            sw.setBackground(round(color, 0xFF3A4236, 6));
            sw.setOnClickListener(v -> { mEditor.setColor(color); updateColor(); });
            pal.addView(sw);
        }
        root.addView(palScroll);

        // bottom
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(10);
        bottom.setLayoutParams(bp);
        TextView cancel = button(getString(android.R.string.cancel), false, v -> finish());
        TextView done = button(getString(R.string.spirit_editor_done), true, v -> save());
        bottom.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, dp(48), 1f);
        dl.leftMargin = dp(8);
        bottom.addView(done, dl);
        root.addView(bottom);

        setContentView(root);
        selectTool(SkinEditorView.PEN);
        setMode3d(getSharedPreferences("spirit_editor", MODE_PRIVATE).getBoolean("mode3d", true));
        updateColor();
        updateHistoryButtons();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView chip(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(6);
        t.setLayoutParams(p);
        t.setOnClickListener(l);
        style(t, false);
        return t;
    }

    private void style(TextView t, boolean on) {
        t.setBackground(round(on ? LIME : CARD, on ? LIME : 0xFF2A3026, 16));
        t.setTextColor(on ? 0xFF0A0C09 : TEXT);
    }

    private TextView button(String text, boolean primary, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(primary ? LIME : CARD, primary ? LIME : 0xFF2A3026, 24));
        t.setTextColor(primary ? 0xFF0A0C09 : TEXT);
        t.setOnClickListener(l);
        return t;
    }

    private void selectTool(int tool) {
        mEditor.setTool(tool);
        for(int i = 0; i < 4; i++) style(mTools[i], i == tool);
    }

    private void updateColor() {
        mColorPreview.setBackground(round(mEditor.getColor(), LIME, 8));
    }

    private void updateHistoryButtons() {
        mUndoButton.setAlpha(mEditor.canUndo() ? 1f : 0.4f);
        mRedoButton.setAlpha(mEditor.canRedo() ? 1f : 0.4f);
    }

    private View build3dPanel() {
        mPanel3d = new LinearLayout(this);
        mPanel3d.setOrientation(LinearLayout.HORIZONTAL);
        mPreview = new SkinPreviewView(this);
        mPreview.setPlaceholder("");
        mPreview.setSkin(mEditor.getBitmapRef(), getIntent().getBooleanExtra(EXTRA_SLIM, false));
        mPreview.setEditMode(true, new SkinPreviewView.EditListener() {
            @Override public void onStrokeStart() { mEditor.beginStroke(); }
            @Override public void onPaint(int x, int y) { mEditor.paintTexel(x, y); }
            @Override public void onStrokeEnd() { mEditor.endStroke(); }
        });
        mPanel3d.addView(mPreview, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        ScrollView side = new ScrollView(this);
        side.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(6), 0, 0, 0);
        side.addView(col);
        TextView hdr = new TextView(this);
        hdr.setText(R.string.spirit_editor_parts);
        hdr.setTextColor(LIME);
        hdr.setTextSize(11);
        hdr.setTypeface(null, android.graphics.Typeface.BOLD);
        col.addView(hdr);
        int[] names = {R.string.spirit_editor_part_head, R.string.spirit_editor_part_body, R.string.spirit_editor_part_rarm,
                R.string.spirit_editor_part_larm, R.string.spirit_editor_part_rleg, R.string.spirit_editor_part_lleg};
        for(int i = 0; i < 6; i++) {
            final int part = i;
            final TextView c = chip(getString(names[i]), null);
            c.setOnClickListener(v -> {
                mPreview.setPartEnabled(part, !mPreview.isPartEnabled(part));
                style(c, mPreview.isPartEnabled(part));
            });
            style(c, true);
            mPartChips[i] = c;
            col.addView(c, new LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        mLayerChip = chip("", v -> {
            mPreview.setLayerMode((mPreview.getLayerMode() + 1) % 3);
            updateLayerChip();
        });
        col.addView(mLayerChip, new LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView reset = chip(getString(R.string.spirit_editor_reset_view), v -> mPreview.resetView());
        col.addView(reset, new LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView hint = new TextView(this);
        hint.setText(R.string.spirit_editor_hint3d);
        hint.setTextColor(DIM);
        hint.setTextSize(11);
        hint.setPadding(0, dp(4), 0, 0);
        col.addView(hint, new LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT));
        mPanel3d.addView(side, new LinearLayout.LayoutParams(dp(98), ViewGroup.LayoutParams.MATCH_PARENT));
        updateLayerChip();
        return mPanel3d;
    }

    private void updateLayerChip() {
        int[] names = {R.string.spirit_editor_layer_both, R.string.spirit_editor_layer_base, R.string.spirit_editor_layer_outer};
        mLayerChip.setText(names[mPreview.getLayerMode()]);
        style(mLayerChip, mPreview.getLayerMode() != SkinPreviewView.LAYER_BOTH);
    }

    private void setMode3d(boolean on) {
        mPanel3d.setVisibility(on ? View.VISIBLE : View.GONE);
        mEditor.setVisibility(on ? View.GONE : View.VISIBLE);
        style(mMode3d, on);
        style(mMode2d, !on);
        mPreview.invalidate();
        try { getSharedPreferences("spirit_editor", MODE_PRIVATE).edit().putBoolean("mode3d", on).apply(); } catch (Throwable ignored) { }
    }

    /** Colour dialog: hue / saturation / brightness sliders, a live preview and a hex field. */
    private void askColor() {
        final float[] hsv = new float[3];
        Color.colorToHSV(mEditor.getColor(), hsv);
        final int[] result = {mEditor.getColor()};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), 0);
        final View preview = new View(this);
        box.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        final EditText hex = new EditText(this);
        hex.setInputType(InputType.TYPE_CLASS_TEXT);
        hex.setSingleLine(true);
        final SeekBar[] bars = new SeekBar[3];
        final boolean[] busy = {false};
        final Runnable refresh = () -> {
            result[0] = Color.HSVToColor(hsv) | 0xFF000000;
            preview.setBackground(round(result[0], LIME, 8));
            busy[0] = true;
            hex.setText(String.format("#%06X", result[0] & 0xFFFFFF));
            busy[0] = false;
        };
        String[] labels = {getString(R.string.spirit_editor_hue), getString(R.string.spirit_editor_sat), getString(R.string.spirit_editor_val)};
        int[] max = {360, 100, 100};
        for(int i = 0; i < 3; i++) {
            final int idx = i;
            TextView l = new TextView(this);
            l.setText(labels[i]);
            l.setTextSize(12);
            l.setPadding(0, dp(8), 0, 0);
            box.addView(l);
            SeekBar bar = new SeekBar(this);
            bar.setMax(max[i]);
            bar.setProgress(Math.round(idx == 0 ? hsv[0] : hsv[idx] * 100f));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar b, int p, boolean user) {
                    if(!user) return;
                    hsv[idx] = idx == 0 ? p : p / 100f;
                    refresh.run();
                }
                @Override public void onStartTrackingTouch(SeekBar b) { }
                @Override public void onStopTrackingTouch(SeekBar b) { }
            });
            bars[i] = bar;
            box.addView(bar);
        }
        hex.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                if(busy[0]) return;
                try {
                    String t = e.toString().trim();
                    if(!t.startsWith("#")) t = "#" + t;
                    if(t.length() != 7) return;
                    int c = Color.parseColor(t);
                    Color.colorToHSV(c, hsv);
                    result[0] = c | 0xFF000000;
                    preview.setBackground(round(result[0], LIME, 8));
                    bars[0].setProgress(Math.round(hsv[0]));
                    bars[1].setProgress(Math.round(hsv[1] * 100f));
                    bars[2].setProgress(Math.round(hsv[2] * 100f));
                } catch (IllegalArgumentException ignored) { /* still typing */ }
            }
        });
        box.addView(hex);
        refresh.run();
        new AlertDialog.Builder(this)
                .setTitle(R.string.spirit_editor_color)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> { mEditor.setColor(result[0]); updateColor(); })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void save() {
        try(FileOutputStream out = new FileOutputStream(new File(mOut))) {
            out.write(SkinStore.encodePng(mEditor.getSkin()));
        } catch (IOException e) {
            git.artdeell.mojo.utils.SpiritToast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Intent data = new Intent();
        data.putExtra(EXTRA_SLIM, getIntent().getBooleanExtra(EXTRA_SLIM, false));
        setResult(Activity.RESULT_OK, data);
        finish();
    }
}
