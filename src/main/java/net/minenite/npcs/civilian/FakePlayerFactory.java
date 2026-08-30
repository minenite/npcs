package net.minenite.npcs.civilian;

import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.skin.SkinService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Real {@link Player} in the world so WarZ hold/ADS uses the Avatar/player
 * pipeline. Not added to the login PlayerList — tab is our own packets.
 */
public final class FakePlayerFactory {
    private FakePlayerFactory() {}

    public static Player spawn(NpcsPlugin plugin, Location at, UUID id, String name, SkinService.Textures skin) {
        try {
            Object nmsServer = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            Object nmsLevel = at.getWorld().getClass().getMethod("getHandle").invoke(at.getWorld());
            Object profile = profile(id, clip(name), skin);

            Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
            Object info = infoClass.getMethod("createDefault").invoke(null);

            Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Object nmsPlayer = construct(playerClass, nmsServer, nmsLevel, profile, info);
            if (nmsPlayer == null) {
                plugin.getLogger().warning("No ServerPlayer constructor accepted the fake civilian args.");
                return null;
            }

            attachDummyConnection(plugin, nmsServer, nmsPlayer, profile);
            move(nmsPlayer, at);

            boolean added = (boolean) nmsLevel.getClass()
                    .getMethod("addFreshEntity", Class.forName("net.minecraft.world.entity.Entity"))
                    .invoke(nmsLevel, nmsPlayer);
            if (!added) {
                plugin.getLogger().warning("addFreshEntity refused fake player " + name);
                return null;
            }

            Object bukkit = invoke(nmsPlayer, "getBukkitEntity");
            if (!(bukkit instanceof Player player)) {
                plugin.getLogger().warning("Fake player " + name + " was not a Bukkit Player");
                return null;
            }
            player.setGravity(false);
            player.setInvulnerable(false);
            player.setCanPickupItems(false);
            player.setSleepingIgnored(true);
            player.setCollidable(true);
            player.setSneaking(false);
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.setHealth(Math.min(player.getMaxHealth(), 16));
            player.addScoreboardTag("minenite_npc");
            plugin.getLogger().info("Civilian " + name + " spawned as fake player " + player.getUniqueId());
            return player;
        } catch (Exception failed) {
            plugin.getLogger().log(Level.WARNING, "Fake player spawn failed for " + name, failed);
            return null;
        }
    }

    private static Object profile(UUID id, String name, SkinService.Textures skin) throws Exception {
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(id, name);
        if (skin == null || skin.value() == null) {
            return profile;
        }
        Object properties = profileClass.getMethod("properties").invoke(profile);
        Object property = skin.signature() == null
                ? propertyClass.getConstructor(String.class, String.class)
                .newInstance("textures", skin.value())
                : propertyClass.getConstructor(String.class, String.class, String.class)
                .newInstance("textures", skin.value(), skin.signature());
        for (Method method : properties.getClass().getMethods()) {
            if (method.getName().equals("put") && method.getParameterCount() == 2) {
                method.invoke(properties, "textures", property);
                break;
            }
        }
        return profile;
    }

    private static void attachDummyConnection(NpcsPlugin plugin, Object nmsServer, Object nmsPlayer, Object profile) {
        try {
            Class<?> packetFlow = Class.forName("net.minecraft.network.protocol.PacketFlow");
            Object serverbound = enumOf(packetFlow, "SERVERBOUND");
            Class<?> connClass = Class.forName("net.minecraft.network.Connection");
            Object conn = connClass.getConstructor(packetFlow).newInstance(serverbound);
            tryEmbed(conn);

            Class<?> cookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
            Object cookie = cookie(cookieClass, profile);
            Class<?> listenerClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
            Object listener = construct(listenerClass, nmsServer, conn, nmsPlayer, cookie);
            if (listener == null) {
                plugin.getLogger().warning("Could not build dummy connection for fake player; tick may be noisy.");
                return;
            }
            Field field = field(nmsPlayer.getClass(), "connection");
            if (field != null) {
                field.setAccessible(true);
                field.set(nmsPlayer, listener);
            }
        } catch (Exception failed) {
            plugin.getLogger().log(Level.WARNING, "Dummy connection failed", failed);
        }
    }

