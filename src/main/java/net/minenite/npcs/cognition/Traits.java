package net.minenite.npcs.cognition;

import net.minenite.npcs.civilian.Personality;

/**
 * Personality as knobs, not scripts. Same world, different numbers.
 */
public final class Traits {
    public final double riskTolerance;
    public final double sociability;
    public final double aggression;
    public final double empathy;
    public final double curiosity;
    public final double paranoia;
    public final double patience;
    public final double loyalty;
    public final double impulsiveness;
    public final double confidence;
    public final double talkativeness;
    public final double resilience;
    public final double pace;
    public final double reaction;
    public final double personalSpace;
    public final double aimSteady;
    public final double painTolerance;
    public final double headTurn;
    public final double fidget;
    public final double hearing;

    public Traits(Personality p) {
        this.riskTolerance = n(p, 0.25, 0.35, 0.55, 0.40, 0.20, 0.45, 0.30, 0.55, 0.35, 0.50);
        this.sociability = n(p, 0.35, 0.40, 0.55, 0.45, 0.15, 0.80, 0.30, 0.50, 0.50, 0.55);
        this.aggression = n(p, 0.25, 0.40, 0.45, 0.20, 0.55, 0.20, 0.35, 0.25, 0.15, 0.40);
        this.empathy = n(p, 0.45, 0.70, 0.25, 0.85, 0.20, 0.60, 0.50, 0.55, 0.90, 0.30);
        this.curiosity = n(p, 0.40, 0.30, 0.45, 0.40, 0.55, 0.50, 0.25, 0.60, 0.35, 0.35);
        this.paranoia = n(p, 0.70, 0.40, 0.45, 0.35, 0.90, 0.30, 0.35, 0.55, 0.40, 0.50);
        this.patience = n(p, 0.25, 0.70, 0.40, 0.65, 0.30, 0.20, 0.80, 0.25, 0.70, 0.45);
        this.loyalty = n(p, 0.35, 0.80, 0.30, 0.60, 0.20, 0.55, 0.65, 0.45, 0.70, 0.35);
        this.impulsiveness = n(p, 0.60, 0.25, 0.40, 0.30, 0.50, 0.55, 0.20, 0.75, 0.25, 0.40);
        this.confidence = n(p, 0.25, 0.45, 0.65, 0.40, 0.55, 0.45, 0.60, 0.20, 0.40, 0.55);
        this.talkativeness = n(p, 0.35, 0.30, 0.70, 0.35, 0.20, 0.80, 0.15, 0.55, 0.40, 0.65);
        this.resilience = n(p, 0.30, 0.60, 0.55, 0.50, 0.45, 0.35, 0.75, 0.25, 0.65, 0.50);
        this.pace = 0.85 + p.walkMul() * 0.15;
        this.reaction = switch (p) {
            case PARANOID_LONER -> 0.90;
            case NERVOUS_SCAV, JITTERY_TEEN -> 0.80;
            case FRIENDLY_DRUNK -> 0.35;
            case TIRED_FATHER, STOIC_FARMER -> 0.45;
            default -> 0.60;
        };
        this.personalSpace = 1.6 + paranoia * 0.8 + (1.0 - sociability) * 0.6;
        this.aimSteady = 0.35 + confidence * 0.4 - impulsiveness * 0.2;
        this.painTolerance = 0.3 + resilience * 0.5;
        this.headTurn = 0.12 + reaction * 0.18;
        this.fidget = 0.15 + impulsiveness * 0.4 + (1.0 - patience) * 0.2;
        this.hearing = 0.55 + paranoia * 0.25 + (p == Personality.PARANOID_LONER ? 0.15 : 0);
    }

    private static double n(Personality p, double... v) {
        return v[p.ordinal()];
    }
}
