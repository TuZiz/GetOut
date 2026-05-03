package ym.getout.util;

import java.util.UUID;

public final class UuidUtil {

    private UuidUtil() {}

    /**
     * 尝试将字符串解析为 UUID，支持带连字符和不带连字符的格式。
     */
    public static UUID parse(String input) {
        if (input == null || input.isEmpty()) return null;
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            // Try without dashes: 32 hex chars
            if (input.length() == 32 && input.matches("[0-9a-fA-F]{32}")) {
                String formatted = input.substring(0, 8) + "-" +
                        input.substring(8, 12) + "-" +
                        input.substring(12, 16) + "-" +
                        input.substring(16, 20) + "-" +
                        input.substring(20);
                try {
                    return UUID.fromString(formatted);
                } catch (IllegalArgumentException e2) {
                    return null;
                }
            }
            return null;
        }
    }

    /**
     * 判断字符串是否是 UUID 格式。
     */
    public static boolean isUuid(String input) {
        return parse(input) != null;
    }
}
