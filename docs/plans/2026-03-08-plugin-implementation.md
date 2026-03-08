# PudCraft Server Connect Plugin Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a Paper/Spigot plugin that connects Minecraft servers to the PudCraft community platform, enabling whitelist sync, status reporting, and server verification.

**Architecture:** Modular single-plugin architecture with 7 modules (config, network, whitelist, sync, status, verify, command). Network layer uses Java 11+ HttpClient for REST and Java-WebSocket library for real-time sync. Whitelist management supports both Bukkit native and plugin-managed modes via strategy pattern.

**Tech Stack:** Java 21, Gradle + Shadow plugin, Spigot API 1.21, Java-WebSocket 1.5.7, Gson 2.11.0

---

## API Contracts Reference

**Base URL:** `https://servers.pudcraft.top` (hardcoded)

**Auth:** All API calls use `Authorization: Bearer <api_key>` header.

### Existing Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/servers/{id}/sync/handshake` | Returns `{ whitelist: string[], pendingSyncs: SyncItem[], wsUrl: string }` |
| GET | `/api/servers/{id}/sync/pending` | Returns `{ pendingSyncs: SyncItem[] }` |
| POST | `/api/sync/{syncId}/ack` | Returns `{ success: true }` |

**SyncItem:** `{ id, memberId, mcUsername, action("add"/"remove"), status("pending"/"pushed"/"acked"/"failed"), retryCount, lastAttemptAt, ackedAt, createdAt }`

### WebSocket

