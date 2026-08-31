package net.minenite.npcs.cognition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.civilian.CivilianNpc;
import net.minenite.npcs.civilian.Personality;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Living civilians persist. Death is permanent (graves).
 */
public final class PersistLives {
    public static final class Snap {
        public String id;
        public String name;
        public String personality;
        public String world;
        public double x, y, z;
        public float yaw;
        public double health;
        public DriveSet drives;
        public String intention;
        public String planWhy;
        public Life life;
        public String friend;
        public List<String> episodeRecall = new ArrayList<>();
        public List<BondSnap> bonds = new ArrayList<>();
        public boolean dead;
    }

    public static final class BondSnap {
        public String id;
        public String name;
        public double trust, liking, fearOf, perceivedDanger, familiarity, resentment, debt;
    }

    public static final class Disk {
        public List<Snap> lives = new ArrayList<>();
        public List<Snap> graves = new ArrayList<>();
    }

    private final NpcsPlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private Disk disk = new Disk();

    public PersistLives(NpcsPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("lives.json");
        load();
    }

    public void capture(Iterable<CivilianNpc> all) {
        Disk next = new Disk();
        next.graves.addAll(disk.graves);
        for (CivilianNpc npc : all) {
            Snap snap = snapOf(npc);
            if (!npc.alive()) {
                next.graves.add(snap);
            } else {
                next.lives.add(snap);
            }
        }
        disk = next;
        save();
    }

    public void bury(CivilianNpc npc) {
        Snap snap = snapOf(npc);
        snap.dead = true;
        disk.lives.removeIf(s -> npc.id().toString().equals(s.id));
        disk.graves.add(snap);
        save();
    }

    public List<Snap> pending() {
        return List.copyOf(disk.lives);
    }

    public boolean buried(String name) {
        for (Snap g : disk.graves) {
            if (g.name != null && g.name.equalsIgnoreCase(name) && g.dead) {
                return true;
            }
        }
        return false;
    }

    public Location location(Snap snap) {
        World world = snap.world == null ? null : Bukkit.getWorld(snap.world);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return null;
        }
        return new Location(world, snap.x, snap.y, snap.z, snap.yaw, 0);
    }

    public Personality personality(Snap snap) {
        try {
            return Personality.valueOf(snap.personality);
        } catch (Exception ignored) {
            return Personality.random();
        }
    }

    public UUID id(Snap snap) {
        try {
            return UUID.fromString(snap.id);
        } catch (Exception ignored) {
            return UUID.nameUUIDFromBytes(("npc:" + snap.name).getBytes());
        }
    }

    public void apply(CivilianNpc npc, Snap snap) {
        if (snap.drives != null) {
            npc.cog().drives.fear = snap.drives.fear;
            npc.cog().drives.stress = snap.drives.stress;
            npc.cog().drives.fatigue = snap.drives.fatigue;
            npc.cog().drives.hunger = snap.drives.hunger;
            npc.cog().drives.thirst = snap.drives.thirst;
            npc.cog().drives.pain = snap.drives.pain;
            npc.cog().drives.loneliness = snap.drives.loneliness;
            npc.cog().drives.boredom = snap.drives.boredom;
            npc.cog().drives.curiosity = snap.drives.curiosity;
            npc.cog().drives.suspicion = snap.drives.suspicion;
            npc.cog().drives.confidence = snap.drives.confidence;
            npc.cog().drives.aggression = snap.drives.aggression;
            npc.cog().drives.desperation = snap.drives.desperation;
            npc.cog().drives.attachment = snap.drives.attachment;
            npc.cog().drives.safety = snap.drives.safety;
            npc.cog().drives.urgency = snap.drives.urgency;
        }
        if (snap.life != null) {
            npc.cog().life.origin = snap.life.origin;
            npc.cog().life.occupation = snap.life.occupation;
            npc.cog().life.worry = snap.life.worry;
            npc.cog().life.want = snap.life.want;
            npc.cog().life.homeName = snap.life.homeName;
            npc.cog().life.homeX = snap.life.homeX;
            npc.cog().life.homeZ = snap.life.homeZ;
            npc.cog().life.usualArea = snap.life.usualArea;
            npc.cog().life.recent = snap.life.recent;
        }
        if (snap.intention != null) {
            try {
                npc.cog().intention = Intention.valueOf(snap.intention);
            } catch (Exception ignored) {
            }
        }
        if (snap.planWhy != null) {
            npc.cog().plan.why = snap.planWhy;
        }
        if (snap.bonds != null) {
            for (BondSnap bs : snap.bonds) {
                try {
                    Bond b = npc.cog().bond(UUID.fromString(bs.id), bs.name);
                    b.trust = bs.trust;
                    b.liking = bs.liking;
                    b.fearOf = bs.fearOf;
                    b.perceivedDanger = bs.perceivedDanger;
                    b.familiarity = bs.familiarity;
                    b.resentment = bs.resentment;
                    b.debt = bs.debt;
                } catch (Exception ignored) {
                }
            }
        }
        if (npc.body() != null && snap.health > 0) {
            npc.body().setHealth(Math.min(npc.body().getMaxHealth(), snap.health));
        }
        npc.mind().did("still here after the world slept");
    }

    private Snap snapOf(CivilianNpc npc) {
        Snap s = new Snap();
        s.id = npc.id().toString();
        s.name = npc.name();
        s.personality = npc.personality().name();
        var body = npc.body();
        Location at = body != null ? body.getLocation() : null;
        if (at != null && at.getWorld() != null) {
            s.world = at.getWorld().getName();
            s.x = at.getX();
            s.y = at.getY();
            s.z = at.getZ();
            s.yaw = at.getYaw();
            s.health = body.getHealth();
        }
        s.drives = npc.cog().drives;
        s.intention = npc.cog().intention.name();
        s.planWhy = npc.cog().plan.why;
        s.life = npc.cog().life;
        s.dead = !npc.alive();
        for (Episode e : npc.cog().episodes) {
            s.episodeRecall.add(e.recall());
        }
        for (Bond b : npc.cog().bonds.values()) {
            BondSnap bs = new BondSnap();
            bs.id = b.id.toString();
            bs.name = b.name;
            bs.trust = b.trust;
            bs.liking = b.liking;
            bs.fearOf = b.fearOf;
            bs.perceivedDanger = b.perceivedDanger;
            bs.familiarity = b.familiarity;
            bs.resentment = b.resentment;
            bs.debt = b.debt;
            s.bonds.add(bs);
        }
        return s;
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(disk));
        } catch (Exception failed) {
            plugin.getLogger().warning("lives.json save failed: " + failed.getMessage());
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Disk loaded = gson.fromJson(Files.readString(file), Disk.class);
            if (loaded != null) {
                disk = loaded;
                plugin.getLogger().info("Lives: " + disk.lives.size() + " living, "
                        + disk.graves.size() + " graves.");
            }
        } catch (Exception failed) {
            plugin.getLogger().warning("lives.json unreadable: " + failed.getMessage());
        }
    }
}
