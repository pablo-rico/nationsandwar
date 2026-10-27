# Nations & War: API for other mods (v0.2, `NationsAndWarApi.VERSION = 1`)

Everything lives in `org.papiricoh.nationsandwar.api`.
- **Server side:** `NationsAndWarApi`, `ManagedNations`, `WarApi`, `ProtectionBypass`, `ClaimVetoes`, `event.*`.
- **Client side:** `api.client.ManagedNationScreens`.
- Keep every reference in your own compat package, and load it only when `ModList.get().isLoaded("nationsandwar")`.

## Depending on it
- **Maven:** `./gradlew publishToMavenLocal` here, then in your mod `compileOnly "org.papiricoh:nationsandwar:0.2.0"` with `mavenLocal()`.
- **Jar:** copy `build/libs/nationsandwar_1.21.1_0.2.0.jar` and use `compileOnly files(...)`. The jar uses official names, so no remapping is needed.
- **`neoforge.mods.toml`:** `modId="nationsandwar" type="optional" versionRange="[0.2,)" ordering="AFTER" side="BOTH"`.

## Reading
`NationsAndWarApi` exposes these reads:
- `politics(server)`: the live `model.Politics`.
- `nationOf(server, player)`, `townOf`.
- `townAt(level, pos)` / `townAt(server, dim, chunk)`.
- `allied`, `atWar`, `dimension(level)`.

Records (`Nation`, `Town`, `Alliance`, `war.War`) are the live ones:
- Read them freely.
- Change them only through the API or `Politics`' rules, and then call `NationsAndWarApi.changed(server)`. That saves the data and resyncs clients and maps.
- `Nation.ext` / `Town.ext` (`CompoundTag`) are saved and never read by this mod. Keep one key per mod in them.

## Nations run by another mod ("managed")
1. **Register a provider** with `ManagedNations.register(new ManagedNationProvider() { key() … })` during setup. Every method has a default:

   | method | default |
   |---|---|
   | `mayBeDeclaredOn(server, self, attacker)` | false |
   | `mayBesiege(server, town, chunk, besieger)` | true |
   | `termsWhenWinning(server, war, self, loser)` | null = this mod's defaults with no annexation |
   | `onFallen(server, war, self, town, winner)` | `RAZE`; also `TRANSFER` or `KEEP` |

2. **Create the nation** with `createManagedNation(server, key, name, titles, color)`.
   - `titles` is a lang prefix: the prefix itself is the noun, and `<prefix>.leader`, `.second` and so on are the ranks.
   - The first town attached becomes its capital.
3. **Create its towns** with `createManagedTown(level, nation, name, centre, chunks, core, placeCore, perms, flags)`.
   - It claims the home chunk plus the listed chunks, where free. No cost, buffer, adjacency or veto applies.
   - A town with no Core block (`placeCore=false`) only falls through `WarApi.townFallen`.
4. **Manage its land** with `claimFor`, `unclaimFor` and `raze(server, town)`.
5. **Find them** with `managedNation(server, key)`, `managedNations`, `managedTowns`.

### What a managed nation cannot do
- **Membership:** nobody joins its towns, invites them, joins the nation, or allies with it (msg `managed`).
- **Brains:** NPC brains neither declare on it nor form an opinion of it.
- **Villages:** they never join it.
- **Wars:** it never declares the ordinary way. Players declare on it only when `mayBeDeclaredOn` allows it (msg `war.managed`).
- **Sieges:** its towns always count as defended, like NPC towns.
- **Losing:** a managed loser is never annexed, taxed or made a vassal. Each fallen town follows `onFallen` instead, and the war ends at once.
- **Winning:** a managed winner's treaty applies at once, with `termsWhenWinning`.

