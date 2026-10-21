# ProjectE:Primordial for NeoForge 1.21.1

This isolated Gradle project builds the NeoForge 1.21.1 version alongside the Forge 1.20.1 source tree.

The first migrated gameplay slice is the EMC balance HUD: on player login, the server reads the player's ProjectE EMC balance and sends it through a versioned NeoForge payload. The server checks balances every 10 ticks and sends an update only when a player's value changes. The client renders the balance above the hotbar, toggles it with `K`, and supports the compact, engineering, and scientific display modes in its client config.

This slice does not migrate offline balance/knowledge queues, EMC transactions, or the other gameplay systems. It is an in-progress development build, not a playable release.

From the repository root, use JDK 21:

```powershell
.\gradlew.bat -p neoforge clean build
```

The `runClient` and `runServer` tasks use separate `neoforge/run/client` and `neoforge/run/server` directories. They must not use or overwrite a Forge 1.20.1 world.

Core versions are fixed in `gradle.properties` and `build.gradle`. Optional integrations will be added as their gameplay systems are ported.
