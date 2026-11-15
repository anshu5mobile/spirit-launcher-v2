package git.artdeell.mojo.game.render;

/** Counts frames presented by the game surface. */
public final class FpsMeter {
    private static volatile int sFrames = 0;
    private FpsMeter() {}
    public static void frame() { sFrames++; }
    /** Returns frames since the last call and resets the counter. */
    public static int drain() { int f = sFrames; sFrames = 0; return f; }
}
