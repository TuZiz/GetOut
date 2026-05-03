package ym.getout;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ym.getout.command.BanCommand;
import ym.getout.command.GetoutCommand;
import ym.getout.command.KickCommand;
import ym.getout.command.TempBanCommand;
import ym.getout.command.UnbanCommand;
import ym.getout.config.ConfigService;
import ym.getout.config.Settings;
import ym.getout.database.DatabaseManager;
import ym.getout.database.repository.BanRepository;
import ym.getout.database.repository.EventRepository;
import ym.getout.database.repository.PlayerRepository;
import ym.getout.database.repository.SyncStateRepository;
import ym.getout.database.schema.SchemaInitializer;
import ym.getout.lang.MessageService;
import ym.getout.listener.JoinListener;
import ym.getout.listener.LoginListener;
import ym.getout.notify.AdminNotifier;
import ym.getout.placeholder.GetoutPlaceholderExpansion;
import ym.getout.placeholder.PlaceholderCache;
import ym.getout.scheduler.SchedulerAdapter;
import ym.getout.scheduler.SchedulerProvider;
import ym.getout.storage.BanStore;
import ym.getout.storage.EventStore;
import ym.getout.storage.PlayerStore;
import ym.getout.storage.SyncStateStore;
import ym.getout.storage.yaml.YamlBanStore;
import ym.getout.storage.yaml.YamlEventStore;
import ym.getout.storage.yaml.YamlPlayerStore;
import ym.getout.storage.yaml.YamlSyncStateStore;
import ym.getout.sync.EventProcessor;
import ym.getout.sync.SyncService;
import ym.getout.util.LoggerUtil;

public class GetoutPlugin extends JavaPlugin {

    private ConfigService configService;
    private MessageService messageService;
    private SchedulerAdapter scheduler;
    private DatabaseManager databaseManager;
    private PlayerStore playerStore;
    private BanStore banStore;
    private EventStore eventStore;
    private SyncStateStore syncStateStore;
    private EventProcessor eventProcessor;
    private SyncService syncService;
    private PlaceholderCache placeholderCache;
    private AdminNotifier adminNotifier;
    private volatile boolean componentsEnabled = false;
    private volatile String runtimeStorageType = "uninitialized";

    @Override
    public void onEnable() {
        configService = new ConfigService(this);
        configService.init();
        Settings settings = configService.getSettings();

        LoggerUtil.init(getLogger(), settings.isDebug());
        LoggerUtil.info("Enabling Getout v" + getDescription().getVersion());

        scheduler = SchedulerProvider.create(this);

        messageService = new MessageService(getDataFolder(), settings.getLang());
        messageService.init();

        placeholderCache = new PlaceholderCache(settings.getPapiCacheSeconds());
        adminNotifier = new AdminNotifier(settings, messageService);

        if (settings.isDatabaseEnabled()) {
            databaseManager = new DatabaseManager(settings);

            scheduler.runAsync(() -> {
                try {
                    if (!databaseManager.init()) {
                        handleDatabaseStartupFailure(settings);
                        return;
                    }
                    SchemaInitializer schemaInitializer = new SchemaInitializer(databaseManager, settings);
                    if (!schemaInitializer.createTables()) {
                        handleDatabaseStartupFailure(settings);
                        return;
                    }
                    playerStore = new PlayerRepository(databaseManager, settings);
                    banStore = new BanRepository(databaseManager, settings);
                    eventStore = new EventRepository(databaseManager, settings);
                    syncStateStore = new SyncStateRepository(databaseManager, settings);
                    scheduler.runGlobal(() -> enableComponents(settings, "database"));
                    LoggerUtil.info("Database initialized and sync service started");
                } catch (Exception e) {
                    LoggerUtil.error("Failed to initialize database", e);
                    handleDatabaseStartupFailure(settings);
                }
            });
        } else {
            setupYamlStorage();
            enableComponents(settings, "yaml");
        }

        LoggerUtil.info("Getout enabled successfully");
    }

