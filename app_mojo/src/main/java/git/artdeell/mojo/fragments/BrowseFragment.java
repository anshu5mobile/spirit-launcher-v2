package git.artdeell.mojo.fragments;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.browse.BrowseInstaller;
import git.artdeell.mojo.browse.BrowseKind;
import git.artdeell.mojo.browse.BrowseTarget;
import git.artdeell.mojo.browse.CurseWorlds;
import git.artdeell.mojo.browse.ModrinthClient;
import git.artdeell.mojo.modding.modpacks.imagecache.ImageReceiver;
import git.artdeell.mojo.modding.modpacks.imagecache.ModIconCache;
import git.artdeell.mojo.utils.SpiritScreen;
import git.artdeell.mojo.utils.lifecycle.ContextExecutor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Browse screen: mods, resource packs, shaders (Modrinth) and worlds (CurseForge, needs a key).
 * Results are filtered by the selected instance's Minecraft version and loader, and installs go into the
 * matching folder of that instance. All network and disk work runs on the shared executor.
 */
public class BrowseFragment extends Fragment {
    public static final String TAG = "SpiritBrowseFragment";

    private static final int[] TAB_IDS = {
            R.id.spirit_tab_mods, R.id.spirit_tab_packs, R.id.spirit_tab_shaders, R.id.spirit_tab_worlds
    };
    private static final BrowseKind[] TAB_KINDS = {
            BrowseKind.MODS, BrowseKind.PACKS, BrowseKind.SHADERS, BrowseKind.WORLDS
    };
    private static final String[] SORTS = {"relevance", "downloads", "updated"};
    private static final int[] SORT_LABELS = {
            R.string.spirit_sort_relevance, R.string.spirit_sort_downloads, R.string.spirit_sort_updated
    };

    /** Shared on purpose: the cache owns a thread pool that should not be created for every visit */
    private static ModIconCache sIcons;

    private LinearLayout mList;
    private TextView mEmpty, mTargetText, mSortView, mInstalledToggle, mUpdateAll, mMore;
    private ProgressBar mProgress;
    private EditText mInput;
    private View mRoot;

    private BrowseKind mKind = BrowseKind.MODS;
    private int mSortIndex = 0;
    private boolean mInstalledMode = false;
    private int mOffset = 0;
    /** Bumped on every new query so a slow, outdated answer is never shown */
    private int mGeneration = 0;
    private boolean mTargetLoaded = false;
    @Nullable private BrowseTarget mTarget;
    // Filters chosen by the player. null = follow the selected instance, "any" = no filter.
    @Nullable private String mLoaderOverride;
    @Nullable private String mVersionOverride;
    @Nullable private String mCategory;
    @Nullable private CurseWorlds mCurse;

