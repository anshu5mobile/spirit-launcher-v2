package git.artdeell.mojo.wardrobe;

import android.app.Activity;
import android.graphics.Bitmap;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.AuthType;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/** Change skin from the in-game Quick settings: pick a skin from the library, the game reloads it with F3+T. */
public final class InGameSkin {
    private InGameSkin() {}

    public static void show(final Activity activity, final File gameDir, final Account launched) {
        // The game runs as the account it was started with: switching account in the launcher later changes nothing in it
        final Account account = launched != null ? launched : Accounts.getCurrent();
        if(account == null || account.authType != AuthType.LOCAL) {
            // Online accounts keep their skin on the server: that is changed from the launcher's Wardrobe
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.spirit_ingame_skin)
                    .setMessage(activity.getString(R.string.spirit_ingame_skin_online, account == null ? "?" : account.username))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        MojoApplication.sExecutorService.execute(() -> {
            SkinStore.seedBuiltins(activity.getApplicationContext());
            final List<SkinStore.Entry> entries = SkinStore.list();
            activity.runOnUiThread(() -> {
                if(activity.isFinishing()) return;
                String[] names = new String[entries.size()];
                for(int i = 0; i < names.length; i++) {
                    SkinStore.Entry e = entries.get(i);
                    names[i] = (e.fav ? "★ " : "") + e.name + (e.slim ? "  (slim)" : "");
                }
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.spirit_ingame_skin)
                        .setItems(names, (d, which) -> apply(activity, gameDir, account, entries.get(which)))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }

    private static void apply(final Activity activity, final File gameDir, final Account account, final SkinStore.Entry entry) {
        MojoApplication.sExecutorService.execute(() -> {
            String message;
            try {
                Bitmap bitmap = SkinStore.load(entry);
                if(bitmap == null) throw new IOException("file missing");
                SkinStore.installDefaultSkinPack(gameDir, bitmap);
                SkinStore.saveActive(account, bitmap, entry.name, entry.url, entry.slim);
                account.updateFaceFromSkin(bitmap);
                message = activity.getString(R.string.spirit_ingame_skin_done, entry.name);
            } catch (IOException | RuntimeException e) {
                message = activity.getString(R.string.spirit_skin_apply_failed, String.valueOf(e.getMessage()));
            }
            final String m = message;
            Tools.runOnUiThread(() -> git.artdeell.mojo.utils.SpiritToast.makeText(activity, m, Toast.LENGTH_LONG).show());
        });
    }
}
