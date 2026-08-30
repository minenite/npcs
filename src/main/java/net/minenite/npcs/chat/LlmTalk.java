package net.minenite.npcs.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.civilian.CivilianNpc;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Small local LLM (Ollama) with a hard personality prompt. Falls back to the
 * personality's written lines if the model is slow or down.
 */
public final class LlmTalk {
    private final NpcsPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public LlmTalk(NpcsPlugin plugin) {
        this.plugin = plugin;
    }

    public void aimedAt(CivilianNpc npc, Player player) {
        speak(npc, player, true, "A survivor named " + player.getName()
                + " is aiming a weapon at you from close range. You raise yours. Speak now.");
    }

    public void ambient(CivilianNpc npc, Player near) {
        speak(npc, near, false, near == null
                ? "You are walking. Say something under your breath, or nothing if you would stay quiet."
                : "A survivor named " + near.getName() + " is nearby but not aiming. You may mutter or greet once.");
    }

    public void dying(CivilianNpc npc, Player killer) {
        speak(npc, killer, true, "You are dying. One last thing, if you would say anything at all.");
    }

    private void speak(CivilianNpc npc, Player context, boolean urgent, String userBeat) {
        if (!npc.canTalk()) {
            return;
        }
        npc.markTalked();
        String fallback = urgent ? npc.personality().aimedLine() : npc.personality().ambientLine();
        CompletableFuture
                .supplyAsync(() -> complete(npc, userBeat, fallback))
                .thenAccept(line -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!npc.alive()) {
                        return;
                    }
                    broadcast(npc, sanitize(line, fallback));
                }));
    }

    private String complete(CivilianNpc npc, String userBeat, String fallback) {
        String endpoint = plugin.getConfig().getString("llm.endpoint", "http://127.0.0.1:11434/api/chat");
        String model = plugin.getConfig().getString("llm.model", "phi3:mini");
        int timeout = plugin.getConfig().getInt("llm.timeout-ms", 8000);
        int tokens = plugin.getConfig().getInt("llm.max-tokens", 70);
        double temp = plugin.getConfig().getDouble("llm.temperature", 0.9);
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);
        JsonObject options = new JsonObject();
        options.addProperty("temperature", temp);
        options.addProperty("num_predict", tokens);
        body.add("options", options);
        JsonArray messages = new JsonArray();
        messages.add(message("system", npc.personality().systemPrompt()
                + "\nYour name in this world is " + npc.name() + ". Stay in that name."
                + "\nYou are " + npc.personality().dossier() + "."
                + "\nOutput ONLY the spoken line. No quotes, no name prefix, no stage directions."));
        messages.add(message("user", userBeat));
        body.add("messages", messages);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofMillis(Math.max(1500, timeout)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return fallback;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String text = extract(json);
            return text == null || text.isBlank() ? fallback : text;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject object = new JsonObject();
        object.addProperty("role", role);
        object.addProperty("content", content);
        return object;
    }

    private static String extract(JsonObject json) {
        if (json.has("message") && json.getAsJsonObject("message").has("content")) {
            return json.getAsJsonObject("message").get("content").getAsString();
        }
        if (json.has("response")) {
            return json.get("response").getAsString();
        }
        return null;
    }

    private static String sanitize(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String line = raw.replace('\n', ' ').replace('\r', ' ').trim();
        line = line.replaceAll("^[\"']+|[\"']+$", "");
        if (line.regionMatches(true, 0, "you say", 0, 7)) {
            return fallback;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("as an ai") || lower.contains("language model") || lower.contains("npc")) {
            return fallback;
        }
        if (line.length() > 180) {
            line = line.substring(0, 177) + "...";
        }
        return line.isBlank() ? fallback : line;
    }

    private static void broadcast(CivilianNpc npc, String line) {
        Bukkit.broadcast(net.kyori.adventure.text.Component.text("<" + npc.name() + "> " + line));
    }
}
