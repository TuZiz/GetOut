package ym.getout.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ym.getout.GetoutPlugin;
import ym.getout.config.Settings;
import ym.getout.lang.MessageService;
import ym.getout.scheduler.SchedulerAdapter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GetoutCommand implements CommandExecutor, TabCompleter {

    private final GetoutPlugin plugin;
    private final Settings settings;
    private final MessageService messages;

    public GetoutCommand(GetoutPlugin plugin, Settings settings, MessageService messages, SchedulerAdapter scheduler) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandUtil.checkPermission(sender, "getout.admin", messages)) return true;

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.reload();
            CommandUtil.sendMessage(sender, messages, "general.reload-success", Map.of());
        } catch (Exception e) {
            ym.getout.util.LoggerUtil.error("Failed to reload plugin", e);
            CommandUtil.sendMessage(sender, messages, "general.database-error", Map.of());
        }
    }

    private void handleInfo(CommandSender sender) {
        Map<String, String> placeholders = Map.of(
                "version", plugin.getDescription().getVersion(),
                "server_id", settings.getServerId(),
                "type", settings.getDbType(),
                "host", settings.getDbHost(),
                "port", String.valueOf(settings.getDbPort()),
                "database", settings.getDbDatabase(),
                "interval", String.valueOf(settings.getSyncPollIntervalTicks())
        );
        CommandUtil.sendMessage(sender, messages, "info.header", Map.of());
        CommandUtil.sendMessage(sender, messages, "info.version", placeholders);
        CommandUtil.sendMessage(sender, messages, "info.server-id", placeholders);
        sender.sendMessage("§eStorage: §f" + settings.getStorageType()
                + (settings.isDatabaseEnabled() ? " (database enabled)" : " (YAML local)"));
        if (settings.isDatabaseEnabled()) {
            CommandUtil.sendMessage(sender, messages, "info.database", placeholders);
        } else {
            sender.sendMessage("§eDatabase: §fdisabled");
        }
        CommandUtil.sendMessage(sender, messages, "info.polling", placeholders);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6[Getout] §e命令列表:");
        sender.sendMessage("§e/ban <玩家名|UUID> [原因] §7- 永久封禁玩家");
        sender.sendMessage("§e/tempban <玩家名|UUID> <时间> [原因] §7- 临时封禁玩家");
        sender.sendMessage("§e/kick <玩家名|UUID> [原因] §7- 踢出玩家");
        sender.sendMessage("§e/getout reload §7- 重载配置");
        sender.sendMessage("§e/getout info §7- 查看插件信息");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = Arrays.asList("reload", "info");
            String prefix = args[0].toLowerCase();
            return completions.stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        return Collections.emptyList();
    }
}
