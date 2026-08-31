package net.minenite.npcs.chat;

import net.kyori.adventure.text.Component;
import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.civilian.CivilianNpc;
import net.minenite.npcs.civilian.NpcBodies;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Spoken lines fire immediately from the personality. Waiting on Ollama
 * was making the first line late and then repeating the same sentence.
 */
public final class LlmTalk {
    private static final double HEAR_RANGE = 26;

    public LlmTalk(NpcsPlugin ignored) {
    }

    public void aimedAt(CivilianNpc npc, Player player) {
        speakNear(npc, player, npc.personality().aimedLine(npc.lastLine()));
    }

    public void ambient(CivilianNpc npc, Player near) {
        speakNear(npc, near, npc.personality().ambientLine(npc.lastLine()));
    }

    public void dying(CivilianNpc npc, Player killer) {
        speakNear(npc, killer, npc.personality().aimedLine(npc.lastLine()));
    }

    private void speakNear(CivilianNpc npc, Player to, String line) {
        if (line == null || line.isBlank() || line.equals(npc.lastLine())) {
            return;
        }
        npc.markTalked(line);
        Component message = Component.text("<" + npc.name() + "> " + line);
        Location at = origin(npc, to);
        if (at == null || at.getWorld() == null) {
            if (to != null) {
                to.sendMessage(message);
            }
            return;
        }
        double rangeSq = HEAR_RANGE * HEAR_RANGE;
        for (Player viewer : at.getWorld().getPlayers()) {
            if (!NpcBodies.realPlayer(viewer)) {
                continue;
            }
            if (viewer.getLocation().distanceSquared(at) <= rangeSq) {
                viewer.sendMessage(message);
            }
        }
    }

    private static Location origin(CivilianNpc npc, Player fallback) {
        LivingEntity body = npc.body();
        if (body != null) {
            return body.getLocation();
        }
        return fallback != null ? fallback.getLocation() : null;
    }
}
