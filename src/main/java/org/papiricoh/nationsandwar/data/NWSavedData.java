package org.papiricoh.nationsandwar.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.papiricoh.nationsandwar.NWConfig;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.war.RestoreLog;
import org.papiricoh.nationsandwar.war.War;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the mod keeps per world, in the overworld's data storage: the politics, the wars with their restore logs,
 * when each pair of nations last fought, and the village starts already looked at for NPC towns.
 */
public final class NWSavedData extends SavedData {
    private static final String NAME = "nationsandwar";

    public Politics politics = new Politics();
    public final Map<UUID, War> wars = new LinkedHashMap<>();
    public final Map<UUID, RestoreLog> restore = new HashMap<>();
    /** "a|b" (sorted ids) → game time the last war between them ended. */
    public final Map<String, Long> lastWar = new HashMap<>();
    /** "dim|chunk" of village starts already handled. */
    public final Set<String> villages = new HashSet<>();
    public long lastBrain;

    public static NWSavedData get(MinecraftServer server) {
        NWSavedData d = server.overworld().getDataStorage().computeIfAbsent(new Factory<>(NWSavedData::new, NWSavedData::load, null), NAME);
        d.politics.rules = NWConfig.rules();
        d.politics.observer = org.papiricoh.nationsandwar.core.ApiEvents.observer(server);
        d.politics.claimVeto = org.papiricoh.nationsandwar.api.ClaimVetoes::vetoed;
        return d;
    }

    public RestoreLog restoreLog(UUID war) {
        return restore.computeIfAbsent(war, k -> new RestoreLog());
    }

    public static String pair(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("politics", politics.save());
        ListTag wl = new ListTag();
        for (War w : wars.values()) {
            CompoundTag t = w.save();
            RestoreLog log = restore.get(w.id);
            if (log != null) t.put("restore", log.save());
            wl.add(t);
        }
        tag.put("wars", wl);
        CompoundTag lw = new CompoundTag();
        lastWar.forEach(lw::putLong);
        tag.put("lastWar", lw);
        ListTag vl = new ListTag();
        for (String v : villages) vl.add(net.minecraft.nbt.StringTag.valueOf(v));
        tag.put("villages", vl);
        tag.putLong("lastBrain", lastBrain);
        return tag;
    }

    private static NWSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NWSavedData d = new NWSavedData();
        d.politics = Politics.load(tag.getCompound("politics"));
        for (Tag e : tag.getList("wars", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            War w = War.load(t);
            d.wars.put(w.id, w);
            if (t.contains("restore")) d.restore.put(w.id, RestoreLog.load(t.getList("restore", Tag.TAG_COMPOUND)));
        }
        CompoundTag lw = tag.getCompound("lastWar");
        for (String k : lw.getAllKeys()) d.lastWar.put(k, lw.getLong(k));
        for (Tag v : tag.getList("villages", Tag.TAG_STRING)) d.villages.add(v.getAsString());
        d.lastBrain = tag.getLong("lastBrain");
        return d;
    }
}
