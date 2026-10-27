# Nations & War — client contract (for the GUI passes)

NeoForge 1.21.1, Java 21, package `org.papiricoh.nationsandwar`. The server side is done and tested; GUI work lives in
`client/` (and `assets/`). Do not change `model/`, `core/`, `war/`, `npc/`, `claim/` or the payload formats unless
something is truly missing — if so, add it minimally and say so in your report.

## Data on the client

### `client.ClientPolitics` — the management GUI's data
- `view()` → `view.PoliticsView` (null until the first answer). Read the record docs in `view/PoliticsView.java`.
- `requestView()` when a screen opens; `closeView()` in `Screen#removed`. While open, the server pushes a new view
  after every change; `listen(Consumer<PoliticsView>)` / `unlisten` to hear about it.
- `act(String action, CompoundTag args)` sends an action. The server replies in chat (green ok / red error) and
  sends a fresh view. Permission checks are all server-side; use `myPerms` masks to grey out buttons.

### `client.ClientClaims` — the public map (every player has it)
- `townAt(dim, chunkX, chunkZ)`, `claims(dim)` (chunk key → town id), `town(id)`, `nation(id)`, `province(id)`,
  `alliance(id)`, `towns()`, `nations()`, `wars()`, `sieges()`, `siegeAt(dim, chunk)`, `occupied(town, chunk)`,
  `enemies(a, b)`, `allied(a, b)`, `atWar(nation)`, `myTown()`, `myNation()`, `mapColor(townBrief)`.
- `listen(Consumer<Change>)` — `CLAIMS`, `BRIEFS`, `SIEGES` (every second during sieges), `SELF`.
- Records: `view.Briefs.TownBrief/ProvinceBrief/NationBrief/AllianceBrief/WarBrief`, `view.SiegeBrief`.
- `dim` strings are dimension ids ("minecraft:overworld"); chunk keys are `ChunkPos.asLong(x, z)`.
- `compat.MapStyle`: the shared colours, states and labels for drawing claims.

### Conventions
- Role names starting with `@` are lang keys: show `Component.translatable(name.substring(1))`.
- `perms` masks: bit `1 << Perm.ordinal()` (`model.Perm`: CLAIM, UNCLAIM, INVITE, KICK, MANAGE_ROLES, MANAGE_FLAGS,
  MANAGE_PLOTS, BANK_DEPOSIT, BANK_WITHDRAW, SET_SPAWN, DIPLOMACY, DECLARE_WAR, SIGN_TREATY, MANAGE_PROVINCES,
  MANAGE_TOWNS). `Perm.NATION_ONLY` lists the ones that only make sense on a nation's tree. Lang: `Perm#langKey()`.
- Flag masks: bit per `model.Flag` ordinal (PVP, MOBS, FIRE, EXPLOSIONS). Lang: `Flag#langKey()`.
- Chunk perms: `new model.ChunkPerms(bits).allows(Group, Action)`; groups RESIDENT/NATION/ALLY/OUTSIDER, actions
  BUILD/DESTROY/SWITCH/CONTAINER/ITEM_USE.
- Governments: `model.GovernmentType` ids kingdom/republic/empire/federation/theocracy/tribe;
  keys `nationsandwar.gov.<id>` (noun) and `.leader/.second/.marshal/.treasurer/.governor`.
- Town levels: `nationsandwar.level.<hamlet|village|city|metropolis>`.
- Emeralds are the currency ("◆" in chat). Prices are in `view().prices()`.

