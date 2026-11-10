package fr.koriamc.items.ore;

import fr.koriamc.items.KoriaItems;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Minerais modés générés au minage (anti x-ray) : le monde n'en contient aucun. Quand un joueur
 * casse un bloc naturel, chaque bloc de pierre qu'il découvre (jusque-là entouré de blocs pleins)
 * a une petite chance de devenir un minerai, avec un petit filon caché derrière.
 * <p>
 * Un client x-ray ne voit donc rien : un minerai n'existe qu'au moment où il devient visible.
 * Casser un bloc posé par un joueur ne déclenche rien (pas de triche en posant / cassant).
 */
@SuppressWarnings("deprecation")
public final class MiningOres implements Listener {

    private static final String PLACED = "koriaitems-pose";
    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST};

    /** Un minerai : matériau, chance (%) par bloc de pierre découvert, taille du filon, hauteurs. */
    private static final class Ore {
        Material material;
        double chance;
        int veinMin;
        int veinMax;
        int minY;
        int maxY;
    }

    private final KoriaItems plugin;
    private final Random random = new Random();
    private final List<Ore> ores = new ArrayList<Ore>();
    private final Set<String> worlds = new HashSet<String>();
    private final Set<Material> replaceable = new HashSet<Material>();
    private final Set<Material> natural = new HashSet<Material>();
    private boolean enabled;
    private boolean creative;

    public MiningOres(KoriaItems plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection c) {
        ores.clear();
        worlds.clear();
        replaceable.clear();
        natural.clear();
        enabled = c != null && c.getBoolean("actif", true);
        if (c == null) {
            return;
        }
        creative = c.getBoolean("en-creatif", false);
        for (String w : c.getStringList("mondes")) {
            worlds.add(w.toLowerCase(Locale.ROOT));
        }
        materials(c.getStringList("remplace"), replaceable);
        materials(c.getStringList("blocs-naturels"), natural);
        ConfigurationSection list = c.getConfigurationSection("minerais");
        if (list == null) {
            return;
        }
        for (String key : list.getKeys(false)) {
            ConfigurationSection s = list.getConfigurationSection(key);
            Material m = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            if (s == null || m == null) {
                plugin.getLogger().warning("config.json : minerais-au-minage.minerais." + key + " : minerai inconnu.");
                continue;
            }
            Ore ore = new Ore();
            ore.material = m;
            ore.chance = Math.max(0, s.getDouble("chance", 0));
            List<Integer> vein = s.getIntegerList("filon");
            ore.veinMin = Math.max(1, vein.size() > 0 ? vein.get(0) : 1);
            ore.veinMax = Math.max(ore.veinMin, vein.size() > 1 ? vein.get(1) : ore.veinMin);
            List<Integer> y = s.getIntegerList("hauteur");
            ore.minY = y.size() > 0 ? y.get(0) : 5;
            ore.maxY = y.size() > 1 ? y.get(1) : 40;
            ores.add(ore);
        }
    }

    private void materials(List<String> names, Set<Material> into) {
        for (String n : names) {
            Material m = Material.matchMaterial(n.toUpperCase(Locale.ROOT));
            if (m != null) {
                into.add(m);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        event.getBlock().setMetadata(PLACED, new FixedMetadataValue(plugin, Boolean.TRUE));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        if (broken.hasMetadata(PLACED)) {
            broken.removeMetadata(PLACED, plugin);
            return;
        }
        if (!enabled || ores.isEmpty() || !natural.contains(broken.getType())
                || (!creative && event.getPlayer().getGameMode() == GameMode.CREATIVE)) {
            return;
        }
        World world = broken.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL
                || (!worlds.isEmpty() && !worlds.contains(world.getName().toLowerCase(Locale.ROOT)))) {
            return;
        }
        for (BlockFace face : FACES) {
            Block side = broken.getRelative(face);
            if (replaceable.contains(side.getType()) && !side.hasMetadata(PLACED) && hiddenExcept(side, broken)) {
                roll(side);
            }
        }
    }

    /** Le bloc n'était visible d'aucun côté avant cette casse : tous ses voisins sauf {@code except} sont pleins. */
    private static boolean hiddenExcept(Block block, Block except) {
        for (BlockFace face : FACES) {
            Block n = block.getRelative(face);
            if (n.equals(except)) {
                continue;
            }
            if (!n.getType().isOccluding()) {
                return false;
            }
        }
        return true;
    }

    /** Tirage pour un bloc de pierre qui vient d'être découvert. */
    private void roll(Block stone) {
        int y = stone.getY();
        for (Ore ore : ores) {
            if (y < ore.minY || y > ore.maxY || random.nextDouble() * 100.0 >= ore.chance) {
                continue;
            }
            stone.setTypeIdAndData(ore.material.getId(), (byte) 0, false);
            vein(stone, ore);
            return;
        }
    }

    /** Le reste du filon, dans la pierre encore cachée derrière le minerai découvert. */
    private void vein(Block start, Ore ore) {
        int extra = ore.veinMin - 1 + random.nextInt(ore.veinMax - ore.veinMin + 1);
        Block current = start;
        int guard = extra * 6;
        while (extra > 0 && guard-- > 0) {
            Block next = current.getRelative(FACES[random.nextInt(FACES.length)]);
            if (next.getY() < 1 || next.getY() > 254) {
                continue;
            }
            if (replaceable.contains(next.getType()) && !next.hasMetadata(PLACED) && hiddenExcept(next, current)) {
                next.setTypeIdAndData(ore.material.getId(), (byte) 0, false);
                extra--;
            }
            if (next.getType() == ore.material) {
                current = next;
            }
        }
    }
}
