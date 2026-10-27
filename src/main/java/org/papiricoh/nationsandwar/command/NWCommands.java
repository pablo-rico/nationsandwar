package org.papiricoh.nationsandwar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.core.Actions;
import org.papiricoh.nationsandwar.core.NW;
import org.papiricoh.nationsandwar.data.NWSavedData;
import org.papiricoh.nationsandwar.model.ChunkPerms;
import org.papiricoh.nationsandwar.model.Flag;
import org.papiricoh.nationsandwar.model.GovernmentType;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Province;
import org.papiricoh.nationsandwar.model.Result;
import org.papiricoh.nationsandwar.model.Role;
import org.papiricoh.nationsandwar.model.RoleTree;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.network.OpenScreenPayload;
import org.papiricoh.nationsandwar.npc.NpcService;
import org.papiricoh.nationsandwar.war.War;
import org.papiricoh.nationsandwar.war.WarService;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Function;

/**
 * {@code /town}, {@code /province}, {@code /nation}, {@code /alliance}, {@code /war}, {@code /nw} (opens the GUI) and
 * {@code /nwadmin}. Every player command builds the arguments of a {@link Actions} action, so the GUI and chat behave alike.
 */
@EventBusSubscriber(modid = NationsAndWar.MODID)
public final class NWCommands {
    private NWCommands() {
    }

    private interface Args {
        void fill(CommandContext<CommandSourceStack> ctx, CompoundTag tag) throws CommandSyntaxException;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(town());
        d.register(province());
        d.register(nation());
        d.register(alliance());
        d.register(war());
        d.register(Commands.literal("nw").executes(ctx -> {
            PacketDistributor.sendToPlayer(ctx.getSource().getPlayerOrException(), new OpenScreenPayload("nation", new CompoundTag()));
            return 1;
        }));
        d.register(admin());
    }

    // --- builders ----------------------------------------------------------------------------------------------------------

