# Seerrfin TV

**A native Android TV application combining Jellyfin playback with Seerr discovery and media requests.**

> **Status:** Planning / project bootstrap. This repository does **not** yet contain a working Android application.
>
> Unofficial community project. Not affiliated with or endorsed by Jellyfin or Seerr.

## Goal

Provide a single, remote-friendly Android TV experience that can:

- Browse and play media already available in a Jellyfin library.
- Discover films and series through Seerr.
- Request missing media using the current user's Seerr permissions.
- Show contextual actions: **Play**, **Resume**, **Request**, or **Check request**.
- Avoid duplicate entries across Jellyfin and Seerr by matching stable external IDs where available.

## Technology direction

- **Language:** Kotlin
- **UI:** Jetpack Compose for TV
- **Playback:** AndroidX Media3 / ExoPlayer
- **Jellyfin:** Official Kotlin SDK
- **Seerr:** REST API, preferably typed from its published OpenAPI contract
- **Patterns:** ViewModel, repositories, Kotlin coroutines / Flow
- **Build:** Gradle Kotlin DSL
- **Testing:** Unit tests, mock API tests, Android TV device validation

The exact dependency versions and Android API levels must be selected after auditing the current toolchain and official documentation.

## Roadmap

1. Bootstrap and reproducible Android TV build
2. Jellyfin authentication
3. Minimal video playback and progress synchronization
4. Jellyfin library UI
5. Seerr authentication, discovery and requests
6. Unified catalog and status/action logic
7. Advanced playback support
8. TV UX, resilience and performance
9. Release validation

Detailed milestones and acceptance checks: [docs/roadmap.md](docs/roadmap.md).

## Getting started

1. Read [docs/setup.md](docs/setup.md).
2. Audit your environment with [prompts/00-audit-setup.md](prompts/00-audit-setup.md) from Codex CLI.
3. Review [docs/architecture.md](docs/architecture.md) and [AGENTS.md](AGENTS.md).
4. Have Codex implement **only** the bootstrap milestone, then validate it on an Android TV device.
5. Enable the CI workflow once a working Gradle wrapper and Android project exist (see `templates/android-ci.yml`).

## Security

Never commit credentials, Jellyfin access tokens, Seerr API keys, private server addresses or screenshots containing personal library data. The Android client must not ship an administrator Seerr API key. Authenticate users with supported mechanisms and enforce their permissions.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Issues and pull requests are welcome once the initial project scaffold is operational.

## License

**Not yet selected.** A license must be chosen before inviting code contributions or publishing releases. Dependencies and any reused third-party code require separate license compliance; see [docs/decisions.md](docs/decisions.md).
