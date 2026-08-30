package net.minenite.npcs.civilian;

import net.minenite.npcs.NpcsPlugin;
import net.minenite.warzplugin.WarzPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Civilian kit: one random pistol, a loaded mag that fits it, and a spare mag.
 * Uses WarzPlugin's item factory so the gun is a real WarZ stick, not a prop.
 */
public final class LoadoutService {
    public record Kit(ItemStack gun, ItemStack mag, ItemStack spare, List<ItemStack> extras) {
    }

    private final NpcsPlugin plugin;

    public LoadoutService(NpcsPlugin plugin) {
        this.plugin = plugin;
    }

    public Kit roll() {
        WarzPlugin warz = warz();
        if (warz == null || warz.items() == null || warz.registry() == null) {
            return new Kit(new ItemStack(Material.WOODEN_HOE), null, null, List.of());
        }
        List<String> ids = plugin.getConfig().getStringList("civilian.pistols");
        if (ids.isEmpty()) {
            ids = List.of("m9", "usp45", "warz_m1911");
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<String> present = new ArrayList<>();
        for (String id : ids) {
            if (warz.registry().get(id).isPresent()) {
                present.add(id.toLowerCase(Locale.ROOT));
            }
        }
        if (present.isEmpty()) {
            plugin.getLogger().warning("No civilian pistols found in WarzPlugin registry.");
            return new Kit(new ItemStack(Material.WOODEN_HOE), null, null, List.of());
        }
        String gunId = present.get(rng.nextInt(present.size()));
        var def = warz.registry().get(gunId).orElseThrow();
        ItemStack gun = warz.items().create(def, 1);
        var magType = magFor(gunId);
        String round = roundFor(magType);
        ItemStack mag = warz.items().createMagazine(magType, magType.capacity(), round, 1);
        ItemStack spare = warz.items().createMagazine(magType, magType.capacity(), round, 1);
        return new Kit(gun, mag, spare, List.of());
    }

    private static com.local.warz.runtime.MagazineType magFor(String gunId) {
        String id = gunId.toLowerCase(Locale.ROOT);
        if (id.contains("1911") || id.contains("deagle") || id.contains("desert")
                || id.contains("python") || id.contains("magnum") || id.contains("ump")) {
            return com.local.warz.runtime.MagazineType.PISTOL_45_8;
        }
        return com.local.warz.runtime.MagazineType.PISTOL_15;
    }

    private static String roundFor(com.local.warz.runtime.MagazineType type) {
        if (type == com.local.warz.runtime.MagazineType.PISTOL_45_8) {
            return "pistol_fmj";
        }
        return "pistol_fmj";
    }

    private static WarzPlugin warz() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WarzPlugin");
        return plugin instanceof WarzPlugin warz ? warz : null;
    }
}
