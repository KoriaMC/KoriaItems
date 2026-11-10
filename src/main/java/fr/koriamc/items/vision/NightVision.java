package fr.koriamc.items.vision;

import fr.koriamc.items.KoriaItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * /vision : vision nocturne permanente, activée ou désactivée par le joueur.
 * Elle reste après la mort, le lait et la reconnexion (liste sauvegardée dans vision.txt).
 */
public final class NightVision implements CommandExecutor, Listener {

    /** Durée « infinie » : au-delà, on sait que l'effet vient de /vision. */
    private static final int DURATION = Integer.MAX_VALUE;
    private static final int OURS = 1000000;

    private final KoriaItems plugin;
    private final File file;
    private final Set<UUID> enabled = new HashSet<UUID>();

    public NightVision(KoriaItems plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "vision.txt");
        load();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.msg().send(sender, "vision.joueur");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("koriaitems.vision")) {
            plugin.msg().send(sender, "commande.permission");
            return true;
        }
        if (enabled.remove(player.getUniqueId())) {
            remove(player);
            plugin.msg().send(player, "vision.desactivee");
        } else {
            enabled.add(player.getUniqueId());
            apply(player);
            plugin.msg().send(player, "vision.activee");
        }
        save();
        return true;
    }

    private static void apply(Player player) {
        PotionEffect current = null;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.NIGHT_VISION)) {
                current = effect;
            }
        }
        if (current == null || current.getDuration() < OURS) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, DURATION, 0, true, false), true);
        }
    }

    private static void remove(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.NIGHT_VISION) && effect.getDuration() >= OURS) {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
    }

    /** Réapplique l'effet au tick suivant (après la mort, le lait...). */
    private void later(final Player player) {
        if (!enabled.contains(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline() && enabled.contains(player.getUniqueId())) {
                    apply(player);
                }
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.MILK_BUCKET) {
            later(event.getPlayer());
        }
    }

    private void load() {
        enabled.clear();
        if (!file.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                line = line.trim();
                if (!line.isEmpty()) {
                    try {
                        enabled.add(UUID.fromString(line));
                    } catch (IllegalArgumentException ignored) {
                        // ligne invalide : ignorée
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("vision.txt : " + e.getMessage());
        }
    }

    private void save() {
        List<String> lines = new ArrayList<String>();
        for (UUID id : enabled) {
            lines.add(id.toString());
        }
        try {
            plugin.getDataFolder().mkdirs();
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("vision.txt : " + e.getMessage());
        }
    }
}
