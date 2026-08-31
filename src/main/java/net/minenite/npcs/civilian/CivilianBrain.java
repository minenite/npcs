package net.minenite.npcs.civilian;

import io.papermc.paper.entity.LookAnchor;
import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.chat.LlmTalk;
import net.minenite.warzplugin.WarzPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.Plugin;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Full person loop: notice, draw, ADS, circle, holster, flinch, cover, inspect.
 * Aim is slowness IV + gun_pose to every client — never locked sneak.
 */
public final class CivilianBrain {
    private final NpcsPlugin plugin;
    private final LlmTalk talk;
    private final GunPoseBridge poses;
    private final EquipmentPackets equipment;

    public CivilianBrain(NpcsPlugin plugin, LlmTalk talk, GunPoseBridge poses, EquipmentPackets equipment) {
        this.plugin = plugin;
        this.talk = talk;
        this.poses = poses;
        this.equipment = equipment;
    }

    public void tick(CivilianNpc npc, LivingEntity body) {
        keepHuman(body);
        boolean aiming = npc.state() == CivilianNpc.State.AIM || npc.state() == CivilianNpc.State.CIRCLE;
        if (npc.dueEquip() || emptyHand(body)) {
            holdPistol(body, npc, aiming);
        }
        if (npc.duePoseRefresh()) {
            poses.refresh();
        }

        Player aimer = aimerOn(body);
        Player near = nearest(body, 18);
        Player armed = armedNear(body, 20);
        Player shooter = shooterNear(body, 28);

        if (shooter != null && npc.state() != CivilianNpc.State.AIM
                && npc.state() != CivilianNpc.State.DRAW
                && npc.state() != CivilianNpc.State.CIRCLE
                && npc.state() != CivilianNpc.State.FLINCH) {
            onGunshot(npc, body, shooter);
        }

        if (aimer != null) {
            onAimedAt(npc, body, aimer);
            applyLook(body, npc);
            return;
        }

        npc.tickAimHold();
        if ((npc.state() == CivilianNpc.State.AIM || npc.state() == CivilianNpc.State.CIRCLE
                || npc.state() == CivilianNpc.State.DRAW) && npc.aimHold() <= 0) {
            beginHolster(npc, body);
        }

        switch (npc.state()) {
            case DRAW -> draw(npc, body);
            case AIM -> aimHold(npc, body);
            case CIRCLE -> circle(npc, body);
            case HOLSTER -> holster(npc, body);
            case BACKPEDAL -> {
                step(npc, body, true);
                poseGun(npc, body, true, true, false);
                if (!npc.walking()) {
                    npc.setState(CivilianNpc.State.WARY);
                    npc.setWaryLeft(90 + ThreadLocalRandom.current().nextInt(80));
                }
            }
            case FLEE -> {
                npc.decFlee();
                step(npc, body, false);
                poseGun(npc, body, true, false, true);
                if (npc.fleeLeft() <= 0 || !npc.walking()) {
                    npc.setState(CivilianNpc.State.COVER);
                    npc.setCoverLeft(40 + ThreadLocalRandom.current().nextInt(40));
                    npc.clearWalk();
                }
            }
            case FLINCH -> flinch(npc, body);
            case INSPECT -> inspect(npc, body);
            case COVER -> cover(npc, body, armed);
            case WARY -> wary(npc, body, armed);
            case WATCH -> watch(npc, body, near, armed);
            case WALK -> {
                maybePause(npc, body, near, armed);
                if (npc.state() == CivilianNpc.State.WALK) {
                    step(npc, body, false);
                    poseGun(npc, body, true, false, false);
                    footstep(body, npc);
                }
            }
            case SCAN -> scan(npc, body, near);
            case STAND -> standThink(npc, body, near, armed);
        }
        applyLook(body, npc);
    }