    private static int run(CommandContext<CommandSourceStack> ctx, String action, Args args) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        CompoundTag tag = new CompoundTag();
        args.fill(ctx, tag);
        Result r = Actions.run(p, action, tag);
        NW.reply(p, r);
        return r.ok() ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> act(String literal, String action) {
        return Commands.literal(literal).executes(ctx -> run(ctx, action, (c, t) -> {
        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> withString(String literal, String action, String key, boolean greedy,
                                                                        SuggestionProvider<CommandSourceStack> suggest, java.util.function.BiFunction<CommandSourceStack, String, String> map) {
        var arg = Commands.argument(key, greedy ? StringArgumentType.greedyString() : StringArgumentType.string());
        if (suggest != null) arg.suggests(suggest);
        return Commands.literal(literal).then(arg.executes(ctx -> run(ctx, action, (c, t) -> t.putString(key, map.apply(c.getSource(), StringArgumentType.getString(c, key))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> withPlayer(String literal, String action) {
        return Commands.literal(literal).then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                .executes(ctx -> run(ctx, action, (c, t) -> t.putString("player", StringArgumentType.getString(c, "player")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> withAmount(String literal, String action) {
        return Commands.literal(literal).then(Commands.argument("amount", IntegerArgumentType.integer(1))
                .executes(ctx -> run(ctx, action, (c, t) -> t.putInt("amount", IntegerArgumentType.getInteger(c, "amount")))));
    }

    private static final SuggestionProvider<CommandSourceStack> ONLINE = (ctx, b) ->
            SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), b);
    private static final SuggestionProvider<CommandSourceStack> TOWNS = (ctx, b) ->
            SharedSuggestionProvider.suggest(NW.pol(ctx.getSource().getServer()).towns.values().stream().map(t -> quote(t.name)), b);
    private static final SuggestionProvider<CommandSourceStack> NATIONS = (ctx, b) ->
            SharedSuggestionProvider.suggest(NW.pol(ctx.getSource().getServer()).nations.values().stream().map(n -> quote(n.name)), b);
    private static final SuggestionProvider<CommandSourceStack> ALLIANCES = (ctx, b) ->
            SharedSuggestionProvider.suggest(NW.pol(ctx.getSource().getServer()).alliances.values().stream().map(a -> quote(a.name)), b);
    private static final SuggestionProvider<CommandSourceStack> GOVS = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(GovernmentType.values()).map(GovernmentType::id), b);

    private static String quote(String s) {
        return s.contains(" ") ? "\"" + s + "\"" : s;
    }

    private static String unquote(String s) {
        return s.startsWith("\"") && s.endsWith("\"") && s.length() > 1 ? s.substring(1, s.length() - 1) : s;
    }

    private static String townId(CommandSourceStack src, String name) {
        for (Town t : NW.pol(src.getServer()).towns.values()) if (t.name.equalsIgnoreCase(unquote(name))) return t.id.toString();
        return name;
    }

    private static String nationId(CommandSourceStack src, String name) {
        for (Nation n : NW.pol(src.getServer()).nations.values()) if (n.name.equalsIgnoreCase(unquote(name))) return n.id.toString();
        return name;
    }

    private static String allianceId(CommandSourceStack src, String name) {
        for (var a : NW.pol(src.getServer()).alliances.values()) if (a.name.equalsIgnoreCase(unquote(name))) return a.id.toString();
        return name;
    }

    // --- /town -------------------------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> town() {
        return Commands.literal("town")
                .executes(ctx -> info(ctx.getSource(), null))
                .then(withString("new", "town.found", "name", true, null, (src, s) -> s))
                .then(act("claim", "town.claim").then(Commands.literal("outpost").executes(ctx -> run(ctx, "town.claim", (c, t) -> t.putBoolean("outpost", true)))))
                .then(act("unclaim", "town.unclaim"))
                .then(withPlayer("invite", "town.invite"))
                .then(withString("join", "town.join", "town", true, TOWNS, NWCommands::townId))
                .then(act("leave", "town.leave"))
                .then(withPlayer("kick", "town.kick"))
                .then(withAmount("deposit", "town.deposit"))
                .then(withAmount("withdraw", "town.withdraw"))
                .then(act("spawn", "town.spawn"))
                .then(act("setspawn", "town.setspawn"))
                .then(flag())
                .then(perm())
                .then(act("resetchunk", "town.reset_chunk"))
                .then(withString("rename", "town.rename", "name", true, null, (src, s) -> s))
                .then(color("town.color"))
                .then(Commands.literal("open").then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> run(ctx, "town.open", (c, t) -> t.putBoolean("value", BoolArgumentType.getBool(c, "value"))))))
                .then(withPlayer("mayor", "town.mayor"))
                .then(Commands.literal("delete").then(Commands.literal("confirm").executes(ctx -> run(ctx, "town.delete", (c, t) -> {
                }))))
                .then(Commands.literal("info").then(Commands.argument("town", StringArgumentType.greedyString()).suggests(TOWNS)
                        .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "town")))))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), false)))
                .then(roles("town"));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> flag() {
        var b = Commands.literal("flag");
        for (Flag f : Flag.values()) {
            var fl = Commands.literal(f.name().toLowerCase());
            for (String v : new String[]{"on", "off", "clear"}) {
                byte value = (byte) (v.equals("on") ? 1 : v.equals("off") ? 0 : -1);
                fl.then(Commands.literal(v).executes(ctx -> run(ctx, "town.flag", (c, t) -> {
                            t.putString("flag", f.name());
                            t.putByte("value", value);
                        }))
                        .then(Commands.literal("here").executes(ctx -> run(ctx, "town.flag", (c, t) -> {
                            t.putString("flag", f.name());
                            t.putByte("value", value);
                            t.putBoolean("here", true);
                        }))));
            }
            b.then(fl);
        }
        return b;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> perm() {
        var b = Commands.literal("perm");
        for (ChunkPerms.Group g : ChunkPerms.Group.values()) {
            var gl = Commands.literal(g.name().toLowerCase());
            for (ChunkPerms.Action a : ChunkPerms.Action.values()) {
                gl.then(Commands.literal(a.name().toLowerCase()).then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> run(ctx, "town.perm", (c, t) -> {
                            t.putString("group", g.name());
                            t.putString("perm", a.name());
                            t.putBoolean("value", BoolArgumentType.getBool(c, "value"));
                        }))
                        .then(Commands.literal("here").executes(ctx -> run(ctx, "town.perm", (c, t) -> {
                            t.putString("group", g.name());
                            t.putString("perm", a.name());
                            t.putBoolean("value", BoolArgumentType.getBool(c, "value"));
                            t.putBoolean("here", true);
                        })))));
            }
            b.then(gl);
        }
        return b;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> color(String action) {
        return Commands.literal("color").then(Commands.argument("hex", StringArgumentType.word()).executes(ctx -> run(ctx, action, (c, t) -> {
            String hex = StringArgumentType.getString(c, "hex").replace("#", "");
            t.putInt("color", Integer.parseInt(hex, 16));
        })));
    }

    /** {@code role list|create|rename|color|parent|perm|assign|unassign|delete} on the town's or the nation's tree. */
    private static ArgumentBuilder<CommandSourceStack, ?> roles(String scope) {
        Function<Args, Args> scoped = a -> (c, t) -> {
            t.putString("scope", scope);
            a.fill(c, t);
        };
        var id = Commands.argument("id", IntegerArgumentType.integer(0));
        return Commands.literal("role")
                .then(Commands.literal("list").executes(ctx -> listRoles(ctx.getSource(), scope)))
                .then(Commands.literal("create").then(Commands.argument("parent", IntegerArgumentType.integer(0)).then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> run(ctx, "role.add", scoped.apply((c, t) -> {
                            t.putInt("parent", IntegerArgumentType.getInteger(c, "parent"));
                            t.putString("name", StringArgumentType.getString(c, "name"));
                            t.putInt("color", 0xAAAAAA);
                        }))))))
                .then(Commands.literal("rename").then(Commands.argument("id", IntegerArgumentType.integer(0)).then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> run(ctx, "role.rename", scoped.apply((c, t) -> {
                            t.putInt("id", IntegerArgumentType.getInteger(c, "id"));
                            t.putString("name", StringArgumentType.getString(c, "name"));
                        }))))))
                .then(Commands.literal("color").then(Commands.argument("id", IntegerArgumentType.integer(0)).then(Commands.argument("hex", StringArgumentType.word())
                        .executes(ctx -> run(ctx, "role.color", scoped.apply((c, t) -> {
                            t.putInt("id", IntegerArgumentType.getInteger(c, "id"));
                            t.putInt("color", Integer.parseInt(StringArgumentType.getString(c, "hex").replace("#", ""), 16));
                        }))))))
                .then(Commands.literal("parent").then(Commands.argument("id", IntegerArgumentType.integer(0)).then(Commands.argument("parent", IntegerArgumentType.integer(0))
                        .executes(ctx -> run(ctx, "role.move", scoped.apply((c, t) -> {
                            t.putInt("id", IntegerArgumentType.getInteger(c, "id"));
                            t.putInt("parent", IntegerArgumentType.getInteger(c, "parent"));
                        }))))))
                .then(Commands.literal("perm").then(Commands.argument("id", IntegerArgumentType.integer(0)).then(Commands.argument("perm", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Perm.values()).map(p -> p.name().toLowerCase()), b))
                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> togglePerm(ctx, scope))))))
                .then(Commands.literal("assign").then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE).then(id
                        .executes(ctx -> run(ctx, "role.assign", scoped.apply((c, t) -> {
                            t.putString("player", StringArgumentType.getString(c, "player"));
                            t.putInt("id", IntegerArgumentType.getInteger(c, "id"));
                        }))))))
                .then(Commands.literal("unassign").then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                        .executes(ctx -> run(ctx, "role.unassign", scoped.apply((c, t) -> t.putString("player", StringArgumentType.getString(c, "player")))))))
                .then(Commands.literal("delete").then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(ctx -> run(ctx, "role.remove", scoped.apply((c, t) -> t.putInt("id", IntegerArgumentType.getInteger(c, "id")))))));
    }

    private static int togglePerm(CommandContext<CommandSourceStack> ctx, String scope) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Politics pol = NW.pol(p.getServer());
        Town town = pol.townOf(p.getUUID());
        Object owner = "nation".equals(scope) ? pol.nationOf(town) : town;
        if (owner == null) {
            NW.reply(p, Result.fail("no_membership"));
            return 0;
        }
        Role role = Politics.treeOf(owner).get(IntegerArgumentType.getInteger(ctx, "id"));
        Perm perm;
        try {
            perm = Perm.valueOf(StringArgumentType.getString(ctx, "perm").toUpperCase());
        } catch (IllegalArgumentException e) {
            NW.reply(p, Result.fail("bad_args"));
            return 0;
        }
        if (role == null) {
            NW.reply(p, Result.fail("role.unknown"));
            return 0;
        }
        var perms = java.util.EnumSet.noneOf(Perm.class);
        perms.addAll(role.perms);
        if (BoolArgumentType.getBool(ctx, "value")) perms.add(perm);
        else perms.remove(perm);
        return run(ctx, "role.perms", (c, t) -> {
            t.putString("scope", scope);
            t.putInt("id", role.id);
            t.putInt("perms", Perm.mask(perms));
        });
    }

    // --- /province ---------------------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> province() {
        return Commands.literal("province")
                .then(withString("create", "province.create", "name", true, null, (src, s) -> s))
                .then(Commands.literal("add").then(provinceArg().then(Commands.argument("town", StringArgumentType.greedyString()).suggests(TOWNS)
                        .executes(ctx -> run(ctx, "province.add", provinceAndTown())))))
                .then(Commands.literal("remove").then(provinceArg().then(Commands.argument("town", StringArgumentType.greedyString()).suggests(TOWNS)
                        .executes(ctx -> run(ctx, "province.remove", provinceAndTown())))))
                .then(Commands.literal("capital").then(provinceArg().then(Commands.argument("town", StringArgumentType.greedyString()).suggests(TOWNS)
                        .executes(ctx -> run(ctx, "province.capital", provinceAndTown())))))
                .then(Commands.literal("governor").then(provinceArg().then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE)
                        .executes(ctx -> run(ctx, "province.governor", (c, t) -> {
                            t.putString("province", provinceId(c));
                            t.putString("player", StringArgumentType.getString(c, "player"));
                        })))))
                .then(Commands.literal("rename").then(provinceArg().then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> run(ctx, "province.rename", (c, t) -> {
                            t.putString("province", provinceId(c));
                            t.putString("name", StringArgumentType.getString(c, "name"));
                        })))))
                .then(Commands.literal("color").then(provinceArg().then(Commands.argument("hex", StringArgumentType.word())
                        .executes(ctx -> run(ctx, "province.color", (c, t) -> {
                            t.putString("province", provinceId(c));
                            t.putInt("color", Integer.parseInt(StringArgumentType.getString(c, "hex").replace("#", ""), 16));
                        })))))
                .then(Commands.literal("delete").then(provinceArg().executes(ctx -> run(ctx, "province.delete", (c, t) -> t.putString("province", provinceId(c))))))
                .then(Commands.literal("list").executes(ctx -> listProvinces(ctx.getSource())));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> provinceArg() {
        return Commands.argument("province", StringArgumentType.string()).suggests((ctx, b) -> {
            Politics pol = NW.pol(ctx.getSource().getServer());
            Nation n = ctx.getSource().getPlayer() == null ? null : pol.nationOfPlayer(ctx.getSource().getPlayer().getUUID());
            return SharedSuggestionProvider.suggest(n == null ? java.util.stream.Stream.empty()
                    : n.provinces.stream().map(pol.provinces::get).filter(java.util.Objects::nonNull).map(p -> quote(p.name)), b);
        });
    }

    private static String provinceId(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String name = StringArgumentType.getString(c, "province");
        Politics pol = NW.pol(c.getSource().getServer());
        Nation n = pol.nationOfPlayer(c.getSource().getPlayerOrException().getUUID());
        if (n != null) for (UUID id : n.provinces) {
            Province p = pol.provinces.get(id);
            if (p != null && p.name.equalsIgnoreCase(name)) return p.id.toString();
        }
        return name;
    }

    private static Args provinceAndTown() {
        return (c, t) -> {
            t.putString("province", provinceId(c));
            t.putString("town", townId(c.getSource(), StringArgumentType.getString(c, "town")));
        };
    }

    // --- /nation -----------------------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> nation() {
        return Commands.literal("nation")
                .executes(ctx -> nationInfo(ctx.getSource()))
                .then(Commands.literal("new").then(Commands.argument("gov", StringArgumentType.word()).suggests(GOVS)
                        .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> run(ctx, "nation.found", (c, t) -> {
                            t.putString("gov", StringArgumentType.getString(c, "gov"));
                            t.putString("name", StringArgumentType.getString(c, "name"));
                        })))))
                .then(townRef("invite", "nation.invite"))
                .then(nationRef("join", "nation.join", "nation"))
                .then(act("leave", "nation.leave"))
                .then(townRef("kick", "nation.kick"))
                .then(townRef("capital", "nation.capital"))
                .then(withPlayer("leader", "nation.leader"))
                .then(Commands.literal("gov").then(Commands.argument("gov", StringArgumentType.word()).suggests(GOVS)
                        .executes(ctx -> run(ctx, "nation.gov", (c, t) -> t.putString("gov", StringArgumentType.getString(c, "gov"))))))
                .then(withString("rename", "nation.rename", "name", true, null, (src, s) -> s))
                .then(color("nation.color"))
                .then(withAmount("deposit", "nation.deposit"))
                .then(withAmount("withdraw", "nation.withdraw"))
                .then(Commands.literal("delete").then(Commands.literal("confirm").executes(ctx -> run(ctx, "nation.delete", (c, t) -> {
                }))))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), true)))
                .then(roles("nation"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> townRef(String literal, String action) {
        return Commands.literal(literal).then(Commands.argument("town", StringArgumentType.greedyString()).suggests(TOWNS)
                .executes(ctx -> run(ctx, action, (c, t) -> {
                    t.putString("town", townId(c.getSource(), StringArgumentType.getString(c, "town")));
                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> nationRef(String literal, String action, String key) {
        return Commands.literal(literal).then(Commands.argument(key, StringArgumentType.greedyString()).suggests(NATIONS)
                .executes(ctx -> run(ctx, action, (c, t) -> {
                    t.putString(key, nationId(c.getSource(), StringArgumentType.getString(c, key)));
                })));
    }

    // --- /alliance, /war ---------------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> alliance() {
        return Commands.literal("alliance")
                .then(withString("create", "alliance.create", "name", true, null, (src, s) -> s))
                .then(nationRef("invite", "alliance.invite", "nation"))
                .then(Commands.literal("join").then(Commands.argument("alliance", StringArgumentType.greedyString()).suggests(ALLIANCES)
                        .executes(ctx -> run(ctx, "alliance.join", (c, t) -> {
                            t.putString("alliance", allianceId(c.getSource(), StringArgumentType.getString(c, "alliance")));
                        }))))
                .then(act("leave", "alliance.leave"))
                .then(nationRef("kick", "alliance.kick", "nation"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> war() {
        return Commands.literal("war")
                .executes(ctx -> warStatus(ctx.getSource()))
                .then(nationRef("declare", "war.declare", "nation"))
                .then(act("peace", "war.peace"))
                .then(Commands.literal("surrender").then(Commands.literal("confirm").executes(ctx -> run(ctx, "war.surrender", (c, t) -> {
                }))))
                .then(Commands.literal("terms").then(Commands.argument("tribute", IntegerArgumentType.integer(0))
                        .then(Commands.argument("vassal", IntegerArgumentType.integer(0)).executes(ctx -> run(ctx, "war.terms", (c, t) -> {
                            t.putInt("tribute", IntegerArgumentType.getInteger(c, "tribute"));
                            t.putInt("vassal", IntegerArgumentType.getInteger(c, "vassal"));
                            NWSavedData data = NW.data(c.getSource().getServer());
                            var list = new net.minecraft.nbt.ListTag();
                            for (War w : data.wars.values()) if (w.phase == War.Phase.TREATY) w.fallen.forEach(f -> list.add(StringTag.valueOf(f.toString())));
                            t.put("annex", list);
                        })))))
                .then(act("confirm", "war.confirm"))
                .then(Commands.literal("status").executes(ctx -> warStatus(ctx.getSource())));
    }

    // --- /nwadmin ----------------------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("nwadmin").requires(s -> s.hasPermission(2))
                .then(Commands.literal("bypass").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    boolean on = NW.BYPASS.add(p.getUUID()) || !NW.BYPASS.remove(p.getUUID());
                    ctx.getSource().sendSuccess(() -> Component.translatable("nationsandwar.msg.bypass_" + (on ? "on" : "off")), false);
                    return 1;
                }))
                .then(Commands.literal("npc").then(Commands.literal("create").executes(ctx -> npc(ctx.getSource(), null))
                        .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> npc(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("endwars").executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    int n = 0;
                    for (War w : new java.util.ArrayList<>(NW.data(server).wars.values())) {
                        if (!w.live()) continue;
                        WarService.end(server, w, War.Outcome.WHITE_PEACE);
                        n++;
                    }
                    int ended = n;
                    ctx.getSource().sendSuccess(() -> Component.translatable("nationsandwar.msg.wars_ended", ended), true);
                    return n;
                }))
                .then(Commands.literal("deletetown").then(Commands.argument("town", StringArgumentType.greedyString()).suggests(TOWNS).executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    Politics p = NW.pol(server);
                    String name = StringArgumentType.getString(ctx, "town").replace("\"", "");
                    for (Town t : p.towns.values()) {
                        if (!t.name.equalsIgnoreCase(name)) continue;
                        p.deleteTown(null, t);
                        Actions.removeCore(server, t);
                        NW.changed(server);
                        ctx.getSource().sendSuccess(() -> Component.translatable("nationsandwar.msg.town_deleted", t.name), true);
                        return 1;
                    }
                    return 0;
                })));
    }

    private static int npc(CommandSourceStack src, String name) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Town t = NpcService.create(p.serverLevel(), p.blockPosition(), name);
        if (t == null) {
            NW.reply(p, Result.fail("chunk_taken"));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("nationsandwar.msg.npc_created", t.name), true);
        return 1;
    }

    // --- info --------------------------------------------------------------------------------------------------------------

    private static int info(CommandSourceStack src, String name) {
        var server = src.getServer();
        Politics p = NW.pol(server);
        Town found = null;
        if (name != null) {
            for (Town x : p.towns.values()) if (x.name.equalsIgnoreCase(name.replace("\"", ""))) found = x;
        } else if (src.getPlayer() != null) {
            found = p.townOf(src.getPlayer().getUUID());
            if (found == null) found = NW.townAt(src.getLevel(), src.getPlayer().blockPosition());
        }
        Town t = found;
        if (t == null) {
            src.sendFailure(Component.translatable("nationsandwar.msg.no_town"));
            return 0;
        }
        Nation n = p.nationOf(t);
        Province pr = p.provinceOf(t);
        MutableComponent c = Component.literal("— " + t.name + " —").withStyle(s -> s.withColor(t.color).withBold(true));
        c.append(line("nationsandwar.info.level", Component.translatable(t.level().langKey())));
        c.append(line("nationsandwar.info.mayor", Component.literal(NW.name(server, t.mayor()))));
        c.append(line("nationsandwar.info.residents", Component.literal(String.valueOf(t.residents.size()))));
        c.append(line("nationsandwar.info.claims", Component.literal(t.claims.size() + "/" + p.maxClaims(t))));
        c.append(line("nationsandwar.info.bank", Component.literal(t.bank + " ◆")));
        if (n != null) c.append(line("nationsandwar.info.nation", Component.literal(n.name).withStyle(s -> s.withColor(n.color))));
        if (pr != null) c.append(line("nationsandwar.info.province", Component.literal(pr.name)));
        src.sendSuccess(() -> c, false);
        return 1;
    }

    private static int nationInfo(CommandSourceStack src) throws CommandSyntaxException {
        var server = src.getServer();
        Politics p = NW.pol(server);
        Nation n = p.nationOfPlayer(src.getPlayerOrException().getUUID());
        if (n == null) {
            src.sendFailure(Component.translatable("nationsandwar.msg.no_nation"));
            return 0;
        }
        MutableComponent c = Component.literal("— ").append(Component.translatable(n.government.key())).append(" " + n.name + " —")
                .withStyle(s -> s.withColor(n.color).withBold(true));
        c.append(line("nationsandwar.info.leader", Component.literal(NW.name(server, n.leader()))));
        Town cap = p.towns.get(n.capital);
        c.append(line("nationsandwar.info.capital", Component.literal(cap == null ? "?" : cap.name)));
        c.append(line("nationsandwar.info.towns", Component.literal(String.valueOf(n.towns.size()))));
        c.append(line("nationsandwar.info.citizens", Component.literal(String.valueOf(p.citizens(n).size()))));
        c.append(line("nationsandwar.info.bank", Component.literal(n.bank + " ◆")));
        if (n.alliance != null && p.alliances.get(n.alliance) != null)
            c.append(line("nationsandwar.info.alliance", Component.literal(p.alliances.get(n.alliance).name)));
        src.sendSuccess(() -> c, false);
        return 1;
    }

    private static Component line(String key, Component value) {
        return Component.literal("\n").append(Component.translatable(key).withStyle(ChatFormatting.GRAY)).append(": ").append(value);
    }

    private static int list(CommandSourceStack src, boolean nations) {
        Politics p = NW.pol(src.getServer());
        MutableComponent c = Component.translatable(nations ? "nationsandwar.info.nation_list" : "nationsandwar.info.town_list").withStyle(ChatFormatting.GOLD);
        if (nations) for (Nation n : p.nations.values())
            c.append(Component.literal("\n• " + n.name + " (" + n.towns.size() + ")" + (n.npc ? " [NPC]" : "")).withStyle(s -> s.withColor(n.color)));
        else for (Town t : p.towns.values())
            c.append(Component.literal("\n• " + t.name + " (" + t.residents.size() + ")" + (t.npc ? " [NPC]" : "")).withStyle(s -> s.withColor(t.color)));
        src.sendSuccess(() -> c, false);
        return 1;
    }

    private static int listRoles(CommandSourceStack src, String scope) throws CommandSyntaxException {
        ServerPlayer pl = src.getPlayerOrException();
        Politics p = NW.pol(src.getServer());
        Town t = p.townOf(pl.getUUID());
        Object owner = "nation".equals(scope) ? p.nationOf(t) : t;
        if (owner == null) {
            src.sendFailure(Component.translatable("nationsandwar.msg.no_membership"));
            return 0;
        }
        RoleTree tree = Politics.treeOf(owner);
        MutableComponent c = Component.empty();
        roleLines(src, tree, RoleTree.ROOT, 0, c);
        src.sendSuccess(() -> c, false);
        return 1;
    }

    private static void roleLines(CommandSourceStack src, RoleTree tree, int id, int depth, MutableComponent out) {
        Role r = tree.get(id);
        if (r == null || depth > 16) return;
        Component name = r.name.startsWith("@") ? Component.translatable(r.name.substring(1)) : Component.literal(r.name);
        StringBuilder holders = new StringBuilder();
        for (UUID h : r.holders) holders.append(holders.isEmpty() ? "" : ", ").append(NW.name(src.getServer(), h));
        out.append(Component.literal((depth == 0 ? "" : "\n") + "  ".repeat(depth) + "#" + r.id + " ").withStyle(ChatFormatting.DARK_GRAY))
                .append(name.copy().withStyle(s -> s.withColor(r.color)))
                .append(Component.literal(holders.isEmpty() ? "" : " — " + holders).withStyle(ChatFormatting.GRAY));
        for (Role child : tree.children(id)) roleLines(src, tree, child.id, depth + 1, out);
    }

    private static int listProvinces(CommandSourceStack src) throws CommandSyntaxException {
        Politics p = NW.pol(src.getServer());
        Nation n = p.nationOfPlayer(src.getPlayerOrException().getUUID());
        if (n == null) {
            src.sendFailure(Component.translatable("nationsandwar.msg.no_nation"));
            return 0;
        }
        MutableComponent c = Component.translatable("nationsandwar.info.province_list").withStyle(ChatFormatting.GOLD);
        for (UUID id : n.provinces) {
            Province pr = p.provinces.get(id);
            if (pr != null) c.append(Component.literal("\n• " + pr.name + " — " + NW.name(src.getServer(), pr.governor) + " (" + pr.towns.size() + ")"));
        }
        src.sendSuccess(() -> c, false);
        return 1;
    }

    private static int warStatus(CommandSourceStack src) {
        var server = src.getServer();
        NWSavedData data = NW.data(server);
        long now = server.overworld().getGameTime();
        MutableComponent c = Component.translatable("nationsandwar.info.wars").withStyle(ChatFormatting.RED);
        for (War w : data.wars.values()) {
            Nation a = data.politics.nations.get(w.attacker), d = data.politics.nations.get(w.defender);
            long mins = Math.max(0, ((w.phase == War.Phase.PREPARATION ? w.activeAt : w.phase == War.Phase.TREATY ? w.treatyUntil : w.deadline) - now) / 1200);
            c.append(Component.literal("\n• " + (a == null ? "?" : a.name) + " vs " + (d == null ? "?" : d.name) + " — " + w.phase.name().toLowerCase()
                    + " " + w.scoreAttackers + ":" + w.scoreDefenders + " (" + mins + " min)").withStyle(ChatFormatting.WHITE));
        }
        src.sendSuccess(() -> c, false);
        return 1;
    }
}
