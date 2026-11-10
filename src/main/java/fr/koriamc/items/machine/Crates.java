package fr.koriamc.items.machine;

import fr.koriamc.items.KoriaItems;
import fr.koriamc.items.util.Menu;
import fr.koriamc.items.util.Nbt;
import fr.koriamc.items.util.Sounds;
import fr.koriamc.items.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Caisses à clés (bloc "caisse", item "cle") : une clé koala, tropique ou spawners ouvre la
 * caisse et donne une récompense tirée au hasard dans config.json (caisses.&lt;type&gt;).
 * <p>
 * Clic droit avec une clé : ouverture. Clic droit sans clé : aperçu des récompenses.
 * Commande : /cle give &lt;joueur&gt; &lt;type&gt; [nombre] (boutique, récompenses de métier...).
 */
public final class Crates implements TabExecutor {

    /** Matériau de l'item clé (koriamc:cle). */
    public static final String KEY = "CLE";

    private final KoriaItems plugin;
    private final Random random = new Random();

    public Crates(KoriaItems plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection crates() {
        ConfigurationSection s = plugin.config().getConfigurationSection("caisses");
        return s == null ? new org.bukkit.configuration.MemoryConfiguration() : s;
    }

    private ConfigurationSection crate(String type) {
        return type == null ? null : crates().getConfigurationSection(type.toLowerCase(Locale.ROOT));
    }

    // ================================================================ Clés

    /** Une clé du type donné (null si le type ou l'item n'existe pas). */
    public ItemStack key(String type, int amount) {
        ConfigurationSection c = crate(type);
        Material material = Material.matchMaterial(KEY);
        if (c == null || material == null) {
            return null;
        }
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color(c.getString("nom-cle", "&6Clé " + type)));
        meta.setLore(Text.color(plugin.msg().lines("caisse.lore-cle", "caisse", Text.color(c.getString("nom", type)))));
        item.setItemMeta(meta);
        return Nbt.set(item, "cle", c.getInt("id", 1));
    }

    /** Type de caisse que cette clé ouvre, ou null. */
    public String typeOf(ItemStack item) {
        if (item == null || !item.getType().name().equals(KEY)) {
            return null;
        }
        int id = Nbt.get(item, "cle");
        for (String type : crates().getKeys(false)) {
            if (crates().getInt(type + ".id", -1) == id) {
                return type;
            }
        }
        return null;
    }

    // ============================================================ Ouverture

    /** Clic droit sur une caisse. */
    public void use(Player player) {
        ItemStack hand = player.getItemInHand();
        String type = typeOf(hand);
        if (type == null) {
            preview(player);
            return;
        }
        ConfigurationSection c = crate(type);
        List<ConfigurationSection> rewards = rewards(c);
        if (rewards.isEmpty()) {
            plugin.msg().send(player, "caisse.vide");
            return;
        }
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }
        ConfigurationSection reward = roll(rewards);
        give(player, reward);
        String crateName = Text.color(c.getString("nom", type));
        String rewardName = Text.color(reward.getString("nom", "?"));
        plugin.msg().send(player, "caisse.gain", "caisse", crateName, "recompense", rewardName);
        Sounds.play(player, plugin.config().getString("caisses-son", "LEVEL_UP:1:1.4"));
        if (reward.getBoolean("annonce", false)) {
            plugin.msg().broadcast("caisse.annonce", "joueur", player.getName(), "caisse", crateName, "recompense", rewardName);
        }
    }

    private List<ConfigurationSection> rewards(ConfigurationSection crate) {
        List<ConfigurationSection> out = new ArrayList<ConfigurationSection>();
        ConfigurationSection r = crate == null ? null : crate.getConfigurationSection("recompenses");
        if (r != null) {
            for (String key : r.getKeys(false)) {
                ConfigurationSection s = r.getConfigurationSection(key);
                if (s != null && s.getDouble("poids", 0) > 0) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private ConfigurationSection roll(List<ConfigurationSection> rewards) {
        double total = 0;
        for (ConfigurationSection r : rewards) {
            total += r.getDouble("poids");
        }
        double pick = random.nextDouble() * total;
        for (ConfigurationSection r : rewards) {
            pick -= r.getDouble("poids");
            if (pick < 0) {
                return r;
            }
        }
        return rewards.get(rewards.size() - 1);
    }

    private void give(Player player, ConfigurationSection reward) {
        for (String spec : stringList(reward, "objets")) {
            ItemStack item = Recipes.parse(spec);
            if (item != null) {
                Recipes.give(player, item);
            } else {
                plugin.getLogger().warning("config.json : caisses, objet inconnu '" + spec + "'");
            }
        }
        for (String command : stringList(reward, "commandes")) {
            String line = command.replace("{joueur}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.startsWith("/") ? line.substring(1) : line);
        }
    }

    private static List<String> stringList(ConfigurationSection s, String path) {
        if (s.isString(path)) {
            return Collections.singletonList(s.getString(path));
        }
        return s.getStringList(path);
    }

    /** Menu d'aperçu : chaque caisse avec ses récompenses et leurs chances. */
    public void preview(Player player) {
        Menu menu = new Menu(3, plugin.msg().text("caisse.titre"));
        int slot = 11;
        for (String type : crates().getKeys(false)) {
            ConfigurationSection c = crate(type);
            List<ConfigurationSection> rewards = rewards(c);
            double total = 0;
            for (ConfigurationSection r : rewards) {
                total += r.getDouble("poids");
            }
            List<String> lore = new ArrayList<String>();
            for (ConfigurationSection r : rewards) {
                lore.add(plugin.msg().text("caisse.ligne", "recompense", Text.color(r.getString("nom", "?")),
                        "chance", Text.number(r.getDouble("poids") * 100.0 / Math.max(1e-9, total), 1)));
            }
            lore.add("");
            lore.add(plugin.msg().text("caisse.aide"));
            ItemStack icon = key(type, 1);
            menu.button(slot, Menu.icon(icon == null ? new ItemStack(Material.CHEST) : icon,
                    c.getString("nom", type), lore), new Menu.Action() {
                @Override
                public void click(Player p, InventoryClickEvent event) {
                    // aperçu seulement
                }
            });
            slot += 2;
            if (slot > 15) {
                break;
            }
        }
        menu.fill();
        menu.open(player);
    }

    // ============================================================ Commande

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("koriaitems.admin")) {
                plugin.msg().send(sender, "commande.permission");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Math.max(1, Integer.parseInt(args[3]));
                } catch (NumberFormatException e) {
                    amount = 1;
                }
            }
            ItemStack key = key(args[2], amount);
            if (target == null || key == null) {
                plugin.msg().send(sender, "caisse.usage", "types", Text.join(crates().getKeys(false), ", "));
                return true;
            }
            Recipes.give(target, key);
            plugin.msg().send(sender, "caisse.donnee", "joueur", target.getName(), "nombre", amount,
                    "caisse", Text.color(crate(args[2]).getString("nom", args[2])));
            return true;
        }
        if (sender instanceof Player) {
            preview((Player) sender);
        } else {
            plugin.msg().send(sender, "caisse.usage", "types", Text.join(crates().getKeys(false), ", "));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Text.complete(args[0], Collections.singletonList("give"));
        }
        if (args.length == 2) {
            return null;
        }
        if (args.length == 3) {
            return Text.complete(args[2], new ArrayList<String>(crates().getKeys(false)));
        }
        return Collections.emptyList();
    }
}
