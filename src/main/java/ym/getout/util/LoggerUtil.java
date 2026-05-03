package ym.getout.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class LoggerUtil {

    private static Logger logger;
    private static boolean debug;

    private LoggerUtil() {}

    public static void init(Logger logger, boolean debug) {
        LoggerUtil.logger = logger;
        LoggerUtil.debug = debug;
    }

    public static void info(String message) {
        if (logger != null) logger.info("[Getout] " + message);
    }

    public static void warn(String message) {
        if (logger != null) logger.warning("[Getout] " + message);
    }

    public static void error(String message) {
        if (logger != null) logger.severe("[Getout] " + message);
    }

    public static void error(String message, Throwable throwable) {
        if (logger != null) logger.log(Level.SEVERE, "[Getout] " + message, throwable);
    }

    public static void debug(String message) {
        if (debug && logger != null) logger.info("[Getout][DEBUG] " + message);
    }
}