    private void onAimedAt(CivilianNpc npc, LivingEntity body, Player aimer) {
        boolean ads = playerAiming(aimer);
        npc.setAimedBy(aimer.getUniqueId());
        npc.addNotice();
        npc.lookToward(body.getEyeLocation(), aimer.getEyeLocation(), ads ? 0.42f : 0.22f, ads ? 0.22f : 0.10f);
        if (ads) {
            snapAim(npc, body, aimer);
            return;
        }
        int need = Math.max(1, npc.personality().noticeDelayTicks());
        if (npc.remembered(aimer.getUniqueId())) {
            need = 1;
        }
        if (npc.noticeTicks() < need) {
            if (npc.state() == CivilianNpc.State.WALK) {
                npc.clearWalk();
            }
            if (npc.state() != CivilianNpc.State.DRAW && npc.state() != CivilianNpc.State.AIM
                    && npc.state() != CivilianNpc.State.CIRCLE) {
                npc.setState(CivilianNpc.State.WATCH);
            }
            poseGun(npc, body, true, false, npc.state() == CivilianNpc.State.DRAW);
            return;
        }
        if (npc.state() == CivilianNpc.State.AIM || npc.state() == CivilianNpc.State.CIRCLE) {
            poseGun(npc, body, true, true, false);
            npc.aimSway();
            if (npc.state() == CivilianNpc.State.CIRCLE) {
                step(npc, body, false);
            } else if (npc.personality().circleStrafes() && ThreadLocalRandom.current().nextInt(80) == 0) {
                startCircle(npc, body, aimer);
            }
            return;
        }
        if (npc.state() != CivilianNpc.State.DRAW) {
            npc.clearWalk();
            npc.setState(CivilianNpc.State.DRAW);
            npc.setDrawLeft(npc.personality().drawDelayTicks());
            poseGun(npc, body, true, false, true);
            body.getWorld().playSound(body.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.45f, 1.25f);
            warnAimer(npc, body, aimer);
        }
        draw(npc, body);
    }

    private void snapAim(CivilianNpc npc, LivingEntity body, Player aimer) {
        HumanMotor.plant(body);
        npc.clearWalk();
        if (npc.state() != CivilianNpc.State.AIM && npc.state() != CivilianNpc.State.CIRCLE) {
            npc.setState(CivilianNpc.State.AIM);
            npc.setDrawLeft(0);
            body.getWorld().playSound(body.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.28f, 1.45f);
            warnAimer(npc, body, aimer);
        }
        poseGun(npc, body, true, true, false);
        npc.aimSway();
    }

    private void warnAimer(CivilianNpc npc, LivingEntity body, Player aimer) {
        if (npc.aimSpoken()) {
            return;
        }
        npc.markAimSpoken();
        talk.aimedAt(npc, aimer);
        if (!npc.personality().standsGround() && ThreadLocalRandom.current().nextInt(3) != 0) {
            backOff(npc, body, aimer);
        }
    }

    private void draw(CivilianNpc npc, LivingEntity body) {
        npc.decDraw();
        poseGun(npc, body, true, false, true);
        if (npc.aimedBy() != null) {
            Player still = Bukkit.getPlayer(npc.aimedBy());
            if (NpcBodies.realPlayer(still)) {
                npc.lookToward(body.getEyeLocation(), still.getEyeLocation(), 0.26f, 0.14f);
            }
        }
        if (npc.drawLeft() > 0) {
            return;
        }
        finishDraw(npc, body);
    }

    private void finishDraw(CivilianNpc npc, LivingEntity body) {
        npc.setState(CivilianNpc.State.AIM);
        poseGun(npc, body, true, true, false);
        body.getWorld().playSound(body.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.2f, 1.6f);
        if (npc.aimedBy() != null && npc.personality().circleStrafes()
                && ThreadLocalRandom.current().nextInt(3) == 0) {
            Player focus = Bukkit.getPlayer(npc.aimedBy());
            if (NpcBodies.realPlayer(focus)) {
                startCircle(npc, body, focus);
            }
        }
    }

    private void aimHold(CivilianNpc npc, LivingEntity body) {
        HumanMotor.plant(body);
        if (npc.aimedBy() != null) {
            Player still = Bukkit.getPlayer(npc.aimedBy());
            if (NpcBodies.realPlayer(still)) {
                npc.lookToward(body.getEyeLocation(), still.getEyeLocation(), 0.30f, 0.18f);
                npc.aimSway();
            }
        }
        poseGun(npc, body, true, true, false);
    }

    private void circle(CivilianNpc npc, LivingEntity body) {
        npc.decCircle();
        step(npc, body, false);
        poseGun(npc, body, true, true, false);
        if (npc.aimedBy() != null) {
            Player still = Bukkit.getPlayer(npc.aimedBy());
            if (NpcBodies.realPlayer(still)) {
                npc.lookToward(body.getEyeLocation(), still.getEyeLocation(), 0.34f, 0.20f);
                npc.aimSway();
            }
        }
        if (npc.circleLeft() <= 0 || !npc.walking()) {
            npc.clearWalk();
            npc.setState(CivilianNpc.State.AIM);
        }
    }

