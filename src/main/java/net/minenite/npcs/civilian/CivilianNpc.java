package net.minenite.npcs.civilian;

import net.minenite.npcs.skin.SkinService;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CivilianNpc {
    public enum State {
        STAND, SCAN, WALK, WATCH, WARY, DRAW, AIM, CIRCLE, HOLSTER,
        BACKPEDAL, FLEE, FLINCH, INSPECT, COVER
    }

    private final UUID id;
    private final String name;
    private final Personality personality;
    private final SkinService.Textures textures;
    private final List<ItemStack> loot = new ArrayList<>();
    private final ItemStack gun;
    private final ItemStack spareMag;
    private UUID entityId;
    private LivingEntity body;
    private boolean fakePlayer;
    private State state = State.STAND;
    private Location walkFrom;
    private Location walkVia;
    private Location walkTo;
    private int walkTicks;
    private int walkMax;
    private int idleLeft;
    private long lastTalkAt;
    private UUID aimedBy;
    private int aimHold;
    private boolean aimSpoken;
    private String lastLine;
    private boolean dead;
    private float lookYaw;
    private float lookPitch;
    private float bodyYaw;
    private int glanceTicks;
    private float glanceYaw;
    private int noticeTicks;
    private int waryLeft;
    private int fleeLeft;
    private int equipTicks;
    private int nextDecision;
    private UUID watching;
    private byte poseFlags;
    private int drawLeft;
    private int holsterLeft;
    private int flinchLeft;
    private int inspectLeft;
    private int coverLeft;
    private int circleLeft;
    private UUID rememberedAimer;
    private long rememberUntil;
    private int poseRefresh;
    private int walkAge;
    private int stuckTicks;
    private Location lastPos;
    private int turnLeft;
    private float turnYaw;

    public CivilianNpc(UUID id, String name, Personality personality, SkinService.Textures textures,
                       ItemStack gun, ItemStack spareMag, List<ItemStack> extras) {
        this.id = id;
        this.name = name;
        this.personality = personality;
        this.textures = textures;
        this.gun = gun;
        this.spareMag = spareMag;
        if (gun != null) {
            loot.add(gun.clone());
        }
        if (spareMag != null) {
            loot.add(spareMag.clone());
        }
        if (extras != null) {
            for (ItemStack extra : extras) {
                if (extra != null) {
                    loot.add(extra.clone());
                }
            }
        }
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Personality personality() {
        return personality;
    }

    public SkinService.Textures textures() {
        return textures;
    }

    public UUID entityId() {
        return entityId;
    }

    public boolean fakePlayer() {
        return fakePlayer;
    }

    public LivingEntity body() {
        return body != null && body.isValid() && !body.isDead() ? body : null;
    }

    public void bind(LivingEntity body, boolean fakePlayer) {
        this.entityId = body.getUniqueId();
        this.body = body;
        this.fakePlayer = fakePlayer;
        this.lookYaw = body.getLocation().getYaw();
        this.bodyYaw = this.lookYaw;
        this.lookPitch = 0f;
        this.idleLeft = 20 + (int) (Math.random() * 40);
        this.nextDecision = 15;
    }

    public State state() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public boolean alive() {
        return !dead;
    }

    public void markDead() {
        dead = true;
    }

    public ItemStack gun() {
        return gun;
    }

    public ItemStack offhand() {
        return spareMag;
    }

    public List<ItemStack> loot() {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : loot) {
            copy.add(stack.clone());
        }
        return copy;
    }

    public boolean canTalk() {
        return System.currentTimeMillis() - lastTalkAt >= 8000L;
    }

    public void markTalked(String line) {
        lastTalkAt = System.currentTimeMillis();
        if (line != null && !line.isBlank()) {
            lastLine = line;
        }
    }

    public String lastLine() {
        return lastLine;
    }

    public boolean aimSpoken() {
        return aimSpoken;
    }

    public void markAimSpoken() {
        aimSpoken = true;
    }

    public UUID aimedBy() {
        return aimedBy;
    }

    public void setAimedBy(UUID id) {
        if (id == null) {
            this.aimedBy = null;
            this.aimHold = 0;
            this.aimSpoken = false;
            this.noticeTicks = 0;
            return;
        }
        if (!id.equals(this.aimedBy)) {
            this.aimedBy = id;
            this.aimSpoken = false;
            this.noticeTicks = remembered(id) ? personality.noticeDelayTicks() : 0;
        }
        remember(id);
        this.aimHold = 55;
    }

    public boolean remembered(UUID id) {
        return id != null && id.equals(rememberedAimer) && System.currentTimeMillis() < rememberUntil;
    }

    public void remember(UUID id) {
        this.rememberedAimer = id;
        this.rememberUntil = System.currentTimeMillis() + 45_000L;
    }

    public UUID rememberedAimer() {
        return System.currentTimeMillis() < rememberUntil ? rememberedAimer : null;
    }

    public int aimHold() {
        return aimHold;
    }

    public void tickAimHold() {
        if (aimHold > 0) {
            aimHold--;
        }
        if (aimHold <= 0 && aimedBy != null) {
            aimedBy = null;
            aimSpoken = false;
            noticeTicks = 0;
        }
    }

    public int noticeTicks() {
        return noticeTicks;
    }

    public void addNotice() {
        noticeTicks++;
    }

    public void clearNotice() {
        noticeTicks = 0;
    }

    public int waryLeft() {
        return waryLeft;
    }

    public void setWaryLeft(int ticks) {
        this.waryLeft = ticks;
    }

    public void decWary() {
        if (waryLeft > 0) {
            waryLeft--;
        }
    }

    public int fleeLeft() {
        return fleeLeft;
    }

    public void setFleeLeft(int ticks) {
        this.fleeLeft = ticks;
    }

    public void decFlee() {
        if (fleeLeft > 0) {
            fleeLeft--;
        }
    }

    public UUID watching() {
        return watching;
    }

    public void setWatching(UUID id) {
        this.watching = id;
    }

    public int nextDecision() {
        return nextDecision;
    }

    public void setNextDecision(int ticks) {
        this.nextDecision = ticks;
    }

    public void decDecision() {
        if (nextDecision > 0) {
            nextDecision--;
        }
    }

    public boolean dueEquip() {
        return ++equipTicks % 40 == 0;
    }

    public int walkAge() {
        return ++walkAge;
    }

    public boolean stuck(Location here) {
        if (lastPos == null || lastPos.getWorld() != here.getWorld()) {
            lastPos = here.clone();
            stuckTicks = 0;
            return false;
        }
        if (here.distanceSquared(lastPos) < 0.04) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastPos = here.clone();
        }
        return stuckTicks > 22;
    }

    public void clearStuck() {
        stuckTicks = 0;
        lastPos = null;
    }

    public void beginTurn(float yaw, int ticks) {
        this.turnYaw = yaw;
        this.turnLeft = Math.max(4, ticks);
    }

    public boolean turning() {
        if (turnLeft <= 0) {
            return false;
        }
        turnLeft--;
        bodyYaw = lerpYaw(bodyYaw, turnYaw, 0.28f);
        lookYaw = lerpYaw(lookYaw, turnYaw, 0.18f);
        return turnLeft > 0;
    }

    public void turnBody(float yaw, float rate) {
        bodyYaw = lerpYaw(bodyYaw, yaw, rate);
    }

    public boolean duePoseRefresh() {
        return ++poseRefresh % 40 == 0;
    }

    public byte poseFlags() {
        return poseFlags;
    }

    public void setPoseFlags(byte flags) {
        this.poseFlags = flags;
    }

    public int drawLeft() {
        return drawLeft;
    }

    public void setDrawLeft(int ticks) {
        this.drawLeft = ticks;
    }

    public void decDraw() {
        if (drawLeft > 0) {
            drawLeft--;
        }
    }

    public int holsterLeft() {
        return holsterLeft;
    }

    public void setHolsterLeft(int ticks) {
        this.holsterLeft = ticks;
    }

    public void decHolster() {
        if (holsterLeft > 0) {
            holsterLeft--;
        }
    }

    public int flinchLeft() {
        return flinchLeft;
    }

    public void setFlinchLeft(int ticks) {
        this.flinchLeft = ticks;
    }

    public void decFlinch() {
        if (flinchLeft > 0) {
            flinchLeft--;
        }
    }

    public int inspectLeft() {
        return inspectLeft;
    }

    public void setInspectLeft(int ticks) {
        this.inspectLeft = ticks;
    }

    public void decInspect() {
        if (inspectLeft > 0) {
            inspectLeft--;
        }
    }

    public int coverLeft() {
        return coverLeft;
    }

    public void setCoverLeft(int ticks) {
        this.coverLeft = ticks;
    }

    public void decCover() {
        if (coverLeft > 0) {
            coverLeft--;
        }
    }

    public int circleLeft() {
        return circleLeft;
    }

    public void setCircleLeft(int ticks) {
        this.circleLeft = ticks;
    }

    public void decCircle() {
        if (circleLeft > 0) {
            circleLeft--;
        }
    }

    public Location walkDest() {
        return walkTo;
    }

    public void beginWalk(Location from, Location via, Location to, int ticks) {
        this.walkFrom = from.clone();
        this.walkVia = via != null ? via.clone() : to.clone();
        this.walkTo = to.clone();
        this.walkTicks = 0;
        this.walkMax = Math.max(18, ticks);
        clearStuck();
        Vector delta = to.toVector().subtract(from.toVector());
        if (delta.lengthSquared() > 0.04) {
            float yaw = yawOf(delta);
            if (Math.abs(wrap(yaw - bodyYaw)) > 55f) {
                beginTurn(yaw, 8 + (int) (Math.abs(wrap(yaw - bodyYaw)) / 18f));
            }
        }
        if (state != State.CIRCLE && state != State.BACKPEDAL && state != State.FLEE) {
            this.state = State.WALK;
        }
    }

    public void clearWalk() {
        walkFrom = walkVia = walkTo = null;
        walkTicks = walkMax = 0;
    }

    public boolean walking() {
        return walkTo != null && (state == State.WALK || state == State.FLEE
                || state == State.BACKPEDAL || state == State.CIRCLE);
    }

    public Location stepWalk() {
        if (walkFrom == null || walkTo == null) {
            return null;
        }
        walkTicks++;
        double t = Math.min(1.0, walkTicks / (double) walkMax);
        Location a = walkFrom;
        Location b = walkVia != null ? walkVia : walkTo;
        Location c = walkTo;
        double x = quad(a.getX(), b.getX(), c.getX(), t);
        double z = quad(a.getZ(), b.getZ(), c.getZ(), t);
        double y = a.getY() + (c.getY() - a.getY()) * t;
        Location at = new Location(a.getWorld(), x, y, z);
        Vector ahead = new Vector(
                quad(a.getX(), b.getX(), c.getX(), Math.min(1.0, t + 0.04)) - x,
                0,
                quad(a.getZ(), b.getZ(), c.getZ(), Math.min(1.0, t + 0.04)) - z);
        if (ahead.lengthSquared() > 1.0e-6 && state != State.AIM && state != State.DRAW
                && state != State.CIRCLE && state != State.BACKPEDAL) {
            lookYaw = yawOf(ahead);
            bodyYaw = lerpYaw(bodyYaw, lookYaw, 0.22f);
            lookPitch = 0f;
        }
        if (walkTicks >= walkMax) {
            clearWalk();
            if (state == State.WALK) {
                state = State.STAND;
                idleLeft = 25 + (int) (Math.random() * 70);
            }
        }
        return at;
    }

    public int idleLeft() {
        return idleLeft;
    }

    public void setIdleLeft(int ticks) {
        this.idleLeft = ticks;
    }

    public void decIdle() {
        if (idleLeft > 0) {
            idleLeft--;
        }
    }

    public float lookYaw() {
        return lookYaw;
    }

    public float lookPitch() {
        return lookPitch;
    }

    public float bodyYaw() {
        return bodyYaw;
    }

    public void lookToward(Location from, Location target, float headRate, float bodyRate) {
        Vector delta = target.toVector().subtract(from.toVector());
        if (delta.lengthSquared() < 1.0e-6) {
            return;
        }
        float yaw = yawOf(delta);
        double horiz = Math.sqrt(delta.getX() * delta.getX() + delta.getZ() * delta.getZ());
        float pitch = (float) Math.toDegrees(-Math.atan2(delta.getY(), horiz));
        pitch = Math.max(-28f, Math.min(22f, pitch));
        float neck = personality.neckLimit();
        float headDelta = wrap(yaw - bodyYaw);
        if (Math.abs(headDelta) > neck) {
            yaw = bodyYaw + Math.copySign(neck, headDelta);
            bodyYaw = lerpYaw(bodyYaw, yawOf(delta), bodyRate * 1.6f);
        }
        lookYaw = lerpYaw(lookYaw, yaw, headRate);
        lookPitch = lerp(lookPitch, pitch, headRate);
        bodyYaw = lerpYaw(bodyYaw, yawOf(delta), bodyRate);
    }

    public void idleGlance() {
        glanceTicks--;
        if (glanceTicks <= 0) {
            glanceTicks = 30 + (int) (Math.random() * 70);
            glanceYaw = bodyYaw + (float) ((Math.random() - 0.5) * personality.neckLimit());
        }
        lookYaw = lerpYaw(lookYaw, glanceYaw, 0.07f);
        lookPitch = lerp(lookPitch, (float) ((Math.random() - 0.5) * 5.0), 0.04f);
        if (Math.abs(wrap(lookYaw - bodyYaw)) > personality.neckLimit() * 0.85f) {
            bodyYaw = lerpYaw(bodyYaw, lookYaw, 0.05f);
        }
    }

    public void aimSway() {
        lookYaw += (float) ((Math.random() - 0.5) * personality.sway());
        lookPitch += (float) ((Math.random() - 0.5) * personality.sway() * 0.6);
        lookPitch = Math.max(-26f, Math.min(20f, lookPitch));
    }

    public void inspectLook() {
        lookPitch = lerp(lookPitch, 28f, 0.12f);
        lookYaw = lerpYaw(lookYaw, bodyYaw + (float) ((Math.random() - 0.5) * 8.0), 0.08f);
    }

    public void flinchLook() {
        lookPitch = Math.min(18f, lookPitch + 6f);
        lookYaw += (float) ((Math.random() - 0.5) * 14.0);
    }

    private static double quad(double a, double b, double c, double t) {
        double u = 1.0 - t;
        return u * u * a + 2 * u * t * b + t * t * c;
    }

    private static float yawOf(Vector vector) {
        return (float) Math.toDegrees(Math.atan2(-vector.getX(), vector.getZ()));
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float lerpYaw(float from, float to, float t) {
        return from + wrap(to - from) * t;
    }

    private static float wrap(float yaw) {
        while (yaw > 180f) {
            yaw -= 360f;
        }
        while (yaw < -180f) {
            yaw += 360f;
        }
        return yaw;
    }
}
