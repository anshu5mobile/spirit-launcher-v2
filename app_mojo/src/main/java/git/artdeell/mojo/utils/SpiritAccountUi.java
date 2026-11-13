package git.artdeell.mojo.utils;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import git.artdeell.mojo.R;
import git.artdeell.mojo.authenticator.AuthType;

/** Shared account labels/badges used by the header pill and the Accounts screen. */
public final class SpiritAccountUi {
    private SpiritAccountUi() {}

    @StringRes
    public static int badgeText(@NonNull AuthType type) {
        switch (type) {
            case MICROSOFT: return R.string.spirit_account_badge_ms;
            case ELY_BY: return R.string.spirit_account_badge_ely;
            default: return R.string.spirit_account_badge_local;
        }
    }

    @StringRes
    public static int subText(@NonNull AuthType type) {
        switch (type) {
            case MICROSOFT: return R.string.spirit_account_sub_ms;
            case ELY_BY: return R.string.spirit_account_sub_ely;
            default: return R.string.spirit_account_sub_local;
        }
    }

    /** Ely.by gets the filled lime tag, everything else the outlined one (matches the design). */
    public static boolean isFilledBadge(@NonNull AuthType type) {
        return type == AuthType.ELY_BY;
    }

    public static void styleBadge(@NonNull TextView view, boolean filled) {
        view.setBackgroundResource(filled ? R.drawable.spirit_tag_fill : R.drawable.spirit_tag_outline);
        view.setTextColor(ContextCompat.getColor(view.getContext(),
                filled ? R.color.spirit_on_lime : R.color.spirit_text));
    }

    public static void bindBadge(@NonNull TextView view, @NonNull AuthType type) {
        view.setText(badgeText(type));
        styleBadge(view, isFilledBadge(type));
    }
}
