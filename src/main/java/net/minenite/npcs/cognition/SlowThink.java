package net.minenite.npcs.cognition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minenite.npcs.chat.OllamaClient;
import net.minenite.npcs.civilian.CivilianNpc;

/**
 * Optional slow interpretation. Never invents objects. Validated against Intention.
 */
public final class SlowThink {
    private SlowThink() {
    }

    public static void maybe(CivilianNpc npc, OllamaClient ollama, Runnable unused) {
        if (ollama == null || npc.cog().drives.urgency > 0.55) {
            return;
        }
        var top = npc.cog().top(2);
        if (top.size() < 2 || Math.abs(top.get(0).getValue() - top.get(1).getValue()) > 0.12) {
            return;
        }
        String system = "You interpret a civilian's situation. Return ONLY JSON with keys "
                + "interpretation, priority, social_intent, possible_goal. "
                + "possible_goal must be one of: " + java.util.Arrays.toString(Intention.values())
                + ". Do not invent people, places, or events not listed.";
        String user = "Name " + npc.name() + "\n" + npc.cog().memoryForLlm()
                + "\nTop wants: " + top.get(0).getKey() + " vs " + top.get(1).getKey()
                + "\nPlan: " + npc.cog().plan.current();
        ollama.complete(system, user, raw -> apply(npc, raw));
    }

    static void apply(CivilianNpc npc, String raw) {
        if (raw == null) {
            return;
        }
        try {
            int a = raw.indexOf('{');
            int b = raw.lastIndexOf('}');
            if (a < 0 || b <= a) {
                return;
            }
            JsonObject json = JsonParser.parseString(raw.substring(a, b + 1)).getAsJsonObject();
            if (json.has("possible_goal")) {
                try {
                    Intention g = Intention.valueOf(json.get("possible_goal").getAsString().trim().toUpperCase()
                            .replace(' ', '_'));
                    if (!g.combat() || npc.cog().drives.urgency > 0.4) {
                        npc.cog().plan.goal = g;
                        npc.cog().plan.why = json.has("priority") ? json.get("priority").getAsString() : npc.cog().plan.why;
                    }
                } catch (Exception ignored) {
                }
            }
            if (json.has("social_intent")) {
                String s = json.get("social_intent").getAsString().toLowerCase();
                if (s.contains("avoid") || s.contains("silence")) {
                    npc.cog().whyTalk = TalkWhy.NONE;
                    npc.cog().intention = npc.cog().intention == Intention.SEEK_COMPANY
                            ? Intention.OBSERVE : npc.cog().intention;
                }
            }
        } catch (Exception ignored) {
        }
    }
}
