package net.minenite.npcs.civilian;

import net.minenite.npcs.NpcsPlugin;
import net.minenite.warzplugin.WarzPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Same {@code pvpgunminus:gun_pose} bytes WarZ uses. Sent to every online
 * viewer — not just companion-hello clients — keyed by the entity UUID the
 * client will look up in {@code GunPoseClient}.
 */
public final class GunPoseBridge {
    public static final String CHANNEL = "pvpgunminus:gun_pose";
    public static final byte FLAG_GUN = 1;
    public static final byte FLAG_AIM = 2;
    public static final byte FLAG_FIRE = 4;

    private final NpcsPlugin plugin;
    private final Map<UUID, Byte> last = new ConcurrentHashMap<>();

    public GunPoseBridge(NpcsPlugin plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
    }

    public void set(UUID id, boolean gun, boolean aim) {
        set(id, gun, aim, false);
    }

    public void set(UUID id, boolean gun, boolean aim, boolean hipRaise) {
        byte flags = 0;
        if (gun) {
            flags |= FLAG_GUN;
        }
        if (aim) {
            flags |= FLAG_AIM;
        } else if (hipRaise) {
            flags |= FLAG_FIRE;
        }
        Byte prev = last.put(id, flags);
        if (prev != null && prev == flags) {
            return;
        }
        broadcast(id, flags);
    }

    public void refresh() {
        for (Map.Entry<UUID, Byte> entry : last.entrySet()) {
            broadcast(entry.getKey(), entry.getValue());
        }
    }

    public void clear(UUID id) {
        last.remove(id);
        broadcast(id, (byte) 0);
    }

    public void syncViewer(Player viewer) {
        if (viewer == null || NpcBodies.isNpc(viewer)) {
            return;
        }
        for (Map.Entry<UUID, Byte> entry : last.entrySet()) {
            send(viewer, entry.getKey(), entry.getValue());
        }
    }

    public void clearAll() {
        for (UUID id : last.keySet()) {
            broadcast(id, (byte) 0);
        }
        last.clear();
    }

    private void broadcast(UUID id, byte flags) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!NpcBodies.isNpc(viewer)) {
                send(viewer, id, flags);
            }
        }
    }

    private void send(Player viewer, UUID id, byte flags) {
        byte[] payload = encode(id, flags);
        if (payload == null) {
            return;
        }
        WarzPlugin warz = warz();
        if (warz == null) {
            return;
        }
        try {
            viewer.sendPluginMessage(warz, CHANNEL, payload);
        } catch (Exception ignored) {
        }
    }

    private static WarzPlugin warz() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WarzPlugin");
        return plugin instanceof WarzPlugin warz ? warz : null;
    }

    private static byte[] encode(UUID id, byte flags) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(1);
            out.writeLong(id.getMostSignificantBits());
            out.writeLong(id.getLeastSignificantBits());
            out.writeByte(flags);
            return bytes.toByteArray();
        } catch (Exception ignored) {
            return null;
        }
    }
}
