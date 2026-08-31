package net.minenite.npcs.cognition;

import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.civilian.NpcBodies;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SoundHook implements Listener {
    private final SoundWorld world;
    private final Map<UUID, Long> lastStep = new ConcurrentHashMap<>();

    public SoundHook(NpcsPlugin ignored, SoundWorld world) {
        this.world = world;
    }

    public SoundWorld world() {
        return world;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!NpcBodies.realPlayer(player) || event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = lastStep.get(player.getUniqueId());
        if (prev != null && now - prev < 280) {
            return;
        }
        lastStep.put(player.getUniqueId(), now);
        boolean sprint = player.isSprinting();
        if (sprint || player.isSwimming()) {
            world.emit(event.getTo(), SoundWorld.Kind.SPRINT, 0.7, player.getUniqueId());
        } else {
            world.emit(event.getTo(), SoundWorld.Kind.FOOT, 0.28, player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        world.emit(event.getEntity().getLocation(), SoundWorld.Kind.FIGHT, 0.7,
                event.getDamager().getUniqueId());
        if (event.getDamager() instanceof Player player && player.getScoreboardTags().contains("pgm_fire")) {
            world.emit(event.getDamager().getLocation(), SoundWorld.Kind.GUN, 1.0, player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (type.name().contains("GLASS")) {
            world.emit(event.getBlock().getLocation(), SoundWorld.Kind.GLASS, 0.85, event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        String n = event.getClickedBlock().getType().name();
        if (n.contains("DOOR") || n.contains("GATE")) {
            world.emit(event.getClickedBlock().getLocation(), SoundWorld.Kind.DOOR, 0.45,
                    event.getPlayer().getUniqueId());
            event.getClickedBlock().getWorld().playSound(event.getClickedBlock().getLocation(),
                    Sound.BLOCK_WOODEN_DOOR_OPEN, 0.3f, 1f);
        }
    }
}
