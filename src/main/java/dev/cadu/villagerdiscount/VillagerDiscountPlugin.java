package dev.cadu.villagerdiscount;

import dev.cadu.villagerdiscount.command.VillagerDiscountCommand;
import dev.cadu.villagerdiscount.listener.CureListener;
import dev.cadu.villagerdiscount.listener.InteractListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VillagerDiscountPlugin extends JavaPlugin {

    private DiscountService discountService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.discountService = new DiscountService(this);

        getServer().getPluginManager().registerEvents(new CureListener(this, discountService), this);
        getServer().getPluginManager().registerEvents(new InteractListener(discountService), this);

        PluginCommand command = getCommand("villagerdiscount");
        if (command != null) {
            VillagerDiscountCommand executor = new VillagerDiscountCommand(this, discountService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("VillagerDiscountSync enabled - cured villager discounts are shared with everyone.");
    }

    public DiscountService discountService() {
        return discountService;
    }
}
