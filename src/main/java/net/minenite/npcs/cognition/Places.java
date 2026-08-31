package net.minenite.npcs.cognition;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Semantic places from what is actually around, plus coordinates.
 */
public final class Places {
    private Places() {
    }

    public static String at(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "somewhere";
        }
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        int wood = 0, stone = 0, glass = 0, road = 0, tree = 0, chest = 0, bed = 0, brew = 0, iron = 0;
        boolean roof = false;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = -1; dy <= 4; dy++) {
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
        return name + " (" + x + ", " + z + ")";
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
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx * dx + dz * dz < 4) {
                    continue;
                }
                Location cand = here.clone().add(dx, 0, dz);
                Block wall = world.getBlockAt(cand.getBlockX(), cand.getBlockY(), cand.getBlockZ());
                Block feet = world.getBlockAt(cand.getBlockX(), cand.getBlockY(), cand.getBlockZ());
                if (feet.getType().isSolid()) {
                    continue;
                }
                boolean blocked = false;
                if (threat != null) {
                    var hit = world.rayTraceBlocks(cand.clone().add(0, 1.4, 0),
                            threat.toVector().subtract(cand.toVector()).normalize(),
                            threat.distance(cand), org.bukkit.FluidCollisionMode.NEVER, true);
                    blocked = hit != null;
                }
                int open = 0;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    if (!world.getBlockAt(cand.getBlockX() + d[0], cand.getBlockY(), cand.getBlockZ() + d[1])
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
                if (wall.getRelative(0, 0, 0).getType().isSolid()) {
                    continue;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = cand;
                }
            }
        }
        return best;
    }
}
