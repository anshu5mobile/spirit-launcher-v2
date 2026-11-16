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
        view.findViewById(R.id.spirit_about_donate).setOnClickListener(v -> showDonate());
        view.findViewById(R.id.spirit_about_license).setOnClickListener(v -> Tools.openURL(requireActivity(), LICENSE_URL));
        view.findViewById(R.id.spirit_about_source).setOnClickListener(v -> Tools.openURL(requireActivity(), SOURCE_URL));
    }

    /** UPI QR (PhonePe, Google Pay, Paytm... all scan it). Donations are optional and unlock nothing. */
    private void showDonate() {
        android.content.Context c = requireContext();
        int pad = (int) (16 * c.getResources().getDisplayMetrics().density);
        android.widget.LinearLayout box = new android.widget.LinearLayout(c);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        android.widget.TextView msg = new android.widget.TextView(c);
        msg.setText(R.string.spirit_donate_msg);
        msg.setTextColor(0xFFEAF2E0);
        msg.setTextSize(14);
        box.addView(msg);
        android.widget.ImageView qr = new android.widget.ImageView(c);
        qr.setImageResource(R.drawable.spirit_donate_qr);
        qr.setAdjustViewBounds(true);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 2;
        box.addView(qr, lp);
        android.widget.ScrollView scroll = new android.widget.ScrollView(c);
        scroll.addView(box);
        new androidx.appcompat.app.AlertDialog.Builder(c)
                .setTitle(R.string.spirit_donate_title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
