package net.minenite.npcs.cognition;

import net.minenite.npcs.civilian.CivilianNpc;
import org.bukkit.entity.LivingEntity;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/** Temporary groups and informal roles. Nothing is permanent. */
public final class Groups {
    private Groups() {
    }

    public static void tick(CivilianNpc npc, Collection<CivilianNpc> all) {
        LivingEntity body = npc.body();
        if (body == null) {
            return;
        }
        Cognition c = npc.cog();
        CivilianNpc closest = null;
        double best = 14 * 14;
        for (CivilianNpc other : all) {
            if (other == npc || other.body() == null || other.body().getWorld() != body.getWorld()) {
                continue;
            }
            double d = other.body().getLocation().distanceSquared(body.getLocation());
            if (d < best) {
                best = d;
                closest = other;
            }
        }
        if (closest == null) {
            if (c.groupWith != null && ThreadLocalRandom.current().nextInt(500) == 0) {
                c.groupWith = null;
                c.role = "";
            }
            return;
        }
        Bond b = c.bond(closest.id(), closest.name());
        Bond theirs = closest.cog().bond(npc.id(), npc.name());
        if (best < 36) {
            b.bumpFamiliar();
            theirs.bumpFamiliar();
            b.liking = DriveSet.clamp(b.liking + 0.01 * c.traits.sociability);
        }
        if (b.resentment > 0.55 || theirs.resentment > 0.55) {
            if (c.groupWith != null && c.groupWith.equals(closest.id())) {
                c.groupWith = null;
                c.role = "";
                closest.cog().groupWith = null;
                closest.cog().role = "";
            }
            return;
        }
        if (b.liking > 0.22 && b.trust > 0.05 && c.traits.sociability > 0.35
                && c.drives.loneliness > 0.25) {
            c.groupWith = closest.id();
            closest.cog().groupWith = npc.id();
            c.role = emerge(c);
            closest.cog().role = emerge(closest.cog());
            if (npc.following() == null && ThreadLocalRandom.current().nextInt(80) == 0
                    && "follower".equals(c.role)) {
                npc.setFollowing(closest.id());
                npc.setFollowLeft(60 + ThreadLocalRandom.current().nextInt(80));
            }
        }
    }

    private static String emerge(Cognition c) {
        if (c.drives.fear > 0.62 && c.traits.confidence < 0.4) {
            return "dependent";
        }
        if (c.traits.empathy > 0.72) {
            return "medic";
        }
        if (c.traits.curiosity > 0.55 && c.traits.paranoia < 0.6) {
            return "scout";
        }
        if (c.traits.aggression > 0.48 && c.traits.loyalty > 0.4) {
            return "protector";
        }
        if (c.traits.confidence > 0.58 && c.traits.sociability > 0.4) {
            return "leader";
        }
        return "follower";
    }
}
