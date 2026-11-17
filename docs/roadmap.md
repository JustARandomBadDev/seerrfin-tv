# Roadmap

Each task has a concrete completion condition. Do not advance to the next milestone until the previous one is verified.

## P00 — Bootstrap

- [x] P00.1 Audit local prerequisites, Codex, Superpowers, Context7, SDK and ADB access.
- [x] P00.2 Select compatible Kotlin, Gradle, AGP, JDK, SDK, Compose for TV, Media3 and Jellyfin SDK versions. Record in decisions.
- [x] P00.3 Create a minimal native Android TV Gradle project and remote-focusable Compose screen.
- [x] P00.4 Compile with checked-in Gradle wrapper, install on emulator or authorized physical Android TV.
- [x] P00.5 Activate GitHub Actions Android CI after P00.4 succeeds.

**Exit:** reproducible `assembleDebug`, app starts from TV launcher, D-pad navigation works on at least one device.

## P01 — Jellyfin connection

- [x] P01.1 Configure server URL and network error handling.
- [x] P01.2 Implement supported user login and secure local session handling.
- [x] P01.3 Fetch libraries via official SDK and support session restoration.

**Exit:** real test account signs in and lists libraries without committing secrets.

## P02 — Minimal player

- [x] P02.1 Resolve a playable Jellyfin item and obtain an appropriate playback source.
- [x] P02.2 Play/pause/seek with Media3 and TV remote.
- [x] P02.3 Report playback progress, stop and resume to Jellyfin.

**Exit:** one real movie plays and Jellyfin reflects progress correctly.

## P03 — Jellyfin catalog

- [x] P03.1 Home rows, library filtering, loading and error states.
- [x] P03.2 Media detail, episodes and season navigation.
- [x] P03.3 Remote-friendly search, back handling and focus recovery.
- [x] P03.4 Correct Jellyfin DeviceProfile negotiation and validate Direct Play on the authorized TV.

**Exit:** browse to a movie/episode and play using D-pad only.

## P04 — Seerr

- [x] P04.1 Validate supported authentication mode and per-user permissions.
- [ ] P04.2 Generate or implement typed requests from the current Seerr API contract.
- [ ] P04.3 Discovery, search, details and request state.
- [ ] P04.4 Submit media requests with the active user's permissions.

**Exit:** discover and request a test item using a normal Seerr account.

## P05 — Unified catalog

- [ ] P05.1 Cross-provider identity and deduplication.
- [ ] P05.2 Availability and request-state reconciliation.
- [ ] P05.3 Play, Resume, Request or Follow-up action depending on state.

**Exit:** one unified media detail behaves correctly across missing / requested / available states.

## P06 — Advanced playback

- [ ] P06.1 Direct play / remux / transcode capabilities.
- [ ] P06.2 Audio and subtitle track selection.
- [ ] P06.3 Episode progression, playback failures and adaptive behavior.

**Exit:** documented matrix tested across representative formats/devices.

## P07 — TV experience

- [ ] P07.1 Focus navigation polish and accessibility.
- [ ] P07.2 Profiles, settings and offline/error handling.
- [ ] P07.3 Caching and performance improvement based on measurements.

**Exit:** full D-pad walkthrough with no focus dead-ends.

## P08 — Release

- [ ] P08.1 Threat model, license review and dependency checks.
- [ ] P08.2 Instrumented tests / smoke tests and CI checks.
- [ ] P08.3 Signed APK, release notes and installation guide.

**Exit:** independently installable tested release and reproducible build instructions.
