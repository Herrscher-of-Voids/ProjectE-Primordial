# NeoForge 1.21.1 migration scaffold

This isolated Gradle project is the loader/build scaffold for the NeoForge branch. It currently contains only a minimal mod entrypoint; the Forge gameplay systems have not been ported yet, so this is not a playable release.

From the repository root, use JDK 21:

```powershell
.\gradlew.bat -p neoforge clean build
```

The `runClient` and `runServer` tasks use separate `neoforge/run/client` and `neoforge/run/server` directories. They must not use or overwrite a Forge 1.20.1 world.

Core versions are fixed in `gradle.properties` and `build.gradle`. Optional integrations will be added as their gameplay systems are ported.
