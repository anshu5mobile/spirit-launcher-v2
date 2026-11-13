package git.artdeell.mojo.utils;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import git.artdeell.mojo.R;

/** Small helpers shared by the Spirit screens that draw their own "< Title" header. */
public final class SpiritScreen {
    private SpiritScreen() {}

    /** Wires the header back chevron (R.id.spirit_back), if the layout has one, to the system back action. */
    public static void bindBack(@NonNull View root, @NonNull FragmentActivity activity) {
        View back = root.findViewById(R.id.spirit_back);
        if(back != null) back.setOnClickListener(v -> activity.onBackPressed());
    }
}
