# Codex prompt — Read-only setup audit (P00.1)

You are auditing the development environment for **Seerrfin TV**, a native Kotlin Android TV application integrating Jellyfin and Seerr. Do not implement app features in this task.

## Operating rules

- This is a **read-only audit**. Do not install packages or plugins, change configuration, pair or control a TV, modify files, commit, push, publish, or run destructive operations.
- You may run non-destructive shell commands and read relevant configuration, but NEVER print, copy, log or transmit credentials, token values, passwords, API keys, private server URLs or full secret-bearing config files.
- Summarize or redact sensitive output. Do not include full `~/.codex/config.toml` or secret-bearing `codex mcp list --json` responses in the report.
- Do not claim that a component works merely because its executable or configuration file exists.
- Verify each integration with a harmless functional probe when possible.
- If a tool or physical device requires interactive user authorization, mark NEEDS_MANUAL; do not bypass authorization or fake a result.
- Avoid broad repo scans, large command logs, installs, lengthy tests or unrelated documentation lookups. Choose the smallest checks that produce evidence.
- Do not assume an Android project exists yet. Mark build/test NOT_APPLICABLE if no Gradle wrapper or `app` module has been created.
- Stop after the report. Do not automatically fix the detected issues.

## 1. Repository and GitHub

Check working directory, Git root, current branch, status and remote safely. Check `git`, `gh`, `gh auth status` and whether this is the intended repository. Do not create a GitHub repository or modify its settings. Do not reveal credentials.

## 2. Codex

Check `codex` availability and version, `codex login status`, available plugin commands and any relevant skill/plugin availability. Verify that this session can execute permitted read-only commands.

## 3. Superpowers

Check the current official Codex installation mechanism and whether Superpowers is installed and loaded **in the current Codex session**. Attempt to discover `using-superpowers` and `writing-plans` (or their official current equivalents) and demonstrate a harmless skill invocation, such as asking the skill how it would structure an audit; do not run a full planning session or create files. If only a folder exists but Codex cannot actually invoke its skills, this is NOT PASS. If the CLI doesn't expose skill introspection, state precisely what you did and what remains unverified. Do not reinstall it during the audit.

## 4. Context7

Check whether Context7 is available as an MCP server or via the official Codex plugin / CLI + skills integration. Use `codex mcp list` or the live `/mcp` tools where appropriate, but presence alone is insufficient. Execute a real, brief documentation lookup via Context7 for Jetpack Compose for TV (`androidx.tv:tv-material`) or AndroidX Media3. Record the library/documentation queried and whether the response contained relevant documentation. If the connected environment does not expose Context7 to this session, mark FAIL or NEEDS_MANUAL and explain; do NOT claim PASS from config alone. Do not expose API keys.

## 5. Local Android toolchain

Check versions and paths for Java/JDK, Node/npm/npx, Android Studio (if detectable), `ANDROID_HOME` / `ANDROID_SDK_ROOT`, Android SDK command-line tools, `adb`, `sdkmanager`, installed platforms/build-tools, `emulator`, `avdmanager`, available Android TV AVDs, Git and Gradle wrapper. Verify versions are present and report compatibility *unknown* until AGP/Gradle versions are selected. Do not download or install components.

## 6. Real Android TV access (ADB)

Use `adb devices -l` to determine whether any devices are connected and authorized. If none, offline or unauthorized, mark FAIL or NEEDS_MANUAL. Do not try to guess TV IPs, pair with unknown devices, enable debugging, alter device settings or send input events.

For an already authorized device, use harmless read-only checks (`adb -s SERIAL shell getprop ro.build.characteristics`, `ro.product.model`, `ro.build.version.sdk`, `wm size`) and determine whether it is plausibly an Android TV / Google TV device. If multiple devices exist, do not pick one blindly. Do not print full serials to the report. Distinguish **ADB shell access PASS** from **app deployment / D-pad / playback NOT TESTED**. Do not install APKs, press remote keys or capture the screen in this audit.

## 7. Jellyfin and Seerr

Check only whether integration prerequisites have been documented; do not contact private servers without explicit permission. Mark network login/API tests NOT_TESTED. Never ask for or paste privileged API keys into the repository.

## 8. Build readiness

If an actual Android Gradle project exists, run a bounded offline-safe Gradle diagnostics/build command only when it will not silently install SDK components or modify system configuration; report exact command/result. If no Android project exists, do not create one, and mark build NOT_APPLICABLE.

## Report format (French)

Start with a compact Markdown table:

| Component | Status | Evidence | Action required |
| --- | --- | --- | --- |

Include at least: GitHub/Git, Codex auth, Superpowers functional invocation, Context7 functional lookup, JDK/Node, Android SDK/build tools, Android TV emulator, ADB physical TV authorization, TV identity, Jellyfin and Seerr test access, Android Gradle build.

Statuses: PASS / FAIL / NEEDS_MANUAL / NOT_TESTED / NOT_APPLICABLE. Use PASS **only** for successful observed checks.

Then include:
1. **Blockers** sorted by criticality.
2. **Exact minimal commands** I need to run manually (if any), including how to connect my TV when applicable; don't invent an IP address or pairing port.
3. **Next permitted action:** what to fix and whether we can proceed to P00.2.
4. One-line confirmation: **No files or settings were changed** (or disclose any unexpected side effect explicitly).

Do not expand into an implementation roadmap or start coding. The purpose is to determine whether Codex, Superpowers, Context7 and an Android TV development environment are *actually operational*.
