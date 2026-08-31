package net.minenite.npcs.chat;

import net.kyori.adventure.text.Component;
import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.civilian.CivilianNpc;
import net.minenite.npcs.civilian.NpcBodies;
import net.minenite.npcs.mind.EnvironmentSense;
import net.minenite.npcs.mind.WorldMemory;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Every spoken line goes through the local LLM with this person's
 * memory and what they can see. Canned personality lines are only a
 * last-ditch fallback if Ollama is down.
 */
public final class LlmTalk {
    public static final double HEAR_RANGE = 28;

    private final NpcsPlugin plugin;
    private WorldMemory street;
    private OllamaClient ollama;
    private Supplier<Collection<CivilianNpc>> roster = java.util.List::of;
    private final Set<UUID> inflight = ConcurrentHashMap.newKeySet();

    public LlmTalk(NpcsPlugin plugin) {
        this.plugin = plugin;
    }

    public void bind(WorldMemory street, OllamaClient ollama, Supplier<Collection<CivilianNpc>> roster) {
        this.street = street;
        this.ollama = ollama;
        this.roster = roster;
    }

    public WorldMemory street() {
        return street;
    }

    public void aimedAt(CivilianNpc npc, Player player) {
        String extra = extraPlayer(npc, player) + "\nThey are aiming at you right now.";
        speak(npc, player, TalkPrompt.Beat.AIMED, extra, npc.personality().aimedLine(npc.lastLine()));
    }

    public void relief(CivilianNpc npc, Player player) {
        speak(npc, player, TalkPrompt.Beat.RELIEF, extraPlayer(npc, player),
                npc.personality().ambientLine(npc.lastLine()));
    }

    public void shots(CivilianNpc npc, Player near) {
        speak(npc, near, TalkPrompt.Beat.SHOT, extraPlayer(npc, near),
                npc.personality().aimedLine(npc.lastLine()));
    }

    public void hurt(CivilianNpc npc, Player from) {
        speak(npc, from, TalkPrompt.Beat.HURT, extraPlayer(npc, from),
                npc.personality().aimedLine(npc.lastLine()));
    }

    public void dying(CivilianNpc npc, Player killer) {
        speak(npc, killer, TalkPrompt.Beat.DYING, extraPlayer(npc, killer),
                npc.personality().aimedLine(npc.lastLine()));
    }

    public void ambient(CivilianNpc npc, Player near) {
        speak(npc, near, TalkPrompt.Beat.PLAYER, extraPlayer(npc, near),
                npc.personality().ambientLine(npc.lastLine()));
    }

    public void replyTo(CivilianNpc npc, Player player, String heard) {
        npc.mind().heard(player.getName(), heard);
        String extra = extraPlayer(npc, player) + "\nThey just said: \"" + heard + "\"";
        speak(npc, player, TalkPrompt.Beat.REPLY, extra, npc.personality().ambientLine(npc.lastLine()));
    }

    public void toCivilian(CivilianNpc npc, CivilianNpc other, String cue) {
        String extra = "You are speaking to " + other.name() + ", " + other.personality().dossier() + "."
                + "\nThey last said: " + blank(other.mind().lastSaid(), "(nothing yet)")
                + (cue == null || cue.isBlank() ? "" : "\n" + cue);
        speak(npc, null, TalkPrompt.Beat.NPC, extra, npc.personality().ambientLine(npc.lastLine()));
    }

    public void corpse(CivilianNpc npc, Player near) {
        speak(npc, near, TalkPrompt.Beat.CORPSE, extraPlayer(npc, near),
                "God. Not another one.");
    }

    public void weather(CivilianNpc npc, Player near) {
        speak(npc, near, TalkPrompt.Beat.WEATHER, extraPlayer(npc, near),
                npc.personality().ambientLine(npc.lastLine()));
    }

    private void speak(CivilianNpc npc, Player to, TalkPrompt.Beat beat, String extra, String fallback) {
        if (npc == null || !npc.alive()) {
            return;
        }
        if (!inflight.add(npc.id())) {
            return;
        }
        LivingEntity body = npc.body();
        EnvironmentSense.Snap env = body == null
                ? null
                : EnvironmentSense.read(body, npc, roster.get());
        String system = TalkPrompt.system(npc, env, street);
        String user = TalkPrompt.user(beat, extra);
        if (ollama == null || !plugin.getConfig().getBoolean("llm.enabled", true)) {
            inflight.remove(npc.id());
            utter(npc, to, fallback);
            return;
        }
        ollama.complete(system, user, line -> {
            inflight.remove(npc.id());
            if (!npc.alive()) {
                return;
            }
            utter(npc, to, line == null || line.isBlank() ? fallback : line);
        });
    }

    public void utter(CivilianNpc npc, Player to, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (line.equals(npc.lastLine()) || line.equals(npc.mind().lastSaid())) {
            return;
        }
        npc.markTalked(line);
        npc.mind().said(line);
        LivingEntity body = npc.body();
        Location at = body != null ? body.getLocation() : (to != null ? to.getLocation() : null);
        if (at != null && street != null) {
            street.hear(at, npc.name() + " said: " + line);
        }
        Component message = Component.text("<" + npc.name() + "> " + line);
        if (at == null || at.getWorld() == null) {
            if (to != null) {
                to.sendMessage(message);
            }
            return;
        }
        double rangeSq = HEAR_RANGE * HEAR_RANGE;
        for (Player viewer : at.getWorld().getPlayers()) {
            if (NpcBodies.realPlayer(viewer) && viewer.getLocation().distanceSquared(at) <= rangeSq) {
                viewer.sendMessage(message);
            }
        }
        for (CivilianNpc other : roster.get()) {
            if (other == npc || !other.alive()) {
                continue;
            }
            LivingEntity otherBody = other.body();
            if (otherBody == null || otherBody.getWorld() != at.getWorld()) {
                continue;
            }
            if (otherBody.getLocation().distanceSquared(at) <= rangeSq) {
                other.mind().heard(npc.name(), line);
            }
        }
    }

    private String extraPlayer(CivilianNpc npc, Player player) {
        if (player == null) {
            return "";
        }
        String known = npc.mind().know(player.getUniqueId());
        String streetAbout = street == null ? "" : street.about(player.getUniqueId());
        StringBuilder extra = new StringBuilder();
        extra.append("The survivor in front of you is named ").append(player.getName()).append(".");
        if (!known.isBlank()) {
            extra.append("\nYou personally know: ").append(known);
        }
        if (!streetAbout.isBlank()) {
            extra.append("\nThe street knows: ").append(streetAbout);
        }
        return extra.toString();
    }

    private static String blank(String value, String or) {
        return value == null || value.isBlank() ? or : value;
    }
}
