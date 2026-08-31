package net.minenite.npcs.cognition;

/**
 * What this person currently wants. The deterministic brain executes these;
 * it does not invent them from if/else trees.
 */
public enum Intention {
    SURVIVE,
    ESCAPE,
    HIDE,
    INVESTIGATE,
    OBSERVE,
    APPROACH,
    AVOID,
    FOLLOW,
    SEARCH,
    REST,
    LOOT,
    SEEK_SHELTER,
    SEEK_COMPANY,
    HELP_PERSON,
    WARN_PERSON,
    TRADE,
    ASK_FOR_HELP,
    INTIMIDATE,
    DEFEND_SELF,
    MOURN,
    CHECK_CORPSE,
    SEARCH_BUILDING,
    WATCH_ENTRANCE,
    LISTEN,
    WAIT,
    PATROL,
    TRAVEL,
    RETURN_HOME,
    LOOK_FOR_FRIEND,
    LOOK_FOR_RESOURCE,
    SILENCE;

    public boolean combat() {
        return this == ESCAPE || this == HIDE || this == DEFEND_SELF || this == INTIMIDATE || this == SURVIVE;
    }

    public boolean social() {
        return this == SEEK_COMPANY || this == HELP_PERSON || this == WARN_PERSON
                || this == TRADE || this == ASK_FOR_HELP || this == APPROACH || this == FOLLOW;
    }
}
