package ym.getout.util;

public final class TimeParser {

    private TimeParser() {}

    /**
     * 解析时间字符串为毫秒数。
     * 支持格式：10s, 5m, 2h, 7d, 1d2h30m, 30d12h
     *
     * @return 毫秒数，解析失败返回 -1
     */
    public static long parse(String input) {
        if (input == null || input.isEmpty()) return -1;

        long total = 0;
        int numStart = -1;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '0' && c <= '9') {
                if (numStart == -1) numStart = i;
            } else {
                if (numStart == -1) return -1;
                long num;
                try {
                    num = Long.parseLong(input.substring(numStart, i));
                } catch (NumberFormatException e) {
                    return -1;
                }
                numStart = -1;
                total += switch (Character.toLowerCase(c)) {
                    case 's' -> num * 1000L;
                    case 'm' -> num * 60 * 1000L;
                    case 'h' -> num * 3600 * 1000L;
                    case 'd' -> num * 86400 * 1000L;
                    default -> -1;
                };
                if (total < 0) return -1;
            }
        }
        if (numStart != -1) {
            // trailing number without unit, try as seconds
            try {
                long num = Long.parseLong(input.substring(numStart));
                total += num * 1000L;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return total > 0 ? total : -1;
    }
}
