package net.minenite.npcs.cognition;

import java.util.UUID;

/** Multi-dimensional relationship with one person (player or civilian). */
public final class Bond {
    public final UUID id;
    public String name;
    public double trust;
    public double fearOf;
    public double respect;
    public double liking;
    public double resentment;
    public double familiarity;
    public double debt;
    public double dependency;
    public double perceivedDanger;
    public double reliability;
    public int sharedHistory;
    public long lastSeenAt;
    public String lastPlace = "";

    public Bond(UUID id, String name) {
        this.id = id;
        this.name = name == null ? "someone" : name;
    }

    public void bumpFamiliar() {
        familiarity = DriveSet.clamp(familiarity + 0.04);
        sharedHistory++;
        lastSeenAt = System.currentTimeMillis();
    }

    /** Beliefs emerge from episodes — do not slam trust on every twitch. */
    public void applyBelief() {
        if (sharedHistory >= 3 && liking > 0.4 && trust < 0.3) {
            trust += 0.04;
        }
        if (perceivedDanger > 0.6 && fearOf < perceivedDanger) {
            fearOf = DriveSet.clamp(fearOf + 0.03);
        }
    }

    public String sketch() {
        return name + " trust=" + round(trust) + " like=" + round(liking)
                + " fear=" + round(fearOf) + " danger=" + round(perceivedDanger)
                + " known=" + round(familiarity);
    }

    private static String round(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
