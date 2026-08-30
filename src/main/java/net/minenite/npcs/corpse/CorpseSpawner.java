package net.minenite.npcs.corpse;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.civilian.CivilianNpc;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * Sleeping player-skinned body plus a loot chest — same idea as WarZ corpses.
 */
public final class CorpseSpawner {
    private final NpcsPlugin plugin;
    private final CorpseOpen opens;
    private final NamespacedKey key;

    public CorpseSpawner(NpcsPlugin plugin, CorpseOpen opens) {
        this.plugin = plugin;
        this.opens = opens;
        this.key = new NamespacedKey(plugin, "npc_corpse");
    }

    public void spawn(CivilianNpc npc, Location at, PlayerProfile profile) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        Location spawnAt = at.clone();
        spawnAt.setY(Math.floor(spawnAt.getY()) + 0.01);
        spawnAt.setPitch(0f);
        UUID id = UUID.randomUUID();
        Component title = Component.text(npc.name() + "'s Corpse", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false);

        Mannequin body = world.spawn(spawnAt, Mannequin.class, mannequin -> {
            mannequin.setPersistent(true);
            mannequin.setRemoveWhenFarAway(false);
            mannequin.setInvulnerable(true);
            mannequin.setSilent(true);
            mannequin.setGravity(false);
            mannequin.setAI(false);
            mannequin.setCollidable(false);
            mannequin.setImmovable(true);
            mannequin.customName(null);
            mannequin.setCustomNameVisible(false);
            mannequin.setDescription(null);
            try {
                if (profile != null) {
                    mannequin.setProfile(ResolvableProfile.resolvableProfile(profile));
                }
            } catch (Exception ignored) {
            }
            EntityEquipment equipment = mannequin.getEquipment();
            if (equipment != null) {
                equipment.setItemInMainHand(npc.gun());
                equipment.setItemInOffHand(npc.offhand());
            }
            mannequin.getPersistentDataContainer().set(key, PersistentDataType.STRING, id.toString());
        });
        body.setRotation(spawnAt.getYaw(), 0f);
        body.setVelocity(new Vector(0, 0, 0));
        if (Mannequin.validPoses().contains(Pose.SLEEPING)) {
            body.setPose(Pose.SLEEPING);
        }

        Location torso = spawnAt.clone().add(0, 0.55, 0);
        world.spawn(torso.clone().add(0, 0.15, 0), Interaction.class, click -> {
            click.setPersistent(true);
            click.setResponsive(true);
            click.setInteractionWidth(2.6f);
            click.setInteractionHeight(0.95f);
            click.getPersistentDataContainer().set(key, PersistentDataType.STRING, id.toString());
        });
        world.spawn(torso.clone().add(0, 0.45, 0), TextDisplay.class, label -> {
            label.text(title);
            label.setBillboard(Display.Billboard.CENTER);
            label.setSeeThrough(false);
            label.setShadowed(true);
            label.setDefaultBackground(true);
            label.setAlignment(TextDisplay.TextAlignment.CENTER);
            label.setViewRange(0.35f);
            label.setPersistent(true);
            label.setGravity(false);
            label.getPersistentDataContainer().set(key, PersistentDataType.STRING, id.toString());
        });

        Inventory chest = plugin.getServer().createInventory(null, 54, title);
        if (npc.gun() != null) {
            chest.setItem(50, npc.gun().clone());
        }
        if (npc.offhand() != null) {
            chest.setItem(49, npc.offhand().clone());
        }
        int slot = 0;
        for (ItemStack stack : npc.loot()) {
            while (slot < 45 && chest.getItem(slot) != null) {
                slot++;
            }
            if (slot >= 45) {
                world.dropItemNaturally(spawnAt, stack);
            } else {
                chest.setItem(slot++, stack);
            }
        }
        opens.track(id, chest, body.getUniqueId());
        long life = plugin.getConfig().getInt("corpses.lifetime-seconds", 180) * 20L;
        plugin.getServer().getScheduler().runTaskLater(plugin, body::remove, life);
    }
}
