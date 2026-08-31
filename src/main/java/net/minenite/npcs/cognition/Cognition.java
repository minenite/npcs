package net.minenite.npcs.cognition;

import net.minenite.npcs.civilian.Personality;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Higher-level person. Produces intention, plan, attention, beliefs.
 * The Java brain executes. The LLM does not.
 */
public final class Cognition {
    public final Traits traits;
    public final DriveSet drives;
    public final Life life;
    public final Map<UUID, Bond> bonds = new ConcurrentHashMap<>();
    public final List<Episode> episodes = new ArrayList<>();
    public final List<Rumor> rumors = new ArrayList<>();
    public final Map<UUID, LastSeen> lastSeen = new ConcurrentHashMap<>();
    public final ConversationTrack talk = new ConversationTrack();
    public final List<Expectation> expect = new ArrayList<>();
    public final Attention attention = new Attention();
    public final Plan plan = new Plan();
    public Intention intention = Intention.WAIT;
    public Intention lastIntention = Intention.WAIT;
    public final double[] scores = new double[Intention.values().length];
    public TalkWhy whyTalk = TalkWhy.NONE;
    public UUID groupWith;
    public String role = "";
    public String lastSound = "";
    public double lastSoundConf;
    public long lastSoundAt;
    public Location lastSoundGuess;
    public int reactionLeft;
    public int listenLeft;
    public int glanceBehind;
    public int spaceTicks;
    public int fireCool;
    public int cognitionIn;
    public long lastCognitionAt;
    public String lastReason = "";
    public int actionRepeats;
    public String lastAction = "";
    public boolean offscreen;
    public int pathFailStreak;
    public int pathCool;
    public UUID waitFor;
    public long waitForUntil;
    public int offscreenClock;
    public final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();

    public Cognition(Personality personality, String name, double x, double z) {
        this.traits = new Traits(personality);
        this.drives = new DriveSet(traits);
        this.life = Life.roll(personality, name, x, z);
        this.plan.why = "still getting my bearings";
        this.plan.goal = Intention.PATROL;
        this.plan.steps = new String[]{"look around", "pick a direction", "keep moving"};
        this.plan.started = System.currentTimeMillis();
        this.plan.failAfter = this.plan.started + 4 * 60_000L;
    }

    public Bond bond(UUID id, String name) {
        return bonds.computeIfAbsent(id, k -> new Bond(id, name));
    }

    public void attend(UUID id, String label, double weight, int ticks) {
        if (weight >= attention.weight || attention.until < System.currentTimeMillis()) {
            attention.id = id;
            attention.label = label;
            attention.weight = weight;
            attention.until = System.currentTimeMillis() + ticks * 50L;
        }
    }

    public void remember(Episode ep) {
        episodes.add(ep);
        while (episodes.size() > 48) {
            episodes.remove(0);
        }
        if (ep.whoId != null) {
            Bond b = bond(ep.whoId, ep.who);
            b.bumpFamiliar();
            if (ep.what.contains("aim") || ep.what.contains("shot") || ep.what.contains("kill")) {
                b.perceivedDanger = DriveSet.clamp(b.perceivedDanger + ep.intensity * 0.15);
                b.resentment = DriveSet.clamp(b.resentment + ep.intensity * 0.08);
            }
            if (ep.what.contains("help") || ep.what.contains("gave") || ep.what.contains("food")) {
                b.liking = DriveSet.clamp(b.liking + 0.12);
                b.debt = DriveSet.clamp(b.debt + 0.1);
            }
            b.applyBelief();
        }
    }

    public void hearRumor(Rumor rumor) {
        rumors.add(rumor);
        while (rumors.size() > 24) {
            rumors.remove(0);
        }
    }

    public void saw(UUID id, String name, Location at, String place, float yaw, double conf) {
        LastSeen seen = lastSeen.computeIfAbsent(id, k -> new LastSeen());
        seen.id = id;
        seen.name = name;
        seen.place = place;
        seen.x = at.getX();
        seen.y = at.getY();
        seen.z = at.getZ();
        seen.world = at.getWorld() == null ? "" : at.getWorld().getName();
        seen.heading = yaw;
        seen.at = System.currentTimeMillis();
        seen.confidence = conf;
        Bond b = bond(id, name);
        b.lastPlace = place;
        b.lastSeenAt = seen.at;
    }

    public void lost(UUID id) {
        LastSeen seen = lastSeen.get(id);
        if (seen != null) {
            seen.confidence *= 0.85;
        }
    }

    public List<Map.Entry<Intention, Double>> top(int n) {
        List<Map.Entry<Intention, Double>> list = new ArrayList<>();
        Intention[] all = Intention.values();
        for (int i = 0; i < all.length; i++) {
            list.add(Map.entry(all[i], scores[i]));
        }
        list.sort(Comparator.<Map.Entry<Intention, Double>>comparingDouble(Map.Entry::getValue).reversed());
        return list.subList(0, Math.min(n, list.size()));
    }

    public String memoryForLlm() {
        StringBuilder sb = new StringBuilder();
        sb.append("mood-drives: ").append(drives.digest()).append('\n');
        int from = Math.max(0, episodes.size() - 6);
        for (int i = from; i < episodes.size(); i++) {
            sb.append("- ").append(episodes.get(i).recall()).append('\n');
        }
        int r = 0;
        for (int i = rumors.size() - 1; i >= 0 && r < 3; i--, r++) {
            sb.append("- ").append(rumors.get(i).asKnown()).append('\n');
        }
        for (Bond b : bonds.values()) {
            if (b.familiarity > 0.05) {
                sb.append("- person ").append(b.sketch()).append('\n');
            }
        }
        if (plan.why != null && !plan.why.isBlank()) {
            sb.append("- I am doing this because: ").append(plan.current()).append('\n');
        }
        return sb.toString();
    }

    public boolean due(boolean urgent) {
        if (cognitionIn > 0) {
            cognitionIn--;
            return false;
        }
        int wait = urgent ? 3 + (int) ((1.0 - traits.reaction) * 6)
                : 28 + (int) (traits.patience * 50) + ThreadLocalRandom.current().nextInt(20);
        cognitionIn = wait;
        lastCognitionAt = System.currentTimeMillis();
        return true;
    }

    public void stickAction(String action) {
        if (action.equals(lastAction)) {
            actionRepeats++;
        } else {
            actionRepeats = 0;
            lastAction = action;
        }
    }

    public boolean edgeBlocked(String edge) {
        Long until = blockedUntil.get(edge);
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            blockedUntil.remove(edge);
            return false;
        }
        return true;
    }

    public void blockEdge(String edge) {
        blockedUntil.put(edge, System.currentTimeMillis() + 45_000L);
    }

    public void expect(String text, long ms) {
        for (Expectation e : expect) {
            if (!e.broken && e.text != null && e.text.equals(text)) {
                return;
            }
        }
        Expectation e = new Expectation();
        e.text = text;
        e.by = System.currentTimeMillis() + ms;
        expect.add(e);
        while (expect.size() > 12) {
            expect.remove(0);
        }
    }

    public boolean violated(String contains) {
        boolean hit = false;
        for (Expectation e : expect) {
            if (!e.broken && e.text != null && e.text.contains(contains) && e.overdue()) {
                e.broken = true;
                hit = true;
            }
        }
        return hit;
    }
}
