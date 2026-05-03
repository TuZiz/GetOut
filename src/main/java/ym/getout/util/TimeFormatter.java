package ym.getout.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeFormatter {

    private TimeFormatter() {}

    public static String formatDuration(long millis) {
        if (millis <= 0) return "0s";
        Duration d = Duration.ofMillis(millis);
        long days = d.toDaysPart();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天");
        if (hours > 0) sb.append(hours).append("时");
        if (minutes > 0) sb.append(minutes).append("分");
        if (seconds > 0) sb.append(seconds).append("秒");
        return sb.toString();
    }

    public static String formatTimestamp(long millis, ZoneId zoneId, String pattern) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern).withZone(zoneId);
        return fmt.format(Instant.ofEpochMilli(millis));
    }

    public static String formatRemaining(long expiresAt) {
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0) return "已过期";
        return formatDuration(remaining);
    }
}
