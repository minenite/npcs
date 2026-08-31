package net.minenite.npcs.cognition;

import java.util.UUID;

public final class Attention {
    public UUID id;
    public String label;
    public double weight;
    public long until;

    public boolean is(UUID other) {
        return id != null && id.equals(other);
    }
}
