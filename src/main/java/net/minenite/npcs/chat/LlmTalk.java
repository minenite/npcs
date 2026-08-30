package net.minenite.npcs.chat;

import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.civilian.CivilianNpc;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

/**
 * Spoken lines fire immediately from the personality. Waiting on Ollama
 * was making the first line late and then repeating the same sentence.
 */
public final class LlmTalk {
    private final NpcsPlugin plugin;

    public LlmTalk(NpcsPlugin plugin) {
        this.plugin = plugin;
    }

    public void aimedAt(CivilianNpc npc, Player player) {
        speakNow(npc, npc.personality().aimedLine(npc.lastLine()));
    }

    public void ambient(CivilianNpc npc, Player near) {
        speakNow(npc, npc.personality().ambientLine(npc.lastLine()));
    }

    public void dying(CivilianNpc npc, Player killer) {
        speakNow(npc, npc.personality().aimedLine(npc.lastLine()));
    }

    private void speakNow(CivilianNpc npc, String line) {
        if (!npc.canTalk() || line == null || line.isBlank()) {
            return;
        }
        if (line.equals(npc.lastLine())) {
            return;
        }
        npc.markTalked(line);
        Bukkit.broadcast(Component.text("<" + npc.name() + "> " + line));
    }
}
