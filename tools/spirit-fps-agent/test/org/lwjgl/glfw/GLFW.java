package org.lwjgl.glfw;

/** Stand-in shaped like the real LWJGL class (a CHECKS branch before the native call). */
public class GLFW {
    static final boolean CHECKS = Boolean.getBoolean("stub.checks");
    public static long lastWindow;
    static void check(long w) { if(w == 0) throw new NullPointerException(); }
    static void invokePV(long w, long fn) { lastWindow = w; }
    public static class Functions { public static final long SwapBuffers = 42L; }

    public static void glfwSwapBuffers(long window) {
        long __functionAddress = Functions.SwapBuffers;
        if (CHECKS) {
            check(window);
        }
        invokePV(window, __functionAddress);
    }
}
