package net.minenite.npcs.chat;

import net.minenite.npcs.civilian.CivilianNpc;
import net.minenite.npcs.mind.EnvironmentSense;
import net.minenite.npcs.mind.WorldMemory;

/**
 * Tight prompts. Small models drown if you dump a novel.
 */
public final class TalkPrompt {
    public enum Beat {
        AIMED("A survivor just aimed a gun at you. You raise yours. Speak like a real mouth under a barrel. One sentence, two if you are breaking."),
        RELIEF("They lowered the gun. You are still holding yours. Speak the leftover fear, not a thank-you speech."),
        SHOT("Shots nearby. Do not narrate the gunshot. Speak to whoever is here, or to yourself if no one is."),
        HURT("You just took a hit. Short. Human. Not a scream-only line unless that is who you are."),
        DYING("You are going down. Last thing a person would actually say. No movie last words."),
        PLAYER("You are talking to a living survivor standing near you. You remember them if the notes say so. Do not greet like a shopkeeper."),
        REPLY("They just spoke to you. Answer what they said. Use your memory. Do not repeat yourself."),
        NPC("You are talking to another civilian survivor, not a player. Use their name. This is two tired people, not a quest."),
        CORPSE("There is a body on the ground. You are looking at it. Do not loot-talk. Be a person."),
        WEATHER("The weather or the hour is on you. One muttered line, not a forecast."),
        GOSSIP("You are passing something you heard or saw to the person in front of you. Keep it specific.");

        final String ask;

        Beat(String ask) {
            this.ask = ask;
        }
    }

    private TalkPrompt() {
    }

    public static String system(CivilianNpc npc, EnvironmentSense.Snap env, WorldMemory street) {
        String name = npc.name();
        String gun = env == null || env.gunInHand().isBlank() ? "a pistol" : env.gunInHand();
        return npc.personality().systemPrompt()
                + "\nYour name here is " + name + ". You carry " + gun + "."
                + "\nYou remember what happened to you. You use it. You do not invent a backstory that is not below."
                + "\nYou remember:\n" + npc.cog().memoryForLlm()
                + npc.cog().talk.threadForPrompt()
                + "\nThe street has been saying: " + street.digest(npc.body() == null ? null : npc.body().getLocation(), 4)
                + (env == null ? "" : "\nRight now: " + env.plain())
                + "\nSpeak-intent: " + npc.cog().whyTalk.name()
                + "\nOutput ONLY the words you speak. No quotes. No name prefix. No stage directions."
                + " One word is fine. Two sentences max. Do not narrate feelings. Do not tell them what they already know."
                + " If you would not talk, output nothing.";
    }

    public static String user(Beat beat, String extra) {
        if (extra == null || extra.isBlank()) {
            return beat.ask;
        }
        return beat.ask + "\n" + extra;
    }
}
