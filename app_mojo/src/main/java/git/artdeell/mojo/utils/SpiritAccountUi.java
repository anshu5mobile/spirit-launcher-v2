package git.artdeell.mojo.utils;

import android.widget.TextView;

import androidx.annotation.DrawableRes;
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

    /** Provider logo: Microsoft squares, Ely.by "E", or the offline plug. */
    @DrawableRes
    public static int typeIcon(@NonNull AuthType type) {
        switch (type) {
            case MICROSOFT: return R.drawable.ic_spirit_type_ms;
            case ELY_BY: return R.drawable.ic_spirit_type_ely;
            default: return R.drawable.ic_spirit_type_offline;
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
