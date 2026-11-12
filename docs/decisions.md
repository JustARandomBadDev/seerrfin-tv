# Architecture decisions

| Decision | Current direction | Status |
| --- | --- | --- |
| UI toolkit | Kotlin + Compose for TV | Proposed |
| Video player | Media3 / ExoPlayer | Proposed |
| Jellyfin integration | Official Kotlin SDK | Proposed |
| Seerr integration | Documented REST / OpenAPI | Proposed |
| Modules | Begin with a single Android `app` module | Proposed |
| Local configuration | DataStore; database only when useful | Proposed |
| Authentication | User-scoped; never bundle an admin Seerr API key | Required |
| Package name | Choose once before first working build | Pending |
| Dependency versions / minimum API | Verify current docs and TV compatibility | Pending |
| License | Explicit choice required before public release | Pending |

Record each accepted decision with the date, rationale, alternatives and implications. Do not silently change accepted decisions.
