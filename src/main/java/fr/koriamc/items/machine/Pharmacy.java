package fr.koriamc.items.machine;

import fr.koriamc.items.KoriaItems;
import fr.koriamc.items.util.Bridges;
import fr.koriamc.items.util.Menu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fabrication de médicaments : table de chimie (petite machine, premières recettes) et
 * machine médicale (toutes les recettes, plus rapide, consomme la charge d'une batterie).
 * <p>
 * Le joueur choisit une recette débloquée par son métier Pharmacien, les ingrédients sont
 * retirés, puis il récupère les médicaments une fois la fabrication terminée.
 */
public final class Pharmacy {

    /** Fabrication en cours d'un joueur dans une machine. */
    private static final class Job {
        final String recipe;
        final long end;

        Job(String recipe, long end) {
            this.recipe = recipe;
            this.end = end;
        }
    }

    private final KoriaItems plugin;
    /** "machine@monde:x:y:z:joueur" -> fabrication. */
    private final Map<String, Job> jobs = new HashMap<String, Job>();

    public Pharmacy(KoriaItems plugin) {
        this.plugin = plugin;
    }

    private static String key(String machine, Location l, UUID player) {
        return machine + "@" + l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ() + ":" + player;
    }

    /** @param machine "table-chimie" ou "machine-medicale" (section de config.json) */
    public void open(final Player player, final String machine, final Location at) {
        final ConfigurationSection cfg = plugin.config().getConfigurationSection("machines." + machine);
        if (cfg == null) {
            return;
        }
        final String jobKey = key(machine, at, player.getUniqueId());
        final Job running = jobs.get(jobKey);
        final Menu menu = new Menu(4, cfg.getString("titre", machine));
        if (running != null) {
            long left = Math.max(0, (running.end - System.currentTimeMillis()) / 1000L);
            ConfigurationSection r = plugin.config().getConfigurationSection("medicaments." + running.recipe);
            String name = r == null ? running.recipe : r.getString("nom", running.recipe);
            boolean done = left <= 0;
            menu.button(13, Menu.icon(new ItemStack(done ? Material.EMERALD : Material.WATCH),
                    done ? "&a&lRécupérer" : "&eFabrication en cours", "&7" + name,
                    done ? "&aClic : récupérer les médicaments" : "&7Temps restant : &f" + left + " s"),
                    new Menu.Action() {
                        @Override
                        public void click(Player p, InventoryClickEvent event) {
                            collect(p, jobKey, cfg);
                            p.closeInventory();
                        }
                    });
            menu.fill();
            menu.open(player);
            return;
        }
        List<String> allowed = cfg.getStringList("recettes");
        int slot = 10;
        ConfigurationSection all = plugin.config().getConfigurationSection("medicaments");
        if (all == null) {
            return;
        }
        for (final String id : all.getKeys(false)) {
            if (!allowed.isEmpty() && !allowed.contains(id)) {
                continue;
            }
            if (slot == 17) {
                slot = 19;
            }
            final ConfigurationSection r = all.getConfigurationSection(id);
            final List<ItemStack> ingredients = Recipes.parseAll(r.getStringList("ingredients"));
            ItemStack result = Recipes.parse(r.getString("resultat"));
            if (result == null) {
                continue;
            }
            boolean unlocked = Bridges.unlocked(player.getUniqueId(), "medicament_" + id);
            final int seconds = time(player, cfg, r);
            List<String> lore = new ArrayList<String>();
            lore.add("&7Ingrédients : &f" + Recipes.describe(ingredients));
            lore.add("&7Produit : &f" + result.getAmount() * cfg.getInt("multiplicateur-quantite", 1) + " médicament(s)");
            lore.add("&7Durée : &f" + seconds + " s");
            if (cfg.getInt("batterie-par-fabrication", 0) > 0) {
                lore.add("&7Batterie : &b" + cfg.getInt("batterie-par-fabrication") + " % &7de charge");
            }
            lore.add("");
            lore.add(unlocked ? "&eClic : lancer la fabrication" : "&cDébloqué par le métier Pharmacien");
            menu.button(slot++, Menu.icon(result, r.getString("nom", id), lore), new Menu.Action() {
                @Override
                public void click(Player p, InventoryClickEvent event) {
                    start(p, jobKey, id, cfg, r, ingredients, seconds);
                    p.closeInventory();
                }
            });
        }
        menu.fill();
        menu.open(player);
    }

    /** Durée de fabrication, divisée par le bonus "vitesse-machine" du Pharmacien pour la machine médicale. */
    private int time(Player player, ConfigurationSection machine, ConfigurationSection recipe) {
        double base = recipe.getDouble("secondes", 60) * machine.getDouble("multiplicateur-duree", 1);
        if (machine.getBoolean("bonus-pharmacien", false)) {
            base /= Math.max(1, Bridges.jobBonus(player.getUniqueId(), "pharmacien", "vitesse-machine", 1));
        }
        return (int) Math.max(1, Math.round(base));
    }

    private void start(Player player, String jobKey, String id, ConfigurationSection machine, ConfigurationSection recipe,
                       List<ItemStack> ingredients, int seconds) {
        if (!Bridges.unlocked(player.getUniqueId(), "medicament_" + id)) {
            plugin.msg().send(player, "pharmacie.bloque");
            return;
        }
        if (!Recipes.has(player, ingredients)) {
            plugin.msg().send(player, "pharmacie.ingredients", "objets", Recipes.describe(ingredients));
            return;
        }
        int battery = machine.getInt("batterie-par-fabrication", 0);
        if (battery > 0) {
            int index = RechargeCenter.findCharged(player, battery);
            if (index < 0) {
                plugin.msg().send(player, "pharmacie.batterie", "charge", battery);
                return;
            }
            ItemStack cell = player.getInventory().getItem(index);
            player.getInventory().setItem(index, RechargeCenter.withCharge(cell, RechargeCenter.charge(cell) - battery));
        }
        Recipes.take(player, ingredients);
        jobs.put(jobKey, new Job(id, System.currentTimeMillis() + seconds * 1000L));
        plugin.msg().send(player, "pharmacie.lance", "medicament", recipe.getString("nom", id), "secondes", seconds);
    }

    private void collect(Player player, String jobKey, ConfigurationSection machine) {
        Job job = jobs.get(jobKey);
        if (job == null || System.currentTimeMillis() < job.end) {
            return;
        }
        jobs.remove(jobKey);
        ConfigurationSection r = plugin.config().getConfigurationSection("medicaments." + job.recipe);
        ItemStack result = r == null ? null : Recipes.parse(r.getString("resultat"));
        if (result == null) {
            return;
        }
        result.setAmount(result.getAmount() * Math.max(1, machine.getInt("multiplicateur-quantite", 1)));
        Recipes.give(player, result);
        Bridges.jobReward(player, "pharmacien", result.getType().name().toUpperCase(Locale.ROOT), result.getAmount());
        plugin.msg().send(player, "pharmacie.recupere", "quantite", result.getAmount(), "medicament", r.getString("nom", job.recipe));
    }
}
