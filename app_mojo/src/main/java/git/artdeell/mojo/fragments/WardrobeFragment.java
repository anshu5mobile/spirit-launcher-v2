package git.artdeell.mojo.fragments;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.AuthType;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.authenticator.listener.LoginListener;
import git.artdeell.mojo.browse.BrowseInstaller;
import git.artdeell.mojo.browse.BrowseKind;
import git.artdeell.mojo.browse.BrowseTarget;
import git.artdeell.mojo.browse.ModrinthClient;
import git.artdeell.mojo.extra.ExtraConstants;
import git.artdeell.mojo.extra.ExtraCore;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.utils.SpiritScreen;
import git.artdeell.mojo.wardrobe.AutoSkin;
import git.artdeell.mojo.wardrobe.SkinNet;
import git.artdeell.mojo.wardrobe.SkinPreviewView;
import git.artdeell.mojo.wardrobe.SkinStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Wardrobe: see and change the skin of the selected account. A skin can come from a username, a link,
 * the phone gallery or the saved library. How it is applied depends on the account type:
 * Microsoft uses the official profile service, Ely.by only accepts uploads on its website, and offline
 * accounts get the skin through the CustomSkinLoader mod. All network and disk work runs off the main thread.
 */
public class WardrobeFragment extends Fragment {
    public static final String TAG = "SpiritWardrobeFragment";
    public static final String PREF_AUTO_SKIN = AutoSkin.PREF_ENABLED;

    private static final int MAX_IMPORT_BYTES = 2 * 1024 * 1024;

    private SkinPreviewView mPreview;
    private TextView mStatus, mModel, mCommand, mOfflineInstall;
    private View mActions, mOfflineCard;

    @Nullable private Account mAccount;
    /** The skin on screen. When mPending it is only a preview and was not applied yet. */
    @Nullable private Bitmap mBitmap;
    private boolean mSlim;
    @Nullable private String mUrl;
    @Nullable private String mName;
    private boolean mPending;

