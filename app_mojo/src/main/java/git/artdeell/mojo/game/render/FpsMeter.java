package git.artdeell.mojo.game.render;

/** Counts frames presented by the game surface. */
public final class FpsMeter {
    private static volatile int sFrames = 0;
    private FpsMeter() {}
    private static volatile long sTotal = 0;
    public static void frame() { sFrames++; sTotal++; }
    /** Frames presented since the process started, never reset (used to know the game really draws). */
    public static long total() { return sTotal; }
    /** Returns frames since the last call and resets the counter. */
    public static int drain() { int f = sFrames; sFrames = 0; return f; }
}
