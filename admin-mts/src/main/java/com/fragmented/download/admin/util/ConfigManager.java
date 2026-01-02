package com.fragmented.download.admin.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Config manager for admin client
 * Reads from config.properties or uses defaults
 */
public class ConfigManager {
    
    private static final String CONFIG_FILE = "/config.properties";
    private static Properties properties;
    
    static {
        loadConfig();
    }
    
    private static void loadConfig() {
        properties = new Properties();
        try (InputStream input = ConfigManager.class.getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
                System.out.println("✓ Loaded admin config from: " + CONFIG_FILE);
            } else {
                System.out.println("⚠ Config file not found, using defaults");
            }
        } catch (IOException e) {
            System.err.println("Error loading config: " + e.getMessage());
        }
    }
    
    public static String get(String key, String defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? value : defaultValue;
    }
    
    public static int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}