    private static Object cookie(Class<?> cookieClass, Object profile) throws Exception {
        for (Method method : cookieClass.getMethods()) {
            if (!method.getName().equals("createInitial") || !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            Class<?>[] args = method.getParameterTypes();
            Object[] values = new Object[args.length];
            boolean ok = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i].getName().contains("GameProfile")) {
                    values[i] = profile;
                } else if (args[i] == boolean.class) {
                    values[i] = false;
                } else if (args[i] == int.class) {
                    values[i] = 0;
                } else {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return method.invoke(null, values);
            }
        }
        return construct(cookieClass, profile, false);
    }

    private static void tryEmbed(Object conn) {
        try {
            Field channel = field(conn.getClass(), "channel");
            if (channel == null) {
                return;
            }
            channel.setAccessible(true);
            Class<?> embedded = Class.forName("io.netty.channel.embedded.EmbeddedChannel");
            channel.set(conn, embedded.getConstructor().newInstance());
        } catch (Exception ignored) {
        }
    }

    private static void move(Object nmsPlayer, Location at) throws Exception {
        Method snap = find(nmsPlayer.getClass(), "snapTo", double.class, double.class, double.class, float.class, float.class);
        if (snap != null) {
            snap.invoke(nmsPlayer, at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
            return;
        }
        Method setPos = find(nmsPlayer.getClass(), "setPos", double.class, double.class, double.class);
        if (setPos != null) {
            setPos.invoke(nmsPlayer, at.getX(), at.getY(), at.getZ());
        }
    }

    private static Object construct(Class<?> type, Object... args) {
        List<Constructor<?>> ctors = new ArrayList<>(Arrays.asList(type.getConstructors()));
        ctors.addAll(Arrays.asList(type.getDeclaredConstructors()));
        ctors.sort(Comparator.comparingInt((Constructor<?> ctor) -> -matched(ctor.getParameterTypes(), args)));
        Exception last = null;
        for (Constructor<?> ctor : ctors) {
            Class<?>[] params = ctor.getParameterTypes();
            if (matched(params, args) < Math.min(2, args.length)) {
                continue;
            }
            Object[] values = bind(params, args);
            if (values == null) {
                continue;
            }
            try {
                ctor.setAccessible(true);
                return ctor.newInstance(values);
            } catch (Exception failed) {
                last = failed;
            }
        }
        if (last != null) {
            last.printStackTrace();
        }
        return null;
    }

    private static int matched(Class<?>[] params, Object... args) {
        boolean[] used = new boolean[args.length];
        int n = 0;
        for (Class<?> need : params) {
            for (int j = 0; j < args.length; j++) {
                if (used[j] || args[j] == null) {
                    continue;
                }
                if (need.isInstance(args[j]) || boxed(need).isInstance(args[j])) {
                    used[j] = true;
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    private static Object[] bind(Class<?>[] params, Object... args) {
        Object[] values = new Object[params.length];
        boolean[] used = new boolean[args.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> need = params[i];
            int hit = -1;
            for (int j = 0; j < args.length; j++) {
                if (used[j] || args[j] == null) {
                    continue;
                }
                if (need.isInstance(args[j]) || boxed(need).isInstance(args[j])) {
                    hit = j;
                    break;
                }
            }
            if (hit >= 0) {
                values[i] = args[hit];
                used[hit] = true;
                continue;
            }
            if (need == boolean.class) {
                values[i] = false;
            } else if (need == int.class) {
                values[i] = 0;
            } else if (!need.isPrimitive()) {
                values[i] = null;
            } else {
                return null;
            }
        }
        return values;
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        return type;
    }

    private static Object invoke(Object target, String name) throws Exception {
        return target.getClass().getMethod(name).invoke(target);
    }

    private static String clip(String name) {
        if (name == null || name.isBlank()) {
            return "Civilian";
        }
        return name.length() <= 16 ? name : name.substring(0, 16);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumOf(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private static Field field(Class<?> type, String name) {
        Class<?> walk = type;
        while (walk != null) {
            try {
                return walk.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                walk = walk.getSuperclass();
            }
        }
        return null;
    }

    private static Method find(Class<?> type, String name, Class<?>... args) {
        Class<?> walk = type;
        while (walk != null) {
            try {
                return walk.getMethod(name, args);
            } catch (NoSuchMethodException ignored) {
                try {
                    Method method = walk.getDeclaredMethod(name, args);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignoredToo) {
                    walk = walk.getSuperclass();
                }
            }
        }
        return null;
    }
}