## Actions (`ClientPolitics.act(name, args)`)
Ids are UUID **strings**; `player` is a UUID string or a name. `x`/`z` are chunk coords (default: the player's chunk).

| action | args | perm |
|---|---|---|
| `town.found` | name | — (pays `prices.town` from inventory; core at feet) |
| `town.claim` | x, z, outpost(bool) | CLAIM |
| `town.unclaim` | x, z | UNCLAIM |
| `town.invite` / `town.kick` / `town.mayor` | player | INVITE / KICK / leader |
| `town.join` | town | invited or open |
| `town.leave`, `town.delete`, `town.setspawn`, `town.spawn` | — | — / leader / SET_SPAWN / resident |
| `town.deposit` / `town.withdraw` | amount | resident / BANK_WITHDRAW |
| `town.flag` | flag ("PVP"…), value (byte 1 on, 0 off, -1 clear chunk override), optional x,z (that chunk) | MANAGE_FLAGS |
| `town.perm` | group, perm (action name), value(bool), optional x,z | MANAGE_PLOTS |
| `town.reset_chunk` | x, z | MANAGE_PLOTS |
| `town.rename` / `town.color` / `town.open` | name / color(int) / value(bool) | leader / leader / INVITE |
| `role.add` | scope ("town"/"nation"), name, color, parent | MANAGE_ROLES above parent (leader: any) |
| `role.rename` / `role.color` | scope, id, name / color | manage that role |
| `role.move` | scope, id, parent | manage both |
| `role.perms` | scope, id, perms(mask) | only perms the actor holds |
| `role.remove` | scope, id | NORMAL roles only; children move up |
| `role.assign` / `role.unassign` | scope, id, player / scope, player | members only; one role per tree |
| `province.create` | name, town (capital; default own town) | MANAGE_PROVINCES (pays `prices.province`) |
| `province.add` / `.remove` / `.capital` | province, town | MANAGE_PROVINCES |
| `province.governor` | province, player ("" clears) | MANAGE_PROVINCES |
| `province.rename` / `.delete` | province, name / province | MANAGE_PROVINCES |
| `nation.found` | name, gov | town mayor (pays `prices.nation` from town bank) |
| `nation.invite` / `.kick` / `.capital` | town | MANAGE_TOWNS / MANAGE_TOWNS / leader |
| `nation.join` | nation | mayor, invited |
| `nation.leave`, `nation.delete` | — | mayor / leader |
| `nation.leader` / `.gov` / `.rename` / `.color` | player / gov / name / color | leader |
| `nation.deposit` / `.withdraw` | amount | citizen / BANK_WITHDRAW |
| `alliance.create` / `.invite` / `.join` / `.kick` / `.leave` | name / nation / alliance / nation / — | DIPLOMACY |
| `war.declare` | nation | DECLARE_WAR (pays `prices.war`) |
| `war.peace` | optional war | SIGN_TREATY — offers, or accepts the other side's offer |
| `war.surrender` | optional war | SIGN_TREATY |
| `war.terms` | annex (ListTag of StringTag town ids, only `fallen`, never the capital), tribute, vassal (days) | SIGN_TREATY, winner during TREATY |
| `war.confirm` | — | applies the treaty now |
| `npc.gift` / `npc.propose_alliance` / `npc.ask_peace` | nation, amount / nation / nation | envoy diplomacy |

War phases: PREPARATION → ACTIVE → TREATY (the winner edits terms until `treatyUntil`, defaults apply after) → ENDED.
Times are overworld game ticks (`view().now()`; 20 ticks = 1 s).

## Screens and HUD
- `client.screen.NationScreen(String tab)` — opened by key N, `/nw`, or `OpenScreenPayload("nation", {tab})`. A placeholder to replace.
- `client.screen.EnvoyScreen(UUID nation, int entityId)` — opened when a player talks to an NPC envoy. A placeholder.
- `client.notice.NoticeHud` — `push(kind, title, subtitle, color, icon)` and `render(GuiGraphics, DeltaTracker)` (a
  GUI layer above the boss bar, registered in `client/NWClient`). Server notices arrive through `ClientPolitics.notice`.
  Kinds: TERRITORY, WAR, POLITICS, PERMISSION. Icons used by the server: war, siege, crown, scroll, shield, skull,
  handshake, flag (territory/pvp may add more). A placeholder to replace.
- Client config: `NWClientConfig` (add sections there).

## Lang
`tools/lang/genlang.py` holds every key in English and Spanish and writes both json files. Add your keys there (or
pass extra JSON tables: `python3 tools/lang/genlang.py extra.json` with `{"key": ["en", "es"]}`); never hand-edit the
json files.

## Art
`tools/artgen/` (stdlib Python: `pngio.write_png(path, rows)` with RGBA tuples; see `nwart.py` for helpers). Put GUI
textures under `assets/nationsandwar/textures/gui/`. Generate, never download.

## Seeing it in the game
`NW_PREVIEW=<scenes> ./gradlew runClient -Ppreview` opens the world `runs/client/saves/nw_preview`, seeds sample politics
once (`client/dev/PreviewSample`: town Highmoor with 10 named residents and roles, the Kingdom of Valoria with a
second town, province North March and a governor, an allied NPC nation, an active war with a siege), plays each
scene and writes `runs/client/screenshots/nw_<scene>.png`, then quits. Scenes live in `client/dev/DevPreview.SCENES`;
add yours with `DevPreview.scene(name, new Scene(serverSetup, screenSupplier, clientSetup, settleTicks, hud))`.
A scene named `gui:roles` runs scene `gui` with `DevPreview.arg()` = "roles" (use it to pick a tab).
Existing: `gui`, `world`, `showcase`. Read the PNGs with the Read tool to check your work. `screencapture` does not work.

## Build and tests
- `./gradlew compileJava` / `./gradlew test` (JUnit) / `./gradlew runGameTestServer` (6 GameTests) must stay green.
- Only one Gradle at a time.
- There is no git history to rely on and you must **not** commit.

## v0.2 GUI pass: nations run by other mods
The server side of the API is done and tested (`docs/API.md`). What the client must show:

### Data
- `ClientClaims.nation(id)` and `ClientClaims.town(id)` return briefs with `managedBy()` (`""` = not managed), `managed()`, and for nations `titles()`.
  - `titles()` is the lang prefix of the government's names: the noun is the prefix itself, the ranks are `<prefix>.leader` and so on.
  - Use it instead of `nationsandwar.gov.<government>` wherever a nation's government is named: `TerritoryWatcher`, `OverviewTab`, `DiplomacyTab`, `EnvoyScreen`.
- `PoliticsView.NationSummary` has no managed flag: look the nation up in `ClientClaims`.
- `api.client.ManagedNationScreens.handler(managedBy)` returns the providing mod's handler, or null. Its methods:
  - `title`, `description`, `button`;
  - `canOpen(player, brief)`, `cannotOpen`;
  - `open(player, brief)`.

### What to build
1. **The managed-nation card**
   - It appears wherever a nation can be selected or inspected: the diplomacy list row, a claim picked on the map tab, the overview's "nation here" info, anything similar.
   - When the nation is managed, show an **empty card** instead of the normal details:
     - its colour and emblem area;
     - the handler's title and description;
     - the badge `nationsandwar.managed.badge`.
   - The card has one button with the handler's label, enabled only if `canOpen`. Its tooltip is `cannotOpen` when disabled; on press it calls `open`.
   - With no handler: the generic text `nationsandwar.managed.description` and no button.
   - The diplomacy row of a managed nation has **no** war/alliance buttons (the server refuses them anyway).
2. **Badge** managed nations in nation lists (diplomacy, overview, war tab participants).
3. **Maps**
   - Managed claims get a distinct style in `compat/MapStyle`, used by both JourneyMap and Xaero. Suggestion: a darker fill with a hatch or border in the nation colour.
   - The territory notice (`TerritoryWatcher`) uses the managed titles.
4. **Wars**
   - `WarTab`/`SiegePanel`/notices must cope with a side that is a managed nation: it has no leader and no citizens; show its name and colour.
   - A war's `reason` lang key is in the server's `War.reason`, but it is not in `WarBrief` yet. If you need it, add a `reason` string to `WarBrief` (`Sync.briefs`) minimally and say so.
5. **Preview**
   - Add to `PreviewSample` a managed nation "The Pit" with one town near Highmoor, created with `NationsAndWarApi.createManagedNation` / `createManagedTown`, key `nationsandwar_preview:pit`, titles `nationsandwar.gov.theocracy`.
   - Register a dev-only `ManagedNationScreens` handler for it in the preview harness; its `open` closes the screen and shows a toast.
   - Add scenes `gui:managed` (the diplomacy tab with the Pit selected) and `gui:managedmap`.

### Lang
Already there: `nationsandwar.managed.description|open|cannot_open|badge`. Add any new keys through `tools/lang/genlang.py` (EN+ES).
