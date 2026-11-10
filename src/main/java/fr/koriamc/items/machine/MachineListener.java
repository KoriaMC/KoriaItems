package fr.koriamc.items.machine;

import fr.koriamc.items.KoriaItems;
import fr.koriamc.items.util.Nbt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ouvre l'interface des machines au clic droit, et gère le marteau légendaire (minage 3 × 3,
 * 5 × 3 une fois renforcé).
 */
public final class MachineListener implements Listener {

    private final KoriaItems plugin;
    /** Face du dernier bloc frappé par chaque joueur (orientation du minage en zone). */
    private final Map<UUID, BlockFace> lastFace = new HashMap<UUID, BlockFace>();
    private final Set<UUID> busy = new HashSet<UUID>();

    public MachineListener(KoriaItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            lastFace.put(player.getUniqueId(), event.getBlockFace());
            return;
        }
        // une protection (claim ennemi) qui refuse l'interaction refuse aussi la machine
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || player.isSneaking() || event.isCancelled()
                || event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) {
            return;
        }
        Block machine = machineOf(block);
        if (machine == null) {
            return;
        }
        String type = machine.getType().name();
        event.setCancelled(true);
        if (type.equals("CENTRE_RECHARGE")) {
            plugin.recharge().open(player);
        } else if (type.equals("TABLE_CHIMIE")) {
            plugin.pharmacy().open(player, "table-chimie", machine.getLocation());
        } else if (type.equals("MEDICAL_MACHINE")) {
            plugin.pharmacy().open(player, "machine-medicale", machine.getLocation());
        } else if (type.equals("TABLE_CRAFT_AVANCEE")) {
            plugin.craft().open(player);
        } else if (type.equals("TABLE_ENCHANT_AVANCEE")) {
            plugin.enchantTable().open(player);
        } else if (type.equals("ARMOR_MACHINE")) {
            plugin.reinforcement().open(player, machine.getLocation());
        } else if (type.equals("CAISSE")) {
            plugin.crates().use(player);
        } else if (type.equals("ALAMBIC")) {
            plugin.msg().send(player, "alambic.info");
        }
    }

    /** Bloc de machine cliqué (les parties invisibles des grosses machines renvoient au bloc principal). */
    private static Block machineOf(Block block) {
        if (isMachine(block.getType())) {
            return block;
        }
        if (!block.getType().name().equals("MODEL_PART")) {
            return null;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -3; dy <= 0; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block b = block.getRelative(dx, dy, dz);
                    String n = b.getType().name();
                    if (n.equals("MEDICAL_MACHINE") || n.equals("ARMOR_MACHINE") || n.equals("TABLE_CHIMIE")) {
                        return b;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isMachine(Material m) {
        String n = m.name();
        return n.equals("CENTRE_RECHARGE") || n.equals("TABLE_CHIMIE") || n.equals("MEDICAL_MACHINE")
                || n.equals("TABLE_CRAFT_AVANCEE") || n.equals("TABLE_ENCHANT_AVANCEE") || n.equals("ARMOR_MACHINE")
                || n.equals("ALAMBIC") || n.equals("CAISSE");
    }

    // ===================================================== Marteau légendaire

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        final Player player = event.getPlayer();
        ItemStack tool = player.getItemInHand();
        if (tool == null || !tool.getType().name().equals("LEGENDARY_HAMMER") || busy.contains(player.getUniqueId())
                || !event.getBlock().getType().isSolid()) {
            return;
        }
        BlockFace face = lastFace.get(player.getUniqueId());
        if (face == null) {
            face = BlockFace.UP;
        }
        boolean wide = Nbt.get(tool, "renfort.zone") > 0;
        Block center = event.getBlock();
        busy.add(player.getUniqueId());
        try {
            // plan perpendiculaire à la face frappée ; 5 × 3 = 2 blocs de plus sur l'axe horizontal
            int half = wide ? 2 : 1;
            for (int a = -half; a <= half; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) {
                        continue;
                    }
                    Block target;
                    if (face == BlockFace.UP || face == BlockFace.DOWN) {
                        target = center.getRelative(a, 0, b);
                    } else if (face == BlockFace.EAST || face == BlockFace.WEST) {
                        target = center.getRelative(0, b, a);
                    } else {
                        target = center.getRelative(a, b, 0);
                    }
                    breakExtra(player, tool, target);
                }
            }
        } finally {
            busy.remove(player.getUniqueId());
        }
    }

    private void breakExtra(Player player, ItemStack tool, Block block) {
        Material m = block.getType();
        if (!m.isSolid() || m == Material.BEDROCK || m == Material.OBSIDIAN || m == Material.MOB_SPAWNER
                || block.getState() instanceof org.bukkit.inventory.InventoryHolder || m.name().equals("MODEL_PART")
                || isMachine(m) || m.name().equals("OBSIDIENNE_RENFORCEE")) {
            return;
        }
        BlockBreakEvent extra = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(extra);
        if (!extra.isCancelled()) {
            block.breakNaturally(tool);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastFace.remove(event.getPlayer().getUniqueId());
    }
}
