package git.artdeell.mojo.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.BuildConfig;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.utils.SpiritScreen;

public class AboutFragment extends Fragment {
    public static final String TAG = "SpiritAboutFragment";
    private static final String LICENSE_URL = "https://www.gnu.org/licenses/lgpl-3.0.html";
    private static final String SOURCE_URL = "https://github.com/anshu5mobile/spirit-launcher-v2";

    public AboutFragment() {
        super(R.layout.fragment_spirit_about);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        SpiritScreen.bindBack(view, requireActivity());
        ((TextView) view.findViewById(R.id.spirit_about_version)).setText("Version " + BuildConfig.VERSION_NAME);
        view.findViewById(R.id.spirit_about_discord).setOnClickListener(v ->
                Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        view.findViewById(R.id.spirit_about_license).setOnClickListener(v -> Tools.openURL(requireActivity(), LICENSE_URL));
        view.findViewById(R.id.spirit_about_source).setOnClickListener(v -> Tools.openURL(requireActivity(), SOURCE_URL));
    }
}
