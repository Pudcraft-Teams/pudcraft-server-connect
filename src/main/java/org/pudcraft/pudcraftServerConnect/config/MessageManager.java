package org.pudcraft.pudcraftServerConnect.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MessageManager {
    private final PudcraftServerConnect plugin;
    private YamlConfiguration messages;
    private String prefix;

    public MessageManager(PudcraftServerConnect plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        String fileName = "lang/" + language + ".yml";

        // Save default lang files if not exist
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        File langFile = new File(plugin.getDataFolder(), fileName);
        if (!langFile.exists()) {
            plugin.saveResource(fileName, false);
        }

        // Load from plugin data folder (user-editable)
        messages = YamlConfiguration.loadConfiguration(langFile);

        // Fallback to bundled resource
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            messages.setDefaults(defaults);
        }

        prefix = colorize(messages.getString("prefix", "[PudCraft] "));
    }

    public String get(String key) {
        return prefix + colorize(messages.getString(key, "Missing message: " + key));
    }

    public String getRaw(String key) {
        return colorize(messages.getString(key, "Missing message: " + key));
    }

    public String get(String key, Map<String, String> placeholders) {
        String message = messages.getString(key, "Missing message: " + key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return prefix + colorize(message);
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
