package net.minenite.npcs.chat;

import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.civilian.CivilianNpc;
import net.minenite.npcs.civilian.NpcBodies;
import net.minenite.npcs.mind.EnvironmentSense;
import net.minenite.npcs.mind.Mood;
import net.minenite.npcs.mind.WorldMemory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Two civilians stop, look at each other, and actually talk — using
 * memory and the street, then maybe walk on together.
 */
public final class ConversationDirector {
    private static final class Thread {
        final UUID a;
        final UUID b;
        int left;
        long nextAt;
        UUID waiting;

        Thread(UUID a, UUID b, int left) {
            this.a = a;
            this.b = b;
            this.left = left;
            this.nextAt = System.currentTimeMillis() + 400;
            this.waiting = a;
        }
    }

    private final NpcsPlugin plugin;
    private final LlmTalk talk;
    private final WorldMemory street;
    private Supplier<Collection<CivilianNpc>> roster = List::of;
    private final List<Thread> open = new ArrayList<>();
    private int clock;

    public ConversationDirector(NpcsPlugin plugin, LlmTalk talk, WorldMemory street) {
        this.plugin = plugin;
        this.talk = talk;
        this.street = street;
    }

    public void setRoster(Supplier<Collection<CivilianNpc>> roster) {
        this.roster = roster;
    }

    public void tick() {
        if (++clock % 8 != 0) {
            return;
        }
        Collection<CivilianNpc> all = roster.get();
        prune(all);
        advance(all);
        if (open.size() < 4) {
            tryStart(all);
        }
    }

    public void playerSpoke(Player player, String line) {
        if (player == null || line == null || line.isBlank()) {
            return;
        }
        CivilianNpc best = null;
        double bestD = 22 * 22;
        for (CivilianNpc npc : roster.get()) {
            LivingEntity body = npc.body();
            if (body == null || body.getWorld() != player.getWorld() || busy(npc)) {
                continue;
            }
            double d = body.getLocation().distanceSquared(player.getLocation());
            if (d > bestD) {
                continue;
            }
            npc.mind().heard(player.getName(), line);
            npc.mind().met(player.getUniqueId(), player.getName(), 0, "said: " + clip(line, 80));
            street.hear(player.getLocation(), player.getName() + " said: " + clip(line, 80));
            bestD = d;
            best = npc;
        }
        if (best == null) {
            return;
        }
        best.mind().feel(Mood.WARY, 20);
        lookAt(best, player);
        talk.replyTo(best, player, line);
    }

    public void endFor(CivilianNpc npc) {
        if (npc == null) {
            return;
        }
        open.removeIf(thread -> thread.a.equals(npc.id()) || thread.b.equals(npc.id()));
        npc.setTalkingTo(null);
        npc.setTalkLeft(0);
    }

    private void tryStart(Collection<CivilianNpc> all) {
        List<CivilianNpc> idle = new ArrayList<>();
        for (CivilianNpc npc : all) {
            if (free(npc) && npc.body() != null) {
                idle.add(npc);
            }
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < idle.size(); i++) {
            CivilianNpc a = idle.get(i);
            LivingEntity aa = a.body();
            for (int j = i + 1; j < idle.size(); j++) {
                CivilianNpc b = idle.get(j);
                LivingEntity bb = b.body();
                if (bb.getWorld() != aa.getWorld()) {
                    continue;
                }
                if (aa.getLocation().distanceSquared(bb.getLocation()) > 6.8 * 6.8) {
                    continue;
                }
                if (inThread(a.id()) || inThread(b.id())) {
                    continue;
                }
                if (a.personality() == net.minenite.npcs.civilian.Personality.PARANOID_LONER
                        && rng.nextInt(4) != 0) {
                    continue;
                }
                if (b.personality() == net.minenite.npcs.civilian.Personality.PARANOID_LONER
                        && rng.nextInt(4) != 0) {
                    continue;
                }
                if (rng.nextInt(3) != 0 && !a.personality().chatty() && !b.personality().chatty()) {
                    continue;
                }
                begin(a, b);
                return;
            }
        }
    }

    private void begin(CivilianNpc a, CivilianNpc b) {
        int turns = 3 + ThreadLocalRandom.current().nextInt(3);
        open.add(new Thread(a.id(), b.id(), turns));
        plantTalk(a, b);
        plantTalk(b, a);
        a.mind().did("stopped to talk to " + b.name());
        b.mind().did("stopped to talk to " + a.name());
        a.mind().met(b.id(), b.name(), 1, "talking");
        b.mind().met(a.id(), a.name(), 1, "talking");
        talk.toCivilian(a, b, cue(a, b));
    }

