package net.minenite.npcs.mind;

import net.minenite.npcs.civilian.CivilianNpc;
import net.minenite.npcs.civilian.NpcBodies;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a person can actually notice from where they are standing.
 */
public final class EnvironmentSense {
    public record Snap(
            String clock,
            String weather,
            boolean indoors,
            boolean roof,
            boolean wet,
            boolean dark,
            boolean corpse,
            String ground,
            String people,
            String civilians,
            String gunInHand
    ) {
        public String plain() {
            List<String> bits = new ArrayList<>();
            bits.add(clock);
            bits.add(weather);
            bits.add(indoors ? "under a roof" : "out in the open");
            if (wet) {
                bits.add("wet");
            }
            if (dark) {
                bits.add("poor light");
            }
            bits.add("ground: " + ground);
            if (corpse) {
                bits.add("a body is on the ground nearby");
            }
            if (!people.isBlank()) {
                bits.add(people);
            }
            if (!civilians.isBlank()) {
                bits.add(civilians);
            }
            if (!gunInHand.isBlank()) {
                bits.add("I am holding my " + gunInHand);
            }
            return String.join("; ", bits);
        }
    }

    private EnvironmentSense() {
    }

    public static Snap read(LivingEntity body, CivilianNpc self, Iterable<CivilianNpc> others) {
        Location at = body.getLocation();
        World world = at.getWorld();
        if (world == null) {
            return new Snap("day", "still", false, false, false, false, false, "dirt", "", "", gunName(self));
        }
        long time = world.getTime();
        String clock = clock(time);
        boolean storm = world.hasStorm();
        boolean thunder = world.isThundering();
        String weather = thunder ? "thunder" : storm ? "rain" : "clear";
        boolean roof = roofed(at);
        boolean dark = at.getBlock().getLightLevel() < 8 || time > 13000 && time < 23000;
        boolean corpse = corpseNear(body);
        String ground = groundName(at.clone().subtract(0, 1, 0).getBlock().getType());
        return new Snap(clock, weather, roof, roof, storm, dark, corpse, ground,
                people(body), civilians(body, self, others), gunName(self));
    }

    public static boolean roofed(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return false;
        }
        int x = at.getBlockX();
        int z = at.getBlockZ();
        int y = at.getBlockY();
        for (int dy = 2; dy <= 6; dy++) {
            Material type = world.getBlockAt(x, y + dy, z).getType();
            if (type.isSolid() && type != Material.BARRIER) {
                return true;
            }
        }
        return false;
    }

    public static Location shelterNear(Location here) {
        World world = here.getWorld();
        if (world == null) {
            return null;
        }
        for (int r = 3; r <= 14; r += 2) {
            for (int i = 0; i < 12; i++) {
                double a = (Math.PI * 2 * i) / 12.0;
                Location raw = here.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                if (roofed(raw) && raw.getBlock().getType().isAir()) {
                    raw.setY(here.getY());
                    return raw;
                }
            }
        }
        return null;
    }

    public static boolean corpseNear(LivingEntity body) {
        for (Entity entity : body.getNearbyEntities(9, 3, 9)) {
            if (entity instanceof Mannequin mannequin && mannequin.getPose() == Pose.SLEEPING) {
                return true;
            }
        }
        return false;
    }

    private static String people(LivingEntity body) {
        List<String> bits = new ArrayList<>();
        for (Player player : body.getWorld().getPlayers()) {
            if (!NpcBodies.realPlayer(player)) {
                continue;
            }
            double d = player.getLocation().distanceSquared(body.getLocation());
            if (d > 22 * 22) {
                continue;
            }
            String gun = held(player.getInventory().getItemInMainHand());
            boolean aim = player.getScoreboardTags().contains("pgm_aim");
            String extra = gun == null ? "empty hands" : (aim ? "aiming a " + gun : "holding a " + gun);
            bits.add(player.getName() + " " + extra + " ~" + (int) Math.sqrt(d) + "m");
        }
        return bits.isEmpty() ? "" : "people: " + String.join(", ", bits);
    }

    private static String civilians(LivingEntity body, CivilianNpc self, Iterable<CivilianNpc> others) {
        if (others == null) {
            return "";
        }
        List<String> bits = new ArrayList<>();
        for (CivilianNpc npc : others) {
            if (npc == self || !npc.alive()) {
                continue;
            }
            LivingEntity other = npc.body();
            if (other == null || other.getWorld() != body.getWorld()) {
                continue;
            }
            double d = other.getLocation().distanceSquared(body.getLocation());
            if (d > 16 * 16) {
                continue;
            }
            bits.add(npc.name() + " [" + npc.state().name().toLowerCase(Locale.ROOT) + "]");
        }
        return bits.isEmpty() ? "" : "other civilians: " + String.join(", ", bits);
    }

    private static String clock(long time) {
        if (time < 1000 || time >= 23000) {
            return "dawn";
        }
        if (time < 11000) {
            return "day";
        }
        if (time < 13000) {
            return "dusk";
        }
        return "night";
    }

    private static String groundName(Material type) {
        String name = type.name().toLowerCase(Locale.ROOT);
        if (name.contains("grass") || name.contains("dirt") || name.contains("podzol")) {
            return "dirt";
        }
        if (name.contains("sand")) {
            return "sand";
        }
        if (name.contains("stone") || name.contains("cobble") || name.contains("brick")
                || name.contains("concrete")) {
            return "broken pavement";
        }
        if (name.contains("wood") || name.contains("plank") || name.contains("log")) {
            return "boards";
        }
        if (name.contains("gravel") || name.contains("road")) {
            return "gravel";
        }
        if (name.contains("snow") || name.contains("ice")) {
            return "cold ground";
        }
        return name.replace('_', ' ');
    }

    private static String gunName(CivilianNpc npc) {
        if (npc == null || npc.gun() == null) {
            return "pistol";
        }
        return held(npc.gun()) == null ? "pistol" : held(npc.gun());
    }

    private static String held(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            String raw = String.valueOf(stack.getItemMeta().displayName());
            raw = raw.replaceAll(".*content=\"", "").replaceAll("\".*", "");
            if (!raw.isBlank() && raw.length() < 28) {
                return raw;
            }
        }
        return stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
