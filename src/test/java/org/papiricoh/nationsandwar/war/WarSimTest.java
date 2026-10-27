package org.papiricoh.nationsandwar.war;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WarSimTest {
    @Test
    void theSameClashAlwaysEndsTheSame() {
        UUID war = UUID.randomUUID();
        assertArrayEquals(WarSim.clash(4, 4, WarSim.dice(war, 3, 900)), WarSim.clash(4, 4, WarSim.dice(war, 3, 900)));
        assertArrayEquals(new int[]{0, 5}, WarSim.clash(0, 5, new Random(1)), "nobody left to fight");
    }

    @Test
    void theOutnumberedSideBleedsMore() {
        Random r = new Random(7);
        int lostA = 0, lostD = 0;
        for (int i = 0; i < 2000; i++) {
            int[] left = WarSim.clash(6, 2, r);
            lostA += 6 - left[0];
            lostD += 2 - left[1];
        }
        assertTrue(lostD > lostA * 2, "defenders lost " + lostD + ", attackers " + lostA);
    }

    @Test
    void anEvenSiegeDoesNotLastForever() {
        for (int seed = 0; seed < 50; seed++) {
            UUID war = new UUID(seed, seed * 31L);
            float control = 0;
            int a = 4, d = 4;
            long second = 0;
            while (control < 1 && control > -1 && a > 0 && second < 4 * 3600) {
                control = SiegeControl.step(control, a, d, 0.01f, 0.002f);
                if (WarSim.clash(second, 1)) {
                    int[] left = WarSim.clash(a, d, WarSim.dice(war, 1, second));
                    a = left[0];
                    d = left[1];
                }
                second++;
            }
            assertTrue(second < 4 * 3600, "seed " + seed + " still even after four hours");
        }
    }
}
