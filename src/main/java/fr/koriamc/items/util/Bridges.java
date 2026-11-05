package fr.koriamc.items.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Accès facultatifs (par réflexion) à KoriaFactions et KoriaJobs : KoriaItems fonctionne
 * sans eux, avec des valeurs par défaut permissives.
 */
public final class Bridges {

    private Bridges() {
    }

    private static Method method(String className, String name, Class<?>... params) {
        try {
            return Class.forName(className).getMethod(name, params);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean enabled(String plugin) {
        return Bukkit.getPluginManager().isPluginEnabled(plugin);
    }

    // ============================================================ KoriaFactions

    private static final String FACTIONS = "fr.koriamc.factions.api.KoriaFactionsAPI";

    /** Identifiant de la faction du joueur, ou null. */
    public static String factionId(Player player) {
        if (!enabled("KoriaFactions")) {
            return null;
        }
        try {
            Object f = method(FACTIONS, "getFaction", Player.class).invoke(null, player);
            return normal(f) ? (String) f.getClass().getMethod("getId").invoke(f) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Faction propriétaire du terrain : {id, tag}, ou null (zone libre, safezone, warzone). */
    public static String[] factionAt(Location location) {
        if (!enabled("KoriaFactions")) {
            return null;
        }
        try {
            Object f = method(FACTIONS, "getFactionAt", Location.class).invoke(null, location);
            if (!normal(f)) {
                return null;
            }
            return new String[]{(String) f.getClass().getMethod("getId").invoke(f),
                    (String) f.getClass().getMethod("getTag").invoke(f)};
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean normal(Object faction) throws Exception {
        return faction != null && (Boolean) faction.getClass().getMethod("isNormal").invoke(faction);
    }

    /** Machine de renforcement autorisée ici (amélioration MACHINE) ? true sans KoriaFactions. */
    public static boolean reinforcementAllowed(Location location) {
        if (!enabled("KoriaFactions")) {
            return true;
        }
        Method m = method(FACTIONS, "isReinforcementMachineAllowed", Location.class);
        try {
            return m == null || (Boolean) m.invoke(null, location);
        } catch (Throwable t) {
            return false;
        }
    }

    // =============================================================== KoriaJobs

    private static final String JOBS = "fr.koriamc.jobs.api.KoriaJobsAPI";

    /** Déblocage de métier atteint (medicament_jaune...) ? true sans KoriaJobs. */
    public static boolean unlocked(UUID player, String unlock) {
        if (!enabled("KoriaJobs")) {
            return true;
        }
        try {
            return (Boolean) method(JOBS, "isUnlocked", UUID.class, String.class).invoke(null, player, unlock);
        } catch (Throwable t) {
            return true;
        }
    }

    /** Bonus d'un métier (vitesse-machine...), ou {@code def}. */
    public static double jobBonus(UUID player, String job, String bonus, double def) {
        if (!enabled("KoriaJobs")) {
            return def;
        }
        try {
            return (Double) method(JOBS, "getBonus", UUID.class, String.class, String.class, double.class)
                    .invoke(null, player, job, bonus, def);
        } catch (Throwable t) {
            return def;
        }
    }

    /** XP de métier pour une action de la table "xp" (ex. pharmacien + MEDICAMENT_BLEU). */
    public static void jobReward(Player player, String job, String key, int amount) {
        if (!enabled("KoriaJobs")) {
            return;
        }
        try {
            method(JOBS, "reward", Player.class, String.class, String.class, int.class).invoke(null, player, job, key, amount);
        } catch (Throwable ignored) {
            // métier absent
        }
    }
}
