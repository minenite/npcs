package net.minenite.npcs.cognition;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** One conversation thread so "how long ago?" still means the rifleman. */
public final class ConversationTrack {
    public UUID with;
    public String withName = "";
    public String topic = "";
    public String lastTheySaid = "";
    public String lastWeSaid = "";
    public String pendingReferent = "";
    public TalkWhy why = TalkWhy.NONE;
    public int turns;
    public long lastAt;
    public boolean theyWalkingAway;
    public final Deque<String> lines = new ArrayDeque<>();

    public void hear(String name, String line) {
        lastTheySaid = line;
        withName = name;
        lastAt = System.currentTimeMillis();
        lines.addLast(name + ": " + line);
        while (lines.size() > 8) {
            lines.removeFirst();
        }
        if (pendingReferent.isBlank()) {
            pendingReferent = guess(line);
        }
        if (topic.isBlank()) {
            topic = pendingReferent;
        }
    }

    public void weSaid(String line) {
        lastWeSaid = line;
        lastAt = System.currentTimeMillis();
        turns++;
        lines.addLast("me: " + line);
        while (lines.size() > 8) {
            lines.removeFirst();
        }
    }

    public String threadForPrompt() {
        if (lines.isEmpty()) {
            return "";
        }
        return "Conversation so far:\n" + String.join("\n", lines)
                + (pendingReferent.isBlank() ? "" : "\nCurrent referent: " + pendingReferent)
                + "\nSpeak-intent: " + why.name();
    }

    public boolean alive() {
        return System.currentTimeMillis() - lastAt < 45_000L;
    }

    private static String guess(String line) {
        String low = line.toLowerCase();
        if (low.contains("north") || low.contains("south") || low.contains("east") || low.contains("west")) {
            return "the person they mentioned in that direction";
        }
        if (low.contains("rifle") || low.contains("gun") || low.contains("armed")) {
            return "the armed person";
        }
        if (low.contains("seen") || low.contains("anyone")) {
            return "whether anyone has been around";
        }
        return "";
    }
}
