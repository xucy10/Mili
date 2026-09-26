package fun.bm.mili.scheduler;

/**
 * Logging facade for the scheduler core.
 * <p>
 * The core must compile with nothing but the JDK on the classpath — that is what
 * makes the scheduling model verifiable on its own, without first building the
 * whole Minecraft server. So it cannot reference {@code com.mojang.logging},
 * which ships with the game.
 *
 * <p>{@link System.Logger} is part of the JDK, so it costs nothing and still
 * routes to whatever backend the platform installs. The Folia adapter is free to
 * mirror these messages into the server log; nothing here assumes it does.
 *
 * <p>All methods are no-ops when the message is below the platform's threshold,
 * because the level check happens before the message is built.
 */
public final class SchedulerLog {

    private static final System.Logger LOGGER = System.getLogger("Mili.Scheduler");

    /** Set to {@code true} by tests to capture expected warnings instead of printing them. */
    private static volatile boolean quiet;

    private SchedulerLog() {}

    public static void setQuiet(boolean value) {
        quiet = value;
    }

    public static boolean isQuiet() {
        return quiet;
    }

    public static void debug(String format, Object... args) {
        if (quiet) return;
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, format(format, args));
        }
    }

    public static void info(String format, Object... args) {
        if (quiet) return;
        LOGGER.log(System.Logger.Level.INFO, format(format, args));
    }

    public static void warn(String format, Object... args) {
        if (quiet) return;
        LOGGER.log(System.Logger.Level.WARNING, format(format, args));
    }

    public static void error(String format, Object... args) {
        if (quiet) return;
        LOGGER.log(System.Logger.Level.ERROR, format(format, args));
    }

    public static void error(String message, Throwable cause) {
        if (quiet) return;
        LOGGER.log(System.Logger.Level.ERROR, message, cause);
    }

    /**
     * {@code String.format} for callers that already hold a format string.
     * <p>
     * Formatting is intentionally manual rather than via
     * {@code System.Logger}'s own {@code {0}} placeholders: the scheduler wants
     * the familiar {@code %s} style used everywhere else in Mili, and doing the
     * substitution ourselves means a malformed format string degrades to the raw
     * text instead of throwing inside a scheduler path.
     */
    private static String format(String format, Object... args) {
        if (args == null || args.length == 0) return format;
        try {
            return String.format(format, args);
        } catch (Throwable ignored) {
            return format;
        }
    }
}
