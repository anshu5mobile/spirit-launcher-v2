package git.artdeell.mojo.perf;

import android.app.ActivityManager;
import android.content.Context;

/**
 * Picks a memory amount for the game from what the phone really has. The game needs a share of the RAM,
 * not all of it: Android, the launcher and other apps need the rest, and a game given too much gets killed.
 */
public final class AutoAllocate {
    private AutoAllocate() {}

    public static final class Result {
        public final int allocationMb, totalMb, freeMb;

        Result(int allocationMb, int totalMb, int freeMb) {
            this.allocationMb = allocationMb;
            this.totalMb = totalMb;
            this.freeMb = freeMb;
        }
    }

    /**
     * @param maxMb the highest value the memory slider accepts
     * @param minMb the lowest value the slider accepts
     * @param stepMb the slider step, the result is a multiple of it
     */
    public static Result compute(Context ctx, int maxMb, int minMb, int stepMb) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        int total = (int) (mi.totalMem / (1024 * 1024));
        int free = (int) (mi.availMem / (1024 * 1024));

        // Share of the total RAM: small phones can spare less of it
        double share;
        if(total <= 3200) share = 0.33;
        else if(total <= 4400) share = 0.38;
        else if(total <= 8400) share = 0.42;
        else share = 0.38;
        double target = total * share;
        // Many mods and shaders gain little above this, but a bigger heap only costs more garbage collection time
        target = Math.min(target, 6144);
        // What is free right now: a game that asks for more than what is left makes Android close apps, or itself
        target = Math.min(target, Math.max(free - 350, 768));

        int step = Math.max(1, stepMb);
        int value = (int) (target / step) * step;
        value = Math.min(value, maxMb);
        value = Math.max(value, minMb);
        return new Result(value, total, free);
    }
}
