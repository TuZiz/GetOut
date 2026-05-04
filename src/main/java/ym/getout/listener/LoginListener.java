package ym.getout.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import ym.getout.config.Settings;
import ym.getout.database.DatabaseManager;
import ym.getout.lang.MessageService;
import ym.getout.model.BanRecord;
import ym.getout.model.IpBanRecord;
import ym.getout.storage.BanStore;
import ym.getout.storage.IpBanStore;
import ym.getout.util.TimeFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoginListener implements Listener {

    private final DatabaseManager db;
    private final BanStore banRepository;
    private final IpBanStore ipBanRepository;
    private final Settings settings;
    private final MessageService messages;

    public LoginListener(DatabaseManager db, BanStore banRepository, IpBanStore ipBanRepository,
                         Settings settings, MessageService messages) {
        this.db = db;
        this.banRepository = banRepository;
        this.ipBanRepository = ipBanRepository;
        this.settings = settings;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        if (settings.isDatabaseEnabled() && (db == null || !db.isInitialized())) {
            if (!settings.isFailOpenOnDatabaseError()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        messages.getComponentList("login.database-fail-close", Map.of()));
                ym.getout.util.LoggerUtil.warn("Database not ready, denying login for " + name + " (fail-close mode)");
            } else {
                ym.getout.util.LoggerUtil.warn("Database not ready, allowing login for " + name + " (fail-open mode)");
            }
            return;
        }

        try {
            String ip = event.getAddress() != null ? event.getAddress().getHostAddress() : "";
            if (!ip.isBlank()) {
                IpBanRecord ipBan = ipBanRepository.findActiveIpBan(ip);
                if (ipBan != null) {
                    Map<String, String> ipPlaceholders = new HashMap<>();
                    ipPlaceholders.put("player", name);
                    ipPlaceholders.put("ip", ip);
                    ipPlaceholders.put("ip_ban_id", String.valueOf(ipBan.getId()));
                    ipPlaceholders.put("reason", ipBan.getReason() != null ? ipBan.getReason() : "");
                    ipPlaceholders.put("operator", ipBan.getOperatorName() != null ? ipBan.getOperatorName() : "");
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                            messages.getComponentList("login.ip-banned", ipPlaceholders));
                    return;
                }
            }

            BanRecord ban = banRepository.findActiveBan(uuid);
            if (ban == null) return;

            if (ban.isExpired()) {
                banRepository.deactivateBan(uuid);
                return;
            }

            SimpleDateFormat fmt = new SimpleDateFormat(settings.getTimeFormat());
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", name);
            placeholders.put("ban_id", String.valueOf(ban.getId()));
            placeholders.put("reason", ban.getReason() != null ? ban.getReason() : "");
            placeholders.put("operator", ban.getOperatorName() != null ? ban.getOperatorName() : "");
            placeholders.put("time", fmt.format(new Date(ban.getCreatedAt())));

            Component kickMessage;
            if (ban.isPermanent()) {
                placeholders.put("left", messages.getString("placeholder.never", "永久"));
                placeholders.put("expire", messages.getString("placeholder.never", "永久"));
                kickMessage = messages.getComponentList("login.banned", placeholders);
            } else {
                placeholders.put("left", TimeFormatter.formatRemaining(ban.getExpiresAt()));
                placeholders.put("expire", fmt.format(new Date(ban.getExpiresAt())));
                kickMessage = messages.getComponentList("login.temp-banned", placeholders);
            }

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
        } catch (Exception e) {
            ym.getout.util.LoggerUtil.error("Error checking ban status for " + name, e);
            if (!settings.isFailOpenOnDatabaseError()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        messages.getComponentList("login.database-fail-close", Map.of()));
            }
        }
    }
}
