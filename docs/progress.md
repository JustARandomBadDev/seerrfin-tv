# Progress

## Current status

**P00.5 complete** — GitHub Actions run `43636555495` completed successfully at
commit `ea8bbe445e65fef4ca291ade6cdb1e4ed0e2e4d9`; unit tests, `assembleDebug`,
and debug APK publication all succeeded.

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

P00 is complete. P01 has not started; its local build, CI, and physical-TV
prerequisites are satisfied.
