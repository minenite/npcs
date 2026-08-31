package net.minenite.npcs.mind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything this one person has said, heard, seen, and decided.
 * The LLM reads a short digest of this every time they open their mouth.
 */
public final class CivilianMind {
    public record Beat(long at, String kind, String text) {
    }

    public record Face(String name, int trust, Deque<String> facts, long lastSeen) {
        Face(String name) {
            this(name, 0, new ArrayDeque<>(), 0L);
        }
    }

    private static final int MAX_BEATS = 80;
    private static final int MAX_FACTS = 8;

    private final Deque<Beat> life = new ArrayDeque<>();
    private final Map<UUID, Face> faces = new ConcurrentHashMap<>();
    private volatile Mood mood = Mood.CALM;
    private volatile long moodUntil;
    private volatile String lastSaid = "";

    public synchronized void said(String line) {
        if (blank(line)) {
            return;
        }
        lastSaid = line;
        push("SAID", "I said: " + line);
    }

    public synchronized void heard(String who, String line) {
        if (blank(who) || blank(line)) {
            return;
        }
        push("HEARD", who + " said: " + line);
    }

    public synchronized void did(String act) {
        if (!blank(act)) {
            push("DID", act);
        }
    }

    public synchronized void saw(String what) {
        if (!blank(what)) {
            push("SAW", what);
        }
    }

    public void feel(Mood next, int seconds) {
        this.mood = next;
        this.moodUntil = System.currentTimeMillis() + seconds * 1000L;
    }

    public Mood mood() {
        if (mood != Mood.CALM && System.currentTimeMillis() > moodUntil) {
            mood = Mood.CALM;
        }
        return mood;
    }

    public String lastSaid() {
        return lastSaid;
    }

    public void met(UUID id, String name, int trustDelta, String fact) {
        if (id == null) {
            return;
        }
        faces.compute(id, (key, face) -> {
            Face now = face == null ? new Face(name == null ? "someone" : name) : face;
            String keep = name == null || name.isBlank() ? now.name : name;
            int trust = clamp(now.trust + trustDelta, -10, 10);
            if (!blank(fact)) {
                now.facts.addLast(fact);
                while (now.facts.size() > MAX_FACTS) {
                    now.facts.removeFirst();
                }
            }
            return new Face(keep, trust, now.facts, System.currentTimeMillis());
        });
    }

    public int trust(UUID id) {
        Face face = id == null ? null : faces.get(id);
        return face == null ? 0 : face.trust;
    }

    public String know(UUID id) {
        Face face = id == null ? null : faces.get(id);
        if (face == null) {
            return "";
        }
        if (face.facts.isEmpty()) {
            return face.name + " (trust " + face.trust + ")";
        }
        return face.name + " (trust " + face.trust + "): " + String.join("; ", face.facts);
    }

    public boolean knows(UUID id) {
        return id != null && faces.containsKey(id);
    }

    public String digest(int max) {
        List<String> lines = new ArrayList<>();
        lines.add("mood: " + mood().spoken());
        synchronized (this) {
            int skip = Math.max(0, life.size() - max);
            int i = 0;
            for (Beat beat : life) {
                if (i++ >= skip) {
                    lines.add("- " + beat.text);
                }
            }
        }
        if (lines.size() == 1) {
            lines.add("- new to this stretch of road. nothing stored yet.");
        }
        return String.join("\n", lines);
    }

    public String recentSaid() {
        List<String> said = new ArrayList<>();
        synchronized (this) {
            for (Beat beat : life) {
                if ("SAID".equals(beat.kind)) {
                    said.add(beat.text.substring("I said: ".length()));
                }
            }
        }
        if (said.isEmpty()) {
            return "";
        }
        int from = Math.max(0, said.size() - 4);
        return String.join(" / ", said.subList(from, said.size()));
    }

    private void push(String kind, String text) {
        String fold = text.toLowerCase(Locale.ROOT);
        Beat last = life.peekLast();
        if (last != null && last.text.equalsIgnoreCase(text)) {
            return;
        }
        if (last != null && last.kind.equals(kind) && last.text.toLowerCase(Locale.ROOT).equals(fold)) {
            return;
        }
        life.addLast(new Beat(System.currentTimeMillis(), kind, text));
        while (life.size() > MAX_BEATS) {
            life.removeFirst();
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
