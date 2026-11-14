# Local development setup (Arch Linux)

## Base tools

Use pnpm/pnpx for Node-based tools:

```bash
sudo pacman -S --needed git github-cli nodejs pnpm jdk17-openjdk android-tools
```

Codex is already installed. For a new machine, install it with pnpm rather
than npm:

```bash
pnpm add --global @openai/codex
codex --version
codex login status
```

## JDK used by Gradle

AGP 9.3 requires JDK 17. A shell may resolve another SDKMAN Java even when the
Arch JDK 17 package is installed. Select JDK 17 per terminal without changing
the global Java configuration:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

In Android Studio, set **Gradle JDK** to the same JDK 17. CI must also provision
JDK 17 explicitly. Do not commit an absolute `org.gradle.java.home`, because
that path is machine-specific.

## Android SDK

The P00.3 project will use:

- `compileSdk = 37`
- `targetSdk = 36`
- `minSdk = 26`
- `buildToolsVersion = "36.1.0"`

AGP 9.3 defaults to Build Tools 36.0.0. Version 36.1.0 is already installed and
meets that minimum, so it is pinned to avoid downloading a redundant 36.0.0
package. P00.3 must validate the choice with the first real build.

Verified local host inventory:

- Build Tools 35.0.0, 36.1.0, and 37.0.0
- Platform-Tools 37.0.1
- Command-line Tools 23.0

Platforms 35, 36, and 36.1 are installed. Install platform API 37 before the
first build, using Android Studio SDK Manager. The existing command-line tool
can perform the same targeted installation if needed:

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-37"
```

Do not reinstall working SDK components. The emulator and an Android TV AVD
are optional; the authorized physical Android TV API 34 is the primary test
target.

## Gradle project

Do not install a global Gradle. P00.3 will create and check in Gradle Wrapper
9.5.0 plus a pinned version catalog. Run wrapper commands with JDK 17:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew --version
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew assembleDebug
```

These commands become applicable only after P00.3 creates the project.

## Codex, Superpowers, and Context7

Context7 is installed as a Codex plugin and its MCP lookup has been verified.
Check integrations without printing secret-bearing configuration:

```bash
codex mcp list
codex plugin list --marketplace context7-marketplace
```

For an explicit Context7 CLI fallback, use pnpx:

```bash
pnpx ctx7@latest library "Android Developers" "Compose for TV focus navigation"
pnpx ctx7@latest docs /websites/developer_android "Compose for TV focus navigation"
```

Superpowers is verified only when a skill is discovered and actually invoked,
not merely when its directory exists.

## Physical TV validation

The physical TV is preferred for D-pad, Back, media key, focus, launcher, and
playback validation. Device operations require explicit authorization for that
operation. A disconnected TV does not block P00.2 or P00.3 project creation.

When device testing is explicitly authorized:

```bash
adb devices -l
```

Do not guess addresses, pair devices, install, launch, navigate, or capture
screens without the corresponding authorization.

## Git and CI

The repository uses an HTTPS origin and `git ls-remote origin HEAD` has been
verified. Do not activate Android CI until P00.4 produces a reproducible local
build. `templates/android-ci.yml` remains outside `.github/workflows` until
then.
