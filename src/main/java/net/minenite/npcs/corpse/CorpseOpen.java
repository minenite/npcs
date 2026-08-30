package net.minenite.npcs.corpse;

import net.minenite.npcs.NpcsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Opens the loot chest when someone right-clicks a civilian corpse. */
public final class CorpseOpen implements Listener {
    private final NamespacedKey key;
    private final Map<UUID, Inventory> chests = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> bodyByCorpse = new ConcurrentHashMap<>();

    public CorpseOpen(NpcsPlugin plugin) {
        this.key = new NamespacedKey(plugin, "npc_corpse");
    }

    public void track(UUID corpseId, Inventory chest, UUID bodyId) {
        chests.put(corpseId, chest);
        bodyByCorpse.put(corpseId, bodyId);
    }

    @EventHandler
    public void onUse(PlayerInteractEntityEvent event) {
        UUID corpseId = idOf(event.getRightClicked());
        if (corpseId == null) {
            return;
        }
        Inventory chest = chests.get(corpseId);
        if (chest == null) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().openInventory(chest);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        UUID empty = null;
        for (Map.Entry<UUID, Inventory> entry : chests.entrySet()) {
            if (entry.getValue() == top && isEmpty(top)) {
                empty = entry.getKey();
                break;
            }
        }
        if (empty == null) {
            return;
        }
        remove(empty, event.getPlayer().getWorld());
    }

    private void remove(UUID corpseId, org.bukkit.World world) {
        UUID bodyId = bodyByCorpse.remove(corpseId);
        chests.remove(corpseId);
        if (bodyId != null) {
            Entity body = Bukkit.getEntity(bodyId);
            if (body != null) {
                body.remove();
            }
        }
        if (world == null) {
            return;
        }
        for (Entity entity : world.getEntities()) {
            if (corpseId.equals(idOf(entity))) {
                entity.remove();
            }
        }
    }

    private UUID idOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        String raw = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isEmpty(Inventory inventory) {
        for (var stack : inventory.getContents()) {
            if (stack != null && !stack.getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}
