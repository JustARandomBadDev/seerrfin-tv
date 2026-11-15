# Progress

## Current status

**P02.2 PASS on `dev`** — a negotiated P02.1 source now opens a foreground
Media3 player with video output, MediaSession integration, play/pause, bounded
10-second seeking, D-pad and media-key handling, safe playback errors, and Back
navigation that releases the player and restores the libraries screen.

## Completed tasks

- `P00.1` — environment audit: Git/GitHub, Codex, Superpowers, Context7,
  Android SDK, JDK, and physical TV capability were verified as documented.
- `P00.2` — compatible build, SDK, UI, playback, Jellyfin, networking, storage,
  and test decisions were recorded in `docs/decisions.md` and
  `docs/architecture.md`.
- `P00.3` — one-module Android TV project, Gradle Wrapper, version catalog,
  Compose for TV bootstrap screen, launcher manifest, and focused unit test.
- `P00.4` — reproducible wrapper build, installation and launch on the physical
  TV, plus visual D-pad, activation, and Back validation.
- `P00.5` — active Android CI with a successful unit-test and debug-build run,
  plus publication of the debug APK artifact.
- `P01.1` — remote-friendly server address entry, local URL validation,
  unauthenticated Jellyfin connectivity check, network error mapping, and
  DataStore persistence after success.
- `P01.2` — username/password authentication through Jellyfin Kotlin SDK 1.9.4,
  TV-focused login UI, app-owned failure mapping, and encrypted local session
  persistence backed by Android Keystore AES-GCM.
- `P01.3` — encrypted-session restoration, authenticated Jellyfin SDK 1.9.4
  client initialization, user-view library loading, retry/error/empty states,
  and D-pad-focused Android TV rendering.
- `P02.1` — recursive paginated playable-item lookup, conservative Jellyfin
  playback capability negotiation, server-authorized source selection, and a
  temporary D-pad library activation surface.
- `P02.2` — screen-owned Media3 playback, foreground MediaSession, TV remote
  play/pause/seek controls, bounded streaming timeout, safe errors, and player
  lifecycle cleanup on Back.

## P02.2 verification — PASS (2026-11-15)

- Media3 1.11.1 documentation confirmed the Compose `PlayerSurface`, ExoPlayer,
  HLS, and MediaSession APIs. The official Jellyfin Android TV client confirmed
  direct Player commands, seekability checks, session-to-player binding, and a
  playback HTTP client with relaxed request timing; no GPL-licensed code was
  copied.
- The player consumes the exact P02.1 source and required HTTP headers without
  logging them. Its screen owns and releases ExoPlayer and MediaSession, pauses
  when the app leaves the foreground, keeps the display awake while active,
  and exposes immutable UI state through a ViewModel `StateFlow`.
- D-pad Center/Enter and media keys control play/pause; Left/Right and media
  rewind/fast-forward seek by a bounded 10 seconds only when the item is
  seekable. Playback intent makes pause reliable during buffering, and held
  toggle keys execute once while held seek keys may repeat. Back closes
  playback, removes MediaSession, retains the loaded libraries, and restores a
  single focused library action.
- A real transcode initially exposed Media3's default response-header timeout.
  Process-scoped evidence isolated a `SocketTimeoutException`; a tested,
  bounded 60-second playback read timeout fixed startup while preserving the
  existing negotiation and security boundaries.
- The final `:app:testDebugUnitTest :app:assembleDebug` run passed 94 tests with
  no failures or errors and produced the debug APK with JDK 17.
- The final APK installed with `-r` on the authorized Android 14
  `Smart_TV_Pro`. A real movie rendered and played; Center paused at 01:04,
  Right moved to 01:14, Left returned to 01:04, Center resumed, and Back
  returned to the libraries before a second Back returned to the TV launcher.
- A final remote regression check paused during initial buffering at 00:00;
  after preparation completed, an injected long-press Center event resumed
  once and remained playing instead of toggling repeatedly.
- A temporary validation-only unsupported URI produced the safe on-device
  playback error and Back recovery without displaying source details. It was
  removed before the final build and installation. Final process-scoped logs
  contained no fatal exception or URL, authorization, token, password, or API
  key markers; temporary captures and dumps were removed.

## P02.1 verification — PASS (2026-11-15)

- Context7 and the resolved Jellyfin SDK 1.9.4 artifacts confirmed
  `LibraryApi.getItems`, `MediaInfoApi.getPostedPlaybackInfo`, device-profile
  builders, SDK video URL construction, and server-provided transcoding URLs.
  The official Jellyfin Android TV client confirmed the playback negotiation
  pattern and direct play → direct stream → transcode decision order; no
  GPL-licensed code was copied.
