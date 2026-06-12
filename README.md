# minecraft-villagerdiscount

A Paper plugin (Paper **26.1.2**, Java **25**) that shares cured-villager trade discounts with
**every** player — online, offline, or players who have never joined the server.

## Why

In vanilla, curing a zombie villager only grants the *cure gossip* (major positive +20,
minor positive +25) to the player who performed the cure. Everyone else still pays full
price. Gossip is stored per `(villager, player-uuid)` pair, so there is no vanilla way to
share it.

## How it works

The plugin uses Paper's villager reputation API (`com.destroystokyo.paper.entity.villager`)
— no NMS, no packet manipulation:

1. **On cure** (`EntityTransformEvent` with reason `CURED`): one tick after the conversion
   finishes — once vanilla has written the curer's gossip — the plugin records the cure on
   the villager (persistent data) and mirrors the best `MAJOR_POSITIVE` / `MINOR_POSITIVE`
   gossip to every online player.
2. **On interact** (`PlayerInteractEntityEvent`, lowest priority): right before any player
   opens the trade menu, their gossip entry is raised to the villager's best cure gossip.
   Since this happens lazily at trade time, it works for players who were offline or had
   never connected when the cure happened — no need to enumerate UUIDs in advance.

Player reputation is only ever **raised**, never lowered — players who cured villagers
themselves or built trading reputation keep everything. Stacked cures (curing the same
villager repeatedly for bigger discounts) are picked up automatically because the plugin
always mirrors the *best* gossip present.

## Commands

| Command | Description |
|---|---|
| `/villagerdiscount info` (alias `/vds info`) | Inspect the villager you are looking at: recorded cures, best cure gossip, your own gossip and reputation score |
| `/villagerdiscount sync` | Force-sync every loaded cured villager to all online players |
| `/villagerdiscount reload` | Reload `config.yml` |

Permission: `villagerdiscount.admin` (default: op).

## Configuration

See [`src/main/resources/config.yml`](src/main/resources/config.yml) — toggle sync-on-cure,
sync-on-interact, persistence, cure announcement message (MiniMessage), and fallback gossip
values.

## Building

Requires JDK 25 and Gradle 9+ (wrapper included):

```bash
./gradlew build
```

The plugin jar lands in `build/libs/minecraft-villagerdiscount-<version>.jar` — drop it in
your server's `plugins/` folder.
