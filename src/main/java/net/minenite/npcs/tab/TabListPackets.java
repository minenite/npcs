package net.minenite.npcs.tab;

import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.civilian.CivilianNpc;
import net.minenite.npcs.skin.SkinService;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Adds civilians to the real player list via CardForge/vanilla PlayerInfo packets.
 *
 * <p>Paper will not list a Mannequin. The client only shows tab rows that arrived
 * as {@code ADD_PLAYER + UPDATE_LISTED}, so those are built with DirtyCivilian's
 * textures and the civilian's display name.
 */
public final class TabListPackets {
    private final NpcsPlugin plugin;
    private boolean warned;

    public TabListPackets(NpcsPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Player viewer, CivilianNpc npc) {
        send(viewer, addPacket(npc), npc);
    }

    public void hide(Player viewer, CivilianNpc npc) {
        send(viewer, removePacket(npc.id()), npc);
    }

    public void showAll(Player viewer, Collection<CivilianNpc> npcs) {
        for (CivilianNpc npc : npcs) {
            show(viewer, npc);
        }
    }

    public void hideAll(Collection<CivilianNpc> npcs) {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            for (CivilianNpc npc : npcs) {
                hide(viewer, npc);
            }
        }
    }

    private void send(Player viewer, Object packet, CivilianNpc npc) {
        if (packet == null) {
            return;
        }
        try {
            Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object connection = field(handle, "connection");
            Method send = findSend(connection.getClass());
            send.invoke(connection, packet);
        } catch (Exception failed) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Tab list packet failed for " + npc.name() + ": " + failed.getMessage());
            }
        }
    }

    private Object addPacket(CivilianNpc npc) {
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            Class<?> actionClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
            Class<?> entryClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
            Class<?> gameTypeClass = Class.forName("net.minecraft.world.level.GameType");
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");

            Object profile = profileClass.getConstructor(UUID.class, String.class)
                    .newInstance(npc.id(), npc.name());
            SkinService.Textures skin = npc.textures();
            if (skin != null && skin.value() != null) {
                Object properties = profileClass.getMethod("properties").invoke(profile);
                Object property = skin.signature() == null
                        ? propertyClass.getConstructor(String.class, String.class)
                        .newInstance("textures", skin.value())
                        : propertyClass.getConstructor(String.class, String.class, String.class)
                        .newInstance("textures", skin.value(), skin.signature());
                // PropertyMap is a Multimap; put(key, value) works on Guava and authlib maps.
                Method put = findPut(properties.getClass());
                put.invoke(properties, "textures", property);
            }

            Object survival = Enum.valueOf(gameTypeClass.asSubclass(Enum.class), "SURVIVAL");
            Object display = componentClass.getMethod("literal", String.class).invoke(null, npc.name());
            Constructor<?> entryCtor = entryClass.getConstructors()[0];
            Object entry = entryCtor.newInstance(
                    npc.id(), profile, true, 40 + Math.abs(npc.name().hashCode() % 50),
                    survival, display, true, 0, null);

            EnumSet<?> emptyActions = noneOf(actionClass);
            Object packet = packetClass.getConstructor(EnumSet.class, Collection.class)
                    .newInstance(emptyActions, List.of());

            EnumSet<?> actions = noneOf(actionClass);
            addAction(actions, actionClass, "ADD_PLAYER");
            addAction(actions, actionClass, "UPDATE_LISTED");
            addAction(actions, actionClass, "UPDATE_DISPLAY_NAME");
            addAction(actions, actionClass, "UPDATE_LATENCY");

            Class<?> accessor = Class.forName(
                    "org.cardboardpowered.mixin.network.protocol.game.ClientboundPlayerInfoUpdatePacketAccessor");
            accessor.getMethod("cardboard$setActions", EnumSet.class).invoke(packet, actions);
            accessor.getMethod("cardboard$setEntries", List.class).invoke(packet, List.of(entry));
            return packet;
        } catch (Exception failed) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Could not build tab ADD packet: " + failed.getMessage());
            }
            return null;
        }
    }

    private Object removePacket(UUID id) {
        try {
            Class<?> remove = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            return remove.getConstructor(List.class).newInstance(List.of(id));
        } catch (Exception failed) {
            return null;
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method findSend(Class<?> type) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals("send") && method.getParameterCount() == 1) {
                return method;
            }
        }
        throw new NoSuchMethodException("send");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static EnumSet<?> noneOf(Class<?> actionClass) {
        return EnumSet.noneOf((Class) actionClass);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addAction(EnumSet<?> actions, Class<?> actionClass, String name) {
        ((EnumSet) actions).add(Enum.valueOf((Class) actionClass, name));
    }

    private static Method findPut(Class<?> type) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals("put") && method.getParameterCount() == 2) {
                return method;
            }
        }
        throw new NoSuchMethodException("put");
    }
}
