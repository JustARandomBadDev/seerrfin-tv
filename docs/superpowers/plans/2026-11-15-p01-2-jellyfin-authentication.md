# P01.2 Jellyfin Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Authenticate a Jellyfin user from an Android TV remote and persist the resulting session as Android-Keystore-encrypted ciphertext.

**Architecture:** Extend the existing Jellyfin repository with an app-owned authentication result, then persist its minimal session behind a `JellyfinSessionStore`. A dedicated login ViewModel and Compose for TV screen follow the current StateFlow and explicit-focus patterns; `AppContainer` wires the SDK adapter and encrypted store.

**Tech Stack:** Kotlin 2.4.21, Compose for TV 1.1.0, coroutines 1.11.0, Jellyfin Kotlin SDK 1.9.4, Android Keystore AES-GCM.

**Spec:** `docs/superpowers/specs/2026-11-15-p01-2-jellyfin-authentication-design.md`

## Global Constraints

- Work only on P01.2; do not restore sessions, fetch libraries, or start P01.3.
- Preserve P01.1 server validation and persistence.
- Do not log or hard-code server URLs, usernames, passwords, access tokens, or user data.
- Use username/password authentication exposed by Jellyfin Kotlin SDK 1.9.4.
- Persist only Android-Keystore-encrypted session ciphertext.
- Do not commit or push.

## Review Focus

- HTTP 401 maps to invalid credentials while other HTTP failures remain server errors.
- Blank passwords remain supported, but blank usernames never contact Jellyfin.
- A successful network login is not reported successful if secure persistence fails.
- Duplicate submit events make only one request and never expose the password in errors.
- Corrupt or undecryptable stored data is cleared and never returned as a session.

---

### Task 1: Repository authentication contract

**Files:**
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/domain/model/JellyfinSession.kt`
- Modify: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/domain/repository/JellyfinRepository.kt`
- Modify: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/data/jellyfin/SdkJellyfinRepository.kt`
- Modify: `app/src/test/kotlin/io/github/justarandombaddev/seerrfintv/data/jellyfin/SdkJellyfinRepositoryTest.kt`

**Interfaces:**
- Produces: `suspend fun JellyfinRepository.authenticate(serverUrl: String, username: String, password: String): JellyfinAuthenticationResult`.
- Produces: `JellyfinAuthenticationResult.Authenticated(session: JellyfinSession, displayName: String)` and app-owned failure variants.

- [ ] Write repository tests for valid SDK data, incomplete SDK data, 401, transport/TLS/server/content/client errors, and cancellation.
- [ ] Run the focused test and verify failure because authentication does not exist.
- [ ] Implement the minimal SDK adapter using `authenticationApi.authenticateUserByName` and bounded timeouts.
- [ ] Run the focused repository tests and verify they pass.

### Task 2: Encrypted session store

**Files:**
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/domain/session/JellyfinSessionStore.kt`
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/data/session/EncryptedJellyfinSessionStore.kt`
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/data/session/AndroidKeystoreSessionCipher.kt`
- Create: `app/src/test/kotlin/io/github/justarandombaddev/seerrfintv/data/session/EncryptedJellyfinSessionStoreTest.kt`

**Interfaces:**
- Consumes: `JellyfinSession` from Task 1.
- Produces: `JellyfinSessionStore.save/load/clear` and injectable ciphertext storage/cipher boundaries.

- [ ] Write tests proving round-trip, no plaintext persistence, replacement, clear, malformed data cleanup, and decryption failure cleanup.
- [ ] Run the focused test and verify failure because the store does not exist.
- [ ] Implement versioned serialization, app-private storage, and Android Keystore AES-GCM with a fresh IV and fixed AAD.
- [ ] Run the focused store tests and verify they pass.

### Task 3: Login state and TV UI

**Files:**
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/feature/login/JellyfinLoginViewModel.kt`
- Create: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/feature/login/JellyfinLoginScreen.kt`
- Create: `app/src/test/kotlin/io/github/justarandombaddev/seerrfintv/feature/login/JellyfinLoginViewModelTest.kt`
- Create: `app/src/test/kotlin/io/github/justarandombaddev/seerrfintv/feature/login/JellyfinLoginScreenTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `JellyfinRepository.authenticate` and `JellyfinSessionStore.save`.
- Produces: immutable `JellyfinLoginUiState`, explicit field events, `login()`, and a remote-usable screen.

- [ ] Write ViewModel tests for validation, blank password, loading/duplicate submit, all result mappings, secure-store failure, and password clearing on success.
- [ ] Write key-routing tests for username → password → button and Center/Enter keyboard behavior.
- [ ] Run focused tests and verify failure because login UI/state does not exist.
- [ ] Implement the minimal ViewModel and masked three-stop TV form with explicit focus.
- [ ] Run focused login tests and verify they pass.

### Task 4: App integration and validation

**Files:**
- Modify: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/AppContainer.kt`
- Modify: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/MainActivity.kt`
- Modify: `app/src/main/kotlin/io/github/justarandombaddev/seerrfintv/feature/connect/ServerConnectionViewModel.kt`
- Modify: `app/src/test/kotlin/io/github/justarandombaddev/seerrfintv/feature/connect/ServerConnectionViewModelTest.kt`
- Modify after validation: `docs/roadmap.md`
- Modify after validation: `docs/progress.md`

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: server-connect → login routing, Back to server editing, and a P01.2 signed-in state without P01.3 behavior.

- [ ] Write a focused state test for returning from login to server editing and verify it fails first.
- [ ] Wire the encrypted store and login screen through `AppContainer` and `MainActivity`.
- [ ] Run targeted tests, then `:app:testDebugUnitTest`, then `:app:assembleDebug` with JDK 17.
- [ ] If an authorized TV transport is available, install and exercise focus, Back, masked entry, invalid-login handling, and successful persistence when credentials are entered by the user.
- [ ] Inspect the final diff for secret/log leakage and P01.3 scope creep.
- [ ] Only after validation, mark P01.2 complete in `docs/roadmap.md` and update `docs/progress.md` with observed evidence and blockers.

