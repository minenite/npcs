package net.minenite.npcs.cognition;

import net.minenite.npcs.civilian.CivilianNpc;
import net.minenite.npcs.civilian.WanderEngine;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Cheap high-level existence when no player is nearby.
 * Body is teleported along intent so instantiation matches what happened.
 */
public final class Offscreen {
    private Offscreen() {
    }

    public static void tick(CivilianNpc npc, LivingEntity body) {
        Cognition c = npc.cog();
        if (++c.offscreenClock % 20 != 0) {
            return;
        }
        if (c.plan.expired()) {
            c.plan.failAfter = System.currentTimeMillis() + 90_000L;
            c.plan.step = 0;
        }
        Location dest = dest(npc, body);
        if (dest == null || dest.getWorld() != body.getWorld()) {
            rummage(c);
            return;
        }
        Vector d = dest.toVector().subtract(body.getLocation().toVector());
        d.setY(0);
        if (d.lengthSquared() < 9) {
            if (c.intention == Intention.RETURN_HOME || c.intention == Intention.SEEK_SHELTER
                    || c.intention == Intention.REST) {
                c.drives.safety = DriveSet.clamp(c.drives.safety + 0.05);
                c.drives.fatigue = DriveSet.clamp(c.drives.fatigue - 0.04);
            }
            rummage(c);
            return;
        }
        d.normalize().multiply(2.4 + c.traits.pace * 1.4);
        Location next = body.getLocation().clone().add(d);
        Location grounded = WanderEngine.ground(next);
        if (grounded != null) {
            body.teleport(grounded);
        }
        rummage(c);
    }

    private static Location dest(CivilianNpc npc, LivingEntity body) {
        Cognition c = npc.cog();
        return switch (c.intention) {
            case RETURN_HOME, REST, SEEK_SHELTER ->
                    new Location(body.getWorld(), c.life.homeX, body.getY(), c.life.homeZ);
            case LOOK_FOR_FRIEND, LOOK_FOR_RESOURCE, SEARCH, TRAVEL, PATROL -> {
                double ox = (ThreadLocalRandom.current().nextDouble() - 0.5) * 18;
                double oz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 18;
                yield body.getLocation().clone().add(ox, 0, oz);
            }
            default -> null;
        };
    }

    private static void rummage(Cognition c) {
        if (ThreadLocalRandom.current().nextInt(18) != 0) {
            return;
        }
        c.drives.hunger = DriveSet.clamp(c.drives.hunger - 0.04);
        c.drives.thirst = DriveSet.clamp(c.drives.thirst - 0.03);
        if (ThreadLocalRandom.current().nextBoolean() && !c.life.recent.isBlank()) {
            c.expect(c.life.friendName.isBlank() ? "the usual place is quiet" : c.life.friendName + " should be around",
                    120_000L);
        }
    }
}
