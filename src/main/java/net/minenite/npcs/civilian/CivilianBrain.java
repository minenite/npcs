package net.minenite.npcs.civilian;

import io.papermc.paper.entity.LookAnchor;
import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.chat.ConversationDirector;
import net.minenite.npcs.chat.LlmTalk;
import net.minenite.npcs.cognition.Cognition;
import net.minenite.npcs.cognition.DriveSet;
import net.minenite.npcs.cognition.Episode;
import net.minenite.npcs.cognition.Groups;
import net.minenite.npcs.cognition.Intention;
import net.minenite.npcs.cognition.NpcFire;
import net.minenite.npcs.cognition.Perception;
import net.minenite.npcs.cognition.Places;
import net.minenite.npcs.cognition.SoundWorld;
import net.minenite.npcs.cognition.TalkWhy;
import net.minenite.npcs.cognition.Utility;
import net.minenite.npcs.cognition.WorldCue;
import net.minenite.npcs.mind.EnvironmentSense;
import net.minenite.npcs.mind.Mood;
import net.minenite.npcs.nav.HumanNav;
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

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Full person loop: notice, draw, ADS, circle, holster, flinch, cover, inspect.
 * Aim is slowness IV + gun_pose to every client — never locked sneak.
 */
public final class CivilianBrain {
    private final NpcsPlugin plugin;
    private final LlmTalk talk;
    private final GunPoseBridge poses;
    private final EquipmentPackets equipment;
    private final ConversationDirector social;
    private final Supplier<Collection<CivilianNpc>> roster;
    private final SoundWorld sounds;

    public CivilianBrain(NpcsPlugin plugin, LlmTalk talk, GunPoseBridge poses, EquipmentPackets equipment,
                         ConversationDirector social, Supplier<Collection<CivilianNpc>> roster, SoundWorld sounds) {
        this.plugin = plugin;
        this.talk = talk;
        this.poses = poses;
        this.equipment = equipment;
        this.social = social;
        this.roster = roster;
        this.sounds = sounds;
    }

