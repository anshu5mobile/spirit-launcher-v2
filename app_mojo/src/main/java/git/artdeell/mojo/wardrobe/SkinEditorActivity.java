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
    private String mOut;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        mOut = getIntent().getStringExtra(EXTRA_OUT);
        String in = getIntent().getStringExtra(EXTRA_IN);
        Bitmap skin = in == null ? null : BitmapFactory.decodeFile(in);
        if(skin == null || mOut == null) {
            Toast.makeText(this, R.string.spirit_editor_nofile, Toast.LENGTH_LONG).show();
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = dp(8); lp.bottomMargin = dp(8);
        mEditor.setLayoutParams(lp);
        root.addView(mEditor);

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
        mColorPreview.setOnClickListener(v -> askHex());
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

    private void askHex() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("#A6F03A");
        input.setText(String.format("#%06X", mEditor.getColor() & 0xFFFFFF));
        new AlertDialog.Builder(this)
                .setTitle(R.string.spirit_editor_color)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        String t = input.getText().toString().trim();
                        if(!t.startsWith("#")) t = "#" + t;
                        mEditor.setColor(Color.parseColor(t));
                        updateColor();
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(this, R.string.spirit_editor_badcolor, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void save() {
        try(FileOutputStream out = new FileOutputStream(new File(mOut))) {
            out.write(SkinStore.encodePng(mEditor.getSkin()));
        } catch (IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Intent data = new Intent();
        data.putExtra(EXTRA_SLIM, getIntent().getBooleanExtra(EXTRA_SLIM, false));
        setResult(Activity.RESULT_OK, data);
        finish();
    }
}
