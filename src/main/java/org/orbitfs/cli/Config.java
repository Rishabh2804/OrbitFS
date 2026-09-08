package org.orbitfs.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads OrbitFS configuration from (in priority order):
 * 1. System properties (--host, --port, etc. override these)
 * 2. Environment variables (ORBITFS_HOST, ORBITFS_PORT)
 * 3. Config file at ~/.orbitfs/config
 */
public class Config {

    private static final String CONFIG_FILE = "~/.orbitfs/config";

    public static String getHost() {
        String val = System.getProperty("orbitfs.host");
        if (val != null) return val;
        val = System.getenv("ORBITFS_HOST");
        if (val != null) return val;
        return loadConfig().getProperty("host", "127.0.0.1");
    }

    public static int getPort() {
        String val = System.getProperty("orbitfs.port");
        if (val != null) return Integer.parseInt(val);
        val = System.getenv("ORBITFS_PORT");
        if (val != null) return Integer.parseInt(val);
        return Integer.parseInt(loadConfig().getProperty("port", "9090"));
    }

    public static long getTimeoutMs() {
        String val = System.getProperty("orbitfs.timeout");
        if (val != null) return Long.parseLong(val);
        val = System.getenv("ORBITFS_TIMEOUT");
        if (val != null) return Long.parseLong(val);
        return Long.parseLong(loadConfig().getProperty("timeout", "30000"));
    }

    private static Properties loadConfig() {
        Properties props = new Properties();
        Path configPath = Path.of(System.getProperty("user.home"), ".orbitfs", "config");
        if (Files.exists(configPath)) {
            try (var in = Files.newBufferedReader(configPath)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("orbit: warning: could not read config: " + e.getMessage());
            }
        }
        return props;
    }
}
