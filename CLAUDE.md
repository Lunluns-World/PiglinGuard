# PiglinGuard

Paper plugin. When a zombified piglin goes through a nether portal from the Overworld to the Nether, it removes the piglin if it came from a portal-based gold farm.

Why: the server's public gold farm, built by players, keeps pushing zombified piglins through its portals into the Nether. Nothing kills them there, so they pile up by the thousands. One spark profile showed 11,662 zombified piglins and 2,940 items on the Nether side, and mob ticking took about 8.7% of main-thread time. Those piglins add nothing to the farm's output, so removing them costs players nothing.

Hard rule: the plugin must not change anything for ordinary players. Outside the cases it handles, the game stays fully vanilla.

## Tech stack

- Language: **Kotlin**, built with Gradle Kotlin DSL (`build.gradle.kts`)
- API: `io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT`
  - Must also run on 26.2. Don't use NMS or reflection; use the Paper API only.
- Java toolchain 21. The production server runs Java 25; the bytecode just has to run on 21+.
- Kotlin stdlib:
  - Don't shade it. Declare it in `plugin.yml` under `libraries:` and let Paper download it.
  - If `paper-plugin.yml` is used instead, use the matching loader mechanism.
- Folia support:
  - Put `folia-supported: true` in `plugin.yml`.
  - Don't use `Bukkit.getScheduler()`. Use `RegionScheduler` / `EntityScheduler` / `AsyncScheduler` / `GlobalRegionScheduler`.
- No external runtime dependencies. If a build plugin is needed (for example run-paper for testing), add it to the build only, never to the jar.

## Thread rules (important)

- Read or write a world block or entity only on the thread that owns it.
  - `EntityPortalEvent` already runs on the owning thread of the entity's location, so `entity.remove()` can be called directly there.
- Portal-block counts for neighbouring chunks must not be read synchronously inside the event. On Folia a neighbouring chunk may belong to another region.
  - Take a `ChunkSnapshot` on the owning thread during `ChunkLoadEvent` or when a portal changes, count on an async thread, and write the result into a `ConcurrentHashMap`.
  - The event only reads the cache. A missing entry counts as 0, and a computation for it is scheduled on that chunk's region.
- Mutable shared state may only use thread-safe structures:
  - cache: `ConcurrentHashMap`;
  - area list: immutable snapshot plus `@Volatile` reference swap;
  - statistics: `LongAdder`.

## Code style

- Keep the decision logic as a pure function so it can be unit tested. Take the needed facts (type, name, leash, vehicle/passengers, from/to dimension, spawn reason, whitelist/blacklist hit, portal density) and return a `Decision` (`IGNORE_*` / `REMOVE_*` plus the reason).
- Unit tests: JUnit 5, covering every branch of the decision function and the area-containment checks.
- Configuration:
  - `config.yml` holds the settings, `areas.yml` holds the whitelist and blacklist.
  - Missing settings are filled with defaults; invalid values log a warning and fall back to the default instead of failing.
- Player messages: Adventure / MiniMessage, Traditional Chinese, and the server's cute kaomoji style. Example: `<green>(๑•̀ㅂ•́)و✧ 已新增白名單 <gold>{name}`.
- Don't print anything per tick or per event unless `log-removals: true`.

## Git

- Commit author and committer must both be `TW527E <github@tw527e.zip>`.
  - Set it per repo: `git config user.name "TW527E"` and `git config user.email "github@tw527e.zip"`.
- Never put any other email or personal data in commits, code, or config.
- Commit only when asked. Write commit messages in English, imperative mood.

## Verification

- `./gradlew build` must pass, tests included.
- Before calling it done:
  - run the server once with run-paper (1.21.8);
  - confirm the plugin loads without errors;
  - confirm every `/piglinguard` subcommand works.
- Explain in plain language any behaviour change that could affect ordinary players.
