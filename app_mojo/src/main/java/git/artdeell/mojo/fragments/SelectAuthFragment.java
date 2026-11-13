package git.artdeell.mojo.fragments;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.extra.ExtraConstants;
import git.artdeell.mojo.extra.ExtraCore;

/** "Add new account" bottom sheet: pick Microsoft, Ely.by or Offline. */
public class SelectAuthFragment extends AppCompatDialogFragment {
    public static final String TAG = "AUTH_SELECT_FRAGMENT";

    public SelectAuthFragment() {
        super();
    }

    @Override
    public int getTheme() {
        return R.style.SpiritSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_select_auth_method, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Stick the sheet to the bottom edge, full width
        Dialog dialog = getDialog();
        if(dialog == null) return;
        Window window = dialog.getWindow();
        if(window == null) return;
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.BOTTOM);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.button_microsoft_authentication).setOnClickListener(v ->
                launchAuthFragment(MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG));
        view.findViewById(R.id.button_local_authentication).setOnClickListener(v ->
                launchAuthFragment(LocalLoginFragment.class, LocalLoginFragment.TAG));
        view.findViewById(R.id.button_elyby_authentication).setOnClickListener(v ->
                launchAuthFragment(ElyByLoginFragment.class, ElyByLoginFragment.TAG));
        view.findViewById(R.id.spirit_auth_cancel).setOnClickListener(v -> dismissAllowingStateLoss());
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        // The header spinner parks itself on its "add account" row when this sheet opens, put it back
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }

    private void launchAuthFragment(Class<? extends Fragment> fragmentClass, String fragmentTag) {
        if(!ProgressLayout.ensureNoOngoingTasks(requireContext(), ProgressLayout.AUTHENTICATE)) return;
        FragmentActivity activity = requireActivity();
        dismissAllowingStateLoss();
        Tools.swapFragment(activity, fragmentClass, fragmentTag, null);
    }
}