    private void handleDatabaseStartupFailure(Settings settings) {
        if (settings.isDatabaseFallbackYamlEnabled()) {
            LoggerUtil.warn("Database unavailable, falling back to YAML storage for this runtime");
            scheduler.runAsync(() -> {
                settings.setStorageType("yaml");
                setupYamlStorage();
                scheduler.runGlobal(() -> enableComponents(settings, "yaml-fallback"));
            });
            return;
        }

        LoggerUtil.error("Database unavailable and storage.database-failure-strategy=fail-fast; disabling Getout");
        scheduler.runGlobal(() -> Bukkit.getPluginManager().disablePlugin(this));
    }

    private void setupYamlStorage() {
        playerStore = new YamlPlayerStore(getDataFolder());
        banStore = new YamlBanStore(getDataFolder());
        eventStore = new YamlEventStore(getDataFolder());
        syncStateStore = new YamlSyncStateStore(getDataFolder());
        LoggerUtil.info("YAML storage initialized");
    }

    private void enableComponents(Settings settings, String storageMode) {
        if (componentsEnabled) return;
        componentsEnabled = true;
        runtimeStorageType = storageMode;

        eventProcessor = new EventProcessor(eventStore, banStore, syncStateStore, settings, scheduler, messageService, adminNotifier);
        syncService = new SyncService(databaseManager, banStore, eventStore, settings, scheduler, eventProcessor);
        syncService.start();

        Bukkit.getPluginManager().registerEvents(new LoginListener(databaseManager, banStore, settings, messageService), this);
        Bukkit.getPluginManager().registerEvents(new JoinListener(playerStore, settings, scheduler), this);

        registerCommands(settings);
        registerPAPI();
        LoggerUtil.info("Getout runtime components enabled (storage=" + storageMode + ")");
    }

    @Override
    public void onDisable() {
        LoggerUtil.info("Disabling Getout...");

        if (scheduler != null) {
            scheduler.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        LoggerUtil.info("Getout disabled");
    }

    public void reload() {
        configService.reload();
        Settings settings = configService.getSettings();
        if (runtimeStorageType.startsWith("yaml")
                && settings.isDatabaseEnabled()
                && (databaseManager == null || !databaseManager.isInitialized())) {
            settings.setStorageType("yaml");
            LoggerUtil.warn("Reload kept runtime YAML storage because database is not initialized");
        }

        LoggerUtil.init(getLogger(), settings.isDebug());
        messageService.reload(settings.getLang());

        if (placeholderCache != null) {
            placeholderCache.invalidateAll();
        }

        LoggerUtil.debug("Plugin reloaded");
    }

    private void registerCommands(Settings settings) {
        BanCommand banCommand = new BanCommand(playerStore, banStore, eventStore, messageService, settings, scheduler, adminNotifier);
        TempBanCommand tempBanCommand = new TempBanCommand(playerStore, banStore, eventStore, messageService, settings, scheduler, adminNotifier);
        UnbanCommand unbanCommand = new UnbanCommand(playerStore, banStore, eventStore, messageService, settings, scheduler, adminNotifier);
        KickCommand kickCommand = new KickCommand(playerStore, eventStore, messageService, settings, scheduler, adminNotifier);
        GetoutCommand getoutCommand = new GetoutCommand(this, settings, messageService, scheduler, databaseManager);

        registerCommandSafe("ban", banCommand, banCommand);
        registerCommandSafe("tempban", tempBanCommand, tempBanCommand);
        registerCommandSafe("unban", unbanCommand, unbanCommand);
        registerCommandSafe("kick", kickCommand, kickCommand);
        registerCommandSafe("getout", getoutCommand, getoutCommand);
    }

    private void registerCommandSafe(String name, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter tabCompleter) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (tabCompleter != null) {
                cmd.setTabCompleter(tabCompleter);
            }
        } else {
            LoggerUtil.warn("Failed to register command: " + name);
        }
    }

    private void registerPAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            Settings settings = configService.getSettings();
            GetoutPlaceholderExpansion expansion = new GetoutPlaceholderExpansion(
                    this, banStore, playerStore, messageService, settings, scheduler, placeholderCache);
            expansion.register();
            LoggerUtil.info("PlaceholderAPI expansion registered");
        } else {
            LoggerUtil.info("PlaceholderAPI not found, skipping expansion registration");
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
