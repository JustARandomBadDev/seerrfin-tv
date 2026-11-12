# Codex working agreement

## Project

Seerrfin TV: a native Android TV application that combines Jellyfin playback and Seerr discovery / media requests.

## Constraints

- Kotlin + Jetpack Compose for TV. D-pad / remote support is mandatory.
- Official Jellyfin Kotlin SDK and documented Seerr API. Do not invent endpoints.
- Media3 for video playback. Prefer direct play when supported; handle unsupported codecs without unsupported assumptions.
- Prefer one Android Gradle module initially; split only with a demonstrated need.
- Prefer ViewModels, repositories, coroutines and Flow. Keep interfaces testable.
- No extra backend unless a written architecture decision justifies it.
- Jellyfin is required; Seerr should be optional after the initial MVP.
- Keep dependency versions compatible and pinned in a version catalog once configured.

## Security

- Never add service tokens, credentials, local IPs, personal library data or keys to Git.
- Never include an administrator Seerr API key in the Android app.
- Do not leak tokens into logs, errors, tests or screenshots.
- User actions must respect Seerr authorization. Do not bypass server checks.
- Do not copy code from third-party clients without reviewing licenses and attribution.

## Execution

- Read `docs/progress.md` and the relevant task; avoid opening the whole repository unnecessarily.
- Read `docs/architecture.md` for architectural changes; inspect real code before changing it.
- Work on exactly one small, testable roadmap task at a time.
- Use Superpowers for design, complex debugging and substantial features, not trivial edits.
- Use Context7 when version-specific library documentation is needed; avoid redundant queries.
- Prefer precise edits; avoid sweeping refactors, speculative abstractions or new dependencies.
- Add or update focused tests for behavior changes. Use a basic build check and targeted tests during development; broader checks at milestone boundaries.
- Test D-pad focus behavior, back navigation and remote control usability for UI changes.
- Treat connected physical devices as user-owned: do not install, launch, remove apps, navigate, capture screens or alter settings without explicit authorization for that operation.
- Never claim to have built, tested or connected to a TV unless the command succeeded.
- Never create commits, push, publish or change GitHub settings unless explicitly requested.
- Keep the final report brief: changes, verification performed, blockers, next task.
- Update `docs/progress.md` only after observing the result.

## Workflow

1. Identify the task and its acceptance criteria.
2. Inspect only relevant sources and tool documentation.
3. Implement with focused tests.
4. Run available targeted verification and capture concise results.
5. Update progress and stop. Do not start unrelated roadmap tasks.

## Git conventions

- `feat: description`, `fix: description`, `refactor: description`, `docs: description`, `test: description`, `chore: description`.
- Do not use scope parentheses unless instructed otherwise.
