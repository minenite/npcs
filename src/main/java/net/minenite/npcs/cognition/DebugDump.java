package net.minenite.npcs.cognition;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minenite.npcs.civilian.CivilianNpc;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class DebugDump {
    private DebugDump() {
    }

    public static void send(CommandSender to, CivilianNpc npc) {
        Cognition c = npc.cog();
        to.sendMessage(Component.text(npc.name(), NamedTextColor.GOLD)
                .append(Component.text("  " + npc.personality().name().toLowerCase(), NamedTextColor.GRAY)));
        to.sendMessage(line("Goal", c.plan.goal + " / " + c.intention + "  because " + c.plan.current()));
        to.sendMessage(line("Action", npc.state() + "  repeats=" + c.actionRepeats));
        to.sendMessage(line("Attention", c.attention.label == null ? "—" : c.attention.label));
        StringBuilder top = new StringBuilder();
        for (Map.Entry<Intention, Double> e : c.top(5)) {
            top.append(e.getKey().name().toLowerCase()).append("=")
                    .append(String.format("%.2f", e.getValue())).append("  ");
        }
        to.sendMessage(line("Utility", top.toString()));
        to.sendMessage(line("Drives", c.drives.digest()));
        to.sendMessage(line("Sound", c.lastSound.isBlank() ? "—" : c.lastSound + " conf="
                + String.format("%.2f", c.lastSoundConf)));
        if (c.talk.alive()) {
            to.sendMessage(line("Talk", c.whyTalk + " topic=" + c.talk.topic));
        }
        Bond threat = null;
        for (Bond b : c.bonds.values()) {
            if (threat == null || b.perceivedDanger > threat.perceivedDanger) {
                threat = b;
            }
        }
        if (threat != null) {
            to.sendMessage(line("Known", threat.sketch()));
        }
        to.sendMessage(line("Reason", c.lastReason));
        to.sendMessage(line("Life", c.life.occupation + " / worry: " + c.life.worry + " / want: " + c.life.want));
    }

    private static Component line(String k, String v) {
        return Component.text(k + ": ", NamedTextColor.YELLOW).append(Component.text(v, NamedTextColor.WHITE));
    }
}
