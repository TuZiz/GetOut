package ym.getout.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ym.getout.config.Settings;
import ym.getout.lang.MessageService;
import ym.getout.model.PlayerIndex;
import ym.getout.notify.AdminNotifier;
import ym.getout.scheduler.SchedulerAdapter;
import ym.getout.storage.BanStore;
import ym.getout.storage.EventStore;
import ym.getout.storage.PlayerStore;
import ym.getout.util.LoggerUtil;
import ym.getout.util.TimeFormatter;
import ym.getout.util.TimeParser;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class TempBanCommand implements CommandExecutor, TabCompleter {

    private final PlayerStore playerRepository;
    private final BanStore banRepository;
    private final EventStore eventRepository;
    private final MessageService messages;
    private final Settings settings;
    private final SchedulerAdapter scheduler;
    private final AdminNotifier adminNotifier;

    public TempBanCommand(PlayerStore playerRepository, BanStore banRepository,
                          EventStore eventRepository, MessageService messages,
                          Settings settings, SchedulerAdapter scheduler,
                          AdminNotifier adminNotifier) {
        this.playerRepository = playerRepository;
        this.banRepository = banRepository;
        this.eventRepository = eventRepository;
        this.messages = messages;
        this.settings = settings;
        this.scheduler = scheduler;
        this.adminNotifier = adminNotifier;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandUtil.checkPermission(sender, "getout.command.tempban", messages)) return true;

        if (args.length < 2) {
            CommandUtil.sendMessage(sender, messages, "tempban.usage", Map.of());
            return true;
        }

        String targetName = args[0];
        String timeStr = args[1];
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : messages.getString("tempban.default-reason", "违反服务器规则");

        long durationMs = TimeParser.parse(timeStr);
        if (durationMs <= 0) {
            CommandUtil.sendMessage(sender, messages, "general.invalid-time", Map.of());
            return true;
        }

        long expiresAt = System.currentTimeMillis() + durationMs;
        String duration = TimeFormatter.formatDuration(durationMs);

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
                        operatorUuid, operatorName, expiresAt, settings.getServerId());

                if (banId < 0) {
                    scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "general.database-error", Map.of()));
                    return;
                }

                String payload = "expires_at=" + expiresAt + "&ban_id=" + banId;
                eventRepository.insertEvent("TEMPBAN", target.getUuid(), target.getName(),
                        reason, operatorName, settings.getServerId(), payload);

                if (settings.isSyncKickOnlineAfterBan()) {
                    kickOnlinePlayer(target.getUuid(), target.getName(), reason, operatorName, duration, expiresAt, banId);
                }

                adminNotifier.notifyPunishment("TEMPBAN", target.getName(), reason, operatorName, settings.getServerId(), false);

                scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "tempban.success",
                        Map.of("player", target.getName(), "duration", duration, "reason", reason)));

            } catch (Exception e) {
                LoggerUtil.error("Error executing tempban command", e);
                scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "general.database-error", Map.of()));
            }
        });

        return true;
    }

    private void kickOnlinePlayer(UUID uuid, String name, String reason, String operator, String duration, long expiresAt, long banId) {
        scheduler.runGlobal(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                SimpleDateFormat fmt = new SimpleDateFormat(settings.getTimeFormat());
                Map<String, String> placeholders = Map.of(
                        "ban_id", String.valueOf(banId),
                        "reason", reason,
                        "operator", operator,
                        "duration", duration,
                        "left", TimeFormatter.formatRemaining(expiresAt),
                        "expire", fmt.format(new Date(expiresAt))
                );
                Component kickMessage = messages.getComponentList("tempban.kick-message", placeholders);
                player.kick(kickMessage);
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
