package net.minenite.npcs.civilian;

import net.minenite.npcs.skin.SkinService;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CivilianNpc {
    public enum State {
        STAND, SCAN, WALK, WATCH, WARY, AIM, BACKPEDAL, FLEE
    }

    private final UUID id;
    private final String name;
    private final Personality personality;
    private final SkinService.Textures textures;
    private final List<ItemStack> loot = new ArrayList<>();
    private final ItemStack gun;
    private final ItemStack spareMag;
    private UUID entityId;
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

    public void bind(Mannequin body) {
        this.entityId = body.getUniqueId();
        this.lookYaw = body.getLocation().getYaw();
        this.bodyYaw = this.lookYaw;
        this.lookPitch = 0f;
        this.idleLeft = 30 + (int) (Math.random() * 50);
        this.nextDecision = 20;
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
            this.noticeTicks = 0;
        }
        this.aimHold = 40;
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
        return ++equipTicks % 20 == 0;
    }

    public byte poseFlags() {
        return poseFlags;
    }

    public void setPoseFlags(byte flags) {
        this.poseFlags = flags;
    }

    public void beginWalk(Location from, Location via, Location to, int ticks) {
        this.walkFrom = from.clone();
        this.walkVia = via.clone();
        this.walkTo = to.clone();
        this.walkTicks = 0;
        this.walkMax = Math.max(25, ticks);
        this.state = State.WALK;
    }

    public void clearWalk() {
        walkFrom = walkVia = walkTo = null;
        walkTicks = walkMax = 0;
    }

    public boolean walking() {
        return walkTo != null && (state == State.WALK || state == State.FLEE || state == State.BACKPEDAL);
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
        if (ahead.lengthSquared() > 1.0e-6) {
            lookYaw = yawOf(ahead);
            bodyYaw = lerpYaw(bodyYaw, lookYaw, 0.22f);
            lookPitch = 0f;
        }
        if (walkTicks >= walkMax) {
            clearWalk();
            if (state == State.WALK) {
                state = State.STAND;
                idleLeft = 35 + (int) (Math.random() * 90);
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
        pitch = Math.max(-35f, Math.min(28f, pitch));
        lookYaw = lerpYaw(lookYaw, yaw, headRate);
        lookPitch = lerp(lookPitch, pitch, headRate);
        bodyYaw = lerpYaw(bodyYaw, yaw, bodyRate);
    }

    public void idleGlance() {
        glanceTicks--;
        if (glanceTicks <= 0) {
            glanceTicks = 40 + (int) (Math.random() * 80);
            glanceYaw = bodyYaw + (float) ((Math.random() - 0.5) * 55.0);
        }
        lookYaw = lerpYaw(lookYaw, glanceYaw, 0.06f);
        lookPitch = lerp(lookPitch, (float) ((Math.random() - 0.5) * 4.0), 0.03f);
        if (Math.abs(wrap(lookYaw - bodyYaw)) > 55f) {
            bodyYaw = lerpYaw(bodyYaw, lookYaw, 0.04f);
        }
    }

    public void aimSway() {
        lookYaw += (float) ((Math.random() - 0.5) * 0.35);
        lookPitch += (float) ((Math.random() - 0.5) * 0.2);
        lookPitch = Math.max(-30f, Math.min(24f, lookPitch));
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
