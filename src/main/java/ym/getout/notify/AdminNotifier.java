package ym.getout.notify;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ym.getout.config.Settings;
import ym.getout.lang.MessageService;
import ym.getout.util.TextUtil;

import java.util.Map;

public class AdminNotifier {

    private final Settings settings;
    private final MessageService messages;

    public AdminNotifier(Settings settings, MessageService messages) {
        this.settings = settings;
        this.messages = messages;
    }

    public void notifyPunishment(String eventType, String targetName, String reason,
                                 String operatorName, String serverId, boolean synced) {
        if (!settings.isAdminNotifyEnabled()) return;

        String path = switch (eventType) {
            case "BAN" -> "admin-notify.ban";
            case "IP_BAN" -> "admin-notify.banip";
            case "TEMPBAN" -> "admin-notify.tempban";
            case "UNBAN" -> "admin-notify.unban";
            case "KICK" -> "admin-notify.kick";
            default -> "admin-notify.generic";
        };

        Map<String, String> placeholders = Map.of(
                "type", eventType,
                "player", targetName != null ? targetName : "",
                "reason", reason != null ? reason : "",
                "operator", operatorName != null ? operatorName : "Console",
                "server", serverId != null ? serverId : "",
                "scope", synced ? messages.getString("admin-notify.scope-synced", "跨服同步") : messages.getString("admin-notify.scope-local", "本服执行")
        );
        String legacy = TextUtil.toLegacy(messages.getComponent(path, placeholders));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(settings.getAdminNotifyPermission())) {
                player.sendMessage(legacy);
            }
        }

        if (settings.isAdminNotifyConsole()) {
            CommandSender console = Bukkit.getConsoleSender();
            console.sendMessage(legacy);
        }
    }
}
