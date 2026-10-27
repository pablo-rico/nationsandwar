package org.papiricoh.nationsandwar.view;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.papiricoh.nationsandwar.model.Nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Everything the management GUI shows one player: their town, nation and alliance in detail (members with roles, role
 * trees, provinces, banks, flags), every nation and town in summary, the wars they are in, their pending invites, the
 * prices, and the game time. Built by {@code core.ViewBuilder}, sent as {@code network.ViewPayload}, read on the client into
 * {@code client.ClientPolitics#view()}.
 *
 * <p>Role names starting with {@code @} are lang keys (show {@code Component.translatable(name.substring(1))}).
 * Perm masks are bits per {@code model.Perm} ordinal; flag masks per {@code model.Flag} ordinal; chunk-perm bits as
 * {@code model.ChunkPerms}.
 */
public record PoliticsView(UUID me, long now, TownView town, NationView nation, AllianceView alliance, List<NationSummary> nations,
                           List<TownSummary> towns, List<AllianceView> alliances, List<WarView> wars, List<UUID> townInvites,
                           List<UUID> nationInvites, List<UUID> allianceInvites, Prices prices) {

    /** {@code npc}: a character of an NPC court (never a real player); {@code skin} its guard skin (0..3). */
    public record Member(UUID id, String name, int role, boolean online, boolean npc, int skin) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            t.putInt("role", role);
            t.putBoolean("online", online);
            t.putBoolean("npc", npc);
            t.putInt("skin", skin);
            return t;
        }

        static Member read(CompoundTag t) {
            return new Member(t.getUUID("id"), t.getString("name"), t.getInt("role"), t.getBoolean("online"), t.getBoolean("npc"), t.getInt("skin"));
        }
    }

    /** {@code kind}: "LEADER", "GOVERNOR" or "NORMAL". {@code parent} −1 for the root. */
    public record RoleView(int id, String kind, String name, int color, int parent, int perms, List<UUID> holders) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id);
            t.putString("kind", kind);
            t.putString("name", name);
            t.putInt("color", color);
            t.putInt("parent", parent);
            t.putInt("perms", perms);
            t.put("holders", Nbt.uuids(holders));
            return t;
        }

        static RoleView read(CompoundTag t) {
            return new RoleView(t.getInt("id"), t.getString("kind"), t.getString("name"), t.getInt("color"), t.getInt("parent"),
                    t.getInt("perms"), List.copyOf(Nbt.uuids(t, "holders")));
        }
    }

    /**
     * {@code level}: a {@code model.TownLevel} name. {@code myPerms}: what the viewer may do for this town (town roles,
     * nation roles and governorship together). {@code spawn}: a {@code BlockPos.asLong}. {@code chunkSettings}: chunk key →
     * (flag overrides as two masks "set"/"on", perms bits or −1).
     */
    public record TownView(UUID id, String name, int color, String dimension, long homeChunk, long spawn, String level, int claims,
                           int maxClaims, int outposts, int maxOutposts, int bank, int flags, int chunkPerms, boolean open, boolean npc,
                           UUID nation, UUID province, List<Member> residents, List<RoleView> roles, int myPerms, int nextClaimCost,
                           List<long[]> chunkSettings) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            t.putInt("color", color);
            t.putString("dim", dimension);
            t.putLong("home", homeChunk);
            t.putLong("spawn", spawn);
            t.putString("level", level);
            t.putInt("claims", claims);
            t.putInt("maxClaims", maxClaims);
            t.putInt("outposts", outposts);
            t.putInt("maxOutposts", maxOutposts);
            t.putInt("bank", bank);
            t.putInt("flags", flags);
            t.putInt("chunkPerms", chunkPerms);
            t.putBoolean("open", open);
            t.putBoolean("npc", npc);
            Nbt.putUuid(t, "nation", nation);
            Nbt.putUuid(t, "province", province);
            t.put("residents", list(residents, Member::write));
            t.put("roles", list(roles, RoleView::write));
            t.putInt("myPerms", myPerms);
            t.putInt("nextClaim", nextClaimCost);
            ListTag cs = new ListTag();
            for (long[] c : chunkSettings) cs.add(new net.minecraft.nbt.LongArrayTag(c));
            t.put("chunkSettings", cs);
            return t;
        }

        static TownView read(CompoundTag t) {
            List<long[]> cs = new ArrayList<>();
            for (Tag e : t.getList("chunkSettings", Tag.TAG_LONG_ARRAY)) cs.add(((net.minecraft.nbt.LongArrayTag) e).getAsLongArray());
            return new TownView(t.getUUID("id"), t.getString("name"), t.getInt("color"), t.getString("dim"), t.getLong("home"), t.getLong("spawn"),
                    t.getString("level"), t.getInt("claims"), t.getInt("maxClaims"), t.getInt("outposts"), t.getInt("maxOutposts"), t.getInt("bank"),
                    t.getInt("flags"), t.getInt("chunkPerms"), t.getBoolean("open"), t.getBoolean("npc"), Nbt.getUuid(t, "nation"),
                    Nbt.getUuid(t, "province"), readList(t, "residents", Member::read), readList(t, "roles", RoleView::read), t.getInt("myPerms"),
                    t.getInt("nextClaim"), cs);
        }
    }

    /** {@code color}: the colour maps show; {@code picked}: whether it was chosen (false: a shade of the nation's). */
    public record ProvinceView(UUID id, String name, int color, boolean picked, UUID governor, String governorName, UUID capital, List<UUID> towns) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            t.putInt("color", color);
            t.putBoolean("picked", picked);
            Nbt.putUuid(t, "governor", governor);
            t.putString("governorName", governorName);
            Nbt.putUuid(t, "capital", capital);
            t.put("towns", Nbt.uuids(towns));
            return t;
        }

        static ProvinceView read(CompoundTag t) {
            return new ProvinceView(t.getUUID("id"), t.getString("name"), t.getInt("color"), t.getBoolean("picked"), Nbt.getUuid(t, "governor"), t.getString("governorName"),
                    Nbt.getUuid(t, "capital"), List.copyOf(Nbt.uuids(t, "towns")));
        }
    }

    /** {@code citizens}' {@code role} is their nation role id (−1 none). {@code myPerms}: the viewer's nation perms. */
    public record NationView(UUID id, String name, int color, String government, UUID capital, int bank, UUID alliance, boolean npc,
                             List<UUID> towns, List<ProvinceView> provinces, List<Member> citizens, List<RoleView> roles, int myPerms,
                             UUID overlord, long vassalUntil, List<UUID> invitedTowns) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            t.putInt("color", color);
            t.putString("gov", government);
            Nbt.putUuid(t, "capital", capital);
            t.putInt("bank", bank);
            Nbt.putUuid(t, "alliance", alliance);
            t.putBoolean("npc", npc);
            t.put("towns", Nbt.uuids(towns));
            t.put("provinces", list(provinces, ProvinceView::write));
            t.put("citizens", list(citizens, Member::write));
            t.put("roles", list(roles, RoleView::write));
            t.putInt("myPerms", myPerms);
            Nbt.putUuid(t, "overlord", overlord);
            t.putLong("vassalUntil", vassalUntil);
            t.put("invited", Nbt.uuids(invitedTowns));
            return t;
        }

        static NationView read(CompoundTag t) {
            return new NationView(t.getUUID("id"), t.getString("name"), t.getInt("color"), t.getString("gov"), Nbt.getUuid(t, "capital"),
                    t.getInt("bank"), Nbt.getUuid(t, "alliance"), t.getBoolean("npc"), List.copyOf(Nbt.uuids(t, "towns")),
                    readList(t, "provinces", ProvinceView::read), readList(t, "citizens", Member::read), readList(t, "roles", RoleView::read),
                    t.getInt("myPerms"), Nbt.getUuid(t, "overlord"), t.getLong("vassalUntil"), List.copyOf(Nbt.uuids(t, "invited")));
        }
    }

    public record AllianceView(UUID id, String name, int color, UUID leader, List<UUID> members, List<UUID> invites) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            t.putInt("color", color);
            t.putUUID("leader", leader);
            t.put("members", Nbt.uuids(members));
            t.put("invites", Nbt.uuids(invites));
            return t;
        }

        static AllianceView read(CompoundTag t) {
            return new AllianceView(t.getUUID("id"), t.getString("name"), t.getInt("color"), t.getUUID("leader"),
                    List.copyOf(Nbt.uuids(t, "members")), List.copyOf(Nbt.uuids(t, "invites")));
        }
    }

    /** {@code relation}: "self", "ally", "enemy" (at war with the viewer's nation), "neutral". {@code opinion}: an NPC nation's of the viewer's nation. */
    public record NationSummary(UUID id, String name, int color, String government, int towns, int citizens, boolean npc, UUID alliance,
                                String relation, int opinion, String leader) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            t.putInt("color", color);
            t.putString("gov", government);
            t.putInt("towns", towns);
            t.putInt("citizens", citizens);
            t.putBoolean("npc", npc);
            Nbt.putUuid(t, "alliance", alliance);
            t.putString("relation", relation);
            t.putInt("opinion", opinion);
            t.putString("leader", leader);
            return t;
        }

        static NationSummary read(CompoundTag t) {
            return new NationSummary(t.getUUID("id"), t.getString("name"), t.getInt("color"), t.getString("gov"), t.getInt("towns"),
                    t.getInt("citizens"), t.getBoolean("npc"), Nbt.getUuid(t, "alliance"), t.getString("relation"), t.getInt("opinion"),
                    t.getString("leader"));
        }
    }

    public record TownSummary(UUID id, String name, int color, UUID nation, UUID province, int residents, int claims, boolean npc,
                              String mayor, String level) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            t.putInt("color", color);
            Nbt.putUuid(t, "nation", nation);
            Nbt.putUuid(t, "province", province);
            t.putInt("residents", residents);
            t.putInt("claims", claims);
            t.putBoolean("npc", npc);
            t.putString("mayor", mayor);
            t.putString("level", level);
            return t;
        }

        static TownSummary read(CompoundTag t) {
            return new TownSummary(t.getUUID("id"), t.getString("name"), t.getInt("color"), Nbt.getUuid(t, "nation"), Nbt.getUuid(t, "province"),
                    t.getInt("residents"), t.getInt("claims"), t.getBoolean("npc"), t.getString("mayor"), t.getString("level"));
        }
    }

    /**
     * A war the viewer's nation is in (or any live war if they have none). {@code phase}: PREPARATION, ACTIVE, TREATY, ENDED;
     * {@code outcome}: NONE, ATTACKERS, DEFENDERS, WHITE_PEACE. {@code peaceBy}: "attackers"/"defenders"/"" (white peace
     * offered). {@code annex}/{@code tribute}/{@code vassalDays}: the treaty terms during TREATY.
     */
    public record WarView(UUID id, UUID attacker, UUID defender, List<UUID> attackers, List<UUID> defenders, String phase, long activeAt,
                          long deadline, long treatyUntil, int scoreAttackers, int scoreDefenders, int target, List<SiegeBrief> sieges,
                          List<UUID> coreOpen, List<UUID> fallen, String outcome, String peaceBy, List<UUID> annex, int tribute, int vassalDays) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putUUID("attacker", attacker);
            t.putUUID("defender", defender);
            t.put("attackers", Nbt.uuids(attackers));
            t.put("defenders", Nbt.uuids(defenders));
            t.putString("phase", phase);
            t.putLong("activeAt", activeAt);
            t.putLong("deadline", deadline);
            t.putLong("treatyUntil", treatyUntil);
            t.putInt("scoreA", scoreAttackers);
            t.putInt("scoreD", scoreDefenders);
            t.putInt("target", target);
            t.put("sieges", list(sieges, SiegeBrief::write));
            t.put("coreOpen", Nbt.uuids(coreOpen));
            t.put("fallen", Nbt.uuids(fallen));
            t.putString("outcome", outcome);
            t.putString("peaceBy", peaceBy);
            t.put("annex", Nbt.uuids(annex));
            t.putInt("tribute", tribute);
            t.putInt("vassal", vassalDays);
            return t;
        }

        static WarView read(CompoundTag t) {
            return new WarView(t.getUUID("id"), t.getUUID("attacker"), t.getUUID("defender"), List.copyOf(Nbt.uuids(t, "attackers")),
                    List.copyOf(Nbt.uuids(t, "defenders")), t.getString("phase"), t.getLong("activeAt"), t.getLong("deadline"),
                    t.getLong("treatyUntil"), t.getInt("scoreA"), t.getInt("scoreD"), t.getInt("target"), readList(t, "sieges", SiegeBrief::read),
                    List.copyOf(Nbt.uuids(t, "coreOpen")), List.copyOf(Nbt.uuids(t, "fallen")), t.getString("outcome"), t.getString("peaceBy"),
                    List.copyOf(Nbt.uuids(t, "annex")), t.getInt("tribute"), t.getInt("vassal"));
        }
    }

    public record Prices(int town, int nation, int province, int alliance, int war, int outpost, int maxVassalDays) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putIntArray("p", new int[]{town, nation, province, alliance, war, outpost, maxVassalDays});
            return t;
        }

        static Prices read(CompoundTag t) {
            int[] p = t.getIntArray("p");
            if (p.length < 7) p = new int[7];
            return new Prices(p[0], p[1], p[2], p[3], p[4], p[5], p[6]);
        }
    }

    public CompoundTag write() {
        CompoundTag t = new CompoundTag();
        t.putUUID("me", me);
        t.putLong("now", now);
        if (town != null) t.put("town", town.write());
        if (nation != null) t.put("nation", nation.write());
        if (alliance != null) t.put("alliance", alliance.write());
        t.put("nations", list(nations, NationSummary::write));
        t.put("towns", list(towns, TownSummary::write));
        t.put("alliances", list(alliances, AllianceView::write));
        t.put("wars", list(wars, WarView::write));
        t.put("townInvites", Nbt.uuids(townInvites));
        t.put("nationInvites", Nbt.uuids(nationInvites));
        t.put("allianceInvites", Nbt.uuids(allianceInvites));
        t.put("prices", prices.write());
        return t;
    }

    public static PoliticsView read(CompoundTag t) {
        return new PoliticsView(t.getUUID("me"), t.getLong("now"),
                t.contains("town") ? TownView.read(t.getCompound("town")) : null,
                t.contains("nation") ? NationView.read(t.getCompound("nation")) : null,
                t.contains("alliance") ? AllianceView.read(t.getCompound("alliance")) : null,
                readList(t, "nations", NationSummary::read), readList(t, "towns", TownSummary::read), readList(t, "alliances", AllianceView::read),
                readList(t, "wars", WarView::read), List.copyOf(Nbt.uuids(t, "townInvites")), List.copyOf(Nbt.uuids(t, "nationInvites")),
                List.copyOf(Nbt.uuids(t, "allianceInvites")), Prices.read(t.getCompound("prices")));
    }

    // --- lookups for the GUI -----------------------------------------------------------------------------------------------

    public NationSummary nationSummary(UUID id) {
        for (NationSummary n : nations) if (n.id().equals(id)) return n;
        return null;
    }

    public TownSummary townSummary(UUID id) {
        for (TownSummary t : towns) if (t.id().equals(id)) return t;
        return null;
    }

    static <T> ListTag list(List<T> items, Function<T, CompoundTag> w) {
        ListTag l = new ListTag();
        for (T i : items) l.add(w.apply(i));
        return l;
    }

    static <T> List<T> readList(CompoundTag t, String key, Function<CompoundTag, T> r) {
        List<T> out = new ArrayList<>();
        for (Tag e : t.getList(key, Tag.TAG_COMPOUND)) out.add(r.apply((CompoundTag) e));
        return out;
    }
}
