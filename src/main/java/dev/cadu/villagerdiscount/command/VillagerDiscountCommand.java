package dev.cadu.villagerdiscount.command;

import com.destroystokyo.paper.entity.villager.Reputation;
import com.destroystokyo.paper.entity.villager.ReputationType;
import dev.cadu.villagerdiscount.DiscountService;
import dev.cadu.villagerdiscount.VillagerDiscountPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.List;
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
            sender.sendMessage(Component.text("Usage: /" + label + " <info|sync|reload>", NamedTextColor.YELLOW));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "info" -> info(sender);
            case "sync" -> {
                int villagers = service.syncAllLoadedVillagers();
                sender.sendMessage(Component.text(
                        "Synced " + villagers + " cured villager(s) to all online players.",
                        NamedTextColor.GREEN));
            }
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(Component.text("minecraft-villagerdiscount config reloaded.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text(
                    "Unknown subcommand. Usage: /" + label + " <info|sync|reload>", NamedTextColor.RED));
        }
        return true;
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can inspect a villager.", NamedTextColor.RED));
            return;
        }
        Entity target = player.getTargetEntity(8);
        if (!(target instanceof Villager villager)) {
            sender.sendMessage(Component.text("Look at a villager (within 8 blocks) first.", NamedTextColor.RED));
            return;
        }

        DiscountService.CureGossip best = service.bestCureGossip(villager);
        Reputation mine = villager.getReputation(player.getUniqueId());
        int myMajor = mine != null ? mine.getReputation(ReputationType.MAJOR_POSITIVE) : 0;
        int myMinor = mine != null ? mine.getReputation(ReputationType.MINOR_POSITIVE) : 0;
        int myTrading = mine != null ? mine.getReputation(ReputationType.TRADING) : 0;
        int myMinorNeg = mine != null ? mine.getReputation(ReputationType.MINOR_NEGATIVE) : 0;
        int myMajorNeg = mine != null ? mine.getReputation(ReputationType.MAJOR_NEGATIVE) : 0;
        // Vanilla weights: major +/-5, everything else +/-1. The game multiplies this
        // score by each offer's price multiplier to compute the emerald discount.
        int score = 5 * myMajor + myMinor + myTrading - myMinorNeg - 5 * myMajorNeg;

        player.sendMessage(Component.text("Villager " + villager.getUniqueId(), NamedTextColor.GOLD));
        player.sendMessage(Component.text("  Recorded cures: " + service.cureCount(villager), NamedTextColor.GRAY));
        player.sendMessage(Component.text(
                "  Best cure gossip: major=" + best.majorPositive() + " minor=" + best.minorPositive(),
                NamedTextColor.GRAY));
        player.sendMessage(Component.text(
                "  Your gossip: major=" + myMajor + " minor=" + myMinor + " trading=" + myTrading
                        + (myMinorNeg + myMajorNeg > 0
                                ? " negatives=" + myMinorNeg + "/" + myMajorNeg
                                : ""),
                NamedTextColor.GRAY));
        player.sendMessage(Component.text("  Your reputation score: " + score, NamedTextColor.GRAY));
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
