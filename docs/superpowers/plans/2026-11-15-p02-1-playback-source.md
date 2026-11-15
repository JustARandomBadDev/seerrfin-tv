# P02.1 Playback Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve the first server-authorized playable Jellyfin video in an activated library and expose the result on Android TV without starting playback.

**Architecture:** Extend the existing Jellyfin repository boundary with app-owned playback models and a paginated SDK adapter. The existing libraries ViewModel owns the temporary validation interaction through `StateFlow`, and the Compose screen retains its remote-focusable library list while rendering resolution status.

**Tech Stack:** Kotlin 2.4.21, coroutines/StateFlow 1.11.0, Compose for TV 1.1.0, Jellyfin Kotlin SDK 1.9.4, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-15-p02-1-playback-source-design.md`

## Global Constraints

- Implement P02.1 only; do not create a Media3 player or begin P02.2/P03.
- Use the official Jellyfin SDK and server-returned capability flags and URLs.
- Preserve D-pad focus, direct library activation, normal Back behavior, MVVM, `StateFlow`, and `AppContainer`.
- Do not log or render tokens, URLs, headers, server addresses, or personal payloads.
- Do not commit or push any change.

## Review Focus

- A page containing only candidates without usable playback sources must advance rather than report empty early; Task 2 tests it.
- An empty page must terminate without an infinite pagination loop; Task 2 tests it.
- A source capability flag without its required URL must not be selected; Task 1 tests it.
- HTTP 401 during either item lookup or playback negotiation must clear the session and return to login; Tasks 2 and 3 test it.
- Repeated D-pad activation while resolution is loading must not create concurrent requests or lose focus; Task 3 tests request suppression and Task 4 preserves the focusable list.

---

### Task 1: Playback domain contract and source decision

**Files:**
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/domain/model/JellyfinPlaybackSource.kt`
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/playback/PlaybackSourceSelector.kt`
- Test: `app/src/test/kotlin/io/github/justarandombaddev/seerrfintv/playback/PlaybackSourceSelectorTest.kt`

**Interfaces:**
- Consumes: app-owned item identity plus server media-source flags and URL factories.
- Produces: `PlaybackMethod`, redacted `JellyfinPlaybackSource`, and `PlaybackSourceSelector.select(item, playSessionId, sources): JellyfinPlaybackSource?`.

- [x] Write failing tests proving direct play wins over direct stream/transcode, unavailable methods are skipped, a missing required URL invalidates a method, remote direct-play paths are accepted only when the server marks them direct-play capable, and sensitive source values are redacted from `toString`.
- [x] Run `:app:testDebugUnitTest --tests '*PlaybackSourceSelectorTest'` and confirm failure because the domain and selector do not exist.
- [x] Implement the minimal app-owned models and pure selector with method priority `DIRECT_PLAY`, `DIRECT_STREAM`, `TRANSCODE`.
- [x] Re-run the targeted test and confirm it passes.

### Task 2: Paginated Jellyfin SDK resolution

**Files:**
- Modify: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/domain/repository/JellyfinRepository.kt`
- Modify: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/data/jellyfin/SdkJellyfinRepository.kt`
- Modify: `app/src/test/kotlin/io/github/justarandombaddev/seerrfintv/data/jellyfin/SdkJellyfinRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1 `PlaybackSourceSelector` and playback models.
- Produces: `JellyfinRepository.resolvePlaybackSource(serverUrl, session, libraryId): JellyfinPlaybackResolutionResult` with success, empty, invalid-session, network, TLS, server, invalid-response, and unexpected outcomes.

- [x] Add failing repository tests for recursive query parameters, deterministic page offsets, empty termination, unusable candidates followed by a later success, all-unusable exhaustion, malformed IDs, 401 mapping, transport mappings, and cancellation propagation.
- [x] Run `:app:testDebugUnitTest --tests '*SdkJellyfinRepositoryTest'` and confirm the new tests fail because resolution is absent.
- [x] Add SDK-facing page and playback-info provider interfaces, the conservative `DeviceProfile`, official SDK URL construction, pagination, source selection, and result/error mapping.
- [x] Re-run the targeted repository test and confirm it passes.

### Task 3: ViewModel resolution state

**Files:**
- Modify: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/feature/home/JellyfinLibrariesViewModel.kt`
- Modify: `app/src/test/kotlin/io/github/justarandombaddev/seerrfintv/feature/home/JellyfinLibrariesViewModelTest.kt`

**Interfaces:**
- Consumes: Task 2 `resolvePlaybackSource` result and the privately restored Jellyfin session.
- Produces: `resolveLibrary(libraryId)` plus immutable playback-resolution fields in `JellyfinLibrariesUiState`.

- [x] Add failing ViewModel tests for success, empty library, each retryable error, 401 session clearing/login, duplicate activation suppression, retry after failure, and unexpected exception mapping.
- [x] Run `:app:testDebugUnitTest --tests '*JellyfinLibrariesViewModelTest'` and confirm the new tests fail.
- [x] Retain the active session privately, implement the one-at-a-time resolution event, and map results to immutable `StateFlow` UI state without discarding the library list.
- [x] Re-run the targeted ViewModel test and confirm it passes.

### Task 4: Temporary Android TV validation UI

**Files:**
- Modify: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/feature/home/JellyfinLibrariesScreen.kt`
- Modify: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/kotlin/io/github/justarandombaddev/seerrfintv/feature/home/JellyfinLibrariesScreenTest.kt`

**Interfaces:**
- Consumes: Task 3 UI state and `resolveLibrary(libraryId)` event.
- Produces: direct D-pad activation of existing library buttons and non-sensitive loading/ready/empty/error feedback.

- [x] Add a failing screen behavior test proving activation is accepted only for stable library content and suppressed during resolution.
- [x] Run `:app:testDebugUnitTest --tests '*JellyfinLibrariesScreenTest'` and confirm failure because the behavior function is absent.
- [x] Wire library `onClick`, add status text that never contains a URL/header/token, and keep the first library as the initial focus target.
- [x] Re-run the targeted screen test and confirm it passes.
- [x] Run the complete unit suite and `assembleDebug` with JDK 17.
- [x] Follow `docs/android-tv-testing.md`: select and identify one authorized TV transport, install with `-r`, launch, activate a real library using D-pad, verify loading and a ready/empty/actionable error outcome, verify focus and Back, and check process-scoped fatal logs without retaining personal captures.
- [x] Update `docs/roadmap.md` and `docs/progress.md` only with observed results, then run `git diff --check`.