    public void tick(CivilianNpc npc, LivingEntity body) {
        keepHuman(body);
        Cognition cog = npc.cog();
        if (cog.fireCool > 0) {
            cog.fireCool--;
        }
        boolean aiming = npc.state() == CivilianNpc.State.AIM || npc.state() == CivilianNpc.State.CIRCLE;
        if (npc.dueEquip() || emptyHand(body)) {
            holdPistol(body, npc, aiming);
        }
        if (npc.duePoseRefresh()) {
            poses.refresh();
        }

        Player aimer = Perception.visibleAimer(npc, body, plugin.getConfig().getDouble("civilian.aim-range", 52));
        Player near = nearest(body, 18);
        Player armed = armedNear(body, 20);
        Player shooter = shooterNear(body, 28);
        boolean corpse = EnvironmentSense.corpseNear(body);
        SoundWorld.Pulse pulse = sounds == null ? null : sounds.hear(body.getLocation(), cog.traits);
        double nearestPlayer = Double.MAX_VALUE;
        for (Player p : body.getWorld().getPlayers()) {
            if (NpcBodies.realPlayer(p)) {
                nearestPlayer = Math.min(nearestPlayer, p.getLocation().distanceSquared(body.getLocation()));
            }
        }
        cog.offscreen = nearestPlayer > 96 * 96;
        double company = near == null ? 0 : 1;
        double trusted = 0;
        if (near != null && cog.bonds.containsKey(near.getUniqueId())
                && cog.bonds.get(near.getUniqueId()).trust > 0.2) {
            trusted = 1;
        }
        cog.drives.tick(cog.traits, npc.walking(), npc.state() == CivilianNpc.State.STAND
                        || npc.state() == CivilianNpc.State.SCAN || npc.state() == CivilianNpc.State.WATCH,
                aimer != null ? 0.8 : (pulse != null ? 0.25 : 0),
                company, trusted, body.getWorld().getTime() > 13000, body.getHealth() < 10);
        if (cog.offscreen) {
            WorldCue far = Perception.cue(npc, body, near, armed, aimer, corpse, pulse, roster.get());
            if (cog.due(false)) {
                Utility.pick(cog, far);
            }
            net.minenite.npcs.cognition.Offscreen.tick(npc, body);
            poseGun(npc, body, true, false, false);
            return;
        }

        Groups.tick(npc, roster.get());

        if (pulse != null && System.currentTimeMillis() - cog.lastSoundAt > 400) {
            cog.lastSound = pulse.kind.name().toLowerCase();
            cog.lastSoundConf = SoundWorld.confidence(body.getLocation(), pulse,
                    Places.indoors(body.getLocation()), cog.traits.hearing);
            cog.lastSoundGuess = SoundWorld.guessed(body.getLocation(), pulse, cog.traits.hearing);
            cog.lastSoundAt = pulse.atMs;
            cog.listenLeft = 8 + ThreadLocalRandom.current().nextInt(10);
            cog.attend(pulse.source, "sound:" + cog.lastSound, 0.55, 40);
            if (npc.state() != CivilianNpc.State.AIM && npc.state() != CivilianNpc.State.DRAW) {
                npc.setState(CivilianNpc.State.SCAN);
            }
        }
        if (cog.listenLeft > 0) {
            cog.listenLeft--;
            HumanMotor.plant(body);
            if (cog.lastSoundGuess != null) {
                npc.lookToward(body.getEyeLocation(), cog.lastSoundGuess.clone().add(0, 1.4, 0), 0.18f, 0.06f);
            }
            poseGun(npc, body, true, false, false);
            applyLook(body, npc);
            if (cog.listenLeft > 0 && aimer == null) {
                micro(npc, body, near);
                return;
            }
        }

        if (near != null && Perception.sees(npc, body, near)) {
            if (cog.violated(near.getName() + " is leaving")) {
                cog.drives.suspicion = DriveSet.clamp(cog.drives.suspicion + 0.08);
                cog.drives.curiosity = DriveSet.clamp(cog.drives.curiosity + 0.05);
            }
            cog.saw(near.getUniqueId(), near.getName(), near.getLocation(),
                    Places.at(near.getLocation()), near.getLocation().getYaw(), 0.82);
            cog.attend(near.getUniqueId(), near.getName(), 0.4, 30);
        } else if (near != null) {
            cog.lost(near.getUniqueId());
            cog.expect(near.getName() + " is leaving", 8_000L);
        }

        WorldCue cue = Perception.cue(npc, body, near, armed, aimer, corpse, pulse, roster.get());
        boolean urgent = aimer != null || (pulse != null && pulse.kind == SoundWorld.Kind.GUN);
        if (cog.due(urgent)) {
            Utility.pick(cog, cue);
            cog.stickAction(cog.intention.name());
            if (!urgent && ThreadLocalRandom.current().nextInt(8) == 0
                    && plugin.getConfig().getBoolean("cognition.slow-llm", true)) {
                net.minenite.npcs.cognition.SlowThink.maybe(npc, plugin.ollama(), null);
            }
        }

        if (shooter != null && npc.state() != CivilianNpc.State.AIM
                && npc.state() != CivilianNpc.State.DRAW
                && npc.state() != CivilianNpc.State.CIRCLE
                && npc.state() != CivilianNpc.State.FLINCH) {
            onGunshot(npc, body, shooter);
        }

        if (aimer != null) {
            onAimedAt(npc, body, aimer);
            applyLook(body, npc);
            micro(npc, body, aimer);
            return;
        }

        npc.tickAimHold();
        if ((npc.state() == CivilianNpc.State.AIM || npc.state() == CivilianNpc.State.CIRCLE
                || npc.state() == CivilianNpc.State.DRAW) && npc.aimHold() <= 0) {
            beginHolster(npc, body);
        }

        enact(npc, body, near, armed, cue);
        space(npc, body, near, armed);
        micro(npc, body, near);
        applyLook(body, npc);
    }

