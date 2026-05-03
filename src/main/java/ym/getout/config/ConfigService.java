package ym.getout.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ym.getout.util.LoggerUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ConfigService {

    private final JavaPlugin plugin;
    private final Settings settings;
    private FileConfiguration config;

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.settings = new Settings();
    }

    /**
     * 初始化配置文件（首次启动时复制默认配置）。
     */
    public void init() {
        plugin.saveDefaultConfig();
        reload();
    }

    /**
     * 重新加载配置文件并更新 Settings。
     * 注意：必须在异步线程中执行文件读取。
     */
    public void reload() {
        // Reload config file
        plugin.reloadConfig();
        config = plugin.getConfig();
        settings.load(config);
        LoggerUtil.init(plugin.getLogger(), settings.isDebug());
        LoggerUtil.debug("Configuration loaded successfully");
    }

    public Settings getSettings() {
        return settings;
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
