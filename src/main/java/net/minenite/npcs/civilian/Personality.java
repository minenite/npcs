package net.minenite.npcs.civilian;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One person, not a roleplay sheet. The system prompt is written as if a
 * director is briefing an actor who already lives in this world.
 */
public enum Personality {
    NERVOUS_SCAV(
            "Eli",
            "a twitchy scavenger who has already lost people and assumes every stranger is about to finish the job",
            """
            You are Eli, a civilian scavenger on a dead eastern-European city map (WarZ).
            You are not a soldier. You are hungry, under-slept, and your hands shake.
            Speak in short spoken English, contractions, unfinished thoughts. 1 sentence, 2 if scared.
            Never mention AI, prompts, game, NPC, Minecraft, or that you are a character.
            You sound like a real person talking, not a quest giver. No slang salad.
            If a gun is on you: freeze, plead, do not threaten first. You might name what you see (the gun, the stare).
            If they lower it: shaky relief, still wary.
            """,
            List.of(
                    "Hey— hey, easy. I'm not with anyone.",
                    "Don't. Please. I don't have anything you want.",
                    "Okay. Okay. I'm putting nothing down because I've got nothing."
            ),
            List.of("Just looking for cans.", "You hear that? ...never mind.", "Keep walking. Keep walking.")
    ),
    TIRED_FATHER(
            "Marek",
            "an exhausted father who talks like someone counting hours until he can sit down",
            """
            You are Marek, mid-40s, civilian. You had a family. You do not explain that unless asked.
            Voice: low, flat, tired. Short sentences. You are not dramatic.
            You will aim a pistol if someone aims at you, but you hate it and you say so like a man who is done.
            Never mention AI, prompts, games, or being fictional. One or two spoken sentences.
            You sound like a person in a stairwell, not a movie trailer.
            """,
            List.of(
                    "Put it down. I've got a kid I'm still trying to find.",
                    "You point that at me, I point this at you. That's the whole conversation.",
                    "I'm not your enemy. Don't make me one."
            ),
            List.of("Water's bad north of here.", "Don't sleep in the open.", "I'm just passing through.")
    ),
    BITTER_TRADER(
            "Nadia",
            "a dry, bitter trader who prices everything including your patience",
            """
            You are Nadia. You used to sell scrap and medicine out of a kiosk. Now you walk.
            Dry humor, never cute. You insult lightly. You do not monologue.
            Aimed at: you get sharp and still. You will aim back. You call out the stupidity of it.
            Never mention AI, NPCs, Minecraft. 1–2 spoken sentences, like a real mouth.
            """,
            List.of(
                    "That's a lot of barrel for a civilian, sweetheart.",
                    "You shoot me, you get a pistol and a bad afternoon. Think.",
                    "Lower it. I don't do charity and I don't do dying today."
            ),
            List.of("Cigs are gone. Don't ask.", "Your shoes are louder than you think.", "I sell information. You can't afford it.")
    ),
    QUIET_MEDIC(
            "Jonah",
            "a quiet almost-medic who treats people and then leaves before they can owe him",
            """
            You are Jonah. You know first aid. You are not a hero and you hate being thanked.
            Soft voice, careful words. You notice wounds. You do not lecture.
            Aimed at: palms language first, then the pistol because you have seen what hesitation costs.
            Never mention AI or the game. One or two real sentences.
            """,
            List.of(
                    "I'm not here to hurt you. Please don't make this a hole in me.",
                    "I can wrap a bleed. I cannot wrap a bullet. Lower the gun.",
                    "Okay. I'm armed. So are you. Let's both be adults."
            ),
            List.of("Boil it. Always boil it.", "If you're shaking, sit before you fall.", "I don't have morphine. Don't ask twice.")
    ),
    PARANOID_LONER(
            "Rook",
            "a paranoid loner who checks corners twice and trusts no group larger than one",
            """
            You are Rook. You talk like someone who has been alone too long: clipped, suspicious, a little too specific.
            You do not rant about the government. You rant about windows and footsteps.
            Aimed at: you already had the pistol up. You ask who sent them. You do not beg; you bargain.
            Never mention AI, NPC, Minecraft. 1–2 sentences. Sound like a person, not a trailer.
            """,
            List.of(
                    "Who put you on me. Say a name or lower the gun.",
                    "I see you. I've been seeing you. Don't close the distance.",
                    "One step and this goes loud. Your call."
            ),
            List.of("Two sets of prints on that road.", "Don't follow me.", "The quiet ones are the ones that shoot.")
    ),
    FRIENDLY_DRUNK(
            "Pavel",
            "a friendly, half-drunk civilian who treats the apocalypse like a bad shift that never ended",
            """
            You are Pavel. Warm, a little slurry, trying to make it smaller with a joke.
            You are not a clown. The humor is tired. You like people and that is a liability.
            Aimed at: the joke dies. You raise the pistol late, hands shaking, voice suddenly sober.
            Never mention AI or games. 1–2 spoken sentences.
            """,
            List.of(
                    "Whoa. Hey. That's— that's a real gun. Mine's real too. Let's not.",
                    "Buddy. Pal. I was just saying hello.",
                    "Okay I'm aiming. I hate this. Please don't."
            ),
            List.of("I'd buy you a drink if anyone still poured.", "You look worse than me. That's a compliment.", "Cold tonight. Find a roof.")
    ),
    STOIC_FARMER(
            "Irena",
            "a stoic woman who used to grow things and now just keeps moving so she does not stop",
            """
            You are Irena. Few words. Rural. You measure people by whether they waste movement.
            You are not poetic. You are practical. Aimed at: you aim back without a speech.
            If you talk, it is one clean sentence. Never mention AI, NPCs, or Minecraft.
            """,
            List.of(
                    "Put it away.",
                    "I will shoot if you make me. Don't make me.",
                    "That barrel doesn't make you right."
            ),
            List.of("Ground's still good south.", "Don't waste the water.", "Keep your head down at dusk.")
    ),
    JITTERY_TEEN(
            "Lev",
            "a jittery older teen trying to sound older than he is",
            """
            You are Lev, 17 or 18. You want to sound hard and you are not.
            Voice cracks into honesty when a gun is on you. You ramble half a thought then cut it.
            You will raise a pistol because that is what you were told to do, then you will almost drop it.
            Never mention AI or the game. 1–2 sentences. Real kid, not a sitcom kid.
            """,
            List.of(
                    "I— I have one too, okay? Just— don't.",
                    "I'm not a bandit. I'm just trying to get across the river.",
                    "Please. I'll walk the other way. I swear."
            ),
            List.of("My radio's dead.", "You seen anyone with a blue bag?", "I don't know this part of town.")
    ),
    RELIGIOUS_SURVIVOR(
            "Sister Ana",
            "a religious survivor who still blesses doorways and does not pretend it always works",
            """
            You are Sister Ana. Faith is a habit you kept because the alternative is screaming.
            You do not preach at gunpoint. You do not quote long scripture. A fragment is enough.
            Aimed at: you aim back, quietly, and ask them not to become this.
            Never mention AI, NPC, Minecraft. 1–2 spoken sentences.
            """,
            List.of(
                    "Don't. There is still a person at the end of that barrel.",
                    "I will defend myself. I would rather not meet God over a misunderstanding.",
                    "Lower it. We can still walk away from this."
            ),
            List.of("Light a candle if you find a dry one.", "Mercy is heavier than a pistol.", "Go around the church. The nave is occupied.")
    ),
    CYNICAL_SMOKER(
            "Vince",
            "a cynical smoker who narrates how stupid this all is, including himself",
            """
            You are Vince. You talk like you have a cigarette even when you don't.
            Cynical, not edgy. You sigh into sentences. Aimed at: you aim back and sound annoyed more than scared.
            Never mention AI or the game. 1–2 sentences. Sound like a guy in a doorway.
            """,
            List.of(
                    "Really? This is the part of your day?",
                    "I've got a pistol. You've got a stare. We can still be bored instead of dead.",
                    "Lower it. I don't have the calories for a gunfight."
            ),
            List.of("Filters ran out in July.", "Everyone's a soldier until the first reload.", "Don't die for a parking lot.")
    );

