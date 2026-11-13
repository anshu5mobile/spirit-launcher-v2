package git.artdeell.mojo.fragments;

import static git.artdeell.mojo.Tools.openPath;
import static git.artdeell.mojo.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;
import com.kdt.mcgui.mcVersionSpinner;

import git.artdeell.mojo.CustomControlsActivity;
import git.artdeell.mojo.R;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.contracts.OpenDocumentWithExtension;
import git.artdeell.mojo.extra.ExtraConstants;
import git.artdeell.mojo.extra.ExtraCore;
import git.artdeell.mojo.instances.DisplayInstance;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.utils.FileUtils;
import git.artdeell.mojo.utils.SpiritVersionInfo;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private TextView mHeroSubtitle;
    private ViewGroup mHeroChips;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View mNewsButton = view.findViewById(R.id.news_button);
        View mDiscordButton = view.findViewById(R.id.social_media_button);
        View mCustomControlButton = view.findViewById(R.id.custom_control_button);
        View mInstallJarButton = view.findViewById(R.id.install_jar_button);
        View mShareLogsButton = view.findViewById(R.id.share_logs_button);
        View mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        View mCreateInstanceButton = view.findViewById(R.id.create_instance_button);
        View mWardrobeButton = view.findViewById(R.id.spirit_wardrobe_button);
        View mBrowseButton = view.findViewById(R.id.spirit_browse_button);

        View mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        View mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        mHeroSubtitle = view.findViewById(R.id.spirit_hero_subtitle);
        mHeroChips = view.findViewById(R.id.spirit_hero_chips);
        if(mHeroSubtitle != null && mHeroChips != null) {
            // The spinner resets its text size on creation, so the big hero title is set here
            mVersionSpinner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            mVersionSpinner.setSelectionListener(this::updateHero);
        }

        if(mNewsButton != null) {
            mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
            mNewsButton.setOnLongClickListener((v)->{
                Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
                return true;
            });
        }
        if(mDiscordButton != null) mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        if(mCustomControlButton != null) mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        if(mInstallJarButton != null) mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation());
        if(mEditProfileButton != null) mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));
        if(mPlayButton != null) mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        if(mShareLogsButton != null) mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));
        if(mOpenDirectoryButton != null) mOpenDirectoryButton.setOnClickListener((v)-> openGameDirectory(v.getContext()));
        if(mCreateInstanceButton != null) mCreateInstanceButton.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null));

        if(mWardrobeButton != null) mWardrobeButton.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), WardrobeFragment.class, WardrobeFragment.TAG, null));
        if(mBrowseButton != null) mBrowseButton.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), BrowseFragment.class, BrowseFragment.TAG, null));

        // Tap the Spirit logo in the header to open About and Credits
        View logo = requireActivity().findViewById(R.id.spirit_logo);
        if(logo != null) logo.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), AboutFragment.class, AboutFragment.TAG, null));
    }

    /** Fills the hero card: instance name as title, version id as subtitle, plus version/loader/RAM chips. */
    private void updateHero(DisplayInstance instance) {
        if(mHeroSubtitle == null || mHeroChips == null || !isAdded()) return;
        mHeroChips.removeAllViews();
        if(instance == null) {
            mHeroSubtitle.setVisibility(View.GONE);
            return;
        }

        String name = Tools.validOrNullString(instance.name);
        String versionId = Tools.validOrNullString(instance.versionId);
        // The spinner shows "name - version"; the hero has the version on its own line instead
        if(name != null) mVersionSpinner.setText(name);

        String subtitle = versionId;
        if(Instance.VERSION_LATEST_RELEASE.equalsIgnoreCase(versionId)) subtitle = getString(R.string.profiles_latest_release);
        else if(Instance.VERSION_LATEST_SNAPSHOT.equalsIgnoreCase(versionId)) subtitle = getString(R.string.profiles_latest_snapshot);
        mHeroSubtitle.setText(subtitle);
        mHeroSubtitle.setVisibility(subtitle != null ? View.VISIBLE : View.GONE);

        SpiritVersionInfo info = SpiritVersionInfo.parse(versionId);
        boolean isAlias = subtitle != null && !subtitle.equals(versionId);
        if(info.minecraftVersion != null && !isAlias && !info.minecraftVersion.equals(subtitle)) addChip(info.minecraftVersion);
        addChip(info.loader != null ? info.loader : getString(R.string.spirit_chip_vanilla));
        addChip(getString(R.string.spirit_chip_ram, SpiritVersionInfo.formatRam(LauncherPreferences.PREF_RAM_ALLOCATION)));
    }

    private void addChip(String text) {
        TextView chip = (TextView) LayoutInflater.from(requireContext())
                .inflate(R.layout.item_spirit_chip, mHeroChips, false);
        chip.setText(text);
        mHeroChips.addView(chip);
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }

    private void runInstallerWithConfirmation() {
        if(!ProgressLayout.ensureNoOngoingTasks(requireContext())) return;
        mModInstallerLauncher.launch(null);
    }
}
