package dev.cadu.villagerdiscount.listener;

import dev.cadu.villagerdiscount.DiscountService;
import dev.cadu.villagerdiscount.VillagerDiscountPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Villager;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTransformEvent;

public final class CureListener implements Listener {

    private final VillagerDiscountPlugin plugin;
    private final DiscountService service;

    public CureListener(VillagerDiscountPlugin plugin, DiscountService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCure(EntityTransformEvent event) {
        if (event.getTransformReason() != EntityTransformEvent.TransformReason.CURED) {
            return;
        }
        if (!(event.getTransformedEntity() instanceof Villager villager)) {
            return;
        }

        String curerName = null;
        if (event.getEntity() instanceof ZombieVillager zombieVillager) {
            OfflinePlayer curer = zombieVillager.getConversionPlayer();
            if (curer != null) {
                curerName = curer.getName();
            }
        }
        String curer = curerName != null ? curerName : "Someone";

        // Vanilla writes the curer's gossip as the conversion finishes; wait a tick so
        // we observe the real values instead of an empty gossip container.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!villager.isValid()) {
                return;
            }
            service.recordCure(villager);

            int synced = 0;
            if (service.syncOnCure()) {
                synced = service.syncAllOnline(villager);
            }

            if (service.announceEnabled()) {
                plugin.getServer().broadcast(MiniMessage.miniMessage().deserialize(
                        service.announceMessage(),
                        Placeholder.unparsed("curer", curer),
                        Placeholder.unparsed("synced", String.valueOf(synced))));
            }
        }, 1L);
    }
}
