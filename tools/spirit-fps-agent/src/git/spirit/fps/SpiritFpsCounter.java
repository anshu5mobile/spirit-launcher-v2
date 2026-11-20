package git.spirit.fps;

/** Loaded by the bootstrap loader (see SpiritFpsAgent), so every class loader of the game can call tick(). */
public final class SpiritFpsCounter {
    private SpiritFpsCounter() {}

    /** Written only by the render thread, read by the reporter thread. */
    public static volatile int count = 0;

    /** Injected at the start of GLFW.glfwSwapBuffers(long): one call is one frame of the game. */
    public static void tick() {
        count++;
    }
}
