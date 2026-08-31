package net.minenite.npcs.civilian;

import net.minenite.npcs.NpcsPlugin;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * CardForge {@code setItemSlot} on a mannequin often never emits
 * {@code ClientboundSetEquipmentPacket}. Paper's {@code sendEquipmentChange}
 * writes that packet ourselves so clients actually see the pistol.
 */
public final class EquipmentPackets {
    private final NpcsPlugin plugin;
    private boolean warned;

    public EquipmentPackets(NpcsPlugin plugin) {
        this.plugin = plugin;
    }

    public void hands(LivingEntity body, ItemStack main, ItemStack off) {
        if (body == null) {
            return;
        }
        ItemStack mainHand = cloneOrAir(main);
        ItemStack offHand = cloneOrAir(off);
        EntityEquipment eq = body.getEquipment();
        if (eq != null) {
            eq.setItem(EquipmentSlot.HAND, mainHand);
            eq.setItem(EquipmentSlot.OFF_HAND, offHand);
        }
        Map<EquipmentSlot, ItemStack> slots = Map.of(
                EquipmentSlot.HAND, mainHand,
                EquipmentSlot.OFF_HAND, offHand);
        for (Player viewer : body.getWorld().getPlayers()) {
            if (NpcBodies.realPlayer(viewer)
                    && viewer.getLocation().distanceSquared(body.getLocation()) < 96 * 96) {
                send(viewer, body, slots);
            }
        }
    }

    public void syncViewer(Player viewer, Iterable<CivilianNpc> npcs) {
        if (!NpcBodies.realPlayer(viewer)) {
            return;
        }
        for (CivilianNpc npc : npcs) {
            LivingEntity body = NpcBodies.living(npc);
            if (body == null || !body.isValid() || body.getWorld() != viewer.getWorld()) {
                continue;
            }
            ItemStack gun = held(body, npc);
            send(viewer, body, Map.of(
                    EquipmentSlot.HAND, cloneOrAir(gun),
                    EquipmentSlot.OFF_HAND, cloneOrAir(null)));
        }
    }

    private void send(Player viewer, LivingEntity body, Map<EquipmentSlot, ItemStack> slots) {
        try {
            viewer.sendEquipmentChange(body, slots);
        } catch (Exception failed) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Equipment packet failed: " + failed.getClass().getSimpleName()
                        + ": " + failed.getMessage());
            }
        }
    }

    private static ItemStack held(LivingEntity body, CivilianNpc npc) {
        EntityEquipment eq = body.getEquipment();
        if (eq != null && eq.getItemInMainHand() != null && !eq.getItemInMainHand().getType().isAir()) {
            return eq.getItemInMainHand();
        }
        return npc.gun();
    }

    private static ItemStack cloneOrAir(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return new ItemStack(Material.AIR);
        }
        return stack.clone();
    }
}
