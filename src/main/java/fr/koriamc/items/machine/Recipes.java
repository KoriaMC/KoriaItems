package fr.koriamc.items.machine;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lecture d'objets de configuration ("MATERIAU", "MATERIAU:quantite", "MATERIAU:data:quantite")
 * et gestion des ingrédients dans l'inventaire d'un joueur.
 */
@SuppressWarnings("deprecation")
public final class Recipes {

    private Recipes() {
    }

    /** @return l'objet, ou null si le matériau est inconnu */
    public static ItemStack parse(String spec) {
        if (spec == null || spec.trim().isEmpty()) {
            return null;
        }
        String[] p = spec.trim().split(":");
        Material m = Material.matchMaterial(p[0].toUpperCase(Locale.ROOT));
        if (m == null) {
            return null;
        }
        try {
            if (p.length == 1) {
                return new ItemStack(m, 1);
            }
            if (p.length == 2) {
                return new ItemStack(m, Integer.parseInt(p[1]));
            }
            return new ItemStack(m, Integer.parseInt(p[2]), Short.parseShort(p[1]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static List<ItemStack> parseAll(List<String> specs) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (String s : specs) {
            ItemStack item = parse(s);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    /** Le joueur a-t-il tous ces objets (data comparée seulement si elle est non nulle) ? */
    public static boolean has(Player player, List<ItemStack> items) {
        for (ItemStack need : items) {
            if (count(player, need) < need.getAmount()) {
                return false;
            }
        }
        return true;
    }

    public static int count(Player player, ItemStack like) {
        int n = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (same(item, like)) {
                n += item.getAmount();
            }
        }
        return n;
    }

    public static boolean same(ItemStack item, ItemStack like) {
        return item != null && item.getType() == like.getType()
                && (like.getDurability() == 0 || item.getDurability() == like.getDurability());
    }

    /** Retire les objets (à appeler après {@link #has}). */
    public static void take(Player player, List<ItemStack> items) {
        for (ItemStack need : items) {
            int left = need.getAmount();
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length && left > 0; i++) {
                ItemStack item = contents[i];
                if (!same(item, need)) {
                    continue;
                }
                int used = Math.min(left, item.getAmount());
                left -= used;
                if (used >= item.getAmount()) {
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - used);
                }
            }
        }
        player.updateInventory();
    }

    public static void give(Player player, ItemStack item) {
        for (ItemStack rest : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    /** "3x Diamant, 16x Lithium" */
    public static String describe(List<ItemStack> items) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack i : items) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(i.getAmount()).append("x ").append(i.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        return sb.toString();
    }
}
