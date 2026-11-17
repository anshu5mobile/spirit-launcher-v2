package git.artdeell.mojo.utils.plugins;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Downloads a plugin APK and hands it to Android's installer. Android always asks the person to confirm an
 * install, an app cannot install another app silently.
 */
public final class PluginInstaller {
    private PluginInstaller() {}

    /** The FFmpeg plugin the MojoLauncher project publishes as a GitHub release */
    public static final String FFMPEG_URL = "https://github.com/MojoLauncher/FFmpegPlugin/releases/download/rel/app-release.apk";
    public static final String FFMPEG_PAGE = "https://github.com/MojoLauncher/FFmpegPlugin/releases";

    public interface Progress {
        void onProgress(int percent);
    }

    /** Blocking: call it off the main thread. */
    @NonNull
    public static File download(@NonNull Context ctx, @NonNull String url, @NonNull String fileName, Progress progress) throws IOException {
        File dir = new File(ctx.getCacheDir(), "plugins");
        if(!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
        File target = new File(dir, fileName);
        File part = new File(dir, fileName + ".part");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        try {
            int code = c.getResponseCode();
            if(code != 200) throw new IOException("HTTP " + code);
            long total = c.getContentLengthLong();
            try(InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[1 << 16];
                long done = 0;
                int last = -1, r;
                while((r = in.read(buf)) != -1) {
                    out.write(buf, 0, r);
                    done += r;
                    if(progress != null && total > 0) {
                        int p = (int) (done * 100 / total);
                        if(p != last) { last = p; progress.onProgress(p); }
                    }
                }
            }
        } finally {
            c.disconnect();
        }
        // An error page saved as an APK would only confuse the installer
        if(part.length() < 100 * 1024) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw new IOException("The download is not an APK");
        }
        if(target.exists() && !target.delete()) throw new IOException("Could not replace " + target);
        if(!part.renameTo(target)) throw new IOException("Could not save " + target);
        return target;
    }

    /**
     * Opens the Android installer for the APK.
     * @return false when the person first has to allow installs from this app (the settings page was opened)
     */
    public static boolean install(@NonNull Activity activity, @NonNull File apk) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settings);
            return false;
        }
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".spirit.fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(intent);
        return true;
    }
}
