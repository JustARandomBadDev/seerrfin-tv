#!/usr/bin/env bash
# Lightweight read-only probe. Does not verify MCP functionality or control the TV.
set -u

printf 'Seerrfin TV — local prerequisites\n\n'
for command_name in git gh codex node npm npx java adb sdkmanager emulator avdmanager; do
  if command -v "$command_name" >/dev/null 2>&1; then
    printf '[FOUND] %-12s %s\n' "$command_name" "$(command -v "$command_name")"
  else
    printf '[MISSING] %s\n' "$command_name"
  fi
done

printf '\nAndroid SDK path (no secrets):\n'
if [ -n "${ANDROID_HOME:-}" ]; then
  printf 'ANDROID_HOME set: yes\n'
else
  printf 'ANDROID_HOME set: no\n'
fi
if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
  printf 'ANDROID_SDK_ROOT set: yes\n'
else
  printf 'ANDROID_SDK_ROOT set: no\n'
fi

printf '\nGit repository:\n'
if git rev-parse --show-toplevel >/dev/null 2>&1; then
  git branch --show-current
  git status --short
else
  printf 'Not currently inside a Git repository\n'
fi

printf '\nADB status:\n'
if command -v adb >/dev/null 2>&1; then
  adb devices -l
else
  printf 'ADB not installed\n'
fi

printf '\nNote: This script does NOT prove Context7/Superpowers are functional.\n'
printf 'Run prompts/00-audit-setup.md inside Codex for a complete audit.\n'