- URL: `wss://servers.pudcraft.top/ws?serverId={id}&token={apiKey}` (or ws:// based on wsUrl from handshake)
- Auth via query params, 30s heartbeat ping/pong
- Messages: JSON from Redis pub/sub channel `whitelist:change`, contains `{ serverId }`

### New Endpoints (to be added by community platform)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/servers/{id}/status/report` | Body: `{ online, playerCount, maxPlayers, tps, memoryUsed, memoryMax, version }` |
| POST | `/api/servers/{id}/verify/claim` | API Key auth = server claimed |

---

## Task 1: Build System Setup

**Files:**
- Modify: `build.gradle`
- Modify: `settings.gradle`

**Step 1: Update build.gradle with shadow plugin and dependencies**

```groovy
plugins {
    id 'java'
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

group = 'org.pudcraft'
version = '1.0-SNAPSHOT'

repositories {
    mavenCentral()
    maven {
        name = "spigotmc-repo"
        url = "https://hub.spigotmc.org/nexus/content/repositories/snapshots/"
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    implementation("com.google.code.gson:gson:2.11.0")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        relocate("org.java_websocket", "org.pudcraft.libs.websocket")
        relocate("com.google.gson", "org.pudcraft.libs.gson")
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("1.21")
    }
}

def targetJavaVersion = 21
java {
    def javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible()) {
        options.release.set(targetJavaVersion)
    }
}

processResources {
    def props = [version: version]
    inputs.properties props
    filteringCharset 'UTF-8'
    filesMatching('plugin.yml') {
        expand props
    }
}
```

**Step 2: Verify build compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add build.gradle
git commit -m "build: add shadow plugin, Java-WebSocket and Gson dependencies"
```

---

## Task 2: Resource Files

**Files:**
- Modify: `src/main/resources/plugin.yml`
- Create: `src/main/resources/config.yml`
- Create: `src/main/resources/lang/zh_CN.yml`
- Create: `src/main/resources/lang/en_US.yml`

**Step 1: Update plugin.yml with commands and permissions**

```yaml
name: pudcraft-server-connect
version: '${version}'
main: org.pudcraft.pudcraftServerConnect.PudcraftServerConnect
api-version: '1.21'
description: Connect your Minecraft server to PudCraft community platform

commands:
  pudcraft:
    description: PudCraft Server Connect management command
    usage: /<command> <reload|status|verify|sync|whitelist>
    permission: pudcraft.admin

permissions:
  pudcraft.admin:
    description: Parent permission for all PudCraft commands
    default: op
    children:
      pudcraft.reload: true
      pudcraft.status: true
      pudcraft.verify: true
      pudcraft.sync: true
      pudcraft.whitelist: true
  pudcraft.reload:
    description: Reload plugin configuration
    default: op
  pudcraft.status:
    description: View plugin connection status
    default: op
  pudcraft.verify:
    description: Claim server ownership
    default: op
  pudcraft.sync:
    description: Manually trigger whitelist sync
    default: op
  pudcraft.whitelist:
    description: Manage whitelist
    default: op
```

**Step 2: Create config.yml**

```yaml
# PudCraft Server Connect Configuration

# Community platform connection
api:
  server-id: ""
  api-key: ""

# WebSocket configuration
websocket:
  enabled: true
  reconnect-delay-seconds: 5
  max-reconnect-delay-seconds: 300

# Whitelist mode: "native" (Bukkit whitelist) or "plugin" (plugin-managed)
whitelist:
  mode: "native"

# Sync configuration
sync:
  poll-interval-seconds: 300
  handshake-on-startup: true

# Status reporting
status:
  report-interval-seconds: 60
  report-tps: true
  report-memory: true

# Language: "zh_CN" or "en_US"
language: "zh_CN"
```

**Step 3: Create lang/zh_CN.yml**

```yaml
prefix: "&7[&bPudCraft&7] &r"

config:
  reload-success: "&a配置已重载"
  missing-api-config: "&c请先在 config.yml 中配置 api.server-id 和 api.api-key"

whitelist:
  not-whitelisted: "&c你不在白名单中，请在社区平台申请加入服务器"
  added: "&a玩家 {player} 已加入白名单"
  removed: "&e玩家 {player} 已从白名单移除"
  list-header: "&6当前白名单 ({count} 人):"
  list-entry: "&7- {player}"
  list-empty: "&7白名单为空"

sync:
  handshake-success: "&a握手成功，已同步 {count} 个白名单条目"
  handshake-failed: "&c握手失败: {reason}"
  sync-success: "&a白名单同步操作完成"
  sync-failed: "&c同步失败: {reason}"
  manual-trigger: "&e正在手动触发白名单同步..."

status:
  header: "&6===== PudCraft 连接状态 ====="
  api-connected: "&aAPI 连接正常"
  api-disconnected: "&cAPI 连接异常"
  ws-connected: "&aWebSocket 已连接"
  ws-disconnected: "&cWebSocket 未连接"
  ws-disabled: "&7WebSocket 已禁用"
  whitelist-mode: "&7白名单模式: &f{mode}"
  server-id: "&7服务器 ID: &f{id}"

verify:
  success: "&a服务器认领成功！"
  failed: "&c服务器认领失败: {reason}"
  already-claimed: "&c该服务器已被认领"

command:
  no-permission: "&c你没有权限执行此命令"
  unknown-subcommand: "&c未知子命令，使用 /pudcraft 查看帮助"
  usage: "&e用法: /pudcraft <reload|status|verify|sync|whitelist>"
  player-only: "&c该命令只能在游戏内执行"
  console-only: "&c该命令只能在控制台执行"
```

**Step 4: Create lang/en_US.yml**

```yaml
prefix: "&7[&bPudCraft&7] &r"

config:
  reload-success: "&aConfiguration reloaded"
  missing-api-config: "&cPlease configure api.server-id and api.api-key in config.yml"

whitelist:
  not-whitelisted: "&cYou are not whitelisted. Please apply on the community platform."
  added: "&aPlayer {player} has been added to the whitelist"
  removed: "&ePlayer {player} has been removed from the whitelist"
  list-header: "&6Current whitelist ({count} players):"
  list-entry: "&7- {player}"
  list-empty: "&7Whitelist is empty"

sync:
  handshake-success: "&aHandshake successful, synced {count} whitelist entries"
  handshake-failed: "&cHandshake failed: {reason}"
  sync-success: "&aWhitelist sync operation completed"
  sync-failed: "&cSync failed: {reason}"
  manual-trigger: "&eManually triggering whitelist sync..."

status:
  header: "&6===== PudCraft Connection Status ====="
  api-connected: "&aAPI connection OK"
  api-disconnected: "&cAPI connection failed"
  ws-connected: "&aWebSocket connected"
  ws-disconnected: "&cWebSocket disconnected"
  ws-disabled: "&7WebSocket disabled"
  whitelist-mode: "&7Whitelist mode: &f{mode}"
  server-id: "&7Server ID: &f{id}"

verify:
  success: "&aServer claimed successfully!"
  failed: "&cServer claim failed: {reason}"
  already-claimed: "&cThis server has already been claimed"

command:
  no-permission: "&cYou do not have permission to execute this command"
  unknown-subcommand: "&cUnknown subcommand. Use /pudcraft for help"
  usage: "&eUsage: /pudcraft <reload|status|verify|sync|whitelist>"
  player-only: "&cThis command can only be executed in-game"
  console-only: "&cThis command can only be executed from console"
```

**Step 5: Verify build with new resources**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/resources/
git commit -m "feat: add plugin.yml, config.yml, and language files"
```

---

## Task 3: Config Module

**Files:**
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/config/PluginConfig.java`
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/config/ConfigManager.java`
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/config/MessageManager.java`

**Step 1: Create PluginConfig.java**

```java
package org.pudcraft.pudcraftServerConnect.config;

import org.bukkit.configuration.file.FileConfiguration;

public class PluginConfig {
    // API
    private String serverId;
    private String apiKey;

    // WebSocket
    private boolean websocketEnabled;
    private int reconnectDelaySeconds;
    private int maxReconnectDelaySeconds;

    // Whitelist
    private String whitelistMode; // "native" or "plugin"

    // Sync
    private int pollIntervalSeconds;
    private boolean handshakeOnStartup;

    // Status
    private int reportIntervalSeconds;
    private boolean reportTps;
    private boolean reportMemory;

    // Language
    private String language;

    public void load(FileConfiguration config) {
        this.serverId = config.getString("api.server-id", "");
        this.apiKey = config.getString("api.api-key", "");

        this.websocketEnabled = config.getBoolean("websocket.enabled", true);
        this.reconnectDelaySeconds = config.getInt("websocket.reconnect-delay-seconds", 5);
        this.maxReconnectDelaySeconds = config.getInt("websocket.max-reconnect-delay-seconds", 300);

        this.whitelistMode = config.getString("whitelist.mode", "native");

        this.pollIntervalSeconds = config.getInt("sync.poll-interval-seconds", 300);
        this.handshakeOnStartup = config.getBoolean("sync.handshake-on-startup", true);

        this.reportIntervalSeconds = config.getInt("status.report-interval-seconds", 60);
        this.reportTps = config.getBoolean("status.report-tps", true);
        this.reportMemory = config.getBoolean("status.report-memory", true);

        this.language = config.getString("language", "zh_CN");
    }

    public boolean isConfigured() {
        return serverId != null && !serverId.isEmpty()
            && apiKey != null && !apiKey.isEmpty();
    }

    // Getters for all fields
    public String getServerId() { return serverId; }
    public String getApiKey() { return apiKey; }
    public boolean isWebsocketEnabled() { return websocketEnabled; }
    public int getReconnectDelaySeconds() { return reconnectDelaySeconds; }
    public int getMaxReconnectDelaySeconds() { return maxReconnectDelaySeconds; }
    public String getWhitelistMode() { return whitelistMode; }
    public int getPollIntervalSeconds() { return pollIntervalSeconds; }
    public boolean isHandshakeOnStartup() { return handshakeOnStartup; }
    public int getReportIntervalSeconds() { return reportIntervalSeconds; }
    public boolean isReportTps() { return reportTps; }
    public boolean isReportMemory() { return reportMemory; }
    public String getLanguage() { return language; }
}
```

**Step 2: Create ConfigManager.java**

```java
package org.pudcraft.pudcraftServerConnect.config;

import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;

public class ConfigManager {
    private final PudcraftServerConnect plugin;
    private final PluginConfig pluginConfig;
    private final MessageManager messageManager;

    public ConfigManager(PudcraftServerConnect plugin) {
        this.plugin = plugin;
        this.pluginConfig = new PluginConfig();
        this.messageManager = new MessageManager(plugin);
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        pluginConfig.load(plugin.getConfig());
        messageManager.load(pluginConfig.getLanguage());
    }

    public void reload() {
        load();
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }
}
```

**Step 3: Create MessageManager.java**

```java
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
```

**Step 4: Verify build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/org/pudcraft/pudcraftServerConnect/config/
git commit -m "feat: add config module (PluginConfig, ConfigManager, MessageManager)"
```

---

## Task 4: Network Module — ApiClient & ApiResponse

**Files:**
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/network/ApiResponse.java`
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/network/ApiClient.java`

**Step 1: Create ApiResponse.java**

```java
package org.pudcraft.pudcraftServerConnect.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ApiResponse {
    private final int statusCode;
    private final String body;

    public ApiResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() { return statusCode; }
    public String getBody() { return body; }
    public boolean isSuccess() { return statusCode >= 200 && statusCode < 300; }

    public JsonObject getJsonObject() {
        return JsonParser.parseString(body).getAsJsonObject();
    }

    public JsonArray getJsonArray() {
        return JsonParser.parseString(body).getAsJsonArray();
    }

    public String getError() {
        try {
            JsonObject json = getJsonObject();
            if (json.has("error")) {
                return json.get("error").getAsString();
            }
        } catch (Exception ignored) {}
        return "HTTP " + statusCode;
    }
}
```

**Step 2: Create ApiClient.java**

```java
package org.pudcraft.pudcraftServerConnect.network;

import org.pudcraft.pudcraftServerConnect.config.PluginConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

public class ApiClient {
    private static final String BASE_URL = "https://servers.pudcraft.top";
    private final HttpClient httpClient;
    private final String serverId;
    private final String apiKey;
    private final Logger logger;

    public ApiClient(PluginConfig config, Logger logger) {
        this.serverId = config.getServerId();
        this.apiKey = config.getApiKey();
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public ApiResponse get(String path) {
        return request("GET", path, null);
    }

    public ApiResponse post(String path, String jsonBody) {
        return request("POST", path, jsonBody);
    }

    public ApiResponse postEmpty(String path) {
        return request("POST", path, "{}");
    }

    private ApiResponse request(String method, String path, String jsonBody) {
        try {
            String url = BASE_URL + path.replace("{id}", serverId);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15));

            if ("GET".equals(method)) {
                builder.GET();
            } else if ("POST".equals(method) && jsonBody != null) {
                builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());

            return new ApiResponse(response.statusCode(), response.body());
        } catch (Exception e) {
            logger.warning("API request failed: " + method + " " + path + " - " + e.getMessage());
            return new ApiResponse(-1, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    public String getServerId() { return serverId; }
    public String getApiKey() { return apiKey; }

    /**
     * Test API connectivity by performing a handshake.
     */
    public boolean testConnection() {
        ApiResponse response = post("/api/servers/{id}/sync/handshake", "{}");
        return response.isSuccess();
    }
}
```

**Step 3: Verify build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/org/pudcraft/pudcraftServerConnect/network/ApiResponse.java
git add src/main/java/org/pudcraft/pudcraftServerConnect/network/ApiClient.java
git commit -m "feat: add network module (ApiClient, ApiResponse)"
```

---

## Task 5: Network Module — WebSocket Client

**Files:**
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/network/PudcraftWebSocketClient.java`

**Step 1: Create PudcraftWebSocketClient.java**

```java
package org.pudcraft.pudcraftServerConnect.network;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class PudcraftWebSocketClient extends WebSocketClient {
    private final Logger logger;
    private final Consumer<String> messageHandler;
    private final int initialDelay;
    private final int maxDelay;
    private final ScheduledExecutorService reconnectExecutor;
    private int currentDelay;
    private volatile boolean intentionallyClosed = false;

    public PudcraftWebSocketClient(URI serverUri, Logger logger,
                                    Consumer<String> messageHandler,
                                    int initialDelaySeconds,
                                    int maxDelaySeconds) {
        super(serverUri);
        this.logger = logger;
        this.messageHandler = messageHandler;
        this.initialDelay = initialDelaySeconds;
        this.maxDelay = maxDelaySeconds;
        this.currentDelay = initialDelaySeconds;
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PudCraft-WS-Reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("WebSocket connected");
        currentDelay = initialDelay; // Reset backoff on successful connection
    }

    @Override
    public void onMessage(String message) {
        messageHandler.accept(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("WebSocket closed (code=" + code + ", reason=" + reason + ", remote=" + remote + ")");
        if (!intentionallyClosed) {
            scheduleReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        logger.warning("WebSocket error: " + ex.getMessage());
    }

    private void scheduleReconnect() {
        logger.info("Scheduling WebSocket reconnect in " + currentDelay + " seconds...");
        reconnectExecutor.schedule(() -> {
            if (!intentionallyClosed) {
                try {
                    reconnect();
                } catch (Exception e) {
                    logger.warning("WebSocket reconnect failed: " + e.getMessage());
                    scheduleReconnect();
                }
            }
        }, currentDelay, TimeUnit.SECONDS);
        // Exponential backoff
        currentDelay = Math.min(currentDelay * 2, maxDelay);
    }

    public void shutdown() {
        intentionallyClosed = true;
        reconnectExecutor.shutdownNow();
        try {
            closeBlocking();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isConnectedAndOpen() {
        return isOpen();
    }
}
```

**Step 2: Verify build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/org/pudcraft/pudcraftServerConnect/network/PudcraftWebSocketClient.java
git commit -m "feat: add WebSocket client with auto-reconnect and exponential backoff"
```

---

## Task 6: Whitelist Module

**Files:**
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/whitelist/WhitelistProvider.java`
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/whitelist/NativeWhitelistProvider.java`
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/whitelist/PluginWhitelistProvider.java`
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/whitelist/WhitelistManager.java`

**Step 1: Create WhitelistProvider.java**

```java
package org.pudcraft.pudcraftServerConnect.whitelist;

import java.util.List;

public interface WhitelistProvider {
    boolean addPlayer(String username);
    boolean removePlayer(String username);
    boolean isWhitelisted(String username);
    List<String> getWhitelistedPlayers();
}
```

**Step 2: Create NativeWhitelistProvider.java**

```java
package org.pudcraft.pudcraftServerConnect.whitelist;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class NativeWhitelistProvider implements WhitelistProvider {
    private final Logger logger;

    public NativeWhitelistProvider(Logger logger) {
        this.logger = logger;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean addPlayer(String username) {
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(username);
            player.setWhitelisted(true);
            logger.info("Added " + username + " to native whitelist");
            return true;
        } catch (Exception e) {
            logger.warning("Failed to add " + username + " to native whitelist: " + e.getMessage());
            return false;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean removePlayer(String username) {
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(username);
            player.setWhitelisted(false);
            logger.info("Removed " + username + " from native whitelist");
            return true;
        } catch (Exception e) {
            logger.warning("Failed to remove " + username + " from native whitelist: " + e.getMessage());
            return false;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isWhitelisted(String username) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(username);
        return player.isWhitelisted();
    }

    @Override
    public List<String> getWhitelistedPlayers() {
        return Bukkit.getWhitelistedPlayers().stream()
            .map(OfflinePlayer::getName)
            .filter(name -> name != null)
            .collect(Collectors.toList());
    }
}
```

**Step 3: Create PluginWhitelistProvider.java**

```java
package org.pudcraft.pudcraftServerConnect.whitelist;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.MessageManager;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PluginWhitelistProvider implements WhitelistProvider, Listener {
    private final PudcraftServerConnect plugin;
    private final Logger logger;
    private final MessageManager messageManager;
    private final File whitelistFile;
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();
    private final Gson gson = new Gson();

    public PluginWhitelistProvider(PudcraftServerConnect plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.messageManager = messageManager;
        this.whitelistFile = new File(plugin.getDataFolder(), "whitelist.json");
        loadFromFile();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(AsyncPlayerPreLoginEvent event) {
        if (!whitelist.contains(event.getName().toLowerCase())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                messageManager.getRaw("whitelist.not-whitelisted"));
        }
    }

    @Override
    public boolean addPlayer(String username) {
        boolean added = whitelist.add(username.toLowerCase());
        if (added) {
            saveToFile();
            logger.info("Added " + username + " to plugin whitelist");
        }
        return added;
    }

    @Override
    public boolean removePlayer(String username) {
        boolean removed = whitelist.remove(username.toLowerCase());
        if (removed) {
            saveToFile();
            logger.info("Removed " + username + " from plugin whitelist");
        }
        return removed;
    }

    @Override
    public boolean isWhitelisted(String username) {
        return whitelist.contains(username.toLowerCase());
    }

    @Override
    public List<String> getWhitelistedPlayers() {
        return new ArrayList<>(whitelist);
    }

    public void setWhitelist(List<String> players) {
        whitelist.clear();
        players.forEach(p -> whitelist.add(p.toLowerCase()));
        saveToFile();
    }

    private void loadFromFile() {
        if (!whitelistFile.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(whitelistFile), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<String>>(){}.getType();
            List<String> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                loaded.forEach(p -> whitelist.add(p.toLowerCase()));
            }
            logger.info("Loaded " + whitelist.size() + " players from plugin whitelist");
        } catch (Exception e) {
            logger.warning("Failed to load whitelist.json: " + e.getMessage());
        }
    }

    private void saveToFile() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(whitelistFile), StandardCharsets.UTF_8)) {
            gson.toJson(new ArrayList<>(whitelist), writer);
        } catch (Exception e) {
            logger.warning("Failed to save whitelist.json: " + e.getMessage());
        }
    }
}
```

**Step 4: Create WhitelistManager.java**

```java
package org.pudcraft.pudcraftServerConnect.whitelist;

