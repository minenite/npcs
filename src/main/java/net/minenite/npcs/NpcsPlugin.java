package net.minenite.npcs;

import net.minenite.npcs.chat.ConversationDirector;
import net.minenite.npcs.chat.LlmTalk;
import net.minenite.npcs.chat.OllamaClient;
import net.minenite.npcs.chat.PlayerChatHook;
import net.minenite.npcs.civilian.NpcManager;
import net.minenite.npcs.command.NpcCommand;
import net.minenite.npcs.mind.WorldMemory;
import net.minenite.npcs.skin.SkinService;
import net.minenite.npcs.tab.TabListPackets;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * WarZ civilian NPCs: they remember, they talk to each other, they
 * answer you, and the local LLM writes the mouth.
 */
public final class NpcsPlugin extends JavaPlugin {

    private SkinService skins;
    private LlmTalk talk;
    private OllamaClient ollama;
    private WorldMemory street;
    private TabListPackets tab;
    private NpcManager npcs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        skins = new SkinService(this);
        talk = new LlmTalk(this);
        ollama = new OllamaClient(this);
        street = new WorldMemory(this);
        ConversationDirector social = new ConversationDirector(this, talk, street);
        tab = new TabListPackets(this);
        npcs = new NpcManager(this, skins, talk, tab, social, street);
        talk.bind(street, ollama, npcs::all);
        social.setRoster(npcs::all);
        npcs.start();
        skins.prefetch();
        getServer().getPluginManager().registerEvents(new PlayerChatHook(this, social), this);
        getServer().getScheduler().runTaskTimer(this, street::save, 20L * 30, 20L * 30);
        var command = getCommand("npc");
        if (command != null) {
            NpcCommand exec = new NpcCommand(this, npcs);
            command.setExecutor(exec);
            command.setTabCompleter(exec);
        }
        getLogger().info("Civilian NPCs ready. Skin=" + getConfig().getString("skin-username", "DirtyCivilian")
                + " llm=" + getConfig().getString("llm.model", "llama3.1:8b"));
        ollama.warm();
    }

    @Override
    public void onDisable() {
        if (street != null) {
            street.save();
        }
        if (npcs != null) {
            npcs.shutdown();
        }
        if (ollama != null) {
            ollama.shutdown();
        }
    }

    public NpcManager npcs() {
        return npcs;
    }
}
