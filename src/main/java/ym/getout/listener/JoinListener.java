package ym.getout.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ym.getout.config.Settings;
import ym.getout.scheduler.SchedulerAdapter;
import ym.getout.storage.PlayerStore;

import java.util.UUID;

/**
 * 玩家加入监听器，异步更新玩家索引。
 */
public class JoinListener implements Listener {

    private final PlayerStore playerRepository;
    private final Settings settings;
    private final SchedulerAdapter scheduler;

    public JoinListener(PlayerStore playerRepository, Settings settings, SchedulerAdapter scheduler) {
        this.playerRepository = playerRepository;
        this.settings = settings;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        String serverId = settings.getServerId();

        // 异步写入玩家索引
        scheduler.runAsync(() -> {
            try {
                playerRepository.upsert(uuid, name, serverId);
            } catch (Exception e) {
                ym.getout.util.LoggerUtil.error("Failed to update player index for " + name, e);
            }
        });
    }
}
