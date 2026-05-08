# ProxyChat

Proximity voice chat for Minecraft 1.7.10, designed for the **GregTech: New Horizons** modpack.

Modeled after Simple Voice Chat in behavior. ProxyChat is an independent, clean-room
implementation; it does not share code with Simple Voice Chat.

## Status

Phase 0 — toolchain bootstrap. Not functional yet. See [PLAN.md](PLAN.md) for the roadmap.

## Requirements (planned)

- Minecraft 1.7.10
- Forge 10.13.4.1614
- [UniMixins](https://github.com/LegacyModdingMC/UniMixins)
- [lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify) — required, provides LWJGL 3 / modern Java

## Build

```sh
./gradlew build
```

## Run dev client

```sh
./gradlew runClient
```

## License

MIT — see [LICENSE](LICENSE).
