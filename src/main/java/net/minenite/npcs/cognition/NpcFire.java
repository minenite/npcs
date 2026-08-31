package net.minenite.npcs.cognition;

import net.minenite.npcs.civilian.CivilianNpc;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Extreme-only fire. WarZ guns stay authoritative for players;
 * civilians use a deterministic ray + sound so mannequins can shoot.
 */
public final class NpcFire {
    private NpcFire() {
    }

    public enum Choice {
        FREEZE, BACK, THREATEN, DRAW, RUN, COVER, CALL, WARN_SHOT, SHOOT
    }

    public static Choice choose(Cognition cog, double aimedAt, boolean hasLos) {
        DriveSet d = cog.drives;
        Traits t = cog.traits;
        if (aimedAt < 0.4) {
            return Choice.FREEZE;
        }
        double shoot = d.desperation * 0.3 + d.aggression * 0.35 + d.pain * 0.25
                - t.empathy * 0.4 - d.fear * 0.15 + t.riskTolerance * 0.2;
        double run = d.fear * 0.5 - t.aggression * 0.2 + (1.0 - t.riskTolerance) * 0.2;
        double freeze = (1.0 - t.reaction) * 0.4 + d.fear * 0.2 - d.confidence * 0.2;
        if (shoot > 0.72 && hasLos && d.urgency > 0.55 && cog.fireCool <= 0) {
            return Choice.SHOOT;
        }
        if (shoot > 0.55 && hasLos) {
            return Choice.WARN_SHOT;
        }
        if (run > shoot && run > freeze) {
            return Choice.RUN;
        }
        if (d.fear > 0.7 && t.aggression < 0.35) {
            return Choice.BACK;
        }
        if (t.aggression > 0.45) {
            return Choice.THREATEN;
        }
        if (freeze > 0.45) {
            return Choice.FREEZE;
        }
        return Choice.DRAW;
    }

    public static boolean fire(LivingEntity body, CivilianNpc npc, Location toward, boolean warnOnly) {
        World world = body.getWorld();
        Location eye = body.getEyeLocation();
        Vector dir = toward.toVector().subtract(eye.toVector());
        if (dir.lengthSquared() < 1e-6) {
            return false;
        }
        dir.normalize();
        double miss = (1.0 - npc.cog().traits.aimSteady) * 0.12 + npc.cog().drives.stress * 0.08
                + npc.cog().drives.fear * 0.06;
        dir.setX(dir.getX() + (ThreadLocalRandom.current().nextDouble() - 0.5) * miss);
        dir.setY(dir.getY() + (ThreadLocalRandom.current().nextDouble() - 0.5) * miss * 0.6);
        dir.setZ(dir.getZ() + (ThreadLocalRandom.current().nextDouble() - 0.5) * miss);
        dir.normalize();
        world.playSound(eye, warnOnly ? Sound.ENTITY_FIREWORK_ROCKET_BLAST : Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.8f);
        world.spawnParticle(Particle.SMOKE, eye.clone().add(dir), 4, 0.05, 0.05, 0.05, 0.01);
        body.addScoreboardTag("pgm_fire");
        npc.cog().fireCool = warnOnly ? 35 : 18;
        if (warnOnly) {
            return false;
        }
        RayTraceResult hit = world.rayTrace(eye, dir, 48, FluidCollisionMode.NEVER, true, 0.15,
                e -> e instanceof LivingEntity living && living != body);
        if (hit != null && hit.getHitEntity() instanceof LivingEntity living) {
            living.damage(5.5 + ThreadLocalRandom.current().nextDouble() * 3.0, body);
            return true;
        }
        return false;
    }

    public static boolean los(LivingEntity from, Entity to) {
        if (to == null) {
            return false;
        }
        Location a = from.getEyeLocation();
        Location b = to instanceof LivingEntity living ? living.getEyeLocation() : to.getLocation();
        Vector dir = b.toVector().subtract(a.toVector());
        double dist = dir.length();
        if (dist < 0.2) {
            return true;
        }
        dir.normalize();
        RayTraceResult blocks = from.getWorld().rayTraceBlocks(a, dir, dist, FluidCollisionMode.NEVER, true);
        return blocks == null || blocks.getHitPosition().distanceSquared(a.toVector()) > dist * dist * 0.95;
    }
}