import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;

import java.util.List;
import java.util.logging.Logger;

public class WhitelistManager {
    private final WhitelistProvider provider;
    private final String mode;
    private final Logger logger;

    public WhitelistManager(PudcraftServerConnect plugin, ConfigManager configManager) {
        this.logger = plugin.getLogger();
        this.mode = configManager.getPluginConfig().getWhitelistMode();

        if ("plugin".equalsIgnoreCase(mode)) {
            this.provider = new PluginWhitelistProvider(plugin, configManager.getMessageManager());
            logger.info("Using plugin-managed whitelist mode");
        } else {
            this.provider = new NativeWhitelistProvider(logger);
            logger.info("Using native Bukkit whitelist mode");
        }
    }

    public boolean addPlayer(String username) {
        return provider.addPlayer(username);
    }

    public boolean removePlayer(String username) {
        return provider.removePlayer(username);
    }

    public boolean isWhitelisted(String username) {
        return provider.isWhitelisted(username);
    }

    public List<String> getWhitelistedPlayers() {
        return provider.getWhitelistedPlayers();
    }

    /**
     * Full whitelist replacement (used during handshake).
     * Only supported in plugin mode.
     */
    public void setWhitelist(List<String> players) {
        if (provider instanceof PluginWhitelistProvider pluginProvider) {
            pluginProvider.setWhitelist(players);
        } else {
            // In native mode, add missing players and remove extras
            List<String> current = provider.getWhitelistedPlayers();
            for (String player : players) {
                if (!provider.isWhitelisted(player)) {
                    provider.addPlayer(player);
                }
            }
            for (String player : current) {
                if (!players.stream().anyMatch(p -> p.equalsIgnoreCase(player))) {
                    provider.removePlayer(player);
                }
            }
        }
        logger.info("Whitelist set to " + players.size() + " players");
    }