    private void enact(CivilianNpc npc, LivingEntity body, Player near, Player armed, WorldCue cue) {
        runState(npc, body, near, armed);
        Intention want = npc.cog().intention;
        if (npc.state() == CivilianNpc.State.AIM || npc.state() == CivilianNpc.State.DRAW
                || npc.state() == CivilianNpc.State.FLINCH || npc.state() == CivilianNpc.State.CIRCLE) {
            return;
        }
        switch (want) {
            case ESCAPE, SURVIVE -> {
                if (npc.state() != CivilianNpc.State.FLEE) {
                    npc.setFleeLeft(40 + ThreadLocalRandom.current().nextInt(40));
                    startWalk(npc, body, true);
                    npc.mind().did("ran because " + npc.cog().plan.why);
                }
            }
            case HIDE -> hide(npc, body, near);
            case INVESTIGATE, LOOK_FOR_RESOURCE, SEARCH, PATROL, TRAVEL -> {
                Location dest = investigateTarget(npc, body);
                if (Places.afford(body.getLocation()).equals("road") && ThreadLocalRandom.current().nextBoolean()) {
                    dest = body.getLocation().clone().add(
                            body.getLocation().getDirection().setY(0).normalize().multiply(8));
                }
                go(npc, body, dest);
            }
            case OBSERVE, LISTEN, WAIT, SILENCE, WATCH_ENTRANCE -> {
                if (npc.state() != CivilianNpc.State.WATCH && npc.state() != CivilianNpc.State.SCAN) {
                    npc.setState(CivilianNpc.State.SCAN);
                    npc.setIdleLeft(20 + ThreadLocalRandom.current().nextInt(25));
                }
            }
            case APPROACH, SEEK_COMPANY, FOLLOW, HELP_PERSON, WARN_PERSON, TRADE, ASK_FOR_HELP -> {
                Player focus = near != null ? near : armed;
                if (focus != null) {
                    go(npc, body, focus.getLocation());
                    npc.setWatching(focus.getUniqueId());
                }
            }
            case AVOID -> {
                if (armed != null) {
                    Location away = body.getLocation().clone().subtract(
                            armed.getLocation().toVector().subtract(body.getLocation().toVector()).normalize().multiply(6));
                    go(npc, body, away);
                }
            }
            case SEEK_SHELTER, RETURN_HOME -> {
                Location dest = want == Intention.RETURN_HOME
                        ? new Location(body.getWorld(), npc.cog().life.homeX, body.getY(), npc.cog().life.homeZ)
                        : EnvironmentSense.shelterNear(body.getLocation());
                if (dest != null) {
                    npc.setState(CivilianNpc.State.SHELTER);
                    go(npc, body, dest);
                    npc.setState(CivilianNpc.State.SHELTER);
                }
            }
            case REST -> {
                npc.setState(CivilianNpc.State.STAND);
                HumanMotor.plant(body);
            }
            case MOURN, CHECK_CORPSE, LOOT -> {
                npc.setState(CivilianNpc.State.MOURN);
                npc.setMournLeft(40);
            }
            case DEFEND_SELF, INTIMIDATE -> {
                if (armed != null) {
                    npc.setAimedBy(armed.getUniqueId());
                }
            }
            case LOOK_FOR_FRIEND -> {
                Location guess = null;
                for (var seen : npc.cog().lastSeen.values()) {
                    if (!seen.stale()) {
                        guess = seen.guess(body.getWorld());
                        break;
                    }
                }
                if (guess != null) {
                    go(npc, body, guess);
                }
            }
            case SEARCH_BUILDING -> go(npc, body, body.getLocation().clone().add(
                    (Math.random() - 0.5) * 6, 0, (Math.random() - 0.5) * 6));
            default -> {
            }
        }
    }

