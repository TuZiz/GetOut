package ym.getout.lang;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import ym.getout.config.Settings;
import ym.getout.util.LoggerUtil;
import ym.getout.util.TextUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageService {

    private final File dataFolder;
    private String lang;
    private YamlConfiguration messages;
    private String prefix = "";

    public MessageService(File dataFolder, String lang) {
        this.dataFolder = dataFolder;
        this.lang = lang;
    }

    /**
     * 初始化语言文件，确保文件存在。
     */
    public void init() {
        ensureFileExists();
        load();
    }

    /**
     * 重新加载语言文件。
     * 注意：必须在异步线程中执行文件读取。
     */
    public void reload(String newLang) {
        this.lang = newLang;
        ensureFileExists();
        load();
    }

    private void ensureFileExists() {
        File langDir = new File(dataFolder, "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        File langFile = new File(langDir, lang + ".yml");
        if (!langFile.exists()) {
            try (InputStream is = getClass().getResourceAsStream("/lang/" + lang + ".yml")) {
                if (is != null) {
                    Files.copy(is, langFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    LoggerUtil.info("Created default language file: " + langFile.getPath());
                }
            } catch (IOException e) {
                LoggerUtil.error("Failed to create language file", e);
            }
        }
    }

    private void load() {
        File langFile = new File(dataFolder, "lang" + File.separator + lang + ".yml");
        if (langFile.exists()) {
            messages = YamlConfiguration.loadConfiguration(langFile);
        } else {
            // Load from jar resource as fallback
            try (InputStream is = getClass().getResourceAsStream("/lang/" + lang + ".yml")) {
                if (is != null) {
                    messages = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                LoggerUtil.error("Failed to load language file from jar", e);
            }
        }
        if (messages == null) {
            messages = new YamlConfiguration();
        }
        prefix = messages.getString("prefix", "<gradient:#ff5555:#ffaa00>[Getout]</gradient> ");
        LoggerUtil.debug("Language file loaded: " + lang);
    }

    /**
     * 获取原始字符串消息。
     */
    public String getString(String path, String def) {
        return messages.getString(path, def);
    }

    /**
     * 获取原始字符串列表消息。
     */
    public List<String> getStringList(String path) {
        return messages.getStringList(path);
    }

    /**
     * 获取带变量替换的 Component 消息。
     */
    public Component getComponent(String path, Map<String, String> placeholders) {
        String raw = getString(path, "");
        if (raw.isEmpty()) return Component.empty();
        raw = applyPrefix(raw);
        raw = TextUtil.replacePlaceholders(raw, placeholders);
        return TextUtil.parseMiniMessage(raw);
    }

    /**
     * 获取带变量替换的多行 Component 消息。
     */
    public Component getComponentList(String path, Map<String, String> placeholders) {
        List<String> lines = getStringList(path);
        if (lines.isEmpty()) return Component.empty();
        List<String> replaced = TextUtil.replacePlaceholders(lines, placeholders);
        replaced = replaced.stream().map(this::applyPrefix).toList();
        return TextUtil.parseMiniMessage(replaced);
    }

    /**
     * 获取带变量替换的原始字符串（已应用前缀）。
     */
    public String getFormatted(String path, Map<String, String> placeholders) {
        String raw = getString(path, "");
        raw = applyPrefix(raw);
        return TextUtil.replacePlaceholders(raw, placeholders);
    }

    /**
     * 获取带变量替换的原始字符串列表（已应用前缀）。
     */
    public List<String> getFormattedList(String path, Map<String, String> placeholders) {
        List<String> lines = getStringList(path);
        List<String> replaced = TextUtil.replacePlaceholders(lines, placeholders);
        return replaced.stream().map(this::applyPrefix).toList();
    }

    private String applyPrefix(String text) {
        if (text.contains("<prefix>")) {
            return text.replace("<prefix>", prefix);
        }
        return text;
    }

    public String getPrefix() {
        return prefix;
    }
}
