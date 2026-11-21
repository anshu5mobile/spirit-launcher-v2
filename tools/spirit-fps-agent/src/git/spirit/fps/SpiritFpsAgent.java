package git.spirit.fps;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * Counts the frames Minecraft really renders by counting org.lwjgl.glfw.GLFW.glfwSwapBuffers calls and writes
 * "fps epochMillis" to the file given as the agent argument once per second.
 * Every failure is swallowed: the worst case is that no number is written and the launcher falls back.
 */
public final class SpiritFpsAgent {
    private static final String TARGET_CLASS = "org/lwjgl/glfw/GLFW";
    private static final String TARGET_METHOD = "glfwSwapBuffers";
    private static final String TARGET_DESC = "(J)V";
    private static final String COUNTER_CLASS = "git/spirit/fps/SpiritFpsCounter";

    private SpiritFpsAgent() {}

    public static void premain(String args, Instrumentation inst) {
        try {
            // The counter class (spirit-fps-boot.jar) is put on the bootstrap class path by the launcher with
            // -Xbootclasspath/a:, so every class loader of the game sees it. Doing it here with
            // Instrumentation.appendToBootstrapClassLoaderSearch crashes the Android JVM (iconv_open in libinstrument).
            final String out = args;
            inst.addTransformer(new ClassFileTransformer() {
                @Override
                public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain pd, byte[] bytes) {
                    if(!TARGET_CLASS.equals(name)) return null;
                    try {
                        return ClassPatcher.insertStaticCall(bytes, TARGET_METHOD, TARGET_DESC, COUNTER_CLASS, "tick", "()V");
                    } catch (Throwable t) {
                        if(Boolean.getBoolean("spirit.fps.debug")) t.printStackTrace();
                        return null; // original bytes stay in use
                    }
                }
            });
            if(out != null && !out.isEmpty()) startReporter(new File(out));
        } catch (Throwable t) {
            if(Boolean.getBoolean("spirit.fps.debug")) t.printStackTrace();
        }
    }

    private static void startReporter(final File file) {
        Thread t = new Thread(() -> {
            int last = SpiritFpsCounter.count;
            long lastNs = System.nanoTime();
            while(true) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                try {
                    int now = SpiritFpsCounter.count;
                    long ns = System.nanoTime();
                    double seconds = Math.max(0.2, (ns - lastNs) / 1e9);
                    int fps = (int) Math.round((now - last) / seconds);
                    last = now;
                    lastNs = ns;
                    try (FileOutputStream os = new FileOutputStream(file, false)) {
                        os.write((fps + " " + System.currentTimeMillis()).getBytes("UTF-8"));
                    }
                } catch (Throwable ignored) {}
            }
        }, "Spirit-FPS");
        t.setDaemon(true);
        t.start();
    }
}