    private void advance(Collection<CivilianNpc> all) {
        long now = System.currentTimeMillis();
        for (Thread thread : List.copyOf(open)) {
            if (now < thread.nextAt) {
                continue;
            }
            CivilianNpc speaker = find(all, thread.waiting);
            CivilianNpc other = find(all, thread.waiting.equals(thread.a) ? thread.b : thread.a);
            if (speaker == null || other == null || busy(speaker) && speaker.state() != CivilianNpc.State.TALK) {
                open.remove(thread);
                continue;
            }
            if (speaker.state() != CivilianNpc.State.TALK) {
                plantTalk(speaker, other);
            }
            if (other.state() != CivilianNpc.State.TALK) {
                plantTalk(other, speaker);
            }
            lookAtNpc(speaker, other);
            lookAtNpc(other, speaker);
            if (thread.left <= 0) {
                finish(thread, speaker, other);
                continue;
            }
            String cue = thread.left <= 1
                    ? "This is the last thing you say before you move. Do not say goodbye like a script."
                    : "Answer them. Do not repeat a line you already said: " + speaker.mind().recentSaid();
            talk.toCivilian(speaker, other, cue);
            thread.left--;
            thread.waiting = other.id();
            thread.nextAt = now + 2800 + ThreadLocalRandom.current().nextInt(1800);
        }
    }

    private void finish(Thread thread, CivilianNpc a, CivilianNpc b) {
        open.remove(thread);
        a.setTalkingTo(null);
        b.setTalkingTo(null);
        a.setTalkLeft(0);
        b.setTalkLeft(0);
        if (ThreadLocalRandom.current().nextBoolean()) {
            a.setFollowing(b.id());
            a.setFollowLeft(80 + ThreadLocalRandom.current().nextInt(80));
            a.setState(CivilianNpc.State.FOLLOW);
            a.mind().did("decided to walk with " + b.name());
            b.mind().did(a.name() + " is walking with me");
            b.setState(CivilianNpc.State.STAND);
            b.setIdleLeft(20);
        } else {
            a.setState(CivilianNpc.State.STAND);
            b.setState(CivilianNpc.State.STAND);
            a.setIdleLeft(25 + ThreadLocalRandom.current().nextInt(30));
            b.setIdleLeft(25 + ThreadLocalRandom.current().nextInt(30));
        }
    }

    private void prune(Collection<CivilianNpc> all) {
        Iterator<Thread> it = open.iterator();
        while (it.hasNext()) {
            Thread thread = it.next();
            CivilianNpc a = find(all, thread.a);
            CivilianNpc b = find(all, thread.b);
            if (a == null || b == null || combat(a) || combat(b)) {
                if (a != null) {
                    a.setTalkingTo(null);
                }
                if (b != null) {
                    b.setTalkingTo(null);
                }
                it.remove();
            }
        }
    }

    private void plantTalk(CivilianNpc npc, CivilianNpc other) {
        npc.clearWalk();
        npc.setState(CivilianNpc.State.TALK);
        npc.setTalkingTo(other.id());
        npc.setTalkLeft(160);
        lookAtNpc(npc, other);
    }

    private String cue(CivilianNpc a, CivilianNpc b) {
        LivingEntity body = a.body();
        if (body == null) {
            return "Start the conversation.";
        }
        EnvironmentSense.Snap env = EnvironmentSense.read(body, a, roster.get());
        String streetBit = street.digest(body.getLocation(), 2);
        if (env.corpse()) {
            return "Start by what you both can see: the body.";
        }
        if (env.wet()) {
            return "Start from the rain. Do not greet.";
        }
        if (env.clock().equals("night")) {
            return "It is night. Start from that.";
        }
        if (streetBit != null && !streetBit.contains("quiet")) {
            return "You might mention what the street has been saying: " + streetBit;
        }
        if (!env.people().isBlank()) {
            return "There is someone else nearby. You can notice them without making a quest of it. " + env.people();
        }
        return "Start a real conversation with " + b.name() + ". You already know the weather and the road.";
    }

    private boolean free(CivilianNpc npc) {
        return npc.alive() && !busy(npc) && !inThread(npc.id())
                && (npc.state() == CivilianNpc.State.STAND
                || npc.state() == CivilianNpc.State.SCAN
                || npc.state() == CivilianNpc.State.WATCH
                || npc.state() == CivilianNpc.State.WARY)
                && npc.canTalk();
    }

    private boolean busy(CivilianNpc npc) {
        return combat(npc) || npc.state() == CivilianNpc.State.FOLLOW
                || npc.state() == CivilianNpc.State.SHELTER
                || npc.state() == CivilianNpc.State.MOURN;
    }

    private static boolean combat(CivilianNpc npc) {
        return switch (npc.state()) {
            case AIM, DRAW, CIRCLE, FLEE, FLINCH, BACKPEDAL -> true;
            default -> false;
        };
    }

    private boolean inThread(UUID id) {
        for (Thread thread : open) {
            if (thread.a.equals(id) || thread.b.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static CivilianNpc find(Collection<CivilianNpc> all, UUID id) {
        for (CivilianNpc npc : all) {
            if (npc.id().equals(id) && npc.alive()) {
                return npc;
            }
        }
        return null;
    }

    private static void lookAtNpc(CivilianNpc npc, CivilianNpc other) {
        LivingEntity a = npc.body();
        LivingEntity b = other.body();
        if (a != null && b != null) {
            npc.lookToward(a.getEyeLocation(), b.getEyeLocation(), 0.28f, 0.16f);
        }
    }

    private static void lookAt(CivilianNpc npc, Player player) {
        LivingEntity body = npc.body();
        if (body != null) {
            npc.lookToward(body.getEyeLocation(), player.getEyeLocation(), 0.24f, 0.12f);
        }
    }

    private static String clip(String text, int max) {
        String clean = text.replace('\n', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max - 1) + "…";
    }
}