## Wars (`WarApi`)
- **`declare(server, attacker, defender, WarOptions)`** declares as the provider.
  - The managed locks are skipped.
  - The ordinary rules still apply: allies, vassals, one war per pair, the cooldown unless `noCooldown()`, and `WarEvent.Declare`.
  - Options: `WarOptions.free().times(prepTicks, durationTicks).target(score).reason("lang.key").noCooldown()`. Use `ordinary(pay)` for a normal declaration.
- **`addScore(server, war, nation, points)`** adds points to that nation's side.
- **`end(server, war, outcome)`** ends the war.
- **`townFallen(server, war, town, byNation)`** takes a town's heart. The town's home chunk must have been taken first, which raises `WarEvent.CoreOpened`.
- **`plantBannerAs(level, pos, nation, requireDefendersOnline)`** places the banner block and starts the siege. It removes the block again if the siege is refused.
- **`registerCombatant(entity -> nationId or null)`** says which nation an entity fights for. It applies to:
  - siege control around banners;
  - building and breaking in war zones;
  - Core hits;
  - kill points;
  - PvP during a war.

  Players fight for their own nation whenever that nation is in the war. Otherwise the resolvers are asked, which covers a player who serves a managed nation from outside.

## Events (`api.event`, on `NeoForge.EVENT_BUS`)
**Delivery**
- **`WarEvent.Declare`** is posted at once and can be cancelled; the declarer is told msg `war.vetoed`.
- **Every other event** is queued and delivered at the end of the server tick, in order. Listeners may therefore change politics.
- **`NationsAndWarApi.flushEvents()`** delivers the queue now.

| class | events |
|---|---|
| `TownEvent` | `Founded`, `Removed`, `Renamed`, `ResidentJoined`, `ResidentLeft`, `Claimed`, `Unclaimed`, `ChunkTransferred` |
| `NationEvent` | `Founded`, `Removed`, `Renamed`, `LeaderChanged`, `TownJoined`, `TownLeft` |
| `AllianceEvent` | `Joined`, `Left` |
| `WarEvent` | `Declare`, `Started`, `PhaseChanged`, `SiegeStarted`, `ChunkTaken`, `CoreOpened`, `TownFallen`, `Ended`, `TreatyApplied` |

Citizenship follows towns. A player leaves a nation through any of these:
- `TownEvent.ResidentLeft`;
- `NationEvent.TownLeft`;
- `TownEvent.Removed`;
- `NationEvent.Removed`.

## Protection exceptions (`ProtectionBypass`)
- **`openZone(key, dim, AABB, Set<Bypass>)`** switches protection off inside a box until `close(key)` / `closeAll(prefix)`.
  - Use it for a boss arena, a set piece or a ritual site.
  - `Bypass` values: BUILD, DESTROY, SWITCH, CONTAINER, ITEM_USE, ATTACK, PVP, SPAWNS, MOB_GRIEFING, EXPLOSIONS, FIRE, PISTONS. `ALL` covers every one.
  - Zones live in memory: open them again after a restart.
  - Changes inside a zone are **not** logged for a war's restoration.
  - A Town Core stays protected.
- **`exemptEntities(predicate)`** exempts entities from the spawn, griefing and explosion rules everywhere.

## Reserved land (`ClaimVetoes`)
`register((dim, chunk) -> reserved)` reserves land.
- **Refused for players:** founding a town or claiming the chunk fails with msg `claim_reserved`.
- **Skipped by NPC towns.**
- **Ignored by managed towns.**

## Client: the card of a managed nation
`ManagedNationScreens.register(key, handler)` sets how the management GUI shows one of your nations. It replaces the nation's tabs with an empty card.

| handler method | what it gives the card |
|---|---|
| `title` | the title |
| `description` | the text |
| `button` | the button label |
| `canOpen(player, brief)` | whether the button is enabled |
| `cannotOpen` | the tooltip when it is disabled |
| `open(player, brief)` | what happens on press; the screen is still open, so close or replace it |

The briefs carry `NationBrief.managedBy()` / `titles()` and `TownBrief.managedBy()` (`""` = not managed), with `managed()` helpers.
