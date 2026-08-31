package net.minenite.npcs.cognition;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/** Deterministic probes — flag loops, not a full game sim. */
public final class Probe {
    private Probe() {
    }

    public static List<String> run() {
        List<String> flags = new ArrayList<>();
        Traits t = new Traits(net.minenite.npcs.civilian.Personality.PARANOID_LONER);
        Cognition cog = new Cognition(net.minenite.npcs.civilian.Personality.PARANOID_LONER, "Rook", 0, 0);
        WorldCue cue = new WorldCue();
        Intention last = null;
        int flips = 0;
        for (int i = 0; i < 40; i++) {
            cog.drives.tick(t, false, true, 0.05, 0, 0, false, false);
            Intention now = Utility.pick(cog, cue);
            if (last != null && now != last) {
                flips++;
            }
            last = now;
        }
        if (flips > 22) {
            flags.add("hysteresis weak: " + flips + " flips in 40 calm ticks");
        }
        cue.aimedAt = 1;
        cue.threat = 0.9;
        cog.drives.fear = 0.8;
        Intention threat = Utility.pick(cog, cue);
        if (threat != Intention.DEFEND_SELF && threat != Intention.ESCAPE && threat != Intention.HIDE
                && threat != Intention.INTIMIDATE && threat != Intention.SURVIVE) {
            flags.add("aimed-at did not raise a survival intention: " + threat);
        }
        Episode ep = new Episode();
        ep.at = System.currentTimeMillis() - 20 * 60_000L;
        ep.who = "icedmoca";
        ep.what = "fired three shots";
        ep.where = "18 blocks east";
        ep.certainty = 0.9;
        String late = ep.recall();
        if (late.contains("18 blocks")) {
            flags.add("memory did not compress: " + late);
        }
        Rumor a = new Rumor();
        a.text = "I saw icedmoca aim at Lev";
        a.hops = 0;
        a.confidence = 0.9;
        Rumor b = a.mutate().mutate();
        if (b.hops != 2 || b.confidence >= a.confidence) {
            flags.add("rumor hops/confidence did not decay");
        }
        DriveSet d = new DriveSet(t);
        double fear0 = d.fear;
        d.fear = 0.9;
        for (int i = 0; i < 400; i++) {
            d.tick(t, false, true, 0, 0, 0, false, false);
        }
        if (d.fear < 0.15) {
            flags.add("fear recovered too fast: " + d.fear);
        }
        if (d.fear > 0.89) {
            flags.add("fear never recovers: " + d.fear);
        }
        LastSeen seen = new LastSeen();
        seen.x = 100;
        seen.z = -50;
        seen.y = 64;
        seen.heading = 0;
        seen.at = System.currentTimeMillis() - 8_000L;
        seen.world = "dummy";
        org.bukkit.World w = null;
        try {
            w = org.bukkit.Bukkit.getWorlds().isEmpty() ? null : org.bukkit.Bukkit.getWorlds().get(0);
        } catch (Exception ignored) {
        }
        if (w != null) {
            seen.world = w.getName();
            Location guess = seen.guess(w);
            if (guess != null && Math.abs(guess.getX() - 100) < 0.01 && Math.abs(guess.getZ() + 50) < 0.01) {
                flags.add("lastSeen guess was exact live coordinates");
            }
        }
        Traits quiet = new Traits(net.minenite.npcs.civilian.Personality.STOIC_FARMER);
        Cognition mute = new Cognition(net.minenite.npcs.civilian.Personality.STOIC_FARMER, "Irena", 0, 0);
        mute.drives.fear = 0.7;
        mute.drives.stress = 0.7;
        WorldCue armed = new WorldCue();
        armed.armedStranger = 1;
        Intention hush = Utility.pick(mute, armed);
        if (hush != Intention.SILENCE && hush != Intention.AVOID && hush != Intention.OBSERVE
                && hush != Intention.HIDE) {
            flags.add("armed stranger did not raise quiet/avoid: " + hush);
        }
        if (flags.isEmpty()) {
            flags.add("ok hysteresis=" + flips + " fear400=" + String.format("%.2f", d.fear)
                    + " recall=" + late + " rumorConf=" + String.format("%.2f", b.confidence));
        }
        return flags;
    }
}
