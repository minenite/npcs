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
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
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
    private final GunPoseBridge poses = new GunPoseBridge();
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
        this.brain = new CivilianBrain(plugin, talk, poses);
        this.npcKey = new NamespacedKey(plugin, "civilian");
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
        Mannequin body = place.getWorld().spawn(place, Mannequin.class, mannequin -> {
            mannequin.setPersistent(false);
            mannequin.setRemoveWhenFarAway(false);
            mannequin.setInvulnerable(false);
            mannequin.setSilent(false);
            mannequin.setGravity(false);
            mannequin.setAI(false);
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
            mannequin.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, id.toString());
        });
        npc.bind(body);
        byId.put(id, npc);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!body.isValid()) {
                return;
            }
            EntityEquipment equipment = body.getEquipment();
            if (equipment != null && kit.gun() != null) {
                equipment.setItem(EquipmentSlot.HAND, kit.gun().clone());
                equipment.setItem(EquipmentSlot.OFF_HAND, null);
            }
            poses.set(id, true, false);
            poses.set(body.getUniqueId(), true, false);
            body.addScoreboardTag("pgm_gun");
        });
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            tab.show(viewer, npc);
        }
        return npc;
    }

    public int removeNear(Location at, double range) {
        int n = 0;
        double rangeSq = range * range;
        for (CivilianNpc npc : List.copyOf(byId.values())) {
            Mannequin body = body(npc);
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
            Mannequin body = body(npc);
            if (body == null || !body.isValid()) {
                poses.clear(npc.id());
                byId.remove(npc.id());
                continue;
            }
            brain.tick(npc, body);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            tab.showAll(event.getPlayer(), all());
            poses.syncViewer(event.getPlayer());
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
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
        if (killer != null && npc.canTalk() && npc.state() != CivilianNpc.State.AIM) {
            talk.aimedAt(npc, killer);
        }
        if (npc.state() != CivilianNpc.State.AIM && npc.state() != CivilianNpc.State.FLEE) {
            npc.setState(CivilianNpc.State.WARY);
            npc.setWaryLeft(70);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        CivilianNpc npc = of(event.getEntity());
        if (npc == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        Location at = event.getEntity().getLocation();
        Player killer = event.getEntity().getKiller();
        npc.markDead();
        if (killer != null) {
            talk.dying(npc, killer);
        }
        poses.clear(npc.id());
        if (event.getEntity() != null) {
            poses.clear(event.getEntity().getUniqueId());
        }
        corpses.spawn(npc, at, skins.profileFor(npc.id(), npc.name()));
        removeBody(npc, true);
    }

    private CivilianNpc of(Entity entity) {
        if (entity == null) {
            return null;
        }
        String raw = entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return byId.get(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void removeBody(CivilianNpc npc, boolean hideTab) {
        poses.clear(npc.id());
        if (hideTab) {
            for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                tab.hide(viewer, npc);
            }
        }
        Mannequin body = body(npc);
        if (body != null) {
            poses.clear(body.getUniqueId());
            body.remove();
        }
        byId.remove(npc.id());
    }

    private Mannequin body(CivilianNpc npc) {
        if (npc.entityId() == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(npc.entityId());
        return entity instanceof Mannequin mannequin ? mannequin : null;
    }

    private static Player killer(Entity damager) {
        if (damager instanceof Player player) {
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
