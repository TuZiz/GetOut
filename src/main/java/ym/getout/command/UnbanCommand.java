package ym.getout.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ym.getout.config.Settings;
import ym.getout.lang.MessageService;
import ym.getout.model.BanRecord;
import ym.getout.model.PlayerIndex;
import ym.getout.notify.AdminNotifier;
import ym.getout.scheduler.SchedulerAdapter;
import ym.getout.storage.BanStore;
import ym.getout.storage.EventStore;
import ym.getout.storage.PlayerStore;
import ym.getout.util.LoggerUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class UnbanCommand implements CommandExecutor, TabCompleter {

    private final PlayerStore playerRepository;
    private final BanStore banRepository;
    private final EventStore eventRepository;
    private final MessageService messages;
    private final Settings settings;
    private final SchedulerAdapter scheduler;
    private final AdminNotifier adminNotifier;

    public UnbanCommand(PlayerStore playerRepository, BanStore banRepository,
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
        if (!CommandUtil.checkPermission(sender, "getout.command.unban", messages)) return true;

        if (args.length < 1) {
            CommandUtil.sendMessage(sender, messages, "unban.usage", Map.of());
            return true;
        }

        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : messages.getString("unban.default-reason", "管理员手动解封");

        scheduler.runAsync(() -> {
            try {
                PlayerIndex target = playerRepository.findByNameOrUuid(targetName);
                if (target == null) {
                    scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "general.player-not-found",
                            Map.of("player", targetName)));
                    return;
                }

                BanRecord activeBan = banRepository.findActiveBan(target.getUuid());
                if (activeBan == null) {
                    scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "unban.not-banned",
                            Map.of("player", target.getName())));
                    return;
                }

                String operatorName = sender instanceof Player player ? player.getName() : "Console";
                banRepository.deactivateBan(target.getUuid());

                eventRepository.insertEvent("UNBAN", target.getUuid(), target.getName(),
                        reason, operatorName, settings.getServerId(), "ban_id=" + activeBan.getId());

                adminNotifier.notifyPunishment("UNBAN", target.getName(), reason, operatorName, settings.getServerId(), false);

                scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "unban.success",
                        Map.of("player", target.getName(), "reason", reason, "ban_id", String.valueOf(activeBan.getId()))));
            } catch (Exception e) {
                LoggerUtil.error("Error executing unban command", e);
                scheduler.runGlobal(() -> CommandUtil.sendMessage(sender, messages, "general.database-error", Map.of()));
            }
        });

        return true;
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
