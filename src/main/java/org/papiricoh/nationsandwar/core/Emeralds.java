package org.papiricoh.nationsandwar.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Emeralds in a player's inventory, the mod's currency (emerald blocks count nine; change is given back). */
public final class Emeralds {
    private Emeralds() {
    }

    public static int count(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(Items.EMERALD)) n += s.getCount();
            else if (s.is(Items.EMERALD_BLOCK)) n += s.getCount() * 9;
        }
        return n;
    }

    /** Takes {@code amount} if the player has it; emeralds first, then blocks (with change). Creative players pay nothing. */
    public static boolean take(ServerPlayer p, int amount) {
        if (amount <= 0 || p.getAbilities().instabuild) return true;
        if (count(p) < amount) return false;
        int left = amount;
        for (ItemStack s : p.getInventory().items) {
            if (left <= 0) break;
            if (s.is(Items.EMERALD)) {
                int t = Math.min(left, s.getCount());
                s.shrink(t);
                left -= t;
            }
        }
        for (ItemStack s : p.getInventory().items) {
            if (left <= 0) break;
            while (left > 0 && s.is(Items.EMERALD_BLOCK) && !s.isEmpty()) {
                s.shrink(1);
                left -= 9;
            }
        }
        if (left < 0) give(p, -left);
        p.getInventory().setChanged();
        return true;
    }

    public static void give(ServerPlayer p, int amount) {
        int blocks = amount / 9, rest = amount % 9;
        while (blocks > 0) {
            int n = Math.min(64, blocks);
            ItemStack s = new ItemStack(Items.EMERALD_BLOCK, n);
            if (!p.getInventory().add(s)) p.drop(s, false);
            blocks -= n;
        }
        if (rest > 0) {
            ItemStack s = new ItemStack(Items.EMERALD, rest);
            if (!p.getInventory().add(s)) p.drop(s, false);
        }
    }
}
