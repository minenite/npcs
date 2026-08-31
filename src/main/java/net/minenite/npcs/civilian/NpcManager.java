package net.minenite.npcs.civilian;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.chat.LlmTalk;
import net.minenite.npcs.corpse.CorpseOpen;
import net.minenite.npcs.corpse.CorpseSpawner;
import net.minenite.npcs.skin.SkinService;
import net.minenite.npcs.tab.TabListPackets;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcManager implements Listener {
    private final NpcsPlugin plugin;
    private final SkinService skins;
    private final LlmTalk talk;
    private final TabListPackets tab;
    private final LoadoutService loadouts;
    private final CorpseSpawner corpses;
    private final GunPoseBridge poses;
    private final CivilianBrain brain;
    private final NamespacedKey npcKey;
    private final Map<UUID, CivilianNpc> byId = new ConcurrentHashMap<>();
    private BukkitTask tick;

    public NpcManager(NpcsPlugin plugin, SkinService skins, LlmTalk talk, TabListPackets tab) {
        this.plugin = plugin;
        this.skins = skins;
        this.talk = talk;
        this.tab = tab;
        this.loadouts = new LoadoutService(plugin);
        CorpseOpen opens = new CorpseOpen(plugin);
        this.corpses = new CorpseSpawner(plugin, opens);
        this.poses = new GunPoseBridge(plugin);
        this.brain = new CivilianBrain(plugin, talk, poses);
        this.npcKey = NpcBodies.key();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(opens, plugin);
    }

    public void start() {
        tick = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (tick != null) {
            tick.cancel();
        }
        poses.clearAll();
        poses.shutdown();
        tab.hideAll(List.copyOf(byId.values()));
        for (CivilianNpc npc : List.copyOf(byId.values())) {
            removeBody(npc, false);
        }
        byId.clear();
    }

    public CivilianNpc spawnCivilian(Location at) {
        Personality personality = Personality.random();
        String name = unique(CivilianNames.roll(personality));
        UUID id = UUID.nameUUIDFromBytes(("npc:" + name + ":" + System.nanoTime()).getBytes());
        LoadoutService.Kit kit = loadouts.roll();
        CivilianNpc npc = new CivilianNpc(id, name, personality, skins.textures(),
                kit.gun(), kit.spare(), extras(kit));
        PlayerProfile profile = skins.profileFor(id, name);
        Location spawnAt = WanderEngine.ground(at);
        if (spawnAt == null) {
            spawnAt = at.clone();
        }
        Location place = spawnAt;
        LivingEntity body = spawnMannequin(place, id, name, profile);
        npc.bind(body, false);
        plugin.getLogger().info("Civilian " + name + " spawned at "
                + place.getBlockX() + "," + place.getBlockY() + "," + place.getBlockZ());
        byId.put(id, npc);
        LivingEntity spawned = body;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!spawned.isValid()) {
                return;
            }
            putGun(spawned, kit);
            poses.set(spawned.getUniqueId(), true, false);
            if (!spawned.getUniqueId().equals(id)) {
                poses.set(id, true, false);
            }
            spawned.addScoreboardTag("pgm_gun");
        });
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!NpcBodies.isNpc(viewer)) {
                tab.show(viewer, npc);
            }
        }
        return npc;
    }

    private Mannequin spawnMannequin(Location place, UUID id, String name, PlayerProfile profile) {
        return place.getWorld().spawn(place, Mannequin.class, mannequin -> {
            mannequin.setPersistent(false);
            mannequin.setRemoveWhenFarAway(false);
            mannequin.setInvulnerable(false);
            mannequin.setSilent(false);
            mannequin.setGravity(true);
            mannequin.setAI(true);
            mannequin.setCollidable(true);
            mannequin.setImmovable(false);
            mannequin.customName(Component.text(name));
            mannequin.setCustomNameVisible(true);
            mannequin.setDescription(null);
            try {
                mannequin.setProfile(ResolvableProfile.resolvableProfile(profile));
            } catch (Exception ignored) {
            }
            try {
                var parts = mannequin.getSkinParts();
                parts.setCapeEnabled(true);
                parts.setHatsEnabled(true);
                parts.setJacketEnabled(true);
                parts.setLeftSleeveEnabled(true);
                parts.setRightSleeveEnabled(true);
                parts.setLeftPantsEnabled(true);
                parts.setRightPantsEnabled(true);
                mannequin.setSkinParts(parts);
            } catch (Exception ignored) {
            }
            try {
                mannequin.setPose(Pose.STANDING, false);
            } catch (Exception ignored) {
            }
            mannequin.setHealth(Math.min(mannequin.getMaxHealth(),
                    plugin.getConfig().getDouble("civilian.health", 16)));
            mark(mannequin, id);
        });
    }

    private void mark(LivingEntity body, UUID id) {
        body.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, id.toString());
        body.addScoreboardTag("minenite_npc");
        body.addScoreboardTag("pgm_gun");
        body.setPersistent(false);
        body.setRemoveWhenFarAway(false);
        body.setGravity(true);
        body.setAI(true);
    }

    private void putGun(LivingEntity body, LoadoutService.Kit kit) {
        if (kit.gun() == null) {
            return;
        }
        if (body instanceof Player player) {
            player.getInventory().setItemInMainHand(kit.gun().clone());
            player.getInventory().setItemInOffHand(null);
            player.updateInventory();
        }
        EntityEquipment equipment = body.getEquipment();
        if (equipment != null) {
            equipment.setItem(EquipmentSlot.HAND, kit.gun().clone());
            equipment.setItem(EquipmentSlot.OFF_HAND, null);
        }
    }

    public int removeNear(Location at, double range) {
        int n = 0;
        double rangeSq = range * range;
        for (CivilianNpc npc : List.copyOf(byId.values())) {
            LivingEntity body = NpcBodies.living(npc);
            if (body == null || body.getLocation().distanceSquared(at) > rangeSq) {
                continue;
            }
            removeBody(npc, true);
            n++;
        }
        return n;
    }

    public int removeAll() {
        int n = byId.size();
        for (CivilianNpc npc : List.copyOf(byId.values())) {
            removeBody(npc, true);
        }
        return n;
    }

    public Collection<CivilianNpc> all() {
        return List.copyOf(byId.values());
    }

    private void tick() {
        for (CivilianNpc npc : List.copyOf(byId.values())) {
            if (!npc.alive()) {
                continue;
            }
            LivingEntity body = NpcBodies.living(npc);
            if (body == null || !body.isValid() || body.isDead()) {
                poses.clear(npc.id());
                byId.remove(npc.id());
                continue;
            }
            brain.tick(npc, body);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (NpcBodies.isNpc(event.getPlayer())) {
            event.joinMessage(null);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            tab.showAll(event.getPlayer(), all());
            poses.syncViewer(event.getPlayer());
        }, 15L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                poses.syncViewer(event.getPlayer()), 40L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (NpcBodies.isNpc(event.getPlayer())) {
            event.quitMessage(null);
            return;
        }
        for (CivilianNpc npc : byId.values()) {
            if (event.getPlayer().getUniqueId().equals(npc.aimedBy())) {
                npc.setAimedBy(null);
            }
        }
    }

    @EventHandler
    public void onHurt(EntityDamageByEntityEvent event) {
        CivilianNpc npc = of(event.getEntity());
        if (npc == null) {
            return;
        }
        Player killer = killer(event.getDamager());
        LivingEntity body = event.getEntity() instanceof LivingEntity living ? living : NpcBodies.living(npc);
        if (body != null) {
            brain.hurt(npc, body, killer);
        }
        if (killer != null && npc.canTalk()) {
            talk.aimedAt(npc, killer);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        die(of(event.getEntity()), event.getEntity(), event.getEntity().getKiller(), event);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        CivilianNpc npc = of(event.getEntity());
        if (npc == null) {
            return;
        }
        event.setDeathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        die(npc, event.getEntity(), event.getEntity().getKiller(), null);
    }

    private void die(CivilianNpc npc, LivingEntity body, Player killer, EntityDeathEvent drops) {
        if (npc == null || !npc.alive()) {
            return;
        }
        if (drops != null) {
            drops.getDrops().clear();
            drops.setDroppedExp(0);
        }
        Location at = body.getLocation();
        npc.markDead();
        if (killer != null) {
            talk.dying(npc, killer);
        }
        poses.clear(npc.id());
        poses.clear(body.getUniqueId());
        corpses.spawn(npc, at, skins.profileFor(npc.id(), npc.name()));
        removeBody(npc, true);
    }

    private CivilianNpc of(Entity entity) {
        UUID id = NpcBodies.npcId(entity);
        return id == null ? null : byId.get(id);
    }

    private void removeBody(CivilianNpc npc, boolean hideTab) {
        poses.clear(npc.id());
        if (hideTab) {
            for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                if (!NpcBodies.isNpc(viewer)) {
                    tab.hide(viewer, npc);
                }
            }
        }
        LivingEntity body = NpcBodies.living(npc);
        if (body != null) {
            poses.clear(body.getUniqueId());
            body.remove();
        }
        byId.remove(npc.id());
    }

    private static Player killer(Entity damager) {
        if (damager instanceof Player player && NpcBodies.realPlayer(player)) {
            return player;
        }
        if (damager instanceof LivingEntity living && living.getKiller() != null) {
            return living.getKiller();
        }
        return null;
    }

    private String unique(String name) {
        String base = name;
        int n = 2;
        while (taken(base)) {
            String suffix = String.valueOf(n++);
            base = name.substring(0, Math.min(name.length(), 16 - suffix.length())) + suffix;
        }
        return base;
    }

    private boolean taken(String name) {
        for (CivilianNpc npc : byId.values()) {
            if (npc.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static List<org.bukkit.inventory.ItemStack> extras(LoadoutService.Kit kit) {
        List<org.bukkit.inventory.ItemStack> extras = new ArrayList<>();
        if (kit.mag() != null) {
            extras.add(kit.mag());
        }
        extras.addAll(kit.extras());
        return extras;
    }
}
