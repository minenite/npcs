package net.minenite.npcs.civilian;

import io.papermc.paper.entity.LookAnchor;
import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.chat.LlmTalk;
import net.minenite.warzplugin.WarzPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-tick person: notice delay, wary aftermath, carry/aim pistol, walk, watch,
 * back off or stand ground. Never locks sneak — standing is always unfixed.
 */
public final class CivilianBrain {
    private final NpcsPlugin plugin;
    private final LlmTalk talk;
    private final GunPoseBridge poses;

    public CivilianBrain(NpcsPlugin plugin, LlmTalk talk, GunPoseBridge poses) {
        this.plugin = plugin;
        this.talk = talk;
        this.poses = poses;
    }

    public void tick(CivilianNpc npc, Mannequin body) {
        standUnlocked(body);
        if (npc.dueEquip()) {
            holdPistol(body, npc);
        }

        Player aimer = aimerOn(body);
        Player near = nearest(body, 16);
        Player armed = armedNear(body, 18);

        if (aimer != null) {
            onAimedAt(npc, body, aimer);
            applyLook(body, npc);
            return;
        }

        npc.tickAimHold();
        if (npc.state() == CivilianNpc.State.AIM && npc.aimHold() <= 0) {
            dropAim(npc, body);
        }

        switch (npc.state()) {
            case AIM -> {
                // held a moment after they looked away
                if (npc.aimedBy() != null) {
                    Player still = Bukkit.getPlayer(npc.aimedBy());
                    if (still != null && still.isOnline()) {
                        npc.lookToward(body.getEyeLocation(), still.getEyeLocation(), 0.28f, 0.18f);
                        npc.aimSway();
                    }
                }
                poseGun(npc, body, true, true);
            }
            case BACKPEDAL -> {
                step(npc, body, true);
                poseGun(npc, body, true, true);
                if (!npc.walking()) {
                    npc.setState(CivilianNpc.State.WARY);
                    npc.setWaryLeft(80 + ThreadLocalRandom.current().nextInt(80));
                }
            }
            case FLEE -> {
                npc.decFlee();
                step(npc, body, false);
                poseGun(npc, body, true, false);
                if (npc.fleeLeft() <= 0 || !npc.walking()) {
                    npc.setState(CivilianNpc.State.WARY);
                    npc.setWaryLeft(60 + ThreadLocalRandom.current().nextInt(50));
                    npc.clearWalk();
                }
            }
            case WARY -> {
                npc.decWary();
                poseGun(npc, body, true, false);
                if (armed != null) {
                    npc.lookToward(body.getEyeLocation(), armed.getEyeLocation(), 0.16f, 0.08f);
                } else {
                    npc.idleGlance();
                }
                if (npc.waryLeft() <= 0) {
                    npc.setState(CivilianNpc.State.STAND);
                    npc.setIdleLeft(40 + ThreadLocalRandom.current().nextInt(70));
                    poseGun(npc, body, true, false);
                }
            }
            case WATCH -> watch(npc, body, near, armed);
            case WALK -> {
                maybePauseToWatch(npc, body, near, armed);
                if (npc.state() == CivilianNpc.State.WALK) {
                    step(npc, body, false);
                    poseGun(npc, body, true, false);
                    footstep(body, npc);
                }
            }
            case SCAN -> scan(npc, body, near);
            case STAND -> standThink(npc, body, near, armed);
        }
        applyLook(body, npc);
    }

    private void onAimedAt(CivilianNpc npc, Mannequin body, Player aimer) {
        npc.setAimedBy(aimer.getUniqueId());
        npc.addNotice();
        npc.lookToward(body.getEyeLocation(), aimer.getEyeLocation(), 0.22f, 0.10f);
        if (npc.noticeTicks() < npc.personality().noticeDelayTicks()) {
            if (npc.state() == CivilianNpc.State.WALK) {
                npc.clearWalk();
            }
            npc.setState(CivilianNpc.State.WATCH);
            poseGun(npc, body, true, false);
            return;
        }
        npc.clearWalk();
        boolean first = npc.state() != CivilianNpc.State.AIM
                && npc.state() != CivilianNpc.State.BACKPEDAL;
        npc.setState(CivilianNpc.State.AIM);
        poseGun(npc, body, true, true);
        npc.aimSway();
        if (first && !npc.aimSpoken() && npc.canTalk()) {
            npc.markAimSpoken();
            talk.aimedAt(npc, aimer);
            if (!npc.personality().standsGround() && ThreadLocalRandom.current().nextBoolean()) {
                backOff(npc, body, aimer);
            }
        }
    }

