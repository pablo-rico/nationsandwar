# Nations & War

**Found a town. Forge a nation. Take the field.**

Nations & War is a Towny-style mod for NeoForge, built for server and single-player use:
- **Build it up:** claim land, grow your town into a city, unite towns into provinces and nations, and sign alliances.
- **Fight for it:** take what you want in siege wars that put every block back the way it was when the peace is signed.
- **Live among others:** villages grow into NPC nations with their own guards, envoys and opinions of you.

Everything runs from one in-game menu (press **N**) or from commands, and your territory shows up on JourneyMap and Xaero's maps.

## Features

### Towns and land
- **Found a town** with emeralds; its Town Core is placed at your feet.
- **Claim land** chunk by chunk, from the map in the menu (click or drag) or by command. Up to two outposts may stand apart from the rest.
- **Town levels**: Hamlet, Village, City and Metropolis. Each level allows more claimed land as your number of residents grows.
- **Protection by group**: decide what residents, members of your nation, allies and outsiders may do in your land:
  - build;
  - break;
  - use doors and switches;
  - open containers;
  - use buckets and similar items.

  Set it for the whole town or chunk by chunk.
- **Flags** for PvP, mobs, fire and explosions, town-wide or per chunk. Pistons cannot push across borders.
- **One-time costs, no upkeep.** Emeralds go into town and nation banks, and nobody loses their home for missing a payment.

### Nations, provinces and alliances
- **Unite towns into a nation** and choose its government: Kingdom, Republic, Empire, Federation, Theocracy or Tribe. The
  government names its ranks (King, President, Emperor, High Priest...); the rules are the same for all of them.
- **Provinces** group towns under a governor.
- **Rank trees** for towns and nations:
  - create, rename and recolour roles;
  - nest roles under each other;
  - give each role exactly the permissions it needs: claim, invite, manage flags, use the bank, declare war, sign treaties...

  The menu draws the tree in 2.5D with every member's face.
- **Alliances**: allies cannot fight each other and join each other's wars.

### War
1. **Declaring**: a nation's marshal declares war. Both sides get a preparation period, then the fighting starts.
2. **Siege Banners**: plant one on the enemy's border. Whichever side has more fighters around it pushes its control bar. Win the bar and the chunk is yours.
   - **Liberation**: plant your own banner in a chunk the enemy holds to take it back. They lose the points it gave them, and freeing your home chunk shuts your Core again.
3. **The Town Core**: take a town's home chunk, then break its Core several times to make the town fall.
4. **Score**: chunks, Cores and kills score points. Reach the target score, or lead when time runs out, to win.
5. **Treaties**: the winner dictates the peace:
   - annex fallen towns;
   - take a tribute from the loser's bank;
   - make the loser a vassal for a few days.
6. **Everything comes back**: every block broken or placed in a war zone is restored when the war ends, and nothing drops. Wars change borders, not landscapes.
7. **Fair fights**: a siege can only start while enough defenders are online, and the same two nations must wait before fighting again.

### NPC nations
- **Villages become NPC towns**: a share of the world's villages turn into towns and band together into NPC nations with names and governments of their own.
- **Guards** patrol their towns and march out with banners when their nation is at war.
- **Envoys** stand in every NPC capital: talk to one to see what their nation thinks of you, send gifts, propose an alliance or ask for peace.
- **Courts of their own**: every NPC nation and town has named characters in its rank tree, from the King down to the Treasurer. The envoy is the nation's Chancellor, and the court changes when a war is lost, a capital falls or simply as the days go by.
- **They make their own choices**: NPC nations ally with friends, declare war on nations they hate and ask for peace when they are losing or have lost too many soldiers.
- **They wage real campaigns**:
  - they push siege after siege toward a town's Core and strike it when it is exposed;
  - they leave garrisons in the land they take and open several fronts when they are strong enough;
  - defenders rush to every siege and win back their occupied land, home chunk first;
  - wars between NPC nations far from any player keep going on their own.

### Interface
- **A full management menu** with tabs: overview, town, provinces, nation, the Atlas, diplomacy, war and an interactive claim map.
  - **The Atlas** lets you browse every alliance, nation, province and town and look at any rank tree: who holds which rank, in 2.5D. Yours stays editable.
  - Every tool (renaming, colours, ranks, permissions, banks, invitations, treaties...) opens in its own window, and nothing is deleted without confirming first.
- **Notices at the top of the screen**:
  - entering and leaving territory;
  - wars declared, sieges started, towns fallen;
  - political changes;
  - actions you are not allowed to do.
- **A siege panel** shows every running siege's control bar.
- **Maps**:
  - Claims and sieges appear on the menu's map and, when installed, on **JourneyMap** and **Xaero's World Map / Minimap**.
  - Pick a layer (nations, provinces, towns or alliances) on the map tab or with its key; every map follows it.
  - Bold borders mark where each nation, province or alliance ends, with dotted lines between its towns.
  - The names are written across the land, Paradox style.
  - Provinces can have their own colour.

### Languages
English and Spanish.

## For mod developers
Nations & War has a public API (`org.papiricoh.nationsandwar.api`):
- events for towns, nations, alliances and wars, including a cancellable war declaration;
- nations run by your own mod;
- a war API: declare, score, take towns, plant banners, and say which nation your mobs fight for;
- protection-free zones for boss arenas and set pieces;
- reserved land.

SupernaturalCraft uses it to bring Heaven, Hell and its player guilds into the world's politics. See `docs/API.md` in the source repository.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.221+ |
| [JourneyMap](https://modrinth.com/mod/journeymap) | optional: claims, sieges and names on the map |
| [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) / [Minimap](https://modrinth.com/mod/xaeros-minimap) | optional: claims on the map (names on the World Map) |

Install it on the **server and on every client**. It works in single player too. Prices, war timing, NPC villages and more can be set in the server config (`nationsandwar-server.toml`).

## Getting started
1. Gather **64 emeralds** and run `/town new <name>` (or press **N**). Your Town Core appears at your feet.
2. Claim more land from the **Map** tab (4 emeralds per chunk, a little more for each one after).
3. Invite friends with `/town invite <player>` and hand out ranks in the **Atlas** tab.
4. With **256 emeralds** in the town bank, found a nation with `/nation new <government> <name>` (kingdom, republic, empire, federation, theocracy or tribe).
5. Ready for war? A **Siege Banner** and a marshal are all you need.

## Status
Nations & War is in active development (0.x). Back up your worlds before updating. Bug reports and ideas are very welcome.
