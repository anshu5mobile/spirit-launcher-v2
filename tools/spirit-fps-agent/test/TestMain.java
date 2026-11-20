public class TestMain {
    public static void main(String[] a) throws Exception {
        for(int i = 1; i <= 5000; i++) org.lwjgl.glfw.GLFW.glfwSwapBuffers(i);
        Class<?> c = Class.forName("git.spirit.fps.SpiritFpsCounter", false, null); // boot loader only
        System.out.println("count=" + c.getField("count").get(null) + " lastWindow=" + org.lwjgl.glfw.GLFW.lastWindow);
        Thread.sleep(2300);
        for(int i = 1; i <= 3000; i++) org.lwjgl.glfw.GLFW.glfwSwapBuffers(i);
        Thread.sleep(1200);
    }
}
