package dev.cadu.villagerdiscount;

import com.destroystokyo.paper.entity.villager.Reputation;
import com.destroystokyo.paper.entity.villager.ReputationType;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Reads, persists and mirrors cure gossip across players.
 *
 * Vanilla stores gossip per (villager, player-uuid) pair, which is why only the curer
 * gets the discount. Mirroring the best MAJOR_POSITIVE / MINOR_POSITIVE values onto a
 * player's own gossip entry makes the game compute the exact same discounted prices
 * for them, with no NMS or packet hackery.
 */
public final class DiscountService {

    /** Best cure-derived gossip found on a villager. */
    public record CureGossip(int majorPositive, int minorPositive) {
        public boolean isEmpty() {
            return majorPositive <= 0 && minorPositive <= 0;
        }
    }

    private final VillagerDiscountPlugin plugin;
    private final NamespacedKey majorKey;
    private final NamespacedKey minorKey;
    private final NamespacedKey cureCountKey;

    public DiscountService(VillagerDiscountPlugin plugin) {
        this.plugin = plugin;
        this.majorKey = new NamespacedKey(plugin, "cured_major_positive");
        this.minorKey = new NamespacedKey(plugin, "cured_minor_positive");
        this.cureCountKey = new NamespacedKey(plugin, "cure_count");
    }

    public boolean syncOnCure() {
        return plugin.getConfig().getBoolean("sync-on-cure", true);
    }

    public boolean syncOnInteract() {
        return plugin.getConfig().getBoolean("sync-on-interact", true);
    }

    public boolean persistDiscounts() {
        return plugin.getConfig().getBoolean("persist-discounts", true);
    }

    public boolean announceEnabled() {
        return plugin.getConfig().getBoolean("announce.enabled", true);
    }

    public String announceMessage() {
        return plugin.getConfig().getString("announce.message",
                "<green><curer> cured a villager! The discounted prices are now shared with everyone.</green>");
    }

    private boolean debug() {
        return plugin.getConfig().getBoolean("debug", false);
    }

    /**
     * The strongest cure gossip on this villager: the maximum across every player's
     * gossip entry, plus whatever this plugin persisted on the villager itself.
     * Stacked cures (which raise the values further) are picked up automatically.
     */
    public CureGossip bestCureGossip(Villager villager) {
        int major = 0;
        int minor = 0;
        for (Reputation reputation : villager.getReputations().values()) {
            major = Math.max(major, reputation.getReputation(ReputationType.MAJOR_POSITIVE));
            minor = Math.max(minor, reputation.getReputation(ReputationType.MINOR_POSITIVE));
        }
        if (persistDiscounts()) {
            PersistentDataContainer pdc = villager.getPersistentDataContainer();
            major = Math.max(major, pdc.getOrDefault(majorKey, PersistentDataType.INTEGER, 0));
            minor = Math.max(minor, pdc.getOrDefault(minorKey, PersistentDataType.INTEGER, 0));
        }
        return new CureGossip(major, minor);
    }

    /**
     * Raises the given player's gossip on this villager to the villager's best cure
     * gossip. Never lowers anything the player already earned (e.g. their own cures
     * or trading reputation).
     *
     * @return true if the player's reputation changed
     */
    public boolean apply(Villager villager, UUID playerId) {
        CureGossip best = bestCureGossip(villager);
        if (best.isEmpty()) {
            return false;
        }
        Reputation reputation = villager.getReputation(playerId);
        if (reputation == null) {
            reputation = new Reputation();
        }
        boolean changed = false;
        if (reputation.getReputation(ReputationType.MAJOR_POSITIVE) < best.majorPositive()) {
            reputation.setReputation(ReputationType.MAJOR_POSITIVE, best.majorPositive());
            changed = true;
        }
        if (reputation.getReputation(ReputationType.MINOR_POSITIVE) < best.minorPositive()) {
            reputation.setReputation(ReputationType.MINOR_POSITIVE, best.minorPositive());
            changed = true;
        }
        if (changed) {
            villager.setReputation(playerId, reputation);
            if (debug()) {
                plugin.getLogger().info("Synced cure gossip " + best + " to " + playerId
                        + " on villager " + villager.getUniqueId());
            }
        }
        return changed;
    }

    /** Mirrors the villager's best cure gossip to every online player. */
    public int syncAllOnline(Villager villager) {
        int synced = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (apply(villager, player.getUniqueId())) {
                synced++;
            }
        }
        return synced;
    }

    /**
     * Called one tick after a cure conversion finishes, once vanilla has written the
     * curer's gossip. Persists the observed values on the villager so the discount can
     * be re-applied forever, even to players who join years later.
     */
    public void recordCure(Villager villager) {
        CureGossip observed = bestCureGossip(villager);
        int major = observed.majorPositive();
        int minor = observed.minorPositive();
        if (major <= 0) {
            major = plugin.getConfig().getInt("fallback.major-positive", 20);
        }
        if (minor <= 0) {
            minor = plugin.getConfig().getInt("fallback.minor-positive", 25);
        }
        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        pdc.set(majorKey, PersistentDataType.INTEGER, major);
        pdc.set(minorKey, PersistentDataType.INTEGER, minor);
        pdc.set(cureCountKey, PersistentDataType.INTEGER, cureCount(villager) + 1);
    }

    /** How many cures this plugin has recorded on the villager. */
    public int cureCount(Villager villager) {
        return villager.getPersistentDataContainer()
                .getOrDefault(cureCountKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * Force-syncs every loaded villager in every world to all online players.
     *
     * @return number of villagers that had a discount to share
     */
    public int syncAllLoadedVillagers() {
        int villagersWithDiscount = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (bestCureGossip(villager).isEmpty()) {
                    continue;
                }
                villagersWithDiscount++;
                syncAllOnline(villager);
            }
        }
        return villagersWithDiscount;
    }
}
