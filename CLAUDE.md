# PudCraft Server Connect

Paper/Spigot Minecraft 插件，将 MC 服务器连接到 PudCraft 社区平台（pudcraft-community），实现白名单同步、状态上报和服务器认领。

## 技术栈

- Java 21, Gradle + Shadow plugin
- Spigot API 1.21 (compileOnly)
- Java-WebSocket 1.5.7 (relocated to `org.pudcraft.libs.websocket`)
- Gson 2.11.0 (relocated to `org.pudcraft.libs.gson`)
- HTTP: Java 内置 `java.net.http.HttpClient` (强制 HTTP/1.1)

## 构建

```bash
./gradlew build          # 输出: build/libs/pudcraft-server-connect-1.0-SNAPSHOT.jar
./gradlew clean build    # 清理后构建
./gradlew runServer      # 启动 Paper 测试服务器（MC 1.21）
```

Shadow JAR 会自动 relocate 依赖，避免与其他插件冲突。

## 项目结构

```
src/main/java/org/pudcraft/pudcraftServerConnect/
├── PudcraftServerConnect.java      # 主类，模块生命周期管理
├── config/
│   ├── PluginConfig.java           # config.yml 映射 POJO
│   ├── ConfigManager.java          # 配置加载/重载
│   └── MessageManager.java         # 多语言 i18n（zh_CN/en_US）
├── network/
│   ├── ApiClient.java              # REST API 封装（Bearer Token 认证）
│   ├── ApiResponse.java            # HTTP 响应解析
│   └── PudcraftWebSocketClient.java # WS 客户端（指数退避自动重连）
├── whitelist/
│   ├── WhitelistProvider.java      # 策略接口
│   ├── NativeWhitelistProvider.java # Bukkit 原生白名单
│   ├── PluginWhitelistProvider.java # 插件自管理白名单（whitelist.json + LoginEvent 拦截）
│   └── WhitelistManager.java       # 统一管理，根据配置选择实现
├── sync/
│   ├── SyncManager.java            # 同步核心（握手、WS 消息处理、轮询、ACK）
│   └── SyncTask.java               # 定时轮询 BukkitRunnable
├── status/
│   └── StatusReporter.java         # 定时心跳上报（玩家数、TPS、内存）
├── verify/
│   └── MotdVerifyManager.java      # API Key 一键认领服务器
└── command/
    └── MainCommand.java            # /pudcraft 命令 + Tab 补全

src/main/resources/
├── plugin.yml                      # 插件元数据、命令、权限
├── config.yml                      # 默认配置
└── lang/
    ├── zh_CN.yml                   # 中文消息
    └── en_US.yml                   # 英文消息
```

## 模块依赖关系

```
ConfigManager → ApiClient → WhitelistManager → SyncManager
                         → StatusReporter
                         → MotdVerifyManager
                                              → MainCommand
```

主类 `onEnable` 按此顺序初始化，`reload()` 会先 shutdown 再重新初始化。

## 对接的社区平台 API

**Base URL**: 配置在 `config.yml` 的 `api.base-url`，开发默认 `http://localhost:3000`，生产改为 `https://servers.pudcraft.top`。

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/api/servers/{id}/sync/handshake` | 握手，获取白名单 + 待同步 + WS 地址 |
| GET  | `/api/servers/{id}/sync/pending` | 获取待处理同步项 |
| POST | `/api/sync/{syncId}/ack` | 确认同步完成 |
| POST | `/api/servers/{id}/status/report` | 状态心跳上报 |
| POST | `/api/servers/{id}/verify/claim` | API Key 认领服务器 |

WebSocket: `ws://{wsUrl}/ws?serverId={id}&token={apiKey}`

## 关键设计决策

- **API base URL 可配置**: 默认 localhost 开发用，生产环境改配置即可
- **HTTP/1.1 强制**: Java HttpClient 默认 HTTP/2，Next.js 开发服务器不兼容，已强制 HTTP/1.1
- **server-id 支持 PSID**: 配置中可使用数字 PSID（如 `935648`），REST API 自动解析；WS 服务端需支持 PSID 查询
- **TPS 反射调用**: `Bukkit.getTPS()` 是 Paper API，通过反射调用以兼容 Spigot
- **白名单双模式**: `native` 使用 Bukkit 白名单 API，`plugin` 自维护 whitelist.json + 拦截登录事件
- **WS 401 不重连**: WebSocket 收到 401 认证失败时停止重连，提示运行 `/pudcraft verify`
- **reload 全量重启**: `/pudcraft reload` 会关闭所有服务、重载配置、重新初始化，不是热更新

## 开发注意事项

- 修改代码后需要 `./gradlew build` 重新打包 JAR
- 本地测试需要社区平台 Next.js (`pnpm dev` 端口 3000) 和 WS 服务 (`pnpm ws:dev` 端口 3001) 同时运行
- `@SuppressWarnings("deprecation")` 用于 `Bukkit.getOfflinePlayer(String)`，这是 Bukkit API 的已知 deprecation 但仍是唯一方式
- Shadow relocate 确保 Gson 和 Java-WebSocket 不与服务器上其他插件冲突
