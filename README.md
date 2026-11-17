# BigBois Minecraft Server (`26.3` Fabric Crossplay)

Infrastructure, mod stack, and modpack export tooling for **`mc.bigbois.live`**.

## Architecture
- **Host:** `homeserver` (`100.102.113.106` Tailscale / `192.168.1.225` LAN)
- **Manager:** Crafty 4 (`crafty.service`, server ID `8b9dd022-ae6d-42b9-b0ea-87f33310c950`)
- **Runtime:** Fabric `26.3` (`loader 0.20.1`), Java 25, `-Xms6G -Xmx8G` Generational ZGC
- **Ports:** `58141/tcp` (Java `1.8`–`26.3` via ViaFabric), `19132/udp` + `19132/tcp` (Bedrock/Console via Geyser + Floodgate)
- **Website:** https://bigbois.live/

## Server Mods (`26.3` Fabric)
- **Cross-Play & Multi-Version:** `Geyser-Fabric`, `Floodgate-Fabric`, `ViaFabric` (`ViaVersion`), `ViaBackwards`, `ViaRewind`
- **Discord Bridge:** `Discord-MC-Chat` (`3.0.0-beta.3`, `single_server` mode)
- **Performance & LODs:** `Lithium`, `FerriteCore`, `C2ME`, `ScalableLux`, `ServerCore`, `Krypton`, `Alternate Current`, `VMP`, `Clumps`, `Chunky`, `Distant Horizons` (`3.3.4`, `4000 KB/s` streaming, `256` chunk LODs), `spark`
- **Gameplay QoL:** `JEI` (`+ MezzConfig`), `Veinminer` (enchant-compatible, 0 extra durability/hunger), `Tree Harvester` (`+ Collective`, fast leaf decay, auto sapling replant, 0 extra durability/hunger)

## Scripts
- `scripts/export_packs.py` — Builds tiered `.mrpack` and CurseForge `.zip` client packs (`lite`, `standard`, `ultra-dh-shaders`) from the local CurseForge instance.
- `scripts/homeserver-integrity.sh` — Daily `homeserver` integrity check (`sha256sum` baseline, `pacman -Qkk`, `arch-audit`, systemd/Docker/disk health).
