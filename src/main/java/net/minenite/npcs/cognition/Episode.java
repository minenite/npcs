package net.minenite.npcs.cognition;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Structured event. Text for the LLM is derived later, and compresses with age. */
public final class Episode {
    public long at;
    public String who;
    public UUID whoId;
    public String what;
    public String where;
    public double x, z;
    public String world;
    public double intensity;
    public double certainty;
    public double importance;
    public String result = "";
    public final List<String> related = new ArrayList<>();

    public String recall() {
        long age = System.currentTimeMillis() - at;
        if (age < 45_000L && certainty > 0.7) {
            return who + " " + what + " at " + where;
        }
        if (age < 8 * 60_000L) {
            return who + " " + soften(what) + " near " + where;
        }
        return who + " has been " + soften(what) + " around town";
    }

    private static String soften(String what) {
        if (what == null) {
            return "involved";
        }
        if (what.contains("aim")) {
            return "pointing guns";
        }
        if (what.contains("shot") || what.contains("kill") || what.contains("fire")) {
            return "shooting";
        }
        if (what.contains("help") || what.contains("gave") || what.contains("food")) {
            return "helping people";
        }
        return what;
    }
}
