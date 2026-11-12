# Local development setup (Arch Linux)

## Install basic tools

```bash
sudo pacman -S --needed git github-cli nodejs npm jdk17-openjdk android-tools
```

**JDK note:** JDK 17 is only a bootstrap candidate. Verify the Android Gradle Plugin's current Java requirement before choosing the final JDK. If Android Studio uses a bundled JBR, align terminal and CI versions accordingly.

Install Android Studio from https://developer.android.com/studio and use SDK Manager to install compatible SDK platforms, command-line tools, build tools, platform tools and an Android TV emulator image. Do not add a global Gradle dependency; commit Gradle Wrapper after bootstrap.

## Codex

```bash
npm install -g @openai/codex
codex --version
codex login status
codex mcp list
```

Avoid sharing raw `~/.codex/config.toml` or any MCP entry containing secrets.

## Superpowers

In the Codex CLI, open `/plugins`, find **Superpowers** and install it using the official plugin flow. Start a fresh Codex session afterward. Verify by making Codex locate and explicitly invoke a skill, not only by checking that files exist.

Official: https://github.com/obra/superpowers

## Context7

Prefer the official authenticated setup:

```bash
npx ctx7 setup --codex
```

Or install as a Codex plugin:

```bash
codex plugin marketplace add upstash/context7
codex plugin add context7@context7-marketplace
```

Use **one** primary installation path to avoid duplicated configuration. Authentication can require completing an OAuth flow in a browser. Restart Codex after installation. Verify that a real documentation lookup succeeds, not just that `context7` is listed.

Official: https://github.com/upstash/context7/blob/master/docs/clients/codex.mdx

## Connect the TV

On the TV, enable developer options and ADB debugging. For modern Android versions supporting Wi-Fi pairing, use the pairing IP/port and code shown by the TV:

```bash
adb pair TV_IP:PAIRING_PORT
adb connect TV_IP:DEBUG_PORT
adb devices -l
```

Older TV devices may use legacy `adb connect TV_IP:5555` with a prompt to authorize debugging. The debug port isn't universally 5555; use the value shown by the device. Keep TV and computer on an accessible local network. Never enable ADB exposure to the public internet.

To inspect device capabilities without touching the TV UI:

```bash
adb -s DEVICE_SERIAL shell getprop ro.build.characteristics
adb -s DEVICE_SERIAL shell getprop ro.product.model
adb -s DEVICE_SERIAL shell getprop ro.build.version.sdk
```

If your device has a different pairing procedure, follow its vendor documentation.

## Run the audit

Launch `codex` in the repository, then paste `prompts/00-audit-setup.md`. This first pass should be **read-only** and should produce a structured report, not silently modify the computer.

## Enable Android CI later

`templates/android-ci.yml` is deliberately outside `.github/workflows` because the initial kit contains **no `gradlew` and no compilable Android app**. When P00.4 passes, copy it to `.github/workflows/android-ci.yml`, align Java/SDK versions with the project, then validate the GitHub Actions workflow.
