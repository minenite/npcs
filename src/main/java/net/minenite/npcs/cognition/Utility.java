package net.minenite.npcs.cognition;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weighted utilities with noise, personality, hysteresis.
 * Never a single boolean gate.
 */
public final class Utility {
    private Utility() {
    }

    public static Intention pick(Cognition cog, WorldCue cue) {
        DriveSet d = cog.drives;
        Traits t = cog.traits;
        Intention[] all = Intention.values();
        double[] s = cog.scores;
        for (int i = 0; i < all.length; i++) {
            s[i] = score(all[i], d, t, cue, cog) * (0.88 + ThreadLocalRandom.current().nextDouble() * 0.24);
        }
        s[cog.intention.ordinal()] += 0.16;
        int bestI = 0;
        for (int i = 1; i < all.length; i++) {
            if (s[i] > s[bestI]) {
                bestI = i;
            }
        }
        Intention next = all[bestI];
        if (next != cog.intention && s[bestI] < s[cog.intention.ordinal()] + 0.11) {
            next = cog.intention;
        }
        cog.lastIntention = cog.intention;
        cog.intention = next;
        cog.lastReason = reason(next, d, cue);
        maybePlan(cog, next, cue);
        return next;
    }

    private static double score(Intention i, DriveSet d, Traits t, WorldCue c, Cognition cog) {
        return switch (i) {
            case SURVIVE -> d.urgency * 0.6 + d.pain * 0.3 + (1.0 - d.safety) * 0.4;
            case ESCAPE -> d.fear * c.threat * 0.9 + d.pain * 0.3 - d.confidence * 0.25
                    - d.attachment * 0.2 + d.desperation * 0.1 - t.loyalty * c.friendNear * 0.3;
            case HIDE -> d.fear * 0.5 + (1.0 - d.safety) * 0.4 + c.coverHere * 0.3
                    - d.curiosity * 0.15 + c.night * 0.15;
            case INVESTIGATE -> d.curiosity * 0.45 + c.soundConf * 0.5 * t.curiosity
                    - d.fear * 0.4 - t.paranoia * 0.1 + (c.soundConf > 0.2 && c.soundConf < 0.7 ? 0.2 : 0);
            case OBSERVE -> d.suspicion * 0.4 + t.paranoia * 0.2 + c.visibleStranger * 0.35
                    - d.urgency * 0.2;
            case APPROACH -> d.loneliness * t.sociability * 0.5 + c.friendlyNear * 0.4
                    - c.armedStranger * 0.6 - d.fear * 0.3 + d.curiosity * 0.15
                    + (c.playerNear > 0.5 && bondLike(cog, c.focusId) > 0.3 ? 0.25 : 0);
            case AVOID -> c.armedStranger * 0.55 + d.suspicion * 0.3 + d.fear * 0.25
                    - t.sociability * 0.15 + spaceNeed(cog, c);
            case FOLLOW -> d.attachment * 0.5 + t.loyalty * 0.25 * c.friendNear
                    - d.fear * 0.2 + (cog.groupWith != null ? 0.3 : 0);
            case SEARCH, LOOK_FOR_RESOURCE -> d.hunger * 0.4 + d.thirst * 0.4 + d.boredom * 0.15
                    + t.curiosity * 0.15 - d.fear * 0.25;
            case REST -> d.fatigue * 0.55 + (1.0 - d.urgency) * 0.2 - d.boredom * 0.2
                    + (c.indoors > 0.5 && d.safety > 0.4 ? 0.25 : 0);
            case LOOT, CHECK_CORPSE -> c.corpse * 0.55 + d.hunger * 0.15 - d.fear * 0.25
                    - t.empathy * 0.1 + d.desperation * 0.2;
            case SEEK_SHELTER -> ((c.rain || c.night > 0.5) ? 0.45 : 0.05) + (1.0 - d.safety) * 0.25
                    + d.fatigue * 0.15 - (c.indoors > 0.5 ? 0.4 : 0);
            case SEEK_COMPANY -> d.loneliness * t.sociability * 0.6 - c.armedStranger * 0.5
                    - d.fear * 0.15 + (d.fear > 0.4 && d.loneliness > 0.4 ? 0.2 : 0);
            case HELP_PERSON -> t.empathy * c.hurtNear * 0.7 - d.fear * 0.3 + bondLike(cog, c.focusId) * 0.3;
            case WARN_PERSON -> t.empathy * 0.3 + c.threat * 0.4 * c.friendNear - d.fear * 0.1
                    + t.talkativeness * 0.1;
            case TRADE -> t.sociability * 0.2 * c.playerNear * (1.0 - c.armedStranger)
                    + (cog.life.occupation.contains("trad") ? 0.25 : 0);
            case ASK_FOR_HELP -> d.desperation * 0.5 * t.sociability - t.confidence * 0.2
                    - c.armedStranger * 0.3;
            case INTIMIDATE -> d.aggression * 0.4 + c.armedStranger * 0.2 * t.aggression
                    - t.empathy * 0.2 + d.confidence * 0.15;
            case DEFEND_SELF -> c.aimedAt * 0.85 + d.fear * 0.2 + t.aggression * 0.2
                    + d.pain * 0.15 - t.empathy * 0.1;
            case MOURN -> c.corpse * t.empathy * 0.55 - d.urgency * 0.3;
            case SEARCH_BUILDING -> c.indoors * 0.2 + d.curiosity * 0.3 + c.night * 0.1
                    - d.fear * 0.2;
            case WATCH_ENTRANCE -> c.indoors * 0.35 + d.suspicion * 0.3 + c.night * 0.15
                    + d.fear * 0.1;
            case LISTEN -> c.soundConf * 0.55 * t.hearing + (cog.listenLeft > 0 ? 0.4 : 0)
                    - d.urgency * 0.15;
            case WAIT -> (1.0 - d.urgency) * 0.25 + d.fatigue * 0.15 + t.patience * 0.2
                    + (cog.waitFor != null ? 0.4 : 0);
            case PATROL, TRAVEL -> (1.0 - d.fatigue) * 0.25 + d.boredom * 0.25 + t.curiosity * 0.15
                    - d.fear * 0.2;
            case RETURN_HOME -> (c.night > 0.5 ? 0.35 : 0.05) + d.fatigue * 0.2 + t.patience * 0.1
                    - c.threat * 0.3;
            case LOOK_FOR_FRIEND -> (cog.life.friendName.isBlank() ? 0 : 0.2) + d.loneliness * 0.25
                    + t.loyalty * 0.2 - c.threat * 0.2;
            case SILENCE -> (1.0 - t.talkativeness) * 0.45 + d.fear * 0.2 + c.armedStranger * 0.2
                    + (d.stress > 0.6 ? 0.2 : 0);
        };
    }