    public String getMode() {
        return mode;
    }
}
```

**Step 5: Verify build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/java/org/pudcraft/pudcraftServerConnect/whitelist/
git commit -m "feat: add whitelist module with native and plugin-managed modes"
```

---

## Task 7: Sync Module

**Files:**
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/sync/SyncManager.java`
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/sync/SyncTask.java`

**Step 1: Create SyncManager.java**

```java
package org.pudcraft.pudcraftServerConnect.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;
import org.pudcraft.pudcraftServerConnect.network.ApiClient;
import org.pudcraft.pudcraftServerConnect.network.ApiResponse;
import org.pudcraft.pudcraftServerConnect.network.PudcraftWebSocketClient;
import org.pudcraft.pudcraftServerConnect.whitelist.WhitelistManager;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SyncManager {
    private final PudcraftServerConnect plugin;
    private final ApiClient apiClient;
    private final WhitelistManager whitelistManager;
    private final ConfigManager configManager;
    private final Logger logger;
    private PudcraftWebSocketClient wsClient;
    private SyncTask syncTask;

    public SyncManager(PudcraftServerConnect plugin, ApiClient apiClient,
                       WhitelistManager whitelistManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.whitelistManager = whitelistManager;
        this.configManager = configManager;
        this.logger = plugin.getLogger();
    }

    /**
     * Perform initial handshake: full whitelist sync + start WS + start poll task.
     */
    public void start() {
        if (configManager.getPluginConfig().isHandshakeOnStartup()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::handshake);
        }
        startPollTask();
    }

    public void handshake() {
        logger.info("Performing handshake with community platform...");
        ApiResponse response = apiClient.post("/api/servers/{id}/sync/handshake", "{}");

        if (!response.isSuccess()) {
            logger.warning("Handshake failed: " + response.getError());
            return;
        }

        try {
            JsonObject json = response.getJsonObject();

            // 1. Full whitelist sync
            JsonArray whitelistArray = json.getAsJsonArray("whitelist");
            List<String> whitelist = new ArrayList<>();
            for (JsonElement el : whitelistArray) {
                whitelist.add(el.getAsString());
            }

            // Run whitelist update on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                whitelistManager.setWhitelist(whitelist);
                logger.info("Handshake: synced " + whitelist.size() + " whitelist entries");
            });

            // 2. Process pending syncs
            JsonArray pendingSyncs = json.getAsJsonArray("pendingSyncs");
            processSyncArray(pendingSyncs);

            // 3. Connect WebSocket
            if (configManager.getPluginConfig().isWebsocketEnabled() && json.has("wsUrl")) {
                String wsUrl = json.get("wsUrl").getAsString();
                connectWebSocket(wsUrl);
            }
        } catch (Exception e) {
            logger.warning("Failed to parse handshake response: " + e.getMessage());
        }
    }

    public void processSync(String syncId, String username, String action) {
        // Execute whitelist operation on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success;
            if ("add".equals(action)) {
                success = whitelistManager.addPlayer(username);
            } else if ("remove".equals(action)) {
                success = whitelistManager.removePlayer(username);
            } else {
                logger.warning("Unknown sync action: " + action);
                return;
            }

            // ACK async
            if (success) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> ack(syncId));
            }
        });
    }

    private void ack(String syncId) {
        ApiResponse response = apiClient.postEmpty("/api/sync/" + syncId + "/ack");
        if (response.isSuccess()) {
            logger.fine("ACK sent for sync " + syncId);
        } else {
            logger.warning("ACK failed for sync " + syncId + ": " + response.getError());
        }
    }

    public void pollPending() {
        ApiResponse response = apiClient.get("/api/servers/{id}/sync/pending");
        if (!response.isSuccess()) {
            logger.warning("Failed to fetch pending syncs: " + response.getError());
            return;
        }

        try {
            JsonObject json = response.getJsonObject();
            JsonArray pendingSyncs = json.getAsJsonArray("pendingSyncs");
            processSyncArray(pendingSyncs);
        } catch (Exception e) {
            logger.warning("Failed to parse pending syncs: " + e.getMessage());
        }
    }

    private void processSyncArray(JsonArray syncs) {
        if (syncs == null || syncs.isEmpty()) return;
        for (JsonElement el : syncs) {
            JsonObject sync = el.getAsJsonObject();
            String syncId = sync.get("id").getAsString();
            String action = sync.get("action").getAsString();
            JsonElement mcUsernameEl = sync.get("mcUsername");
            if (mcUsernameEl == null || mcUsernameEl.isJsonNull()) {
                logger.warning("Sync " + syncId + " has no mcUsername, skipping");
                continue;
            }
            String username = mcUsernameEl.getAsString();
            processSync(syncId, username, action);
        }
    }

    public void onWebSocketMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String serverId = json.has("serverId") ? json.get("serverId").getAsString() : null;

            // The WS message from Redis pub/sub only contains serverId as a notification
            // We need to fetch pending syncs to get the actual data
            if (serverId != null) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, this::pollPending);
            }
        } catch (Exception e) {
            logger.warning("Failed to parse WebSocket message: " + e.getMessage());
        }
    }

    private void connectWebSocket(String wsUrl) {
        if (wsClient != null) {
            wsClient.shutdown();
        }

        try {
            String url = wsUrl + "/ws?serverId=" + apiClient.getServerId()
                + "&token=" + apiClient.getApiKey();
            URI uri = URI.create(url);

            wsClient = new PudcraftWebSocketClient(
                uri, logger, this::onWebSocketMessage,
                configManager.getPluginConfig().getReconnectDelaySeconds(),
                configManager.getPluginConfig().getMaxReconnectDelaySeconds()
            );
            wsClient.connect();
        } catch (Exception e) {
            logger.warning("Failed to connect WebSocket: " + e.getMessage());
        }
    }

    private void startPollTask() {
        int intervalTicks = configManager.getPluginConfig().getPollIntervalSeconds() * 20;
        syncTask = new SyncTask(this);
        syncTask.runTaskTimerAsynchronously(plugin, intervalTicks, intervalTicks);
    }

    public void shutdown() {
        if (syncTask != null) {
            syncTask.cancel();
        }
        if (wsClient != null) {
            wsClient.shutdown();
        }
    }

    public boolean isWebSocketConnected() {
        return wsClient != null && wsClient.isConnectedAndOpen();
    }
}
```

