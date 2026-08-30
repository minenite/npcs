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
    public enum Mood { IDLE, WALK, AIMED, FLEE }

    private final UUID id;
    private final String name;
    private final Personality personality;
    private final SkinService.Textures textures;
    private final List<ItemStack> loot = new ArrayList<>();
    private final ItemStack gun;
    private final ItemStack offhand;
    private UUID entityId;
    private Mood mood = Mood.IDLE;
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
    private int glanceTicks;
    private float glanceYaw;

    public CivilianNpc(UUID id, String name, Personality personality, SkinService.Textures textures,
                       ItemStack gun, ItemStack offhand, List<ItemStack> extras) {
        this.id = id;
        this.name = name;
        this.personality = personality;
        this.textures = textures;
        this.gun = gun;
        this.offhand = offhand;
        if (gun != null) {
            loot.add(gun.clone());
        }
        if (offhand != null) {
            loot.add(offhand.clone());
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
        this.lookPitch = body.getLocation().getPitch();
    }

    public Mood mood() {
        return mood;
    }

    public void setMood(Mood mood) {
        this.mood = mood;
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
        return offhand;
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

    public long lastTalkAt() {
        return lastTalkAt;
    }

    public UUID aimedBy() {
        return aimedBy;
    }

    public void setAimedBy(UUID id) {
        if (id == null) {
            this.aimedBy = null;
            this.aimHold = 0;
            this.aimSpoken = false;
            return;
        }
        if (!id.equals(this.aimedBy)) {
            this.aimedBy = id;
            this.aimSpoken = false;
        }
        this.aimHold = 50;
    }

    public int aimHold() {
        return aimHold;
    }

    public void tickAimHold() {
        if (aimHold > 0) {
            aimHold--;
        }
        if (aimHold <= 0) {
            aimedBy = null;
            aimSpoken = false;
        }
    }

    public Location walkTo() {
        return walkTo;
    }

    public void beginWalk(Location from, Location via, Location to, int ticks) {
        this.walkFrom = from.clone();
        this.walkVia = via.clone();
        this.walkTo = to.clone();
        this.walkTicks = 0;
        this.walkMax = Math.max(25, ticks);
        this.mood = Mood.WALK;
    }

    public void clearWalk() {
        walkFrom = walkVia = walkTo = null;
        walkTicks = walkMax = 0;
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
            lookPitch = 0f;
        }
        if (walkTicks >= walkMax) {
            clearWalk();
            mood = Mood.IDLE;
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

    public void lookAt(Location from, Location target) {
        Vector delta = target.toVector().subtract(from.toVector());
        if (delta.lengthSquared() < 1.0e-6) {
            return;
        }
        lookYaw = yawOf(delta);
        double horiz = Math.sqrt(delta.getX() * delta.getX() + delta.getZ() * delta.getZ());
        lookPitch = (float) Math.toDegrees(-Math.atan2(delta.getY(), horiz));
        lookPitch = Math.max(-40f, Math.min(40f, lookPitch));
    }

    public void idleGlance() {
        glanceTicks--;
        if (glanceTicks <= 0) {
            glanceTicks = 30 + (int) (Math.random() * 70);
            glanceYaw = lookYaw + (float) ((Math.random() - 0.5) * 70.0);
        }
        lookYaw = lerpYaw(lookYaw, glanceYaw, 0.08f);
        lookPitch = lerp(lookPitch, (float) ((Math.random() - 0.5) * 6.0), 0.04f);
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
        float d = wrap(to - from);
        return from + d * t;
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
