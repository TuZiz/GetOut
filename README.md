# Getout

一款简洁、高效的 Minecraft 跨服封禁插件。

## 功能特性

- **永久封禁** (`/ban`) - 永久封禁玩家
- **临时封禁** (`/tempban`) - 支持灵活时间格式的临时封禁
- **跨服踢出** (`/kick`) - 踢出任意服务器上的在线玩家
- **跨服同步** - 基于数据库 Outbox 模式的多服数据同步
- **异步安全** - 所有数据库操作均在异步线程执行，绝不阻塞主线程
- **Folia 兼容** - 自动检测 Folia 环境并使用对应的调度器
- **PAPI 变量** - 支持 PlaceholderAPI 变量查询
- **MiniMessage** - 支持 Kyori Adventure MiniMessage RGB 格式

## 安装方法

1. 将 `Getout-1.0.0.jar` 放入服务器的 `plugins` 目录
2. 启动服务器，生成默认配置文件
3. 编辑 `plugins/Getout/config.yml` 配置数据库
4. 重启服务器或执行 `/getout reload`

## 数据库配置

### MySQL / MariaDB（推荐）

```yaml
database:
  type: mysql
  host: localhost
  port: 3306
  database: getout
  username: root
  password: password
```

首次启动时插件会自动创建所需的数据库表。

### SQLite（仅限单服测试）

```yaml
database:
  type: sqlite
  database: getout
```

**注意：SQLite 不适合多服同步场景，仅建议用于单服测试。**

## 多服同步说明

Getout 使用数据库作为强一致性数据源，通过 Outbox 模式实现跨服同步：

1. 每次执行 ban/tempban/kick 时，操作记录会写入 `getout_events` 表
2. 每台服务器定期轮询该表，获取其他服务器产生的事件
3. 通过事件 ID 防止重复处理
4. 通过 `server-id` 配置区分不同服务器

### 配置要求

- 每台服务器的 `server-id` 必须不同
- 所有服务器必须连接同一个数据库
- `sync.poll-interval-ticks` 控制轮询间隔（默认 20 ticks = 1 秒）

```yaml
# 服务器 A
server-id: "server-1"

# 服务器 B
server-id: "server-2"
```

## 命令列表

| 命令 | 说明 | 权限 |
|------|------|------|
| `/ban <玩家名\|UUID> [原因]` | 永久封禁玩家 | `getout.command.ban` |
| `/tempban <玩家名\|UUID> <时间> [原因]` | 临时封禁玩家 | `getout.command.tempban` |
| `/kick <玩家名\|UUID> [原因]` | 踢出玩家 | `getout.command.kick` |
| `/getout reload` | 重载配置和语言文件 | `getout.admin` |
| `/getout info` | 查看插件信息 | `getout.admin` |

### 时间格式

临时封禁支持以下时间格式：

- `10s` - 10 秒
- `5m` - 5 分钟
- `2h` - 2 小时
- `7d` - 7 天
- `1d2h30m` - 1 天 2 小时 30 分钟
- `30d12h` - 30 天 12 小时

## 权限列表

| 权限 | 说明 | 默认 |
|------|------|------|
| `getout.command.ban` | 使用 /ban 命令 | op |
| `getout.command.tempban` | 使用 /tempban 命令 | op |
| `getout.command.kick` | 使用 /kick 命令 | op |
| `getout.admin` | 管理员权限（包含所有权限） | op |

## PAPI 变量

如果服务器安装了 PlaceholderAPI，以下变量可用：

| 变量 | 说明 |
|------|------|
| `%getout_is_banned%` | 当前玩家是否被封禁（是/否） |
| `%getout_ban_reason%` | 当前玩家封禁原因 |
| `%getout_ban_expire%` | 当前玩家封禁到期时间 |
| `%getout_ban_left%` | 当前玩家封禁剩余时间 |
| `%getout_ban_operator%` | 当前玩家封禁操作者 |
| `%getout_is_banned_<玩家名>%` | 指定玩家是否被封禁 |
| `%getout_ban_reason_<玩家名>%` | 指定玩家封禁原因 |
| `%getout_ban_expire_<玩家名>%` | 指定玩家封禁到期时间 |
| `%getout_ban_left_<玩家名>%` | 指定玩家封禁剩余时间 |
| `%getout_server_id%` | 当前服务器 ID |

## Folia 兼容说明

Getout 自动检测 Folia 环境：

- **Spigot / Paper**：使用 Bukkit Scheduler 执行异步任务
- **Folia**：使用 GlobalRegionScheduler / EntityScheduler

所有数据库操作、配置重载、同步轮询均在异步线程执行，不会阻塞主线程。

## 数据库表结构

插件会自动创建以下表（表名带可配置前缀，默认 `getout_`）：

- `getout_players` - 玩家索引表，避免主线程查 OfflinePlayer
- `getout_bans` - 封禁记录表
- `getout_events` - 跨服同步事件表（Outbox 模式）

## 注意事项

1. **SQLite 不适合多服同步**：SQLite 仅建议用于单服测试环境
2. **server-id 必须不同**：每台子服的 `server-id` 配置必须唯一
3. **不要删除同步事件表**：`getout_events` 表用于跨服同步，运行中删除会导致同步中断
4. **数据库是最终可信源**：内存缓存仅用于 PAPI 变量等展示优化，不作为封禁判断依据
5. **fail-open / fail-close**：数据库异常时的行为可通过配置控制