**Step 2: Create SyncTask.java**

```java
package org.pudcraft.pudcraftServerConnect.sync;

import org.bukkit.scheduler.BukkitRunnable;

public class SyncTask extends BukkitRunnable {
    private final SyncManager syncManager;

    public SyncTask(SyncManager syncManager) {
        this.syncManager = syncManager;
    }

    @Override
    public void run() {
        syncManager.pollPending();
    }
}
```

**Step 3: Verify build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/org/pudcraft/pudcraftServerConnect/sync/
git commit -m "feat: add sync module (handshake, WebSocket, polling, ACK)"
```

---

## Task 8: Status Module

**Files:**
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/status/StatusReporter.java`

**Step 1: Create StatusReporter.java**

```java
package org.pudcraft.pudcraftServerConnect.status;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;
import org.pudcraft.pudcraftServerConnect.network.ApiClient;
import org.pudcraft.pudcraftServerConnect.network.ApiResponse;

import java.util.logging.Logger;

public class StatusReporter {
    private final PudcraftServerConnect plugin;
    private final ApiClient apiClient;
    private final ConfigManager configManager;
    private final Logger logger;
    private BukkitRunnable reportTask;

    public StatusReporter(PudcraftServerConnect plugin, ApiClient apiClient,
                          ConfigManager configManager) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.configManager = configManager;
        this.logger = plugin.getLogger();
    }

    public void start() {
        int intervalTicks = configManager.getPluginConfig().getReportIntervalSeconds() * 20;
        reportTask = new BukkitRunnable() {
            @Override
            public void run() {
                report(true);
            }
        };
        reportTask.runTaskTimerAsynchronously(plugin, 100L, intervalTicks); // 5s initial delay
    }

    public void report(boolean online) {
        JsonObject body = new JsonObject();
        body.addProperty("online", online);
        body.addProperty("playerCount", Bukkit.getOnlinePlayers().size());
        body.addProperty("maxPlayers", Bukkit.getMaxPlayers());
        body.addProperty("version", Bukkit.getVersion());

        if (configManager.getPluginConfig().isReportTps()) {
            try {
                double[] tps = Bukkit.getTPS();
                body.addProperty("tps", Math.round(tps[0] * 100.0) / 100.0);
            } catch (NoSuchMethodError ignored) {
                // Spigot without Paper API
            }
        }

        if (configManager.getPluginConfig().isReportMemory()) {
            Runtime runtime = Runtime.getRuntime();
            body.addProperty("memoryUsed", runtime.totalMemory() - runtime.freeMemory());
            body.addProperty("memoryMax", runtime.maxMemory());
        }

        ApiResponse response = apiClient.post("/api/servers/{id}/status/report", body.toString());
        if (!response.isSuccess()) {
            logger.warning("Status report failed: " + response.getError());
        }
    }

    public void reportOffline() {
        report(false);
    }

    public void shutdown() {
        if (reportTask != null) {
            reportTask.cancel();
        }
    }
}
```