    public BrowseFragment() {
        super(R.layout.fragment_spirit_browse);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mRoot = view;
        SpiritScreen.bindBack(view, requireActivity());
        mList = view.findViewById(R.id.spirit_browse_list);
        mEmpty = view.findViewById(R.id.spirit_browse_empty);
        mTargetText = view.findViewById(R.id.spirit_browse_target);
        mSortView = view.findViewById(R.id.spirit_browse_sort);
        mInstalledToggle = view.findViewById(R.id.spirit_browse_installed_toggle);
        mUpdateAll = view.findViewById(R.id.spirit_browse_update_all);
        mMore = view.findViewById(R.id.spirit_browse_more);
        mProgress = view.findViewById(R.id.spirit_browse_progress);
        mInput = view.findViewById(R.id.spirit_browse_input);

        String key = getString(R.string.curseforge_api_key);
        mCurse = CurseWorlds.isAvailable(key) ? new CurseWorlds(key) : null;
        synchronized (BrowseFragment.class) {
            if(sIcons == null) sIcons = new ModIconCache();
        }

        for(int i = 0; i < TAB_IDS.length; i++) {
            final int index = i;
            view.findViewById(TAB_IDS[i]).setOnClickListener(v -> selectTab(index));
        }
        View controlsTab = view.findViewById(R.id.spirit_tab_controls);
        if(controlsTab != null) controlsTab.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ControlsFragment.class, ControlsFragment.TAG, null));
        mInput.setOnEditorActionListener((v, actionId, event) -> {
            if(actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                        requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if(imm != null) imm.hideSoftInputFromWindow(mInput.getWindowToken(), 0);
                reload();
                return true;
            }
            return false;
        });
        mSortView.setOnClickListener(v -> {
            mSortIndex = (mSortIndex + 1) % SORTS.length;
            reload();
        });
        mInstalledToggle.setOnClickListener(v -> {
            mInstalledMode = !mInstalledMode;
            reload();
        });
        mMore.setOnClickListener(v -> searchPage());
        view.findViewById(R.id.spirit_browse_filters).setOnClickListener(v -> openFilters());
        mUpdateAll.setOnClickListener(v -> updateAllMods());
        mEmpty.setOnClickListener(v -> reload()); // tap to retry after an error

        styleTabs();
        loadTargetThenReload();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mGeneration++; // late answers are dropped
        mRoot = null;
    }

    // ---------------------------------------------------------------- state

    private void loadTargetThenReload() {
        mProgress.setVisibility(View.VISIBLE);
        final int gen = ++mGeneration;
        MojoApplication.sExecutorService.execute(() -> {
            BrowseTarget target = null;
            try {
                target = BrowseTarget.forSelectedInstance();
            } catch (Throwable ignored) {
                // No readable instance: Browse still works, installing then asks for one
            }
            final BrowseTarget loaded = target;
            Tools.runOnUiThread(() -> {
                if(!isAlive() || gen != mGeneration) return;
                mTarget = loaded;
                mTargetLoaded = true;
                reload();
            });
        });
    }

    private boolean isAlive() {
        return isAdded() && mRoot != null;
    }

    private void selectTab(int index) {
        BrowseKind kind = TAB_KINDS[index];
        if(kind == mKind) return;
        mKind = kind;
        styleTabs();
        reload();
    }

    private void styleTabs() {
        for(int i = 0; i < TAB_IDS.length; i++) {
            TextView tab = mRoot.findViewById(TAB_IDS[i]);
            boolean selected = TAB_KINDS[i] == mKind;
            tab.setBackgroundResource(selected ? R.drawable.spirit_badge_fill : R.drawable.spirit_card_bg);
            tab.setTextColor(ContextCompat.getColor(requireContext(),
                    selected ? R.color.spirit_on_lime : R.color.spirit_text));
            tab.setTypeface(null, Typeface.BOLD);
        }
    }

    /** Starts over: clears the list and loads the first page (or the installed list). */
    private void reload() {
        if(!isAlive()) return;
        mGeneration++;
        mOffset = 0;
        mList.removeAllViews();
        mMore.setVisibility(View.GONE);
        mEmpty.setVisibility(View.GONE);
        mProgress.setVisibility(View.GONE);

        mSortView.setText(SORT_LABELS[mSortIndex]);
        mSortView.setVisibility(mInstalledMode ? View.GONE : View.VISIBLE);
        mInput.setEnabled(!mInstalledMode);
        mInstalledToggle.setText(mInstalledMode ? R.string.spirit_browse_back_to_browse : R.string.spirit_browse_installed);
        mUpdateAll.setVisibility(mInstalledMode && mKind == BrowseKind.MODS ? View.VISIBLE : View.GONE);
        updateTargetText();
        if(!mTargetLoaded) {
            mProgress.setVisibility(View.VISIBLE);
            return;
        }

        if(mInstalledMode) showInstalled();
        else searchPage();
    }

    private void updateTargetText() {
        BrowseTarget t = mTarget;
        if(t == null) {
            mTargetText.setText(R.string.spirit_browse_no_instance);
            return;
        }
        if(mInstalledMode) {
            mTargetText.setText(getString(R.string.spirit_browse_installed_in, t.instanceName));
            return;
        }
        if(mKind == BrowseKind.MODS && t.isVanilla()) {
            mTargetText.setText(R.string.spirit_browse_vanilla_warn);
            return;
        }
        List<String> parts = new ArrayList<>();
        if(mKind == BrowseKind.MODS && t.loaderName != null) parts.add(t.loaderName);
        if(t.mcVersion != null) parts.add(t.mcVersion);
        String filter = parts.isEmpty() ? getString(R.string.spirit_browse_any_version) : TextUtils.join(" · ", parts);
        mTargetText.setText(getString(R.string.spirit_browse_target_fmt, t.instanceName, filter));
    }

    // ---------------------------------------------------------------- search

    private void searchPage() {
        if(!isAlive()) return;
        if(mKind == BrowseKind.WORLDS && mCurse == null) {
            showEmpty(getString(R.string.spirit_browse_worlds_key));
            return;
        }
        final int gen = ++mGeneration;
        final BrowseKind kind = mKind;
        final String query = mInput.getText().toString().trim();
        final String sort = SORTS[mSortIndex];
        final int offset = mOffset;
        final BrowseTarget target = mTarget;
        final CurseWorlds curse = mCurse;
        final String category = mCategory;
        mProgress.setVisibility(View.VISIBLE);
        mMore.setVisibility(View.GONE);
        mEmpty.setVisibility(View.GONE);

        MojoApplication.sExecutorService.execute(() -> {
            List<ModrinthClient.Project> found = null;
            try {
                String mc = effectiveVersion(target);
                if(kind == BrowseKind.WORLDS) {
                    found = curse.search(query, mc, sort, offset);
                } else {
                    List<String> loaders = effectiveLoaders(kind, target);
                    found = ModrinthClient.search(kind.modrinthType, query, mc, loaders, category, sort, offset);
                }
            } catch (IOException | RuntimeException e) {
                found = null;
            }
            final List<ModrinthClient.Project> result = found;
            Tools.runOnUiThread(() -> {
                if(!isAlive() || gen != mGeneration) return;
                mProgress.setVisibility(View.GONE);
                if(result == null) {
                    if(mList.getChildCount() == 0) showEmpty(getString(R.string.spirit_browse_error));
                    else git.artdeell.mojo.utils.SpiritToast.makeText(requireContext(), R.string.spirit_browse_error, Toast.LENGTH_SHORT).show();
                    return;
                }
                for(ModrinthClient.Project p : result) addProjectEntry(p, kind);
                mOffset += result.size();
                if(mList.getChildCount() == 0) showEmpty(getString(R.string.spirit_browse_no_results));
                mMore.setVisibility(result.size() >= ModrinthClient.PAGE_SIZE ? View.VISIBLE : View.GONE);
            });
        });
    }

    // ---------------------------------------------------------------- filters

    @Nullable
    private String effectiveVersion(@Nullable BrowseTarget target) {
        if(mVersionOverride != null) return "any".equals(mVersionOverride) ? null : mVersionOverride;
        return target != null ? target.mcVersion : null;
    }

    @Nullable
    private List<String> effectiveLoaders(BrowseKind kind, @Nullable BrowseTarget target) {
        if(kind != BrowseKind.MODS) return null;
        if(mLoaderOverride != null) {
            if("any".equals(mLoaderOverride)) return null;
            List<String> one = new ArrayList<>();
            one.add(mLoaderOverride);
            return one;
        }
        return target != null && !target.isVanilla() ? target.loaders : null;
    }

    private static final String[] MOD_CATEGORIES = {"adventure", "decoration", "economy", "equipment", "food",
            "game-mechanics", "library", "magic", "management", "mobs", "optimization", "social", "storage",
            "technology", "transportation", "utility", "worldgen"};
    private static final String[] PACK_CATEGORIES = {"combat", "decoration", "entities", "environment", "gui",
            "items", "realistic", "simplistic", "themed", "tweaks", "utility", "vanilla-like", "16x", "32x", "64x", "128x"};
    private static final String[] SHADER_CATEGORIES = {"cartoon", "fantasy", "realistic", "semi-realistic",
            "vanilla-like", "atmosphere", "bloom", "colored-lighting", "foliage", "pbr", "reflections", "shadows"};
    private static final String[] LOADERS = {"fabric", "forge", "neoforge", "quilt"};

    private String[] categoriesFor(BrowseKind kind) {
        if(kind == BrowseKind.MODS) return MOD_CATEGORIES;
        if(kind == BrowseKind.PACKS) return PACK_CATEGORIES;
        if(kind == BrowseKind.SHADERS) return SHADER_CATEGORIES;
        return new String[0];
    }

    private String describeFilter(@Nullable String value) {
        return value == null ? getString(R.string.spirit_filter_auto) : "any".equals(value) ? getString(R.string.spirit_filter_any) : value;
    }

    private void openFilters() {
        if(!isAlive()) return;
        final boolean mods = mKind == BrowseKind.MODS;
        final boolean worlds = mKind == BrowseKind.WORLDS;
        List<String> rows = new ArrayList<>();
        if(mods) rows.add(getString(R.string.spirit_filter_loader, describeFilter(mLoaderOverride)));
        rows.add(getString(R.string.spirit_filter_version, describeFilter(mVersionOverride)));
        if(!worlds) rows.add(getString(R.string.spirit_filter_category, mCategory == null ? getString(R.string.spirit_filter_any) : mCategory));
        rows.add(getString(R.string.spirit_filter_reset));
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.spirit_browse_filters)
                .setItems(rows.toArray(new String[0]), (d, which) -> {
                    // Map the tapped row back to what it is, rows can be hidden
                    int i = 0;
                    String what = null;
                    if(mods) { if(which == i++) what = "loader"; }
                    if(what == null) { if(which == i++) what = "version"; }
                    if(what == null && !worlds) { if(which == i++) what = "category"; }
                    if(what == null) what = "reset";
                    switch(what) {
                        case "loader": pickLoader(); break;
                        case "version": pickVersion(); break;
                        case "category": pickCategory(); break;
                        default: mLoaderOverride = null; mVersionOverride = null; mCategory = null; reload();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickLoader() {
        final String[] values = new String[LOADERS.length + 2];
        final String[] labels = new String[values.length];
        values[0] = null; labels[0] = getString(R.string.spirit_filter_auto);
        values[1] = "any"; labels[1] = getString(R.string.spirit_filter_any);
        for(int i = 0; i < LOADERS.length; i++) { values[i + 2] = LOADERS[i]; labels[i + 2] = LOADERS[i]; }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.spirit_filter_loader_title)
                .setItems(labels, (d, which) -> { mLoaderOverride = values[which]; reload(); })
                .show();
    }

    private void pickVersion() {
        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("1.21.1");
        input.setSingleLine();
        if(mVersionOverride != null && !"any".equals(mVersionOverride)) input.setText(mVersionOverride);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.spirit_filter_version_title)
                .setMessage(R.string.spirit_filter_version_hint)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String t = input.getText().toString().trim();
                    mVersionOverride = t.isEmpty() ? null : t;
                    reload();
                })
                .setNeutralButton(R.string.spirit_filter_any, (d, w) -> { mVersionOverride = "any"; reload(); })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickCategory() {
        final String[] cats = categoriesFor(mKind);
        final String[] labels = new String[cats.length + 1];
        labels[0] = getString(R.string.spirit_filter_any);
        System.arraycopy(cats, 0, labels, 1, cats.length);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.spirit_filter_category_title)
                .setItems(labels, (d, which) -> { mCategory = which == 0 ? null : cats[which - 1]; reload(); })
                .show();
    }

    private void showEmpty(String text) {
        mEmpty.setText(text);
        mEmpty.setVisibility(View.VISIBLE);
    }

    private void addProjectEntry(ModrinthClient.Project project, BrowseKind kind) {
        View entry = LayoutInflater.from(requireContext()).inflate(R.layout.item_spirit_browse_entry, mList, false);
        ((TextView) entry.findViewById(R.id.spirit_entry_name)).setText(project.title);
        String sub = project.description != null ? project.description : "";
        String meta = project.author != null ? getString(R.string.spirit_browse_by, project.author) : null;
        if(project.downloads > 0) {
            String dl = getString(R.string.spirit_browse_downloads, formatCount(project.downloads));
            meta = meta != null ? meta + " · " + dl : dl;
        }
        if(meta != null) sub = sub.isEmpty() ? meta : meta + "\n" + sub;
        ((TextView) entry.findViewById(R.id.spirit_entry_sub)).setText(sub);

        ImageView icon = entry.findViewById(R.id.spirit_entry_icon);
        if(project.iconUrl != null && !project.iconUrl.isEmpty() && sIcons != null) {
            final ImageReceiver receiver = bitmap -> {
                if(!isAlive()) return;
                icon.setImageDrawable(RoundedBitmapDrawableFactory.create(getResources(), bitmap));
            };
            icon.setTag(receiver); // keep a strong reference while the row lives
            sIcons.getImage(receiver, (kind == BrowseKind.WORLDS ? "cf_" : "mr_") + project.id, project.iconUrl);
        }

        View.OnClickListener open = v -> pickVersion(project, kind);
        entry.setOnClickListener(open);
        entry.findViewById(R.id.spirit_entry_install).setOnClickListener(open);
        mList.addView(entry);
    }

    private static String formatCount(int n) {
        if(n >= 1_000_000) return String.format(java.util.Locale.US, "%.1fM", n / 1_000_000f);
        if(n >= 1_000) return String.format(java.util.Locale.US, "%.1fK", n / 1_000f);
        return String.valueOf(n);
    }

    // ---------------------------------------------------------------- install

    private void pickVersion(ModrinthClient.Project project, BrowseKind kind) {
        BrowseTarget target = mTarget;
        if(target == null) {
            git.artdeell.mojo.utils.SpiritToast.makeText(requireContext(), R.string.spirit_browse_no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        if(kind == BrowseKind.MODS && target.isVanilla()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.spirit_browse_vanilla_title)
                    .setMessage(R.string.spirit_browse_vanilla_body)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        if(!ProgressLayout.ensureNoOngoingTasks(requireContext())) return;

        git.artdeell.mojo.utils.SpiritToast.makeText(requireContext(), R.string.spirit_browse_loading_versions, Toast.LENGTH_SHORT).show();
        final CurseWorlds curse = mCurse;
        MojoApplication.sExecutorService.execute(() -> {
            List<ModrinthClient.Version> versions;
            try {
                if(kind == BrowseKind.WORLDS) versions = curse.versions(project.id, target.mcVersion);
                else versions = ModrinthClient.versions(project.id, target.mcVersion,
                        kind == BrowseKind.MODS ? target.loaders : null);
            } catch (IOException | RuntimeException e) {
                versions = null;
            }
            final List<ModrinthClient.Version> result = versions;
            Tools.runOnUiThread(() -> {
                if(!isAlive()) return;
                if(result == null) {
                    git.artdeell.mojo.utils.SpiritToast.makeText(requireContext(), R.string.spirit_browse_error, Toast.LENGTH_LONG).show();
                } else if(result.isEmpty()) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(project.title)
                            .setMessage(R.string.spirit_browse_no_compatible)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                } else {
                    showVersionDialog(project, kind, target, result);
                }
            });
        });
    }

    private void showVersionDialog(ModrinthClient.Project project, BrowseKind kind, BrowseTarget target,
                                   List<ModrinthClient.Version> versions) {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad / 2, pad, 0);

        TextView description = new TextView(requireContext());
        String text = project.description != null ? project.description : "";
        description.setText(text + "\n\n" + getString(kind == BrowseKind.WORLDS
                ? R.string.spirit_browse_world_note : R.string.spirit_browse_deps_note));
        description.setTextSize(13);
        content.addView(description);

        String[] labels = new String[versions.size()];
        for(int i = 0; i < labels.length; i++) labels[i] = versions.get(i).label();
        ListView listView = new ListView(requireContext());
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_single_choice, labels));
        listView.setItemChecked(0, true);
        content.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (220 * density)));

        new AlertDialog.Builder(requireContext())
                .setTitle(project.title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.spirit_install, (d, w) -> {
                    int selected = listView.getCheckedItemPosition();
                    if(selected < 0) selected = 0;
                    startInstall(kind, target, versions.get(selected));
                })
                .show();
    }

    private void startInstall(BrowseKind kind, BrowseTarget target, ModrinthClient.Version version) {
        if(!ProgressLayout.ensureNoOngoingTasks(requireContext())) return;
        // Claim the task slot right away so a second install cannot start before the download reports progress
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        final android.content.Context app = requireContext().getApplicationContext();
        MojoApplication.sExecutorService.execute(() -> {
            String summary;
            try {
                if(kind == BrowseKind.WORLDS) {
                    ModrinthClient.FileInfo file = version.primaryFile();
                    if(file == null) throw new IOException("No file to download");
                    String world = CurseWorlds.installWorld(target, file);
                    summary = app.getString(R.string.spirit_browse_world_added, world);
                } else {
                    BrowseInstaller.Result r = BrowseInstaller.install(target, kind, version);
                    summary = summarize(app, r);
                }
            } catch (IOException | RuntimeException e) {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                Tools.showErrorRemote(app, R.string.spirit_browse_install_failed, e);
                return;
            }
            final String message = summary;
            ContextExecutor.executeActivity(activity -> new AlertDialog.Builder(activity)
                    .setTitle(R.string.spirit_browse_done_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
        });
    }

    private static String summarize(android.content.Context c, BrowseInstaller.Result r) {
        StringBuilder sb = new StringBuilder();
        if(!r.added.isEmpty()) sb.append(c.getString(R.string.spirit_browse_added, TextUtils.join(", ", r.added)));
        if(!r.alreadyThere.isEmpty()) {
            if(sb.length() > 0) sb.append("\n\n");
            sb.append(c.getString(R.string.spirit_browse_already, TextUtils.join(", ", r.alreadyThere)));
        }
        if(!r.skippedDependencies.isEmpty()) {
            if(sb.length() > 0) sb.append("\n\n");
            sb.append(c.getString(R.string.spirit_browse_skipped_deps, TextUtils.join(", ", r.skippedDependencies)));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- installed

    private void showInstalled() {
        final BrowseTarget target = mTarget;
        if(target == null) {
            showEmpty(getString(R.string.spirit_browse_no_instance));
            return;
        }
        final int gen = ++mGeneration;
        final BrowseKind kind = mKind;
        mProgress.setVisibility(View.VISIBLE);
        MojoApplication.sExecutorService.execute(() -> {
            List<BrowseInstaller.Installed> items = BrowseInstaller.listInstalled(target, kind);
            Tools.runOnUiThread(() -> {
                if(!isAlive() || gen != mGeneration) return;
                mProgress.setVisibility(View.GONE);
                if(items.isEmpty()) {
                    showEmpty(getString(R.string.spirit_browse_installed_empty));
                    return;
                }
                for(BrowseInstaller.Installed item : items) addInstalledEntry(item, kind);
            });
        });
    }

    private void addInstalledEntry(BrowseInstaller.Installed item, BrowseKind kind) {
        View entry = LayoutInflater.from(requireContext()).inflate(R.layout.item_spirit_browse_entry, mList, false);
        ((TextView) entry.findViewById(R.id.spirit_entry_name)).setText(item.displayName);
        TextView sub = entry.findViewById(R.id.spirit_entry_sub);
        TextView action = entry.findViewById(R.id.spirit_entry_install);
        TextView delete = entry.findViewById(R.id.spirit_entry_delete);
        if(kind == BrowseKind.MODS) {
            delete.setVisibility(View.VISIBLE);
            delete.setOnClickListener(v -> confirmRemove(item));
            sub.setText(item.enabled ? R.string.spirit_browse_enabled : R.string.spirit_browse_disabled);
            action.setText(item.enabled ? R.string.spirit_browse_disable : R.string.spirit_browse_enable);
            action.setOnClickListener(v -> {
                if(!BrowseInstaller.setEnabled(item, !item.enabled)) {
                    git.artdeell.mojo.utils.SpiritToast.makeText(requireContext(), R.string.spirit_browse_error, Toast.LENGTH_SHORT).show();
                }
                reload();
            });
        } else {
            sub.setText(kind.folder);
            action.setText(R.string.spirit_browse_remove);
            action.setOnClickListener(v -> confirmRemove(item));
        }
        entry.setOnClickListener(v -> confirmRemove(item));
        mList.addView(entry);
    }

    private void confirmRemove(BrowseInstaller.Installed item) {
        new AlertDialog.Builder(requireContext())
                .setTitle(item.displayName)
                .setMessage(R.string.spirit_browse_confirm_remove)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.spirit_browse_remove, (d, w) ->
                        MojoApplication.sExecutorService.execute(() -> {
                            try {
                                BrowseInstaller.remove(item);
                            } catch (IOException e) {
                                Tools.showErrorRemote(e);
                            }
                            Tools.runOnUiThread(this::reload);
                        }))
                .show();
    }

    private void updateAllMods() {
        final BrowseTarget target = mTarget;
        if(target == null) return;
        if(!ProgressLayout.ensureNoOngoingTasks(requireContext())) return;
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        final android.content.Context app = requireContext().getApplicationContext();
        MojoApplication.sExecutorService.execute(() -> {
            List<String> updated;
            try {
                updated = BrowseInstaller.updateAllMods(target);
            } catch (IOException | RuntimeException e) {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                Tools.showErrorRemote(app, R.string.spirit_browse_install_failed, e);
                return;
            }
            final String message = updated.isEmpty()
                    ? app.getString(R.string.spirit_browse_all_up_to_date)
                    : app.getString(R.string.spirit_browse_updated, TextUtils.join(", ", updated));
            ContextExecutor.executeActivity(activity -> new AlertDialog.Builder(activity)
                    .setTitle(R.string.spirit_browse_update_all)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
            Tools.runOnUiThread(() -> { if(isAlive() && mInstalledMode) reload(); });
        });
    }
}
