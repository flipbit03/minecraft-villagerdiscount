package dev.cadu.villagerdiscount.command;

import dev.cadu.villagerdiscount.DiscountService;
import dev.cadu.villagerdiscount.VillagerDiscountPlugin;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.ReputationType;
import org.bukkit.util.RayTraceResult;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public final class VillagerDiscountCommand implements org.bukkit.command.CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("info", "sync", "reload");

    private final VillagerDiscountPlugin plugin;
    private final DiscountService service;

    public VillagerDiscountCommand(VillagerDiscountPlugin plugin, DiscountService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <info|sync|reload>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "info" -> info(sender);
            case "sync" -> {
                int villagers = service.syncAllLoadedVillagers();
                sender.sendMessage(ChatColor.GREEN
                        + "Synced " + villagers + " cured villager(s) to all online players.");
            }
            case "reload" -> {
                plugin.reloadConfig();
                service.validateAnnounceMessage();
                sender.sendMessage(ChatColor.GREEN + "minecraft-villagerdiscount config reloaded.");
            }
            default -> sender.sendMessage(ChatColor.RED
                    + "Unknown subcommand. Usage: /" + label + " <info|sync|reload>");
        }
        return true;
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can inspect a villager.");
            return;
        }
        Entity target = targetEntity(player, 8);
        if (!(target instanceof Villager villager)) {
            sender.sendMessage(ChatColor.RED + "Look at a villager (within 8 blocks) first.");
            return;
        }

        DiscountService.CureGossip best = service.bestCureGossip(villager);
        UUID me = player.getUniqueId();
        int myMajor = villager.getReputation(me, ReputationType.MAJOR_POSITIVE);
        int myMinor = villager.getReputation(me, ReputationType.MINOR_POSITIVE);
        int myTrading = villager.getReputation(me, ReputationType.TRADING);
        int myMinorNeg = villager.getReputation(me, ReputationType.MINOR_NEGATIVE);
        int myMajorNeg = villager.getReputation(me, ReputationType.MAJOR_NEGATIVE);
        // Vanilla weights: major +/-5, everything else +/-1. The game multiplies this
        // score by each offer's price multiplier to compute the emerald discount.
        int score = 5 * myMajor + myMinor + myTrading - myMinorNeg - 5 * myMajorNeg;

        player.sendMessage(ChatColor.GOLD + "Villager " + villager.getUniqueId());
        player.sendMessage(ChatColor.GRAY + "  Recorded cures: " + service.cureCount(villager));
        player.sendMessage(ChatColor.GRAY
                + "  Best cure gossip: major=" + best.majorPositive() + " minor=" + best.minorPositive());
        player.sendMessage(ChatColor.GRAY
                + "  Your gossip: major=" + myMajor + " minor=" + myMinor + " trading=" + myTrading
                + (myMinorNeg + myMajorNeg > 0
                        ? " negatives=" + myMinorNeg + "/" + myMajorNeg
                        : ""));
        player.sendMessage(ChatColor.GRAY + "  Your reputation score: " + score);
    }

    /**
     * Spigot stand-in for Paper's {@code getTargetEntity(maxDistance)}: the nearest entity
     * on the player's line of sight (spectators and the player excluded), or null if a
     * block is hit first.
     */
    private static Entity targetEntity(Player player, int maxDistance) {
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTrace(eye, eye.getDirection(), maxDistance,
                FluidCollisionMode.NEVER, false, 0.0,
                entity -> entity != player
                        && !(entity instanceof Player other && other.getGameMode() == GameMode.SPECTATOR));
        return hit != null ? hit.getHitEntity() : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Stream.of(SUBCOMMANDS.toArray(String[]::new))
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
