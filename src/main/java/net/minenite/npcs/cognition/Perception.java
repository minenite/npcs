package net.minenite.npcs.cognition;

import net.minenite.npcs.civilian.CivilianNpc;
import net.minenite.npcs.civilian.NpcBodies;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Limited, uncertain seeing. Live coordinates are not granted.
 */
public final class Perception {
    private Perception() {
    }

    public static boolean sees(CivilianNpc npc, LivingEntity body, LivingEntity other) {
        if (other == null || body.getWorld() != other.getWorld()) {
            return false;
        }
        Location eye = body.getEyeLocation();
        Location tgt = other.getEyeLocation();
        double dist = eye.distance(tgt);
        if (dist > 48) {
            return false;
        }
        Vector to = tgt.toVector().subtract(eye.toVector());
        if (to.lengthSquared() < 1e-6) {
            return true;
        }
        to.normalize();
        double yaw = Math.toRadians(npc.lookYaw());
        Vector look = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        double dot = look.dot(new Vector(to.getX(), 0, to.getZ()).normalize());
        boolean focus = npc.cog().attention.is(other.getUniqueId());
        double need = focus ? 0.28 : 0.62;
        need -= npc.cog().drives.suspicion * 0.08;
        need += (1.0 - npc.cog().traits.reaction) * 0.08;
        if (dot < need) {
            return false;
        }
        if (!focus && ThreadLocalRandom.current().nextDouble() > 0.55 + npc.cog().traits.reaction * 0.3) {
            return false;
        }
        RayTraceResult hit = body.getWorld().rayTraceBlocks(eye, to, dist, FluidCollisionMode.NEVER, true);
        return hit == null;
    }

    public static Player visibleAimer(CivilianNpc npc, LivingEntity body, double range) {
        Player best = null;
        double bestDot = -1;
        for (Player player : body.getWorld().getPlayers()) {
            if (!NpcBodies.realPlayer(player) || !holdingGun(player)) {
                continue;
            }
            if (player.getLocation().distanceSquared(body.getLocation()) > range * range) {
                continue;
            }
            if (!sees(npc, body, player)) {
                continue;
            }
            Location eye = player.getEyeLocation();
            Vector dir = eye.getDirection();
            if (dir.lengthSquared() < 1e-6) {
                continue;
            }
            dir.normalize();
            Vector to = body.getEyeLocation().toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist < 1e-6) {
                continue;
            }
            to.multiply(1.0 / dist);
            double dot = dir.dot(to);
            boolean ads = player.getScoreboardTags().contains("pgm_aim");
            if (dot < (ads ? 0.70 : 0.88)) {
                continue;
            }
            if (dot > bestDot) {
                bestDot = dot;
                best = player;
            }
        }
        return best;
    }

    public static int noticeDelay(CivilianNpc npc, Player who, boolean ads) {
        Cognition c = npc.cog();
        int base = (int) (4 + (1.0 - c.traits.reaction) * 18);
        if (c.attention.is(who.getUniqueId())) {
            base = Math.max(3, base / 3);
        } else {
            base += 8 + (int) (c.drives.boredom * 6);
        }
        if (ads) {
            base = Math.max(4, (int) (base * 0.65));
        }
        if (c.drives.stress > 0.7) {
            base += 4;
        }
        return base;
    }

    private static boolean holdingGun(Player player) {
        org.bukkit.plugin.Plugin warz = org.bukkit.Bukkit.getPluginManager().getPlugin("WarzPlugin");
        if (warz instanceof net.minenite.warzplugin.WarzPlugin plugin && plugin.items() != null) {
            return plugin.items().isGunItem(player.getInventory().getItemInMainHand());
        }
        return !player.getInventory().getItemInMainHand().getType().isAir();
    }

    public static WorldCue cue(CivilianNpc npc, LivingEntity body, Player near, Player armed,
                              Player aimer, boolean corpse, SoundWorld.Pulse sound, Iterable<CivilianNpc> others) {
        WorldCue c = new WorldCue();
        Location at = body.getLocation();
        c.indoors = Places.indoors(at) ? 1 : 0;
        c.night = at.getWorld().getTime() > 13000 && at.getWorld().getTime() < 23000 ? 1 : 0;
        c.rain = at.getWorld().hasStorm();
        c.corpse = corpse ? 1 : 0;
        c.playerNear = near != null ? 1 : 0;
        c.armedStranger = 0;
        if (armed != null) {
            Bond b = npc.cog().bonds.get(armed.getUniqueId());
            boolean friend = b != null && b.trust > 0.25 && b.perceivedDanger < 0.4;
            if (!friend) {
                c.armedStranger = 1;
            } else {
                c.friendNear = 1;
            }
            c.focusId = armed.getUniqueId();
            c.distanceFocus = armed.getLocation().distance(at);
        }
        if (aimer != null) {
            c.aimedAt = 1;
            c.threat = 0.85;
            c.focusId = aimer.getUniqueId();
        }
        if (near != null && Perception.sees(npc, body, near)) {
            c.visibleStranger = 1;
        }
        if (sound != null) {
            boolean walls = Places.indoors(at);
            c.soundConf = SoundWorld.confidence(at, sound, walls, npc.cog().traits.hearing);
        }
        int friends = 0;
        for (CivilianNpc o : others) {
            if (o == npc || o.body() == null) {
                continue;
            }
            if (o.body().getLocation().distanceSquared(at) < 64) {
                Bond b = npc.cog().bonds.get(o.id());
                if (b != null && b.trust > 0.2) {
                    friends++;
                }
                c.friendlyNear = 1;
            }
        }
        if (friends > 0) {
            c.friendNear = 1;
        }
        c.coverHere = Places.coverHint(at);
        if (c.threat == 0) {
            c.threat = c.armedStranger * 0.4 + (1.0 - npc.cog().drives.safety) * 0.3 + c.soundConf * 0.2;
        }
        return c;
    }
}
