package net.minenite.npcs.cognition;

public final class Plan {
    public String why = "";
    public Intention goal = Intention.WAIT;
    public String[] steps = new String[0];
    public int step;
    public long started;
    public long failAfter;

    public String current() {
        if (steps.length == 0 || step >= steps.length) {
            return why;
        }
        return steps[step] + " because " + why;
    }

    public boolean done() {
        return step >= steps.length;
    }

    public boolean expired() {
        return failAfter > 0 && System.currentTimeMillis() > failAfter;
    }
}
