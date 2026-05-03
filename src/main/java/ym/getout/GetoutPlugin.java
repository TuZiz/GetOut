package ym.getout;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ym.getout.command.BanCommand;
import ym.getout.command.GetoutCommand;
import ym.getout.command.KickCommand;
import ym.getout.command.TempBanCommand;
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

        if (settings.isDatabaseEnabled()) {
            databaseManager = new DatabaseManager(settings);
            playerStore = new PlayerRepository(databaseManager, settings);
            banStore = new BanRepository(databaseManager, settings);
            eventStore = new EventRepository(databaseManager, settings);
            syncStateStore = new SyncStateRepository(databaseManager, settings);

            scheduler.runAsync(() -> {
                try {
                    databaseManager.init();
                    SchemaInitializer schemaInitializer = new SchemaInitializer(databaseManager, settings);
                    schemaInitializer.createTables();
                    enableSync(settings);
                    LoggerUtil.info("Database initialized and sync service started");
                } catch (Exception e) {
                    LoggerUtil.error("Failed to initialize database", e);
                    if (!settings.isFailOpenOnDatabaseError()) {
                        LoggerUtil.error("Fail-close mode: players will be denied login until database is available");
                    }
                }
            });
        } else {
            playerStore = new YamlPlayerStore(getDataFolder());
            banStore = new YamlBanStore(getDataFolder());
            eventStore = new YamlEventStore(getDataFolder());
            syncStateStore = new YamlSyncStateStore(getDataFolder());
            enableSync(settings);
            LoggerUtil.info("YAML storage initialized");
        }

        placeholderCache = new PlaceholderCache(settings.getPapiCacheSeconds());

        Bukkit.getPluginManager().registerEvents(new LoginListener(databaseManager, banStore, settings, messageService), this);
        Bukkit.getPluginManager().registerEvents(new JoinListener(playerStore, settings, scheduler), this);

        registerCommands(settings);
        registerPAPI();

        LoggerUtil.info("Getout enabled successfully");
    }

    private void enableSync(Settings settings) {
        eventProcessor = new EventProcessor(eventStore, banStore, syncStateStore, settings, scheduler);
        syncService = new SyncService(databaseManager, banStore, eventStore, settings, scheduler, eventProcessor);
        syncService.start();
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

        LoggerUtil.init(getLogger(), settings.isDebug());
        messageService.reload(settings.getLang());

        if (placeholderCache != null) {
            placeholderCache.invalidateAll();
        }

        LoggerUtil.debug("Plugin reloaded");
    }

    private void registerCommands(Settings settings) {
        BanCommand banCommand = new BanCommand(playerStore, banStore, eventStore, messageService, settings, scheduler);
        TempBanCommand tempBanCommand = new TempBanCommand(playerStore, banStore, eventStore, messageService, settings, scheduler);
        KickCommand kickCommand = new KickCommand(playerStore, eventStore, messageService, settings, scheduler);
        GetoutCommand getoutCommand = new GetoutCommand(this, settings, messageService, scheduler);

        registerCommandSafe("ban", banCommand, banCommand);
        registerCommandSafe("tempban", tempBanCommand, tempBanCommand);
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
}