**Step 2: Verify build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/org/pudcraft/pudcraftServerConnect/status/
git commit -m "feat: add status reporter with periodic heartbeat"
```

---

## Task 9: Verify Module

**Files:**
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/verify/MotdVerifyManager.java`

**Step 1: Create MotdVerifyManager.java**

```java
package org.pudcraft.pudcraftServerConnect.verify;

import org.bukkit.Bukkit;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.MessageManager;
import org.pudcraft.pudcraftServerConnect.network.ApiClient;
import org.pudcraft.pudcraftServerConnect.network.ApiResponse;

import java.util.Map;
import java.util.logging.Logger;

public class MotdVerifyManager {
    private final PudcraftServerConnect plugin;
    private final ApiClient apiClient;
    private final MessageManager messageManager;
    private final Logger logger;

    public MotdVerifyManager(PudcraftServerConnect plugin, ApiClient apiClient,
                             MessageManager messageManager) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.messageManager = messageManager;
        this.logger = plugin.getLogger();
    }

    /**
     * Claim server ownership via API key.
     * Returns a user-facing message.
     */
    public String claim() {
        ApiResponse response = apiClient.postEmpty("/api/servers/{id}/verify/claim");

        if (response.isSuccess()) {
            logger.info("Server claimed successfully");
            return messageManager.get("verify.success");
        } else if (response.getStatusCode() == 409) {
            return messageManager.get("verify.already-claimed");
        } else {
            return messageManager.get("verify.failed",
                Map.of("reason", response.getError()));
        }
    }
}
```

