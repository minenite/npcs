package net.minenite.npcs.cognition;

import java.util.UUID;

/** What the scorer is allowed to know — already filtered by attention/uncertainty. */
public final class WorldCue {
    public double threat;
    public double aimedAt;
    public double armedStranger;
    public double visibleStranger;
    public double playerNear;
    public double friendNear;
    public double friendlyNear;
    public double hurtNear;
    public double corpse;
    public double soundConf;
    public double coverHere;
    public double indoors;
    public double night;
    public boolean rain;
    public double distanceFocus = -1;
    public UUID focusId;
}
