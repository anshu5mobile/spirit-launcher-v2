import git.spirit.fps.ClassPatcher;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Patches every method/constructor of many real javac classes one at a time and lets the JVM verify each result. */
public class Stress {
    static byte[] patchedBytes; static String patchName, patchDesc; static String targetClass;

    static class L extends ClassLoader {
        L() { super(null); }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                Path p = Paths.get(URI.create("jrt:/jdk.compiler/" + name.replace('.', '/') + ".class"));
                byte[] b = Files.readAllBytes(p);
                if(name.equals(targetClass) && patchName != null) {
                    b = ClassPatcher.insertStaticCall(b, patchName, patchDesc, "git/spirit/fps/SpiritFpsCounter", "tick", "()V");
                }
                return defineClass(name, b, 0, b.length);
            } catch (java.io.IOException e) {
                // not in jdk.compiler: java.base etc. come from the boot loader through the null parent normally
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    public static void main(String[] a) throws Exception {
        FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
        List<String> names;
        try(Stream<Path> s = Files.walk(jrt.getPath("/modules/jdk.compiler/com/sun/tools/javac"))) {
            names = s.filter(p -> p.toString().endsWith(".class"))
                     .map(p -> p.toString().substring("/modules/jdk.compiler/".length()).replace('/', '.').replaceAll("\\.class$", ""))
                     .sorted().collect(Collectors.toList());
        }
        int max = Integer.parseInt(a[0]);
        Collections.shuffle(names, new Random(7));
        int classes = 0, patched = 0, failed = 0, skipped = 0;
        for(String n : names) {
            if(classes >= max) break;
            targetClass = n; patchName = null;
            List<String[]> todo = new ArrayList<>();
            try {
                Class<?> c = new L().loadClass(n);
                for(Method m : c.getDeclaredMethods())
                    if(!Modifier.isAbstract(m.getModifiers()) && !Modifier.isNative(m.getModifiers()))
                        todo.add(new String[]{m.getName(), MethodType.methodType(m.getReturnType(), m.getParameterTypes()).toMethodDescriptorString()});
                for(Constructor<?> k : c.getDeclaredConstructors())
                    todo.add(new String[]{"<init>", MethodType.methodType(void.class, k.getParameterTypes()).toMethodDescriptorString()});
            } catch (Throwable t) { if(skipped++ < 2) t.printStackTrace(System.out); continue; }
            classes++;
            for(String[] t : todo) {
                patchName = t[0]; patchDesc = t[1];
                try {
                    Class<?> c = new L().loadClass(n);
                    c.getDeclaredMethods(); // forces linking = verification (-Xverify:all)
                    patched++;
                } catch (VerifyError | ClassFormatError | IncompatibleClassChangeError e) {
                    failed++; System.out.println("FAIL " + n + "." + t[0] + t[1] + " -> " + e);
                } catch (RuntimeException | NoClassDefFoundError e) {
                    skipped++;
                } catch (Throwable e) { skipped++; }
            }
        }
        System.out.println("classes=" + classes + " methodsPatchedAndVerified=" + patched + " failed=" + failed + " skipped=" + skipped);
    }
}
