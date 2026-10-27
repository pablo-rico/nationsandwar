package org.papiricoh.nationsandwar.api;

import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Town;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registry of {@link ManagedNationProvider}s, by key. Register during mod construction or common setup; a nation whose
 * provider is missing (the other mod was removed) keeps its locks and follows the defaults.
 */
public final class ManagedNations {
    private static final Map<String, ManagedNationProvider> PROVIDERS = new ConcurrentHashMap<>();
    private static final ManagedNationProvider ORPHAN = () -> "";

    private ManagedNations() {
    }

    public static void register(ManagedNationProvider provider) {
        PROVIDERS.put(provider.key(), provider);
    }

    /** The provider of {@code key}, or one with every default. */
    public static ManagedNationProvider provider(String key) {
        return key == null ? ORPHAN : PROVIDERS.getOrDefault(key, ORPHAN);
    }

    public static ManagedNationProvider of(Nation nation) {
        return provider(nation.managedBy);
    }

    public static ManagedNationProvider of(Town town) {
        return provider(town.managedBy);
    }
}
