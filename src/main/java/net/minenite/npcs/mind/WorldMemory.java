package net.minenite.npcs.mind;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minenite.npcs.NpcsPlugin;
import org.bukkit.Location;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the street itself remembers: who pointed a gun, who died, what
 * was said in earshot. Civilians share this the way real people share
 * a town — if Rook got aimed at, Lev can already have heard.
 */
public final class WorldMemory {
    public record Gossip(long at, String text, String world, int x, int z) {
    }

    public static final class Rep {
        public String name = "";
        public int trust;
        public final List<String> facts = new ArrayList<>();
    }

    private static final Type GOSSIP_TYPE = new TypeToken<List<Gossip>>() {
    }.getType();
    private static final Type REP_TYPE = new TypeToken<Map<String, Rep>>() {
    }.getType();
    private static final int MAX_GOSSIP = 60;

    private final NpcsPlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Deque<Gossip> gossip = new ArrayDeque<>();
    private final Map<UUID, Rep> people = new ConcurrentHashMap<>();
    private final Path file;

    public WorldMemory(NpcsPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("street.json");
        load();
    }

    public void hear(Location at, String text) {
        if (text == null || text.isBlank() || at == null || at.getWorld() == null) {
            return;
        }
        synchronized (gossip) {
            Gossip last = gossip.peekLast();
            if (last != null && last.text.equalsIgnoreCase(text)) {
                return;
            }
            gossip.addLast(new Gossip(System.currentTimeMillis(), text.trim(),
                    at.getWorld().getName(), at.getBlockX(), at.getBlockZ()));
            while (gossip.size() > MAX_GOSSIP) {
                gossip.removeFirst();
            }
        }
    }

    public void mark(UUID id, String name, int trustDelta, String fact, Location at) {
        if (id == null) {
            return;
        }
        Rep rep = people.computeIfAbsent(id, ignored -> new Rep());
        if (name != null && !name.isBlank()) {
            rep.name = name;
        }
        rep.trust = Math.max(-10, Math.min(10, rep.trust + trustDelta));
        if (fact != null && !fact.isBlank()) {
            rep.facts.add(fact);
            while (rep.facts.size() > 10) {
                rep.facts.remove(0);
            }
            hear(at, fact);
        }
    }

    public int trust(UUID id) {
        Rep rep = id == null ? null : people.get(id);
        return rep == null ? 0 : rep.trust;
    }

    public String about(UUID id) {
        Rep rep = id == null ? null : people.get(id);
        if (rep == null) {
            return "";
        }
        if (rep.facts.isEmpty()) {
            return rep.name + " trust " + rep.trust;
        }
        int from = Math.max(0, rep.facts.size() - 3);
        return rep.name + " trust " + rep.trust + ": "
                + String.join("; ", rep.facts.subList(from, rep.facts.size()));
    }

    public String digest(Location at, int max) {
        List<String> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        synchronized (gossip) {
            List<Gossip> copy = new ArrayList<>(gossip);
            for (int i = copy.size() - 1; i >= 0 && lines.size() < max; i--) {
                Gossip g = copy.get(i);
                if (now - g.at > 30 * 60_000L) {
                    continue;
                }
                if (at != null && at.getWorld() != null && at.getWorld().getName().equals(g.world)) {
                    int dx = at.getBlockX() - g.x;
                    int dz = at.getBlockZ() - g.z;
                    if (dx * dx + dz * dz > 80 * 80) {
                        continue;
                    }
                }
                lines.add(g.text);
            }
        }
        if (lines.isEmpty()) {
            return "the street has been quiet";
        }
        return String.join(" | ", lines);
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Map<String, Object> dump = Map.of(
                    "gossip", List.copyOf(gossip),
                    "people", people);
            Files.writeString(file, gson.toJson(dump));
        } catch (IOException failed) {
            plugin.getLogger().warning("Could not save street memory: " + failed.getMessage());
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            var raw = gson.fromJson(Files.readString(file), Map.class);
            if (raw == null) {
                return;
            }
            Object g = raw.get("gossip");
            if (g != null) {
                List<Gossip> loaded = gson.fromJson(gson.toJson(g), GOSSIP_TYPE);
                if (loaded != null) {
                    gossip.addAll(loaded);
                }
            }
            Object p = raw.get("people");
            if (p != null) {
                Map<String, Rep> loaded = gson.fromJson(gson.toJson(p), REP_TYPE);
                if (loaded != null) {
                    loaded.forEach((key, rep) -> {
                        try {
                            people.put(UUID.fromString(key), rep);
                        } catch (IllegalArgumentException ignored) {
                        }
                    });
                }
            }
            plugin.getLogger().info("Street memory: " + gossip.size() + " rumors, "
                    + people.size() + " faces.");
        } catch (Exception failed) {
            plugin.getLogger().warning("Street memory unreadable: " + failed.getMessage());
        }
    }
}
