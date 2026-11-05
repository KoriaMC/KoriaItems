package fr.koriamc.items.machine;

import fr.koriamc.items.KoriaItems;
import fr.koriamc.items.util.Menu;
import fr.koriamc.items.util.Nbt;
import fr.koriamc.items.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Centre de recharge : recharge les batteries (battery_cell) avec du lithium.
 * La charge (0 à 100 %) est stockée dans l'objet et affichée dans sa description.
 */
public final class RechargeCenter {

    public static final String BATTERY = "BATTERY_CELL";
    public static final String LITHIUM = "LITHIUM";
    private static final int BATTERY_SLOT = 11;
    private static final int LITHIUM_SLOT = 13;
    private static final int BUTTON_SLOT = 15;

    private final KoriaItems plugin;

    public RechargeCenter(KoriaItems plugin) {
        this.plugin = plugin;
    }

    public static boolean isBattery(ItemStack item) {
        return item != null && item.getType().name().equals(BATTERY);
    }

    public static int charge(ItemStack battery) {
        return Math.max(0, Math.min(100, Nbt.get(battery, "charge")));
    }

    /** Copie de la batterie avec sa nouvelle charge et sa description. */
    public static ItemStack withCharge(ItemStack battery, int charge) {
        ItemStack copy = Nbt.set(battery, "charge", Math.max(0, Math.min(100, charge)));
        ItemMeta meta = copy.getItemMeta();
        List<String> lore = new ArrayList<String>();
        int c = charge(copy);
        String color = c >= 60 ? "&a" : c >= 25 ? "&e" : "&c";
        lore.add(Text.color("&7Charge : " + color + c + " %"));
        meta.setLore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    /** Batterie chargée d'au moins {@code needed} % dans l'inventaire : index, ou -1. */
    public static int findCharged(Player player, int needed) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isBattery(contents[i]) && charge(contents[i]) >= needed) {
                return i;
            }
        }
        return -1;
    }

    public void open(final Player player) {
        final Menu menu = new Menu(3, plugin.msg().text("recharge.titre"));
        menu.free(BATTERY_SLOT);
        menu.free(LITHIUM_SLOT);
        final int perLithium = plugin.config().getInt("machines.recharge.charge-par-lithium", 25);
        menu.button(BUTTON_SLOT, Menu.icon(new ItemStack(Material.REDSTONE_BLOCK), "&a&lRecharger",
                "&7Batterie à gauche, lithium au milieu.", "&7Chaque lithium : &a+" + perLithium + " %"),
                new Menu.Action() {
                    @Override
                    public void click(Player p, InventoryClickEvent event) {
                        charge(p, menu, perLithium);
                    }
                });
        menu.onClose(new Runnable() {
            @Override
            public void run() {
                menu.giveBackFree(player);
            }
        });
        menu.fill();
        menu.open(player);
    }

    private void charge(Player player, Menu menu, int perLithium) {
        ItemStack battery = menu.getInventory().getItem(BATTERY_SLOT);
        ItemStack lithium = menu.getInventory().getItem(LITHIUM_SLOT);
        if (!isBattery(battery)) {
            plugin.msg().send(player, "recharge.pas-de-batterie");
            return;
        }
        if (lithium == null || !lithium.getType().name().toUpperCase(Locale.ROOT).equals(LITHIUM)) {
            plugin.msg().send(player, "recharge.pas-de-lithium");
            return;
        }
        int current = charge(battery);
        if (current >= 100) {
            plugin.msg().send(player, "recharge.pleine");
            return;
        }
        int needed = (int) Math.ceil((100 - current) / (double) perLithium);
        int used = Math.min(needed, lithium.getAmount());
        int charge = Math.min(100, current + used * perLithium);
        menu.getInventory().setItem(BATTERY_SLOT, withCharge(battery, charge));
        if (used >= lithium.getAmount()) {
            menu.getInventory().setItem(LITHIUM_SLOT, null);
        } else {
            lithium.setAmount(lithium.getAmount() - used);
            menu.getInventory().setItem(LITHIUM_SLOT, lithium);
        }
        plugin.msg().send(player, "recharge.ok", "charge", charge, "lithium", used);
    }
}
