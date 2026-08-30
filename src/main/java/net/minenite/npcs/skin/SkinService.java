package net.minenite.npcs.skin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minenite.npcs.NpcsPlugin;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches and caches the DirtyCivilian (or configured) Mojang skin so every
 * civilian and their corpse shares the same textures.
 */
public final class SkinService {
    private final NpcsPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final AtomicReference<Textures> textures = new AtomicReference<>();

    public SkinService(NpcsPlugin plugin) {
        this.plugin = plugin;
    }

    public void prefetch() {
        loadCache();
        if (textures.get() != null) {
            return;
        }
        CompletableFuture.runAsync(this::fetchMojang);
    }

    public PlayerProfile profileFor(UUID id, String name) {
        PlayerProfile profile = Bukkit.createProfile(id, clip(name));
        Textures skin = textures.get();
        if (skin != null) {
            profile.setProperty(new ProfileProperty("textures", skin.value, skin.signature));
        }
        return profile;
    }

    public Textures textures() {
        return textures.get();
    }

    public String username() {
        return plugin.getConfig().getString("skin-username", "DirtyCivilian");
    }

    private void fetchMojang() {
        String user = username();
        try {
            String lookup = get("https://api.mojang.com/users/profiles/minecraft/" + user);
            JsonObject idJson = JsonParser.parseString(lookup == null ? "{}" : lookup).getAsJsonObject();
            if (!idJson.has("id")) {
                plugin.getLogger().warning("Skin lookup failed for " + user + ": " + lookup);
                return;
            }
            String hex = idJson.get("id").getAsString().replace("-", "");
            String session = get("https://sessionserver.mojang.com/session/minecraft/profile/"
                    + hex + "?unsigned=false");
            JsonObject sessionJson = JsonParser.parseString(session).getAsJsonObject();
            JsonObject prop = sessionJson.getAsJsonArray("properties").get(0).getAsJsonObject();
            Textures next = new Textures(
                    prop.get("value").getAsString(),
                    prop.has("signature") ? prop.get("signature").getAsString() : null);
            textures.set(next);
            writeCache(next);
            plugin.getLogger().info("Cached skin for " + user);
        } catch (Exception failed) {
            plugin.getLogger().warning("Could not fetch skin for " + user + ": " + failed.getMessage());
        }
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url);
        }
        return response.body();
    }

    private void loadCache() {
        var file = plugin.getDataFolder().toPath().resolve("skin-cache.json");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (json.has("value")) {
                textures.set(new Textures(json.get("value").getAsString(),
                        json.has("signature") ? json.get("signature").getAsString() : null));
            }
        } catch (Exception ignored) {
        }
    }

    private void writeCache(Textures skin) {
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            String body = "{\"value\":\"" + skin.value + "\",\"signature\":\""
                    + (skin.signature == null ? "" : skin.signature) + "\"}";
            Files.writeString(plugin.getDataFolder().toPath().resolve("skin-cache.json"),
                    body, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static String clip(String name) {
        if (name == null || name.isBlank()) {
            return "Civilian";
        }
        return name.length() <= 16 ? name : name.substring(0, 16);
    }

    public record Textures(String value, String signature) {
    }
}