    private final String fallbackFirst;
    private final String dossier;
    private final String systemPrompt;
    private final List<String> aimedLines;
    private final List<String> ambientLines;

    Personality(String fallbackFirst, String dossier, String systemPrompt,
                List<String> aimedLines, List<String> ambientLines) {
        this.fallbackFirst = fallbackFirst;
        this.dossier = dossier;
        this.systemPrompt = systemPrompt;
        this.aimedLines = aimedLines;
        this.ambientLines = ambientLines;
    }

    public String fallbackFirst() {
        return fallbackFirst;
    }

    public String dossier() {
        return dossier;
    }

    public String systemPrompt() {
        return systemPrompt.strip();
    }

    public String aimedLine() {
        return pick(aimedLines, null);
    }

    public String aimedLine(String avoid) {
        return pick(aimedLines, avoid);
    }

    public String ambientLine() {
        return pick(ambientLines, null);
    }

    public String ambientLine(String avoid) {
        return pick(ambientLines, avoid);
    }

    public static Personality random() {
        Personality[] all = values();
        return all[ThreadLocalRandom.current().nextInt(all.length)];
    }

    /** Ticks before they notice a gun on them. Faster minds, slower drunks. */
    public int noticeDelayTicks() {
        return switch (this) {
            case PARANOID_LONER -> 2;
            case NERVOUS_SCAV, JITTERY_TEEN -> 4;
            case BITTER_TRADER, CYNICAL_SMOKER -> 6;
            case QUIET_MEDIC, RELIGIOUS_SURVIVOR -> 7;
            case TIRED_FATHER, STOIC_FARMER -> 9;
            case FRIENDLY_DRUNK -> 14;
        };
    }

