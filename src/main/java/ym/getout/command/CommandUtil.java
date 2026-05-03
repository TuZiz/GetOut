package ym.getout.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ym.getout.lang.MessageService;
import ym.getout.util.TextUtil;

import java.util.List;
import java.util.Map;

public final class CommandUtil {

    private CommandUtil() {}

    /**
     * 发送消息给命令发送者。
     * 将 MiniMessage Component 转换为 legacy 文本发送，兼容 Spigot。
     */
    public static void sendMessage(CommandSender sender, MessageService messages, String path, Map<String, String> placeholders) {
        Component component = messages.getComponent(path, placeholders);
        String legacy = TextUtil.toLegacy(component);
        sender.sendMessage(legacy);
    }

    /**
     * 发送多行消息给命令发送者。
     */
    public static void sendMessageList(CommandSender sender, MessageService messages, String path, Map<String, String> placeholders) {
        Component component = messages.getComponentList(path, placeholders);
        String legacy = TextUtil.toLegacy(component);
        sender.sendMessage(legacy);
    }

    /**
     * 检查命令发送者是否有权限。
     */
    public static boolean checkPermission(CommandSender sender, String permission, MessageService messages) {
        if (sender.hasPermission(permission)) return true;
        sendMessage(sender, messages, "general.no-permission", Map.of());
        return false;
    }

    /**
     * 检查是否是玩家。
     */
    public static boolean checkPlayer(CommandSender sender, MessageService messages) {
        if (sender instanceof Player) return true;
        sendMessage(sender, messages, "general.player-only", Map.of());
        return false;
    }
}
