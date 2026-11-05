package fr.koriamc.items.machine;

import fr.koriamc.items.KoriaItems;
import fr.koriamc.items.enchant.Enchants;
import fr.koriamc.items.util.Bridges;
import fr.koriamc.items.util.Menu;
import fr.koriamc.items.util.Nbt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Machine de renforcement : améliore l'objet tenu en main contre des ressources rares.
 * Utilisable seulement sur le territoire d'une faction qui a l'amélioration MACHINE.
 * <p>
 * Types de renforcement (config.json : renforcements) :
 * <ul>
 *   <li>DURABILITE : +1 niveau de Solidité, au-delà du maximum vanilla (jusqu'à "max") ;</li>
 *   <li>ZONE : le marteau légendaire mine en 5 × 3 au lieu de 3 × 3 ;</li>
 *   <li>ENCHANTEMENT : pose un enchantement custom ("enchantement" + "niveau"), même hors table.</li>
 * </ul>
 */
public final class Reinforcement {

    private final KoriaItems plugin;

    public Reinforcement(KoriaItems plugin) {
        this.plugin = plugin;
    }

    public void open(final Player player, Location at) {
        if (!Bridges.reinforcementAllowed(at)) {
            plugin.msg().send(player, "renforcement.faction");
            return;
        }
        final ItemStack hand = player.getItemInHand();
        final Menu menu = new Menu(3, plugin.msg().text("renforcement.titre"));
        ConfigurationSection all = plugin.config().getConfigurationSection("renforcements");
        int slot = 10;
        if (all != null) {
            for (final String id : all.getKeys(false)) {
                final ConfigurationSection r = all.getConfigurationSection(id);
                if (r == null || slot > 16) {
                    continue;
                }
                final List<ItemStack> cost = Recipes.parseAll(r.getStringList("cout"));
                boolean ok = applies(r, hand) && !done(r, hand);
                List<String> lore = new ArrayList<String>();
                for (String l : r.getStringList("description")) {
                    lore.add(l);
                }
                lore.add("");
                lore.add("&7Coût : &f" + Recipes.describe(cost));
                lore.add(ok ? "&eClic : renforcer l'objet en main" : "&cObjet en main incompatible ou déjà renforcé");
                ItemStack icon = Recipes.parse(r.getString("icone", "ANVIL"));
                menu.button(slot++, Menu.icon(icon == null ? new ItemStack(Material.ANVIL) : icon, r.getString("nom", id), lore),
                        new Menu.Action() {
                            @Override
                            public void click(Player p, InventoryClickEvent event) {
                                reinforce(p, r, cost);
                                p.closeInventory();
                            }
                        });
            }
        }
        menu.fill();
        menu.open(player);
    }

    private static boolean applies(ConfigurationSection r, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        List<String> items = r.getStringList("objets");
        if (items.isEmpty()) {
            return item.getType().getMaxDurability() > 0;
        }
        String name = item.getType().name();
        for (String s : items) {
            String pattern = s.toUpperCase(Locale.ROOT);
            if (pattern.startsWith("*") ? name.endsWith(pattern.substring(1)) : name.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean done(ConfigurationSection r, ItemStack item) {
        String type = r.getString("type", "").toUpperCase(Locale.ROOT);
        if (type.equals("DURABILITE")) {
            return item.getEnchantmentLevel(Enchantment.DURABILITY) >= r.getInt("max", 5);
        }
        if (type.equals("ZONE")) {
            return Nbt.get(item, "renfort.zone") > 0;
        }
        if (type.equals("ENCHANTEMENT")) {
            return Enchants.level(item, r.getString("enchantement", "")) >= r.getInt("niveau", 1);
        }
        return true;
    }

    private void reinforce(Player player, ConfigurationSection r, List<ItemStack> cost) {
        ItemStack hand = player.getItemInHand();
        if (!applies(r, hand) || done(r, hand)) {
            plugin.msg().send(player, "renforcement.incompatible");
            return;
        }
        if (!Recipes.has(player, cost)) {
            plugin.msg().send(player, "renforcement.objets", "objets", Recipes.describe(cost));
            return;
        }
        Recipes.take(player, cost);
        String type = r.getString("type", "").toUpperCase(Locale.ROOT);
        ItemStack result = hand.clone();
        if (type.equals("DURABILITE")) {
            result.addUnsafeEnchantment(Enchantment.DURABILITY, result.getEnchantmentLevel(Enchantment.DURABILITY) + 1);
        } else if (type.equals("ZONE")) {
            result = Nbt.set(result, "renfort.zone", 1);
            result = Enchants.render(result);
            org.bukkit.inventory.meta.ItemMeta meta = result.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
            lore.add(fr.koriamc.items.util.Text.color("&6Renforcé : minage 5 × 3"));
            meta.setLore(lore);
            result.setItemMeta(meta);
        } else if (type.equals("ENCHANTEMENT")) {
            result = Enchants.apply(result, r.getString("enchantement", ""), r.getInt("niveau", 1));
        }
        player.setItemInHand(result);
        plugin.msg().send(player, "renforcement.ok", "renforcement", fr.koriamc.items.util.Text.color(r.getString("nom", "")));
    }
}
