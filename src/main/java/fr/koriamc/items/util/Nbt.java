package fr.koriamc.items.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Données entières stockées dans le NBT d'un objet (tag "KoriaItems"), par réflexion sur
 * CraftItemStack : enchantements custom, charge des batteries, renforcements...
 * Les données suivent l'objet (échange, coffre, hôtel des ventes).
 */
public final class Nbt {

    public static final String TAG = "KoriaItems";

    private static boolean initialized;
    private static boolean available;
    private static Method asNMSCopy;
    private static Method asBukkitCopy;
    private static Method hasTag;
    private static Method getTag;
    private static Method setTag;
    private static Constructor<?> newCompound;
    private static Method hasKey;
    private static Method getCompound;
    private static Method set;
    private static Method setInt;
    private static Method getInt;
    private static Method keys;
    private static Method remove;

    private Nbt() {
    }

    private static void init() {
        initialized = true;
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            String version = pkg.substring(pkg.lastIndexOf('.') + 1);
            String nms = "net.minecraft.server." + version + ".";
            Class<?> craftItem = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            Class<?> nmsItem = Class.forName(nms + "ItemStack");
            Class<?> compound = Class.forName(nms + "NBTTagCompound");
            Class<?> base = Class.forName(nms + "NBTBase");
            asNMSCopy = craftItem.getMethod("asNMSCopy", ItemStack.class);
            asBukkitCopy = craftItem.getMethod("asBukkitCopy", nmsItem);
            hasTag = nmsItem.getMethod("hasTag");
            getTag = nmsItem.getMethod("getTag");
            setTag = nmsItem.getMethod("setTag", compound);
            newCompound = compound.getConstructor();
            hasKey = compound.getMethod("hasKey", String.class);
            getCompound = compound.getMethod("getCompound", String.class);
            set = compound.getMethod("set", String.class, base);
            setInt = compound.getMethod("setInt", String.class, int.class);
            getInt = compound.getMethod("getInt", String.class);
            keys = compound.getMethod("c");
            remove = compound.getMethod("remove", String.class);
            available = true;
        } catch (Throwable t) {
            available = false;
            Bukkit.getLogger().log(Level.SEVERE, "[KoriaItems] Accès NBT impossible.", t);
        }
    }

    public static boolean isAvailable() {
        if (!initialized) {
            init();
        }
        return available;
    }

    /** Toutes les valeurs de l'objet. */
    @SuppressWarnings("unchecked")
    public static Map<String, Integer> read(ItemStack item) {
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        if (item == null || item.getTypeId() == 0 || !isAvailable()) {
            return out;
        }
        try {
            Object nms = asNMSCopy.invoke(null, item);
            if (nms == null || !(Boolean) hasTag.invoke(nms)) {
                return out;
            }
            Object tag = getTag.invoke(nms);
            if (!(Boolean) hasKey.invoke(tag, TAG)) {
                return out;
            }
            Object data = getCompound.invoke(tag, TAG);
            for (String key : (Set<String>) keys.invoke(data)) {
                out.put(key, (Integer) getInt.invoke(data, key));
            }
        } catch (Throwable ignored) {
            // objet sans données
        }
        return out;
    }

    public static int get(ItemStack item, String key) {
        Integer value = read(item).get(key);
        return value == null ? 0 : value;
    }

    /** @return une copie de l'objet avec la valeur (0 la supprime) */
    public static ItemStack set(ItemStack item, String key, int value) {
        Map<String, Integer> all = read(item);
        if (value == 0) {
            all.remove(key);
        } else {
            all.put(key, value);
        }
        return write(item, all);
    }

    public static ItemStack write(ItemStack item, Map<String, Integer> values) {
        if (item == null || item.getTypeId() == 0 || !isAvailable()) {
            return item;
        }
        try {
            Object nms = asNMSCopy.invoke(null, item);
            if (nms == null) {
                return item;
            }
            Object tag = (Boolean) hasTag.invoke(nms) ? getTag.invoke(nms) : newCompound.newInstance();
            if (values.isEmpty()) {
                remove.invoke(tag, TAG);
            } else {
                Object compound = newCompound.newInstance();
                for (Map.Entry<String, Integer> e : values.entrySet()) {
                    setInt.invoke(compound, e.getKey(), e.getValue());
                }
                set.invoke(tag, TAG, compound);
            }
            setTag.invoke(nms, tag);
            return (ItemStack) asBukkitCopy.invoke(null, nms);
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "[KoriaItems] Écriture NBT impossible", t);
            return item;
        }
    }
}
