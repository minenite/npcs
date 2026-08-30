package net.minenite.npcs.civilian;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Human wandering: pick a nearby spot, curve toward it, pause, look around.
 * No pathfinder, no grid traces — if the ground or a wall disagrees, they
 * stop and choose again the way a person does.
 */
public final class WanderEngine {

    private WanderEngine() {}

    public static void plan(CivilianNpc npc, Location here, double min, double max, double speed) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        World world = here.getWorld();
        if (world == null) {
            return;
        }
        Location dest = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            double dist = min + rng.nextDouble() * Math.max(0.5, max - min);
            double yaw = rng.nextDouble() * Math.PI * 2.0;
            Location raw = here.clone().add(Math.cos(yaw) * dist, 0, Math.sin(yaw) * dist);
            Location grounded = ground(raw);
            if (grounded != null && walkable(here, grounded)) {
                dest = grounded;
                break;
            }
        }
        if (dest == null) {
            npc.setIdleLeft(40 + rng.nextInt(80));
            return;
        }
        Vector mid = dest.toVector().subtract(here.toVector()).multiply(0.5);
        Vector side = new Vector(-mid.getZ(), 0, mid.getX());
        if (side.lengthSquared() > 1.0e-4) {
            side.normalize().multiply((rng.nextDouble() - 0.5) * 1.4);
        }
        Location via = keepXZ(here.clone().add(mid).add(side));
        if (via == null) {
            via = here.clone().add(mid);
        }
        double length = Math.hypot(dest.getX() - here.getX(), dest.getZ() - here.getZ());
        int ticks = (int) Math.max(25, length / Math.max(0.08, speed));
        npc.beginWalk(here, via, dest, ticks);
    }

    /** Snap Y to the floor under this exact XZ — do not jump to block center. */
    public static Location keepXZ(Location at) {
        Double y = floorY(at);
        if (y == null) {
            return null;
        }
        Location loc = at.clone();
        loc.setY(y);
        return loc;
    }

    public static Double floorY(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        int x = at.getBlockX();
        int z = at.getBlockZ();
        int start = at.getBlockY();
        for (int y = start + 2; y >= start - 4; y--) {
            Block feet = world.getBlockAt(x, y, z);
            Block below = world.getBlockAt(x, y - 1, z);
            if (isFloor(below.getType()) && isAirish(feet.getType())
                    && isAirish(world.getBlockAt(x, y + 1, z).getType())) {
                return (double) y;
            }
        }
        return null;
    }

    public static Location ground(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        int x = at.getBlockX();
        int z = at.getBlockZ();
        int start = at.getBlockY();
        for (int y = start + 2; y >= start - 5; y--) {
            Block feet = world.getBlockAt(x, y, z);
            Block below = world.getBlockAt(x, y - 1, z);
            if (isFloor(below.getType()) && isAirish(feet.getType())
                    && isAirish(world.getBlockAt(x, y + 1, z).getType())) {
                Location loc = new Location(world, x + 0.5, y, z + 0.5);
                loc.setYaw(at.getYaw());
                return loc;
            }
        }
        return null;
    }

    public static boolean walkable(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || to.getWorld() != world) {
            return false;
        }
        if (Math.abs(to.getY() - from.getY()) > 2.2) {
            return false;
        }
        int steps = 4;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            Location sample = from.clone().add(
                    (to.getX() - from.getX()) * t,
                    (to.getY() - from.getY()) * t,
                    (to.getZ() - from.getZ()) * t);
            Block body = sample.getBlock();
            if (body.getType().isSolid() && !isFloor(body.getType())) {
                return false;
            }
            Material floor = world.getBlockAt(body.getX(), body.getY() - 1, body.getZ()).getType();
            if (floor == Material.LAVA || floor == Material.WATER || floor == Material.KELP) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFloor(Material material) {
        return material.isSolid() && material != Material.LAVA;
    }

    private static boolean isAirish(Material material) {
        return !material.isSolid() || material == Material.SHORT_GRASS
                || material == Material.TALL_GRASS || material == Material.SNOW;
    }
}