    private void dropAim(CivilianNpc npc, Mannequin body) {
        npc.setAimedBy(null);
        npc.setState(CivilianNpc.State.WARY);
        npc.setWaryLeft(90 + ThreadLocalRandom.current().nextInt(70));
        npc.clearWalk();
        standUnlocked(body);
        poseGun(npc, body, true, false);
    }

    private void standThink(CivilianNpc npc, Mannequin body, Player near, Player armed) {
        poseGun(npc, body, true, false);
        npc.decIdle();
        npc.decDecision();
        if (armed != null && npc.personality().standsGround()) {
            npc.setWatching(armed.getUniqueId());
            npc.setState(CivilianNpc.State.WATCH);
            npc.setIdleLeft(35 + ThreadLocalRandom.current().nextInt(40));
            return;
        }
        if (near != null && ThreadLocalRandom.current().nextInt(80) == 0) {
            npc.setWatching(near.getUniqueId());
            npc.setState(CivilianNpc.State.WATCH);
            npc.setIdleLeft(25 + ThreadLocalRandom.current().nextInt(35));
            if (npc.personality().chatty() && npc.canTalk() && ThreadLocalRandom.current().nextInt(3) == 0) {
                talk.ambient(npc, near);
            }
            return;
        }
        npc.idleGlance();
        if (npc.idleLeft() > 0) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(5) == 0) {
            npc.setState(CivilianNpc.State.SCAN);
            npc.setIdleLeft(25 + ThreadLocalRandom.current().nextInt(35));
            return;
        }
        startWalk(npc, body, false);
    }

    private void scan(CivilianNpc npc, Mannequin body, Player near) {
        poseGun(npc, body, true, false);
        npc.decIdle();
        npc.idleGlance();
        if (near != null) {
            npc.lookToward(body.getEyeLocation(), near.getEyeLocation(), 0.08f, 0.03f);
        }
        if (npc.idleLeft() <= 0) {
            npc.setState(CivilianNpc.State.STAND);
            npc.setIdleLeft(20 + ThreadLocalRandom.current().nextInt(40));
        }
    }

    private void watch(CivilianNpc npc, Mannequin body, Player near, Player armed) {
        poseGun(npc, body, true, false);
        npc.decIdle();
        Player focus = armed != null ? armed : (npc.watching() != null ? Bukkit.getPlayer(npc.watching()) : near);
        if (focus != null && focus.isOnline() && focus.getWorld() == body.getWorld()) {
            npc.lookToward(body.getEyeLocation(), focus.getEyeLocation(), 0.14f, 0.06f);
        } else {
            npc.idleGlance();
        }
        if (npc.idleLeft() <= 0) {
            npc.setWatching(null);
            npc.setState(CivilianNpc.State.STAND);
            npc.setIdleLeft(15 + ThreadLocalRandom.current().nextInt(30));
        }
    }

    private void maybePauseToWatch(CivilianNpc npc, Mannequin body, Player near, Player armed) {
        if (armed == null && near == null) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(90) != 0) {
            return;
        }
        npc.clearWalk();
        npc.setWatching(armed != null ? armed.getUniqueId() : near.getUniqueId());
        npc.setState(CivilianNpc.State.WATCH);
        npc.setIdleLeft(20 + ThreadLocalRandom.current().nextInt(30));
    }

    private void startWalk(CivilianNpc npc, Mannequin body, boolean flee) {
        double min = plugin.getConfig().getDouble("civilian.wander-min", 4);
        double max = plugin.getConfig().getDouble("civilian.wander-max", 11);
        double speed = plugin.getConfig().getDouble("civilian.walk-speed", 0.11);
        if (flee) {
            min = 8;
            max = 16;
            speed = 0.18;
        }
        WanderEngine.plan(npc, body.getLocation(), min, max, speed);
        if (npc.state() != CivilianNpc.State.WALK && !flee) {
            npc.setState(CivilianNpc.State.STAND);
            npc.setIdleLeft(30);
        }
        if (flee) {
            npc.setState(CivilianNpc.State.FLEE);
        }
    }

    private void backOff(CivilianNpc npc, Mannequin body, Player from) {
        Location here = body.getLocation();
        org.bukkit.util.Vector away = here.toVector().subtract(from.getLocation().toVector());
        if (away.lengthSquared() < 0.01) {
            away = here.getDirection().multiply(-1);
        }
        away.setY(0).normalize().multiply(4.5);
        Location dest = WanderEngine.keepXZ(here.clone().add(away));
        if (dest == null) {
            return;
        }
        npc.beginWalk(here, here.clone().add(away.clone().multiply(0.5)), dest, 36);
        npc.setState(CivilianNpc.State.BACKPEDAL);
    }

    private void step(CivilianNpc npc, Mannequin body, boolean backpedal) {
        Location next = npc.stepWalk();
        if (next == null) {
            return;
        }
        Location use = WanderEngine.keepXZ(next);
        if (use == null) {
            use = next;
        }
        float yaw = backpedal ? npc.lookYaw() : npc.bodyYaw();
        use.setYaw(yaw);
        use.setPitch(npc.lookPitch());
        body.teleport(use);
    }

    private void footstep(Mannequin body, CivilianNpc npc) {
        if (ThreadLocalRandom.current().nextInt(9) != 0) {
            return;
        }
        body.getWorld().playSound(body.getLocation(), Sound.BLOCK_GRAVEL_STEP, 0.25f, 0.9f
                + ThreadLocalRandom.current().nextFloat() * 0.2f);
    }

    private void poseGun(CivilianNpc npc, Mannequin body, boolean gun, boolean aim) {
        holdPistol(body, npc);
        standUnlocked(body);
        if (gun) {
            body.addScoreboardTag("pgm_gun");
        } else {
            body.removeScoreboardTag("pgm_gun");
        }
        if (aim) {
            body.addScoreboardTag("pgm_aim");
        } else {
            body.removeScoreboardTag("pgm_aim");
        }
        poses.set(npc.id(), gun, aim);
        poses.set(body.getUniqueId(), gun, aim);
    }

    private void holdPistol(Mannequin body, CivilianNpc npc) {
        EntityEquipment equipment = body.getEquipment();
        if (equipment == null || npc.gun() == null) {
            return;
        }
        equipment.setItem(EquipmentSlot.HAND, npc.gun().clone());
        equipment.setItem(EquipmentSlot.OFF_HAND, null);
    }

    private void standUnlocked(Mannequin body) {
        try {
            if (body.getPose() != Pose.STANDING) {
                body.setPose(Pose.STANDING, false);
            } else {
                body.setPose(Pose.STANDING, false);
            }
        } catch (IllegalArgumentException ignored) {
            try {
                body.setPose(Pose.STANDING);
            } catch (Exception ignoredToo) {
            }
        }
    }

    private void applyLook(Mannequin body, CivilianNpc npc) {
        try {
            double dist = 8;
            double yaw = Math.toRadians(npc.lookYaw());
            double pitch = Math.toRadians(npc.lookPitch());
            double x = body.getEyeLocation().getX() - Math.sin(yaw) * Math.cos(pitch) * dist;
            double y = body.getEyeLocation().getY() - Math.sin(pitch) * dist;
            double z = body.getEyeLocation().getZ() + Math.cos(yaw) * Math.cos(pitch) * dist;
            body.lookAt(x, y, z, LookAnchor.EYES);
        } catch (Exception ignored) {
        }
        body.setRotation(npc.lookYaw(), npc.lookPitch());
        try {
            body.setBodyYaw(npc.bodyYaw());
        } catch (Exception ignored) {
        }
    }

    private Player aimerOn(Mannequin body) {
        double range = plugin.getConfig().getDouble("civilian.aim-range", 42);
        double need = plugin.getConfig().getDouble("civilian.aim-dot", 0.92);
        Player best = null;
        double bestDot = need;
        for (Player player : body.getWorld().getPlayers()) {
            if (!player.isOnline() || player.getLocation().distanceSquared(body.getLocation()) > range * range) {
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
            double dist = to.length();
            if (dist < 1.0e-6) {
                continue;
            }
            to.multiply(1.0 / dist);
            double dot = dir.dot(to);
            if (dot < bestDot) {
                continue;
            }
            if (body.getBoundingBox().expand(0.4).rayTrace(eye.toVector(), dir, dist + 1.0) == null) {
                continue;
            }
            bestDot = dot;
            best = player;
        }
        return best;
    }

    private Player nearest(Mannequin body, double range) {
        Player best = null;
        double bestD = range * range;
        for (Player player : body.getWorld().getPlayers()) {
            double d = player.getLocation().distanceSquared(body.getLocation());
            if (d < bestD) {
                bestD = d;
                best = player;
            }
        }
        return best;
    }

    private Player armedNear(Mannequin body, double range) {
        Player best = null;
        double bestD = range * range;
        for (Player player : body.getWorld().getPlayers()) {
            if (!holdingGun(player)) {
                continue;
            }
            double d = player.getLocation().distanceSquared(body.getLocation());
            if (d < bestD) {
                bestD = d;
                best = player;
            }
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
}
