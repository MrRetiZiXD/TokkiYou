package cat.narezany.tokkiyou;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stalls and slow frames counted up, apart from how they are caught.
 *
 * One stall says little: an app stops for a tenth of a second for a hundred
 * reasons. What says something is the same place turning up again and again,
 * so stalls are kept by where they were, and a report puts the places that
 * cost the most time first. Frames are kept by the screen they were drawn
 * on, with how a slow frame's time was spent, because "the feed drops frames
 * while it lays itself out" and "the feed drops frames in the GPU" are two
 * different faults.
 */
final class LagTally {

    /** No more places than this are kept; what comes after is counted as one. */
    static final int PLACES = 40;
    static final String REST = "elsewhere";

    private static final class Place {
        int times;
        long total, longest;
        String example;
    }

    private static final class Screen {
        long frames, slow, stuck;
        final long[] spent;
        Screen(int stages) { spent = new long[stages]; }
    }

    private final Map<String, Place> places = new HashMap<String, Place>();
    private final Map<String, Screen> screens = new HashMap<String, Screen>();
    private final String[] stages;

    LagTally(String[] stages) {
        this.stages = stages;
    }

    /** The main thread did nothing else for `took` milliseconds, at `where`. */
    synchronized void stall(String where, String example, long took) {
        String key = places.containsKey(where) || places.size() < PLACES ? where : REST;
        Place place = places.get(key);
        if (place == null) {
            place = new Place();
            places.put(key, place);
        }
        place.times++;
        place.total += took;
        if (took > place.longest) {
            place.longest = took;
            place.example = example;
        }
    }

    /** A frame was drawn on a screen. `spent` is nanoseconds a stage, for a slow one. */
    synchronized void frame(String screen, boolean slow, boolean stuck, long[] spent) {
        Screen known = screens.get(screen);
        if (known == null) {
            if (screens.size() >= PLACES) return;
            known = new Screen(stages.length);
            screens.put(screen, known);
        }
        known.frames++;
        if (!slow) return;
        known.slow++;
        if (stuck) known.stuck++;
        for (int i = 0; i < stages.length && i < spent.length; i++) {
            if (spent[i] > 0) known.spent[i] += spent[i];
        }
    }

    /** Everything counted so far, the costliest first, as lines for a report. */
    synchronized String report() {
        StringBuilder out = new StringBuilder();
        List<String> names = new ArrayList<String>(screens.keySet());
        Collections.sort(names, (one, two) -> Long.compare(screens.get(two).frames, screens.get(one).frames));
        for (String name : names) {
            Screen screen = screens.get(name);
            out.append(String.format(Locale.US, "%s: %d frames, %d slow (%.1f%%), %d stuck",
                    name, screen.frames, screen.slow, 100.0 * screen.slow / Math.max(1, screen.frames), screen.stuck));
            long all = 0;
            for (long one : screen.spent) all += one;
            if (all > 0) {
                out.append("; slow frames went on");
                List<Integer> order = new ArrayList<Integer>();
                for (int i = 0; i < stages.length; i++) order.add(i);
                Collections.sort(order, (one, two) -> Long.compare(screen.spent[two], screen.spent[one]));
                int said = 0;
                for (int i : order) {
                    long share = Math.round(100.0 * screen.spent[i] / all);
                    if (share < 5 || said >= 4) break;
                    out.append(said == 0 ? " " : ", ").append(stages[i]).append(' ').append(share).append('%');
                    said++;
                }
            }
            out.append('\n');
        }
        List<String> where = new ArrayList<String>(places.keySet());
        Collections.sort(where, (one, two) -> Long.compare(places.get(two).total, places.get(one).total));
        if (where.isEmpty()) out.append("no stalls\n");
        for (String key : where) {
            Place place = places.get(key);
            out.append(String.format(Locale.US, "%d ms in %d stalls, longest %d: %s",
                    place.total, place.times, place.longest, key));
            if (place.example != null && !place.example.equals(key)) out.append("  [").append(place.example).append(']');
            out.append('\n');
        }
        return out.toString();
    }
}
