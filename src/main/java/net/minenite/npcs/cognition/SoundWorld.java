package net.minenite.npcs.cognition;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SoundWorld {
    public enum Kind {
        GUN, SUPPRESSED, FOOT, SPRINT, DOOR, GLASS, EXPLODE, RELOAD, VEHICLE, TALK, FIGHT, IMPACT
    }

    public static final class Pulse {
        public Location at;
        public Kind kind;
        public double loud;
        public UUID source;
        public long atMs;
    }

    private final List<Pulse> live = new ArrayList<>();

    public void emit(Location at, Kind kind, double loud, UUID source) {
        if (at == null) {
            return;
        }
        Pulse p = new Pulse();
        p.at = at.clone();
        p.kind = kind;
        p.loud = loud;
        p.source = source;
        p.atMs = System.currentTimeMillis();
        synchronized (live) {
            live.add(p);
            if (live.size() > 80) {
                live.remove(0);
            }
        }
    }

    public Pulse hear(Location ear, Traits traits) {
        if (ear == null || ear.getWorld() == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        Pulse best = null;
        double bestV = 0;
        synchronized (live) {
            live.removeIf(p -> now - p.atMs > 4000);
            for (Pulse p : live) {
                if (p.at.getWorld() != ear.getWorld()) {
                    continue;
                }
                double dist = p.at.distance(ear);
                double reach = p.loud * 18 * (0.7 + traits.hearing);
                if (dist > reach) {
                    continue;
                }
                double v = (1.0 - dist / reach) * p.loud;
                if (v > bestV) {
                    bestV = v;
                    best = p;
                }
            }
        }
        return best;
    }

    /** Uncertain origin — walls and distance scramble it. */
    public static Location guessed(Location ear, Pulse p, double hearing) {
        if (p == null) {
            return null;
        }
        double dist = ear.distance(p.at);
        double err = 1.2 + dist * (0.12 / Math.max(0.3, hearing));
        Vector n = p.at.toVector().subtract(ear.toVector());
        if (n.lengthSquared() < 1e-6) {
            return p.at.clone();
        }
        n.normalize();
        Location g = ear.clone().add(n.multiply(dist * (0.7 + Math.random() * 0.5)));
        g.add((Math.random() - 0.5) * err, 0, (Math.random() - 0.5) * err);
        return g;
    }

    public static double confidence(Location ear, Pulse p, boolean walls, double hearing) {
        if (p == null) {
            return 0;
        }
        double dist = ear.distance(p.at);
        double c = p.loud * (1.0 - Math.min(1, dist / 40.0)) * hearing;
        if (walls) {
            c *= 0.45;
        }
        if (p.kind == Kind.SUPPRESSED) {
            c *= 0.5;
        }
        return DriveSet.clamp(c);
    }
}