    private final ActivityResultLauncher<String> mGalleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::onGalleryPicked);

    private final ActivityResultLauncher<android.content.Intent> mEditorLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if(result.getResultCode() != android.app.Activity.RESULT_OK || result.getData() == null || !isAdded()) return;
                final boolean slim = result.getData().getBooleanExtra(git.artdeell.mojo.wardrobe.SkinEditorActivity.EXTRA_SLIM, false);
                final File out = new File(Tools.DIR_CACHE, "spirit_editor_out.png");
                final String name = mName == null ? getString(R.string.spirit_skin_unnamed) : mName;
                MojoApplication.sExecutorService.execute(() -> {
                    Bitmap edited = android.graphics.BitmapFactory.decodeFile(out.getAbsolutePath());
                    if(edited == null) return;
                    // The edited skin is a new picture: it has no public link until it is applied
                    ui(() -> show(edited, slim, null, name, true));
                });
            });

    public WardrobeFragment() {
        super(R.layout.fragment_spirit_wardrobe);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        SpiritScreen.bindBack(view, requireActivity());
        mPreview = view.findViewById(R.id.spirit_skin_preview);
        mStatus = view.findViewById(R.id.spirit_skin_status);
        mModel = view.findViewById(R.id.spirit_skin_model);
        mActions = view.findViewById(R.id.spirit_skin_actions);
        mCommand = view.findViewById(R.id.spirit_autoskin_cmd);
        mOfflineCard = view.findViewById(R.id.spirit_offline_card);
        mOfflineInstall = view.findViewById(R.id.spirit_offline_install);

        mPreview.setPlaceholder(getString(R.string.spirit_skin_placeholder));
        mStatus.setText(R.string.spirit_skin_loading);

        view.findViewById(R.id.spirit_skin_username_card).setOnClickListener(v ->
                promptText(R.string.spirit_skin_username, R.string.spirit_skin_username_hint, InputType.TYPE_CLASS_TEXT, null, this::fetchByUsername));
        view.findViewById(R.id.spirit_skin_url_card).setOnClickListener(v ->
                promptText(R.string.spirit_skin_url, R.string.spirit_skin_url_hint, InputType.TYPE_TEXT_VARIATION_URI, null, this::fetchByUrl));
        view.findViewById(R.id.spirit_skin_library_card).setOnClickListener(v -> openLibrary());
        view.findViewById(R.id.spirit_skin_gallery_card).setOnClickListener(v -> mGalleryLauncher.launch("image/*"));
        view.findViewById(R.id.spirit_skin_apply).setOnClickListener(v -> applySkin());
        view.findViewById(R.id.spirit_editor_card).setOnClickListener(v -> openEditor());
        view.findViewById(R.id.spirit_cape_card).setOnClickListener(v -> openCapes());
        view.findViewById(R.id.spirit_skin_save).setOnClickListener(v -> saveToLibrary());
        mModel.setOnClickListener(v -> toggleModel());
        mOfflineInstall.setOnClickListener(v -> installSkinSupport());

        SwitchCompat autoSkin = view.findViewById(R.id.spirit_autoskin_switch);
        autoSkin.setChecked(LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_AUTO_SKIN, false));
        autoSkin.setOnCheckedChangeListener((b, checked) -> {
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF_AUTO_SKIN, checked).apply();
            refreshCompat();
            if(checked && (mUrl == null || mUrl.isEmpty())) {
                Toast.makeText(requireContext(), R.string.spirit_autoskin_no_url, Toast.LENGTH_LONG).show();
            }
        });
        view.findViewById(R.id.spirit_autoskin_servers).setOnClickListener(v -> openServers());
        mCommand.setOnClickListener(v -> editCommand());

        loadCurrent();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshOfflineCard();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Views are gone, late results must not touch them
        mPreview = null;
        mStatus = null;
        mModel = null;
        mActions = null;
        mCommand = null;
        mOfflineCard = null;
        mOfflineInstall = null;
    }

    // ---------------------------------------------------------------- helpers

    /** Runs on the main thread, and only while the screen is still there */
    private void ui(Runnable action) {
        Tools.runOnUiThread(() -> {
            if(isAdded() && getView() != null) action.run();
        });
    }

    private void toast(String text) {
        ui(() -> Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show());
    }

    private void promptText(int title, int hint, int inputType, @Nullable String initial, TextCallback callback) {
        Context context = requireContext();
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setHint(hint);
        if(initial != null) {
            input.setText(initial);
            input.setSelection(initial.length());
        }
        FrameLayout frame = new FrameLayout(context);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        frame.setPadding(pad, pad / 2, pad, 0);
        frame.addView(input, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(frame)
                .setPositiveButton(R.string.spirit_skin_ok, (d, w) -> callback.onText(input.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private interface TextCallback {
        void onText(String text);
    }

    // ---------------------------------------------------------------- showing a skin

    private void show(@Nullable Bitmap bitmap, boolean slim, @Nullable String url, @Nullable String name, boolean pending) {
        mBitmap = bitmap;
        mSlim = slim;
        mUrl = url;
        mName = name;
        mPending = pending;
        refreshViews();
    }

    private void refreshViews() {
        if(mPreview == null) return;
        mPreview.setSkin(mBitmap, mSlim);
        mModel.setText(mSlim ? R.string.spirit_model_slim : R.string.spirit_model_classic);
        mModel.setVisibility(mBitmap != null ? View.VISIBLE : View.GONE);
        mActions.setVisibility(mBitmap != null && mPending ? View.VISIBLE : View.GONE);
        String shownName = mName != null ? mName : getString(R.string.spirit_skin_unnamed);
        if(mBitmap == null) mStatus.setText(R.string.spirit_skin_status_none);
        else if(mPending) mStatus.setText(getString(R.string.spirit_skin_status_preview, shownName));
        else mStatus.setText(getString(R.string.spirit_skin_status_wearing, shownName));
        mCommand.setText(mUrl != null && !mUrl.isEmpty() ? AutoSkin.commandFor(mUrl) : getString(R.string.spirit_autoskin_cmd));
        refreshCompat();
    }

    /** Plain-words state of Auto Skin so the player knows what will happen on a server */
    private void refreshCompat() {
        View root = getView();
        if(root == null) return;
        TextView view = root.findViewById(R.id.spirit_autoskin_status);
        if(view == null) return;
        boolean on = LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_AUTO_SKIN, false);
        int text;
        if(!on) text = R.string.spirit_compat_off;
        else if(mUrl == null || mUrl.isEmpty()) text = R.string.spirit_compat_nolink;
        else if(mAccount != null && mAccount.authType == git.artdeell.mojo.authenticator.AuthType.MICROSOFT) text = R.string.spirit_compat_microsoft;
        else text = R.string.spirit_compat_ready;
        view.setText(getString(text, AutoSkin.servers().size()));
    }

    private void toggleModel() {
        if(mBitmap == null) return;
        mSlim = !mSlim;
        mPending = true;
        refreshViews();
    }

    /** Shows the skin the current account wears: what Wardrobe applied last, else what the server says */
    private void loadCurrent() {
        MojoApplication.sExecutorService.execute(() -> {
            Account account = Accounts.getCurrent();
            SkinStore.Active active = null;
            SkinNet.Skin remote = null;
            if(account != null) {
                active = SkinStore.loadActive(account);
                if(active == null) {
                    remote = SkinNet.fetchAccountSkin(account);
                    if(remote != null) {
                        try {
                            SkinStore.saveActive(account, remote.bitmap, remote.name, remote.url, remote.slim);
                        } catch (IOException ignored) {
                            // Only a cache of what the server has
                        }
                    }
                }
            }
            final Account fAccount = account;
            final SkinStore.Active fActive = active;
            final SkinNet.Skin fRemote = remote;
            ui(() -> {
                mAccount = fAccount;
                if(fActive != null) show(fActive.bitmap, fActive.slim, fActive.url, fActive.name, false);
                else if(fRemote != null) show(fRemote.bitmap, fRemote.slim, fRemote.url, fRemote.name, false);
                else show(null, false, null, null, false);
                refreshOfflineCard();
            });
        });
    }

    // ---------------------------------------------------------------- sources

    private void fetchByUsername(String name) {
        if(name.isEmpty()) return;
        if(mStatus != null) mStatus.setText(R.string.spirit_skin_loading);
        MojoApplication.sExecutorService.execute(() -> {
            try {
                SkinNet.Skin skin = SkinNet.fetchByUsername(name);
                ui(() -> show(skin.bitmap, skin.slim, skin.url, skin.name, true));
            } catch (IOException | RuntimeException e) {
                failedFetch(e);
            }
        });
    }

    private void fetchByUrl(String url) {
        if(url.isEmpty()) return;
        if(mStatus != null) mStatus.setText(R.string.spirit_skin_loading);
        MojoApplication.sExecutorService.execute(() -> {
            try {
                SkinNet.Skin skin = SkinNet.fetchByUrl(url);
                ui(() -> show(skin.bitmap, skin.slim, skin.url, skin.name, true));
            } catch (IOException | RuntimeException e) {
                failedFetch(e);
            }
        });
    }

    private void failedFetch(Exception e) {
        ui(() -> {
            refreshViews();
            Toast.makeText(requireContext(), getString(R.string.spirit_skin_fetch_failed, messageOf(e)), Toast.LENGTH_LONG).show();
        });
    }

    private static String messageOf(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private void onGalleryPicked(@Nullable Uri uri) {
        if(uri == null || !isAdded()) return;
        final Context context = requireContext().getApplicationContext();
        final ContentResolver resolver = context.getContentResolver();
        MojoApplication.sExecutorService.execute(() -> {
            try(InputStream in = resolver.openInputStream(uri)) {
                if(in == null) throw new IOException("Could not open the file.");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if(out.size() > MAX_IMPORT_BYTES) throw new IOException("The file is too big to be a skin.");
                }
                Bitmap skin = SkinStore.decodeBytes(out.toByteArray());
                String fileName = Tools.getFileName(context, uri);
                String name = fileName == null ? "Imported skin" : fileName.replaceFirst("\\.[A-Za-z0-9]+$", "");
                boolean slim = SkinStore.detectSlim(skin);
                // A file on the phone has no public link, so Auto Skin cannot use it
                ui(() -> show(skin, slim, null, name, true));
            } catch (IOException | RuntimeException e) {
                failedFetch(e);
            }
        });
    }

    // ---------------------------------------------------------------- library

    private void saveToLibrary() {
        final Bitmap bitmap = mBitmap;
        if(bitmap == null) return;
        promptText(R.string.spirit_skin_save_title, R.string.spirit_skin_name_hint, InputType.TYPE_CLASS_TEXT, mName, name -> {
            final String finalName = name.isEmpty() ? getString(R.string.spirit_skin_unnamed) : name;
            final String url = mUrl;
            final boolean slim = mSlim;
            MojoApplication.sExecutorService.execute(() -> {
                try {
                    SkinStore.add(finalName, bitmap, url, slim);
                    toast(getString(R.string.spirit_skin_saved));
                } catch (IOException e) {
                    toast(getString(R.string.spirit_skin_fetch_failed, messageOf(e)));
                }
            });
        });
    }

    private void openLibrary() {
        final Context appContext = requireContext().getApplicationContext();
        MojoApplication.sExecutorService.execute(() -> {
            SkinStore.seedBuiltins(appContext);
            final List<SkinStore.Entry> entries = SkinStore.list();
            ui(() -> {
                String[] names = new String[entries.size()];
                for(int i = 0; i < names.length; i++) {
                    SkinStore.Entry e = entries.get(i);
                    names[i] = (e.fav ? "\u2605 " : "") + e.name + (e.slim ? "  (slim)" : "");
                }
                AlertDialog dialog = new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.spirit_skin_library_title)
                        .setItems(names, (d, which) -> useEntry(entries.get(which)))
                        .setNeutralButton(R.string.spirit_skin_history, (d, w) -> openHistory())
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
                dialog.show();
                dialog.getListView().setOnItemLongClickListener((parent, v, position, id) -> {
                    entryMenu(entries.get(position), dialog);
                    return true;
                });
            });
        });
    }

    private void entryMenu(SkinStore.Entry entry, AlertDialog libraryDialog) {
        String favLabel = getString(entry.fav ? R.string.spirit_skin_unfavorite : R.string.spirit_skin_favorite);
        String[] items = entry.builtin ? new String[]{favLabel} : new String[]{favLabel, getString(R.string.spirit_skin_delete)};
        new AlertDialog.Builder(requireContext())
                .setTitle(entry.name)
                .setItems(items, (d, which) -> {
                    if(which == 0) {
                        MojoApplication.sExecutorService.execute(() -> {
                            try { SkinStore.setFavorite(entry.id, !entry.fav); } catch (IOException e) { toast(messageOf(e)); }
                            ui(() -> { libraryDialog.dismiss(); openLibrary(); });
                        });
                    } else {
                        confirmDelete(entry, libraryDialog);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openHistory() {
        MojoApplication.sExecutorService.execute(() -> {
            final List<SkinStore.HistoryEntry> list = SkinStore.history();
            ui(() -> {
                if(list.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.spirit_skin_history_empty, Toast.LENGTH_LONG).show();
                    return;
                }
                java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT);
                String[] names = new String[list.size()];
                for(int i = 0; i < names.length; i++) {
                    SkinStore.HistoryEntry h = list.get(i);
                    names[i] = (h.name == null ? getString(R.string.spirit_skin_unnamed) : h.name) + "\n" + df.format(new java.util.Date(h.time));
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.spirit_skin_history)
                        .setItems(names, (d, which) -> MojoApplication.sExecutorService.execute(() -> {
                            SkinStore.HistoryEntry h = list.get(which);
                            Bitmap bitmap = SkinStore.loadHistory(h);
                            if(bitmap == null) { toast(getString(R.string.spirit_skin_fetch_failed, "file missing")); return; }
                            ui(() -> show(bitmap, h.slim, h.url, h.name, true));
                        }))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }

    private void useEntry(SkinStore.Entry entry) {
        MojoApplication.sExecutorService.execute(() -> {
            Bitmap bitmap = SkinStore.load(entry);
            if(bitmap == null) {
                toast(getString(R.string.spirit_skin_fetch_failed, "file missing"));
                return;
            }
            ui(() -> show(bitmap, entry.slim, entry.url, entry.name, true));
        });
    }

    private void confirmDelete(SkinStore.Entry entry, AlertDialog libraryDialog) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.spirit_skin_delete_title)
                .setMessage(entry.name)
                .setPositiveButton(R.string.spirit_skin_delete, (d, w) -> MojoApplication.sExecutorService.execute(() -> {
                    try {
                        SkinStore.remove(entry.id);
                        toast(getString(R.string.spirit_skin_deleted));
                    } catch (IOException e) {
                        toast(getString(R.string.spirit_skin_fetch_failed, messageOf(e)));
                    }
                    ui(libraryDialog::dismiss);
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------- applying

    private void applySkin() {
        final Account account = mAccount;
        final Bitmap bitmap = mBitmap;
        if(account == null || bitmap == null) {
            Toast.makeText(requireContext(), R.string.no_saved_accounts, Toast.LENGTH_LONG).show();
            return;
        }
        final boolean slim = mSlim;
        final String name = mName;
        if(mUrl == null && LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_AUTO_SKIN, false)) {
            // A skin from the gallery has no link yet: make one so Auto Skin can use it on servers
            if(mStatus != null) mStatus.setText(R.string.spirit_skin_uploading);
            MojoApplication.sExecutorService.execute(() -> {
                String made = null;
                try {
                    made = SkinNet.uploadPublic(bitmap);
                } catch (IOException e) {
                    toast(getString(R.string.spirit_skin_upload_failed, messageOf(e)));
                }
                final String link = made;
                ui(() -> { if(link != null) mUrl = link; applyWith(account, bitmap, slim, link, name); });
            });
            return;
        }
        applyWith(account, bitmap, slim, mUrl, name);
    }

    private void applyWith(Account account, Bitmap bitmap, boolean slim, @Nullable String url, @Nullable String name) {
        switch(account.authType) {
            case MICROSOFT:
                applyMicrosoft(account, bitmap, slim, url, name, true);
                break;
            case ELY_BY:
                applyElyBy(account, bitmap, slim, url, name);
                break;
            default:
                applyLocal(account, bitmap, slim, url, name);
        }
    }

    private void applyMicrosoft(Account account, Bitmap bitmap, boolean slim, @Nullable String url, @Nullable String name, boolean mayRefresh) {
        if(mStatus != null) mStatus.setText(R.string.spirit_skin_applying);
        MojoApplication.sExecutorService.execute(() -> {
            // Another part of the app may have refreshed the token since this screen opened
            Account fresh = account.reload();
            if(fresh == null) fresh = account;
            try {
                SkinNet.applyMicrosoft(fresh, bitmap, slim);
                finishApplied(fresh, bitmap, slim, url, name);
            } catch (IOException e) {
                if("TOKEN_EXPIRED".equals(e.getMessage()) && mayRefresh) {
                    final Account toRefresh = fresh;
                    ui(() -> refreshThenRetry(toRefresh, bitmap, slim, url, name));
                } else if("TOKEN_EXPIRED".equals(e.getMessage())) {
                    ui(() -> {
                        refreshViews();
                        Toast.makeText(requireContext(), R.string.spirit_skin_token_expired, Toast.LENGTH_LONG).show();
                    });
                } else {
                    ui(() -> {
                        refreshViews();
                        Toast.makeText(requireContext(), getString(R.string.spirit_skin_apply_failed, messageOf(e)), Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    /** The Microsoft game token lives a day; get a new one with the refresh token, then try the upload again */
    private void refreshThenRetry(Account account, Bitmap bitmap, boolean slim, @Nullable String url, @Nullable String name) {
        if(!account.authType.requiresLogin()) {
            refreshViews();
            Toast.makeText(requireContext(), R.string.spirit_skin_token_expired, Toast.LENGTH_LONG).show();
            return;
        }
        account.authType.createAuth().refreshAccount(new LoginListener() {
            @Override
            public void onLoginDone(Account refreshed) {
                if(!isAdded()) return;
                applyMicrosoft(refreshed, bitmap, slim, url, name, false);
            }

            @Override
            public void onLoginError(Throwable error) {
                if(!isAdded() || getView() == null) return;
                refreshViews();
                Toast.makeText(requireContext(), R.string.spirit_skin_token_expired, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onLoginProgress(int step) {}

            @Override
            public void setMaxLoginProgress(int max) {}
        }, account);
    }

    private void applyElyBy(Account account, Bitmap bitmap, boolean slim, @Nullable String url, @Nullable String name) {
        MojoApplication.sExecutorService.execute(() -> {
            File exported;
            try {
                File dir = new File(Tools.DIR_GAME_HOME, "skins");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                String safe = (name == null ? "skin" : name).replaceAll("[^A-Za-z0-9_.-]", "_");
                exported = new File(dir, safe + ".png");
                try(FileOutputStream out = new FileOutputStream(exported)) {
                    out.write(SkinStore.encodePng(bitmap));
                }
                SkinStore.saveActive(account, bitmap, name, url, slim);
            SkinStore.addHistory(bitmap, name, url, slim);
            } catch (IOException e) {
                toast(getString(R.string.spirit_skin_apply_failed, messageOf(e)));
                return;
            }
            account.updateFaceFromSkin(bitmap);
            final String path = exported.getAbsolutePath();
            ui(() -> {
                mPending = false;
                refreshViews();
                ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.spirit_skin_ely_title)
                        .setMessage(getString(R.string.spirit_skin_ely_body, path))
                        .setPositiveButton(R.string.spirit_skin_ely_open, (d, w) -> Tools.openURL(requireActivity(), "https://ely.by/skins"))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }

    private void applyLocal(Account account, Bitmap bitmap, boolean slim, @Nullable String url, @Nullable String name) {
        MojoApplication.sExecutorService.execute(() -> {
            try {
                BrowseTarget target = BrowseTarget.forSelectedInstance();
                if(target != null) {
                    writeLocalSkin(target, account, bitmap);
                    SkinStore.installDefaultSkinPack(new File(target.gameDir.toString()), bitmap);
                }
                finishApplied(account, bitmap, slim, url, name);
            } catch (IOException e) {
                toast(getString(R.string.spirit_skin_apply_failed, messageOf(e)));
            }
        });
    }

    /** CustomSkinLoader reads skins/<name>.png from its LocalSkin folder, it works out slim arms by itself */
    private static void writeLocalSkin(BrowseTarget target, Account account, Bitmap bitmap) throws IOException {
        File dir = new File(target.gameDir, "CustomSkinLoader/LocalSkin/skins");
        if(!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
        try(FileOutputStream out = new FileOutputStream(new File(dir, account.username + ".png"))) {
            out.write(SkinStore.encodePng(bitmap));
        }
    }

    private void finishApplied(Account account, Bitmap bitmap, boolean slim, @Nullable String url, @Nullable String name) {
        try {
            SkinStore.saveActive(account, bitmap, name, url, slim);
        } catch (IOException e) {
            toast(getString(R.string.spirit_skin_apply_failed, messageOf(e)));
            return;
        }
        account.updateFaceFromSkin(bitmap);
        ui(() -> {
            mAccount = account;
            mPending = false;
            refreshViews();
            Toast.makeText(requireContext(), getString(R.string.spirit_skin_applied, account.username), Toast.LENGTH_LONG).show();
            ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        });
    }

    // ---------------------------------------------------------------- offline accounts

    private void refreshOfflineCard() {
        if(mOfflineCard == null) return;
        final Account account = mAccount;
        boolean offline = account != null && account.authType == AuthType.LOCAL;
        mOfflineCard.setVisibility(offline ? View.VISIBLE : View.GONE);
        if(!offline) return;
        MojoApplication.sExecutorService.execute(() -> {
            BrowseTarget target = BrowseTarget.forSelectedInstance();
            final boolean installed = target != null && hasSkinSupport(target);
            ui(() -> {
                mOfflineInstall.setText(installed ? R.string.spirit_offline_installed : R.string.spirit_offline_install);
                mOfflineInstall.setAlpha(installed ? 0.6f : 1f);
            });
        });
    }

    private static boolean hasSkinSupport(BrowseTarget target) {
        File[] mods = new File(target.gameDir, "mods").listFiles();
        if(mods == null) return false;
        for(File mod : mods) {
            String name = mod.getName().toLowerCase(Locale.ROOT);
            if(name.contains("customskinloader") && name.endsWith(".jar")) return true;
        }
        return false;
    }

    private void installSkinSupport() {
        if(!ProgressLayout.ensureNoOngoingTasks(requireContext())) return;
        MojoApplication.sExecutorService.execute(() -> {
            BrowseTarget target = BrowseTarget.forSelectedInstance();
            if(target == null) {
                toast(getString(R.string.spirit_browse_no_instance));
                return;
            }
            if(hasSkinSupport(target)) {
                toast(getString(R.string.spirit_offline_installed));
                return;
            }
            if(target.isVanilla()) {
                ui(() -> new AlertDialog.Builder(requireContext())
                        .setMessage(R.string.spirit_offline_vanilla)
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
                return;
            }
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
            try {
                List<ModrinthClient.Version> versions = ModrinthClient.versions("customskinloader", target.mcVersion, target.loaders);
                if(versions.isEmpty()) {
                    ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                    toast(getString(R.string.spirit_offline_no_version, target.mcVersion == null ? "?" : target.mcVersion));
                    return;
                }
                BrowseInstaller.install(target, BrowseKind.MODS, versions.get(0));
                toast(getString(R.string.spirit_offline_installed_now));
            } catch (IOException | RuntimeException e) {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                toast(getString(R.string.spirit_skin_apply_failed, messageOf(e)));
                return;
            }
            ui(this::refreshOfflineCard);
        });
    }

    // ---------------------------------------------------------------- auto skin servers

    private void openServers() {
        final Set<String> servers = AutoSkin.servers();
        List<String> sorted = new ArrayList<>(servers);
        Collections.sort(sorted);
        List<String> labels = new ArrayList<>();
        for(String k : sorted) labels.add(k.contains("|") ? k.substring(0, k.indexOf('|')) : k);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.spirit_autoskin_servers_title)
                .setNeutralButton(R.string.spirit_autoskin_reset, (d, w) -> {
                    AutoSkin.setServers(new java.util.HashSet<>());
                    Toast.makeText(requireContext(), R.string.spirit_autoskin_reset_done, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.spirit_autoskin_close, null);
        if(sorted.isEmpty()) {
            builder.setMessage(R.string.spirit_autoskin_servers_empty);
        } else {
            builder.setItems(labels.toArray(new String[0]), (d, which) -> {
                servers.remove(sorted.get(which));
                AutoSkin.setServers(servers);
                openServers();
            });
        }
        builder.show();
    }

    private void editCommand() {
        promptText(R.string.spirit_autoskin_cmd_title, R.string.spirit_autoskin_cmd_hint, InputType.TYPE_CLASS_TEXT, AutoSkin.template(), text -> {
            AutoSkin.setTemplate(text.isEmpty() ? AutoSkin.DEFAULT_CMD : text);
            refreshViews();
        });
    }

    // ---------------------------------------------------------------- editor

    private void openEditor() {
        final Bitmap bitmap = mBitmap;
        if(bitmap == null) {
            Toast.makeText(requireContext(), R.string.spirit_editor_need_skin, Toast.LENGTH_LONG).show();
            return;
        }
        final boolean slim = mSlim;
        MojoApplication.sExecutorService.execute(() -> {
            try {
                File in = new File(Tools.DIR_CACHE, "spirit_editor_in.png");
                try(FileOutputStream out = new FileOutputStream(in)) {
                    out.write(SkinStore.encodePng(bitmap));
                }
                android.content.Intent intent = new android.content.Intent(requireContext(), git.artdeell.mojo.wardrobe.SkinEditorActivity.class);
                intent.putExtra(git.artdeell.mojo.wardrobe.SkinEditorActivity.EXTRA_IN, in.getAbsolutePath());
                intent.putExtra(git.artdeell.mojo.wardrobe.SkinEditorActivity.EXTRA_OUT, new File(Tools.DIR_CACHE, "spirit_editor_out.png").getAbsolutePath());
                intent.putExtra(git.artdeell.mojo.wardrobe.SkinEditorActivity.EXTRA_SLIM, slim);
                ui(() -> mEditorLauncher.launch(intent));
            } catch (IOException e) {
                toast(messageOf(e));
            }
        });
    }

    // ---------------------------------------------------------------- capes

    private interface CapeAction {
        void run(Account account) throws IOException;
    }

    private void openCapes() {
        final Account account = mAccount;
        if(account == null || account.authType != git.artdeell.mojo.authenticator.AuthType.MICROSOFT) {
            Toast.makeText(requireContext(), R.string.spirit_cape_only_ms, Toast.LENGTH_LONG).show();
            return;
        }
        MojoApplication.sExecutorService.execute(() -> {
            final java.util.List<SkinNet.Cape> capes = new ArrayList<>();
            boolean ok = capeCall(account, acc -> { capes.clear(); capes.addAll(SkinNet.fetchCapes(acc)); }, null);
            if(!ok) return;
            ui(() -> showCapeDialog(account, capes));
        });
    }

    private void showCapeDialog(Account account, java.util.List<SkinNet.Cape> capes) {
        if(!isAdded()) return;
        if(capes.isEmpty()) {
            Toast.makeText(requireContext(), R.string.spirit_cape_empty, Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[capes.size() + 1];
        names[0] = getString(R.string.spirit_cape_none);
        for(int i = 0; i < capes.size(); i++) names[i + 1] = (capes.get(i).active ? "\u2713 " : "") + capes.get(i).alias;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.spirit_cape_title)
                .setItems(names, (d, which) -> {
                    final String id = which == 0 ? null : capes.get(which - 1).id;
                    MojoApplication.sExecutorService.execute(() -> {
                        boolean ok = capeCall(account, acc -> SkinNet.setCape(acc, id), null);
                        if(ok) toast(getString(R.string.spirit_cape_done));
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Runs a Microsoft call off the main thread; when the token is stale, refreshes it once and retries. Returns success. */
    private boolean capeCall(Account account, CapeAction action, @Nullable Account refreshed) {
        Account use = refreshed != null ? refreshed : account.reload();
        if(use == null) use = account;
        try {
            action.run(use);
            return true;
        } catch (IOException e) {
            if("TOKEN_EXPIRED".equals(e.getMessage())) {
                if(refreshed != null || !account.authType.requiresLogin()) {
                    toast(getString(R.string.spirit_skin_token_expired));
                    return false;
                }
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                final Account[] fresh = new Account[1];
                final Account base = use;
                Tools.runOnUiThread(() -> account.authType.createAuth().refreshAccount(new LoginListener() {
                    @Override public void onLoginDone(Account a) { fresh[0] = a; latch.countDown(); }
                    @Override public void onLoginError(Throwable error) { latch.countDown(); }
                    @Override public void onLoginProgress(int step) {}
                    @Override public void setMaxLoginProgress(int max) {}
                }, base));
                try { latch.await(30, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                if(fresh[0] == null) {
                    toast(getString(R.string.spirit_skin_token_expired));
                    return false;
                }
                return capeCall(account, action, fresh[0]);
            }
            toast(getString(R.string.spirit_cape_failed, messageOf(e)));
            return false;
        }
    }
}
