package git.artdeell.mojo.crash;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a game log and explains, in plain words, what most likely went wrong. No Android classes here. */
public final class CrashAnalyzer {
    private CrashAnalyzer() {}

    public enum Fix { NONE, MORE_RAM, DISABLE_MOD, DISABLE_ALL_MODS, OPEN_SETTINGS }

    public static final class Finding {
        public final String id;
        public final Fix fix;
        /** For DISABLE_MOD: the mod id found in the log */
        public final String arg;

        public Finding(String id, Fix fix, String arg) {
            this.id = id;
            this.fix = fix;
            this.arg = arg;
        }
    }

    private static final Pattern MOD_QUOTED = Pattern.compile("provided by '([A-Za-z0-9_.\\-]+)'");
    private static final Pattern MIXIN_MOD = Pattern.compile("Mixin apply for mod ([A-Za-z0-9_.\\-]+) failed");
    private static final Pattern FABRIC_REQUIRES = Pattern.compile("Mod '[^']*' \\(([A-Za-z0-9_.\\-]+)\\) [^\\n]*?(requires|depends)");
    private static final Pattern FORGE_FAILED = Pattern.compile("([A-Za-z0-9_.\\-]+) \\(([A-Za-z0-9_.\\-]+)\\) has failed to load correctly");
    private static final Pattern MIXIN_CONFIG = Pattern.compile("([A-Za-z0-9_.\\-]+)\\.mixins?\\.json");

    public static List<Finding> analyze(String log, boolean nativeSignal) {
        List<Finding> out = new ArrayList<>();
        if(log == null) log = "";
        String lower = log.toLowerCase(Locale.ROOT);

        if(lower.contains("outofmemoryerror") || lower.contains("could not reserve enough space")
                || lower.contains("gc overhead limit") || lower.contains("java heap space")) {
            out.add(new Finding("oom", Fix.MORE_RAM, null));
        }
        if(lower.contains("unsupportedclassversionerror")) {
            out.add(new Finding("java_version", Fix.OPEN_SETTINGS, null));
        }
        if(lower.contains("no space left on device")) {
            out.add(new Finding("storage", Fix.NONE, null));
        }
        if(lower.contains("incompatible mod set") || lower.contains("missing or unsupported mandatory dependencies")
                || FABRIC_REQUIRES.matcher(log).find() && lower.contains("requires")) {
            out.add(new Finding("dependency", Fix.NONE, null));
        }

        Set<String> mods = new LinkedHashSet<>();
        collect(MOD_QUOTED, log, 1, mods);
        collect(MIXIN_MOD, log, 1, mods);
        collect(FABRIC_REQUIRES, log, 1, mods);
        Matcher forge = FORGE_FAILED.matcher(log);
        while(forge.find()) mods.add(forge.group(2));
        if(mods.isEmpty() && (lower.contains("mixin") && (lower.contains("failed") || lower.contains("invalidmixin")))) {
            collect(MIXIN_CONFIG, log, 1, mods);
        }
        mods.remove("minecraft");
        mods.remove("fabricloader");
        mods.remove("fabric-loader");
        mods.remove("forge");
        mods.remove("neoforge");
        mods.remove("java");
        int count = 0;
        for(String mod : mods) {
            out.add(new Finding("mod", Fix.DISABLE_MOD, mod));
            if(++count >= 3) break;
        }

        if(lower.contains("glfw error") || lower.contains("failed to create opengl") || lower.contains("couldn't set pixel format")
                || lower.contains("egl_bad") || lower.contains("could not create context") || lower.contains("glxbadcontext")
                || lower.contains("opengl 3.2 is not supported") || lower.contains("exit code: 255 ")) {
            out.add(new Finding("graphics", Fix.OPEN_SETTINGS, null));
        }
        if(nativeSignal && out.isEmpty()) {
            out.add(new Finding("signal", Fix.DISABLE_ALL_MODS, null));
        }
        if(out.isEmpty()) {
            out.add(new Finding("unknown", Fix.DISABLE_ALL_MODS, null));
        }
        return out;
    }

    private static void collect(Pattern p, String log, int group, Set<String> into) {
        Matcher m = p.matcher(log);
        while(m.find()) into.add(m.group(group));
    }

    /** Lower-cases and drops separators so "Sodium-Extra" and "sodium_extra" compare equal */
    public static String squash(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