**Step 2: Verify build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/org/pudcraft/pudcraftServerConnect/verify/
git commit -m "feat: add server verification via API key claim"
```

---

## Task 10: Command System

**Files:**
- Create: `src/main/java/org/pudcraft/pudcraftServerConnect/command/MainCommand.java`

**Step 1: Create MainCommand.java**

```java
package org.pudcraft.pudcraftServerConnect.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Bukkit;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;
import org.pudcraft.pudcraftServerConnect.config.MessageManager;
import org.pudcraft.pudcraftServerConnect.sync.SyncManager;
import org.pudcraft.pudcraftServerConnect.verify.MotdVerifyManager;
import org.pudcraft.pudcraftServerConnect.whitelist.WhitelistManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MainCommand implements CommandExecutor, TabCompleter {
    private final PudcraftServerConnect plugin;
    private final ConfigManager configManager;
    private final SyncManager syncManager;
    private final WhitelistManager whitelistManager;
    private final MotdVerifyManager verifyManager;

    public MainCommand(PudcraftServerConnect plugin, ConfigManager configManager,
                       SyncManager syncManager, WhitelistManager whitelistManager,
                       MotdVerifyManager verifyManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.syncManager = syncManager;
        this.whitelistManager = whitelistManager;
        this.verifyManager = verifyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager msg = configManager.getMessageManager();

        if (args.length == 0) {
            sender.sendMessage(msg.get("command.usage"));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("pudcraft.reload")) {
                    sender.sendMessage(msg.get("command.no-permission"));
                    return true;
                }
                configManager.reload();
                sender.sendMessage(msg.get("config.reload-success"));
            }
            case "status" -> {
                if (!sender.hasPermission("pudcraft.status")) {
                    sender.sendMessage(msg.get("command.no-permission"));
                    return true;
                }
                showStatus(sender, msg);
            }
            case "verify" -> {
                if (!sender.hasPermission("pudcraft.verify")) {
                    sender.sendMessage(msg.get("command.no-permission"));
                    return true;
                }
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String result = verifyManager.claim();
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(result));
                });
            }
            case "sync" -> {
                if (!sender.hasPermission("pudcraft.sync")) {
                    sender.sendMessage(msg.get("command.no-permission"));
                    return true;
                }
                sender.sendMessage(msg.get("sync.manual-trigger"));
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> syncManager.pollPending());
            }
            case "whitelist" -> {
                if (!sender.hasPermission("pudcraft.whitelist")) {
                    sender.sendMessage(msg.get("command.no-permission"));
                    return true;
                }
                handleWhitelist(sender, args, msg);
            }
            default -> sender.sendMessage(msg.get("command.unknown-subcommand"));
        }

        return true;
    }

    private void showStatus(CommandSender sender, MessageManager msg) {
        sender.sendMessage(msg.getRaw("status.header"));
        sender.sendMessage(msg.get("status.server-id",
            Map.of("id", configManager.getPluginConfig().getServerId())));
        sender.sendMessage(msg.get("status.whitelist-mode",
            Map.of("mode", whitelistManager.getMode())));

        if (syncManager.isWebSocketConnected()) {
            sender.sendMessage(msg.getRaw("status.ws-connected"));
        } else if (!configManager.getPluginConfig().isWebsocketEnabled()) {
            sender.sendMessage(msg.getRaw("status.ws-disabled"));
        } else {
            sender.sendMessage(msg.getRaw("status.ws-disconnected"));
        }
    }

    private void handleWhitelist(CommandSender sender, String[] args, MessageManager msg) {
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            List<String> players = whitelistManager.getWhitelistedPlayers();
            sender.sendMessage(msg.get("whitelist.list-header",
                Map.of("count", String.valueOf(players.size()))));
            if (players.isEmpty()) {
                sender.sendMessage(msg.getRaw("whitelist.list-empty"));
            } else {
                for (String player : players) {
                    sender.sendMessage(msg.getRaw("whitelist.list-entry").replace("{player}", player));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("reload", "status", "verify", "sync", "whitelist");
            return subs.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && "whitelist".equalsIgnoreCase(args[0])) {
            return List.of("list").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
```

**Step 2: Verify build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/org/pudcraft/pudcraftServerConnect/command/
git commit -m "feat: add /pudcraft command with tab completion"
```

---

## Task 11: Main Class Wiring

**Files:**
- Modify: `src/main/java/org/pudcraft/pudcraftServerConnect/PudcraftServerConnect.java`

**Step 1: Wire all modules in the main class**

```java
package org.pudcraft.pudcraftServerConnect;

import org.bukkit.plugin.java.JavaPlugin;
import org.pudcraft.pudcraftServerConnect.command.MainCommand;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;
import org.pudcraft.pudcraftServerConnect.network.ApiClient;
import org.pudcraft.pudcraftServerConnect.status.StatusReporter;
import org.pudcraft.pudcraftServerConnect.sync.SyncManager;
import org.pudcraft.pudcraftServerConnect.verify.MotdVerifyManager;
import org.pudcraft.pudcraftServerConnect.whitelist.WhitelistManager;

public final class PudcraftServerConnect extends JavaPlugin {
    private ConfigManager configManager;
    private ApiClient apiClient;
    private WhitelistManager whitelistManager;
    private SyncManager syncManager;
    private StatusReporter statusReporter;
    private MotdVerifyManager verifyManager;

    @Override
    public void onEnable() {
        // 1. Config
        configManager = new ConfigManager(this);
        configManager.load();

        // 2. Check configuration
        if (!configManager.getPluginConfig().isConfigured()) {
            getLogger().warning(configManager.getMessageManager()
                .getRaw("config.missing-api-config"));
            getLogger().warning("Plugin will not connect until configured. Edit config.yml and run /pudcraft reload");
            registerCommandsOnly();
            return;
        }

        // 3. Network
        apiClient = new ApiClient(configManager.getPluginConfig(), getLogger());

        // 4. Whitelist
        whitelistManager = new WhitelistManager(this, configManager);

        // 5. Sync
        syncManager = new SyncManager(this, apiClient, whitelistManager, configManager);
        syncManager.start();

        // 6. Status
        statusReporter = new StatusReporter(this, apiClient, configManager);
        statusReporter.start();

        // 7. Verify
        verifyManager = new MotdVerifyManager(this, apiClient, configManager.getMessageManager());

        // 8. Commands
        registerCommands();

        getLogger().info("PudCraft Server Connect enabled successfully");
    }

    @Override
    public void onDisable() {
        // Report offline status
        if (statusReporter != null) {
            statusReporter.reportOffline();
            statusReporter.shutdown();
        }

        // Shutdown sync and WebSocket
        if (syncManager != null) {
            syncManager.shutdown();
        }

        getLogger().info("PudCraft Server Connect disabled");
    }

    private void registerCommands() {
        MainCommand cmd = new MainCommand(this, configManager, syncManager,
            whitelistManager, verifyManager);
        getCommand("pudcraft").setExecutor(cmd);
        getCommand("pudcraft").setTabCompleter(cmd);
    }

    private void registerCommandsOnly() {
        // Register commands that work without API connection (reload only)
        MainCommand cmd = new MainCommand(this, configManager, null, null, null);
        getCommand("pudcraft").setExecutor(cmd);
        getCommand("pudcraft").setTabCompleter(cmd);
    }
}
```

**Step 2: Update MainCommand to handle null modules (unconfigured state)**

Add null checks at the top of each subcommand handler in `MainCommand.java`:

For `verify`, `sync`, `whitelist`, `status` subcommands, add:
```java
if (syncManager == null) {
    sender.sendMessage(msg.get("config.missing-api-config"));
    return true;
}
```

**Step 3: Verify full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 4: Test with runServer**

Run: `./gradlew runServer`
Expected: Server starts, plugin loads, logs "Please configure api.server-id and api.api-key in config.yml"

**Step 5: Commit**

```bash
git add src/main/java/org/pudcraft/pudcraftServerConnect/PudcraftServerConnect.java
git add src/main/java/org/pudcraft/pudcraftServerConnect/command/MainCommand.java
git commit -m "feat: wire all modules in main class, handle unconfigured state"
```

---

## Task 12: Final Integration Test

**Step 1: Full clean build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL, shadow JAR generated

**Step 2: Test with Paper server**

Run: `./gradlew runServer`
Expected:
- Plugin loads without errors
- config.yml generated in plugins/pudcraft-server-connect/
- lang/zh_CN.yml generated
- Warning about missing API config
- `/pudcraft` command works (shows usage)
- `/pudcraft reload` works
- `/pudcraft status` shows unconfigured state

**Step 3: Final commit**

```bash
git add -A
git commit -m "feat: PudCraft Server Connect v1.0 - whitelist sync, status reporting, server verification"
```
