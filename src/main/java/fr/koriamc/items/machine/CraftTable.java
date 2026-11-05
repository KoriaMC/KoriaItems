package fr.koriamc.items.machine;

import fr.koriamc.items.KoriaItems;
import fr.koriamc.items.util.Bridges;
import fr.koriamc.items.util.Menu;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Table de craft avancée : grille de 5 × 5 et recettes custom (config.json : craft-avance).
 * <p>
 * Une recette a un "motif" de 5 lignes de 5 caractères (espace = case vide), des "cles"
 * (caractère -> objet) et un "resultat". Elle peut demander un "deblocage" de métier.
 */
public final class CraftTable {

    /** Cases de la grille dans le coffre de 6 lignes : colonnes 1 à 5, lignes 0 à 4. */
    private static final int[] GRID = new int[25];
    private static final int RESULT = 25;

    static {
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                GRID[r * 5 + c] = r * 9 + c + 1;
            }
        }
    }

    private final KoriaItems plugin;

    public CraftTable(KoriaItems plugin) {
        this.plugin = plugin;
    }

    public void open(final Player player) {
        final Menu menu = new Menu(6, plugin.msg().text("craft.titre"));
        for (int slot : GRID) {
            menu.free(slot);
        }
        menu.icon(24, Menu.icon(new ItemStack(Material.ARROW), "&7→"));
        menu.button(RESULT, null, new Menu.Action() {
            @Override
            public void click(Player p, InventoryClickEvent event) {
                take(p, menu);
            }
        });
        menu.onChange(new Runnable() {
            @Override
            public void run() {
                preview(player, menu);
            }
        });
        menu.onClose(new Runnable() {
            @Override
            public void run() {
                menu.getInventory().setItem(RESULT, null);
                menu.giveBackFree(player);
            }
        });
        menu.fill();
        menu.getInventory().setItem(RESULT, null);
        menu.open(player);
    }

    /** Recette correspondant à la grille, ou null. */
    private ConfigurationSection match(Inventory inv) {
        ConfigurationSection all = plugin.config().getConfigurationSection("craft-avance");
        if (all == null) {
            return null;
        }
        for (String id : all.getKeys(false)) {
            ConfigurationSection r = all.getConfigurationSection(id);
            if (r != null && matches(r, inv)) {
                return r;
            }
        }
        return null;
    }

    private boolean matches(ConfigurationSection r, Inventory inv) {
        List<String> pattern = r.getStringList("motif");
        ConfigurationSection keys = r.getConfigurationSection("cles");
        Map<Character, ItemStack> map = new HashMap<Character, ItemStack>();
        if (keys != null) {
            for (String k : keys.getKeys(false)) {
                ItemStack item = Recipes.parse(keys.getString(k));
                if (item == null || k.isEmpty()) {
                    return false;
                }
                map.put(k.charAt(0), item);
            }
        }
        for (int row = 0; row < 5; row++) {
            String line = row < pattern.size() ? pattern.get(row) : "";
            for (int col = 0; col < 5; col++) {
                char c = col < line.length() ? line.charAt(col) : ' ';
                ItemStack in = inv.getItem(GRID[row * 5 + col]);
                boolean empty = in == null || in.getType() == Material.AIR;
                if (c == ' ') {
                    if (!empty) {
                        return false;
                    }
                    continue;
                }
                ItemStack need = map.get(c);
                if (need == null || empty || !Recipes.same(in, need) || in.getAmount() < need.getAmount()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void preview(Player player, Menu menu) {
        ConfigurationSection r = match(menu.getInventory());
        ItemStack result = r == null ? null : Recipes.parse(r.getString("resultat"));
        if (result != null && r.isString("deblocage") && !Bridges.unlocked(player.getUniqueId(), r.getString("deblocage"))) {
            result = Menu.icon(result, "&c" + result.getType().name().toLowerCase().replace('_', ' '),
                    "&cRecette pas encore débloquée", "&7(" + r.getString("deblocage") + ")");
        }
        menu.getInventory().setItem(RESULT, result);
    }

    private void take(Player player, Menu menu) {
        Inventory inv = menu.getInventory();
        ConfigurationSection r = match(inv);
        if (r == null) {
            return;
        }
        if (r.isString("deblocage") && !Bridges.unlocked(player.getUniqueId(), r.getString("deblocage"))) {
            plugin.msg().send(player, "craft.bloque");
            return;
        }
        ItemStack result = Recipes.parse(r.getString("resultat"));
        if (result == null) {
            return;
        }
        List<String> pattern = r.getStringList("motif");
        ConfigurationSection keys = r.getConfigurationSection("cles");
        for (int row = 0; row < 5; row++) {
            String line = row < pattern.size() ? pattern.get(row) : "";
            for (int col = 0; col < 5; col++) {
                char c = col < line.length() ? line.charAt(col) : ' ';
                if (c == ' ') {
                    continue;
                }
                ItemStack need = Recipes.parse(keys.getString(String.valueOf(c)));
                int slot = GRID[row * 5 + col];
                ItemStack in = inv.getItem(slot);
                if (in.getAmount() <= need.getAmount()) {
                    inv.setItem(slot, null);
                } else {
                    in.setAmount(in.getAmount() - need.getAmount());
                    inv.setItem(slot, in);
                }
            }
        }
        Recipes.give(player, result);
        plugin.msg().send(player, "craft.ok", "objet", result.getType().name().toLowerCase().replace('_', ' '));
        preview(player, menu);
    }
}
