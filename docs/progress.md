# Progress

## Current status

**P00.2 complete** — the bootstrap stack and MVP architecture are selected and
recorded. No Android project has been generated yet.

## Completed tasks

- `P00.1` — environment audit: Git/GitHub, Codex, Superpowers, Context7,
  Android SDK, JDK, and physical TV capability were verified as documented.
- `P00.2` — compatible build, SDK, UI, playback, Jellyfin, networking, storage,
  and test decisions were recorded in `docs/decisions.md` and
  `docs/architecture.md`.

## Verified results (2026-11-14)

- HTTPS origin and `git ls-remote --exit-code origin HEAD` succeed.
- JDK 17.0.21 runs from `/usr/lib/jvm/java-17-openjdk`.
- The current Codex shell still resolves SDKMAN Java 24 unless JDK 17 is
  selected explicitly; Gradle commands must use the setup documented here.
- Context7 MCP returned current Android documentation for AGP built-in Kotlin,
  SDK levels, Compose for TV focus, and Media3 TV input.
- AGP 9.3.3, Gradle 9.5.0, Compose for TV 1.1.0, Media3 1.11.1,
  Jellyfin SDK 1.9.4, coroutines 1.11.0, serialization 1.11.0, and OkHttp
  5.5.1 artifacts are available from their official repositories.
- Media3's initial module set covers ExoPlayer, HLS, Compose UI, and a
  foreground MediaSession; background playback remains outside the MVP.
- Jellyfin SDK 1.9.4 declares Jellyfin 12.0 minimum and 12.4 recommended,
  matching the target server version 12.4.
- Android platforms 35, 36, and 36.1 are installed; API 37 is not installed.
- No Gradle wrapper, Android source, AVD, deployment, or build was created.

## Prerequisites for P00.3

- Choose the final application name and Android namespace.
- Install Android platform API 37 without reinstalling working SDK packages.
- Ensure Gradle runs with JDK 17 in the terminal and Android Studio.

The project license can remain pending until before public distribution. The
exact user-scoped Seerr login mode is intentionally deferred to P04.1, where it
will be checked against the target deployment.

## Next task

`P00.3` — create one minimal native Android TV Gradle project using the accepted
version catalog and a remote-focusable Compose for TV screen. Do not start
P00.4 device installation or CI activation as part of P00.3.
