# Architecture

## Scope

Seerrfin TV is a native Kotlin Android TV application. Jellyfin is required;
Seerr is an optional provider after the initial Jellyfin MVP. There is no extra
backend.

```text
Compose for TV screens -> ViewModels -> app domain models
                                   |-> UnifiedCatalogRepository
                                   |     |-> JellyfinRepository -> official SDK
                                   |     `-> SeerrRepository ----> REST client
                                   `-> PlaybackCoordinator ------> Media3 + Jellyfin

AppContainer -> repositories, clients, session stores, playback dependencies
```

## Gradle and package structure

Start with one `app` Gradle module. Split only after measured build-time,
ownership, or reuse pressure. Suggested packages inside the application
namespace:

```text
app/                 Application, AppContainer, navigation root
domain/model/        app-owned media, identity, request and playback models
domain/repository/   repository contracts
data/jellyfin/       SDK adapter, authentication, DTO-to-domain mapping
data/seerr/          REST client, cookie session, DTO-to-domain mapping
data/catalog/        unified catalogue and reconciliation
data/session/        tokens, encrypted sessions, preferences
playback/            PlaybackCoordinator and Media3 controller
feature/home/        screen, ViewModel, UI state
feature/details/     screen, ViewModel, UI state
feature/search/      screen, ViewModel, UI state
feature/player/      player screen and remote controls
ui/focus/            shared D-pad focus helpers
```

Dependencies are wired manually through `AppContainer` and constructors. Do
not introduce Hilt until manual wiring creates concrete maintenance pain.

## UI and state

- Compose for TV components are the default UI primitives.
- A ViewModel exposes one immutable `StateFlow<UiState>` per screen and accepts
  explicit UI events; it does not expose SDK or HTTP DTOs.
- Navigation preserves focused item identity and list position. Use focus
  groups, explicit initial focus, focus restoration, and predictable Back
  behavior. Every screen must be usable without touch.
- The Media3 player receives D-pad and media key events explicitly; player
  ownership follows the player screen lifecycle rather than a ViewModel.

## Provider boundaries

### Jellyfin

Depend on `org.jellyfin.sdk:jellyfin-core` only. It provides the official API,
models, discovery, WebSocket support, and OkHttp transport transitively. A
`JellyfinRepository` owns API instances and maps SDK responses into domain
models. Jellyfin and Seerr sessions remain independent.

### Seerr

Use a small typed OkHttp + kotlinx.serialization client for only the documented
MVP endpoints. Keep the `/api/v1` contract at the client boundary. On Jellyfin
deployments, authenticate through Seerr's Jellyfin Quick Connect flow and keep
the returned user-scoped `connect.sid` cookie in encrypted storage; never ship
the administrator `X-Api-Key`. Validate every restored or newly created session
through `/auth/me`, and use its server-provided permission mask before exposing
request actions.

Do not add an OpenAPI generator initially. Reconsider only if handwritten
endpoint drift or model volume becomes costly.

## Unified catalogue

`UnifiedCatalogRepository` combines provider results without hiding their
origin. Use `(media type, TMDb ID)` as the primary cross-provider identity and
other stable external IDs such as TVDb or IMDb only as explicit fallbacks.
Never merge solely by title.

Keep source references and states separate:

- Jellyfin: available, playable, resumable, unknown.
- Seerr: not requested, pending, approved, declined, unavailable, unknown.
- Unified action: Play, Resume, Request, or status/follow-up.

Missing data is not explicit absence. Refreshes must not overwrite a known
state with an incomplete response.

## Playback

`PlaybackCoordinator` asks Jellyfin for playback information, supplies declared
device capabilities, and converts the selected server-provided source into a
Media3 item. Use the same Media3 1.11.1 version for ExoPlayer, HLS, Compose UI,
and session modules. Prefer direct play when supported, then server-approved
remux or transcode. Do not guess codec support, construct undocumented stream
URLs, or assume a container is directly playable.

The Media3 layer owns play/pause/seek, tracks, errors, and player lifecycle.
One foreground `MediaSession` exposes the active player to Android and handles
platform/media keys; Compose for TV owns D-pad navigation, focus, and visible
controls. Player and session end with the player screen. Background playback
and `MediaSessionService` are outside the MVP. The Jellyfin repository owns
progress, stop, and resume reporting. Validate containers, codecs, HDR, audio,
and subtitles on the physical TV in P02/P06.

## Sessions, storage, and errors

- Store non-secret settings and server choices in DataStore.
- Hide Jellyfin tokens and Seerr cookie material behind `SessionStore`; persist
  only Android-Keystore-encrypted ciphertext.
- Do not add Room until offline queries or measured cache needs justify it.
- Apply bounded timeouts and cancellation to network calls.
- Map network, authentication, permission, server, parsing, and playback
  failures to app-owned errors with retry guidance.
- Never log secrets, private server addresses, or personal library payloads.

## Test strategy

- Unit tests: mappings, identity/deduplication, repositories, ViewModels,
  session behavior, and playback-source decisions using fakes.
- Coroutine tests: deterministic dispatchers and Flow emissions.
- Compose tests: loading/error/content states, focus entry/restoration, Back.
- Integration tests: HTTP fixtures for Jellyfin/Seerr adapters; no private
  credentials in fixtures.
- Device validation: authorized physical TV for D-pad, remote keys, launcher,
  focus recovery, and representative playback. Emulator use is optional.

## Risks kept explicit

- Jellyfin server/SDK version coupling and playback capability negotiation.
- Seerr deployment-specific login options, cookie lifecycle, and permissions.
- Cross-provider identifiers that are absent or inconsistent.
- TV codec, subtitle, HDR, and audio differences that compilation cannot prove.
- Focus dead ends and lost focus after asynchronous row updates.

Version choices and their rationale are recorded in `docs/decisions.md`.
