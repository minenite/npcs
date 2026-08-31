package net.minenite.npcs.cognition;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Semantic places from what is actually around, plus coordinates.
 */
public final class Places {
    private static final Map<Long, Cached> PLACE_CACHE = new ConcurrentHashMap<>();

    private record Cached(String label, long at) {
    }

    private Places() {
    }

    public static String at(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "somewhere";
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        long key = (((long) x >> 2) << 32) ^ (z >> 2);
        Cached hit = PLACE_CACHE.get(key);
        long now = System.currentTimeMillis();
        if (hit != null && now - hit.at < 8_000L) {
            return hit.label;
        }
        if (PLACE_CACHE.size() > 64) {
            PLACE_CACHE.clear();
        }
        World world = loc.getWorld();
        int wood = 0, stone = 0, glass = 0, road = 0, tree = 0, chest = 0, bed = 0, brew = 0, iron = 0;
        boolean roof = false;
        for (int dx = -6; dx <= 6; dx += 2) {
            for (int dz = -6; dz <= 6; dz += 2) {
                for (int dy = 0; dy <= 3; dy += 1) {
                    Material m = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    String n = m.name();
                    if (n.contains("PLANKS") || n.contains("LOG") || n.contains("WOOD")) {
                        wood++;
                    }
                    if (n.contains("STONE") || n.contains("BRICK") || n.contains("COBBLE") || n.contains("CONCRETE")) {
                        stone++;
                    }
                    if (n.contains("GLASS")) {
                        glass++;
                    }
                    if (n.contains("GRAVEL") || n.contains("DIRT_PATH") || n.contains("COARSE") || n.contains("ASPHALT")) {
                        road++;
                    }
                    if (n.contains("LEAVES") || n.contains("LOG")) {
                        tree++;
                    }
                    if (n.contains("CHEST") || n.contains("BARREL")) {
                        chest++;
                    }
                    if (n.contains("BED")) {
                        bed++;
                    }
                    if (n.contains("BREWING") || n.contains("CAULDRON")) {
                        brew++;
                    }
                    if (n.contains("IRON") || n.contains("ANVIL")) {
                        iron++;
                    }
                    if (dy >= 2 && m.isSolid()) {
                        roof = true;
                    }
                }
            }
        }
        String name;
        if (brew > 0) {
            name = "the pharmacy";
        } else if (bed > 1) {
            name = "the apartments";
        } else if (iron > 4 && stone > 20) {
            name = "the warehouse";
        } else if (chest > 2) {
            name = "the market";
        } else if (wood > 25 && stone < 10) {
            name = "a wooden house";
        } else if (roof && stone > 15) {
            name = "a building";
        } else if (tree > 40) {
            name = "the tree line";
        } else if (road > 12) {
            name = "the road";
        } else if (glass > 8) {
            name = "the shopfront";
        } else {
            name = "open ground";
        }
        String label = name + " (" + x + ", " + z + ")";
        PLACE_CACHE.put(key, new Cached(label, now));
        return label;
    }

    /** Cheap: is there a solid next to me? No ray traces. */
    public static double coverHint(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return 0.1;
        }
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        int solids = 0;
        for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
            if (world.getBlockAt(x + d[0], y, z + d[1]).getType().isSolid()) {
                solids++;
            }
        }
        return solids >= 2 ? 0.5 : solids == 1 ? 0.25 : 0.1;
    }

    public static String afford(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "open";
        }
        if (indoors(loc)) {
            return "shelter";
        }
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                String n = world.getBlockAt(x + dx, y, z + dz).getType().name();
                if (n.contains("DOOR")) {
                    return "doorway";
                }
                if (n.contains("GLASS") || n.contains("WINDOW")) {
                    return "window";
                }
            }
        }
        String n = world.getBlockAt(x, y - 1, z).getType().name();
        if (n.contains("GRAVEL") || n.contains("PATH") || n.contains("CONCRETE")) {
            return "road";
        }
        if (n.contains("LEAVES") || world.getBlockAt(x, y + 3, z).getType().name().contains("LEAVES")) {
            return "forest";
        }
        return "open";
    }

    public static boolean indoors(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        for (int dy = 2; dy <= 6; dy++) {
            if (world.getBlockAt(x, y + dy, z).getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    public static Location coverFrom(Location here, Location threat) {
        if (here == null || here.getWorld() == null) {
            return null;
        }
        World world = here.getWorld();
        Location best = null;
        double bestScore = -1e9;
        int[][] dirs = {{4, 0}, {-4, 0}, {0, 4}, {0, -4}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3},
                {6, 0}, {-6, 0}, {0, 6}, {0, -6}};
        for (int[] d : dirs) {
            Location cand = here.clone().add(d[0], 0, d[1]);
            if (world.getBlockAt(cand.getBlockX(), cand.getBlockY(), cand.getBlockZ()).getType().isSolid()) {
                continue;
            }
            boolean blocked = false;
            if (threat != null && threat.getWorld() == world) {
                Vector to = threat.toVector().subtract(cand.toVector());
                double dist = to.length();
                if (dist > 0.4) {
                    var hit = world.rayTraceBlocks(cand.clone().add(0, 1.4, 0),
                            to.multiply(1.0 / dist), Math.min(dist, 18),
                            org.bukkit.FluidCollisionMode.NEVER, true);
                    blocked = hit != null;
                }
            } else if (world.getBlockAt(cand.getBlockX() + Integer.signum(d[0]), cand.getBlockY(),
                    cand.getBlockZ() + Integer.signum(d[1])).getType().isSolid()) {
                blocked = true;
            }
            int open = 0;
            for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (!world.getBlockAt(cand.getBlockX() + n[0], cand.getBlockY(), cand.getBlockZ() + n[1])
                        .getType().isSolid()) {
                    open++;
                }
            }
            if (open <= 1) {
                continue;
            }
            double distThreat = threat == null ? 4 : cand.distance(threat);
            double score = (blocked ? 4 : 0) + distThreat * 0.15 + open * 0.4
                    - cand.distance(here) * 0.2;
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return best;
    }
}
