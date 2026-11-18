# Agent Guidelines (`bigbois-mc-server`)

- **Never commit secrets:** Keep RCON passwords, Crafty credentials, and Discord bot tokens out of git.
- **Keep server mods server-side compatible:** Vanilla Java (`ViaFabric`) and Bedrock (`Geyser`) clients must always be able to join without installing client mods.
- **Veinminer & Tree Harvester invariants:** Always preserve `0` extra durability per chained block/log and `0` extra hunger/exhaustion.
- **Integrity baseline:** After any intentional `/etc/*` or systemd edit on the server host, update the host integrity baseline.
- **Discord changelog:** Document every non-vanilla server tweak, recipe, or mod change in `#server-changes` via the Discord server provisioner.
