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
import ym.getout.storage.PlayerStore;
import ym.getout.util.LoggerUtil;
import ym.getout.util.UuidUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class KickCommand implements CommandExecutor, TabCompleter {

    private final PlayerStore playerRepository;
    private final EventStore eventRepository;
    private final MessageService messages;
    private final Settings settings;
    private final SchedulerAdapter scheduler;
    private final AdminNotifier adminNotifier;

    public KickCommand(PlayerStore playerRepository, EventStore eventRepository,
                       MessageService messages, Settings settings, SchedulerAdapter scheduler,
                       AdminNotifier adminNotifier) {
        this.playerRepository = playerRepository;
        this.eventRepository = eventRepository;
        this.messages = messages;
        this.settings = settings;
        this.scheduler = scheduler;
        this.adminNotifier = adminNotifier;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandUtil.checkPermission(sender, "getout.command.kick", messages)) return true;

        if (args.length < 1) {
            CommandUtil.sendMessage(sender, messages, "kick.usage", Map.of());
            return true;
        }

        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : messages.getString("kick.default-reason", "你已被服务器踢出");

        Player targetPlayer = Bukkit.getPlayerExact(targetName);
        if (targetPlayer == null) {
            UUID uuid = UuidUtil.parse(targetName);
            if (uuid != null) {
                targetPlayer = Bukkit.getPlayer(uuid);
            }
        }

        UUID resolvedUuid = targetPlayer != null ? targetPlayer.getUniqueId() : null;
        String resolvedName = targetPlayer != null ? targetPlayer.getName() : targetName;

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            if (!settings.isKickCrossServer()) {
                CommandUtil.sendMessage(sender, messages, "general.target-offline",
                        Map.of("player", targetName));
                return true;
            }

            PlayerIndex index = playerRepository.findByNameOrUuid(targetName);
            if (index == null) {
                CommandUtil.sendMessage(sender, messages, "general.player-not-found",
                        Map.of("player", targetName));
                return true;
            }
            resolvedUuid = index.getUuid();
            resolvedName = index.getName();
        }

        final UUID targetUuid = resolvedUuid;
        final String targetDisplayName = resolvedName;
        final String operatorName = sender instanceof Player player ? player.getName() : "Console";

        scheduler.runAsync(() -> {
            try {
                eventRepository.insertEvent("KICK", targetUuid, targetDisplayName,
                        reason, operatorName, settings.getServerId(), "");
            } catch (Exception e) {
                LoggerUtil.error("Failed to write kick sync event", e);
            }
        });

        Map<String, String> placeholders = Map.of(
                "reason", reason,
                "operator", operatorName
        );
        Component kickMessage = messages.getComponentList("kick.message", placeholders);

        scheduler.runGlobal(() -> {
            Player current = Bukkit.getPlayer(targetUuid);
            if (current != null && current.isOnline()) {
                current.kick(kickMessage);
            }
        });

        adminNotifier.notifyPunishment("KICK", targetDisplayName, reason, operatorName, settings.getServerId(), false);

        CommandUtil.sendMessage(sender, messages, "kick.success",
                Map.of("player", targetDisplayName, "reason", reason));

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