    private void beginHolster(CivilianNpc npc, LivingEntity body) {
        npc.setAimedBy(null);
        npc.clearWalk();
        npc.setState(CivilianNpc.State.HOLSTER);
        npc.setHolsterLeft(npc.personality().holsterDelayTicks());
        standUnlocked(body);
        poseGun(npc, body, true, false, true);
    }

    private void holster(CivilianNpc npc, LivingEntity body) {
        npc.decHolster();
        poseGun(npc, body, true, false, npc.holsterLeft() > npc.personality().holsterDelayTicks() / 2);
        npc.idleGlance();
        if (npc.holsterLeft() > 0) {
            return;
        }
        npc.setState(CivilianNpc.State.WARY);
        npc.setWaryLeft(80 + ThreadLocalRandom.current().nextInt(90));
        poseGun(npc, body, true, false, false);
    }

    private void flinch(CivilianNpc npc, LivingEntity body) {
        npc.decFlinch();
        npc.flinchLook();
        poseGun(npc, body, true, npc.aimedBy() != null, false);
        if (npc.flinchLeft() > 0) {
            return;
        }
        if (npc.aimedBy() != null) {
            npc.setState(CivilianNpc.State.AIM);
            poseGun(npc, body, true, true, false);
        } else {
            npc.setState(CivilianNpc.State.WARY);
            npc.setWaryLeft(50);
        }
    }

    private void inspect(CivilianNpc npc, LivingEntity body) {
        npc.decInspect();
        npc.inspectLook();
        poseGun(npc, body, true, false, true);
        if (npc.inspectLeft() <= 0) {
            npc.setState(CivilianNpc.State.STAND);
            npc.setIdleLeft(20 + ThreadLocalRandom.current().nextInt(40));
            poseGun(npc, body, true, false, false);
        }
    }

    private void cover(CivilianNpc npc, LivingEntity body, Player armed) {
        npc.decCover();
        poseGun(npc, body, true, false, false);
        if (armed != null) {
            npc.lookToward(body.getEyeLocation(), armed.getEyeLocation(), 0.10f, 0.05f);
        } else {
            npc.idleGlance();
        }
        if (npc.coverLeft() <= 0) {
            npc.setState(CivilianNpc.State.WARY);
            npc.setWaryLeft(40 + ThreadLocalRandom.current().nextInt(40));
        }
    }

    private void wary(CivilianNpc npc, LivingEntity body, Player armed) {
        npc.decWary();
        poseGun(npc, body, true, false, false);
        if (armed != null) {
            npc.lookToward(body.getEyeLocation(), armed.getEyeLocation(), 0.16f, 0.08f);
        } else if (npc.rememberedAimer() != null) {
            Player ghost = Bukkit.getPlayer(npc.rememberedAimer());
            if (NpcBodies.realPlayer(ghost) && ghost.getWorld() == body.getWorld()) {
                npc.lookToward(body.getEyeLocation(), ghost.getEyeLocation(), 0.08f, 0.03f);
            } else {
                npc.idleGlance();
            }
        } else {
            npc.idleGlance();
        }
        if (npc.waryLeft() <= 0) {
            npc.setState(CivilianNpc.State.STAND);
            npc.setIdleLeft(30 + ThreadLocalRandom.current().nextInt(60));
            poseGun(npc, body, true, false, false);
        }
    }

