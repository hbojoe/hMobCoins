# hMobCoins

Paper/Purpur plugin that drops physical ItemsAdder coin items from vanilla hostile mob kills.

## Build

```bash
mvn package
```

The compiled plugin jar is written to:

```text
target/hmobcoins-1.0.0.jar
```

## Install

Place the jar in your server's `plugins` folder and restart the server. The plugin data folder will be:

```text
plugins/hMobCoins
```

## Command

```text
/mobcoins reload
```

Permission:

```text
mobcoins.admin
```

## Notes

- ItemsAdder is a soft dependency and is accessed through its `CustomStack` API when available.
- Invalid or missing ItemsAdder item IDs fail safely and are warned once per reload.
- Drops require a real player killer. Direct player damage and player projectile kills are supported.
- Configured mobs are restricted to the plugin's vanilla hostile mob whitelist.
- Citizens NPCs, common MythicMobs custom mobs, configured custom mob markers, custom-named mobs, armor stands, players, unsupported entities, and marked spawner mobs are skipped.
