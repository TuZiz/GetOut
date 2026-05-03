package ym.getout.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ym.getout.GetoutPlugin;
import ym.getout.config.Settings;
import ym.getout.database.DatabaseManager;
import ym.getout.lang.MessageService;
import ym.getout.migrate.YamlToDatabaseMigrator;
import ym.getout.scheduler.SchedulerAdapter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GetoutCommand implements CommandExecutor, TabCompleter {

    private final GetoutPlugin plugin;
    private final Settings settings;
    private final MessageService messages;
    private final SchedulerAdapter scheduler;
    private final DatabaseManager databaseManager;

    public GetoutCommand(GetoutPlugin plugin, Settings settings, MessageService messages,
                         SchedulerAdapter scheduler, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.scheduler = scheduler;
        this.databaseManager = databaseManager;
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
            case "migrate" -> handleMigrate(sender, args);
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
        sender.sendMessage("§eDatabase failure strategy: §f" + settings.getDatabaseFailureStrategy());
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
        sender.sendMessage("§e/unban <玩家名|UUID> [原因] §7- 解除封禁");
        sender.sendMessage("§e/kick <玩家名|UUID> [原因] §7- 踢出玩家");
        sender.sendMessage("§e/getout reload §7- 重载配置");
        sender.sendMessage("§e/getout info §7- 查看插件信息");
        sender.sendMessage("§e/getout migrate yaml-to-database §7- 将 YAML 数据迁移到数据库");
    }

    private void handleMigrate(CommandSender sender, String[] args) {
        if (args.length < 2 || !"yaml-to-database".equalsIgnoreCase(args[1])) {
            sendHelp(sender);
            return;
        }

        if (!settings.isDatabaseEnabled()) {
            CommandUtil.sendMessage(sender, messages, "general.database-error", Map.of());
            sender.sendMessage("§c当前 storage.type 不是 database，迁移需要先启用数据库存储。");
            return;
        }

        if (databaseManager == null || !databaseManager.isInitialized()) {
            CommandUtil.sendMessage(sender, messages, "general.database-error", Map.of());
            sender.sendMessage("§c数据库尚未初始化，无法迁移。");
            return;
        }

        sender.sendMessage("§e开始迁移 YAML 到数据库...");
        scheduler.runAsync(() -> {
            try {
                YamlToDatabaseMigrator migrator = new YamlToDatabaseMigrator(plugin.getDataFolder(), settings);
                YamlToDatabaseMigrator.MigrationResult result = migrator.migrate(databaseManager);
                scheduler.runGlobal(() -> {
                    sender.sendMessage("§a迁移完成: players=" + result.players()
                            + ", bans=" + result.bans()
                            + ", events=" + result.events()
                            + ", syncStates=" + result.syncStates());
                });
            } catch (Exception e) {
                ym.getout.util.LoggerUtil.error("Failed to migrate YAML to database", e);
                scheduler.runGlobal(() -> sender.sendMessage("§c迁移失败，请查看日志。"));
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = Arrays.asList("reload", "info", "migrate");
            String prefix = args[0].toLowerCase();
            return completions.stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        } else if (args.length == 2 && "migrate".equalsIgnoreCase(args[0])) {
            return Arrays.asList("yaml-to-database").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