    private void standThink(CivilianNpc npc, LivingEntity body, Player near, Player armed) {
        poseGun(npc, body, true, false, false);
        npc.decIdle();
        npc.decDecision();
        if (armed != null && npc.personality().standsGround()) {
            npc.setWatching(armed.getUniqueId());
            npc.setState(CivilianNpc.State.WATCH);
            npc.setIdleLeft(35 + ThreadLocalRandom.current().nextInt(40));
            return;
        }
        if (near != null && ThreadLocalRandom.current().nextInt(70) == 0) {
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
        int roll = ThreadLocalRandom.current().nextInt(12);
        if (roll == 0) {
            npc.setState(CivilianNpc.State.SCAN);
            npc.setIdleLeft(25 + ThreadLocalRandom.current().nextInt(35));
            return;
        }
        if (roll == 1 && npc.personality().inspectsGun()) {
            npc.setState(CivilianNpc.State.INSPECT);
            npc.setInspectLeft(18 + ThreadLocalRandom.current().nextInt(16));
            return;
        }
        if (roll == 2 && npc.personality().ducksToCover()) {
            npc.setState(CivilianNpc.State.COVER);
            npc.setCoverLeft(25 + ThreadLocalRandom.current().nextInt(30));
            return;
        }
        startWalk(npc, body, false);
    }

    private void scan(CivilianNpc npc, LivingEntity body, Player near) {
        poseGun(npc, body, true, false, false);
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

    private void watch(CivilianNpc npc, LivingEntity body, Player near, Player armed) {
        poseGun(npc, body, true, false, false);
        npc.decIdle();
        Player focus = armed != null ? armed : (npc.watching() != null ? Bukkit.getPlayer(npc.watching()) : near);
        if (NpcBodies.realPlayer(focus) && focus.getWorld() == body.getWorld()) {
            npc.lookToward(body.getEyeLocation(), focus.getEyeLocation(), 0.16f, 0.07f);
        } else {
            npc.idleGlance();
        }
        if (npc.idleLeft() <= 0) {
            npc.setWatching(null);
            npc.setState(CivilianNpc.State.STAND);
            npc.setIdleLeft(15 + ThreadLocalRandom.current().nextInt(30));
        }
    }

    private void maybePause(CivilianNpc npc, LivingEntity body, Player near, Player armed) {
        if (armed == null && near == null) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(70) != 0) {
            return;
        }
        npc.clearWalk();
        if (armed != null && npc.personality().ducksToCover() && ThreadLocalRandom.current().nextBoolean()) {
            npc.setState(CivilianNpc.State.COVER);
            npc.setCoverLeft(20 + ThreadLocalRandom.current().nextInt(25));
            return;
        }
        npc.setWatching(armed != null ? armed.getUniqueId() : near.getUniqueId());
        npc.setState(CivilianNpc.State.WATCH);
        npc.setIdleLeft(20 + ThreadLocalRandom.current().nextInt(30));
    }

    private void onGunshot(CivilianNpc npc, LivingEntity body, Player shooter) {
        npc.remember(shooter.getUniqueId());
        npc.lookToward(body.getEyeLocation(), shooter.getEyeLocation(), 0.28f, 0.12f);
        if (npc.personality().panicsAtShots() && ThreadLocalRandom.current().nextBoolean()) {
            npc.setFleeLeft(50 + ThreadLocalRandom.current().nextInt(40));
            startWalk(npc, body, true);
            return;
        }
        npc.setState(CivilianNpc.State.FLINCH);
        npc.setFlinchLeft(8 + ThreadLocalRandom.current().nextInt(8));
        if (npc.canTalk() && ThreadLocalRandom.current().nextInt(3) == 0) {
            talk.aimedAt(npc, shooter);
        }
    }

    public void hurt(CivilianNpc npc, LivingEntity body, Player from) {
        npc.setState(CivilianNpc.State.FLINCH);
        npc.setFlinchLeft(10 + ThreadLocalRandom.current().nextInt(8));
        if (from != null) {
            npc.setAimedBy(from.getUniqueId());
            npc.remember(from.getUniqueId());
        }
        poseGun(npc, body, true, true, false);
    }

    private void startWalk(CivilianNpc npc, LivingEntity body, boolean flee) {
        double min = plugin.getConfig().getDouble("civilian.wander-min", 4);
        double max = plugin.getConfig().getDouble("civilian.wander-max", 11);
        double speed = plugin.getConfig().getDouble("civilian.walk-speed", 0.20);
        if (flee) {
            min = 8;
            max = 16;
            speed = 0.28;
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

    private void startCircle(CivilianNpc npc, LivingEntity body, Player around) {
        Location here = body.getLocation();
        Vector out = here.toVector().subtract(around.getLocation().toVector());
        if (out.lengthSquared() < 0.01) {
            out = here.getDirection();
        }
        out.setY(0).normalize();
        Vector side = new Vector(-out.getZ(), 0, out.getX())
                .multiply(ThreadLocalRandom.current().nextBoolean() ? 3.2 : -3.2);
        Location dest = WanderEngine.keepXZ(here.clone().add(side));
        if (dest == null) {
            return;
        }
        npc.setState(CivilianNpc.State.CIRCLE);
        npc.setCircleLeft(24 + ThreadLocalRandom.current().nextInt(20));
        npc.beginWalk(here, here.clone().add(side.clone().multiply(0.5)), dest, 22);
        npc.setState(CivilianNpc.State.CIRCLE);
    }

    private void backOff(CivilianNpc npc, LivingEntity body, Player from) {
        Location here = body.getLocation();
        Vector away = here.toVector().subtract(from.getLocation().toVector());
        if (away.lengthSquared() < 0.01) {
            away = here.getDirection().multiply(-1);
        }
        away.setY(0).normalize().multiply(4.8);
        Location dest = WanderEngine.keepXZ(here.clone().add(away));
        if (dest == null) {
            return;
        }
        npc.beginWalk(here, here.clone().add(away.clone().multiply(0.5)), dest, 32);
        npc.setState(CivilianNpc.State.BACKPEDAL);
    }

    private void step(CivilianNpc npc, LivingEntity body, boolean backpedal) {
        if (npc.turning()) {
            HumanMotor.plant(body);
            HumanMotor.face(body, npc);
            return;
        }
        Location dest = npc.walkDest();
        if (dest == null) {
            HumanMotor.plant(body);
            return;
        }
        double speed = plugin.getConfig().getDouble("civilian.walk-speed", 0.20);
        if (npc.state() == CivilianNpc.State.FLEE) {
            speed = 0.28;
        } else if (npc.state() == CivilianNpc.State.BACKPEDAL) {
            speed = 0.14;
        } else if (npc.state() == CivilianNpc.State.CIRCLE) {
            speed = 0.16;
        }
        speed *= npc.personality().walkMul();
        boolean arrived = HumanMotor.walkToward(body, npc, dest, speed, backpedal);
        if (arrived || npc.stuck(body.getLocation())) {
            npc.clearWalk();
            npc.clearStuck();
            HumanMotor.plant(body);
            if (npc.state() == CivilianNpc.State.WALK) {
                npc.setState(CivilianNpc.State.STAND);
                npc.setIdleLeft(20 + ThreadLocalRandom.current().nextInt(55));
            }
        }
    }

    private void footstep(LivingEntity body, CivilianNpc npc) {
        if (ThreadLocalRandom.current().nextInt(8) != 0) {
            return;
        }
        body.getWorld().playSound(body.getLocation(), Sound.BLOCK_GRAVEL_STEP, 0.28f, 0.85f
                + ThreadLocalRandom.current().nextFloat() * 0.25f);
    }

    private void poseGun(CivilianNpc npc, LivingEntity body, boolean gun, boolean aim, boolean hipRaise) {
        byte flags = (byte) ((gun ? 1 : 0) | (aim ? 2 : 0) | (hipRaise ? 4 : 0));
        if (npc.poseFlags() != flags) {
            npc.setPoseFlags(flags);
            holdPistol(body, npc, aim);
        }
        standUnlocked(body);
        if (gun) {
            body.addScoreboardTag("pgm_gun");
        } else {
            body.removeScoreboardTag("pgm_gun");
        }
        if (aim) {
            body.addScoreboardTag("pgm_aim");
            body.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 16, 3, false, false, false));
            if (body instanceof Player player) {
                try {
                    player.startUsingItem(EquipmentSlot.HAND);
                } catch (Exception ignored) {
                }
            }
        } else {
            body.removeScoreboardTag("pgm_aim");
            body.removePotionEffect(PotionEffectType.SLOWNESS);
            if (body instanceof Player player) {
                try {
                    player.clearActiveItem();
                } catch (Exception ignored) {
                }
            }
        }
        if (hipRaise) {
            body.addScoreboardTag("pgm_fire");
        } else {
            body.removeScoreboardTag("pgm_fire");
        }
        UUID entityId = body.getUniqueId();
        poses.set(entityId, gun, aim, hipRaise);
        if (!entityId.equals(npc.id())) {
            poses.set(npc.id(), gun, aim, hipRaise);
        }
    }

    private void holdPistol(LivingEntity body, CivilianNpc npc, boolean aim) {
        if (npc.gun() == null) {
            return;
        }
        ItemStack gun = npc.gun().clone();
        ItemMeta meta = gun.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey("pvpgunminus", "npc_hold"),
                    PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(new NamespacedKey("pvpgunminus", "npc_ads"),
                    PersistentDataType.BYTE, (byte) (aim ? 1 : 0));
            gun.setItemMeta(meta);
        }
        if (body instanceof Player player) {
            player.getInventory().setItemInMainHand(gun);
            player.getInventory().setItemInOffHand(null);
        }
        EntityEquipment hands = body.getEquipment();
        if (hands != null) {
            hands.setItemInMainHand(gun);
            hands.setItemInOffHand(null);
        }
        this.equipment.hands(body, gun, null);
    }

