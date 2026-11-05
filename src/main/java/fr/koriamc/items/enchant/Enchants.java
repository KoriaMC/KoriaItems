package fr.koriamc.items.enchant;

import fr.koriamc.items.util.Nbt;
import fr.koriamc.items.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Enchantements custom de la table d'enchantement avancée. Le niveau est stocké dans le NBT
 * de l'objet ("ench.&lt;id&gt;") et affiché en tête de la description.
 */
public final class Enchants {

    /** Types d'objets compatibles. */
    public enum Target {
        PLASTRON, EPEE, ARC, BOTTES, PIOCHE, OUTIL, TOUT;

        public boolean accepts(Material m) {
            String n = m.name();
            switch (this) {
                case PLASTRON:
                    return n.endsWith("_CHESTPLATE");
                case EPEE:
                    return n.endsWith("_SWORD") || n.equals("MACHETTE");
                case ARC:
                    return n.equals("BOW") || n.endsWith("_BOW");
                case BOTTES:
                    return n.endsWith("_BOOTS");
                case PIOCHE:
                    return n.endsWith("_PICKAXE") || n.endsWith("_MULTITOOL") || n.equals("LEGENDARY_HAMMER");
                case OUTIL:
                    return n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SPADE") || n.endsWith("_SHOVEL")
                            || n.endsWith("_HOE") || n.endsWith("_MULTITOOL") || n.equals("LEGENDARY_HAMMER");
                default:
                    return m.getMaxDurability() > 0;
            }
        }
    }

    /** Un enchantement custom. */
    public static final class Def {
        public final String id;
        public final String name;
        public final int maxLevel;
        public final Target target;
        public final String description;

        Def(String id, String name, int maxLevel, Target target, String description) {
            this.id = id;
            this.name = name;
            this.maxLevel = maxLevel;
            this.target = target;
            this.description = description;
        }
    }

    public static final String ANTI_POISON = "anti_poison";
    public static final String MASTER_KEY = "passe_partout";
    public static final String MARKER = "marqueur";
    public static final String SOUL_WALKER = "marcheur_ames";
    public static final String ORE_FORTUNE = "fortune_minerais";
    public static final String RESISTANCE = "resistance";
    public static final String TREASURE = "chasseur_tresor";
    public static final String AQUA = "agilite_aquatique";
    public static final String TELEKINESIS = "telekinesie";
    public static final String KOALA_LEATHER = "cuir_koala";

    private static final Map<String, Def> DEFS = new LinkedHashMap<String, Def>();

    static {
        add(ANTI_POISON, "Anti poison", 1, Target.PLASTRON, "Ne plus recevoir de poison");
        add(MASTER_KEY, "Passe partout", 1, Target.EPEE, "Ouvre les portes ennemies (10 utilisations)");
        add(MARKER, "Marqueur", 1, Target.ARC, "Marque un ennemi touché pendant 15 secondes");
        add(SOUL_WALKER, "Marcheur des âmes", 1, Target.BOTTES, "Pas ralenti sur le sable des âmes");
        add(ORE_FORTUNE, "Fortune minerais", 3, Target.PIOCHE, "20/40/60 % de doubler les minerais modés");
        add(RESISTANCE, "Résistance", 3, Target.TOUT, "2/4/6 % de chance de ne pas perdre de durabilité");
        add(TREASURE, "Chasseur de trésor", 1, Target.PIOCHE, "2 % de chance de trouver un trésor");
        add(AQUA, "Agilité aquatique", 1, Target.BOTTES, "Courir plus vite dans l'eau");
        add(TELEKINESIS, "Télékinésie", 1, Target.OUTIL, "Les blocs vont dans l'inventaire");
        add(KOALA_LEATHER, "Récolte cuir de koala", 1, Target.EPEE, "2 % de chance de récupérer du cuir de koala");
    }

    private static void add(String id, String name, int max, Target target, String description) {
        DEFS.put(id, new Def(id, name, max, target, description));
    }

    private Enchants() {
    }

    public static Map<String, Def> all() {
        return Collections.unmodifiableMap(DEFS);
    }

    public static Def get(String id) {
        return id == null ? null : DEFS.get(id.toLowerCase(Locale.ROOT));
    }

    /** Niveau d'un enchantement custom sur l'objet (0 s'il ne l'a pas). */
    public static int level(ItemStack item, String id) {
        return Nbt.get(item, "ench." + id);
    }

    /** Pose un niveau et met la description à jour. @return la copie modifiée */
    public static ItemStack apply(ItemStack item, String id, int level) {
        return render(Nbt.set(item, "ench." + id, Math.max(0, level)));
    }

    /** Réécrit les lignes d'enchantements custom en tête de la description. */
    public static ItemStack render(ItemStack item) {
        Map<String, Integer> data = Nbt.read(item);
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
        int previous = Math.min(lore.size(), data.containsKey("ench.lignes") ? data.get("ench.lignes") : 0);
        for (int i = 0; i < previous; i++) {
            lore.remove(0);
        }
        List<String> lines = new ArrayList<String>();
        for (Def def : DEFS.values()) {
            Integer level = data.get("ench." + def.id);
            if (level != null && level > 0) {
                String line = "&7" + def.name + (def.maxLevel > 1 ? " " + roman(level) : "");
                if (def.id.equals(MASTER_KEY)) {
                    line += " &8(" + Math.max(0, data.containsKey("passe.uses") ? data.get("passe.uses") : 0) + "/10)";
                }
                lines.add(Text.color(line));
            }
        }
        lore.addAll(0, lines);
        meta.setLore(lore.isEmpty() ? null : lore);
        copy.setItemMeta(meta);
        return Nbt.set(copy, "ench.lignes", lines.size());
    }

    public static String roman(int value) {
        String[] r = {"I", "II", "III", "IV", "V"};
        return value >= 1 && value <= 5 ? r[value - 1] : String.valueOf(value);
    }
}
