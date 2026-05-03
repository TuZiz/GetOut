package ym.getout.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TextUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private TextUtil() {}

    /**
     * 解析 MiniMessage 字符串为 Component。
     */
    public static Component parseMiniMessage(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        try {
            return MINI_MESSAGE.deserialize(text);
        } catch (Exception e) {
            // Fallback to legacy text if MiniMessage fails
            return Component.text(text);
        }
    }

    /**
     * 解析多行 MiniMessage 字符串列表为单个 Component（带换行）。
     */
    public static Component parseMiniMessage(List<String> lines) {
        if (lines == null || lines.isEmpty()) return Component.empty();
        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) result = result.append(Component.newline());
            result = result.append(parseMiniMessage(lines.get(i)));
        }
        return result;
    }

    /**
     * 替换字符串中的变量占位符。
     */
    public static String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return result;
    }

    /**
     * 替换列表中每行的变量占位符。
     */
    public static List<String> replacePlaceholders(List<String> lines, Map<String, String> placeholders) {
        if (lines == null) return List.of();
        return lines.stream()
                .map(line -> replacePlaceholders(line, placeholders))
                .collect(Collectors.toList());
    }

    /**
     * 将 Component 序列化为 legacy 文本（用于 Spigot 降级）。
     */
    public static String toLegacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
