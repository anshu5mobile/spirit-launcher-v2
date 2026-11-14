package git.artdeell.mojo.game;


import static git.artdeell.mojo.Tools.dialogForceClose;
import static git.artdeell.mojo.game.platform.Platform.PLATFORM;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_ENABLE_GYRO;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_SUSTAINED_PERFORMANCE;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_USE_ALTERNATE_SURFACE;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_VIRTUAL_MOUSE_START;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.kdt.LoggerView;

import git.artdeell.mojo.BaseActivity;
import git.artdeell.mojo.CallbackBridge;
import git.artdeell.mojo.game.renderer.GameRenderer;
import git.artdeell.mojo.utils.GpuUtils;
import git.artdeell.mojo.utils.KeycodeUtils;
import git.artdeell.mojo.Logger;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.input.ControlButtonMenuListener;
import git.artdeell.mojo.input.ControlData;
import git.artdeell.mojo.input.ControlDrawerData;
import git.artdeell.mojo.input.ControlJoystickData;
import git.artdeell.mojo.input.ControlLayout;
import git.artdeell.mojo.input.CustomControls;
import git.artdeell.mojo.input.EditorExitable;
import git.artdeell.mojo.input.keyboard.TouchCharInput;
import git.artdeell.mojo.input.mouse.GyroControl;
import git.artdeell.mojo.input.mouse.HotbarView;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.utils.lifecycle.ContextExecutor;
import git.artdeell.mojo.game.platform.Platform;
import git.artdeell.mojo.game.platform.backend.DummyBackend;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.prefs.QuickSettingSideDialog;
import git.artdeell.mojo.services.GameService;
import git.artdeell.mojo.tasks.AsyncAssetManager;
import git.artdeell.mojo.utils.JREUtils;
import git.artdeell.mojo.utils.MCOptionUtils;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.utils.jre.GameRunner;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Objects;

import git.artdeell.mojo.R;

public class GameActivity extends BaseActivity implements ControlButtonMenuListener, EditorExitable, ServiceConnection {
    public static final String INTENT_LAUNCH_VERSION = "intent_version";
    public static final String INTENT_LAUNCH_CLASSPATH = "intent_classpath";

    public static TouchCharInput touchCharInput;
    private GameView launcherGLView;
    private static WeakReference<GameCursorView> weakCursor;
    private LoggerView loggerView;
    private DrawerLayout drawerLayout;
    private ListView navDrawer;
    private View mDrawerPullButton;
    private GyroControl mGyroControl = null;
    private ControlLayout mControlLayout;
    private HotbarView mHotbarView;
    private View mLoadingScreen;
    private GameRenderer mGameRenderer;

    Instance instance;
    Account account;

    private ArrayAdapter<String> gameActionArrayAdapter;
    private AdapterView.OnItemClickListener gameActionClickListener;
    public ArrayAdapter<String> ingameControlsEditorArrayAdapter;
    public AdapterView.OnItemClickListener ingameControlsEditorListener;
    private GameService.LocalBinder mServiceBinder;

    private QuickSettingSideDialog mQuickSettingSideDialog;

