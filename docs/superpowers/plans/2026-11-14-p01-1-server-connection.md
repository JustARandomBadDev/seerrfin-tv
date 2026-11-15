# P01.1 Server Connection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure and validate a Jellyfin server URL from Android TV, verify it through the official SDK, and expose actionable connection states without implementing authentication.

**Architecture:** A Compose for TV screen sends explicit events to a `ViewModel` exposing one immutable `StateFlow`. Pure URL normalization precedes a `JellyfinRepository` connectivity check; a DataStore-backed settings repository saves the normalized non-secret URL only after success. `AppContainer` wires the dependencies manually.

**Tech Stack:** Kotlin 2.4.21, Compose for TV 1.1.0, AndroidX Lifecycle, Preferences DataStore, coroutines 1.11.0, Jellyfin Kotlin SDK 1.9.4.

**Spec:** `docs/roadmap.md` P01.1, `docs/architecture.md`, and `docs/decisions.md` D003/D004/D006/D007.

## Global Constraints

- Work only on P01.1; no authentication, catalogue, playback, or Seerr code.
- Never persist or log credentials, tokens, private addresses, or library data.
- Keep one Gradle module and manual constructor injection through `AppContainer`.
- Do not commit or push P01.1 changes in this session.

## Review Focus

- Blank, malformed, non-HTTP, credential-bearing, query, and fragment URLs are rejected locally.
- Host, port, reverse-proxy path, and trailing slash are normalized without losing meaningful path data.
- Repeated connect actions do not run concurrently, and loading always terminates in success or an actionable error.
- SDK timeout, TLS, HTTP-status, invalid-content, and unexpected client failures do not expose sensitive details.
- D-pad focus moves predictably between the address field and connect action; Back is not intercepted.

---

### Task 1: Dependencies and pure URL normalization

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/data/jellyfin/ServerUrlNormalizer.kt`
- Test: `app/src/test/kotlin/io/github/justarandombaddev/seerrfintv/data/jellyfin/ServerUrlNormalizerTest.kt`

- [ ] Add only the validated Lifecycle, DataStore, coroutines, and Jellyfin SDK dependencies.
- [ ] Write normalization tests and run them to observe the expected missing-implementation failure.
- [ ] Implement HTTP/HTTPS validation, credential/query/fragment rejection, and trailing-slash normalization.
- [ ] Run the focused tests to green.

### Task 2: Repository and settings boundaries

**Files:**
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/domain/repository/JellyfinRepository.kt`
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/domain/repository/ServerSettingsRepository.kt`
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/data/jellyfin/SdkJellyfinRepository.kt`
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/data/settings/DataStoreServerSettingsRepository.kt`
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/AppContainer.kt`
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/SeerrfinApplication.kt`

- [ ] Define app-owned connection results and settings contracts.
- [ ] Call unauthenticated `systemApi.getPublicSystemInfo()` with bounded SDK timeouts and map SDK exceptions.
- [ ] Persist only the normalized URL and wire both repositories in `AppContainer`.

### Task 3: ViewModel state machine

**Files:**
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/feature/connect/ServerConnectionViewModel.kt`
- Test: `app/src/test/kotlin/io/github/justarandombaddev/seerrfintv/feature/connect/ServerConnectionViewModelTest.kt`
- Test: `app/src/test/kotlin/io/github/justarandombaddev/seerrfintv/test/MainDispatcherRule.kt`

- [ ] Write failing tests for initial saved URL, invalid input, loading/success persistence, duplicate submission, and mapped errors.
- [ ] Implement the minimal immutable state and ViewModel events.
- [ ] Run the focused tests and the complete unit-test task to green.

### Task 4: TV screen and application wiring

**Files:**
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/feature/connect/ServerConnectionScreen.kt`
- Modify: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] Build the address field, focused border, connect button, and loading/error/success messages with explicit D-pad focus directions.
- [ ] Obtain the ViewModel through the application container and collect state lifecycle-aware.
- [ ] Add Internet access, the application class, and HTTP support required for common LAN Jellyfin deployments.
- [ ] Run `assembleDebug` and inspect the manifest.

### Task 5: Device and documentation validation

**Files:**
- Modify after evidence: `docs/roadmap.md`
- Modify after evidence: `docs/progress.md`

- [ ] If an authorized TV transport is available, install and launch with an explicit serial.
- [ ] Validate initial focus, address entry, Down/Up, activation, error rendering with a reserved invalid domain, and Back.
- [ ] Check filtered application logs only if a failure occurs.
- [ ] Update roadmap/progress only for behavior actually observed, then run `git diff --check`.
