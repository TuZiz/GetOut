package ym.getout.sync;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ym.getout.config.Settings;
import ym.getout.lang.MessageService;
import ym.getout.model.BanRecord;
import ym.getout.model.IpBanRecord;
import ym.getout.model.SyncEvent;
import ym.getout.notify.AdminNotifier;
import ym.getout.scheduler.SchedulerAdapter;
import ym.getout.storage.BanStore;
import ym.getout.storage.EventStore;
import ym.getout.storage.IpBanStore;
import ym.getout.storage.SyncStateStore;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDeactivateIpBanWhenProcessingUnbanIp() {
        UUID targetUuid = UUID.randomUUID();
        FakeEventStore eventStore = new FakeEventStore(List.of(
                new SyncEvent(1L, "UNBAN_IP", targetUuid, "Tester", "reason", "Console", System.currentTimeMillis(), "server-b", "ip=127.0.0.1&ip_ban_id=7")
        ));
        FakeBanStore banStore = new FakeBanStore();
        FakeIpBanStore ipBanStore = new FakeIpBanStore();
        FakeSyncStateStore syncStateStore = new FakeSyncStateStore();

        EventProcessor processor = new EventProcessor(
                eventStore,
                banStore,
                ipBanStore,
                syncStateStore,
                newSettings(),
                new ImmediateScheduler(),
                newMessageService(),
                newNotifier(),
                0L
        );

        processor.processNewEvents();

        assertTrue(ipBanStore.deactivatedIps.contains("127.0.0.1"));
        assertEquals(1L, syncStateStore.lastSavedEventId);
    }

    @Test
    void shouldDeactivateBanAndIpWhenProcessingUnban() {
        UUID targetUuid = UUID.randomUUID();
        FakeEventStore eventStore = new FakeEventStore(List.of(
                new SyncEvent(5L, "UNBAN", targetUuid, "Tester", "reason", "Console", System.currentTimeMillis(), "server-b", "ip=10.0.0.8&ban_id=12")
        ));
        FakeBanStore banStore = new FakeBanStore();
        FakeIpBanStore ipBanStore = new FakeIpBanStore();
        FakeSyncStateStore syncStateStore = new FakeSyncStateStore();

        EventProcessor processor = new EventProcessor(
                eventStore,
                banStore,
                ipBanStore,
                syncStateStore,
                newSettings(),
                new ImmediateScheduler(),
                newMessageService(),
                newNotifier(),
                0L
        );

        processor.processNewEvents();

        assertEquals(targetUuid, banStore.lastDeactivatedUuid);
        assertTrue(ipBanStore.deactivatedIps.contains("10.0.0.8"));
        assertEquals(5L, syncStateStore.lastSavedEventId);
    }

    private Settings newSettings() {
        Settings settings = new Settings();
        YamlConfiguration config = new YamlConfiguration();
        config.set("server-id", "server-a");
        config.set("admin-notify.enabled", false);
        config.set("sync.kick-online-after-ban", false);
        settings.load(config);
        return settings;
    }

    private MessageService newMessageService() {
        MessageService messageService = new MessageService(new File(tempDir.toFile(), "messages"), "zh_cn");
        messageService.init();
        return messageService;
    }

    private AdminNotifier newNotifier() {
        return new AdminNotifier(newSettings(), newMessageService(), new ImmediateScheduler());
    }

    private static final class ImmediateScheduler implements SchedulerAdapter {

        @Override
        public void runAsync(Runnable task) {
            task.run();
        }

        @Override
        public void runGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void runForPlayer(org.bukkit.entity.Player player, Runnable task) {
            task.run();
        }

        @Override
        public void runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
            // Not needed for unit tests.
        }

        @Override
        public void shutdown() {
            // Nothing to close in tests.
        }
    }

    private static final class FakeEventStore implements EventStore {

        private final List<SyncEvent> events;

        private FakeEventStore(List<SyncEvent> events) {
            this.events = events;
        }

        @Override
        public long insertEvent(String eventType, UUID targetUuid, String targetName, String reason, String operatorName, String serverId, String payload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SyncEvent> findEventsAfter(long lastId, boolean includeOwn, String serverId) {
            return events.stream().filter(event -> event.getId() > lastId).toList();
        }

        @Override
        public int cleanExpiredEvents(int retentionDays) {
            return 0;
        }
    }

    private static final class FakeBanStore implements BanStore {

        private UUID lastDeactivatedUuid;

        @Override
        public long createBan(UUID uuid, String name, String reason, UUID operatorUuid, String operatorName, Long expiresAt, String serverId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BanRecord findActiveBan(UUID uuid) {
            return null;
        }

        @Override
        public void deactivateBan(UUID uuid) {
            lastDeactivatedUuid = uuid;
        }

        @Override
        public int deactivateExpiredBans() {
            return 0;
        }
    }

    private static final class FakeIpBanStore implements IpBanStore {

        private final List<String> deactivatedIps = new ArrayList<>();

        @Override
        public long createIpBan(String ip, String reason, UUID operatorUuid, String operatorName, String serverId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IpBanRecord findActiveIpBan(String ip) {
            return null;
        }

        @Override
        public void deactivateIpBan(String ip) {
            deactivatedIps.add(ip);
        }
    }

    private static final class FakeSyncStateStore implements SyncStateStore {

        private long lastSavedEventId;

        @Override
        public long getLastProcessedEventId(String serverId) {
            return 0;
        }

        @Override
        public void saveLastProcessedEventId(String serverId, long eventId) {
            lastSavedEventId = eventId;
        }
    }
}
