package dev.cadu.villagerdiscount.listener;

import dev.cadu.villagerdiscount.DiscountService;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Lazily applies the shared discount the moment any player right-clicks a cured
 * villager. This is what covers players who were offline (or had never joined the
 * server) when the cure happened: their gossip entry is raised right before the
 * merchant menu opens, so the very first trade screen already shows discounted prices.
 */
public final class InteractListener implements Listener {

    private final DiscountService service;

    public InteractListener(DiscountService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        if (!service.syncOnInteract()) {
            return;
        }
        service.apply(villager, event.getPlayer().getUniqueId());
    }
}
