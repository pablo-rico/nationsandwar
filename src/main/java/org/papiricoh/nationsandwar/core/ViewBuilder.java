package org.papiricoh.nationsandwar.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.papiricoh.nationsandwar.NWConfig;
import org.papiricoh.nationsandwar.data.NWSavedData;
import org.papiricoh.nationsandwar.model.Alliance;
import org.papiricoh.nationsandwar.model.Flag;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Province;
import org.papiricoh.nationsandwar.model.Role;
import org.papiricoh.nationsandwar.model.RoleTree;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.view.PoliticsView;
import org.papiricoh.nationsandwar.view.SiegeBrief;
import org.papiricoh.nationsandwar.war.War;
import org.papiricoh.nationsandwar.war.WarService;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Builds the {@link PoliticsView} a player's GUI shows. */
public final class ViewBuilder {
    private ViewBuilder() {
    }

    public static PoliticsView build(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        NWSavedData data = NW.data(server);
        Politics pol = data.politics;
        UUID me = player.getUUID();
        Town town = pol.townOf(me);
        Nation nation = pol.nationOf(town);
        Alliance alliance = nation == null || nation.alliance == null ? null : pol.alliances.get(nation.alliance);

        List<PoliticsView.NationSummary> nations = new ArrayList<>();
        for (Nation n : pol.nations.values()) {
            String rel = nation == null ? "neutral" : n == nation ? "self" : WarService.atWar(data, nation.id, n.id) ? "enemy"
                    : pol.allied(nation.id, n.id) ? "ally" : "neutral";
            nations.add(new PoliticsView.NationSummary(n.id, n.name, n.color, n.government.id(), n.towns.size(), pol.citizens(n).size(), n.npc,
                    n.alliance, rel, nation == null ? 0 : n.opinionOf(nation.id), NW.name(server, n.leader())));
        }
        List<PoliticsView.TownSummary> towns = new ArrayList<>();
        for (Town t : pol.towns.values()) {
            towns.add(new PoliticsView.TownSummary(t.id, t.name, t.color, t.nation, t.province, t.residents.size(), t.claims.size(), t.npc,
                    NW.name(server, t.mayor()), t.level().name()));
        }
        List<PoliticsView.AllianceView> alliances = new ArrayList<>();
        for (Alliance a : pol.alliances.values()) alliances.add(alliance(a));

        List<PoliticsView.WarView> wars = new ArrayList<>();
        for (War w : data.wars.values()) {
            if (w.phase == War.Phase.ENDED) continue;
            if (nation != null && !w.involves(nation.id)) continue;
            wars.add(war(server, w));
        }

        List<UUID> townInvites = new ArrayList<>();
        for (Town t : pol.towns.values()) if (t.invites.contains(me)) townInvites.add(t.id);
        List<UUID> nationInvites = new ArrayList<>();
        if (town != null) for (Nation n : pol.nations.values()) if (n.invites.contains(town.id)) nationInvites.add(n.id);
        List<UUID> allianceInvites = new ArrayList<>();
        if (nation != null) for (Alliance a : pol.alliances.values()) if (a.invites.contains(nation.id)) allianceInvites.add(a.id);

        var r = pol.rules;
        PoliticsView.Prices prices = new PoliticsView.Prices(r.townCost(), r.nationCost(), r.provinceCost(), r.allianceCost(),
                NWConfig.get(NWConfig.WAR_COST), r.outpostCost(), NWConfig.get(NWConfig.MAX_VASSAL_DAYS));

        return new PoliticsView(me, server.overworld().getGameTime(), town == null ? null : town(server, pol, town, me),
                nation == null ? null : nation(server, pol, nation, me), alliance == null ? null : alliance(alliance), nations, towns, alliances,
                wars, townInvites, nationInvites, allianceInvites, prices);
    }

