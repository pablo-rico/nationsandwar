package org.papiricoh.nationsandwar.war;

/**
 * How a siege's control bar moves each second (pure). The side with more fighters near the banner pushes it at
 * {@code rate} per extra fighter (at most three counted). A banner nobody besieges (an empty field too) slips toward the
 * holders at {@code decay}: whoever plants one must stay by it, or the siege fails at −1.
 */
public final class SiegeControl {
    public static final int MAX_EDGE = 3;

    private SiegeControl() {
    }

    public static float step(float control, int besiegers, int defenders, float rate, float decay) {
        int edge = Math.max(-MAX_EDGE, Math.min(MAX_EDGE, besiegers - defenders));
        float next;
        if (besiegers == 0 && defenders == 0) {
            next = control - decay;
        } else {
            next = control + edge * rate;
        }
        return Math.max(-1, Math.min(1, next));
    }

    /** Seconds a lone besieger needs to take a chunk from an even bar. */
    public static int secondsAlone(float rate) {
        return (int) Math.ceil(1 / rate);
    }
}
