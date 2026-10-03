# minecraft-villagerdiscount

A Spigot plugin (Spigot **26.3**, Java **25**) that shares cured-villager trade discounts with
**every** player — online, offline, or players who have never joined the server.

Requires Spigot 26.3+. Paper will refuse to load it.

## Why

In vanilla, curing a zombie villager only grants the *cure gossip* (major positive +20,
minor positive +25) to the player who performed the cure. Everyone else still pays full
price. Gossip is stored per `(villager, player-uuid)` pair, so there is no vanilla way to
share it.

## How it works

The plugin reads and writes gossip through the Bukkit/Spigot villager reputation API
(`Villager#getReputation` / `setReputation`). The API has no way to list which players a
villager has gossip about, so that single lookup reflects into the server
(`CraftVillager#getHandle()` → `Villager#getGossips()` → `GossipContainer#getGossipEntries()`);
if a server build lacks those methods the plugin logs why and disables itself at startup.
No packet manipulation:

1. **On cure** (`EntityTransformEvent` with reason `CURED`): one tick after the conversion
   finishes — once vanilla has written the curer's gossip — the plugin records the cure on
   the villager (persistent data) and mirrors the best `MAJOR_POSITIVE` / `MINOR_POSITIVE`
   gossip to every online player.
2. **On interact** (`PlayerInteractEntityEvent`, lowest priority): right before any player
   opens the trade menu, their gossip entry is raised to the villager's best cure gossip.
   Since this happens lazily at trade time, it works for players who were offline or had
   never connected when the cure happened — no need to enumerate UUIDs in advance.

Player reputation is only ever **raised**, never lowered — players who cured villagers
themselves or built trading reputation keep everything. Repeat cures by anyone are picked
up automatically because the plugin always mirrors the *best* gossip present (vanilla caps
cure gossip at major 20 / minor 25, so repeat cures refresh the discount rather than stack it).

Writes go through Spigot's setter, so each one fires `VillagerReputationChangeEvent` (reason
`UNSPECIFIED`). Don't cancel that event for these writes: when a cancelled change would have
created a new entry, Spigot's `GossipContainer.add` leaves that entry behind with value 0.

## Commands

| Command | Description |
|---|---|
| `/villagerdiscount info` (alias `/vds info`) | Inspect the villager you are looking at: recorded cures, best cure gossip, your own gossip and reputation score |
| `/villagerdiscount sync` | Force-sync every loaded cured villager to all online players |
| `/villagerdiscount reload` | Reload `config.yml` |

Permission: `villagerdiscount.admin` (default: op).

## Configuration

See [`src/main/resources/config.yml`](src/main/resources/config.yml) — toggle sync-on-cure,
sync-on-interact, persistence, cure announcement message (MiniMessage colors and decorations,
rendered as legacy chat colors), and fallback gossip values.

## Building

Requires JDK 25 and Gradle 9+ (wrapper included):

```bash
./gradlew build
```

The plugin jar lands in `build/libs/minecraft-villagerdiscount-<version>.jar` — drop it in
your server's `plugins/` folder.
