package dev.cadu.villagerdiscount;

import org.bukkit.Server;
import org.bukkit.entity.Villager;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lists the UUIDs a villager holds gossip about.
 *
 * Spigot's API reads and writes a single (villager, uuid, gossip type) entry but cannot
 * enumerate the uuids, so this one lookup goes through the server internals:
 * {@code CraftVillager#getHandle()} -> {@code Villager#getGossips()} ->
 * {@code GossipContainer#getGossipEntries()}. Everything is resolved once at enable, so a
 * server without it fails loudly at startup instead of mid-game.
 */
public final class GossipTargets {

    private final Class<?> craftVillager;
    private final Method getHandle;
    private final Method getGossips;
    private final Method getGossipEntries;

    private GossipTargets(Class<?> craftVillager, Method getHandle, Method getGossips, Method getGossipEntries) {
        this.craftVillager = craftVillager;
        this.getHandle = getHandle;
        this.getGossips = getGossips;
        this.getGossipEntries = getGossipEntries;
    }

    public static GossipTargets resolve(Server server) throws ReflectiveOperationException {
        // The entries themselves are read and written through Spigot's reputation API,
        // which Paper does not have: check for it here so Paper gets the same clean exit.
        Class.forName("org.bukkit.entity.Villager$ReputationType", false, Villager.class.getClassLoader());
        Class<?> craftVillager = Class.forName(
                server.getClass().getPackageName() + ".entity.CraftVillager",
                false, server.getClass().getClassLoader());
        // getMethod picks the override with the most specific return type, i.e. the
        // NMS Villager rather than one of the bridge methods returning a superclass.
        Method getHandle = craftVillager.getMethod("getHandle");
        Method getGossips = getHandle.getReturnType().getMethod("getGossips");
        Method getGossipEntries = getGossips.getReturnType().getMethod("getGossipEntries");
        if (!Map.class.isAssignableFrom(getGossipEntries.getReturnType())) {
            throw new NoSuchMethodException(getGossipEntries + " does not return a Map");
        }
        return new GossipTargets(craftVillager, getHandle, getGossips, getGossipEntries);
    }

    public Set<UUID> of(Villager villager) {
        if (!craftVillager.isInstance(villager)) {
            throw new IllegalStateException("Unexpected villager implementation: " + villager.getClass().getName());
        }
        try {
            Object handle = getHandle.invoke(villager);
            Object gossips = getGossips.invoke(handle);
            Map<?, ?> entries = (Map<?, ?>) getGossipEntries.invoke(gossips);
            Set<UUID> targets = new HashSet<>();
            for (Object target : entries.keySet()) {
                targets.add((UUID) target);
            }
            return targets;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read gossip of villager " + villager.getUniqueId(), e);
        }
    }
}
