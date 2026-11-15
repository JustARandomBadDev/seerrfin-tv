# P02.1 Playback Source Design

## Intent

Prove, on the existing Android TV library screen, that Seerrfin TV can resolve
one real Jellyfin video item and obtain a server-authorized playback source.
This is the input to P02.2; P02.1 does not instantiate Media3 or start playback.

## Goals

- Activate an existing library with the TV remote as a temporary validation UI.
- Search recursively for movies, episodes, or videos with bounded pagination.
- Continue across empty folders, pages, and candidates without an usable source.
- POST playback information with a conservative TV device profile.
- Accept only source methods explicitly enabled by the Jellyfin response and
  backed by the URL data required for that method.
- Prefer direct play, then direct stream, then transcoding.
- Expose loading, ready, empty, authentication, and retryable failure states
  through the existing ViewModel `StateFlow`.

## Non-goals

- No Media3 player, play/pause/seek, progress reporting, or resume behavior.
- No media catalogue, details screen, episode browser, or P03 navigation.
- No advanced device codec discovery, track selection, or P06 capability matrix.

## Constraints

- Keep one Android module, app-owned domain models, the official Jellyfin Kotlin
  SDK 1.9.4, manual `AppContainer` wiring, MVVM, coroutines, and `StateFlow`.
- Do not construct undocumented endpoints or assume a server media path is
  directly playable.
- Never render or log playback URLs, required headers, access tokens, server
  addresses, or personal payloads. Sensitive playback models redact `toString`.
- Preserve D-pad focus and normal Back behavior on Android TV.

## Approaches

1. **Selected: activate a library and resolve its first usable video.** Query
   recursively in deterministic pages, negotiate each candidate, and keep the
   existing library list as the temporary TV surface. This proves the complete
   P02.1 boundary without building P03.
2. Resolve a hidden automatic item at startup. Rejected because the real-TV
   action and selected library would be difficult to verify.
3. Build a media picker. Rejected because browsing and media navigation belong
   to P03.

## Design

The repository receives the authenticated session and selected library ID. It
uses the SDK library API with `recursive=true`, playable video item kinds,
`isMissing=false`, a stable sort, and a fixed page size. For every candidate it
POSTs `PlaybackInfoDto` with a conservative profile: H.264/AAC MP4 direct play
and H.264/AAC HLS streaming fallback. The search continues when a candidate
has no authorized usable method. It stops on transport, server, malformed
response, or authorization failures so those conditions remain actionable.

Source selection scans server-returned media sources by method priority. Direct
play requires `supportsDirectPlay` and an SDK-generated video URL (or a
server-provided remote URL). Direct stream requires `supportsDirectStream` and
a nonblank transcoding URL. Transcoding requires `supportsTranscoding` and a
nonblank transcoding URL. The selected source retains item identity, media
source identity, play session, method, container, URL, and required HTTP
headers for P02.2 without exposing them in UI or logs.

The existing libraries ViewModel retains the restored session privately. A
library activation starts one resolution at a time and updates immutable UI
state. The Compose screen keeps library buttons focusable and shows only the
resolved item name and method, or an actionable retry message. Activating a
library again retries. Session rejection clears the encrypted record and
returns to login through the existing path.
