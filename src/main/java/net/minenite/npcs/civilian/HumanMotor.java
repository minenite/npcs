package net.minenite.npcs.civilian;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Player-like locomotion: gravity, acceleration, step-up, and velocity so the
 * client gets real move packets (teleports zero walk-limb animation).
 */
public final class HumanMotor {
    private HumanMotor() {}

    public static void prepare(LivingEntity body) {
        body.setGravity(true);
        body.setAI(true);
        body.setCollidable(true);
        if (body instanceof Player player) {
            player.setSneaking(false);
            player.setFlying(false);
            player.setAllowFlight(false);
            player.setGliding(false);
        }
    }

    /**
     * @return true when they have arrived
     */
    public static boolean walkToward(LivingEntity body, CivilianNpc npc, Location dest,
                                     double speed, boolean backpedal) {
        prepare(body);
        Location here = body.getLocation();
        if (dest == null || dest.getWorld() != here.getWorld()) {
            plant(body);
            return true;
        }
        Vector delta = dest.toVector().subtract(here.toVector());
        double horiz = Math.hypot(delta.getX(), delta.getZ());
        if (horiz < 0.55) {
            plant(body);
            return true;
        }

        Vector wish = new Vector(delta.getX(), 0, delta.getZ()).normalize().multiply(speed);
        wish.add(sideSway(npc, wish, speed));
        if (stepUp(here, wish)) {
            wish.setY(0.42);
        } else {
            wish.setY(body.getVelocity().getY());
        }

        Vector cur = body.getVelocity();
        Vector next = new Vector(
                cur.getX() * 0.28 + wish.getX() * 0.72,
                wish.getY(),
                cur.getZ() * 0.28 + wish.getZ() * 0.72);
        cap(next, speed * 1.15);
        body.setVelocity(next);

        float moveYaw = yawOf(wish);
        if (backpedal) {
            npc.lookToward(body.getEyeLocation(), dest.clone().add(0, 1.4, 0), 0.08f, 0.04f);
            body.setRotation(moveYaw + 180f, npc.lookPitch());
        } else {
            float bodyRate = npc.state() == CivilianNpc.State.AIM || npc.state() == CivilianNpc.State.CIRCLE
                    ? 0.10f : 0.22f;
            npc.turnBody(moveYaw, bodyRate);
            if (npc.state() == CivilianNpc.State.WALK && ThreadLocalRandom.current().nextInt(18) == 0) {
                npc.idleGlance();
            }
            body.setRotation(npc.bodyYaw(), npc.lookPitch());
        }
        return false;
    }

    public static void plant(LivingEntity body) {
        Vector v = body.getVelocity();
        body.setVelocity(new Vector(v.getX() * 0.2, Math.min(v.getY(), 0), v.getZ() * 0.2));
    }

    public static void face(LivingEntity body, CivilianNpc npc) {
        body.setRotation(npc.lookYaw(), npc.lookPitch());
    }

    private static Vector sideSway(CivilianNpc npc, Vector wish, double speed) {
        if (wish.lengthSquared() < 1.0e-6) {
            return new Vector();
        }
        Vector side = new Vector(-wish.getZ(), 0, wish.getX());
        if (side.lengthSquared() < 1.0e-6) {
            return new Vector();
        }
        double wobble = Math.sin((npc.walkAge() + npc.name().hashCode()) * 0.13) * speed * 0.08;
        return side.normalize().multiply(wobble);
    }

    private static boolean stepUp(Location here, Vector wish) {
        Location ahead = here.clone().add(wish.clone().normalize().multiply(0.7));
        Block feet = ahead.getBlock();
        Block head = ahead.clone().add(0, 1, 0).getBlock();
        Block above = ahead.clone().add(0, 2, 0).getBlock();
        return solid(feet) && !solid(head) && !solid(above);
    }

    private static boolean solid(Block block) {
        Material type = block.getType();
        return type.isSolid() && type != Material.LAVA;
    }

    private static void cap(Vector vector, double max) {
        double horiz = Math.hypot(vector.getX(), vector.getZ());
        if (horiz <= max || horiz < 1.0e-6) {
            return;
        }
        double s = max / horiz;
        vector.setX(vector.getX() * s);
        vector.setZ(vector.getZ() * s);
    }

    private static float yawOf(Vector vector) {
        return (float) Math.toDegrees(Math.atan2(-vector.getX(), vector.getZ()));
    }
}
