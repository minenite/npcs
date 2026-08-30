package net.minenite.npcs.civilian;

import java.util.concurrent.ThreadLocalRandom;

/** Short first names that fit a 16-char Minecraft tab slot with a last initial. */
public final class CivilianNames {
    private static final String[] FIRST = {
            "Eli", "Marek", "Nadia", "Jonah", "Rook", "Pavel", "Irena", "Lev", "Ana", "Vince",
            "Oskar", "Katya", "Dima", "Vera", "Tomas", "Lina", "Boris", "Sofi", "Yuri", "Hana",
            "Petr", "Mila", "Anton", "Greta", "Niko", "Ola", "Ivan", "Sasha", "Lukas", "Eva"
    };
    private static final String[] LAST = {
            "Novak", "Horvath", "Kovac", "Petrov", "Volkov", "Nagy", "Kline", "Dunn", "Reeves",
            "Hale", "Voss", "Keller", "Moran", "Beck", "Frost", "Adler", "Shaw", "Quinn"
    };

    private CivilianNames() {}

    public static String roll(Personality personality) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        String first = rng.nextBoolean() ? personality.fallbackFirst() : FIRST[rng.nextInt(FIRST.length)];
        if (first.contains(" ")) {
            first = first.substring(first.lastIndexOf(' ') + 1);
        }
        String last = LAST[rng.nextInt(LAST.length)];
        String full = first + last;
        if (full.length() <= 16) {
            return full;
        }
        String compact = first + last.charAt(0);
        return compact.length() <= 16 ? compact : first.substring(0, Math.min(16, first.length()));
    }
}
