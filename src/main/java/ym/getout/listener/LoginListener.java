package ym.getout.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import ym.getout.config.Settings;
import ym.getout.database.DatabaseManager;
import ym.getout.storage.BanStore;
import ym.getout.lang.MessageService;
import ym.getout.model.BanRecord;
import ym.getout.util.TextUtil;
import ym.getout.util.TimeFormatter;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 登录拦截监听器，使用 AsyncPlayerPreLoginEvent 在异步线程中检查封禁状态。
 */
public class LoginListener implements Listener {

    private final DatabaseManager db;
    private final BanStore banRepository;
    private final Settings settings;
    private final MessageService messages;

    public LoginListener(DatabaseManager db, BanStore banRepository, Settings settings, MessageService messages) {
        this.db = db;
        this.banRepository = banRepository;
        this.settings = settings;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        if (settings.isDatabaseEnabled() && (db == null || !db.isInitialized())) {
            if (!settings.isFailOpenOnDatabaseError()) {
                // fail-close: 拒绝进入
                List<String> kickLines = messages.getStringList("login.database-fail-close");
                String kickMessage = String.join("\n", kickLines);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
                ym.getout.util.LoggerUtil.warn("Database not ready, denying login for " + name + " (fail-close mode)");
            } else {
                // fail-open: 允许进入
                ym.getout.util.LoggerUtil.warn("Database not ready, allowing login for " + name + " (fail-open mode)");
            }
            return;
        }

        try {
            BanRecord ban = banRepository.findActiveBan(uuid);
            if (ban == null) return;

            // 检查是否已过期
            if (ban.isExpired()) {
                // 异步标记为 inactive（当前已在异步线程中）
                banRepository.deactivateBan(uuid);
                return;
            }

            // 封禁有效，拒绝登录
            SimpleDateFormat fmt = new SimpleDateFormat(settings.getTimeFormat());
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("reason", ban.getReason() != null ? ban.getReason() : "");
            placeholders.put("operator", ban.getOperatorName() != null ? ban.getOperatorName() : "");
            placeholders.put("time", fmt.format(new Date(ban.getCreatedAt())));

            String kickMessage;
            if (ban.isPermanent()) {
                placeholders.put("left", messages.getString("placeholder.never", "永久"));
                placeholders.put("expire", messages.getString("placeholder.never", "永久"));
                kickMessage = String.join("\n", messages.getFormattedList("login.banned", placeholders));
            } else {
                placeholders.put("left", TimeFormatter.formatRemaining(ban.getExpiresAt()));
                placeholders.put("expire", fmt.format(new Date(ban.getExpiresAt())));
                kickMessage = String.join("\n", messages.getFormattedList("login.temp-banned", placeholders));
            }

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);

        } catch (Exception e) {
            ym.getout.util.LoggerUtil.error("Error checking ban status for " + name, e);
            if (!settings.isFailOpenOnDatabaseError()) {
                List<String> kickLines = messages.getStringList("login.database-fail-close");
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, String.join("\n", kickLines));
            }
        }
    }
}