    public static int mForcedPanningHeight = 0;
    public static int mImeHeight = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = Instances.loadSelectedInstance();
        account = Accounts.getCurrent();
        if(instance == null) {
            Toast.makeText(this, R.string.instance_dir_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        mGameRenderer = new GameRenderer(instance.getLaunchRenderer());

        if(!PREF_ZINK_PREFER_SYSTEM_DRIVER) {
            mGameRenderer.overrideVulkanDriver();
        }

        AsyncAssetManager.extractDefaultSettings(this, instance.getGameDirectory());
        MCOptionUtils.load(instance.getGameDirectory().getAbsolutePath());

        Intent gameServiceIntent = new Intent(this, GameService.class);
        // Start the service a bit early
        ContextCompat.startForegroundService(this, gameServiceIntent);
        initLayout(R.layout.activity_basemain);

        Platform.initialize(this, launcherGLView);

        mGyroControl = new GyroControl(this);

        // Enabling this on TextureView results in a broken white result
        if(PREF_USE_ALTERNATE_SURFACE) getWindow().setBackgroundDrawable(null);
        else getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        // Set the sustained performance mode for available APIs
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            getWindow().setSustainedPerformanceMode(PREF_SUSTAINED_PERFORMANCE);

        // This is required on Android 10 for the insets listener
        // https://issuetracker.google.com/issues/266331465
        boolean androidCompat = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q;
        if(androidCompat)
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // Make keyboard pan the activity so the user sees what they're typing
        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (view, insets) -> {
            if(launcherGLView.mSurface == null)
                return insets;
            ViewPropertyAnimator animSurface = launcherGLView.mSurface.animate()
                    .setDuration(100);
            ViewPropertyAnimator animCursor = launcherGLView.mCursorView.animate()
                    .setDuration(100);
            if(!insets.isVisible(WindowInsetsCompat.Type.ime())){
                animSurface.translationY(0).start();
                animCursor.translationY(0).start();
                mImeHeight = 0;
                if(androidCompat) {
                    // AndroidX keeps SystemUI visible for some reason after IME session
                    view.postDelayed(() -> {
                        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN);
                    }, 150);
                }
                return insets;
            }
            if(mForcedPanningHeight == 0 && !LauncherPreferences.PREF_KEYBOARD_AUTOPANNING)
                return insets;
            mImeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int translationY;
            // Autopanning (if keyboardPan wasn't clicked)
            if(mForcedPanningHeight == 0) {
                translationY = Tools.getTranslationFromCursorY(
                        (int)(Platform.cursorY * launcherGLView.getCursorRatioY() + 100),
                        launcherGLView.getHeight(),
                        mImeHeight,
                        0
                );
            } else
                translationY = mForcedPanningHeight == -1 ? mImeHeight : Math.clamp(mImeHeight - mForcedPanningHeight, 0, mImeHeight);
            animSurface.translationY(-translationY).start();
            animCursor.translationY(-translationY).start();
            return insets;
        });

        ingameControlsEditorArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.menu_customcontrol));
        ingameControlsEditorListener = (parent, view, position, id) -> {
            switch(position) {
                case 0: mControlLayout.addControlButton(new ControlData("New")); break;
                case 1: mControlLayout.addDrawer(new ControlDrawerData()); break;
                case 2: mControlLayout.addJoystickButton(new ControlJoystickData()); break;
                case 3: mControlLayout.openLoadDialog(); break;
                case 4: mControlLayout.openSaveDialog(this); break;
                case 5: mControlLayout.openSetDefaultDialog(); break;
                case 6: mControlLayout.openExitDialog(this);
            }
        };

        // Recompute the gui scale when options are changed
        MCOptionUtils.MCOptionListener optionListener = MCOptionUtils::getMcScale;
        MCOptionUtils.addMCOptionListener(optionListener);
        mControlLayout.setModifiable(false);
        mControlLayout.post(() -> setLiveStatsEnabled(LauncherPreferences.DEFAULT_PREF.getBoolean(QuickSettingSideDialog.PREF_LIVE_STATS, false)));

        // Set the activity for the executor. Must do this here, or else Tools.showErrorRemote() may not
        // execute the correct method
        ContextExecutor.setActivity(this);
        //Now, attach to the service. The game will only start when this happens, to make sure that we know the right state.
        bindService(gameServiceIntent, this, 0);
    }

    protected void initLayout(int resId) {
        setContentView(resId);
        bindValues();
        mControlLayout.setMenuListener(this);

        mDrawerPullButton.setOnClickListener(v -> onClickedMenu());
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        launcherGLView.mCursorView.setCursorScale(LauncherPreferences.PREF_MOUSESCALE);
        weakCursor = new WeakReference<>(launcherGLView.mCursorView);

        try {
            File latestLogFile = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
            if(!latestLogFile.exists() && !latestLogFile.createNewFile())
                throw new IOException("Failed to create a new log file");
            Logger.begin(latestLogFile.getAbsolutePath());

            Intent activityIntent = getIntent();
            Bundle extras = Objects.requireNonNull(activityIntent.getExtras());
            String version = extras.getString(INTENT_LAUNCH_VERSION);
            File[] classpath = (File[]) extras.getSerializable(INTENT_LAUNCH_CLASSPATH);

            activityIntent.removeExtra(INTENT_LAUNCH_VERSION);
            activityIntent.removeExtra(INTENT_LAUNCH_CLASSPATH);

            setIntent(activityIntent);

            setTitle("Spirit Launcher (" + version + ")");
            populateLoadingScreen(version);

            // Menu
            gameActionArrayAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.menu_ingame));
            gameActionClickListener = (parent, view, position, id) -> {
                switch(position) {
                     case 0: dialogForceClose(GameActivity.this); break;
                     case 1: openLogOutput(); break;
                     case 2: dialogSendCustomKey(); break;
                     case 3: openQuickSettings(); break;
                     case 4: openCustomControls(); break;
                }
                drawerLayout.closeDrawers();
            };
            navDrawer.setAdapter(gameActionArrayAdapter);
            navDrawer.setOnItemClickListener(gameActionClickListener);
            drawerLayout.closeDrawers();

            launcherGLView.setSurfaceReadyListener(() -> {
                try {
                    Tools.runOnUiThread(() -> { if(PREF_VIRTUAL_MOUSE_START) launcherGLView.mCursorView.setVisibility(View.VISIBLE); });
                    if(version == null || classpath == null) {
                        Tools.runOnUiThread(()->{
                            Toast.makeText(this, R.string.main_please_restart, Toast.LENGTH_LONG).show();
                            finish();
                        });
                        return;
                    }
                    runCraft(version, classpath);
                }catch (Throwable e){
                    Tools.showErrorRemote(e);
                }
            });
        } catch (Throwable e) {
            Tools.showError(this, e, true);
        }
    }

    private void populateLoadingScreen(String versionId) {
        if(mLoadingScreen == null || versionId == null) return;
        TextView nameView = mLoadingScreen.findViewById(R.id.main_loading_name);
        TextView versionView = mLoadingScreen.findViewById(R.id.main_loading_version);
        String name = null;
        // DisplayInstance holds the profile's display name; read it via reflection so a
        // different field/getter name can never break the build.
        try {
            java.lang.reflect.Field f = instance.getClass().getField("name");
            Object v = f.get(instance);
            if(v instanceof String) name = (String) v;
        } catch (Throwable ignored) {}
        if(name == null || name.trim().isEmpty()) {
            try {
                Object v = instance.getClass().getMethod("getName").invoke(instance);
                if(v instanceof String) name = (String) v;
            } catch (Throwable ignored) {}
        }
        if(name == null || name.trim().isEmpty()) name = versionId;
        nameView.setText(name);
        versionView.setText(prettyVersion(versionId));
    }

    private static String prettyVersion(String id) {
        java.util.regex.Matcher m;
        m = java.util.regex.Pattern.compile("^(fabric|quilt)-loader-([0-9.]+)-(.+)$").matcher(id);
        if(m.matches()) {
            String l = m.group(1);
            return "Minecraft " + m.group(3) + " \u00b7 " + Character.toUpperCase(l.charAt(0)) + l.substring(1) + " " + m.group(2);
        }
        m = java.util.regex.Pattern.compile("^(.+)-forge-(.+)$").matcher(id);
        if(m.matches()) return "Minecraft " + m.group(1) + " \u00b7 Forge " + m.group(2);
        m = java.util.regex.Pattern.compile("^neoforge-(.+)$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(id);
        if(m.matches()) return "NeoForge " + m.group(1);
        return "Minecraft " + id;
    }

    // ---------------- Spirit: live RAM counter + FPS booster ----------------
    private TextView mStatsView;
    private boolean mStatsEnabled = false;
    private float mOriginalScaleBeforeBoost = -1f;
    private final Runnable mStatsUpdater = new Runnable() {
        @Override
        public void run() {
            if(!mStatsEnabled || mStatsView == null) return;
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                android.os.Debug.MemoryInfo[] pm = am.getProcessMemoryInfo(new int[]{android.os.Process.myPid()});
                long usedMb = pm[0].getTotalPss() / 1024;
                long freeMb = mi.availMem / (1024 * 1024);
                mStatsView.setText("RAM " + usedMb + " MB  |  free " + freeMb + " MB");
            } catch (Throwable ignored) {}
            mStatsView.postDelayed(this, 1000);
        }
    };

    private void setLiveStatsEnabled(boolean enabled) {
        mStatsEnabled = enabled;
        if(enabled) {
            if(mStatsView == null) {
                ViewGroup frame = findViewById(R.id.content_frame);
                if(frame == null) return;
                mStatsView = new TextView(this);
                mStatsView.setTextColor(0xFFA6F03A);
                mStatsView.setTextSize(11);
                mStatsView.setBackgroundColor(0x990A0C09);
                int pad = (int) (6 * getResources().getDisplayMetrics().density);
                mStatsView.setPadding(pad, pad / 2, pad, pad / 2);
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
                mStatsView.setLayoutParams(lp);
                mStatsView.setTranslationZ(5);
                mStatsView.setClickable(false);
                frame.addView(mStatsView);
            }
            mStatsView.setVisibility(View.VISIBLE);
            mStatsView.removeCallbacks(mStatsUpdater);
            mStatsView.post(mStatsUpdater);
        } else if(mStatsView != null) {
            mStatsView.removeCallbacks(mStatsUpdater);
            mStatsView.setVisibility(View.GONE);
        }
    }

    private void applyFpsBooster(boolean enabled) {
        if(enabled) {
            if(mOriginalScaleBeforeBoost < 0) mOriginalScaleBeforeBoost = LauncherPreferences.PREF_SCALE_FACTOR;
            LauncherPreferences.PREF_SCALE_FACTOR = Math.min(LauncherPreferences.PREF_SCALE_FACTOR, 0.6f);
        } else if(mOriginalScaleBeforeBoost > 0) {
            LauncherPreferences.PREF_SCALE_FACTOR = mOriginalScaleBeforeBoost;
            mOriginalScaleBeforeBoost = -1f;
        }
        launcherGLView.refreshSize();
        mHotbarView.onResolutionChanged();
    }

    private void loadControls() {
        try {
            // Load keys
            mControlLayout.loadLayout(instance.getLaunchControls());
        } catch(IOException e) {
            try {
                Log.w("MainActivity", "Unable to load the control file, loading the default now", e);
                mControlLayout.loadLayout(Tools.CTRLDEF_FILE);
            } catch (IOException ioException) {
                Tools.showError(this, ioException);
            }
        } catch (Throwable th) {
            Tools.showError(this, th);
        }
        mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
        mControlLayout.toggleControlVisible();
    }

    @Override
    public void onAttachedToWindow() {
        // Post to get the correct display dimensions after layout.
        mControlLayout.post(()->{
            Tools.getDisplayMetrics(this);
            loadControls();
        });
    }

    /** Boilerplate binding */
    private void bindValues(){
        mControlLayout = findViewById(R.id.main_control_layout);
        launcherGLView = findViewById(R.id.main_game_render_view);
        drawerLayout = findViewById(R.id.main_drawer_options);
        navDrawer = findViewById(R.id.main_navigation_view);
        loggerView = findViewById(R.id.mainLoggerView);
        touchCharInput = findViewById(R.id.mainTouchCharInput);
        mDrawerPullButton = findViewById(R.id.drawer_button);
        mHotbarView = findViewById(R.id.hotbar_view);
        mLoadingScreen = findViewById(R.id.main_loading_screen);
    }

    @Override
    public void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        if(PREF_ENABLE_GYRO) mGyroControl.enable();
        PLATFORM.setHovered(true);
    }

    @Override
    protected void onPause() {
        ContextExecutor.clearActivity();
        mGyroControl.disable();
        // Avoid going through the JNI each time.
        if (Platform.isGrabbing()){
            CallbackBridge.sendKeyPress(KeyEvent.KEYCODE_ESCAPE);
        }
        if(mQuickSettingSideDialog != null) {
            mQuickSettingSideDialog.cancel();
        }
        PLATFORM.setHovered(false);
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        PLATFORM.setVisible(true);
    }

    @Override
    protected void onStop() {
        PLATFORM.setVisible(false);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ContextExecutor.clearActivity();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if(mGyroControl != null) mGyroControl.updateOrientation();
        // Layout resize is practically guaranteed on a configuration change, and `onConfigurationChanged`
        // does not implicitly start a layout. So, request a layout and expect the screen dimensions to be valid after the]
        // post.
        if(mControlLayout == null) return;
        mControlLayout.requestLayout();
        mControlLayout.post(()->{
            // Child of mControlLayout, so refreshing size here is correct
            launcherGLView.refreshSize();
            mControlLayout.refreshControlButtonPositions();
        });
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if(mLoadingScreen != null && !(PLATFORM instanceof DummyBackend)) hideLoadingScreen();
        if(launcherGLView != null)  // Useful when backing out of the app
            Tools.MAIN_HANDLER.postDelayed(() -> launcherGLView.refreshSize(), 500);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            // Reload PREF_DEFAULTCTRL_PATH
            // If the storage root got unmounted/unreadable we won't be able to load the file anyway,
            // and MissingStorageActivity will be started.
            if(!Tools.checkStorageRoot(this)) return;
            LauncherPreferences.loadPreferences(getApplicationContext());
            try {
                mControlLayout.loadLayout(LauncherPreferences.PREF_DEFAULTCTRL_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void runCraft(String versionId, File[] classpath) throws Throwable {
        Logger.appendToLog("--------- Starting game with Launcher Debug!");
        Tools.printLauncherInfo(versionId, instance.getLaunchArgs(), mGameRenderer.getCurrentRenderer(), instance.selectedRuntime, this);
        JREUtils.redirectAndPrintJRELog();
        GameRunner.launchGame(this, account, instance, versionId, classpath, mGameRenderer);
        //Note that we actually stall in the above function, even if the game crashes. But let's be safe.
        Tools.runOnUiThread(()-> mServiceBinder.isActive = false);
    }

    private void dialogSendCustomKey() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.control_customkey);
        dialog.setItems(KeycodeUtils.generateKeyName(), (dInterface, position) -> KeycodeUtils.execKeyIndex(position));
        dialog.show();
    }

    boolean isInEditor;
    private void openCustomControls() {
        if(ingameControlsEditorListener == null || ingameControlsEditorArrayAdapter == null) return;

        mControlLayout.setModifiable(true);
        navDrawer.setAdapter(ingameControlsEditorArrayAdapter);
        navDrawer.setOnItemClickListener(ingameControlsEditorListener);
        mDrawerPullButton.setVisibility(View.VISIBLE);
        isInEditor = true;
    }

    private void openLogOutput() {
        loggerView.setVisibility(View.VISIBLE);
    }

    private void openQuickSettings() {
        if(mQuickSettingSideDialog == null) {
            mQuickSettingSideDialog = new QuickSettingSideDialog(this, mControlLayout) {
                @Override
                public void onResolutionChanged() {
                    launcherGLView.refreshSize();
                    mHotbarView.onResolutionChanged();
                }

                @Override
                public void onGyroStateChanged() {
                    mGyroControl.updateOrientation();
                    if (PREF_ENABLE_GYRO) {
                        mGyroControl.enable();
                    } else {
                        mGyroControl.disable();
                    }
                }

                @Override
                public void onButtonTransparencyChanged() {
                    mControlLayout.updateButtonOpacity();
                }

                @Override
                public void onLiveStatsChanged(boolean enabled) {
                    setLiveStatsEnabled(enabled);
                }

                @Override
                public void onFpsBoosterChanged(boolean enabled) {
                    applyFpsBooster(enabled);
                }

                @Override
                public String getCurrentControlPath() {
                    return mControlLayout.getLoadedPath();
                }

                @Override
                public void onSelectControl(String path) {
                    mControlLayout.switchLayout(path, (loaded, error) -> {
                        if(error != null) {
                            Toast.makeText(GameActivity.this, getString(R.string.spirit_qs_switch_failed), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String name = new java.io.File(loaded).getName().replaceAll("(?i)\\.json$", "");
                        Toast.makeText(GameActivity.this, getString(R.string.spirit_qs_switched, name), Toast.LENGTH_SHORT).show();
                        // The new layout may not have its own menu button, so keep the side menu reachable
                        if(!isInEditor) mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
                    });
                }

                @Override
                public boolean areControlsVisible() {
                    return mControlLayout.areControlVisible();
                }

                @Override
                public void onShowControlsChanged(boolean visible) {
                    mControlLayout.setControlVisible(visible);
                }

                @Override
                public void onButtonSizeApplied() {
                    String path = mControlLayout.getLoadedPath();
                    if(path != null) mControlLayout.switchLayout(path, null);
                }

                @Override
                public boolean isSustainedPerformanceSupported() {
                    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
                }

                @Override
                public void onSustainedPerformanceChanged(boolean enabled) {
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        getWindow().setSustainedPerformanceMode(enabled);
                }
            };
        }
        mQuickSettingSideDialog.appear(true);
    }

    public static void toggleMouse(Context ctx) {
        // Avoid going through the JNI each time.
        if (Platform.isGrabbing()) return;
        GameCursorView cursorView = Tools.getWeakReference(weakCursor);
        if(cursorView == null) return;
        int toastString = 0;
        switch (cursorView.getVisibility()) {
            case View.GONE:
            case View.INVISIBLE:
                toastString = R.string.control_mouseon;
                cursorView.setVisibility(View.VISIBLE);
                break;
            case View.VISIBLE:
                toastString = R.string.control_mouseoff;
                cursorView.setVisibility(View.GONE);
                break;
        }

        if(toastString != 0) Toast.makeText(ctx, toastString, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(isInEditor) {
            if(event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if(event.getAction() == KeyEvent.ACTION_DOWN) mControlLayout.askToExit(this);
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        boolean handleEvent;
        if(!(handleEvent = launcherGLView.processKeyEvent(event))) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && !touchCharInput.isEnabled()) {
                if(event.getAction() != KeyEvent.ACTION_UP) return true; // We eat it anyway
                CallbackBridge.sendKeyPress(KeyEvent.KEYCODE_ESCAPE);
                return true;
            }
        }
        return handleEvent;
    }

    public static void switchKeyboardState(boolean panning) {
        if(touchCharInput != null) {
            touchCharInput.switchKeyboardState();
            GameActivity.mForcedPanningHeight = panning ? -1 : 0;
        }
    }
    public static void toggleKeyboardState(boolean state, int panningHeight) {
        if(touchCharInput != null) {
            touchCharInput.setKeyboardState(state);
            GameActivity.mForcedPanningHeight = panningHeight;
        }
    }

    public void hideLoadingScreen(){
        if(mLoadingScreen == null) return;
        ((TextView) mLoadingScreen.findViewById(R.id.main_loading_screen_text)).setText(getString(R.string.loading_screen_booted, PLATFORM.backendName()));
        mLoadingScreen.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> {
                    ((ViewGroup) mLoadingScreen.getParent()).removeView(mLoadingScreen);
                    mLoadingScreen = null;
                })
                .start();
    }

    @Override
    public void onClickedMenu() {
        drawerLayout.openDrawer(navDrawer);
        navDrawer.requestLayout();
    }

    @Override
    public void exitEditor() {
        try {
            mControlLayout.loadLayout((CustomControls)null);
            mControlLayout.setModifiable(false);
            System.gc();
            mControlLayout.loadLayout(instance.getLaunchControls());
            mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
        } catch (Exception e) {
            Tools.showError(this,e);
        }

        navDrawer.setAdapter(gameActionArrayAdapter);
        navDrawer.setOnItemClickListener(gameActionClickListener);
        isInEditor = false;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        GameService.LocalBinder localBinder = (GameService.LocalBinder) service;
        mServiceBinder = localBinder;
        launcherGLView.start(localBinder.isActive);
        localBinder.isActive = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    /*
     * Android 14 (or some devices, at least) seems to dispatch the the captured mouse events as trackball events
     * due to a bug(?) somewhere(????)
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean checkCaptureDispatchConditions(MotionEvent event) {
        int eventSource = event.getSource();
        // On my device, the mouse sends events as a relative mouse device.
        // Not comparing with == here because apparently `eventSource` is a mask that can
        // sometimes indicate multiple sources, like in the case of InputDevice.SOURCE_TOUCHPAD
        // (which is *also* an InputDevice.SOURCE_MOUSE when controlling a cursor)
        return (eventSource & InputDevice.SOURCE_MOUSE_RELATIVE) != 0 ||
                (eventSource & InputDevice.SOURCE_MOUSE) != 0;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent ev) {
        if(Tools.isAndroid8OrHigher() && checkCaptureDispatchConditions(ev))
            return launcherGLView.dispatchCapturedPointerEvent(ev);
        else return super.dispatchTrackballEvent(ev);
    }
}
