package net.minenite.npcs.mind;

/**
 * How this person feels right now. Talk and movement both read it.
 */
public enum Mood {
    CALM,
    WARY,
    AFRAID,
    ANGRY,
    RELIEVED,
    LONELY,
    TIRED,
    GRIEF;

    public String spoken() {
        return switch (this) {
            case CALM -> "steady, not looking for trouble";
            case WARY -> "watching hands and exits";
            case AFRAID -> "scared, trying not to show all of it";
            case ANGRY -> "angry and done being polite";
            case RELIEVED -> "the worst just passed, still shaking it off";
            case LONELY -> "tired of being alone";
            case TIRED -> "exhausted, words come slower";
            case GRIEF -> "someone is on the ground and it is sitting in you";
        };
    }
}
