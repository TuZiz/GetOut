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
import ym.getout.storage.EventStore;
import ym.getout.storage.IpBanStore;
import ym.getout.storage.PlayerStore;
import ym.getout.util.LoggerUtil;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class BanIpCommand implements CommandExecutor, TabCompleter {

    private final PlayerStore playerRepository;
    private final IpBanStore ipBanRepository;
    private final EventStore eventRepository;
    private final MessageService messages;
    private final Settings settings;
    private final SchedulerAdapter scheduler;
    private final AdminNotifier adminNotifier;

    public BanIpCommand(PlayerStore playerRepository, IpBanStore ipBanRepository,
                        EventStore eventRepository, MessageService messages,
                        Settings settings, SchedulerAdapter scheduler,
                        AdminNotifier adminNotifier) {
        this.playerRepository = playerRepository;
        this.ipBanRepository = ipBanRepository;
        this.eventRepository = eventRepository;
        this.messages = messages;
        this.settings = settings;
        this.scheduler = scheduler;
        this.adminNotifier = adminNotifier;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandUtil.checkPermission(sender, "getout.command.banip", messages)) return true;

        if (args.length < 1) {
            CommandUtil.sendMessage(sender, messages, "banip.usage", Map.of());
            return true;
        }

        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : messages.getString("banip.default-reason", "IP 被管理员封禁");
        String onlineIp = CommandUtil.findOnlineIp(targetName);
        UUID operatorUuid = null;
        String operatorName = "Console";
        if (sender instanceof Player player) {
            operatorUuid = player.getUniqueId();
            operatorName = player.getName();
        }
        UUID finalOperatorUuid = operatorUuid;
        String finalOperatorName = operatorName;

        scheduler.runAsync(() -> {
            try {
                PlayerIndex target = playerRepository.findByNameOrUuid(targetName);
                if (target == null) {
                    scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "general.player-not-found",
                            Map.of("player", targetName)));
                    return;
                }

                String ip = CommandUtil.firstNonBlank(onlineIp, target.getLastIp());
                if (ip == null || ip.isBlank()) {
                    scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "banip.no-ip",
                            Map.of("player", target.getName())));
                    return;
                }

                long ipBanId = ipBanRepository.createIpBan(ip, reason, finalOperatorUuid, finalOperatorName, settings.getServerId());
                if (ipBanId < 0) {
                    scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "general.database-error", Map.of()));
                    return;
                }

                eventRepository.insertEvent("IP_BAN", target.getUuid(), target.getName(),
                        reason, finalOperatorName, settings.getServerId(), "ip=" + ip + "&ip_ban_id=" + ipBanId);
                if (settings.isSyncKickOnlineAfterBan()) {
                    kickOnlinePlayer(target.getUuid(), target.getName(), ip, reason, finalOperatorName, ipBanId);
                }
                adminNotifier.notifyPunishment("IP_BAN", target.getName(), reason, finalOperatorName, settings.getServerId(), false);

                scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "banip.success",
                        Map.of("player", target.getName(), "ip", ip, "ip_ban_id", String.valueOf(ipBanId), "reason", reason)));
            } catch (Exception e) {
                LoggerUtil.error("Error executing banip command", e);
                scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "general.database-error", Map.of()));
            }
        });

        return true;
    }

    private void kickOnlinePlayer(UUID uuid, String name, String ip, String reason, String operator, long ipBanId) {
        scheduler.runGlobal(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Map<String, String> placeholders = Map.of(
                        "player", name,
                        "ip", ip,
                        "ip_ban_id", String.valueOf(ipBanId),
                        "reason", reason,
                        "operator", operator,
                        "time", new SimpleDateFormat(settings.getTimeFormat()).format(new Date())
                );
                Component kickMessage = messages.getComponentList("banip.kick-message", placeholders);
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
