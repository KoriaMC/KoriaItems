package fr.koriamc.items;

import fr.koriamc.items.armor.ArmorEffects;
import fr.koriamc.items.config.JsonFile;
import fr.koriamc.items.config.Messages;
import fr.koriamc.items.enchant.EnchantEffects;
import fr.koriamc.items.enchant.EnchantTable;
import fr.koriamc.items.enchant.Enchants;
import fr.koriamc.items.machine.CraftTable;
import fr.koriamc.items.machine.MachineListener;
import fr.koriamc.items.machine.Pharmacy;
import fr.koriamc.items.machine.RechargeCenter;
import fr.koriamc.items.machine.Reinforcement;
import fr.koriamc.items.util.Menu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * KoriaItems : effets des sets d'armure, enchantements custom et machines
 * (centre de recharge, table de chimie, machine médicale, craft avancé 5 × 5,
 * table d'enchantement avancée, machine de renforcement, marteau légendaire).
 */
public final class KoriaItems extends JavaPlugin {

    private static KoriaItems instance;

    private JsonFile configFile;
    private JsonFile messagesFile;
    private Configuration config;
    private final Messages messages = new Messages();
    private ArmorEffects armor;
    private EnchantEffects enchantEffects;
    private EnchantTable enchantTable;
    private RechargeCenter recharge;
    private Pharmacy pharmacy;
    private CraftTable craft;
    private Reinforcement reinforcement;

    public static KoriaItems get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        configFile = new JsonFile(this, "config.json", "medicaments", "medicaments.*", "craft-avance", "craft-avance.*",
                "craft-avance.*.cles", "renforcements", "renforcements.*");
        messagesFile = new JsonFile(this, "messages.json");
        reload();

        armor = new ArmorEffects(this);
        enchantEffects = new EnchantEffects(this);
        enchantTable = new EnchantTable(this);
        recharge = new RechargeCenter(this);
        pharmacy = new Pharmacy(this);
        craft = new CraftTable(this);
        reinforcement = new Reinforcement(this);

        Bukkit.getPluginManager().registerEvents(armor, this);
        Bukkit.getPluginManager().registerEvents(enchantEffects, this);
        Bukkit.getPluginManager().registerEvents(new MachineListener(this), this);
        Bukkit.getPluginManager().registerEvents(new Menu.Handler(this), this);
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                armor.tick();
                enchantEffects.tick();
            }
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
        instance = null;
    }

    public void reload() {
        for (JsonFile file : new JsonFile[]{configFile, messagesFile}) {
            String error = file.load();
            if (error != null) {
                getLogger().warning(file.getName() + " : " + error);
            }
        }
        config = configFile.toConfiguration();
        messages.load(messagesFile.toConfiguration());
    }

    /** /koriaitems reload | enchant &lt;id&gt; [niveau] | charge &lt;0-100&gt; | traqueur */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("koriaitems.admin")) {
            messages.send(sender, "commande.permission");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            reload();
            messages.send(sender, "commande.recharge");
            return true;
        }
        if (sender instanceof Player && args.length >= 2 && args[0].equalsIgnoreCase("enchant")) {
            Player p = (Player) sender;
            Enchants.Def def = Enchants.get(args[1]);
            if (def == null) {
                sender.sendMessage("§cEnchantements : " + Enchants.all().keySet());
                return true;
            }
            int level = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
            p.setItemInHand(Enchants.apply(p.getItemInHand(), def.id, level));
            return true;
        }
        if (sender instanceof Player && args.length >= 2 && args[0].equalsIgnoreCase("charge")) {
            Player p = (Player) sender;
            if (RechargeCenter.isBattery(p.getItemInHand())) {
                p.setItemInHand(RechargeCenter.withCharge(p.getItemInHand(), Integer.parseInt(args[1])));
            }
            return true;
        }
        if (sender instanceof Player && args.length >= 1 && args[0].equalsIgnoreCase("traqueur")) {
            armor.scan((Player) sender);
            return true;
        }
        messages.send(sender, "commande.aide");
        return true;
    }

    public Configuration config() {
        return config;
    }

    public Messages msg() {
        return messages;
    }

    public EnchantTable enchantTable() {
        return enchantTable;
    }

    public RechargeCenter recharge() {
        return recharge;
    }

    public Pharmacy pharmacy() {
        return pharmacy;
    }

    public CraftTable craft() {
        return craft;
    }

    public Reinforcement reinforcement() {
        return reinforcement;
    }
}
