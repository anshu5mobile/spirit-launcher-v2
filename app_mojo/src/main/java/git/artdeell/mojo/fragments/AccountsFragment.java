package git.artdeell.mojo.fragments;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.extra.ExtraConstants;
import git.artdeell.mojo.extra.ExtraCore;
import git.artdeell.mojo.utils.SpiritAccountUi;
import git.artdeell.mojo.utils.SpiritScreen;

import java.io.IOException;

/** Accounts screen: every saved account, the selected one highlighted, and the "Add new account" button. */
public class AccountsFragment extends Fragment {
    public static final String TAG = "SpiritAccountsFragment";

    private LinearLayout mList;
    private View mEmpty;
    private View mHint;

    public AccountsFragment() {
        super(R.layout.fragment_spirit_accounts);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        SpiritScreen.bindBack(view, requireActivity());
        mList = view.findViewById(R.id.spirit_accounts_list);
        mEmpty = view.findViewById(R.id.spirit_accounts_empty);
        mHint = view.findViewById(R.id.spirit_accounts_hint);
        // Opens the add account bottom sheet (handled by LauncherActivity)
        view.findViewById(R.id.spirit_add_account_button).setOnClickListener(v ->
                ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true));
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAccounts();
    }

    private void loadAccounts() {
        MojoApplication.sExecutorService.execute(() -> {
            try {
                Accounts accounts = Accounts.load();
                Tools.runOnUiThread(() -> showAccounts(accounts));
            } catch (IOException e) {
                Tools.runOnUiThread(() -> {
                    if(isAdded()) Tools.showError(requireContext(), e);
                });
            }
        });
    }

    private void showAccounts(Accounts accounts) {
        // The fragment may be gone by the time the disk read finishes
        if(!isAdded() || getView() == null) return;
        mList.removeAllViews();
        boolean empty = accounts.accounts.isEmpty();
        mEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        mHint.setVisibility(empty ? View.GONE : View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for(int i = 0; i < accounts.accounts.size(); i++) {
            Account account = accounts.accounts.get(i);
            boolean selected = i == accounts.selectionIndex;
            View row = inflater.inflate(R.layout.item_spirit_account, mList, false);
            bindRow(row, account, selected);
            mList.addView(row);
        }
    }

    private void bindRow(View row, Account account, boolean selected) {
        ImageView avatar = row.findViewById(R.id.spirit_account_avatar);
        TextView name = row.findViewById(R.id.spirit_account_name);
        TextView sub = row.findViewById(R.id.spirit_account_sub);
        TextView tag = row.findViewById(R.id.spirit_account_tag);

        Bitmap face = account.getSkinFace();
        if(face != null) {
            BitmapDrawable head = new BitmapDrawable(getResources(), face);
            head.setFilterBitmap(false);
            avatar.clearColorFilter();
            avatar.setImageDrawable(head);
        } else {
            avatar.setImageResource(R.drawable.ic_spirit_nav_accounts);
            avatar.setColorFilter(getResources().getColor(R.color.spirit_lime));
        }

        name.setText(account.username);
        sub.setText(selected ? R.string.spirit_account_selected : SpiritAccountUi.subText(account.authType));
        SpiritAccountUi.bindBadge(tag, account.authType);
        row.setBackgroundResource(selected ? R.drawable.spirit_card_selected_bg : R.drawable.spirit_card_bg);

        row.setOnClickListener(v -> {
            if(selected) return;
            Accounts.setCurrent(account);
            // Refreshes the header pill too (and renews the token when it has expired)
            ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
            loadAccounts();
        });
        row.setOnLongClickListener(v -> {
            confirmDelete(account);
            return true;
        });
    }

    private void confirmDelete(Account account) {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.warning_remove_account)
                .setPositiveButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.global_delete, (dialog, which) -> {
                    Accounts.delete(account);
                    ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
                    loadAccounts();
                })
                .show();
    }
}
