package git.artdeell.mojo.perf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the garbage collector log of the last game session (the JVM writes it, see JavaRunner) and sums it up.
 * These are real numbers from the player's own phone: how often the game stopped for the collector and for how long.
 */
public final class GcReport {
    public int pauses;
    public int over50;
    public int over100;
    public int fullGcs;
    public double maxMs;
    public double totalMs;
    public double sessionSeconds;
    public int heapMb;

    private static final Pattern PAUSE = Pattern.compile("^\\[([\\d.]+)s\\].*?Pause (.*?)\\s+([\\d.]+)ms\\s*$");
    private static final Pattern HEAP = Pattern.compile("\\d+M->\\d+M\\((\\d+)M\\)");

    /** @return null when there is no log (the game has not been played with it yet) */
    public static GcReport read(File log) {
        if(log == null || !log.isFile()) return null;
        GcReport r = new GcReport();
        try(BufferedReader in = new BufferedReader(new FileReader(log))) {
            String line;
            while((line = in.readLine()) != null) {
                Matcher m = PAUSE.matcher(line);
                if(!m.find()) continue;
                double at = Double.parseDouble(m.group(1));
                double ms = Double.parseDouble(m.group(3));
                String kind = m.group(2);
                r.pauses++;
                r.totalMs += ms;
                if(ms > r.maxMs) r.maxMs = ms;
                if(ms >= 50) r.over50++;
                if(ms >= 100) r.over100++;
                if(kind.startsWith("Full")) r.fullGcs++;
                if(at > r.sessionSeconds) r.sessionSeconds = at;
                Matcher h = HEAP.matcher(line);
                if(h.find()) r.heapMb = Integer.parseInt(h.group(1));
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
        return r.pauses == 0 && r.sessionSeconds == 0 ? null : r;
    }

    /** Share of the session in which the game was stopped by the collector, in percent */
    public double pausePercent() {
        return sessionSeconds <= 0 ? 0 : totalMs / (sessionSeconds * 10.0);
    }
}
