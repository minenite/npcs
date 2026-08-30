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
import net.minenite.warzplugin.WarzPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class NpcManager implements Listener {
    private final NpcsPlugin plugin;
    private final SkinService skins;
    private final LlmTalk talk;
    private final TabListPackets tab;
    private final LoadoutService loadouts;
    private final CorpseSpawner corpses;
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
                kit.gun(), kit.mag(), extras(kit));
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
            mannequin.setGravity(true);
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
            mannequin.setHealth(Math.min(mannequin.getMaxHealth(),
                    plugin.getConfig().getDouble("civilian.health", 16)));
            EntityEquipment equipment = mannequin.getEquipment();
            if (equipment != null) {
                equipment.setItemInMainHand(kit.gun());
                equipment.setItemInOffHand(kit.mag());
            }
            mannequin.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, id.toString());
        });
        npc.bind(body);
        npc.setIdleLeft(20 + ThreadLocalRandom.current().nextInt(40));
        byId.put(id, npc);
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
                byId.remove(npc.id());
                continue;
            }
            Player aimer = aimerOn(body);
            if (aimer != null) {
                handleAimed(npc, body, aimer);
                continue;
            }
            npc.tickAimHold();
            if (npc.mood() == CivilianNpc.Mood.AIMED && npc.aimHold() <= 0) {
                npc.setMood(CivilianNpc.Mood.IDLE);
                npc.setIdleLeft(30 + ThreadLocalRandom.current().nextInt(50));
            }
            if (npc.mood() == CivilianNpc.Mood.WALK) {
                Location next = npc.stepWalk();
                if (next != null) {
                    Location grounded = WanderEngine.ground(next);
                    Location use = grounded != null ? grounded : next;
                    use.setYaw(npc.lookYaw());
                    use.setPitch(npc.lookPitch());
                    body.teleport(use);
                    body.setRotation(npc.lookYaw(), npc.lookPitch());
                }
                continue;
            }
            npc.decIdle();
            npc.idleGlance();
            body.setRotation(npc.lookYaw(), npc.lookPitch());
            if (npc.idleLeft() <= 0) {
                WanderEngine.plan(npc, body.getLocation(),
                        plugin.getConfig().getDouble("civilian.wander-min", 7),
                        plugin.getConfig().getDouble("civilian.wander-max", 22),
                        plugin.getConfig().getDouble("civilian.walk-speed", 0.13));
            }
            maybeAmbient(npc, body);
        }
    }

    private void handleAimed(CivilianNpc npc, Mannequin body, Player aimer) {
        boolean first = npc.aimedBy() == null || !npc.aimedBy().equals(aimer.getUniqueId());
        npc.setAimedBy(aimer.getUniqueId());
        npc.setMood(CivilianNpc.Mood.AIMED);
        npc.clearWalk();
        Location eyes = body.getEyeLocation();
        npc.lookAt(eyes, aimer.getEyeLocation());
        body.setRotation(npc.lookYaw(), npc.lookPitch());
        if (first || (npc.canTalk() && System.currentTimeMillis() - npc.lastTalkAt()
                > plugin.getConfig().getLong("civilian.talk-cooldown-ms", 9000L))) {
            talk.aimedAt(npc, aimer);
        }
    }

    private Player aimerOn(Mannequin body) {
        double range = plugin.getConfig().getDouble("civilian.aim-range", 42);
        double need = plugin.getConfig().getDouble("civilian.aim-dot", 0.94);
        Player best = null;
        double bestDot = need;
        for (Player player : body.getWorld().getPlayers()) {
            if (!player.isOnline() || player.getWorld() != body.getWorld()) {
                continue;
            }
            if (player.getLocation().distanceSquared(body.getLocation()) > range * range) {
                continue;
            }
            if (!holdingGun(player)) {
                continue;
            }
            Location eye = player.getEyeLocation();
            org.bukkit.util.Vector dir = eye.getDirection();
            if (dir.lengthSquared() < 1.0e-6) {
                continue;
            }
            dir.normalize();
            org.bukkit.util.Vector to = body.getEyeLocation().toVector().subtract(eye.toVector());
            if (to.lengthSquared() < 1.0e-6) {
                continue;
            }
            double dist = to.length();
            to.multiply(1.0 / dist);
            double dot = dir.dot(to);
            if (dot < bestDot) {
                continue;
            }
            if (body.getBoundingBox().expand(0.35).rayTrace(eye.toVector(), dir, dist + 1.0) == null) {
                continue;
            }
            bestDot = dot;
            best = player;
        }
        return best;
    }

    private boolean holdingGun(Player player) {
        Plugin warz = Bukkit.getPluginManager().getPlugin("WarzPlugin");
        if (warz instanceof WarzPlugin plugin && plugin.items() != null) {
            return plugin.items().isGunItem(player.getInventory().getItemInMainHand());
        }
        return !player.getInventory().getItemInMainHand().getType().isAir();
    }

    private void maybeAmbient(CivilianNpc npc, Mannequin body) {
        if (ThreadLocalRandom.current().nextInt(400) != 0 || !npc.canTalk()) {
            return;
        }
        Player near = null;
        double best = 14 * 14;
        for (Player player : body.getWorld().getPlayers()) {
            double d = player.getLocation().distanceSquared(body.getLocation());
            if (d < best) {
                best = d;
                near = player;
            }
        }
        if (near != null) {
            talk.ambient(npc, near);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> tab.showAll(event.getPlayer(), all()), 15L);
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
        if (killer != null && npc.canTalk()) {
            talk.aimedAt(npc, killer);
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
        if (hideTab) {
            for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                tab.hide(viewer, npc);
            }
        }
        Mannequin body = body(npc);
        if (body != null) {
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
        if (kit.spare() != null) {
            extras.add(kit.spare());
        }
        extras.addAll(kit.extras());
        return extras;
    }
}
