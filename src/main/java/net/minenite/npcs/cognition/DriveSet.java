package net.minenite.npcs.cognition;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Continuous internals. They drift, they couple, they never snap 0/1.
 */
public final class DriveSet {
    public double fear;
    public double stress;
    public double fatigue;
    public double hunger;
    public double thirst;
    public double pain;
    public double loneliness;
    public double boredom;
    public double curiosity;
    public double suspicion;
    public double confidence;
    public double aggression;
    public double desperation;
    public double attachment;
    public double safety = 0.55;
    public double urgency;

    public DriveSet() {
    }

    public DriveSet(Traits t) {
        hunger = 0.15 + ThreadLocalRandom.current().nextDouble() * 0.25;
        thirst = 0.12 + ThreadLocalRandom.current().nextDouble() * 0.22;
        fatigue = 0.10 + ThreadLocalRandom.current().nextDouble() * 0.20;
        curiosity = t.curiosity * 0.5;
        confidence = t.confidence;
        loneliness = 0.2 + (1.0 - t.sociability) * 0.2;
    }

    /** One game tick. walking/resting/threat are 0..1. */
    public void tick(Traits t, boolean walking, boolean resting, double threatNow,
                     double company, double trustedNear, boolean night, boolean hurt) {
        hunger = clamp(hunger + 0.000018);
        thirst = clamp(thirst + 0.000024);
        if (walking) {
            fatigue = clamp(fatigue + 0.00012 * (1.1 - t.resilience));
        } else if (resting) {
            fatigue = clamp(fatigue - 0.00035 * t.resilience);
        }
        if (hurt) {
            pain = clamp(pain + 0.08);
            stress = clamp(stress + 0.05);
            fear = clamp(fear + 0.04);
        }
        pain = clamp(pain * (0.9994 - t.painTolerance * 0.0002));
        double fearDecay = 0.9992 + t.resilience * 0.0004;
        fear = clamp(fear * fearDecay + threatNow * 0.035);
        stress = clamp(stress * 0.9993 + fear * 0.008 + (1.0 - safety) * 0.004);
        if (threatNow < 0.05) {
            safety = clamp(safety * 0.998 + 0.004);
        } else {
            safety = clamp(safety * 0.97);
        }
        loneliness = clamp(loneliness + (company < 0.15 ? 0.0004 * t.sociability : -0.0012));
        boredom = walking || threatNow > 0.2 ? clamp(boredom - 0.002) : clamp(boredom + 0.0003);
        curiosity = clamp(t.curiosity * 0.4 + boredom * 0.3 - fear * 0.25);
        suspicion = clamp(t.paranoia * 0.4 + fear * 0.2 + (threatNow > 0 ? 0.15 : 0) - trustedNear * 0.2);
        confidence = clamp(t.confidence * 0.6 + safety * 0.3 - fear * 0.35 - pain * 0.15);
        aggression = clamp(t.aggression * 0.5 + stress * 0.15 + pain * 0.1 - t.empathy * 0.2);
        desperation = clamp(hunger * 0.25 + thirst * 0.25 + pain * 0.2 + fear * 0.2 - confidence * 0.15);
        attachment = clamp(trustedNear * 0.7 + t.loyalty * 0.2);
        if (night) {
            fear = clamp(fear + 0.00008);
            safety = clamp(safety - 0.0001);
        }
        urgency = clamp(Math.max(Math.max(fear, desperation), threatNow) * 0.8 + (1.0 - safety) * 0.2);
    }

    public String digest() {
        return String.format(Locale.ROOT,
                "fear=%.2f stress=%.2f fatigue=%.2f hunger=%.2f thirst=%.2f pain=%.2f lonely=%.2f bored=%.2f cur=%.2f sus=%.2f conf=%.2f agg=%.2f desp=%.2f attach=%.2f safe=%.2f urg=%.2f",
                fear, stress, fatigue, hunger, thirst, pain, loneliness, boredom, curiosity,
                suspicion, confidence, aggression, desperation, attachment, safety, urgency);
    }

    public static double clamp(double v) {
        if (v < 0) {
            return 0;
        }
        return Math.min(1, v);
    }
}
