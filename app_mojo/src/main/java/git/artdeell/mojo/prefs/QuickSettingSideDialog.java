package git.artdeell.mojo.prefs;

import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_BUTTONSIZE;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_BUTTON_TRANSPARENCY;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_DISABLE_GESTURES;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_ENABLE_GYRO;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_GYRO_INVERT_X;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_HOTBAR_DROP_DELAY;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_HOTBAR_HOLD_DROP;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_GYRO_INVERT_Y;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_GYRO_SENSITIVITY;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_LONGPRESS_TRIGGER;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_MOUSESPEED;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_SCALE_FACTOR;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.kdt.CustomSeekbar;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.utils.interfaces.SimpleSeekBarListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Side dialog for quick settings that you can change in game
 * The implementation has to take action on some preference changes
 */
public abstract class QuickSettingSideDialog extends com.kdt.SideDialogView {

    public static final String PREF_LIVE_STATS = "spiritLiveStats";
    public static final String PREF_SHOW_FPS = "spiritShowFps";
    /** Performance mode: 70 % resolution + the Performance profile's game options on the next start */
    public static final String PREF_PERF_MODE = "spiritPerfMode";
    public static final String PREF_SUSTAINED = "sustainedPerformance";
    private static boolean sFpsBoost = false;

    // Control layout switcher

    private SwitchCompat mShowControlsSwitch, mSustainedSwitch;
    private CustomSeekbar mButtonSizeBar;
    private TextView mButtonSizeText;
    private float mOriginalButtonSize;
    private boolean mButtonSizeChanged;

    private SharedPreferences.Editor mEditor;

    private SwitchCompat mGyroSwitch, mGyroXSwitch, mGyroYSwitch, mGestureSwitch, mHotbarDropSwitch;
    private CustomSeekbar mGyroSensitivityBar, mMouseSpeedBar, mGestureDelayBar, mResolutionBar, mTransparencyBar, mHotbarDropDelayBar;
    private TextView mGyroSensitivityText, mGyroSensitivityDisplayText, mMouseSpeedText, mGestureDelayText, mGestureDelayDisplayText, mResolutionText, mTransparencyText, mHotbarDropDelayText, mHotbarDropDelayDisplayText;

    private boolean mOriginalGyroEnabled, mOriginalGyroXEnabled, mOriginalGyroYEnabled, mOriginalGestureDisabled;
    private float mOriginalGyroSensitivity, mOriginalMouseSpeed, mOriginalResolution;
    private int mOriginalGestureDelay, mOriginalHotbarDropDelay;
    private boolean mOriginalHotbarDrop;
    private short mOriginalTransparency;

    public QuickSettingSideDialog(Context context, ViewGroup parent) {
        super(context, parent, R.layout.dialog_quick_setting);
        setTitle(R.string.quick_setting_title);
        setupCancelButton();
    }

    @Override
    protected void onInflate() {
        bindLayout();
        Tools.runOnUiThread(() -> {
            this.setupListeners();
            this.updateGyroCompatibility();
        });
    }

    @Override
    protected void onDestroy() {
        removeListeners();
    }

