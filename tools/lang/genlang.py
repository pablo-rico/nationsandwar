"""Writes en_us.json and es_es.json from one table (run: python3 tools/lang/genlang.py)."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "nationsandwar", "lang")
T = {}

def k(key, en, es):
    T[key] = (en, es)

# items, blocks, entities, keys
k("itemGroup.nationsandwar", "Nations & War", "Naciones y Guerra")
k("block.nationsandwar.town_core", "Town Core", "Núcleo de Ciudad")
k("block.nationsandwar.siege_banner", "Siege Banner", "Estandarte de Asedio")
k("item.nationsandwar.guard_spawn_egg", "Guard Spawn Egg", "Huevo de guardia")
k("item.nationsandwar.envoy_spawn_egg", "Envoy Spawn Egg", "Huevo de enviado")
k("entity.nationsandwar.guard", "Guard", "Guardia")
k("entity.nationsandwar.envoy", "Envoy", "Enviado")
k("entity.nationsandwar.envoy.of", "Envoy of %s", "Enviado de %s")
k("key.categories.nationsandwar", "Nations & War", "Naciones y Guerra")
k("key.nationsandwar.open", "Open nation menu", "Abrir menú de nación")
k("nationsandwar.gui.title", "Nations & War", "Naciones y Guerra")
k("nationsandwar.gui.envoy", "Envoy", "Enviado")
k("nationsandwar.map.besieged", " — besieged by %s (%s%%)", " — asediada por %s (%s%%)")

govs = {
    "kingdom": (("Kingdom", "King", "Chancellor", "Marshal", "Treasurer", "Duke"), ("Reino", "Rey", "Canciller", "Mariscal", "Tesorero", "Duque")),
    "republic": (("Republic", "President", "Vice President", "Minister of War", "Minister of Finance", "Governor"),
                 ("República", "Presidente", "Vicepresidente", "Ministro de Guerra", "Ministro de Hacienda", "Gobernador")),
    "empire": (("Empire", "Emperor", "Grand Vizier", "Warlord", "Imperial Treasurer", "Satrap"),
               ("Imperio", "Emperador", "Gran Visir", "Señor de la Guerra", "Tesorero Imperial", "Sátrapa")),
    "federation": (("Federation", "Chancellor", "Deputy Chancellor", "Defence Commissioner", "Finance Commissioner", "State Governor"),
                   ("Federación", "Canciller", "Vicecanciller", "Comisario de Defensa", "Comisario de Finanzas", "Gobernador de Estado")),
    "theocracy": (("Theocracy", "High Priest", "Cardinal", "Templar Master", "Almoner", "Bishop"),
                  ("Teocracia", "Sumo Sacerdote", "Cardenal", "Maestre Templario", "Limosnero", "Obispo")),
    "tribe": (("Tribe", "Chieftain", "Elder", "War Chief", "Keeper of Stores", "Clan Head"),
              ("Tribu", "Jefe", "Anciano", "Jefe de Guerra", "Guardián de Provisiones", "Cabeza de Clan")),
}
for g, (en, es) in govs.items():
    k(f"nationsandwar.gov.{g}", en[0], es[0])
    for i, r in enumerate(["leader", "second", "marshal", "treasurer", "governor"], 1):
        k(f"nationsandwar.gov.{g}.{r}", en[i], es[i])
k("nationsandwar.role.leader", "Leader", "Líder")
k("nationsandwar.role.mayor", "Mayor", "Alcalde")
k("nationsandwar.role.assistant", "Assistant", "Asistente")
k("nationsandwar.role.builder", "Builder", "Constructor")

for l, en, es in [("hamlet", "Hamlet", "Aldea"), ("village", "Village", "Pueblo"), ("city", "City", "Ciudad"), ("metropolis", "Metropolis", "Metrópolis")]:
    k(f"nationsandwar.level.{l}", en, es)
for f, en, es in [("pvp", "PvP", "PvP"), ("mobs", "Monsters", "Monstruos"), ("fire", "Fire", "Fuego"), ("explosions", "Explosions", "Explosiones")]:
    k(f"nationsandwar.flag.{f}", en, es)
perms = [("claim", "Claim land", "Reclamar terreno"), ("unclaim", "Release land", "Liberar terreno"), ("invite", "Invite", "Invitar"),
         ("kick", "Expel", "Expulsar"), ("manage_roles", "Manage roles", "Gestionar cargos"), ("manage_flags", "Manage flags", "Gestionar ajustes"),
         ("manage_plots", "Manage chunk permissions", "Gestionar permisos de chunk"), ("bank_deposit", "Deposit", "Ingresar"),
         ("bank_withdraw", "Withdraw", "Retirar"), ("set_spawn", "Set spawn", "Fijar punto"), ("diplomacy", "Diplomacy", "Diplomacia"),
         ("declare_war", "Declare war", "Declarar la guerra"), ("sign_treaty", "Sign treaties", "Firmar tratados"),
         ("manage_provinces", "Manage provinces", "Gestionar provincias"), ("manage_towns", "Manage towns", "Gestionar ciudades")]
for p, en, es in perms:
    k(f"nationsandwar.perm.{p}", en, es)

info = [("level", "Level", "Nivel"), ("mayor", "Mayor", "Alcalde"), ("residents", "Residents", "Residentes"), ("claims", "Chunks", "Chunks"),
        ("bank", "Bank", "Banco"), ("nation", "Nation", "Nación"), ("province", "Province", "Provincia"), ("leader", "Leader", "Líder"),
        ("capital", "Capital", "Capital"), ("towns", "Towns", "Ciudades"), ("citizens", "Citizens", "Ciudadanos"), ("alliance", "Alliance", "Alianza"),
        ("town_list", "Towns:", "Ciudades:"), ("nation_list", "Nations:", "Naciones:"), ("province_list", "Provinces:", "Provincias:"),
        ("wars", "Wars:", "Guerras:")]
for i, en, es in info:
    k(f"nationsandwar.info.{i}", en, es)

notices = [
    ("town_founded", "A new town is founded", "Se funda una nueva ciudad"),
    ("nation_founded", "A new nation rises", "Nace una nueva nación"),
    ("town_invite", "You are invited to a town", "Te invitan a una ciudad"),
    ("resident_joined", "A new resident arrives", "Llega un nuevo residente"),
    ("nation_invite", "Your town is invited to a nation", "Invitan a tu ciudad a una nación"),
    ("town_joined_nation", "A town joins the nation", "Una ciudad se une a la nación"),
    ("alliance_invite", "Invitation to an alliance", "Invitación a una alianza"),
    ("alliance_joined", "A new member of the alliance", "Nuevo miembro de la alianza"),
    ("governor", "You are now governor", "Ahora eres gobernador"),
    ("role_given", "You hold a new office", "Ocupas un nuevo cargo"),
    ("war_declared", "War is declared!", "¡Se declara la guerra!"),
    ("war_active", "The war begins", "Comienza la guerra"),
    ("vs", "%s vs %s", "%s contra %s"),
    ("siege_started", "A siege begins", "Comienza un asedio"),
    ("siege_broken", "The siege banner falls", "Cae el estandarte de asedio"),
    ("siege_failed", "The siege is repelled", "El asedio es repelido"),
    ("chunk_taken", "Land taken", "Terreno tomado"),
    ("core_exposed", "The town core is exposed!", "¡El núcleo de la ciudad queda expuesto!"),
    ("town_fell", "A town has fallen", "Ha caído una ciudad"),
    ("peace_offered", "White peace offered", "Ofrecen paz blanca"),
    ("white_peace", "White peace", "Paz blanca"),
    ("war_won", "Victory", "Victoria"),
    ("treaty_signed", "A treaty is signed", "Se firma un tratado"),
    ("treaty_terms", "%s towns annexed, %s emeralds, %s days of vassalage", "%s ciudades anexionadas, %s esmeraldas, %s días de vasallaje"),
    ("denied.build", "You cannot build here", "No puedes construir aquí"),
    ("denied.destroy", "You cannot break here", "No puedes romper aquí"),
    ("denied.switch", "You cannot use that here", "No puedes usar eso aquí"),
    ("denied.container", "You cannot open that here", "No puedes abrir eso aquí"),
    ("denied.item_use", "You cannot use items here", "No puedes usar objetos aquí"),
]
for n, en, es in notices:
    k(f"nationsandwar.notice.{n}", en, es)

# client-side notices: territory changes, badges and the siege panel
territory = [
    ("wilderness", "Wilderness", "Tierras salvajes"),
    ("left", "Leaving %s", "Sales de %s"),
    ("welcome_home", "Welcome home", "Bienvenido a casa"),
    ("nation_of", "%s of %s", "%s de %s"),
    ("province_of", "Province of %s", "Provincia de %s"),
    ("independent", "Independent town", "Ciudad independiente"),
    ("badge.war", "AT WAR", "EN GUERRA"),
    ("badge.ally", "ALLY", "ALIADO"),
    ("badge.pvp", "PvP", "PvP"),
    ("badge.safe", "No PvP", "Sin PvP"),
    ("siege_of", "Siege of %s", "Asedio de %s"),
]
for n, en, es in territory:
    k(f"nationsandwar.notice.{n}", en, es)

msgs = [
    ("ok", "Done.", "Hecho."), ("bad_args", "Invalid arguments.", "Argumentos no válidos."), ("unknown_action", "Unknown action.", "Acción desconocida."),
    ("no_perm", "You do not have permission for that.", "No tienes permiso para eso."),
    ("no_membership", "You need a town (or nation) for that.", "Necesitas una ciudad (o nación) para eso."),
    ("no_town", "You are not in a town.", "No perteneces a ninguna ciudad."), ("no_nation", "You are not in a nation.", "No perteneces a ninguna nación."),
    ("no_alliance", "Not in an alliance.", "No hay alianza."), ("leader_only", "Only the leader can do that.", "Solo el líder puede hacer eso."),
    ("bad_name", "Names are 3–24 letters, numbers, spaces, ' or -.", "Los nombres tienen 3–24 letras, números, espacios, ' o -."),
    ("name_taken", "That name is taken.", "Ese nombre ya existe."), ("bad_amount", "Invalid amount.", "Cantidad no válida."),
    ("player_poor", "You need %s emeralds.", "Necesitas %s esmeraldas."), ("town_poor", "The town bank needs %s emeralds.", "El banco de la ciudad necesita %s esmeraldas."),
    ("nation_poor", "The nation bank needs %s emeralds.", "El banco de la nación necesita %s esmeraldas."),
    ("already_in_town", "You already belong to a town.", "Ya perteneces a una ciudad."),
    ("already_in_nation", "That town already belongs to a nation.", "Esa ciudad ya pertenece a una nación."),
    ("already_in_alliance", "Already in an alliance.", "Ya está en una alianza."),
    ("chunk_taken", "This land is already claimed.", "Este terreno ya está reclamado."),
    ("too_close", "Too close to another nation's town.", "Demasiado cerca de una ciudad de otra nación."),
    ("core_blocked", "Stand on an open spot: the Town Core goes at your feet.", "Ponte en un sitio libre: el Núcleo se coloca a tus pies."),
    ("town_founded", "The town of %s is founded!", "¡Se funda la ciudad de %s!"),
    ("claimed", "Chunk claimed for %s emeralds.", "Chunk reclamado por %s esmeraldas."),
    ("outpost_claimed", "Outpost claimed for %s emeralds.", "Puesto avanzado reclamado por %s esmeraldas."),
    ("claim_limit", "Your town can hold at most %s chunks.", "Tu ciudad puede tener como mucho %s chunks."),
    ("not_adjacent", "Claims must touch your town (or claim an outpost).", "Los reclamos deben tocar tu ciudad (o reclama un puesto avanzado)."),
    ("outpost_limit", "At most %s outposts.", "Como mucho %s puestos avanzados."), ("wrong_dimension", "Your town is in another dimension.", "Tu ciudad está en otra dimensión."),
    ("unclaimed", "Chunk released.", "Chunk liberado."), ("not_yours", "That chunk is not your town's.", "Ese chunk no es de tu ciudad."),
    ("home_chunk", "The home chunk cannot be released.", "El chunk central no se puede liberar."),
    ("invited", "Invitation sent.", "Invitación enviada."), ("target_in_town", "That player already belongs to a town.", "Ese jugador ya pertenece a una ciudad."),
    ("joined", "You joined %s.", "Te has unido a %s."), ("not_invited", "No invitation.", "No hay invitación."), ("left", "You left %s.", "Has dejado %s."),
    ("mayor_cannot_leave", "The mayor cannot leave: name a new mayor or delete the town.", "El alcalde no puede irse: nombra otro alcalde o borra la ciudad."),
    ("kicked", "Expelled.", "Expulsado."), ("cannot_kick", "You cannot expel them.", "No puedes expulsarlo."), ("not_resident", "Not a resident.", "No es residente."),
    ("mayor_set", "New mayor named.", "Nuevo alcalde nombrado."), ("deposited", "Deposited %s emeralds.", "Ingresadas %s esmeraldas."),
    ("withdrew", "Withdrew %s emeralds.", "Retiradas %s esmeraldas."), ("flag_set", "Setting changed.", "Ajuste cambiado."),
    ("perm_set", "Permission changed.", "Permiso cambiado."), ("chunk_reset", "Chunk settings reset.", "Ajustes del chunk reiniciados."),
    ("spawn_set", "Town spawn set.", "Aparición de la ciudad fijada."), ("teleported", "Teleported.", "Teletransportado."),
    ("in_war_zone", "Not while in a war zone.", "No mientras estés en zona de guerra."), ("renamed", "Renamed.", "Renombrado."),
    ("recolored", "Colour changed.", "Color cambiado."), ("town_opened", "The town is open to anyone.", "La ciudad está abierta a todos."),
    ("town_closed", "The town is invitation-only.", "La ciudad es solo por invitación."), ("town_deleted", "The town of %s is no more.", "La ciudad de %s ya no existe."),
    ("at_war", "Not during a war.", "No durante una guerra."), ("unknown_town", "Unknown town.", "Ciudad desconocida."),
    ("unknown_nation", "Unknown nation.", "Nación desconocida."), ("unknown_alliance", "Unknown alliance.", "Alianza desconocida."),
    ("not_member", "Not a member.", "No es miembro."), ("not_nation_town", "That town is not in your nation.", "Esa ciudad no es de tu nación."),
    ("town_in_province", "That town is already in that province.", "Esa ciudad ya está en esa provincia."),
    ("province_capital", "The provincial capital cannot be moved.", "La capital provincial no se puede mover."),
    ("not_in_province", "That town is not in the province.", "Esa ciudad no está en la provincia."),
    ("province_created", "Province %s created.", "Provincia %s creada."), ("province_deleted", "Province %s dissolved.", "Provincia %s disuelta."),
    ("province_town_added", "Town added to the province.", "Ciudad añadida a la provincia."),
    ("province_town_removed", "Town removed from the province.", "Ciudad quitada de la provincia."),
    ("province_capital_set", "Provincial capital set.", "Capital provincial fijada."), ("governor_set", "Governor named.", "Gobernador nombrado."),
    ("nation_founded", "The nation of %s is proclaimed!", "¡Se proclama la nación de %s!"), ("nation_joined", "Your town joined %s.", "Tu ciudad se ha unido a %s."),
    ("nation_left", "Your town left %s.", "Tu ciudad ha dejado %s."), ("capital_cannot_leave", "The capital cannot leave.", "La capital no puede irse."),
    ("capital_set", "New capital.", "Nueva capital."), ("leader_set", "New leader.", "Nuevo líder."), ("government_set", "Government changed.", "Gobierno cambiado."),
    ("nation_deleted", "The nation of %s is dissolved.", "La nación de %s se disuelve."),
    ("alliance_created", "Alliance %s formed.", "Alianza %s formada."), ("alliance_joined", "Joined the alliance %s.", "Unido a la alianza %s."),
    ("alliance_left", "Left the alliance %s.", "Salida de la alianza %s."),
    ("no_role", "They hold no role.", "No tiene cargo."), ("cannot_assign_leader", "The leader's role cannot change this way.", "El cargo del líder no se cambia así."),
    ("cannot_grant", "You can only grant permissions you hold.", "Solo puedes conceder permisos que tienes."),
    ("role.created", "Role created.", "Cargo creado."), ("role.renamed", "Role renamed.", "Cargo renombrado."), ("role.recolored", "Role colour changed.", "Color del cargo cambiado."),
    ("role.moved", "Role moved.", "Cargo movido."), ("role.perms_set", "Role permissions set.", "Permisos del cargo fijados."), ("role.deleted", "Role deleted.", "Cargo borrado."),
    ("role.assigned", "Role given.", "Cargo asignado."), ("role.unassigned", "Role taken away.", "Cargo retirado."), ("role.unknown", "Unknown role.", "Cargo desconocido."),
    ("role.too_many", "Too many roles.", "Demasiados cargos."), ("role.bad_name", "Role names are 1–24 characters.", "Los nombres de cargo tienen 1–24 caracteres."),
    ("role.root_fixed", "The leader's role cannot be changed that way.", "El cargo de líder no se puede cambiar así."),
    ("role.cycle", "A role cannot be placed under itself.", "Un cargo no puede quedar bajo sí mismo."),
    ("role.builtin", "Built-in roles cannot be deleted or assigned.", "Los cargos fijos no se pueden borrar ni asignar."),
    ("war.declared", "War declared on %s!", "¡Guerra declarada a %s!"), ("war.self", "You cannot fight yourself.", "No puedes luchar contra ti mismo."),
    ("war.ally", "You cannot fight an ally.", "No puedes luchar contra un aliado."), ("war.vassal", "Not between overlord and vassal.", "No entre señor y vasallo."),
    ("war.already", "Already at war with them.", "Ya estáis en guerra."), ("war.busy", "Your nation is already waging a war.", "Tu nación ya libra una guerra."),
    ("war.cooldown", "Too soon: wait %s more minutes.", "Demasiado pronto: espera %s minutos más."), ("war.none", "No war.", "No hay guerra."),
    ("war.peace_offered", "White peace offered.", "Paz blanca ofrecida."), ("war.white_peace", "White peace signed.", "Paz blanca firmada."),
    ("war.surrendered", "You surrendered.", "Te has rendido."), ("war.no_treaty", "No treaty to dictate.", "No hay tratado que dictar."),
    ("war.bad_terms", "Those terms are not allowed.", "Esos términos no están permitidos."), ("war.terms_set", "Terms set.", "Términos fijados."),
    ("war.treaty_signed", "Treaty signed.", "Tratado firmado."),
    ("siege.not_enemy", "Plant it in an enemy town.", "Plántalo en una ciudad enemiga."), ("siege.no_war", "You are not at war with them.", "No estás en guerra con ellos."),
    ("siege.occupied", "That chunk is already taken.", "Ese chunk ya está tomado."), ("siege.already", "There is already a siege here.", "Ya hay un asedio aquí."),
    ("siege.too_many", "At most %s sieges at once.", "Como mucho %s asedios a la vez."),
    ("siege.not_front", "Besiege the town's edge first.", "Asedia primero el borde de la ciudad."),
    ("siege.defenders_offline", "At least %s defenders must be online.", "Debe haber al menos %s defensores conectados."),
    ("siege.started", "The siege of %s begins!", "¡Comienza el asedio de %s!"),
    ("core_hit", "Core hit %s/%s", "Núcleo golpeado %s/%s"),
    ("npc.gift_taken", "Gift accepted (opinion %s).", "Regalo aceptado (opinión %s)."),
    ("npc.refuses", "They refuse (opinion %s).", "Se niegan (opinión %s)."), ("npc.alliance_accepted", "%s accepts the alliance.", "%s acepta la alianza."),
    ("bypass_on", "Protection bypass on.", "Ignorar protecciones activado."), ("bypass_off", "Protection bypass off.", "Ignorar protecciones desactivado."),
    ("npc_created", "NPC town %s created.", "Ciudad NPC %s creada."), ("wars_ended", "%s wars ended.", "%s guerras terminadas."),
]
for m, en, es in msgs:
    k(f"nationsandwar.msg.{m}", en, es)

# management GUI and envoy screen (client/screen)
gui = [
    ("loading", "Asking the court for news…", "Pidiendo noticias a la corte…"), ("needs", "Needs: %s", "Requiere: %s"),
    ("tab.overview", "Overview", "Resumen"), ("tab.town", "Town", "Ciudad"),
    ("tab.provinces", "Provinces", "Provincias"), ("tab.nation", "Nation", "Nación"),
    ("tab.diplomacy", "Diplomacy", "Diplomacia"), ("tab.war", "War", "Guerra"), ("tab.map", "Map", "Mapa"),
    ("no_town", "You belong to no town", "No perteneces a ninguna ciudad"), ("no_nation", "Your town is in no nation", "Tu ciudad no está en ninguna nación"),
    ("no_alliance", "No alliance", "Sin alianza"), ("no_town_hint", "Found one below or accept an invitation.", "Funda una abajo o acepta una invitación."),
    ("no_nation_hint", "Found one below, or wait for an invitation.", "Funda una abajo o espera una invitación."),
    ("no_nation_town", "Join a town first.", "Únete antes a una ciudad."),
    ("no_alliance_hint", "Nations band together in Diplomacy.", "Las naciones se alían en Diplomacia."),
    ("online_of", "%s (%s on)", "%s (%s conect.)"), ("government", "Government", "Gobierno"),
    ("invites", "Invitations", "Invitaciones"), ("invite_town", "Town: %s", "Ciudad: %s"), ("invite_nation", "Nation: %s", "Nación: %s"),
    ("invite_alliance", "Alliance: %s", "Alianza: %s"), ("no_invites", "Nothing waiting for you.", "No tienes nada pendiente."),
    ("accept", "Accept", "Aceptar"), ("leave_first", "Leave your town first", "Deja antes tu ciudad"), ("mayor_only", "Only the mayor", "Solo el alcalde"),
    ("leader_only", "Only the leader", "Solo el líder"), ("found_town", "Found a town", "Fundar una ciudad"),
    ("found_town_hint", "Costs %s ◆ from your inventory. The Town Core appears at your feet.", "Cuesta %s ◆ de tu inventario. El Núcleo aparece a tus pies."),
    ("found_nation", "Found a nation", "Fundar una nación"),
    ("found_nation_hint", "Costs %s ◆ from the town bank. Your town becomes its capital.", "Cuesta %s ◆ del banco de la ciudad. Tu ciudad será la capital."),
    ("town_name", "Town name", "Nombre de la ciudad"), ("nation_name", "Nation name", "Nombre de la nación"),
    ("type_name", "Type a name first", "Escribe antes un nombre"), ("type_amount", "Type an amount first", "Escribe antes una cantidad"),
    ("wars_title", "Wars", "Guerras"), ("versus", "vs %s", "contra %s"), ("at_peace", "At peace", "En paz"),
    ("outposts", "Outposts", "Avanzadas"), ("next_claim", "Next chunk", "Siguiente chunk"), ("amount", "Amount", "Cantidad"),
    ("deposit", "Deposit", "Ingresar"), ("withdraw", "Withdraw", "Retirar"), ("identity", "Name and colour", "Nombre y color"),
    ("new_name", "New name", "Nombre nuevo"), ("rename", "Rename", "Renombrar"),
    ("go_spawn", "Teleport", "Viajar"), ("delete_town", "Delete town", "Borrar ciudad"), ("delete_nation", "Delete nation", "Borrar nación"),
    ("leave_town", "Leave town", "Dejar la ciudad"),
    ("leave_nation", "Leave nation", "Dejar la nación"), ("flags", "Town flags", "Ajustes de la ciudad"),
    ("open", "Open: anyone may join", "Abierta: cualquiera puede unirse"), ("closed", "Closed: invitation only", "Cerrada: solo con invitación"),
    ("chunk_perms", "Who may do what", "Quién puede hacer qué"),
    ("chunk_perms_hint", "Town-wide defaults; single chunks may override them.", "Valores de toda la ciudad; cada chunk puede cambiarlos."),
    ("action.build", "Build", "Construir"), ("action.destroy", "Break", "Romper"), ("action.switch", "Use", "Usar"),
    ("action.container", "Chests", "Cofres"), ("action.item_use", "Items", "Objetos"),
    ("group.resident", "Residents", "Residentes"), ("group.nation", "Nation", "Nación"), ("group.ally", "Allies", "Aliados"), ("group.outsider", "Outsiders", "Forasteros"),
    ("player_name", "Player name", "Nombre del jugador"), ("invite", "Invite", "Invitar"), ("invited", "Invited", "Invitado"),
    ("online", "Online", "En línea"), ("offline", "Offline", "Desconectado"), ("kick", "Kick", "Expulsar"),
    ("province_name", "New province name", "Nueva provincia"), ("no_provinces", "No provinces yet. Create one with a free town as its capital.",
     "Aún no hay provincias. Crea una con una ciudad libre como capital."), ("no_governor", "No governor", "Sin gobernador"),
    ("create_cost", "Create %s ◆", "Crear %s ◆"), ("delete", "Delete", "Borrar"), ("make_capital", "Make it the capital", "Hacerla capital"),
    ("governor", "Governor", "Gobernador"), ("clear", "Clear", "Quitar"),
    ("overlord", "Overlord", "Señor"),
    ("gov_hint", "Governments only change names and titles.", "El gobierno solo cambia nombres y títulos."),
    ("alliance_name", "Alliance name", "Nombre de la alianza"), ("towns_citizens", "%s towns, %s citizens", "%s ciudades, %s ciudadanos"),
    ("relation.self", "Us", "Nosotros"), ("relation.ally", "Ally", "Aliado"), ("relation.enemy", "Enemy", "Enemigo"), ("relation.neutral", "Neutral", "Neutral"),
    ("opinion", "Their opinion of us: %s", "Su opinión de nosotros: %s"), ("at_war", "You are at war with them", "Estáis en guerra"),
    ("is_ally", "Already allies", "Ya sois aliados"), ("war", "War", "Guerra"),
    ("declare_war_cost", "Declare war: %s ◆ from the nation bank", "Declarar la guerra: %s ◆ del banco de la nación"),
    ("leave_alliance", "Leave alliance", "Dejar la alianza"), ("create_alliance", "Create an alliance", "Crear una alianza"),
    ("create_alliance_hint", "Costs %s ◆. Then invite nations from the list.", "Cuesta %s ◆. Después invita naciones de la lista."),
    ("create", "Create", "Crear"),
    ("phase.preparation", "Preparation", "Preparación"), ("phase.active", "Active", "Activa"), ("phase.treaty", "Treaty", "Tratado"), ("phase.ended", "Ended", "Terminada"),
    ("attackers", "Attackers", "Atacantes"), ("defenders", "Defenders", "Defensores"), ("target", "first to %s", "gana quien llegue a %s"),
    ("score_of", "%s of %s points", "%s de %s puntos"), ("sides", "Sides", "Bandos"), ("sieges", "Sieges", "Asedios"),
    ("siege_tip", "%s besieges %s", "%s asedia a %s"), ("no_sieges", "No sieges", "Sin asedios"), ("fallen", "Fallen towns", "Ciudades caídas"),
    ("core_open", "Its Core can be broken", "Su Núcleo puede romperse"), ("none", "None", "Ninguna"),
    ("treaty", "Treaty", "Tratado"), ("outcome.white_peace", "white peace", "paz blanca"), ("outcome.won", "%s won", "ha ganado %s"),
    ("annex", "Annex:", "Anexionar:"), ("no_annex_capital", "A capital cannot be annexed", "Una capital no se puede anexionar"),
    ("winner_only", "Only the winner dictates the terms", "Solo el vencedor dicta los términos"),
    ("tribute", "Tribute ◆", "Tributo ◆"), ("vassal_days", "Days", "Días"), ("vassal_days_max", "Vassal days (max %s)", "Días de vasallaje (máx. %s)"),
    ("set_terms", "Set terms", "Fijar términos"), ("sign_now", "Sign now", "Firmar ya"),
    ("current_terms", "Current terms: annex %s towns, %s ◆ tribute, %s vassal days.", "Términos actuales: anexionar %s ciudades, %s ◆ de tributo, %s días de vasallaje."),
    ("war_leader_only", "Only the nations leading the war", "Solo las naciones que dirigen la guerra"),
    ("accept_peace", "Accept white peace", "Aceptar la paz blanca"), ("offer_peace", "Offer white peace", "Ofrecer la paz blanca"),
    ("peace_offered", "White peace offered", "Paz blanca ofrecida"), ("surrender", "Surrender", "Rendirse"),
    ("wilderness", "Wilderness", "Tierra salvaje"), ("occupied_suffix", " — occupied", " — ocupado"),
    ("click_claim", "Click or drag to claim (%s ◆)", "Pulsa o arrastra para reclamar (%s ◆)"),
    ("click_outpost", "Click to claim an outpost (%s ◆)", "Pulsa para reclamar una avanzada (%s ◆)"),
    ("click_unclaim", "Click or drag to release", "Pulsa o arrastra para liberar"),
    ("outpost_mode", "Outpost %s/%s (%s ◆)", "Avanzada %s/%s (%s ◆)"), ("centre", "Centre", "Centrar"), ("centred", "Already centred", "Ya está centrado"),
    ("max_zoom", "Zoom limit", "Límite de zoom"), ("besieged", "Besieged", "Asediado"), ("occupied", "Occupied", "Ocupado"),
    ("map_hint", "Right-drag: move · wheel: zoom", "Arrastrar con derecho: mover · rueda: zoom"),
    ("vacant", "Vacant", "Vacante"), ("drag_person", "Drag onto another rank to give it to them", "Arrástralo a otro cargo para dárselo"),
    ("n_towns", "Towns: %s", "Ciudades: %s"), ("all_perms", "Every permission", "Todos los permisos"), ("n_perms", "Rights: %s", "Permisos: %s"), ("my_town", "My town", "Mi ciudad"),
    ("province_capital", "The new province's capital (a town in no province)", "La capital de la nueva provincia (una ciudad sin provincia)"),
    ("fit", "Fit", "Ajustar"),
    ("tree_hint", "Drag: pan · wheel: zoom · drag a card onto another to move it · drag a person to promote them",
     "Arrastra: mover · rueda: zoom · arrastra un cargo sobre otro para moverlo · arrastra a una persona para ascenderla"),
    ("page.perms", "Rights", "Permisos"),
    ("name", "Name", "Nombre"), ("colour", "Colour", "Color"),
    ("add", "Add", "Añadir"), ("delete_rank", "Delete rank", "Borrar cargo"),
    ("cannot_manage", "You cannot manage this rank", "No puedes gestionar este cargo"),
    ("governor_hint_short", "Named per province", "Se nombra por provincia"),
    ("leader_all", "The leader holds every permission", "El líder tiene todos los permisos"),
    ("not_yours", "You can only grant permissions you hold", "Solo puedes conceder permisos que tienes"),
    ("holders", "Holders (%s)", "Titulares (%s)"), ("leader_fixed", "The leader changes only by handing over the town or nation",
     "El líder solo cambia al ceder la ciudad o la nación"), ("give_rank", "Give this rank to:", "Dar este cargo a:"),
    ("envoy_of", "Envoy of", "Enviado de"), ("their_opinion", "Their opinion of us", "Su opinión de nosotros"),
    ("mood.warm", "They count us among their friends.", "Nos cuentan entre sus amigos."), ("mood.friendly", "They are well disposed.", "Están bien dispuestos."),
    ("mood.wary", "They watch us warily.", "Nos observan con recelo."), ("mood.cold", "They are cold towards us.", "Se muestran fríos con nosotros."),
    ("mood.hostile", "They openly despise us.", "Nos desprecian abiertamente."), ("gift", "Gift %s emeralds", "Regalar %s esmeraldas"),
    ("propose_alliance", "Propose an alliance", "Proponer una alianza"), ("ask_peace", "Ask for peace", "Pedir la paz"),
    ("not_at_war", "You are not at war with them", "No estáis en guerra"),
]
for g_, en, es in gui:
    k(f"nationsandwar.gui.{g_}", en, es)

# --- API v0.2: nations run by other mods, reserved land, vetoed wars ----------------------------------------------------
for key_, en, es in [
    ("managed", "That nation is run by another mod.", "Esa nación la gestiona otro mod."),
    ("claim_reserved", "This land is reserved.", "Esta tierra está reservada."),
    ("war.managed", "That nation cannot be declared on.", "No se puede declarar la guerra a esa nación."),
    ("war.vetoed", "Something forbids this war.", "Algo prohíbe esta guerra."),
    ("siege.managed", "That ground cannot be besieged.", "Ese terreno no se puede asediar."),
]:
    k(f"nationsandwar.msg.{key_}", en, es)
k("nationsandwar.managed.description", "This nation is run by another mod. Its people and ranks are shown there.",
  "Esta nación la gestiona otro mod. Su gente y sus rangos se muestran allí.")
k("nationsandwar.managed.open", "Open", "Abrir")
k("nationsandwar.managed.cannot_open", "You cannot open it right now.", "Ahora mismo no puedes abrirlo.")
k("nationsandwar.managed.badge", "Managed", "Gestionada")
k("nationsandwar.managed.inspect", "Click: inspect", "Clic: inspeccionar")
k("nationsandwar.notice.badge.managed", "MANAGED", "GESTIONADA")

# --- v0.3 GUI: the Atlas and modals (client/screen) ----------------------------------------------------------------------
for g_, en, es in [
    ("tab.atlas", "Atlas", "Atlas"), ("alliance", "Alliance", "Alianza"), ("alliances", "Alliances", "Alianzas"),
    ("independent_nations", "Independent nations", "Naciones independientes"), ("free_towns", "Free towns", "Ciudades libres"),
    ("search", "Search…", "Buscar…"), ("show_list", "Show the list", "Mostrar la lista"), ("hide_list", "Hide the list", "Ocultar la lista"),
    ("no_match", "Nothing matches", "Nada coincide"), ("nothing_to_pick", "Nothing to choose from", "No hay nada que elegir"),
    ("atlas_empty", "No nations or towns yet.", "Aún no hay naciones ni ciudades."), ("gone", "It no longer exists.", "Ya no existe."),
    ("read_only", "Read only", "Solo lectura"), ("npc", "NPC", "PNJ"), ("nobody", "nobody", "nadie"),
    ("tree_hint_read", "Drag: pan · wheel: zoom · click a card to pick it", "Arrastra: mover · rueda: zoom · pulsa una tarjeta para elegirla"),
    ("open_nation", "Open its nation", "Abrir su nación"), ("open_provinces", "Provinces tab", "Pestaña de provincias"),
    ("open_atlas", "Open the Atlas", "Abrir el Atlas"), ("n_ranks", "%s ranks", "%s cargos"),
    ("residents_claims", "%s residents · %s chunks", "%s residentes · %s chunks"), ("n_residents", "%s residents", "%s residentes"),
    ("edit_rank", "Edit rank", "Editar cargo"), ("edit_rank_of", "Edit rank: %s", "Editar cargo: %s"), ("people", "People", "Personas"),
    ("people_of", "People: %s", "Personas: %s"), ("perms_of", "Rights: %s", "Permisos: %s"),
    ("perms_hint", "Changes apply at once. You can only grant permissions you hold.",
     "Los cambios se aplican al momento. Solo puedes conceder permisos que tienes."),
    ("new_rank", "New sub-rank", "Nuevo subcargo"), ("new_rank_under", "New rank under %s", "Nuevo cargo bajo %s"),
    ("confirm_delete_rank", "Delete the rank %s? Its holders lose it and its sub-ranks move up.",
     "¿Borrar el cargo %s? Quienes lo tienen lo pierden y sus subcargos suben un nivel."),
    ("save", "Save", "Guardar"), ("cancel", "Cancel", "Cancelar"), ("close", "Close", "Cerrar"), ("current", "current", "actual"),
    ("pick_one", "Pick one from the list", "Elige uno de la lista"), ("appoint", "Appoint", "Nombrar"),
    ("governor_of", "Governor of %s", "Gobernador de %s"),
    ("pick_governor_hint", "Pick the citizen who will govern this province, or nobody.", "Elige al ciudadano que gobernará esta provincia, o a nadie."),
    ("province_identity", "Province: %s", "Provincia: %s"), ("create_province", "New province", "Nueva provincia"),
    ("create_province_hint", "Name it and pick its capital among the towns in no province. Costs %s ◆.",
     "Ponle nombre y elige su capital entre las ciudades sin provincia. Cuesta %s ◆."),
    ("add_town", "Add town", "Añadir ciudad"), ("add_town_to", "Add a town to %s", "Añadir una ciudad a %s"),
    ("add_town_hint", "Only the nation's towns that are in no province can join.", "Solo pueden unirse las ciudades de la nación que no están en ninguna provincia."),
    ("delete_province", "Delete province", "Borrar provincia"),
    ("confirm_delete_province", "Delete the province %s? Its towns stay in the nation, in no province.",
     "¿Borrar la provincia %s? Sus ciudades siguen en la nación, sin provincia."),
    ("remove_town", "Remove town", "Quitar ciudad"), ("confirm_remove_town", "Take %s out of %s?", "¿Sacar %s de %s?"), ("remove", "Remove", "Quitar"),
    ("no_province_picked", "Pick a province first", "Elige antes una provincia"),
    ("colour_picked", "Own colour", "Color propio"), ("colour_default", "Nation's shade", "Tono de la nación"),
    ("bank", "Bank", "Banco"), ("town_bank", "Town bank", "Banco de la ciudad"), ("nation_bank", "Nation bank", "Banco de la nación"),
    ("bank_hint", "Deposits come from your inventory; withdrawals go to it.", "Los ingresos salen de tu inventario y las retiradas van a él."),
    ("spawn", "Spawn", "Punto de aparición"), ("spawn_at", "Town spawn", "Punto de la ciudad"),
    ("spawn_hint", "Set it here to move the spawn to where you stand, inside the town. Teleport takes you there.",
     "Fíjalo aquí para mover el punto a donde estás, dentro de la ciudad. Viajar te lleva allí."),
    ("set_spawn_here", "Set it here", "Fijarlo aquí"), ("settings", "Settings", "Ajustes"),
    ("settings_hint", "Change them in Settings.", "Cámbialos en Ajustes."), ("membership", "Who may join", "Quién puede unirse"),
    ("on", "on", "sí"), ("off", "off", "no"),
    ("invite_player", "Invite a player", "Invitar a un jugador"),
    ("invite_player_hint", "They get an invitation to join the town.", "Recibirá una invitación para unirse a la ciudad."),
    ("kick_player", "Expel resident", "Expulsar residente"), ("confirm_kick_player", "Expel %s from %s?", "¿Expulsar a %s de %s?"),
    ("confirm_delete_town", "Delete %s for good? Its land, ranks and bank are lost.", "¿Borrar %s para siempre? Se pierden su terreno, sus cargos y su banco."),
    ("confirm_leave_town", "Leave %s? You will need an invitation to come back.", "¿Dejar %s? Necesitarás una invitación para volver."),
    ("leave", "Leave", "Salir"), ("found_cost", "Found (%s ◆)", "Fundar (%s ◆)"),
    ("no_town_bar", "Found a town from the bar below, or accept an invitation.", "Funda una ciudad desde la barra de abajo o acepta una invitación."),
    ("no_nation_bar", "Your town is in no nation. Found one from the bar below.", "Tu ciudad no está en ninguna nación. Funda una desde la barra de abajo."),
    ("change", "Change", "Cambiar"), ("already_current", "That is already the government", "Ese ya es el gobierno"),
    ("invite_town", "Invite town", "Invitar ciudad"),
    ("invite_town_hint", "A free town gets an invitation to join the nation; its mayor accepts.",
     "Una ciudad libre recibe una invitación para unirse a la nación; su alcalde la acepta."),
    ("mayor_is", "mayor %s", "alcalde %s"), ("kick_town", "Expel town", "Expulsar ciudad"),
    ("confirm_kick_town", "Expel %s from %s?", "¿Expulsar %s de %s?"),
    ("confirm_delete_nation", "Delete %s for good? Its towns become free and its bank is lost.",
     "¿Borrar %s para siempre? Sus ciudades quedan libres y se pierde su banco."),
    ("confirm_leave_nation", "Take your town out of %s?", "¿Sacar tu ciudad de %s?"),
    ("in_alliance", "Already in an alliance", "Ya estáis en una alianza"), ("invite_nation", "Invite nation", "Invitar nación"),
    ("invite_nation_hint", "The nation gets an invitation to join your alliance.", "La nación recibe una invitación para unirse a vuestra alianza."),
    ("confirm_leave_alliance", "Leave %s?", "¿Dejar %s?"), ("see_ranks", "See its ranks", "Ver sus cargos"),
    ("expel_alliance", "Expel from alliance", "Expulsar de la alianza"), ("confirm_expel_alliance", "Expel %s from %s?", "¿Expulsar a %s de %s?"),
    ("ally_invite_long", "Invite to alliance", "Invitar a la alianza"), ("declare_war", "Declare war", "Declarar la guerra"),
    ("confirm_declare_war", "Declare war on %s? It costs %s ◆ from the nation bank, and a preparation phase starts for both sides.",
     "¿Declarar la guerra a %s? Cuesta %s ◆ del banco de la nación y empieza una fase de preparación para ambos bandos."),
    ("declare_war_cost_short", "War (%s ◆)", "Guerra (%s ◆)"),
    ("no_alliance_bar", "Create one from the bar below, or accept an invitation.", "Crea una desde la barra de abajo o acepta una invitación."),
    ("pick_nation_hint", "Pick a nation on the left to deal with it.", "Elige una nación a la izquierda para tratar con ella."),
    ("edit_terms", "Edit terms", "Editar términos"), ("treaty_terms", "Treaty terms", "Términos del tratado"),
    ("annexed_towns", "Towns to annex", "Ciudades a anexionar"),
    ("treaty_hint", "The winner sets the terms; the defaults apply when the window ends.",
     "El vencedor fija los términos; al cerrarse el plazo se aplican los de por defecto."),
    ("confirm_sign", "Sign the treaty now: annex %s towns, %s ◆ tribute, %s vassal days?",
     "¿Firmar ya el tratado: anexionar %s ciudades, %s ◆ de tributo, %s días de vasallaje?"),
    ("confirm_offer_peace", "Offer white peace to %s? The war ends if they accept.", "¿Ofrecer la paz blanca a %s? La guerra termina si la aceptan."),
    ("confirm_accept_peace", "Accept the white peace %s offers? The war ends now.", "¿Aceptar la paz blanca que ofrece %s? La guerra termina ya."),
    ("confirm_surrender", "Surrender to %s? They win the war and dictate the treaty.", "¿Rendirse ante %s? Ganan la guerra y dictan el tratado."),
]:
    k(f"nationsandwar.gui.{g_}", en, es)

# --- v0.3 map layers and labels (client/map, compat) ---------------------------------------------------------------------
k("key.nationsandwar.map_layer", "Cycle map layer", "Cambiar capa del mapa")
k("nationsandwar.map.layer_title", "Map layer", "Capa del mapa")
k("nationsandwar.map.alt_atlas", "Alt+click: open in the Atlas", "Alt+clic: abrir en el Atlas")
for key_, en, es in [("nations", "Nations", "Naciones"), ("provinces", "Provinces", "Provincias"), ("towns", "Towns", "Ciudades"),
                     ("alliances", "Alliances", "Alianzas")]:
    k(f"nationsandwar.map.layer.{key_}", en, es)

# --- v0.4 war AI: liberation (war/, npc/, client/notice) ---------------------------------------------------------------
for key_, en, es in [
    ("liberation_started", "A liberation begins", "Comienza una liberación"),
    ("liberation_failed", "The liberation is repelled", "La liberación es repelida"),
    ("chunk_liberated", "Land liberated", "Terreno liberado"),
    ("core_secured", "The town core is safe again", "El núcleo de la ciudad vuelve a estar a salvo"),
    ("liberation_of", "Liberating %s", "Liberando %s"),
]:
    k(f"nationsandwar.notice.{key_}", en, es)
k("nationsandwar.msg.siege.liberation_started", "You fight to free %s!", "¡Luchas por liberar %s!")

# --- v0.4 courts: the characters of NPC nations and towns (npc/NpcCourt) ---------------------------------------------------
k("nationsandwar.notice.court_new", "New %s of %s", "Nombramiento: %s de %s")
k("nationsandwar.gui.npc_character", "NPC character", "Personaje NPC")

def write(name, i):
    path = os.path.join(OUT, name)
    os.makedirs(OUT, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump({key: v[i] for key, v in sorted(T.items())}, f, ensure_ascii=False, indent=2)
        f.write("\n")

if __name__ == "__main__":
    import sys
    extra = sys.argv[1:]  # optional: other tables to merge (key -> (en, es)) as JSON files
    for e in extra:
        for key, v in json.load(open(e, encoding="utf-8")).items():
            T[key] = tuple(v)
    write("en_us.json", 0)
    write("es_es.json", 1)
    print(len(T), "keys")
