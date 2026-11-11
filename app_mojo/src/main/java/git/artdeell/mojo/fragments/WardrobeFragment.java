package git.artdeell.mojo.fragments;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import git.artdeell.mojo.prefs.LauncherPreferences;

/** Wardrobe screen (skins + Auto Skin toggle). The toggle is stored now, the feature itself is coming soon. */
public class WardrobeFragment extends Fragment {
    public static final String TAG = "SpiritWardrobeFragment";
    public static final String PREF_AUTO_SKIN = "spiritAutoSkin";

    public WardrobeFragment() {
        super(R.layout.fragment_spirit_wardrobe);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        SwitchCompat autoSkin = view.findViewById(R.id.spirit_autoskin_switch);
        autoSkin.setChecked(LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_AUTO_SKIN, false));
        autoSkin.setOnCheckedChangeListener((b, checked) ->
                LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF_AUTO_SKIN, checked).apply());
    }
}
