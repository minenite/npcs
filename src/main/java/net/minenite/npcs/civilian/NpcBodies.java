package net.minenite.npcs.civilian;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class NpcBodies {
    private NpcBodies() {}

    public static NamespacedKey key() {
        return new NamespacedKey("npcs", "civilian");
    }

    public static boolean isNpc(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.getScoreboardTags().contains("minenite_npc")) {
            return true;
        }
        return entity.getPersistentDataContainer().has(key(), PersistentDataType.STRING);
    }

    public static UUID npcId(Entity entity) {
        if (entity == null) {
            return null;
        }
        String raw = entity.getPersistentDataContainer().get(key(), PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static LivingEntity living(CivilianNpc npc) {
        if (npc == null || npc.entityId() == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(npc.entityId());
        return entity instanceof LivingEntity living ? living : null;
    }

    public static boolean realPlayer(Player player) {
        return player != null && player.isOnline() && !isNpc(player);
    }
}