    private static boolean emptyHand(LivingEntity body) {
        EntityEquipment hands = body.getEquipment();
        return hands == null || hands.getItemInMainHand() == null
                || hands.getItemInMainHand().getType().isAir();
    }

    private void keepHuman(LivingEntity body) {
        HumanMotor.prepare(body);
        standUnlocked(body);
        body.setFireTicks(0);
        if (body instanceof Player player) {
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.setExhaustion(0f);
        }
    }

    private void standUnlocked(LivingEntity body) {
        if (body instanceof Player player) {
            player.setSneaking(false);
            return;
        }
        if (!(body instanceof Mannequin mannequin)) {
            return;
        }
        try {
            mannequin.setPose(Pose.STANDING, false);
        } catch (IllegalArgumentException ignored) {
            try {
                mannequin.setPose(Pose.STANDING);
            } catch (Exception ignoredToo) {
            }
        }
    }

    private void applyLook(LivingEntity body, CivilianNpc npc) {
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
            if (body instanceof Mannequin mannequin) {
                mannequin.setBodyYaw(npc.bodyYaw());
            }
        } catch (Exception ignored) {
        }
    }

    private Player aimerOn(LivingEntity body) {
        double range = plugin.getConfig().getDouble("civilian.aim-range", 52);
        Player best = null;
        double bestDot = 0.72;
        for (Player player : body.getWorld().getPlayers()) {
            if (!NpcBodies.realPlayer(player) || player.getWorld() != body.getWorld()) {
                continue;
            }
            if (player.getLocation().distanceSquared(body.getLocation()) > range * range) {
                continue;
            }
            if (!holdingGun(player)) {
                continue;
            }
            Location eye = player.getEyeLocation();
            Vector dir = eye.getDirection();
            if (dir.lengthSquared() < 1.0e-6) {
                continue;
            }
            dir.normalize();
            Vector to = body.getEyeLocation().toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist < 1.0e-6) {
                continue;
            }
            to.multiply(1.0 / dist);
            double dot = dir.dot(to);
            boolean ads = playerAiming(player);
            double need = ads ? 0.74 : 0.88;
            if (dot < need) {
                continue;
            }
            if (dot < bestDot && best != null) {
                continue;
            }
            bestDot = dot;
            best = player;
        }
        return best;
    }

    private Player nearest(LivingEntity body, double range) {
        Player best = null;
        double bestD = range * range;
        for (Player player : body.getWorld().getPlayers()) {
            if (!NpcBodies.realPlayer(player)) {
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

    private Player armedNear(LivingEntity body, double range) {
        Player best = null;
        double bestD = range * range;
        for (Player player : body.getWorld().getPlayers()) {
            if (!NpcBodies.realPlayer(player) || !holdingGun(player)) {
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

    private Player shooterNear(LivingEntity body, double range) {
        Player best = null;
        double bestD = range * range;
        for (Player player : body.getWorld().getPlayers()) {
            if (!NpcBodies.realPlayer(player)) {
                continue;
            }
            if (!player.getScoreboardTags().contains("pgm_fire")) {
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

    private boolean playerAiming(Player player) {
        if (player.getScoreboardTags().contains("pgm_aim")) {
            return true;
        }
        PotionEffect slowness = player.getPotionEffect(PotionEffectType.SLOWNESS);
        return slowness != null && slowness.getAmplifier() >= 3 && holdingGun(player);
    }

    private boolean holdingGun(Player player) {
        Plugin warz = Bukkit.getPluginManager().getPlugin("WarzPlugin");
        if (warz instanceof WarzPlugin plugin && plugin.items() != null) {
            return plugin.items().isGunItem(player.getInventory().getItemInMainHand());
        }
        return !player.getInventory().getItemInMainHand().getType().isAir();
    }
}
