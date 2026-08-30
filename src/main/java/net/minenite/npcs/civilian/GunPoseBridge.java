package net.minenite.npcs.civilian;

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
 * Pushes the same {@code pvpgunminus:gun_pose} bytes WarZ uses for real players
 * so the Minenite client poses a civilian's pistol (carry / aim) by UUID.
 */
public final class GunPoseBridge {
    public static final String CHANNEL = "pvpgunminus:gun_pose";
    public static final byte FLAG_GUN = 1;
    public static final byte FLAG_AIM = 2;

    private final Map<UUID, Byte> last = new ConcurrentHashMap<>();

    public void set(UUID id, boolean gun, boolean aim) {
        byte flags = 0;
        if (gun) {
            flags |= FLAG_GUN;
        }
        if (aim) {
            flags |= FLAG_AIM;
        }
        Byte prev = last.put(id, flags);
        if (prev != null && prev == flags) {
            return;
        }
        broadcast(id, flags);
    }

    public void clear(UUID id) {
        last.remove(id);
        broadcast(id, (byte) 0);
    }

    public void syncViewer(Player viewer) {
        WarzPlugin warz = warz();
        if (warz == null || !companion(warz, viewer)) {
            return;
        }
        for (Map.Entry<UUID, Byte> entry : last.entrySet()) {
            send(warz, viewer, entry.getKey(), entry.getValue());
        }
    }

    public void clearAll() {
        for (UUID id : last.keySet()) {
            broadcast(id, (byte) 0);
        }
        last.clear();
    }

    private void broadcast(UUID id, byte flags) {
        WarzPlugin warz = warz();
        if (warz == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (companion(warz, viewer)) {
                send(warz, viewer, id, flags);
            }
        }
    }

    private static void send(WarzPlugin warz, Player viewer, UUID id, byte flags) {
        byte[] payload = encode(id, flags);
        if (payload != null) {
            viewer.sendPluginMessage(warz, CHANNEL, payload);
        }
    }

    private static boolean companion(WarzPlugin warz, Player viewer) {
        return warz.companions() != null && warz.companions().hasCompanion(viewer);
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
