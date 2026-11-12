# Architecture (initial proposal)

## Scope

A **native Android TV** Kotlin app, without a new mandatory backend.

```
Android TV UI (Compose for TV)
  |-- ViewModels / state / D-pad navigation
  |-- Unified catalog (merge, availability, requests)
  |     |-- Jellyfin repository --> Official Kotlin SDK --> Jellyfin server
  |     `-- Seerr repository ----> Typed REST client -----> Seerr server
  |-- Playback coordinator --> Media3 / ExoPlayer + Jellyfin playback APIs
  `-- Local settings / cache (DataStore; Room only if justified)
```

## Initial principles

- Start with a single `app` Gradle module and clear Kotlin packages. Add modules when scale justifies them.
- External API data stays behind repositories.
- Convert Jellyfin and Seerr responses into app-owned domain models.
- Prefer stable IDs such as TMDb IDs for cross-system matching; never merge solely by title.
- Model media states distinctly: available, playable, resumable, requested, pending, unavailable, unknown.
- Distinguish missing data from explicit absence; asynchronous refresh must not incorrectly overwrite known state.
- Keep authentication separate for Jellyfin and Seerr; do not assume their login endpoints or tokens are interchangeable.
- Jellyfin required, Seerr optional after MVP 3.
- Navigation and accessibility start with D-pad focus, long labels, Back and playback remote keys.

## Authentication and secrets

- Use only authenticated user-level flows supported by each server/version.
- Never bundle privileged server API keys into the APK or repository.
- Investigate safe local storage and token lifecycle in Phase 1 / Phase 4; document tradeoffs.
- Avoid logging credentials and server responses containing personal data.

## Playback risks to validate early

- Direct play vs remux vs transcode selection.
- Device-supported codecs, containers, subtitles and audio tracks.
- Progress, stop and resume reporting to Jellyfin.
- Robust handling of network interruptions.
- Validate on a real TV or Android TV emulator, not just compilation.

## Documentation sources

- Jellyfin Kotlin SDK: https://github.com/jellyfin/jellyfin-sdk-kotlin
- Seerr main repository: https://github.com/seerr-team/seerr
- Android TV: https://developer.android.com/training/tv
- AndroidX Media3: https://developer.android.com/media/media3
- Context7 (optional for version-specific docs): https://context7.com/docs/installation

Versions should be chosen using current official documentation during bootstrap and recorded in `docs/decisions.md`.
