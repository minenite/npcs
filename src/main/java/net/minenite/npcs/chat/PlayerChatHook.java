package net.minenite.npcs.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minenite.npcs.NpcsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * If you talk in the world, nearby civilians hear the actual words
 * and can answer them.
 */
public final class PlayerChatHook implements Listener {
    private final NpcsPlugin plugin;
    private final ConversationDirector director;

    public PlayerChatHook(NpcsPlugin plugin, ConversationDirector director) {
        this.plugin = plugin;
        this.director = director;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String line = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> director.playerSpoke(event.getPlayer(), line));
    }
}
