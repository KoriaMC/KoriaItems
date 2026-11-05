package fr.koriamc.items.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Menu coffre : boutons (clic = action), cases libres (le joueur y pose des objets)
 * et action à la fermeture. Les clics dans l'inventaire du joueur sont autorisés sauf
 * le shift-clic, qui n'enverrait pas l'objet au bon endroit.
 */
public class Menu implements InventoryHolder {

    /** Action d'un bouton. */
    public interface Action {
        void click(Player player, InventoryClickEvent event);
    }

    private final Inventory inventory;
    private final Map<Integer, Action> buttons = new HashMap<Integer, Action>();
    private final Set<Integer> free = new HashSet<Integer>();
    private Runnable onClose;
    private Runnable onChange;

    public Menu(int rows, String title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, Text.truncate(Text.color(title), 32));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void button(int slot, ItemStack icon, Action action) {
        inventory.setItem(slot, icon);
        buttons.put(slot, action);
    }

    public void icon(int slot, ItemStack icon) {
        inventory.setItem(slot, icon);
    }

    /** Case où le joueur peut poser et reprendre des objets. */
    public void free(int slot) {
        free.add(slot);
    }

    public boolean isFree(int slot) {
        return free.contains(slot);
    }

    public void onClose(Runnable action) {
        this.onClose = action;
    }

    /** Appelé au tick suivant un clic dans une case libre ou l'inventaire du joueur. */
    public void onChange(Runnable action) {
        this.onChange = action;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** Remplit les cases vides non libres avec une vitre grise. */
    @SuppressWarnings("deprecation")
    public void fill() {
        ItemStack glass = icon(new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 7), " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null && !free.contains(i)) {
                inventory.setItem(i, glass);
            }
        }
    }

    public static ItemStack icon(ItemStack item, String name, String... lore) {
        return icon(item, name, Arrays.asList(lore));
    }

    public static ItemStack icon(ItemStack item, String name, List<String> lore) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(Text.color(lore));
            try {
                meta.addItemFlags(ItemFlag.values());
            } catch (Throwable ignored) {
                // API ancienne
            }
            copy.setItemMeta(meta);
        }
        return copy;
    }

    /** Écouteur commun à tous les menus. */
    public static final class Handler implements Listener {

        private final org.bukkit.plugin.Plugin plugin;

        public Handler(org.bukkit.plugin.Plugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof Menu) || !(event.getWhoClicked() instanceof Player)) {
                return;
            }
            final Menu menu = (Menu) event.getInventory().getHolder();
            Player player = (Player) event.getWhoClicked();
            int raw = event.getRawSlot();
            boolean top = raw >= 0 && raw < menu.inventory.getSize();
            if (event.isShiftClick() || (top && !menu.free.contains(raw))) {
                event.setCancelled(true);
            }
            if (top && menu.buttons.containsKey(raw)) {
                menu.buttons.get(raw).click(player, event);
                return;
            }
            if (!event.isCancelled() && menu.onChange != null) {
                Bukkit.getScheduler().runTask(plugin, menu.onChange);
            }
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onDrag(InventoryDragEvent event) {
            if (!(event.getInventory().getHolder() instanceof Menu)) {
                return;
            }
            Menu menu = (Menu) event.getInventory().getHolder();
            for (int raw : event.getRawSlots()) {
                if (raw < menu.inventory.getSize() && !menu.free.contains(raw)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (menu.onChange != null) {
                Bukkit.getScheduler().runTask(plugin, menu.onChange);
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent event) {
            if (event.getInventory().getHolder() instanceof Menu) {
                Menu menu = (Menu) event.getInventory().getHolder();
                if (menu.onClose != null) {
                    menu.onClose.run();
                }
            }
        }
    }

    /** Rend au joueur le contenu des cases libres (à la fermeture). */
    public void giveBackFree(Player player) {
        for (int slot : free) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                inventory.setItem(slot, null);
                for (ItemStack rest : player.getInventory().addItem(item).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
            }
        }
    }
}
