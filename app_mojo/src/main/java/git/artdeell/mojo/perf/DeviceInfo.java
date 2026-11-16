package git.artdeell.mojo.perf;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/** What the phone really is. Everything is read once, nothing keeps running afterwards. */
public final class DeviceInfo {
    public enum Tier { LOW, ENTRY, MID, HIGH }

    public int cores;
    /** Highest CPU clock in MHz, 0 when Android does not let us read it */
    public int maxMhz;
    public int bigCores;
    public long totalRamMb;
    public long freeRamMb;
    public String soc;
    public String abi;
    public int sdk;
    public int widthPx, heightPx;
    public float refreshHz;
    public long freeStorageMb;
    public Tier tier;

    public static DeviceInfo read(Context context) {
        DeviceInfo d = new DeviceInfo();
        d.cores = Runtime.getRuntime().availableProcessors();
        d.sdk = Build.VERSION.SDK_INT;
        d.abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown";
        String soc = Build.HARDWARE;
        if(Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL != null && !Build.SOC_MODEL.isEmpty() && !"unknown".equalsIgnoreCase(Build.SOC_MODEL)) {
            soc = (Build.SOC_MANUFACTURER == null ? "" : Build.SOC_MANUFACTURER + " ") + Build.SOC_MODEL;
        }
        d.soc = soc;

        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if(am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            d.totalRamMb = mi.totalMem / (1024 * 1024);
            d.freeRamMb = mi.availMem / (1024 * 1024);
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        d.widthPx = Math.max(metrics.widthPixels, metrics.heightPixels);
        d.heightPx = Math.min(metrics.widthPixels, metrics.heightPixels);
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        d.refreshHz = wm != null ? wm.getDefaultDisplay().getRefreshRate() : 60f;
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            d.freeStorageMb = fs.getAvailableBytes() / (1024 * 1024);
        } catch (RuntimeException ignored) {
            d.freeStorageMb = -1;
        }
        readCpuClocks(d);
        d.tier = classify(d);
        return d;
    }

    private static void readCpuClocks(DeviceInfo d) {
        for(int i = 0; i < d.cores && i < 16; i++) {
            File f = new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if(!f.canRead()) continue;
            try(BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line = r.readLine();
                if(line == null) continue;
                int mhz = Integer.parseInt(line.trim()) / 1000;
                if(mhz > d.maxMhz) d.maxMhz = mhz;
                if(mhz >= 2200) d.bigCores++;
            } catch (Exception ignored) {
                // unreadable on this device
            }
        }
    }

    /**
     * Score 0..3 from CPU speed (50%), RAM (30%) and how modern the Android build is (20%).
     * RAM alone says little: an old 8-core with 6 GB is still slow. The GPU cannot be read here without
     * a GL context, so it is not part of the score.
     */
    static Tier classify(DeviceInfo d) {
        float cpu;
        if(d.maxMhz > 0) {
            cpu = d.maxMhz < 2000 ? 0 : d.maxMhz < 2400 ? 1 : d.maxMhz < 2800 ? 2 : 3;
            if(d.bigCores >= 3 && cpu < 3) cpu += 0.5f;
            if(d.cores <= 4 && cpu > 1) cpu -= 0.5f;
        } else {
            cpu = d.cores <= 4 ? 0 : d.cores < 8 ? 1 : 1.5f;
        }
        float ram = d.totalRamMb < 3000 ? 0 : d.totalRamMb < 4500 ? 1 : d.totalRamMb < 7000 ? 2 : 3;
        float os = d.sdk < 28 ? 0 : d.sdk < 30 ? 1 : d.sdk < 33 ? 2 : 3;
        float score = cpu * 0.5f + ram * 0.3f + os * 0.2f;
        if(score < 0.9f) return Tier.LOW;
        if(score < 1.6f) return Tier.ENTRY;
        if(score < 2.3f) return Tier.MID;
        return Tier.HIGH;
    }

    /** Safe heap for the game in MB. Android, the launcher and the system keep the rest. */
    public int recommendedRamMb() {
        long t = totalRamMb;
        int rec;
        if(t <= 2200) rec = 768;
        else if(t <= 3300) rec = 1024;
        else if(t <= 4300) rec = 1536;
        else if(t <= 6300) rec = 2048;
        else if(t <= 8300) rec = 2560;
        else rec = 3072;
        int cap = (int) (t * 0.45);
        return Math.max(512, Math.min(rec, cap)) / 64 * 64;
    }
}
