package org.papiricoh.nationsandwar.model;

/**
 * The answer to a political action: whether it went through, and the message for the actor as a key under
 * {@code nationsandwar.msg.} with its arguments.
 */
public record Result(boolean ok, String key, Object... args) {
    public static Result ok(String key, Object... args) {
        return new Result(true, key, args);
    }

    public static Result fail(String key, Object... args) {
        return new Result(false, key, args);
    }

    public String langKey() {
        return "nationsandwar.msg." + key;
    }

    /** The first argument as an int (ids returned by creations). */
    public int intArg() {
        return args.length > 0 && args[0] instanceof Integer i ? i : -1;
    }
}
