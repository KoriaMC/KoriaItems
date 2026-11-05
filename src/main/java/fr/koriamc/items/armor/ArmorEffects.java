package fr.koriamc.items.armor;

import fr.koriamc.items.KoriaItems;
import fr.koriamc.items.util.Bridges;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Effets des sets d'armure complets (cahier des charges) :
 * <ul>
 *   <li>Korium : vitesse II, résistance au feu, anti-chute, force I ;</li>
 *   <li>Farm : vitesse II, résistance au feu, anti-chute, anti sable des âmes ;</li>
 *   <li>Traqueur : vitesse III, anti-chute, résistance au feu, et toutes les 10 minutes une
 *       recherche des joueurs et des claims à 500 blocs.</li>
 * </ul>
 */
public final class ArmorEffects implements Listener {

    public enum Set_ {
        KORIUM, FARM, TRAQUEUR
    }

    private static final String[] SLOTS = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};

    private final KoriaItems plugin;
    private final Map<UUID, Long> lastScan = new HashMap<UUID, Long>();

    public ArmorEffects(KoriaItems plugin) {
        this.plugin = plugin;
    }

    /** Set complet porté, ou null. */
    public static Set_ fullSet(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (Set_ set : Set_.values()) {
            boolean full = true;
            for (ItemStack piece : armor) {
                if (piece == null || !isPiece(piece.getType(), set)) {
                    full = false;
                    break;
                }
            }
            if (full) {
                return set;
            }
        }
        return null;
    }

    private static boolean isPiece(Material m, Set_ set) {
        for (String slot : SLOTS) {
            if (m.name().equals(set.name() + "_" + slot)) {
                return true;
            }
        }
        return false;
    }

    /** Chaque seconde. */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Set_ set = fullSet(player);
            if (set == null) {
                continue;
            }
            switch (set) {
                case KORIUM:
                    effect(player, PotionEffectType.SPEED, 1);
                    effect(player, PotionEffectType.FIRE_RESISTANCE, 0);
                    effect(player, PotionEffectType.INCREASE_DAMAGE, 0);
                    break;
                case FARM:
                    effect(player, PotionEffectType.SPEED, onSoulSand(player) ? 3 : 1);
                    effect(player, PotionEffectType.FIRE_RESISTANCE, 0);
                    break;
                case TRAQUEUR:
                    effect(player, PotionEffectType.SPEED, 2);
                    effect(player, PotionEffectType.FIRE_RESISTANCE, 0);
                    scanLater(player);
                    break;
                default:
                    break;
            }
        }
    }

    private static boolean onSoulSand(Player player) {
        Material below = player.getLocation().getBlock().getRelative(BlockFace.DOWN).getType();
        return below == Material.SOUL_SAND || player.getLocation().getBlock().getType() == Material.SOUL_SAND;
    }

    private static void effect(Player player, PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, 60, amplifier, true, false), true);
    }

    /** Anti-chute des trois sets. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player
                && fullSet((Player) event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    // ============================================================ Traqueur

    private void scanLater(Player player) {
        long now = System.currentTimeMillis();
        long every = plugin.config().getLong("armures.traqueur.recherche-minutes", 10) * 60000L;
        Long last = lastScan.get(player.getUniqueId());
        if (last == null) {
            // première fois : on attend une période complète
            lastScan.put(player.getUniqueId(), now);
            return;
        }
        if (now - last < every) {
            return;
        }
        lastScan.put(player.getUniqueId(), now);
        scan(player);
    }

    /** Joueurs d'autres factions et claims dans le rayon. */
    public void scan(Player player) {
        int radius = plugin.config().getInt("armures.traqueur.rayon", 500);
        String own = Bridges.factionId(player);
        int players = 0;
        for (Player other : player.getWorld().getPlayers()) {
            if (other == player || other.getLocation().distanceSquared(player.getLocation()) > (double) radius * radius) {
                continue;
            }
            String f = Bridges.factionId(other);
            if (own == null || !own.equals(f)) {
                players++;
            }
        }
        Set<String> claims = new HashSet<String>();
        Location center = player.getLocation();
        int chunks = radius >> 4;
        Chunk origin = center.getChunk();
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                Location at = new Location(center.getWorld(), (origin.getX() + dx) << 4, 64, (origin.getZ() + dz) << 4);
                String[] f = Bridges.factionAt(at);
                if (f != null && (own == null || !own.equals(f[0]))) {
                    claims.add(f[1]);
                }
            }
        }
        plugin.msg().send(player, "traqueur.resultat", "joueurs", players, "claims", claims.size(),
                "factions", claims.isEmpty() ? "-" : fr.koriamc.items.util.Text.join(claims, ", "), "rayon", radius);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastScan.remove(event.getPlayer().getUniqueId());
    }
}
