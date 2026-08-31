package net.minenite.npcs;

import net.minenite.npcs.chat.ConversationDirector;
import net.minenite.npcs.chat.LlmTalk;
import net.minenite.npcs.chat.OllamaClient;
import net.minenite.npcs.chat.PlayerChatHook;
import net.minenite.npcs.cognition.PersistLives;
import net.minenite.npcs.civilian.NpcManager;
import net.minenite.npcs.command.NpcCommand;
import net.minenite.npcs.mind.WorldMemory;
import net.minenite.npcs.skin.SkinService;
import net.minenite.npcs.tab.TabListPackets;
import org.bukkit.plugin.java.JavaPlugin;

public final class NpcsPlugin extends JavaPlugin {

    private SkinService skins;
    private LlmTalk talk;
    private OllamaClient ollama;
    private WorldMemory street;
    private PersistLives lives;
    private TabListPackets tab;
    private NpcManager npcs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        skins = new SkinService(this);
        talk = new LlmTalk(this);
        ollama = new OllamaClient(this);
        street = new WorldMemory(this);
        lives = new PersistLives(this);
        ConversationDirector social = new ConversationDirector(this, talk, street);
        tab = new TabListPackets(this);
        npcs = new NpcManager(this, skins, talk, tab, social, street, lives);
        talk.bind(street, ollama, npcs::all);
        social.setRoster(npcs::all);
        npcs.start();
        getServer().getScheduler().runTaskLater(this, npcs::restore, 60L);
        getServer().getScheduler().runTaskTimer(this, npcs::snapshotLives, 20L * 45, 20L * 45);
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

    public OllamaClient ollama() {
        return ollama;
    }
}
