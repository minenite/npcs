package net.minenite.npcs.cognition;

import java.util.UUID;

/** Gossip with provenance. Hop 0 = witnessed. */
public final class Rumor {
    public String text;
    public UUID source;
    public String sourceName;
    public UUID originalWitness;
    public int hops;
    public double confidence;
    public long at;
    public String where;

    public Rumor mutate() {
        Rumor next = new Rumor();
        next.text = text;
        next.source = source;
        next.sourceName = sourceName;
        next.originalWitness = originalWitness;
        next.hops = hops + 1;
        next.confidence = Math.max(0.12, confidence * 0.78);
        next.at = System.currentTimeMillis();
        next.where = where;
        if (hops >= 1 && text != null && text.length() > 24) {
            next.text = text.replace("I saw ", "someone said ").replace("exactly ", "");
        }
        return next;
    }

    public String asKnown() {
        if (hops <= 0) {
            return "I saw: " + text;
        }
        if (hops == 1) {
            return (sourceName == null ? "someone" : sourceName) + " told me: " + text;
        }
        return "word is (" + hops + " mouths): " + text;
    }
}
