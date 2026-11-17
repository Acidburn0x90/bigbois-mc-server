#!/usr/bin/env python3
"""Export tiered 26.3 Fabric modpacks (.mrpack and CurseForge .zip) from Fabulously Optimized."""

import json
import os
import zipfile
from pathlib import Path

SRC = Path(os.environ.get("MC_INSTANCE_DIR", "./instance")).expanduser()
OUT = Path(os.environ.get("MC_PACK_OUT_DIR", "./dist")).expanduser()

LITE_PREFIXES = (
    "fabric-api-",
    "fabric-language-kotlin-",
    "sodium-fabric-",
    "lithium-fabric-",
    "ferritecore-",
    "ImmediatelyFast-",
    "entityculling-",
    "moreculling-",
    "modernfix-",
    "Ixeris-",
    "reeses-sodium-options-",
    "sodium-extra-",
    "dynamic-fps-",
    "fastquit-",
    "rrls-",
    "cloth-config-",
    "yet_another_config_lib_v3-",
    "ForgeConfigAPIPort-",
    "modmenu-",
    "language-reload-",
    "CrashAssistant-",
    "mixintrace-",
    "jei-",
    "mezz_config-",
    "Jade-",
    "appleskin-",
    "journeymap-",
    "MouseTweaks-",
)

ULTRA_ONLY_PREFIXES = (
    "DistantHorizons-",
    "iris-fabric-",
)

TIERS = {
    "bigbois-lite-26.3": {
        "name": "BigBois Lite (26.3)",
        "summary": "Essential 26.3 Fabric performance + BigBois server companion mods",
        "include_dh_shaders": False,
        "filter": lambda f: f.startswith(LITE_PREFIXES),
    },
    "bigbois-standard-26.3": {
        "name": "BigBois Standard (26.3)",
        "summary": "Performance + vanilla+ visual & QoL polish (no DH/shaders)",
        "include_dh_shaders": False,
        "filter": lambda f: not f.startswith(ULTRA_ONLY_PREFIXES),
    },
    "bigbois-ultra-dh-shaders-26.3": {
        "name": "BigBois Ultra DH+Shaders (26.3)",
        "summary": "Full 26.3 pack with Distant Horizons 3.3.4 (256 LOD) & Iris + MakeUp-UltraFast",
        "include_dh_shaders": True,
        "filter": lambda f: True,
    },
}


def add_overrides(zf: zipfile.ZipFile, jars: list[Path], include_dh_shaders: bool) -> None:
    """Write mods, configs, options.txt, servers.dat, and optional shaderpacks into overrides/."""
    for jar in jars:
        zf.write(jar, f"overrides/mods/{jar.name}")

    for fname in ("options.txt", "servers.dat"):
        fpath = SRC / fname
        if fpath.exists():
            zf.write(fpath, f"overrides/{fname}")

    cfg_dir = SRC / "config"
    for p in sorted(cfg_dir.rglob("*")):
        if not p.is_file():
            continue
        rel = p.relative_to(cfg_dir)
        if not include_dh_shaders and (rel.name == "DistantHorizons.toml" or rel.name == "iris.properties"):
            continue
        if include_dh_shaders and rel.name == "iris.properties":
            text = p.read_text().replace("enableShaders=false", "enableShaders=true")
            zf.writestr(f"overrides/config/{rel.as_posix()}", text)
        else:
            zf.write(p, f"overrides/config/{rel.as_posix()}")

    if include_dh_shaders:
        sp_dir = SRC / "shaderpacks"
        if sp_dir.exists():
            for p in sorted(sp_dir.rglob("*")):
                if p.is_file():
                    zf.write(p, f"overrides/shaderpacks/{p.relative_to(sp_dir).as_posix()}")


def export_tier(slug: str, spec: dict, all_jars: list[Path]) -> tuple[int, Path, Path]:
    """Build both .mrpack and CurseForge .zip for a single tier."""
    jars = [j for j in all_jars if spec["filter"](j.name)]

    mrpack_path = OUT / f"{slug}.mrpack"
    mrpack_index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": "1.0.0",
        "name": spec["name"],
        "summary": spec["summary"],
        "files": [],
        "dependencies": {
            "minecraft": "26.3",
            "fabric-loader": "0.20.1",
        },
    }
    with zipfile.ZipFile(mrpack_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("modrinth.index.json", json.dumps(mrpack_index, indent=2))
        add_overrides(zf, jars, spec["include_dh_shaders"])

    cf_path = OUT / f"{slug}.zip"
    cf_manifest = {
        "minecraft": {
            "version": "26.3",
            "modLoaders": [{"id": "fabric-0.20.1", "primary": True}],
        },
        "manifestType": "minecraftModpack",
        "manifestVersion": 1,
        "name": spec["name"],
        "version": "1.0.0",
        "author": os.environ.get("PACK_AUTHOR", "BigBois SMP"),
        "files": [],
        "overrides": "overrides",
    }
    with zipfile.ZipFile(cf_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("manifest.json", json.dumps(cf_manifest, indent=2))
        add_overrides(zf, jars, spec["include_dh_shaders"])

    return len(jars), mrpack_path, cf_path


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    all_jars = sorted((SRC / "mods").glob("*.jar"))
    for slug, spec in TIERS.items():
        count, mr, cf = export_tier(slug, spec, all_jars)
        print(f"{slug}: {count} mods -> {mr.name} ({mr.stat().st_size // 1048576} MiB), {cf.name} ({cf.stat().st_size // 1048576} MiB)")


if __name__ == "__main__":
    main()
