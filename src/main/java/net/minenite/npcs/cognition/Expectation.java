package net.minenite.npcs.cognition;

public final class Expectation {
    public String text;
    public long by;
    public boolean broken;

    public boolean overdue() {
        return System.currentTimeMillis() > by && !broken;
    }
}