    private static double bondLike(Cognition cog, UUID id) {
        if (id == null) {
            return 0;
        }
        Bond b = cog.bonds.get(id);
        return b == null ? 0 : b.liking;
    }

    private static double spaceNeed(Cognition cog, WorldCue c) {
        if (c.distanceFocus < 0) {
            return 0;
        }
        double want = cog.traits.personalSpace + c.armedStranger * 2.2;
        return c.distanceFocus < want ? 0.45 : 0;
    }

    private static String reason(Intention i, DriveSet d, WorldCue c) {
        return i.name().toLowerCase()
                + (c.aimedAt > 0.5 ? " | they aimed" : "")
                + (c.threat > 0.3 ? " | threat " + round(c.threat) : "")
                + (c.soundConf > 0.2 ? " | sound " + round(c.soundConf) : "")
                + " | fear " + round(d.fear);
    }

    private static String round(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static void maybePlan(Cognition cog, Intention next, WorldCue cue) {
        if (next == cog.plan.goal && !cog.plan.expired() && !cog.plan.done()) {
            return;
        }
        cog.plan.goal = next;
        cog.plan.started = System.currentTimeMillis();
        cog.plan.failAfter = cog.plan.started + 3 * 60_000L;
        cog.plan.step = 0;
        cog.plan.why = switch (next) {
            case SEEK_SHELTER -> "find somewhere safe"
                    + (cue.night > 0.5 ? " for the night" : " from the weather");
            case RETURN_HOME -> "go back toward " + cog.life.homeName;
            case LOOK_FOR_FRIEND -> "look for " + (cog.life.friendName.isBlank() ? "someone known" : cog.life.friendName);
            case INVESTIGATE -> "figure out that noise without walking into it";
            case HIDE -> "break line of sight";
            case ESCAPE -> "get off this ground";
            case WATCH_ENTRANCE -> "keep the door";
            case SEARCH_BUILDING -> "check this building";
            case SEEK_COMPANY -> "not be alone";
            case DEFEND_SELF -> "not die here";
            default -> "keep going";
        };
        cog.plan.steps = switch (next) {
            case SEEK_SHELTER -> new String[]{"find a building", "check the door", "step inside",
                    "look at rooms", "sit off the window", "watch the entrance"};
            case SEARCH_BUILDING -> new String[]{"enter", "check corners", "listen", "move rooms"};
            case INVESTIGATE -> new String[]{"freeze", "look", "decide", "close distance or leave"};
            case RETURN_HOME -> new String[]{"orient", "travel", "arrive", "settle"};
            default -> new String[]{cog.plan.why};
        };
    }
}
