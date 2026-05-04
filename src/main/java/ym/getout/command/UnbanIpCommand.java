package ym.getout.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ym.getout.config.Settings;
import ym.getout.lang.MessageService;
import ym.getout.model.IpBanRecord;
import ym.getout.model.PlayerIndex;
import ym.getout.notify.AdminNotifier;
import ym.getout.scheduler.SchedulerAdapter;
import ym.getout.storage.EventStore;
import ym.getout.storage.IpBanStore;
import ym.getout.storage.PlayerStore;
import ym.getout.util.LoggerUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class UnbanIpCommand implements CommandExecutor, TabCompleter {

    private static final UUID IP_ONLY_UUID = new UUID(0L, 0L);

    private final PlayerStore playerRepository;
    private final IpBanStore ipBanRepository;
    private final EventStore eventRepository;
    private final MessageService messages;
    private final Settings settings;
    private final SchedulerAdapter scheduler;
    private final AdminNotifier adminNotifier;

    public UnbanIpCommand(PlayerStore playerRepository, IpBanStore ipBanRepository,
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
        if (!CommandUtil.checkPermission(sender, "getout.command.unbanip", messages)) return true;

        if (args.length < 1) {
            CommandUtil.sendMessage(sender, messages, "unbanip.usage", Map.of());
            return true;
        }

        String targetInput = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : messages.getString("unbanip.default-reason", "管理员手动解除 IP 封禁");
        boolean directIp = looksLikeIp(targetInput);
        String onlineIp = directIp ? "" : CommandUtil.findOnlineIp(targetInput);
        String operatorName = sender instanceof Player player ? player.getName() : "Console";

        scheduler.runAsync(() -> {
            try {
                UUID targetUuid = IP_ONLY_UUID;
                String targetName = targetInput;
                String ip = targetInput;

                if (!directIp) {
                    PlayerIndex target = playerRepository.findByNameOrUuid(targetInput);
                    if (target == null) {
                        scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "general.player-not-found",
                                Map.of("player", targetInput)));
                        return;
                    }

                    targetUuid = target.getUuid();
                    targetName = target.getName();
                    ip = CommandUtil.firstNonBlank(onlineIp, target.getLastIp());
                    if (ip.isBlank()) {
                        String finalTargetName = targetName;
                        scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "unbanip.no-ip",
                                Map.of("player", finalTargetName)));
                        return;
                    }
                }

                IpBanRecord activeIpBan = ipBanRepository.findActiveIpBan(ip);
                if (activeIpBan == null) {
                    String finalTargetName = targetName;
                    String finalIp = ip;
                    scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "unbanip.not-banned",
                            Map.of("player", finalTargetName, "ip", finalIp)));
                    return;
                }

                ipBanRepository.deactivateIpBan(ip);
                String eventTargetName = IP_ONLY_UUID.equals(targetUuid) ? "IP" : targetName;
                eventRepository.insertEvent("UNBAN_IP", targetUuid, eventTargetName,
                        reason, operatorName, settings.getServerId(), "ip=" + ip + "&ip_ban_id=" + activeIpBan.getId());
                adminNotifier.notifyPunishment("UNBAN_IP", targetName, reason, operatorName, settings.getServerId(), false);

                String finalTargetName = targetName;
                String finalIp = ip;
                scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "unbanip.success",
                        Map.of("player", finalTargetName, "ip", finalIp,
                                "ip_ban_id", String.valueOf(activeIpBan.getId()), "reason", reason)));
            } catch (Exception e) {
                LoggerUtil.error("Error executing unbanip command", e);
                scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "general.database-error", Map.of()));
            }
        });

        return true;
    }

    private boolean looksLikeIp(String input) {
        return input != null && (input.contains(".") || input.contains(":"));
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