    private void bindLayout() {
        androidx.appcompat.widget.SwitchCompat liveStats = mDialogContent.findViewById(R.id.spirit_switch_live_stats);
        if(liveStats != null) {
            liveStats.setChecked(LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_LIVE_STATS, false));
            liveStats.setOnCheckedChangeListener((b, on) -> {
                LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF_LIVE_STATS, on).apply();
                onLiveStatsChanged(on);
            });
        }
        androidx.appcompat.widget.SwitchCompat showFps = mDialogContent.findViewById(R.id.spirit_switch_show_fps);
        if(showFps != null) {
            showFps.setChecked(LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_SHOW_FPS, false));
            showFps.setOnCheckedChangeListener((b, on) -> {
                LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF_SHOW_FPS, on).apply();
                onShowFpsChanged(on);
            });
        }
        androidx.appcompat.widget.SwitchCompat exactFps = mDialogContent.findViewById(R.id.spirit_switch_exact_fps);
        if(exactFps != null) {
            exactFps.setChecked(LauncherPreferences.DEFAULT_PREF.getBoolean(git.artdeell.mojo.utils.jre.GameRunner.SPIRIT_EXACT_FPS_PREF, true));
            exactFps.setOnCheckedChangeListener((b, on) -> {
                LauncherPreferences.DEFAULT_PREF.edit().putBoolean(git.artdeell.mojo.utils.jre.GameRunner.SPIRIT_EXACT_FPS_PREF, on).apply();
                git.artdeell.mojo.utils.SpiritToast.makeText(mDialogContent.getContext(), R.string.spirit_exact_fps_restart, android.widget.Toast.LENGTH_SHORT).show();
            });
        }
        android.widget.SeekBar hudSeek = mDialogContent.findViewById(R.id.spirit_hud_scale_seek);
        android.widget.TextView hudLabel = mDialogContent.findViewById(R.id.spirit_hud_scale_label);
        if(hudSeek != null) {
            // progress 0..100 -> 60%..160%
            hudSeek.setProgress(Math.round((git.artdeell.mojo.game.hud.HudStyle.globalScale() - 0.6f) * 100f));
            if(hudLabel != null) hudLabel.setText(mDialogContent.getContext().getString(R.string.spirit_hud_size_value, 60 + hudSeek.getProgress()));
            hudSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar sb, int progress, boolean fromUser) {
                    if(hudLabel != null) hudLabel.setText(sb.getContext().getString(R.string.spirit_hud_size_value, 60 + progress));
                    if(!fromUser) return;
                    git.artdeell.mojo.game.hud.HudStyle.setGlobalScale(0.6f + progress / 100f);
                    onHudScaleChanged();
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {}
            });
        }
        androidx.appcompat.widget.SwitchCompat fpsBoost = mDialogContent.findViewById(R.id.spirit_switch_fps_boost);
        if(fpsBoost != null) {
            fpsBoost.setChecked(LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_PERF_MODE, false));
            fpsBoost.setOnCheckedChangeListener((b, on) -> {
                sFpsBoost = on;
                LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF_PERF_MODE, on).apply();
                onFpsBoosterChanged(on);
            });
        }
        bindControlSwitcher();
        bindPerformanceExtras();
        View changeSkin = mDialogContent.findViewById(R.id.spirit_qs_change_skin);
        if(changeSkin != null) {
            if(getSkinGameDir() == null) changeSkin.setVisibility(View.GONE);
            else changeSkin.setOnClickListener(v -> git.artdeell.mojo.wardrobe.InGameSkin.show((android.app.Activity) mDialogContent.getContext(), getSkinGameDir(), getSkinAccount()));
        }
        // Bind layout elements
        mGyroSwitch = mDialogContent.findViewById(R.id.checkboxGyro);
        mGyroXSwitch = mDialogContent.findViewById(R.id.checkboxGyroX);
        mGyroYSwitch = mDialogContent.findViewById(R.id.checkboxGyroY);
        mGestureSwitch = mDialogContent.findViewById(R.id.checkboxGesture);
        mHotbarDropSwitch = mDialogContent.findViewById(R.id.checkboxHotbarDrop);
        mHotbarDropDelayBar = mDialogContent.findViewById(R.id.editHotbarDropDelay_seekbar);
        mHotbarDropDelayText = mDialogContent.findViewById(R.id.editHotbarDropDelay_textView_percent);
        mHotbarDropDelayDisplayText = mDialogContent.findViewById(R.id.editHotbarDropDelay_textView);

        mGyroSensitivityBar = mDialogContent.findViewById(R.id.editGyro_seekbar);
        mMouseSpeedBar = mDialogContent.findViewById(R.id.editMouseSpeed_seekbar);
        mGestureDelayBar = mDialogContent.findViewById(R.id.editGestureDelay_seekbar);
        mResolutionBar = mDialogContent.findViewById(R.id.editResolution_seekbar);
        mTransparencyBar = mDialogContent.findViewById(R.id.buttonTransparency_seekBar);

        mGyroSensitivityText = mDialogContent.findViewById(R.id.editGyro_textView_percent);
        mGyroSensitivityDisplayText = mDialogContent.findViewById(R.id.editGyro_textView);
        mMouseSpeedText = mDialogContent.findViewById(R.id.editMouseSpeed_textView_percent);
        mGestureDelayText = mDialogContent.findViewById(R.id.editGestureDelay_textView_percent);
        mGestureDelayDisplayText = mDialogContent.findViewById(R.id.editGestureDelay_textView);
        mResolutionText = mDialogContent.findViewById(R.id.editResolution_textView_percent);
        mTransparencyText = mDialogContent.findViewById(R.id.buttonTransparency_textView_percent);
    }

    private void setupListeners() {
        mEditor = LauncherPreferences.DEFAULT_PREF.edit();

        mOriginalGyroEnabled = PREF_ENABLE_GYRO;
        mOriginalGyroXEnabled = PREF_GYRO_INVERT_X;
        mOriginalGyroYEnabled = PREF_GYRO_INVERT_Y;
        mOriginalGestureDisabled = PREF_DISABLE_GESTURES;
        mOriginalHotbarDrop = PREF_HOTBAR_HOLD_DROP;
        mOriginalHotbarDropDelay = PREF_HOTBAR_DROP_DELAY;

        mOriginalGyroSensitivity = PREF_GYRO_SENSITIVITY;
        mOriginalMouseSpeed = PREF_MOUSESPEED;
        mOriginalGestureDelay = PREF_LONGPRESS_TRIGGER;
        mOriginalResolution = PREF_SCALE_FACTOR;
        mOriginalTransparency = PREF_BUTTON_TRANSPARENCY;
        mOriginalButtonSize = PREF_BUTTONSIZE;

        mGyroSwitch.setChecked(mOriginalGyroEnabled);
        mGyroXSwitch.setChecked(mOriginalGyroXEnabled);
        mGyroYSwitch.setChecked(mOriginalGyroYEnabled);
        mGestureSwitch.setChecked(mOriginalGestureDisabled);
        mHotbarDropSwitch.setChecked(mOriginalHotbarDrop);

        mGyroSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PREF_ENABLE_GYRO = isChecked;
            onGyroStateChanged();
            updateGyroVisibility(isChecked);
            mEditor.putBoolean("enableGyro", isChecked);
        });

        mGyroXSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PREF_GYRO_INVERT_X = isChecked;
            onGyroStateChanged();
            mEditor.putBoolean("gyroInvertX", isChecked);
        });

        mGyroYSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PREF_GYRO_INVERT_Y = isChecked;
            onGyroStateChanged();
            mEditor.putBoolean("gyroInvertY", isChecked);
        });

        mGestureSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PREF_DISABLE_GESTURES = isChecked;
            updateGestureVisibility(isChecked);
            mEditor.putBoolean("disableGestures", isChecked);
        });

        mHotbarDropSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PREF_HOTBAR_HOLD_DROP = isChecked;
            updateHotbarDropVisibility(isChecked);
            mEditor.putBoolean("hotbarHoldDrop", isChecked);
        });

        mHotbarDropDelayBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_HOTBAR_DROP_DELAY = progress;
            mEditor.putInt("hotbarDropDelay", progress);
            setSeekTextMillisecond(mHotbarDropDelayText, progress);
        });
        mHotbarDropDelayBar.setProgress(mOriginalHotbarDropDelay);
        setSeekTextMillisecond(mHotbarDropDelayText, mHotbarDropDelayBar.getProgress());

        mGyroSensitivityBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_GYRO_SENSITIVITY = progress / 100f;
            mEditor.putInt("gyroSensitivity", progress);
            setSeekTextPercent(mGyroSensitivityText, progress);
        });
        mGyroSensitivityBar.setProgress((int) (mOriginalGyroSensitivity * 100f));
        setSeekTextPercent(mGyroSensitivityText, mGyroSensitivityBar.getProgress());

        mMouseSpeedBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_MOUSESPEED = progress / 100f;
            mEditor.putInt("mousespeed", progress);
            setSeekTextPercent(mMouseSpeedText, progress);
        });
        mMouseSpeedBar.setProgress((int) (mOriginalMouseSpeed * 100f));
        setSeekTextPercent(mMouseSpeedText, mMouseSpeedBar.getProgress());

        mGestureDelayBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_LONGPRESS_TRIGGER = progress;
            mEditor.putInt("timeLongPressTrigger", progress);
            setSeekTextMillisecond(mGestureDelayText, progress);
        });
        mGestureDelayBar.setProgress(mOriginalGestureDelay);
        setSeekTextMillisecond(mGestureDelayText, mGestureDelayBar.getProgress());

        mResolutionBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_SCALE_FACTOR = progress/100f;
            mEditor.putInt("resolutionRatio", progress);
            setSeekTextPercent(mResolutionText, progress);
            onResolutionChanged();
        });
        mResolutionBar.setProgress((int) (mOriginalResolution * 100));
        setSeekTextPercent(mResolutionText, mResolutionBar.getProgress());

        mTransparencyBar.setProgress(PREF_BUTTON_TRANSPARENCY);
        setSeekTextPercent(mTransparencyText, mTransparencyBar.getProgress());
        mTransparencyBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_BUTTON_TRANSPARENCY = (short) progress;
            mEditor.putInt("buttonTransparency", progress);
            setSeekTextPercent(mTransparencyText, progress);
            onButtonTransparencyChanged();
        });


        if(mButtonSizeBar != null && mButtonSizeText != null) {
            mButtonSizeBar.setProgress((int) mOriginalButtonSize);
            setSeekTextPercent(mButtonSizeText, mButtonSizeBar.getProgress());
            mButtonSizeBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
                PREF_BUTTONSIZE = progress;
                mEditor.putInt("buttonscale", progress);
                mButtonSizeChanged = true;
                setSeekTextPercent(mButtonSizeText, progress);
            });
        }

        updateGyroVisibility(mOriginalGyroEnabled);
        updateGestureVisibility(mOriginalGestureDisabled);
        updateHotbarDropVisibility(mOriginalHotbarDrop);
    }

    private static void setSeekTextMillisecond(TextView target, int value) {
        setSeekText(target, R.string.millisecond_format, value);
    }

    private static void setSeekTextPercent(TextView target, int value) {
        setSeekText(target, R.string.percent_format, value);
    }

    private static void setSeekText(TextView target, int format, int value) {
        target.setText(target.getContext().getString(format, value));
    }

    private void updateGyroVisibility(boolean isEnabled) {
        int visibility = isEnabled ? View.VISIBLE : View.GONE;
        mGyroXSwitch.setVisibility(visibility);
        mGyroYSwitch.setVisibility(visibility);

        mGyroSensitivityBar.setVisibility(visibility);
        mGyroSensitivityText.setVisibility(visibility);
        mGyroSensitivityDisplayText.setVisibility(visibility);
    }

    private void updateGyroCompatibility() {
        boolean isGyroAvailable = Tools.deviceSupportsGyro(mDialogContent.getContext());
        if (!isGyroAvailable) {
            mGyroSwitch.setVisibility(View.GONE);
            View gyroSection = mDialogContent.findViewById(R.id.spirit_qs_gyro_section);
            if(gyroSection != null) gyroSection.setVisibility(View.GONE);
            updateGestureVisibility(false);
        }
    }

    private void updateGestureVisibility(boolean isDisabled) {
        int visibility = isDisabled ? View.GONE : View.VISIBLE;
        mGestureDelayBar.setVisibility(visibility);
        mGestureDelayText.setVisibility(visibility);
        mGestureDelayDisplayText.setVisibility(visibility);
    }

    /** SPIRIT: delay slider sirf tab dikhta hai jab hold-to-drop ON ho. */
    private void updateHotbarDropVisibility(boolean isEnabled) {
        int visibility = isEnabled ? View.VISIBLE : View.GONE;
        mHotbarDropDelayBar.setVisibility(visibility);
        mHotbarDropDelayText.setVisibility(visibility);
        mHotbarDropDelayDisplayText.setVisibility(visibility);
    }

    private void removeListeners() {
        mHotbarDropSwitch.setOnCheckedChangeListener(null);
        mHotbarDropDelayBar.setOnSeekBarChangeListener(null);
        mGyroSwitch.setOnCheckedChangeListener(null);
        mGyroXSwitch.setOnCheckedChangeListener(null);
        mGyroYSwitch.setOnCheckedChangeListener(null);
        mGestureSwitch.setOnCheckedChangeListener(null);

        mGyroSensitivityBar.setOnSeekBarChangeListener(null);
        mMouseSpeedBar.setOnSeekBarChangeListener(null);
        mGestureDelayBar.setOnSeekBarChangeListener(null);
        mResolutionBar.setOnSeekBarChangeListener(null);
        mTransparencyBar.setOnSeekBarChangeListener(null);
        if(mButtonSizeBar != null) mButtonSizeBar.setOnSeekBarChangeListener(null);
        if(mShowControlsSwitch != null) mShowControlsSwitch.setOnCheckedChangeListener(null);
        if(mSustainedSwitch != null) mSustainedSwitch.setOnCheckedChangeListener(null);
    }

    private void setupCancelButton() {
        setStartButtonListener(android.R.string.cancel, v -> cancel());
        setEndButtonListener(android.R.string.ok, v -> {
            mEditor.apply();
            // Button size needs the layout rebuilt, so it is applied once, here
            if(mButtonSizeChanged) {
                mButtonSizeChanged = false;
                onButtonSizeApplied();
            }
            disappear(true);
        });
    }

    /** Resets all settings to their original values */
    public void cancel() {
        // Reset all settings if we were editing
        if(isDisplaying()) {
            PREF_ENABLE_GYRO = mOriginalGyroEnabled;
            PREF_GYRO_INVERT_X = mOriginalGyroXEnabled;
            PREF_GYRO_INVERT_Y = mOriginalGyroYEnabled;
            PREF_DISABLE_GESTURES = mOriginalGestureDisabled;
            PREF_HOTBAR_HOLD_DROP = mOriginalHotbarDrop;
            PREF_HOTBAR_DROP_DELAY = mOriginalHotbarDropDelay;

            PREF_GYRO_SENSITIVITY = mOriginalGyroSensitivity;
            PREF_MOUSESPEED = mOriginalMouseSpeed;
            PREF_LONGPRESS_TRIGGER = mOriginalGestureDelay;
            PREF_SCALE_FACTOR = mOriginalResolution;
            PREF_BUTTON_TRANSPARENCY = mOriginalTransparency;
            PREF_BUTTONSIZE = mOriginalButtonSize;
            mButtonSizeChanged = false;

            onGyroStateChanged();
            onResolutionChanged();
            onButtonTransparencyChanged();
        }

        disappear(true);
    }

    /** Called when the resolution is changed. Use {@link LauncherPreferences#PREF_SCALE_FACTOR} */
    public abstract void onResolutionChanged();

    /** Called when the gyro state is changed.
     * Use {@link LauncherPreferences#PREF_ENABLE_GYRO}
     * Use {@link LauncherPreferences#PREF_GYRO_INVERT_X}
     * Use {@link LauncherPreferences#PREF_GYRO_INVERT_Y}
     */
    public abstract void onGyroStateChanged();

    /**
     * Called when the button transparency state is changed. Use {@link LauncherPreferences#PREF_BUTTON_TRANSPARENCY}
     */
    public abstract void onButtonTransparencyChanged();

    /** Spirit: path of the control layout in use right now, or null when unknown. */
    public String getCurrentControlPath() { return null; }

    /** Spirit: the user picked a control layout; switch to it right away. */
    public void onSelectControl(String path) {}

    /** Spirit: whether the on-screen controls are currently shown. */
    public boolean areControlsVisible() { return true; }

    /** Spirit: "Show on-screen controls" toggled. */
    public void onShowControlsChanged(boolean visible) {}

    /** Spirit: the button size was changed and confirmed with OK, so the layout must be rebuilt. */
    public void onButtonSizeApplied() {}

    /** Spirit: the instance folder used by the in-game skin change, null hides the button. */
    @androidx.annotation.Nullable
    public java.io.File getSkinGameDir() { return null; }

    /** Spirit: the account the running game was started with */
    @androidx.annotation.Nullable
    public git.artdeell.mojo.authenticator.accounts.Account getSkinAccount() { return null; }

    /** Spirit: sustained performance mode toggled. */
    public void onSustainedPerformanceChanged(boolean enabled) {}

    /** Spirit: false hides the sustained performance switch (not supported on this device). */
    public boolean isSustainedPerformanceSupported() { return false; }

    // ---------------- Spirit: instant control switching ----------------

    private void bindControlSwitcher() {
        mShowControlsSwitch = mDialogContent.findViewById(R.id.spirit_switch_show_controls);
        if(mShowControlsSwitch != null) {
            mShowControlsSwitch.setChecked(areControlsVisible());
            mShowControlsSwitch.setOnCheckedChangeListener((b, on) -> onShowControlsChanged(on));
        }

        mButtonSizeBar = mDialogContent.findViewById(R.id.spirit_buttonsize_seekbar);
        mButtonSizeText = mDialogContent.findViewById(R.id.spirit_buttonsize_textView_percent);
    }

    private void bindPerformanceExtras() {
        mSustainedSwitch = mDialogContent.findViewById(R.id.spirit_switch_sustained);
        if(mSustainedSwitch == null) return;
        if(!isSustainedPerformanceSupported()) {
            mSustainedSwitch.setVisibility(View.GONE);
            return;
        }
        mSustainedSwitch.setChecked(LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);
        mSustainedSwitch.setOnCheckedChangeListener((b, on) -> {
            LauncherPreferences.PREF_SUSTAINED_PERFORMANCE = on;
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF_SUSTAINED, on).apply();
            onSustainedPerformanceChanged(on);
        });
    }

    /** Spirit: Live RAM overlay toggled. */
    public void onLiveStatsChanged(boolean enabled) {}

    /** Spirit: the HUD size slider moved. */
    public void onHudScaleChanged() {}

    /** Spirit: FPS overlay toggled. */
    public void onShowFpsChanged(boolean enabled) {}

    /** Spirit: FPS booster toggled (this session only). */
    public void onFpsBoosterChanged(boolean enabled) {}

}
