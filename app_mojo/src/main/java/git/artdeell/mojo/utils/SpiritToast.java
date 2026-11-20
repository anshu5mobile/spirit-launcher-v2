package git.artdeell.mojo.utils;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/** Toasts in the Spirit look (dark card, green edge). Same use as Toast.makeText(...).show(). */
public final class SpiritToast {
    private SpiritToast() {}

    public static Toast makeText(@NonNull Context context, @StringRes int resId, int duration) {
        return makeText(context, context.getText(resId), duration);
    }

    public static Toast makeText(@NonNull Context context, @NonNull CharSequence text, int duration) {
        Toast toast = new Toast(context);
        float d = context.getResources().getDisplayMetrics().density;
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(0xFFEAF2E0);
        view.setTextSize(14);
        view.setMaxWidth((int) (360 * d));
        view.setPadding((int) (18 * d), (int) (11 * d), (int) (18 * d), (int) (11 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2141812);
        bg.setCornerRadius(24 * d);
        bg.setStroke((int) Math.max(1, d), 0xFFA6F03A);
        view.setBackground(bg);
        toast.setView(view);
        toast.setDuration(duration);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, (int) (72 * d));
        return toast;
    }
}
