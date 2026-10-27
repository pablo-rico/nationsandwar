# Nations & War: technical guide

A Towny-style countries-and-war mod for **NeoForge 1.21.1** (Java 21, NeoForge 21.1.221, NeoGradle userdev 7.0.x).
- Package `org.papiricoh.nationsandwar`, mod id `nationsandwar`.
- No required dependencies.
- **JourneyMap** and **Xaero's World Map / Minimap** are optional, and only `compat/` imports their APIs.

Code, comments and identifiers are written **in English**. The game ships in **English and Spanish**. Talk to the user in Spanish.

## Working rules

- **Do not commit** unless the user explicitly asks.
- Ask the user about design decisions for new content (they like being asked, in rounds of questions).
- The user watches token spend closely:
  - **At most one agent in parallel** besides yourself.
  - Brief agents with `docs/CONTRACT.md` instead of having them re-read the server.
  - Cap their preview rounds.
- Every change must leave these green:
  - `./gradlew build` (compile + 53 JUnit tests).
  - `./gradlew runGameTestServer` (13 GameTests).
  - Run only one Gradle at a time.
- Look at the result in the game (preview harness, below) before saying something "looks right".

## Map of the code

| package | what it holds |
|---|---|
| `model/` | **Pure** rules and data: `Politics` (every town/province/nation/alliance, claim index, all political actions → `Result`), `Town`, `Province`, `Nation`, `Alliance`, `RoleTree`/`Role`/`Perm`, `ChunkPerms`, `Flag`, `TownLevel`, `GovernmentType` (names only), `Rules` (numbers from the config). NBT in each class. |
| `data/NWSavedData` | Everything persistent (overworld): politics, wars + `RestoreLog`, war cooldowns, villages already reviewed. |
| `core/` | MC-side glue: `Actions` (**single dispatcher** for commands and the GUI: `run(player, "town.claim", nbt)`), `Sync` (claims full/diff, briefs, per-player view, membership; batched per tick), `ViewBuilder`, `NW` (shortcuts, `NW.changed(server)` = save + sync), `Emeralds`, `Notices`. |
| `claim/ProtectionEvents` | Protection by group/action and flags, pistons, explosions, war zone (logs to `RestoreLog`, no drops). Fire is in `mixin/FireBlockMixin`. |
| `war/` | `War`, `Siege`, `Treaty`, `RestoreLog` (data); `WarRules`, `SiegeControl`, `WarPlanner` (NPC campaigns: target town, next chunk toward the Core, liberation first, fronts, parties, reserve), `WarSim` (unwatched NPC sieges) (pure); `WarService` (declaring, ticks, banners, Core, ending, treaty); the Town Core and Siege Banner blocks. |
| `npc/` | `NpcService` (village adoption, guards/envoys, brain, war campaigns every `npc.planTicks`, garrisons, rallies, envoy diplomacy), `NpcCourt` (named characters, `model/Persona`, holding NPC roles; succession), `FactionBrain` (pure), `NameGen`, `GuardEntity`, `EnvoyEntity`. |
| `command/NWCommands` | `/town /province /nation /alliance /war /nw /nwadmin`; they only build NBT for `Actions`. |
| `network/` | Payloads (`ActionPayload` C2S; `ClaimsPayload`, `BriefsPayload`, `SiegesPayload`, `SelfPayload`, `ViewPayload`, `TreePayload` (answers `inspect`), `NoticePayload`, `OpenScreenPayload` S2C). `NWNetwork` references client classes **only inside lambdas**. |
| `view/` | Records shared server↔client (`Briefs`, `SiegeBrief`, `PoliticsView`, `TreeView`) with their NBT. |
| `client/` | Mirrors `ClientClaims` / `ClientPolitics` (also the Atlas's on-demand `TreeView`); `screen/` (management GUI with tabs, `modal/` windows for every tool, `tab/AtlasTab` + `tab/RoleTree` 2.5D tree), `gui/` (`Draw`, `Ui`, `Skins`), `map/MapLabels` (Paradox-style names), `notice/` (cards at top centre, `TerritoryWatcher`, `SiegePanel`), `render/` (guard/envoy + tinted `TabardLayer`), `dev/` (preview harness). |
| `compat/` | `MapLayer` (nations/provinces/towns/alliances, client config `map.layer` + key), `MapStyle` (groups per layer, colours, states, borders), `Territories` (pure: regions and label placement), `journeymap/NWJourneyMapPlugin` (`PolygonOverlay`s), `xaero/` (highlighters injected by `mixin/xaero/*`; names via `WorldMapLabelsMixin`). |
| `api/` | **The API for other mods (v0.2, see `docs/API.md`)**: `NationsAndWarApi` (reads, managed nations/towns), `ManagedNations` + `ManagedNationProvider`, `WarApi` (declare, score, `townFallen`, banners, combatant resolvers), `ProtectionBypass` (zones for boss arenas), `ClaimVetoes`, `event/` (NeoForge events), `client/ManagedNationScreens`. `core/ApiEvents` queues the events (from `model/PoliticsObserver`) and flushes them at the end of the tick. |

## How to add things

- **A new political action**:
  1. Write the rule in `Politics` (or `WarService`/`NpcService`) returning a `Result`.
  2. Add the `case` to `core/Actions.dispatch`.
  3. Add the command in `NWCommands`.
  4. Add the row to `docs/CONTRACT.md`.
  5. Add `nationsandwar.msg.<key>` in EN+ES.
  - Permissions are always checked on the server.
- **Anything that changes politics** calls `NW.changed(server)`. Without it, clients and maps are not refreshed.
- **A new mutation in `Politics`** reports itself to `observer` (`PoliticsObserver`) after the change; that is how the API's events reach other mods. A new war step queues its `WarEvent` with `ApiEvents.queue`.
- **Managed nations** (`Nation.managedBy != null`, run by another mod): every rule that lets players join, ally, edit or declare must refuse them (msg `managed`); NPC logic skips them; `Nation.alwaysDefended()` replaces `npc` where "has no citizens" matters. SupernaturalCraft is the first user (the Pit, the Host, SucroCorp).
- **API changes**: keep `docs/API.md` in step and bump `NationsAndWarApi.VERSION` when callers must adapt.
- **Text**: every key lives in `tools/lang/genlang.py` (EN, ES). Run `python3 tools/lang/genlang.py`. **Never** edit the lang JSON files by hand.
  - Role names starting with `@` are lang keys; renamed roles keep the literal text.
- **Art**: pure-Python generators (`tools/artgen/`: `nwart.py` for entities/blocks/items, `guiart.py`, `noticeart.py`, `logo.py` for `branding/logo*.png` and the in-jar `nationsandwar_logo.png`). Never download assets.
- **Branding**: `README.md` (developers) and `branding/modrinth.md` (the Modrinth page). Keep both in step with new features and the commands' real names (`/town new`, `/nation new <gov> <name>`).
- **Server config**: `NWConfig` (reads guarded by `SPEC.isLoaded()`).
  - If you change defaults, delete `runs/*/config/nationsandwar-*.toml`; the old values stay and break tests.
  - The client has `NWClientConfig` (map, notices: `topCenter`, `size` 0.75 by default, `topOffset`…).

## Tests

- **JUnit** (`src/test`): only pure logic (`model`, `war` rules incl. `WarPlanner`/`WarSim`, `FactionBrain`, `NpcCourt`, `compat/Territories`). `CompoundTag` and `ChunkPos` are fine; registries are not.
- **GameTests** (`gametest/NWGameTests`, template `gametest/empty_48x24x48`), one `batch` per test. Known traps:
  - Structures can come **rotated**: use **absolute** positions (`sameChunk`), never `relativePos` round trips.
  - `FakePlayer`s are not "online" (`NW.online` = false) and do not show up in `getEntitiesOfClass`. Sieges are pushed with a `GuardEntity` (`setNoAi`).
  - Test towns are called "Gt …" and `purge` deletes them at the start. A failure no longer chains into `name_taken`.
  - If a test changes config with `.set()`, it restores it at the end.

## Seeing it in the game

`NW_PREVIEW=<scenes> ./gradlew runClient -Ppreview [-Pjm] [-Pxaero]`
- Opens `runs/client/saves/nw_preview`. If it is missing, copy `runs/gameTestServer/world`.
- Seeds sample data once (`client/dev/PreviewSample`).
- Saves `runs/client/screenshots/nw_<scene>.png` and quits.
- Scenes: `gui:<tab>` (overview, town, provinces, nation, atlas, roles/nationroles = the Atlas on my town/nation, diplomacy, war, map; `gui:<tab>.modal` or `.<n>` opens the n-th modal of the tab's action bar), `layer:<layer>` (map tab on nations/provinces/towns/alliances), `jm:<layer>` (`-Pjm`, JourneyMap full screen; its first launch shows a splash, so put two `jm:` scenes), `xaero:<layer>` (`-Pxaero`), `envoy`, `nameplate` (the envoy's name tag), `treaty` (ends the war, run it last), `notices`, `siegehud`, `showcase`, `world`.
- The system `screencapture` does not work: use this.
- `-Pjm` / `-Pxaero` load JourneyMap / Xaero's in the dev client.

## NPC traps

- Role holders, `Nation.leader()` and `Town.mayor()` may be a court character (`Politics.persona(id)` != null), never a player. Never look up a Mojang profile for one.
- A banner with no besieger near slips toward the holders at `controlDecay` and the siege fails at −1 (`SiegeControl`): an
  abandoned banner never stays forever. `siegeTakesChunkCoreTownAndRestores` has failed once at control 0.49 and passed on
  re-runs (flaky).
- NPC sieges in unloaded chunks start without a block (`Siege.placed` false). They run on `WarSim` only while every nation in the war is NPC or managed; a war with players pauses them. The banner is placed when the chunk goes live.

## Compatibility traps

- JourneyMap uses versions like `1.21.1-6.0.9`: in `neoforge.mods.toml` its range must be `"*"`; a range like `[6,)` breaks loading.
- JourneyMap's `UIState.displayBounds` are not reliable.
  - The notices go at the top centre, below the boss bars. They drop below vanilla toasts if they would reach them.
  - With `topCenter=false`, `topOffset` / auto-reserve avoids JM's minimap.
- Xaero publishes no API. `WorldMapClaims`/`MinimapClaims` register through a mixin on `HighlighterRegistry.end()` (config `nationsandwar.xaero.mixins.json` + `XaeroMixinPlugin`).
  - The Minimap one only applies without the World Map installed.
  - Xaero colours are BGRA.
- Dedicated server: it must never load `client/` classes (check with `./gradlew runServer`).

## Versions and releases

- The version is `mod_version` in `gradle.properties`.
- `.github/workflows/release.yml` runs on every push to `master`/`main`. If tag `v<mod_version>` does not exist yet, it:
  1. builds and runs JUnit;
  2. runs the GameTests;
  3. creates the tag and the GitHub release with `build/libs/nationsandwar_<mc>_<version>.jar` and auto-generated notes.
- To publish, bump `mod_version` and push. If the version did not change, the workflow does nothing.
- It can also be launched by hand (`workflow_dispatch`).