- Recursive item lookup filters movies, episodes, and videos, uses deterministic
  bounded pages, terminates safely on empty pages and folders, and continues
  past candidates that expose no authorized usable source. SDK 401, timeout,
  TLS, server, malformed-response, cancellation, empty, and success paths map
  to app-owned results.
- Playback information is requested with a conservative H.264/AAC MP4 direct
  profile and H.264/AAC HLS fallback. Selection requires both the matching
  server capability flag and the URL data needed by that method, prefers
  direct play then direct stream then transcode, and rejects a missing media
  source identity. Sensitive playback models redact their string output.
- The libraries ViewModel keeps the restored session private, exposes immutable
  resolution state through `StateFlow`, suppresses duplicate activation while
  loading, preserves the library list for retry, and clears a rejected session
  before returning to login. No Media3 player or P02.2 behavior was added.
- The complete `:app:testDebugUnitTest :app:assembleDebug` validation passed 78
  unit tests with no failures or errors and produced the debug APK with JDK 17.
- The final APK installed with `-r` on the authorized Android 14
  `Smart_TV_Pro`, preserving the real encrypted session. D-pad Center on the
  focused first library resolved a real server-approved transcode source;
  Down/Up moved focus predictably between libraries, Back returned to the TV
  launcher, and relaunch restored the library screen. The final installed APK
  re-resolved the source after the last code change.
- Process-scoped logs contained no fatal exception or authorization, token,
  password, API-key, or URL markers. Temporary screenshots and UI dumps that
  could contain personal library data were removed.

## P01.3 verification — PASS (2026-11-15)

- Context7 and the resolved 1.9.4 artifacts confirmed authenticated
  `createApi(baseUrl, accessToken)` construction and `UserViewApi.getUserViews`
  for the restored user UUID. The repository maps SDK DTOs to app-owned library
  models without exposing tokens or personal payloads to logs.
- Session restoration distinguishes missing, restored, and unreadable encrypted
  records. Corrupt or unreadable records are cleared; HTTP 401 and malformed
  restored identities clear the invalid session and request a fresh login.
  Network, TLS, other HTTP, response, cancellation, empty, and success paths
  have focused coverage.
- The full `:app:testDebugUnitTest :app:assembleDebug` validation passed 58
  unit tests with no failures and produced the debug APK with JDK 17.
- The APK installed with `-r` on the authorized Android 14 `Smart_TV_Pro`,
  preserving app data. After a complete process stop, the P01.2 encrypted
  session restored directly to the real accessible libraries without asking
  for credentials. Initial focus, D-pad Down/Up movement with scrolling, Back
  to the TV launcher, relaunch, and one focused element were verified.
- A temporary debug fixture replaced only the local encrypted session with
  random invalid values while the original ciphertext remained backed up in
  private app storage. The real server returned 401, the app cleared the active
  record, and the login screen displayed the expired/revoked notice. The final
  APK was then reinstalled, the original ciphertext restored with the same
  SHA-256 hash and mode `600`, the temporary backup removed, and the libraries
  loaded again.
- Final process-scoped logs contained no fatal exception and no authorization,
  token, or password markers. Temporary UI dumps, screenshots, and the fixture
  workspace were removed.

## P01.2 verification — PASS (2026-11-15)

- Context7 and the resolved 1.9.4 artifacts confirmed
  `authenticationApi.authenticateUserByName`; the adapter maps HTTP 401,
  timeout, TLS, other HTTP status, malformed content, SDK client failure, and
  coroutine cancellation without logging request values.
- The login ViewModel validates only the username so Jellyfin accounts with an
  empty password remain supported, blocks duplicate submission, clears the
  password after successful server authentication, and reports secure-storage
  failure before exposing an authenticated UI state.
- Session persistence serializes only access token, user id, and server id,
  encrypts them with AES/GCM/NoPadding and a non-exportable Android Keystore
  key, and stores only versioned Base64 IV/ciphertext in private preferences.
  Corrupt or undecryptable records are cleared. Sensitive models redact their
  string representations.
- Focused tests cover SDK mapping, encrypted-store round trips and corruption,
  ViewModel behavior, D-pad routing, and Back state. The full unit-test task
  passed 42 tests with no failures, and `assembleDebug` passed with JDK 17.
- The debug APK installed on the authorized Android TV. The saved P01.1 server
  reached the new login screen; visual checks confirmed initial focus,
  username → password → action traversal, password masking, local validation,
  Back navigation, and an actionable HTTP 401 response using fictitious input.
  Failed authentication created no session preference, the process remained
  active, and filtered logs contained no fatal exception or submitted value.
- A dedicated test account authenticated successfully against the real server.
  The success UI was reached only after the SDK returned a non-empty access
  token, user id, and server id and the secure session write completed.
