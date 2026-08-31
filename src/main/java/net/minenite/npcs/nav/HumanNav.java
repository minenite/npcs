package net.minenite.npcs.nav;

import net.minenite.npcs.cognition.Cognition;
import net.minenite.npcs.civilian.CivilianNpc;
import net.minenite.npcs.civilian.HumanMotor;
import net.minenite.npcs.civilian.WanderEngine;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Humanized ground navigation: A* on walkable blocks, imperfect follow,
 * turn pauses, look-then-walk. Not vanilla mob pathing.
 */
public final class HumanNav {
    public static final class Route {
        public final List<Location> points = new ArrayList<>();
        public int i;
        public int turnPause;
        public String blockedKey;
    }

    private HumanNav() {
    }

    public static Route to(Location from, Location goal, Cognition cog) {
        if (from == null || goal == null || from.getWorld() != goal.getWorld()) {
            return null;
        }
        List<int[]> cells = astar(from, goal, cog);
        if (cells == null || cells.isEmpty()) {
            cog.pathFailStreak++;
            return null;
        }
        cog.pathFailStreak = 0;
        Route route = new Route();
        World world = from.getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int[] c : cells) {
            double ox = (rng.nextDouble() - 0.5) * 0.55;
            double oz = (rng.nextDouble() - 0.5) * 0.55;
            Location p = new Location(world, c[0] + 0.5 + ox, c[1], c[2] + 0.5 + oz);
            Double y = WanderEngine.floorY(p);
            if (y != null) {
                p.setY(y);
            }
            route.points.add(p);
        }
        return route;
    }

    public static boolean follow(LivingEntity body, CivilianNpc npc, Route route, double speed) {
        if (route == null || route.points.isEmpty()) {
            return true;
        }
        if (route.turnPause > 0) {
            route.turnPause--;
            HumanMotor.plant(body);
            if (!route.points.isEmpty() && route.i < route.points.size()) {
                npc.lookToward(body.getEyeLocation(), route.points.get(route.i).clone().add(0, 1.5, 0),
                        0.22f, 0.08f);
            }
            return false;
        }
        if (route.i >= route.points.size()) {
            HumanMotor.plant(body);
            return true;
        }
        Location next = route.points.get(route.i);
        Location here = body.getLocation();
        Vector delta = next.toVector().subtract(here.toVector());
        float need = (float) Math.toDegrees(Math.atan2(-delta.getX(), delta.getZ()));
        if (Math.abs(wrap(need - npc.bodyYaw())) > 55f && route.i > 0) {
            npc.beginTurn(need, 6 + (int) (Math.abs(wrap(need - npc.bodyYaw())) / 20f));
            route.turnPause = 5 + ThreadLocalRandom.current().nextInt(5);
            return false;
        }
        if (ThreadLocalRandom.current().nextInt(22) == 0) {
            npc.idleGlance();
        } else {
            npc.lookToward(body.getEyeLocation(), next.clone().add(0, 1.4, 0), 0.16f, 0.10f);
        }
        boolean arrived = HumanMotor.walkToward(body, npc, next, speed * npc.cog().traits.pace
                * (1.0 - npc.cog().drives.fatigue * 0.25), false);
        if (arrived || here.distanceSquared(next) < 0.7) {
            route.i++;
        } else if (npc.stuck(here) && route.i > 0) {
            Location prev = route.points.get(Math.max(0, route.i - 1));
            npc.cog().blockEdge(prev.getBlockX() + "," + prev.getBlockZ() + ">"
                    + next.getBlockX() + "," + next.getBlockZ());
            npc.clearStuck();
            route.i++;
        }
        return route.i >= route.points.size();
    }

    private static List<int[]> astar(Location from, Location goal, Cognition cog) {
        World world = from.getWorld();
        int sx = from.getBlockX(), sy = from.getBlockY(), sz = from.getBlockZ();
        int gx = goal.getBlockX(), gy = goal.getBlockY(), gz = goal.getBlockZ();
        record N(int x, int y, int z) {
        }
        PriorityQueue<N> open = new PriorityQueue<>(Comparator.comparingDouble(n ->
                hypot(n.x - gx, n.z - gz) + hypot(n.x - sx, n.z - sz) * 0.15));
        Map<N, N> came = new HashMap<>();
        Map<N, Double> gScore = new HashMap<>();
        Set<N> seen = new HashSet<>();
        N start = new N(sx, sy, sz);
        open.add(start);
        gScore.put(start, 0.0);
        int guard = 0;
        N found = null;
        while (!open.isEmpty() && guard++ < 280) {
            N cur = open.poll();
            if (!seen.add(cur)) {
                continue;
            }
            if (Math.abs(cur.x - gx) + Math.abs(cur.z - gz) <= 1 && Math.abs(cur.y - gy) <= 2) {
                found = cur;
                break;
            }
            for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 1, 0}, {-1, 1, 0},
                    {0, 1, 1}, {0, 1, -1}, {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}}) {
                N n = new N(cur.x + d[0], cur.y + d[1], cur.z + d[2]);
                if (!walk(world, n.x, n.y, n.z)) {
                    continue;
                }
                String edge = cur.x + "," + cur.z + ">" + n.x + "," + n.z;
                if (cog != null && blocked(cog, edge)) {
                    continue;
                }
                double step = 1.0 + (Math.abs(d[1]) * 0.35);
                Material floor = world.getBlockAt(n.x, n.y - 1, n.z).getType();
                String fn = floor.name();
                if (fn.contains("GRAVEL") || fn.contains("PATH") || fn.contains("CONCRETE") || fn.contains("STONE")) {
                    step *= 0.86;
                }
                if (fn.contains("LEAVES") || fn.contains("SNOW")) {
                    step *= 1.15;
                }
                double ng = gScore.getOrDefault(cur, 1e9) + step;
                if (ng < gScore.getOrDefault(n, 1e9)) {
                    came.put(n, cur);
                    gScore.put(n, ng);
                    open.add(n);
                }
            }
        }
        if (found == null) {
            return null;
        }
        ArrayDeque<int[]> path = new ArrayDeque<>();
        for (N at = found; at != null; at = came.get(at)) {
            path.addFirst(new int[]{at.x, at.y, at.z});
            if (at.equals(start)) {
                break;
            }
        }
        return new ArrayList<>(path);
    }

    private static boolean walk(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block floor = world.getBlockAt(x, y - 1, z);
        if (!floor.getType().isSolid() || floor.getType() == Material.LAVA) {
            return false;
        }
        return airish(feet.getType()) && airish(head.getType());
    }

    private static boolean airish(Material m) {
        return !m.isSolid() || m.name().contains("DOOR") || m.name().contains("GATE")
                || m.name().contains("GRASS") || m == Material.SNOW;
    }

    private static boolean blocked(Cognition cog, String edge) {
        return cog != null && cog.edgeBlocked(edge);
    }

    private static double hypot(int a, int b) {
        return Math.hypot(a, b);
    }

    private static float wrap(float yaw) {
        while (yaw > 180) {
            yaw -= 360;
        }
        while (yaw < -180) {
            yaw += 360;
        }
        return yaw;
    }
}
