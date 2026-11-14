# Architecture decisions

Decisions accepted for bootstrap on 2026-11-14. Dependency versions will be
pinned in `gradle/libs.versions.toml` when P00.3 creates the project.

## Technical baseline

| Item | Decision | Status |
| --- | --- | --- |
| Language | Kotlin 2.4.21, with AGP built-in Kotlin | Accepted |
| Build | AGP 9.3.3, Gradle Wrapper 9.5.0, JDK 17 | Accepted |
| Android SDK | `compileSdk = 37`, `targetSdk = 36`, `minSdk = 26`, Build Tools 36.1.0 | Accepted |
| Compose | Compose BOM 2026.09.00, Compose Compiler plugin 2.4.21 | Accepted |
| TV UI | `androidx.tv:tv-material:1.1.0` | Accepted |
| Playback | AndroidX Media3 1.11.1: ExoPlayer, HLS, Compose UI, session | Accepted |
| Jellyfin | `org.jellyfin.sdk:jellyfin-core:1.9.4` | Accepted |
| Async state | kotlinx.coroutines 1.11.0 and `Flow` / `StateFlow` | Accepted |
| Seerr transport | OkHttp 5.5.1 + kotlinx.serialization 1.11.0 | Accepted |
| Modules | One Android `app` module for the MVP | Accepted |
| Dependency injection | Manual constructor injection through an app container | Accepted |
| Persistence | DataStore for non-secret preferences; no Room initially | Accepted |
| Test device | Physical Android TV API 34; emulator optional | Accepted |

Only dependencies required by the first project are pinned here. Additional
AndroidX artifacts such as Navigation or DataStore receive a version when their
roadmap task first uses them, rather than becoming unused bootstrap dependencies.

## D001 — Build stack and Kotlin

**Decision.** Use AGP 9.3.3 with Gradle 9.5.0 and JDK 17. Keep AGP 9.x
built-in Kotlin enabled; do not apply `org.jetbrains.kotlin.android`. Raise
AGP's default KGP 2.2.10 to 2.4.21 in the root build classpath, and apply
`org.jetbrains.kotlin.plugin.compose` 2.4.21 to the Compose module. Apply the
matching `org.jetbrains.kotlin.plugin.serialization` plugin when the Seerr
client is introduced.

**Rationale.** Android Studio 2026.1.3 supports AGP through 9.3. Kotlin 2.4.21
is the current stable patch and can consume Jellyfin SDK 1.9.4, which is built
with Kotlin 2.4.20. The Compose compiler version must match Kotlin.

**Alternatives rejected.** AGP 9.4+ requires a newer Android Studio release.
AGP 8.x avoids built-in Kotlin but adds migration work to a new project.

**Implication.** Gradle must run on JDK 17. Do not commit a machine-specific
`org.gradle.java.home`; select JDK 17 in Android Studio, CI, and local commands.

## D002 — Android API levels

**Decision.** Compile with API 37, target API 36, and support API 26+.

**Rationale.** Jellyfin SDK 1.9.4 is compiled with API 37 and AGP 9.x checks
consumer compile SDK compatibility. `compileSdk` does not set the minimum
runtime version, so the app remains compatible with the API 34 test TV.
Targeting API 36 avoids claiming Android 17 behavior testing before an API 37
test device is available. API 26 avoids legacy pre-Android-8 desugaring and
TLS/media edge cases while covering the intended TV generation.

**Implication.** Platform API 37 must be installed before the first build. Pin
the already installed Build Tools 36.1.0, which is newer than AGP 9.3's 36.0.0
minimum/default, to avoid downloading a redundant copy. P00.3 must prove this
combination with dependency resolution and a build. Revisit `targetSdk` and
`minSdk` using real device and distribution needs.

## D003 — MVP structure

**Decision.** Start with one `app` Gradle module, packages split by feature and
responsibility, and interfaces at repository boundaries. Use an application
container for manual dependency wiring.

