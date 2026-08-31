package net.minenite.npcs.chat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minenite.npcs.NpcsPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Local Ollama chat. Work stays off the server thread; the spoken line
 * comes back on the main thread.
 */
public final class OllamaClient {
    private final NpcsPlugin plugin;
    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
        Thread thread = new Thread(r, "npcs-ollama");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean loggedFail;

    public OllamaClient(NpcsPlugin plugin) {
        this.plugin = plugin;
    }

    public void complete(String system, String user, Consumer<String> done) {
        pool.execute(() -> {
            String line = request(system, user, plugin.getConfig().getInt("llm.timeout-ms", 8000));
            plugin.getServer().getScheduler().runTask(plugin, () -> done.accept(line));
        });
    }

    public void warm() {
        pool.execute(() -> {
            String line = request("Reply with the single word ready.", "ready", 60000);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (line == null) {
                    plugin.getLogger().warning("Ollama warmup missed — first lines may fall back until the model is loaded.");
                } else {
                    plugin.getLogger().info("Ollama warm: " + line);
                }
            });
        });
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    private String request(String system, String user, int timeoutMs) {
        String endpoint = plugin.getConfig().getString("llm.endpoint", "http://127.0.0.1:11434/api/chat");
        String model = plugin.getConfig().getString("llm.model", "llama3.1:8b");
        int timeout = Math.max(800, timeoutMs);
        int tokens = Math.max(24, plugin.getConfig().getInt("llm.max-tokens", 70));
        double temp = plugin.getConfig().getDouble("llm.temperature", 0.88);
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("stream", false);
            JsonArray messages = new JsonArray();
            messages.add(msg("system", system));
            messages.add(msg("user", user));
            body.add("messages", messages);
            JsonObject options = new JsonObject();
            options.addProperty("temperature", temp);
            options.addProperty("num_predict", tokens);
            JsonArray stop = new JsonArray();
            stop.add("\n\n");
            stop.add("Player:");
            stop.add("User:");
            options.add("stop", stop);
            body.add("options", options);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                fail("HTTP " + response.statusCode());
                return null;
            }
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            if (json == null || !json.has("message")) {
                return null;
            }
            String content = json.getAsJsonObject("message").get("content").getAsString();
            loggedFail = false;
            return sanitize(content);
        } catch (Exception failed) {
            fail(failed.getClass().getSimpleName() + ": " + failed.getMessage());
            return null;
        }
    }

    private JsonObject msg(String role, String content) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", role);
        obj.addProperty("content", content);
        return obj;
    }

    private void fail(String why) {
        if (!loggedFail) {
            loggedFail = true;
            plugin.getLogger().warning("Ollama talk failed (" + why + "). Using fallback lines until it recovers.");
        }
    }

    static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String line = raw.replace('\r', ' ').replace('\n', ' ').trim();
        line = line.replaceAll("^[\"'“”]+|[\"'“”]+$", "");
        line = line.replaceAll("(?i)^(you say:|i say:|civilian:|npc:|assistant:)\\s*", "");
        line = line.replaceAll("\\*[^*]+\\*", "");
        line = line.replaceAll("\\s+", " ").trim();
        String low = line.toLowerCase();
        if (low.contains("as an ai") || low.contains("language model")
                || low.contains("minecraft") || low.contains("i am an npc")
                || low.contains("as a character")) {
            return null;
        }
        if (line.length() < 2) {
            return null;
        }
        int cut = line.length();
        int seen = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                seen++;
                if (seen == 2) {
                    cut = i + 1;
                    break;
                }
            }
        }
        line = line.substring(0, cut).trim();
        if (line.length() > 220) {
            line = line.substring(0, 217).trim() + "…";
        }
        return line;
    }
}
