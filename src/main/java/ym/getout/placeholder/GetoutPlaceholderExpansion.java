package ym.getout.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ym.getout.GetoutPlugin;
import ym.getout.config.Settings;
import ym.getout.lang.MessageService;
import ym.getout.model.BanRecord;
import ym.getout.model.PlayerIndex;
import ym.getout.scheduler.SchedulerAdapter;
import ym.getout.storage.BanStore;
import ym.getout.storage.PlayerStore;
import ym.getout.util.TimeFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class GetoutPlaceholderExpansion extends PlaceholderExpansion {

    private final GetoutPlugin plugin;
    private final BanStore banRepository;
    private final PlayerStore playerRepository;
    private final MessageService messages;
    private final Settings settings;
    private final SchedulerAdapter scheduler;
    private final PlaceholderCache cache;

    public GetoutPlaceholderExpansion(GetoutPlugin plugin, BanStore banRepository,
                                       PlayerStore playerRepository, MessageService messages,
                                       Settings settings, SchedulerAdapter scheduler, PlaceholderCache cache) {
        this.plugin = plugin;
        this.banRepository = banRepository;
        this.playerRepository = playerRepository;
        this.messages = messages;
        this.settings = settings;
        this.scheduler = scheduler;
        this.cache = cache;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "getout";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ymxc";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null && !params.contains("_")) return null;

        // %getout_is_banned% - 检查玩家自身
        // %getout_is_banned_<name>% - 检查指定玩家
        if (params.startsWith("is_banned_")) {
            String targetName = params.substring("is_banned_".length());
            return getBanField(targetName, "is_banned");
        } else if (params.equals("is_banned") && player != null) {
            return getBanField(player.getUniqueId(), player.getName(), "is_banned");
        }

        if (params.startsWith("ban_reason_")) {
            String targetName = params.substring("ban_reason_".length());
            return getBanField(targetName, "reason");
        } else if (params.equals("ban_reason") && player != null) {
            return getBanField(player.getUniqueId(), player.getName(), "reason");
        }

        if (params.startsWith("ban_expire_")) {
            String targetName = params.substring("ban_expire_".length());
            return getBanField(targetName, "expire");
        } else if (params.equals("ban_expire") && player != null) {
            return getBanField(player.getUniqueId(), player.getName(), "expire");
        }

        if (params.startsWith("ban_left_")) {
            String targetName = params.substring("ban_left_".length());
            return getBanField(targetName, "left");
        } else if (params.equals("ban_left") && player != null) {
            return getBanField(player.getUniqueId(), player.getName(), "left");
        }

        if (params.startsWith("ban_operator_")) {
            String targetName = params.substring("ban_operator_".length());
            return getBanField(targetName, "operator");
        } else if (params.equals("ban_operator") && player != null) {
            return getBanField(player.getUniqueId(), player.getName(), "operator");
        }

        if (params.equals("server_id")) {
            return settings.getServerId();
        }

        return null;
    }

    private String getBanField(String targetName, String field) {
        // 先查缓存
        String cacheKey = targetName.toLowerCase() + "." + field;
        String cached = cache.get(cacheKey);
        if (cached != null) return cached;

        // 异步刷新缓存
        scheduler.runAsync(() -> {
            try {
                PlayerIndex idx = playerRepository.findByName(targetName);
                if (idx == null) {
                    cache.put(cacheKey, messages.getString("placeholder.none", "无"));
                    return;
                }
                BanRecord ban = banRepository.findActiveBan(idx.getUuid());
                String value = resolveBanField(ban, field);
                cache.put(cacheKey, value);
            } catch (Exception e) {
                cache.put(cacheKey, messages.getString("placeholder.unknown", "未知"));
            }
        });

        // 返回缓存默认值
        return messages.getString("placeholder.loading", "加载中");
    }

    private String getBanField(UUID uuid, String name, String field) {
        String cacheKey = uuid.toString() + "." + field;
        String cached = cache.get(cacheKey);
        if (cached != null) return cached;

        scheduler.runAsync(() -> {
            try {
                BanRecord ban = banRepository.findActiveBan(uuid);
                String value = resolveBanField(ban, field);
                cache.put(cacheKey, value);
            } catch (Exception e) {
                cache.put(cacheKey, messages.getString("placeholder.unknown", "未知"));
            }
        });

        return messages.getString("placeholder.loading", "加载中");
    }

    private String resolveBanField(BanRecord ban, String field) {
        if (ban == null || !ban.isEffective()) {
            return switch (field) {
                case "is_banned" -> messages.getString("placeholder.no", "否");
                default -> messages.getString("placeholder.none", "无");
            };
        }

        SimpleDateFormat fmt = new SimpleDateFormat(settings.getTimeFormat());
        return switch (field) {
            case "is_banned" -> messages.getString("placeholder.yes", "是");
            case "reason" -> ban.getReason() != null ? ban.getReason() : "";
            case "expire" -> ban.isPermanent() ? messages.getString("placeholder.never", "永久")
                    : fmt.format(new Date(ban.getExpiresAt()));
            case "left" -> ban.isPermanent() ? messages.getString("placeholder.never", "永久")
                    : TimeFormatter.formatRemaining(ban.getExpiresAt());
            case "operator" -> ban.getOperatorName() != null ? ban.getOperatorName() : "";
            default -> "";
        };
    }
}
