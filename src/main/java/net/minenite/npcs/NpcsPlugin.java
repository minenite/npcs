package net.minenite.npcs;

import net.minenite.npcs.chat.LlmTalk;
import net.minenite.npcs.civilian.NpcManager;
import net.minenite.npcs.command.NpcCommand;
import net.minenite.npcs.skin.SkinService;
import net.minenite.npcs.tab.TabListPackets;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * WarZ civilian NPCs: fake players that wander, talk, draw, ADS, and die
 * like people rather than props.
 */
public final class NpcsPlugin extends JavaPlugin {

    private SkinService skins;
    private LlmTalk talk;
    private TabListPackets tab;
    private NpcManager npcs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        skins = new SkinService(this);
        talk = new LlmTalk(this);
        tab = new TabListPackets(this);
        npcs = new NpcManager(this, skins, talk, tab);
        npcs.start();
        skins.prefetch();
        var command = getCommand("npc");
        if (command != null) {
            NpcCommand exec = new NpcCommand(this, npcs);
            command.setExecutor(exec);
            command.setTabCompleter(exec);
        }
        getLogger().info("Civilian NPCs ready. Skin=" + getConfig().getString("skin-username", "DirtyCivilian")
                + " llm=" + getConfig().getString("llm.model", "phi3:mini"));
    }

    @Override
    public void onDisable() {
        if (npcs != null) {
            npcs.shutdown();
        }
    }

    public NpcManager npcs() {
        return npcs;
    }
}
