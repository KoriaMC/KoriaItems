package fr.koriamc.items.enchant;

import fr.koriamc.items.KoriaItems;
import fr.koriamc.items.util.Nbt;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Door;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Openable;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Effets des enchantements custom (voir {@link Enchants}).
 */
@SuppressWarnings("deprecation")
public final class EnchantEffects implements Listener {

    private static final String MARKER_META = "koriaitems-marqueur";

    private final KoriaItems plugin;
    private final Random random = new Random();
    /** Bloc cassé ce tick avec télékinésie -> joueur. */
    private final Map<String, UUID> telekinesis = new HashMap<String, UUID>();
    private boolean clearScheduled;
    /** Joueur marqué -> {tireur, fin du marquage}. */
    private final Map<UUID, Object[]> marked = new HashMap<UUID, Object[]>();

    public EnchantEffects(KoriaItems plugin) {
        this.plugin = plugin;
    }

    /** Chaque seconde : anti poison, marcheur des âmes, agilité aquatique, affichage du marqueur. */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack chest = player.getInventory().getChestplate();
            if (chest != null && Enchants.level(chest, Enchants.ANTI_POISON) > 0 && player.hasPotionEffect(PotionEffectType.POISON)) {
                player.removePotionEffect(PotionEffectType.POISON);
            }
            ItemStack boots = player.getInventory().getBoots();
            if (boots != null) {
                Block feet = player.getLocation().getBlock();
                if (Enchants.level(boots, Enchants.SOUL_WALKER) > 0 && (feet.getType() == Material.SOUL_SAND
                        || feet.getRelative(BlockFace.DOWN).getType() == Material.SOUL_SAND)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 2, true, false), true);
                }
                if (Enchants.level(boots, Enchants.AQUA) > 0 && feet.isLiquid()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 1, true, false), true);
                }
            }
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Object[]>> it = marked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Object[]> e = it.next();
            Player target = Bukkit.getPlayer(e.getKey());
            Player shooter = Bukkit.getPlayer((UUID) e.getValue()[0]);
            if (target == null || shooter == null || now > (Long) e.getValue()[1]) {
                it.remove();
                continue;
            }
            Location l = target.getLocation();
            int distance = shooter.getWorld().equals(l.getWorld()) ? (int) shooter.getLocation().distance(l) : -1;
            plugin.msg().send(shooter, "marqueur.suivi", "joueur", target.getName(), "x", l.getBlockX(),
                    "y", l.getBlockY(), "z", l.getBlockZ(), "distance", distance);
        }
    }

    // ============================================================= Durabilité

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        int level = Enchants.level(event.getItem(), Enchants.RESISTANCE);
        if (level > 0 && random.nextDouble() * 100 < level * 2) {
            event.setCancelled(true);
        }
    }

    // ================================================================ Minage

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getItemInHand();
        if (tool == null || tool.getType() == Material.AIR) {
            return;
        }
        Map<String, Integer> data = Nbt.read(tool);
        if (data.isEmpty()) {
            return;
        }
        Block block = event.getBlock();
        String type = block.getType().name();
        if (get(data, Enchants.TELEKINESIS) > 0) {
            catchDrops(player, block);
        }
        int fortune = get(data, Enchants.ORE_FORTUNE);
        if (fortune > 0 && plugin.config().getStringList("enchantements.minerais-modes").contains(type)
                && random.nextDouble() * 100 < fortune * 20) {
            for (ItemStack drop : block.getDrops(tool)) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
            }
        }
        if (get(data, Enchants.TREASURE) > 0 && (block.getType() == Material.STONE || type.endsWith("_ORE"))
                && random.nextDouble() * 100 < plugin.config().getDouble("enchantements.tresor-chance", 2)) {
            List<String> treasures = plugin.config().getStringList("enchantements.tresors");
            if (!treasures.isEmpty()) {
                ItemStack treasure = fr.koriamc.items.machine.Recipes.parse(treasures.get(random.nextInt(treasures.size())));
                if (treasure != null) {
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), treasure);
                    plugin.msg().send(player, "tresor.trouve");
                }
            }
        }
    }

    private static int get(Map<String, Integer> data, String id) {
        Integer v = data.get("ench." + id);
        return v == null ? 0 : v;
    }

    private void catchDrops(Player player, Block block) {
        telekinesis.put(key(block.getLocation()), player.getUniqueId());
        if (!clearScheduled) {
            clearScheduled = true;
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    telekinesis.clear();
                    clearScheduled = false;
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (telekinesis.isEmpty()) {
            return;
        }
        UUID owner = telekinesis.get(key(event.getLocation()));
        Player player = owner == null ? null : Bukkit.getPlayer(owner);
        if (player == null) {
            return;
        }
        event.setCancelled(true);
        for (ItemStack rest : player.getInventory().addItem(event.getEntity().getItemStack()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    private static String key(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    // ========================================================== Passe partout

    /** Ouvre une porte refusée par une protection (porte ennemie), 10 fois au maximum. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDoor(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!event.isCancelled() && event.useInteractedBlock() != Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack sword = player.getItemInHand();
        if (sword == null || Enchants.level(sword, Enchants.MASTER_KEY) <= 0) {
            return;
        }
        Block block = event.getClickedBlock();
        BlockState state = block.getState();
        MaterialData data = state.getData();
        if (data instanceof Door && ((Door) data).isTopHalf()) {
            block = block.getRelative(BlockFace.DOWN);
            state = block.getState();
            data = state.getData();
        }
        if (!(data instanceof Openable) || block.getType() == Material.IRON_DOOR_BLOCK || block.getType() == Material.IRON_TRAPDOOR) {
            return;
        }
        Openable openable = (Openable) data;
        openable.setOpen(!openable.isOpen());
        state.setData(data);
        state.update(true);
        int uses = Math.max(0, Nbt.get(sword, "passe.uses") - 1);
        ItemStack updated;
        if (uses <= 0) {
            updated = Enchants.apply(Nbt.set(sword, "passe.uses", 0), Enchants.MASTER_KEY, 0);
            plugin.msg().send(player, "passe-partout.use");
        } else {
            updated = Enchants.render(Nbt.set(sword, "passe.uses", uses));
            plugin.msg().send(player, "passe-partout.utilise", "restant", uses);
        }
        player.setItemInHand(updated);
    }

    // =============================================================== Marqueur

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player && event.getBow() != null
                && Enchants.level(event.getBow(), Enchants.MARKER) > 0) {
            event.getProjectile().setMetadata(MARKER_META, new FixedMetadataValue(plugin, event.getEntity().getUniqueId().toString()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow) || !(event.getEntity() instanceof Player)
                || !event.getDamager().hasMetadata(MARKER_META)) {
            return;
        }
        UUID shooter = UUID.fromString(event.getDamager().getMetadata(MARKER_META).get(0).asString());
        Player target = (Player) event.getEntity();
        int seconds = plugin.config().getInt("enchantements.marqueur-secondes", 15);
        marked.put(target.getUniqueId(), new Object[]{shooter, System.currentTimeMillis() + seconds * 1000L});
        plugin.msg().send(target, "marqueur.marque", "secondes", seconds);
    }

    // =========================================================== Cuir de koala

    @EventHandler(priority = EventPriority.HIGH)
    public void onKill(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || Enchants.level(killer.getItemInHand(), Enchants.KOALA_LEATHER) <= 0) {
            return;
        }
        String name = victim.getCustomName() == null ? victim.getType().name()
                : ChatColor.stripColor(victim.getCustomName()).toUpperCase(Locale.ROOT);
        if (!name.contains("KOALA") || random.nextDouble() * 100 >= plugin.config().getDouble("enchantements.cuir-koala-chance", 2)) {
            return;
        }
        ItemStack leather = fr.koriamc.items.machine.Recipes.parse(plugin.config().getString("enchantements.cuir-koala-objet", "LEATHER"));
        if (leather != null) {
            event.getDrops().add(leather);
        }
    }
}