    private void runState(CivilianNpc npc, LivingEntity body, Player near, Player armed) {
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
                if (npc.route() != null) {
                    double speed = plugin.getConfig().getDouble("civilian.walk-speed", 0.20)
                            * npc.personality().walkMul();
                    if (HumanNav.follow(body, npc, npc.route(), speed)) {
                        npc.setRoute(null);
                        npc.setState(CivilianNpc.State.STAND);
                    }
                    poseGun(npc, body, true, false, false);
                    footstep(body, npc);
                } else {
                    maybePause(npc, body, near, armed);
                    if (npc.state() == CivilianNpc.State.WALK) {
                        step(npc, body, false);
                        poseGun(npc, body, true, false, false);
                        footstep(body, npc);
                    }
                }
            }
            case SCAN -> scan(npc, body, near);
            case STAND -> {
            }
            case TALK -> converse(npc, body);
            case SHELTER -> {
                if (npc.route() != null) {
                    HumanNav.follow(body, npc, npc.route(), 0.22);
                } else {
                    step(npc, body, false);
                }
                poseGun(npc, body, true, false, false);
                if (!npc.walking() && (npc.route() == null || npc.route().i >= npc.route().points.size())) {
                    npc.setState(CivilianNpc.State.WATCH);
                    npc.setIdleLeft(70);
                    npc.cog().plan.step++;
                    npc.mind().did("got under a roof");
                }
            }
            case FOLLOW -> follow(npc, body);
            case MOURN -> mourn(npc, body, near);
        }
    }

    private void go(CivilianNpc npc, LivingEntity body, Location dest) {
        if (dest == null) {
            return;
        }
        if (npc.route() == null || npc.stuck(body.getLocation()) || npc.route().i >= npc.route().points.size()) {
            HumanNav.Route route = HumanNav.to(body.getLocation(), dest, npc.cog());
            npc.setRoute(route);
            if (route == null) {
                WanderEngine.planToward(npc, body.getLocation(), dest, 0.20);
            } else {
                npc.setState(CivilianNpc.State.WALK);
            }
        }
    }

    private Location investigateTarget(CivilianNpc npc, LivingEntity body) {
        if (npc.cog().lastSoundGuess != null && System.currentTimeMillis() - npc.cog().lastSoundAt < 20_000L) {
            return npc.cog().lastSoundGuess;
        }
        for (var seen : npc.cog().lastSeen.values()) {
            if (!seen.stale()) {
                Location g = seen.guess(body.getWorld());
                if (g != null) {
                    return g;
                }
            }
        }
        return body.getLocation().clone().add((Math.random() - 0.5) * 10, 0, (Math.random() - 0.5) * 10);
    }

    private void hide(CivilianNpc npc, LivingEntity body, Player from) {
        Location cover = Places.coverFrom(body.getLocation(), from == null ? null : from.getLocation());
        if (cover != null) {
            go(npc, body, cover);
            npc.setState(CivilianNpc.State.COVER);
            npc.setCoverLeft(50);
            npc.mind().did("moved to cover");
        } else {
            npc.setState(CivilianNpc.State.COVER);
            npc.setCoverLeft(30);
        }
    }

    private void space(CivilianNpc npc, LivingEntity body, Player near, Player armed) {
        Player focus = armed != null ? armed : near;
        if (focus == null) {
            return;
        }
        double want = npc.cog().traits.personalSpace;
        if (armed != null) {
            want += 2.4;
        }
        var bond = npc.cog().bonds.get(focus.getUniqueId());
        if (bond != null && bond.trust > 0.4) {
            want *= 0.65;
        }
        double d = body.getLocation().distance(focus.getLocation());
        if (d < want && npc.state() != CivilianNpc.State.AIM) {
            Location back = body.getLocation().clone().subtract(
                    focus.getLocation().toVector().subtract(body.getLocation().toVector()).setY(0).normalize().multiply(1.2));
            HumanMotor.walkToward(body, npc, back, 0.12, true);
            npc.cog().spaceTicks++;
        }
    }

    private void micro(CivilianNpc npc, LivingEntity body, Player near) {
        Cognition cog = npc.cog();
        if (ThreadLocalRandom.current().nextInt((int) (40 / Math.max(0.2, cog.traits.fidget))) == 0) {
            npc.idleGlance();
        }
        if (ThreadLocalRandom.current().nextInt(90) == 0) {
            cog.glanceBehind = 8;
        }
        if (cog.glanceBehind > 0) {
            cog.glanceBehind--;
            npc.lookYaw();
            body.setRotation(npc.bodyYaw() + 140, npc.lookPitch());
        }
        if (near != null && armedGun(near) && ThreadLocalRandom.current().nextInt(25) == 0) {
            npc.lookToward(body.getEyeLocation(), near.getEyeLocation().clone().add(0, -0.4, 0), 0.3f, 0.05f);
        }
    }

    private static boolean armedGun(Player player) {
        return player.getScoreboardTags().contains("pgm_gun")
                || !player.getInventory().getItemInMainHand().getType().isAir();
    }

    private void onAimedAt(CivilianNpc npc, LivingEntity body, Player aimer) {
        boolean ads = playerAiming(aimer);
        Cognition cog = npc.cog();
        cog.attend(aimer.getUniqueId(), aimer.getName(), 0.95, 50);
        npc.setAimedBy(aimer.getUniqueId());
        npc.addNotice();
        int need = Perception.noticeDelay(npc, aimer, ads);
        npc.lookToward(body.getEyeLocation(), aimer.getEyeLocation(), ads ? 0.28f : 0.14f, ads ? 0.08f : 0.04f);
        if (npc.noticeTicks() < need) {
            if (npc.noticeTicks() == 1) {
                social.endFor(npc);
                cog.whyTalk = TalkWhy.NONE;
            }
            poseGun(npc, body, true, false, false);
            return;
        }
        NpcFire.Choice choice = NpcFire.choose(cog, ads ? 1 : 0.6, NpcFire.los(body, aimer));
        Episode ep = new Episode();
        ep.at = System.currentTimeMillis();
        ep.who = aimer.getName();
        ep.whoId = aimer.getUniqueId();
        ep.what = ads ? "aimed a gun at me" : "had a gun on me";
        ep.where = Places.at(body.getLocation());
        ep.x = body.getX();
        ep.z = body.getZ();
        ep.world = body.getWorld().getName();
        ep.intensity = ads ? 0.8 : 0.5;
        ep.certainty = 0.85;
        ep.importance = ads ? 0.8 : 0.5;
        if (npc.noticeTicks() == need) {
            cog.remember(ep);
            npc.mind().did(ep.what + " — " + aimer.getName());
        }
        switch (choice) {
            case RUN -> {
                npc.setFleeLeft(45);
                startWalk(npc, body, true);
            }
            case COVER -> hide(npc, body, aimer);
            case BACK -> backOff(npc, body, aimer);
            case FREEZE -> {
                HumanMotor.plant(body);
                poseGun(npc, body, true, false, false);
            }
            case WARN_SHOT, SHOOT -> {
                snapAim(npc, body, aimer);
            }
            case THREATEN, DRAW, CALL -> snapAim(npc, body, aimer);
        }
        if (!npc.aimSpoken() && (choice == NpcFire.Choice.THREATEN || choice == NpcFire.Choice.DRAW
                || choice == NpcFire.Choice.BACK) && npc.canTalk()) {
            cog.whyTalk = choice == NpcFire.Choice.THREATEN ? TalkWhy.THREATEN : TalkWhy.WARN;
            warnAimer(npc, body, aimer);
        }
    }

    private void snapAim(CivilianNpc npc, LivingEntity body, Player aimer) {
        HumanMotor.plant(body);
        npc.clearWalk();
        if (npc.state() != CivilianNpc.State.AIM && npc.state() != CivilianNpc.State.CIRCLE) {
            npc.setState(CivilianNpc.State.AIM);
            npc.setDrawLeft(0);
            npc.cog().fireCool = Math.max(npc.cog().fireCool,
                    8 + (int) ((1.0 - npc.cog().traits.reaction) * 14));
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
        social.endFor(npc);
        npc.mind().did("drew on " + aimer.getName());
        npc.mind().met(aimer.getUniqueId(), aimer.getName(), -2, "aimed a gun at me");
        npc.mind().feel(npc.personality().standsGround() ? Mood.ANGRY : Mood.AFRAID, 40);
        if (talk.street() != null) {
            talk.street().mark(aimer.getUniqueId(), aimer.getName(), -2,
                    aimer.getName() + " aimed at " + npc.name(), body.getLocation());
        }
        talk.aimedAt(npc, aimer);
        if (!npc.personality().standsGround() && ThreadLocalRandom.current().nextInt(3) != 0) {
            backOff(npc, body, aimer);
            npc.mind().did("backed off from " + aimer.getName());
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
        maybeFire(npc, body);
    }

    private void maybeFire(CivilianNpc npc, LivingEntity body) {
        Cognition cog = npc.cog();
        if (cog.fireCool > 0 || npc.aimedBy() == null) {
            return;
        }
        Player still = Bukkit.getPlayer(npc.aimedBy());
        if (!NpcBodies.realPlayer(still)) {
            return;
        }
        NpcFire.Choice choice = NpcFire.choose(cog, 1.0, NpcFire.los(body, still));
        if (choice != NpcFire.Choice.SHOOT && choice != NpcFire.Choice.WARN_SHOT) {
            return;
        }
        NpcFire.fire(body, npc, still.getEyeLocation(), choice == NpcFire.Choice.WARN_SHOT);
        if (sounds != null) {
            sounds.emit(body.getLocation(), SoundWorld.Kind.GUN, 1.0, npc.id());
        }
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
        Player was = npc.aimedBy() == null ? null : Bukkit.getPlayer(npc.aimedBy());
        if (NpcBodies.realPlayer(was) && npc.aimSpoken()) {
            npc.mind().feel(Mood.RELIEVED, 25);
            npc.mind().did(was.getName() + " lowered it");
            npc.mind().met(was.getUniqueId(), was.getName(), 1, "lowered the gun");
            if (npc.canTalk()) {
                talk.relief(npc, was);
            }
        }
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
        if (armed != null && (npc.personality().standsGround() || npc.mind().trust(armed.getUniqueId()) < -1
                || (talk.street() != null && talk.street().trust(armed.getUniqueId()) < -1))) {
            npc.setWatching(armed.getUniqueId());
            npc.setState(CivilianNpc.State.WATCH);
            npc.setIdleLeft(35 + ThreadLocalRandom.current().nextInt(40));
            npc.mind().saw(armed.getName() + " is armed");
            return;
        }
        if (seekShelter(npc, body)) {
            return;
        }
        if (EnvironmentSense.corpseNear(body) && npc.canTalk() && ThreadLocalRandom.current().nextInt(40) == 0) {
            npc.clearWalk();
            npc.setState(CivilianNpc.State.MOURN);
            npc.setMournLeft(45 + ThreadLocalRandom.current().nextInt(25));
            npc.mind().feel(Mood.GRIEF, 30);
            npc.mind().saw("a body on the ground");
            talk.corpse(npc, near);
            return;
        }
        if (near != null && ThreadLocalRandom.current().nextInt(55) == 0) {
            npc.setWatching(near.getUniqueId());
            npc.setState(CivilianNpc.State.WATCH);
            npc.setIdleLeft(25 + ThreadLocalRandom.current().nextInt(35));
            boolean known = npc.mind().knows(near.getUniqueId());
            if (npc.canTalk() && (known || npc.personality().chatty())
                    && npc.mind().trust(near.getUniqueId()) >= -1
                    && ThreadLocalRandom.current().nextInt(known ? 2 : 4) == 0) {
                talk.ambient(npc, near);
            }
            return;
        }
        npc.idleGlance();
        if (npc.idleLeft() > 0) {
            return;
        }
        if (npc.mind().mood() == Mood.LONELY && ThreadLocalRandom.current().nextBoolean()) {
            npc.setIdleLeft(30);
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
        if (roll == 3 && body.getWorld().hasStorm() && npc.canTalk()) {
            talk.weather(npc, near);
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
        social.endFor(npc);
        npc.mind().saw("shots from " + shooter.getName());
        npc.mind().feel(Mood.AFRAID, 20);
        if (talk.street() != null) {
            talk.street().hear(body.getLocation(), "shots near " + npc.name() + " — " + shooter.getName());
        }
        if (npc.personality().panicsAtShots() && ThreadLocalRandom.current().nextBoolean()) {
            npc.setFleeLeft(50 + ThreadLocalRandom.current().nextInt(40));
            startWalk(npc, body, true);
            npc.mind().did("ran from shots");
            return;
        }
        npc.setState(CivilianNpc.State.FLINCH);
        npc.setFlinchLeft(8 + ThreadLocalRandom.current().nextInt(8));
        if (npc.canTalk() && ThreadLocalRandom.current().nextInt(3) == 0) {
            talk.shots(npc, shooter);
        }
    }

    public void hurt(CivilianNpc npc, LivingEntity body, Player from) {
        npc.setState(CivilianNpc.State.FLINCH);
        npc.setFlinchLeft(10 + ThreadLocalRandom.current().nextInt(8));
        social.endFor(npc);
        if (from != null) {
            npc.setAimedBy(from.getUniqueId());
            npc.remember(from.getUniqueId());
            npc.mind().did("got hit by " + from.getName());
            npc.mind().met(from.getUniqueId(), from.getName(), -3, "shot me");
            npc.mind().feel(Mood.ANGRY, 40);
            if (talk.street() != null) {
                talk.street().mark(from.getUniqueId(), from.getName(), -3,
                        from.getName() + " shot " + npc.name(), body.getLocation());
            }
            if (npc.canTalk()) {
                talk.hurt(npc, from);
            }
        }
        poseGun(npc, body, true, true, false);
    }

    private void converse(CivilianNpc npc, LivingEntity body) {
        HumanMotor.plant(body);
        poseGun(npc, body, true, false, false);
        npc.decTalk();
        CivilianNpc other = find(npc.talkingTo());
        if (other != null && other.body() != null) {
            npc.lookToward(body.getEyeLocation(), other.body().getEyeLocation(), 0.22f, 0.12f);
        } else {
            npc.idleGlance();
        }
        if (npc.talkLeft() <= 0) {
            npc.setTalkingTo(null);
            npc.setState(CivilianNpc.State.STAND);
            npc.setIdleLeft(20);
        }
    }

    private void follow(CivilianNpc npc, LivingEntity body) {
        npc.decFollow();
        poseGun(npc, body, true, false, false);
        CivilianNpc lead = find(npc.following());
        if (lead == null || lead.body() == null || npc.followLeft() <= 0) {
            npc.setFollowing(null);
            npc.clearWalk();
            npc.setState(CivilianNpc.State.STAND);
            npc.setIdleLeft(20 + ThreadLocalRandom.current().nextInt(30));
            return;
        }
        Location dest = lead.body().getLocation();
        npc.lookToward(body.getEyeLocation(), lead.body().getEyeLocation(), 0.18f, 0.10f);
        if (body.getLocation().distanceSquared(dest) > 3.4 * 3.4) {
            if (!npc.walking() || npc.stuck(body.getLocation())) {
                WanderEngine.planToward(npc, body.getLocation(), dest, 0.21);
                npc.setState(CivilianNpc.State.FOLLOW);
            }
            step(npc, body, false);
        } else {
            HumanMotor.plant(body);
            npc.clearWalk();
        }
    }

    private void mourn(CivilianNpc npc, LivingEntity body, Player near) {
        npc.decMourn();
        HumanMotor.plant(body);
        poseGun(npc, body, true, false, false);
        npc.lookToward(body.getEyeLocation(), body.getLocation().clone().add(0, 0.2, 0), 0.08f, 0.04f);
        if (npc.mournLeft() <= 0) {
            npc.setState(CivilianNpc.State.WARY);
            npc.setWaryLeft(40);
        }
    }

    private boolean seekShelter(CivilianNpc npc, LivingEntity body) {
        if (!body.getWorld().hasStorm() && !body.getWorld().isThundering()) {
            return false;
        }
        if (EnvironmentSense.roofed(body.getLocation())) {
            return false;
        }
        if (ThreadLocalRandom.current().nextInt(18) != 0) {
            return false;
        }
        Location roof = EnvironmentSense.shelterNear(body.getLocation());
        if (roof == null) {
            return false;
        }
        npc.setState(CivilianNpc.State.SHELTER);
        WanderEngine.planToward(npc, body.getLocation(), roof, 0.22);
        npc.setState(CivilianNpc.State.SHELTER);
        npc.mind().did("looked for a roof in the rain");
        npc.mind().feel(Mood.TIRED, 20);
        return true;
    }

    private CivilianNpc find(UUID id) {
        if (id == null) {
            return null;
        }
        for (CivilianNpc npc : roster.get()) {
            if (npc.id().equals(id)) {
                return npc;
            }
        }
        return null;
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