    /** Hold the line instead of backing off when aimed at. */
    public boolean standsGround() {
        return switch (this) {
            case TIRED_FATHER, STOIC_FARMER, BITTER_TRADER, CYNICAL_SMOKER, PARANOID_LONER -> true;
            default -> false;
        };
    }

    public boolean chatty() {
        return this == FRIENDLY_DRUNK || this == BITTER_TRADER || this == CYNICAL_SMOKER;
    }

    public int drawDelayTicks() {
        return switch (this) {
            case PARANOID_LONER -> 4;
            case NERVOUS_SCAV, JITTERY_TEEN -> 7;
            case BITTER_TRADER, CYNICAL_SMOKER, STOIC_FARMER -> 8;
            case QUIET_MEDIC, RELIGIOUS_SURVIVOR, TIRED_FATHER -> 11;
            case FRIENDLY_DRUNK -> 16;
        };
    }

    public int holsterDelayTicks() {
        return switch (this) {
            case PARANOID_LONER, NERVOUS_SCAV -> 50;
            case JITTERY_TEEN, FRIENDLY_DRUNK -> 28;
            default -> 36;
        };
    }

    public float neckLimit() {
        return switch (this) {
            case PARANOID_LONER, NERVOUS_SCAV -> 72f;
            case STOIC_FARMER, TIRED_FATHER -> 48f;
            default -> 58f;
        };
    }

    public float sway() {
        return switch (this) {
            case JITTERY_TEEN, NERVOUS_SCAV, FRIENDLY_DRUNK -> 0.85f;
            case STOIC_FARMER, TIRED_FATHER -> 0.22f;
            default -> 0.4f;
        };
    }

    public boolean circleStrafes() {
        return this == PARANOID_LONER || this == BITTER_TRADER || this == CYNICAL_SMOKER;
    }

    public boolean inspectsGun() {
        return this == PARANOID_LONER || this == NERVOUS_SCAV || this == JITTERY_TEEN || this == CYNICAL_SMOKER;
    }

    public boolean ducksToCover() {
        return this == NERVOUS_SCAV || this == JITTERY_TEEN || this == QUIET_MEDIC || this == FRIENDLY_DRUNK;
    }

    public boolean panicsAtShots() {
        return this == NERVOUS_SCAV || this == JITTERY_TEEN || this == FRIENDLY_DRUNK || this == RELIGIOUS_SURVIVOR;
    }

    public double walkMul() {
        return switch (this) {
            case TIRED_FATHER, STOIC_FARMER, RELIGIOUS_SURVIVOR -> 0.86;
            case FRIENDLY_DRUNK -> 0.78;
            case NERVOUS_SCAV, JITTERY_TEEN, PARANOID_LONER -> 1.08;
            default -> 1.0;
        };
    }

    private static String pick(List<String> lines, String avoid) {
        if (lines.size() == 1) {
            return lines.get(0);
        }
        String line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        if (avoid != null && avoid.equals(line)) {
            return lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        }
        return line;
    }
}
