package git.artdeell.mojo.fragments;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import git.artdeell.mojo.utils.SpiritScreen;

/**
 * Browse screen (mods, packs, shaders, worlds). The layout and tabs are live, the catalog behind it
 * is not wired up yet: the Mods tab shows sample entries and Install says "coming soon".
 */
public class BrowseFragment extends Fragment {
    public static final String TAG = "SpiritBrowseFragment";

    private static final int[] TAB_IDS = {
            R.id.spirit_tab_mods, R.id.spirit_tab_packs, R.id.spirit_tab_shaders, R.id.spirit_tab_worlds
    };

    private LinearLayout mList;
    private View mEmpty;

    public BrowseFragment() {
        super(R.layout.fragment_spirit_browse);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        SpiritScreen.bindBack(view, requireActivity());
        mList = view.findViewById(R.id.spirit_browse_list);
        mEmpty = view.findViewById(R.id.spirit_browse_empty);

        View.OnClickListener soon = v -> showComingSoon();
        view.findViewById(R.id.spirit_browse_search).setOnClickListener(soon);

        for(int id : TAB_IDS) {
            view.findViewById(id).setOnClickListener(v -> selectTab(view, id));
        }
        selectTab(view, R.id.spirit_tab_mods);
    }

    private void selectTab(View root, int selectedId) {
        for(int id : TAB_IDS) {
            TextView tab = root.findViewById(id);
            boolean selected = id == selectedId;
            tab.setBackgroundResource(selected ? R.drawable.spirit_badge_fill : R.drawable.spirit_card_bg);
            tab.setTextColor(ContextCompat.getColor(requireContext(),
                    selected ? R.color.spirit_on_lime : R.color.spirit_text));
            tab.setTypeface(null, Typeface.BOLD);
        }

        mList.removeAllViews();
        boolean hasEntries = selectedId == R.id.spirit_tab_mods;
        mEmpty.setVisibility(hasEntries ? View.GONE : View.VISIBLE);
        if(hasEntries) {
            addEntry("Sodium", R.string.spirit_browse_sodium_sub);
            addEntry("Lithium", R.string.spirit_browse_lithium_sub);
            addEntry("FerriteCore", R.string.spirit_browse_ferrite_sub);
        }
    }

    private void addEntry(String name, @StringRes int subtitle) {
        View entry = LayoutInflater.from(requireContext()).inflate(R.layout.item_spirit_browse_entry, mList, false);
        ((TextView) entry.findViewById(R.id.spirit_entry_name)).setText(name);
        ((TextView) entry.findViewById(R.id.spirit_entry_sub)).setText(subtitle);
        entry.findViewById(R.id.spirit_entry_install).setOnClickListener(v -> showComingSoon());
        mList.addView(entry);
    }

    private void showComingSoon() {
        Toast.makeText(requireContext(), R.string.spirit_coming_soon, Toast.LENGTH_SHORT).show();
    }
}
