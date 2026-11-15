# P01.2 Jellyfin Authentication Design

## Intent

Allow a Jellyfin user to sign in with the server-supported username/password
flow from a TV remote, then retain the returned session without storing any
credential or token in plaintext. This completes P01.2 while leaving session
restoration and library loading to P01.3.

## Goals

- Authenticate through Jellyfin Kotlin SDK 1.9.4
  `authenticationApi.authenticateUserByName`.
- Expose app-owned authentication results and actionable UI errors.
- Provide a three-stop D-pad flow: username, password, sign-in action.
- Clear the password from UI state after successful authentication.
- Persist the access token and session identity as Android-Keystore-encrypted
  ciphertext, with no credentials, URLs, or tokens in logs.
- Preserve the P01.1 server connection flow and manual AppContainer wiring.

## Non-goals

- Restoring an authenticated SDK client after process restart (P01.3).
- Fetching Jellyfin libraries (P01.3), Quick Connect, logout UI, profiles,
  Seerr authentication, or navigation-library adoption.

## Constraints

- One Android app module, MVVM/Repository/AppContainer, coroutines and Flow.
- Jellyfin SDK remains pinned to 1.9.4; no alternate HTTP authentication.
- Passwords are never persisted. Session material is never placed in plain
  DataStore or SharedPreferences values.
- The existing `android:allowBackup="false"` setting remains in force.

## Approaches

1. **Direct Android Keystore AES-GCM plus private SharedPreferences
   ciphertext (selected).** This uses platform primitives already available at
   minSdk 26, avoids an extra dependency, and makes the encrypted boundary
   injectable for deterministic JVM tests.
2. **AndroidX Security encrypted preferences.** Rejected because it adds a
   dependency for a small record and its high-level crypto APIs are deprecated.
3. **Keep the token only in memory.** Rejected because it does not meet secure
   local session persistence and would leave no session for P01.3 restoration.

## Design

`JellyfinRepository.authenticate(serverUrl, username, password)` owns SDK DTO
handling and maps 401, transport, TLS, server, malformed-response, and unknown
failures into app-owned results. On success it returns a minimal
`JellyfinSession` containing access token, user id, and server id plus a display
name for the UI.

`JellyfinSessionStore` hides persistence. `EncryptedJellyfinSessionStore`
serializes the minimal session, encrypts it with AES/GCM/NoPadding using a
non-exportable Android Keystore key, and stores only versioned Base64 IV and
ciphertext in an app-private preference file. Malformed or undecryptable data
is removed and treated as no session; passwords never cross this boundary.

`JellyfinLoginViewModel` exposes one immutable state flow, validates a nonblank
username while allowing Jellyfin's supported blank-password accounts, prevents
duplicate submissions, persists before reporting success, and maps persistence
failure separately. `JellyfinLoginScreen` gives explicit initial and directional
focus across username, masked password, and sign-in button. Back returns to the
existing server screen. Successful authentication shows a P01.2 completion
state only; it does not fetch libraries or restore a session.

