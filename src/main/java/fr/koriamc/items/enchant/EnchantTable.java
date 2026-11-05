package fr.koriamc.items.enchant;

import fr.koriamc.items.KoriaItems;
import fr.koriamc.items.machine.Recipes;
import fr.koriamc.items.util.Menu;
import fr.koriamc.items.util.Nbt;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Table d'enchantement avancée : enchantements custom pour l'objet tenu en main,
 * contre des niveaux d'expérience et du lithium (cahier des charges : 50 niveaux, 16 lithium).
 */
public final class EnchantTable {

    private final KoriaItems plugin;

    public EnchantTable(KoriaItems plugin) {
        this.plugin = plugin;
    }

    public void open(final Player player) {
        final Menu menu = new Menu(4, plugin.msg().text("table-enchant.titre"));
        final ItemStack hand = player.getItemInHand();
        final int levels = plugin.config().getInt("enchantements.cout-niveaux", 50);
        final List<ItemStack> cost = Recipes.parseAll(plugin.config().getStringList("enchantements.cout-objets"));
        int slot = 10;
        for (final Enchants.Def def : Enchants.all().values()) {
            if (slot == 17) {
                slot = 19;
            }
            boolean compatible = hand != null && hand.getType() != Material.AIR && def.target.accepts(hand.getType());
            int current = compatible ? Enchants.level(hand, def.id) : 0;
            List<String> lore = new ArrayList<String>();
            lore.add("&7" + def.description);
            lore.add("");
            lore.add("&7Objets : &f" + def.target.name().toLowerCase());
            lore.add("&7Niveau max : &f" + def.maxLevel);
            lore.add("&7Coût : &a" + levels + " niveaux &7+ &b" + Recipes.describe(cost));
            lore.add("");
            if (!compatible) {
                lore.add("&cTenez un objet compatible en main");
            } else if (current >= def.maxLevel) {
                lore.add("&6Niveau maximum atteint");
            } else {
                lore.add("&eClic : niveau " + Enchants.roman(current + 1));
            }
            ItemStack icon = Menu.icon(new ItemStack(compatible ? Material.ENCHANTED_BOOK : Material.BOOK),
                    "&d" + def.name + (current > 0 ? " &7(" + Enchants.roman(current) + ")" : ""), lore);
            menu.button(slot++, icon, new Menu.Action() {
                @Override
                public void click(Player p, InventoryClickEvent event) {
                    enchant(p, def, levels, cost);
                    p.closeInventory();
                }
            });
        }
        menu.fill();
        menu.open(player);
    }

    private void enchant(Player player, Enchants.Def def, int levels, List<ItemStack> cost) {
        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR || !def.target.accepts(hand.getType())) {
            plugin.msg().send(player, "table-enchant.incompatible");
            return;
        }
        int current = Enchants.level(hand, def.id);
        if (current >= def.maxLevel) {
            plugin.msg().send(player, "table-enchant.max");
            return;
        }
        if (player.getLevel() < levels) {
            plugin.msg().send(player, "table-enchant.niveaux", "niveaux", levels);
            return;
        }
        if (!Recipes.has(player, cost)) {
            plugin.msg().send(player, "table-enchant.objets", "objets", Recipes.describe(cost));
            return;
        }
        player.setLevel(player.getLevel() - levels);
        Recipes.take(player, cost);
        ItemStack result = hand;
        if (def.id.equals(Enchants.MASTER_KEY)) {
            result = Nbt.set(result, "passe.uses", 10);
        }
        if (def.id.equals(Enchants.AQUA)) {
            // vitesse dans l'eau : Agilité aquatique vanilla en plus
            result = result.clone();
            result.addUnsafeEnchantment(Enchantment.DEPTH_STRIDER, 3);
        }
        player.setItemInHand(Enchants.apply(result, def.id, current + 1));
        plugin.msg().send(player, "table-enchant.ok", "enchantement", def.name + (def.maxLevel > 1 ? " " + Enchants.roman(current + 1) : ""));
    }
}
