package net.minenite.npcs.cognition;

import net.minenite.npcs.civilian.Personality;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** They existed before you arrived. Never dumped into dialogue. */
public final class Life {
    public String origin;
    public String occupation;
    public String worry;
    public String want;
    public String homeName;
    public double homeX, homeZ;
    public String usualArea;
    public String recent;
    public String friendName = "";

    public static Life roll(Personality p, String name, double x, double z) {
        Life life = new Life();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        life.origin = pick(List.of("the south blocks", "the river flats", "the old rail yards",
                "a village past the forest", "the east apartments"));
        life.occupation = switch (p) {
            case QUIET_MEDIC -> "almost-medic";
            case BITTER_TRADER -> "trader";
            case STOIC_FARMER -> "grew food";
            case RELIGIOUS_SURVIVOR -> "kept a chapel";
            case TIRED_FATHER -> "had a family";
            case CYNICAL_SMOKER -> "warehouse night shift";
            default -> pick(List.of("scavenged", "moved boxes", "drove when there was fuel", "kept a stall"));
        };
        life.worry = pick(List.of("running out of water", "the next night", "who is left",
                "the noise from the highway", "not finding a roof"));
        life.want = pick(List.of("a dry room", "canned food", "to find one person",
                "to get west", "to not shoot anyone"));
        life.homeName = pick(List.of("the gas station", "the pharmacy wall", "the warehouse lee",
                "the apartment stair", "the market stall"));
        life.homeX = x + (rng.nextDouble() - 0.5) * 40;
        life.homeZ = z + (rng.nextDouble() - 0.5) * 40;
        life.usualArea = pick(List.of("this grid", "the market", "the tree line", "the inner roads"));
        life.recent = pick(List.of("slept badly", "heard shooting yesterday", "lost a bag",
                "shared a can with a stranger", "walked from the south"));
        return life;
    }

    private static String pick(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}