    private static PoliticsView.TownView town(MinecraftServer server, Politics pol, Town t, UUID me) {
        List<PoliticsView.Member> residents = new ArrayList<>();
        for (UUID r : t.residents) {
            Role role = t.roles.roleOf(r);
            residents.add(new PoliticsView.Member(r, NW.name(server, r), role == null ? -1 : role.id, NW.online(server, r)));
        }
        int flags = 0;
        for (Flag f : Flag.values()) if (t.flags.getOrDefault(f, f.defaultOn)) flags |= 1 << f.ordinal();
        EnumSet<Perm> mine = EnumSet.noneOf(Perm.class);
        for (Perm p : Perm.values()) if (pol.can(me, t, p)) mine.add(p);
        List<long[]> cs = new ArrayList<>();
        t.chunkSettings.forEach((chunk, s) -> {
            long set = 0, on = 0;
            for (var e : s.flags.entrySet()) {
                set |= 1L << e.getKey().ordinal();
                if (e.getValue()) on |= 1L << e.getKey().ordinal();
            }
            cs.add(new long[]{chunk, set, on, s.perms == null ? -1 : s.perms.bits()});
        });
        return new PoliticsView.TownView(t.id, t.name, t.color, t.dimension, t.homeChunk, t.spawn, t.level().name(), t.claims.size(),
                pol.maxClaims(t), t.outposts.size(), pol.rules.maxOutposts(), t.bank, flags, t.perms.bits(), t.open, t.npc, t.nation, t.province,
                residents, roles(t.roles), Perm.mask(mine), pol.rules.nextClaimCost(t.claims.size()), cs);
    }

    private static PoliticsView.NationView nation(MinecraftServer server, Politics pol, Nation n, UUID me) {
        List<PoliticsView.ProvinceView> provinces = new ArrayList<>();
        for (UUID pid : n.provinces) {
            Province p = pol.provinces.get(pid);
            if (p != null) provinces.add(new PoliticsView.ProvinceView(p.id, p.name, p.governor, NW.name(server, p.governor), p.capital, List.copyOf(p.towns)));
        }
        List<PoliticsView.Member> citizens = new ArrayList<>();
        for (UUID c : pol.citizens(n)) {
            Role role = n.roles.roleOf(c);
            citizens.add(new PoliticsView.Member(c, NW.name(server, c), role == null ? -1 : role.id, NW.online(server, c)));
        }
        EnumSet<Perm> mine = pol.citizens(n).contains(me) ? n.roles.permsOf(me) : EnumSet.noneOf(Perm.class);
        return new PoliticsView.NationView(n.id, n.name, n.color, n.government.id(), n.capital, n.bank, n.alliance, n.npc, List.copyOf(n.towns),
                provinces, citizens, roles(n.roles), Perm.mask(mine), n.overlord, n.vassalUntil, List.copyOf(n.invites));
    }

    private static List<PoliticsView.RoleView> roles(RoleTree tree) {
        List<PoliticsView.RoleView> out = new ArrayList<>();
        for (Role r : tree.all()) {
            out.add(new PoliticsView.RoleView(r.id, r.kind.name(), r.name, r.color, r.parent, r.kind == Role.Kind.LEADER ? Perm.mask(EnumSet.allOf(Perm.class))
                    : Perm.mask(r.perms), List.copyOf(r.holders)));
        }
        return out;
    }

    private static PoliticsView.AllianceView alliance(Alliance a) {
        return new PoliticsView.AllianceView(a.id, a.name, a.color, a.leader, List.copyOf(a.members), List.copyOf(a.invites));
    }

    static PoliticsView.WarView war(MinecraftServer server, War w) {
        List<SiegeBrief> sieges = WarService.siegeBriefs(server, w);
        String peaceBy = w.peaceOfferedByAttackers == null ? "" : w.peaceOfferedByAttackers ? "attackers" : "defenders";
        return new PoliticsView.WarView(w.id, w.attacker, w.defender, List.copyOf(w.attackers), List.copyOf(w.defenders), w.phase.name(),
                w.activeAt, w.deadline, w.treatyUntil, w.scoreAttackers, w.scoreDefenders, NWConfig.get(NWConfig.TARGET_SCORE), sieges,
                List.copyOf(w.coreOpen), List.copyOf(w.fallen), w.outcome.name(), peaceBy,
                w.terms == null ? List.of() : List.copyOf(w.terms.annex), w.terms == null ? 0 : w.terms.tribute, w.terms == null ? 0 : w.terms.vassalDays);
    }
}
