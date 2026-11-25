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

    public enum Fix { NONE, MORE_RAM, DISABLE_MOD, DISABLE_ALL_MODS, OPEN_SETTINGS, UPDATE_LOADER, INSTALL_MOD, UPDATE_MODS }

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
    private static final Pattern MIXIN_MOD = Pattern.compile("Mixin (?:apply|prepare) for mod ([A-Za-z0-9_.\\-]+) failed");
    /** "[some.mixins.json:Name from mod modid]" in a MixinApplyError line */
    private static final Pattern MIXIN_FROM_MOD = Pattern.compile("\\[[^\\]\\n]*? from mod ([A-Za-z0-9_.\\-]+)\\]");
    private static final Pattern FABRIC_REQUIRES = Pattern.compile("Mod '[^']*' \\(([A-Za-z0-9_.\\-]+)\\) [^\\n]*?(requires|depends)");
    private static final Pattern FORGE_FAILED = Pattern.compile("([A-Za-z0-9_.\\-]+) \\(([A-Za-z0-9_.\\-]+)\\) has failed to load correctly");
    private static final Pattern MIXIN_CONFIG = Pattern.compile("([A-Za-z0-9_.\\-]+)\\.mixins?\\.json");

    // "Replace mod 'Fabric Loader' (fabricloader) 0.19.3 with version 0.20.1 or later" and
    // "requires version 0.20.1 or later of mod 'Fabric Loader'"
    private static final Pattern LOADER_REPLACE = Pattern.compile("Replace mod '(?:Fabric|Quilt) Loader'[^\\n]*? with version ([0-9][A-Za-z0-9_.+\\-]*)");
    private static final Pattern LOADER_REQUIRES = Pattern.compile("requires version ([0-9][A-Za-z0-9_.+\\-]*) or later of mod '(?:Fabric|Quilt) Loader'");

    // Fabric's own suggestions: "Install walksylib, version 2.0.0 or later." and
    // "Replace mod 'Sodium' (sodium) 0.5.0 with version 0.6.0 or later."
    private static final Pattern INSTALL_SOLUTION = Pattern.compile("Install ([A-Za-z0-9_.\\-]+), (?:any version|version [^\\s,]+)");
    private static final Pattern REPLACE_SOLUTION = Pattern.compile("Replace mod '[^']*' \\(([A-Za-z0-9_.\\-]+)\\) [^\\n]*? with version");
    private static final Pattern REQUIRES_WRONG = Pattern.compile("requires version [^\\n]*? of mod '[^']*' \\(([A-Za-z0-9_.\\-]+)\\)");

    private static boolean isCore(String id) {
        return id.equals("minecraft") || id.equals("java") || id.equals("fabricloader") || id.equals("fabric-loader")
                || id.equals("quilt_loader") || id.equals("quilt-loader") || id.equals("forge") || id.equals("neoforge");
    }

    /** The loader version a crash log asks for, or null when it does not ask for a newer loader */
    public static String requiredLoaderVersion(String log) {
        Matcher m = LOADER_REPLACE.matcher(log);
        if(m.find()) return m.group(1);
        String best = null;
        m = LOADER_REQUIRES.matcher(log);
        while(m.find()) {
            if(best == null || git.artdeell.mojo.modding.LoaderSwitcher.compare(m.group(1), best) > 0) best = m.group(1);
        }
        return best;
    }

    public static List<Finding> analyze(String log, boolean nativeSignal) {
        List<Finding> out = new ArrayList<>();
        if(log == null) log = "";
        String lower = log.toLowerCase(Locale.ROOT);

        if(lower.contains("outofmemoryerror") || lower.contains("could not reserve enough space")
                || lower.contains("gc overhead limit") || lower.contains("java heap space")) {
            out.add(new Finding("oom", Fix.MORE_RAM, null));
        }
        String loaderNeeded = requiredLoaderVersion(log);
        if(loaderNeeded != null) {
            out.add(new Finding("loader_old", Fix.UPDATE_LOADER, loaderNeeded));
        }
        if(lower.contains("unsupportedclassversionerror")) {
            out.add(new Finding("java_version", Fix.OPEN_SETTINGS, null));
        }
        if(lower.contains("no space left on device")) {
            out.add(new Finding("storage", Fix.NONE, null));
        }
        // Missing and outdated mods: these can be fixed by installing or updating, not by turning mods off
        Set<String> toInstall = new LinkedHashSet<>();
        Matcher inst = INSTALL_SOLUTION.matcher(log);
        while(inst.find()) if(!isCore(inst.group(1))) toInstall.add(inst.group(1));
        Set<String> outdated = new LinkedHashSet<>();
        Matcher rep = REPLACE_SOLUTION.matcher(log);
        while(rep.find()) if(!isCore(rep.group(1))) outdated.add(rep.group(1));
        Matcher wrong = REQUIRES_WRONG.matcher(log);
        while(wrong.find()) if(!isCore(wrong.group(1)) && !toInstall.contains(wrong.group(1))) outdated.add(wrong.group(1));
        int shown = 0;
        for(String id : toInstall) {
            out.add(new Finding("dep_missing", Fix.INSTALL_MOD, id));
            if(++shown >= 4) break;
        }
        if(!outdated.isEmpty()) {
            out.add(new Finding("mods_old", Fix.UPDATE_MODS, join(outdated)));
        }
        boolean fixableByInstall = !toInstall.isEmpty() || !outdated.isEmpty();
        if(loaderNeeded == null && !fixableByInstall && (lower.contains("incompatible mod set") || lower.contains("missing or unsupported mandatory dependencies")
                || FABRIC_REQUIRES.matcher(log).find() && lower.contains("requires"))) {
            out.add(new Finding("dependency", Fix.NONE, null));
        }

        Set<String> mods = new LinkedHashSet<>();
        // A broken mixin names the mod that owns it. The mod "provided by" in the same crash (often Sodium, whose
        // entrypoint merely triggered the failing class load) is only the victim, so it is left out when the owner is known.
        collect(MIXIN_MOD, log, 1, mods);
        collect(MIXIN_FROM_MOD, log, 1, mods);
        if(mods.isEmpty()) collect(MOD_QUOTED, log, 1, mods);
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
        // When the loader itself is too old, disabling the mods that need the newer one would be wrong advice
        if(loaderNeeded != null || fixableByInstall) mods.clear();
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

    private static String join(Set<String> items) {
        StringBuilder sb = new StringBuilder();
        for(String i : items) { if(sb.length() > 0) sb.append(", "); sb.append(i); }
        return sb.toString();
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
