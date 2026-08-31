package net.minenite.npcs.civilian;

import net.minenite.npcs.skin.SkinService;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Authlib 9 {@code GameProfile(UUID, String)} ships an immutable property map.
 * Textures have to be built into a mutable map and passed to the 3-arg ctor.
 */
public final class Profiles {
    private Profiles() {}

    public static Object gameProfile(UUID id, String name, SkinService.Textures skin) throws Exception {
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> mapType = Class.forName("com.mojang.authlib.properties.PropertyMap");
        Object map = newMap(mapType);
        if (skin != null && skin.value() != null) {
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object property = skin.signature() == null
                    ? propertyClass.getConstructor(String.class, String.class)
                    .newInstance("textures", skin.value())
                    : propertyClass.getConstructor(String.class, String.class, String.class)
                    .newInstance("textures", skin.value(), skin.signature());
            Method put = map.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(map, "textures", property);
        }
        try {
            return profileClass.getConstructor(UUID.class, String.class, mapType)
                    .newInstance(id, clip(name), map);
        } catch (NoSuchMethodException ignored) {
            for (Constructor<?> ctor : profileClass.getConstructors()) {
                if (ctor.getParameterCount() == 3) {
                    return ctor.newInstance(id, clip(name), map);
                }
            }
            return profileClass.getConstructor(UUID.class, String.class).newInstance(id, clip(name));
        }
    }

    private static Object newMap(Class<?> mapType) throws Exception {
        try {
            Class<?> mutable = Class.forName("io.papermc.paper.profile.MutablePropertyMap");
            return mutable.getConstructor().newInstance();
        } catch (ReflectiveOperationException ignored) {
            return mapType.getConstructor().newInstance();
        }
    }

    public static String clip(String name) {
        if (name == null || name.isBlank()) {
            return "Civilian";
        }
        return name.length() <= 16 ? name : name.substring(0, 16);
    }
}