**Rationale.** One module keeps Gradle and AI-assisted changes easy to inspect.
Repository interfaces and app-owned domain models preserve testability without
premature module or dependency-injection framework overhead.

**Alternatives rejected.** No Hilt until manual wiring becomes measurably hard;
no multi-module split without build-time or ownership evidence.

## D004 — Jellyfin integration

**Decision.** Use only `jellyfin-core:1.9.4`. It exposes the generated API,
model, and OkHttp transport modules transitively. Keep SDK objects behind a
`JellyfinRepository` and map them to app-owned models.

**Rationale.** SDK 1.9.4 declares Jellyfin 12.0 as minimum and 12.4 as the
recommended API version, matching the target server 12.4.

**Implication.** Playback source selection must use Jellyfin playback APIs and
device capabilities; do not infer direct-play or transcoding URLs.

## D005 — Seerr integration and authentication

**Decision.** Implement only the documented `/api/v1` subset needed by the
MVP using OkHttp and kotlinx.serialization. Use a user-scoped cookie session
from a login mode enabled by the target deployment, then validate it with the
documented current-user endpoint. Never embed or request the administrator API
key.

**Rationale.** The OpenAPI specification supports cookie and API-key auth, but
the global API key is privileged. A small handwritten client is easier to
review than generating thousands of unused models.

**Alternatives rejected.** No OpenAPI generator until endpoint drift or model
volume demonstrates a maintenance benefit. Exact login mode remains a P04.1
deployment check rather than an assumption in bootstrap.

## D006 — State, storage, and errors

**Decision.** ViewModels expose immutable `StateFlow` UI state and accept UI
events. Repositories use suspend functions and `Flow`. Store non-secret
preferences in DataStore. Put Jellyfin tokens and Seerr session material behind
a `SessionStore` backed by Android Keystore encryption; never store secrets as
plain DataStore values.

Map transport, authentication, permission, server, and playback failures to
app-owned error types. UI errors are actionable and retryable; logs omit URLs,
tokens, credentials, and personal library payloads.

## D007 — Testing and TV interaction

**Decision.** Unit-test domain mapping, deduplication, repositories, ViewModels,
and playback-source decisions with fakes. Use Compose tests for state rendering
and focus restoration. Validate D-pad, Back, player keys, and focus recovery on
the authorized physical TV at P00.4 and later UI milestones.

**Implication.** Compilation alone never validates TV usability. The emulator
is optional and does not block bootstrap.

## D008 — Media3 modules and session lifetime

**Decision.** Pin these modules to the same Media3 1.11.1 version:
`media3-exoplayer`, `media3-exoplayer-hls`, `media3-ui-compose`, and
`media3-session`. HLS covers Jellyfin's expected adaptive transcode output;
other streaming modules are added only after a real playback response requires
them. Compose for TV owns visible controls and D-pad focus.

Create one `MediaSession` around the foreground player so Android can route
platform and remote media keys. D-pad navigation and seek gestures remain app
UI events. Release the player and session when the player screen ends. Do not
add `MediaSessionService` or background playback to the MVP; revisit only if
background playback becomes a requirement.

## Verified sources

- [AGP 9.3 release notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes)
- [AGP built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Compose compiler plugin](https://kotlinlang.org/docs/compose-compiler-migration-guide.html)
- [Compose for TV releases](https://developer.android.com/jetpack/androidx/releases/tv)
- [Media3 releases](https://developer.android.com/jetpack/androidx/releases/media3)
- [Media3 on Android TV](https://developer.android.com/media/implement/surfaces/android-tv)
- [Media3 HLS](https://developer.android.com/media/media3/exoplayer/hls)
- [Jellyfin Kotlin SDK releases](https://github.com/jellyfin/jellyfin-sdk-kotlin/releases)
- [Seerr OpenAPI specification](https://github.com/seerr-team/seerr/blob/develop/seerr-api.yml)

## Pending decisions

- Final application name, namespace, and branding before P00.3 creates files.
- Project license before public distribution.
- Exact Seerr login mode supported by the target deployment in P04.1.
