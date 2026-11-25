# BigBois Minecraft Server (`26.3` Fabric Crossplay)

Infrastructure, mod stack, and modpack export tooling for **`mc.bigbois.live`**.

## Architecture
- **Host:** Self-hosted Linux server
- **Manager:** Crafty 4
- **Runtime:** Fabric `26.3` (`loader 0.20.1`), Java 25, `-Xms6G -Xmx8G` Generational ZGC
- **Ports:** `58141/tcp` (Java `1.8`–`26.3` via ViaFabric), `19132/udp` + `19132/tcp` (Bedrock/Console via Geyser + Floodgate)
- **Website:** https://bigbois.live/

## Server Mods (`26.3` Fabric)
- **Cross-Play & Multi-Version:** `Geyser-Fabric`, `Floodgate-Fabric`, `ViaFabric` (`ViaVersion`), `ViaBackwards`, `ViaRewind`
- **Discord Bridge:** `Discord-MC-Chat` (`3.0.0-beta.3`, `single_server` mode, player slash commands excluded from `#mc-chat`)
- **Performance & LODs:** `Lithium`, `FerriteCore`, `C2ME`, `ScalableLux`, `ServerCore`, `Krypton`, `Alternate Current`, `VMP`, `Clumps`, `Chunky`, `Distant Horizons` (`3.3.4`, `4000 KB/s` streaming, `256` chunk LODs), `spark`
- **Gameplay QoL:** `JEI` (`+ MezzConfig`), `Veinminer` (enchant-compatible, 0 extra durability/hunger), `Tree Harvester` (`+ Collective`, fast leaf decay, auto sapling replant, 0 extra durability/hunger), `BigBois Villagers` (`1.2.0`, server-side cross-play unlimited trading, `/cycle` + sign + sneak-click Librarian trade cycling with price range indicator, `/cycle wipe` + `/cycle confirm` villager reset, no hit price hikes, awake job claiming, Fletcher 3-trade start with dynamic `4 Logs -> 1 Emerald` Spacebar support, and inventory-wide Mending)

## Scripts
- `scripts/export_packs.py` — Builds tiered `.mrpack` and CurseForge `.zip` client packs (`lite`, `standard`, `ultra-dh-shaders`) from the local CurseForge instance.
