package ym.getout.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ym.getout.config.Settings;
import ym.getout.lang.MessageService;
import ym.getout.model.PlayerIndex;
import ym.getout.scheduler.SchedulerAdapter;
import ym.getout.storage.BanStore;
import ym.getout.storage.EventStore;
import ym.getout.storage.PlayerStore;
import ym.getout.util.LoggerUtil;
import ym.getout.util.TextUtil;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class BanCommand implements CommandExecutor, TabCompleter {

    private final PlayerStore playerRepository;
    private final BanStore banRepository;
    private final EventStore eventRepository;
    private final MessageService messages;
    private final Settings settings;
    private final SchedulerAdapter scheduler;

    public BanCommand(PlayerStore playerRepository, BanStore banRepository,
                      EventStore eventRepository, MessageService messages,
                      Settings settings, SchedulerAdapter scheduler) {
        this.playerRepository = playerRepository;
        this.banRepository = banRepository;
        this.eventRepository = eventRepository;
        this.messages = messages;
        this.settings = settings;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandUtil.checkPermission(sender, "getout.command.ban", messages)) return true;

        if (args.length < 1) {
            CommandUtil.sendMessage(sender, messages, "ban.usage", Map.of());
            return true;
        }

        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : messages.getString("ban.default-reason", "违反服务器规则");

        scheduler.runAsync(() -> {
            try {
                PlayerIndex target = playerRepository.findByNameOrUuid(targetName);
                if (target == null) {
                    scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "general.player-not-found",
                            Map.of("player", targetName)));
                    return;
                }

                UUID operatorUuid = null;
                String operatorName = "Console";
                if (sender instanceof Player player) {
                    operatorUuid = player.getUniqueId();
                    operatorName = player.getName();
                }

                long banId = banRepository.createBan(
                        target.getUuid(), target.getName(), reason,
                        operatorUuid, operatorName, null, settings.getServerId());

                if (banId < 0) {
                    scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "general.database-error", Map.of()));
                    return;
                }

                eventRepository.insertEvent("BAN", target.getUuid(), target.getName(),
                        reason, operatorName, settings.getServerId(), "");

                if (settings.isSyncKickOnlineAfterBan()) {
                    kickOnlinePlayer(target.getUuid(), target.getName(), reason, operatorName);
                }

                scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "ban.success",
                        Map.of("player", target.getName(), "reason", reason)));

            } catch (Exception e) {
                LoggerUtil.error("Error executing ban command", e);
                scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "general.database-error", Map.of()));
            }
        });

        return true;
    }

    private void kickOnlinePlayer(UUID uuid, String name, String reason, String operator) {
        scheduler.runGlobal(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Map<String, String> placeholders = Map.of(
                        "reason", reason,
                        "operator", operator,
                        "time", new SimpleDateFormat(settings.getTimeFormat()).format(new Date())
                );
                List<String> lines = messages.getFormattedList("ban.kick-message", placeholders);
                String kickMsg = String.join("\n", lines);
                player.kickPlayer(kickMsg);
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
