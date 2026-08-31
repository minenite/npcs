package net.minenite.npcs.cognition;

import org.bukkit.Location;

import java.util.UUID;

/** Object permanence: last known, not live coordinates. */
public final class LastSeen {
    public UUID id;
    public String name;
    public String place;
    public double x, y, z;
    public String world;
    public float heading;
    public long at;
    public double confidence;

    public Location guess(org.bukkit.World w) {
        if (w == null || world == null || !world.equals(w.getName())) {
            return null;
        }
        double ageSec = (System.currentTimeMillis() - at) / 1000.0;
        double dist = Math.min(18, ageSec * 1.4);
        double rad = Math.toRadians(heading);
        double gx = x - Math.sin(rad) * dist;
        double gz = z + Math.cos(rad) * dist;
        // they can be wrong — extra noise grows with age
        double n = Math.min(8, ageSec * 0.25);
        gx += (Math.random() - 0.5) * n;
        gz += (Math.random() - 0.5) * n;
        return new Location(w, gx, y, gz);
    }

    public boolean stale() {
        return System.currentTimeMillis() - at > 90_000L || confidence < 0.12;
    }
}