- The private preference file had mode `600` and contained exactly one
  versioned `v1:IV:ciphertext` record. Its hash remained unchanged across a
  complete process stop and relaunch, proving durable encrypted persistence.
  No credential, token, or authorization header appeared in the process-scoped
  logs, and all sensitive temporary files were removed.
- After relaunch, the encrypted record remained available but the app returned
  to server configuration because constructing an authenticated SDK client from
  that record is deliberately P01.3 scope, not P01.2.

## P01.1 verification (2026-11-15)

- `jellyfin-core:1.9.4` calls the public system-information endpoint with
  bounded timeouts; SDK timeout, TLS, HTTP status, invalid response, and client
  failures map to app-owned UI errors.
- URL tests cover missing schemes, HTTP/HTTPS, valid and invalid port bounds,
  encoded reverse-proxy paths without double encoding, credentials, queries,
  fragments, malformed hosts, trailing slashes, and normalization idempotence.
- Repository tests require a recognizable Jellyfin product and version; an
  otherwise valid empty JSON response is rejected instead of being persisted.
- ViewModel tests cover a saved URL, local validation, loading, successful
  persistence, duplicate submission, and every repository error result.
- A runtime regression test verifies that the SLF4J facade required by the SDK
  is packaged. `slf4j-nop:2.0.20` prevents both the observed connection-time crash
  and logging of private server addresses.
- The full unit-test task and `assembleDebug` passed with JDK 17. The generated
  manifest contains Internet access, the application container, cleartext HTTP
  support for LAN deployments, and the existing Leanback launcher activity.
- The APK installed on the authorized physical TV. Captures verified initial
  input focus, visible focus movement Down to the connect action, Up to the
  input, keyboard entry and completion, activation, actionable network error
  rendering, and Back returning to the TV home screen.
- With explicit authorization, the app reached the target Jellyfin public
  system-information endpoint, displayed success, persisted the normalized
  address, and restored it after Back and relaunch. No credentials were used or
  stored, and the address is intentionally omitted from repository files.
- The final unit-test task passed 22 tests with no failures; `assembleDebug`
  passed, the process remained active on-device, and filtered application logs
  contained no fatal exception.

## P00.5 verification (2026-11-14)

- The workflow uses JDK 17 and explicitly provisions Android API 37.0 and Build
  Tools 36.1.0 before running the checked-in Gradle Wrapper.
- CI runs `:app:testDebugUnitTest` and `:app:assembleDebug`, then uploads the
  exact debug APK with a missing-artifact failure guard.
- Workflow permissions are limited to read-only repository contents; concurrent
  runs on the same ref cancel obsolete work.
- Third-party actions are pinned to verified immutable commit SHAs.
- Run `43636555495` completed with conclusion `success` at commit
  `ea8bbe445e65fef4ca291ade6cdb1e4ed0e2e4d9`; every job step succeeded.
- Artifact `seerrfin-tv-debug` was published and is not expired.
- Two non-blocking notices remain: Gradle 9.5.0 is reported as out of date, and
  the `ubuntu-latest` label is scheduled to migrate to Ubuntu 26 beginning
  October 19, 2026. Neither notice affected this run; review the Gradle pin and
  runner image during future dependency or CI maintenance.

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
- Android platforms 35, 36, 36.1, and 37.0 are installed.
- Gradle Wrapper 9.5.0 ran with JDK 17.0.21 from
  `/usr/lib/jvm/java-17-openjdk`.
- Wrapper scripts, JAR, properties, and version catalog are present in `HEAD`;
  `gradlew` is tracked with executable mode `100755`.
- `./gradlew testDebugUnitTest` passed: 1 test, 0 failures.
- `./gradlew assembleDebug` passed. The APK is
  `app/build/outputs/apk/debug/app-debug.apk`.
- APK inspection confirmed `compileSdk 37`, `targetSdk 36`, `minSdk 26`, the
  no-touch declaration, Leanback launcher activity, banner, and icon.
- The APK installed successfully on the authorized Android TV API 34 using one
  explicitly selected ADB transport. Cold launch succeeded and the process
  remained active. A launch through the Leanback launcher category also
  resolved to `MainActivity`.
- Captures confirmed initial focus and D-pad transitions Right, Down, Up, and
  Left across all three actions. D-pad Center activated `Focus 3` and displayed
  its confirmation. Back returned to the TV home activity.
- Filtered application logcat contained no fatal exception during validation.
- A dark-theme contrast issue found on-device was corrected and visually
  rechecked.

The project license can remain pending until before public distribution. The
exact user-scoped Seerr login mode is intentionally deferred to P04.1, where it
will be checked against the target deployment.

## Next task

`P02.3` — report playback progress, stop, and resume to Jellyfin. It has not
been started and requires a new explicit request.
