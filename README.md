# PudCraft Server Connect

将你的 Minecraft 服务器连接到 [PudCraft 社区平台](https://servers.pudcraft.top)，实现白名单自动同步、服务器状态上报和一键认领。

## 功能

- **白名单同步** — 社区平台审批/邀请的玩家自动同步到服务器白名单，支持 WebSocket 实时推送 + 定时轮询双保障
- **状态上报** — 定时向平台上报在线状态、玩家数、TPS、内存等信息
- **服务器认领** — 通过 API Key 一键证明服务器归属
- **双白名单模式** — 支持 Bukkit 原生白名单或插件自管理白名单
- **多语言** — 中文 / 英文可切换

## 环境要求

- Minecraft 1.21+
- Paper / Spigot 服务端
- Java 21+

## 安装

1. 从 [Releases](https://github.com/pudcraft/pudcraft-server-connect/releases) 下载最新的 JAR 文件
2. 放入服务器 `plugins/` 目录
3. 启动服务器，插件会生成默认配置文件
4. 编辑 `plugins/pudcraft-server-connect/config.yml`，填入 `server-id` 和 `api-key`
5. 在游戏内执行 `/pudcraft reload`

## 配置

```yaml
api:
  base-url: "https://servers.pudcraft.top"
  server-id: "你的服务器 ID"
  api-key: "你的 API Key"

websocket:
  enabled: true
  reconnect-delay-seconds: 5
  max-reconnect-delay-seconds: 300

# "native" 使用 Bukkit 原生白名单，"plugin" 使用插件自管理白名单
whitelist:
  mode: "native"

sync:
  poll-interval-seconds: 300
  handshake-on-startup: true

status:
  report-interval-seconds: 60
  report-tps: true
  report-memory: true

language: "zh_CN"
```

### 获取 API Key

1. 在 [PudCraft 社区平台](https://servers.pudcraft.top) 提交并认领你的服务器
2. 进入服务器管理页面，生成 API Key
3. 将 API Key 和服务器 ID 填入 `config.yml`

## 命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/pudcraft` | `pudcraft.admin` | 查看帮助 |
| `/pudcraft reload` | `pudcraft.reload` | 重载配置并重启所有服务 |
| `/pudcraft status` | `pudcraft.status` | 查看连接状态 |
| `/pudcraft verify` | `pudcraft.verify` | 认领服务器 |
| `/pudcraft sync` | `pudcraft.sync` | 手动触发白名单同步 |
| `/pudcraft whitelist list` | `pudcraft.whitelist` | 查看白名单列表 |

所有权限默认仅 OP 可用。

## 从源码构建

```bash
git clone https://github.com/pudcraft/pudcraft-server-connect.git
cd pudcraft-server-connect
./gradlew build
```

构建产物位于 `build/libs/pudcraft-server-connect-1.0-SNAPSHOT.jar`。

## 许可证

本项目基于 [GNU General Public License v3.0](LICENSE) 开源。
