# Getout

`Getout` 是一个面向 `Paper` / `Spigot` / `Folia` 的惩戒插件，提供账号封禁、IP 封禁、临时封禁、跨服踢出，以及基于共享数据库的跨服同步。

## 支持版本

- Java: `17+`
- Paper: `1.21.11` API 构建，已在 `1.21.x` 环境验证
- Spigot: `1.21.x` Bukkit API 兼容路径
- Folia: `1.21.x`，`plugin.yml` 已声明 `folia-supported: true`

## 核心行为

- `/ban` 只封禁玩家账号，默认不会顺带封禁 IP
- `/banip` 单独封禁玩家当前在线 IP 或最后记录的 IP
- `/unban` 解除账号封禁；若目标当前只剩 IP 封禁，也会同步解除
- `/unbanip` 解除指定玩家或直接指定 IP 的 IP 封禁
- `storage.type: yaml` 时走本地存储
- `storage.type: database` 时启用共享数据库和跨服同步

## 命令

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/ban <玩家名\|UUID> [原因]` | 永久封禁玩家账号 | `getout.command.ban` |
| `/banip <玩家名\|UUID> [原因]` | 封禁玩家当前或最后记录的 IP | `getout.command.banip` |
| `/unban <玩家名\|UUID> [原因]` | 解除账号封禁 | `getout.command.unban` |
| `/unbanip <玩家名\|UUID\|IP> [原因]` | 解除 IP 封禁 | `getout.command.unbanip` |
| `/tempban <玩家名\|UUID> <时长> [原因]` | 临时封禁玩家 | `getout.command.tempban` |
| `/kick <玩家名\|UUID> [原因]` | 踢出玩家 | `getout.command.kick` |
| `/getout reload` | 重载配置和语言 | `getout.admin` |
| `/getout info` | 查看插件状态 | `getout.admin` |
| `/getout migrate yaml-to-database` | 将 YAML 数据迁移到数据库 | `getout.admin` |

## 时间格式

`/tempban` 支持：

- `10s`
- `5m`
- `2h`
- `7d`
- `1d2h30m`
- `30d12h`

末尾纯数字按秒处理，例如 `60` 等于 `60s`。

## 存储模式

### 单服

默认推荐 `YAML`：

```yaml
storage:
  type: yaml

fail-open-on-database-error: false

ban:
  auto-ip-ban: false
```

完整示例见 [examples/single-server.yml](examples/single-server.yml)。

### 跨服

跨服同步要求所有服务器：

- 使用同一个数据库
- `server-id` 彼此不同
- `storage.type` 为 `database`

```yaml
storage:
  type: database

database:
  type: mysql
  host: 127.0.0.1
  port: 3306
  database: getout
  username: root
  password: change-me
  table-prefix: getout_
```

完整示例见 [examples/network.yml](examples/network.yml)。

## 重要配置说明

- `fail-open-on-database-error`
  - 默认 `false`
  - 惩戒插件更适合保守策略，数据库不可用时拒绝登录
- `ban.auto-ip-ban`
  - 默认 `false`
  - 如果你明确需要 `/ban` 顺带封禁 IP，再手动开启
- `database.table-prefix`
  - 必须以字母开头
  - 只允许字母、数字、下划线
  - 非法值会在启动时直接报错，避免把 SQL 名称拼坏

## 数据库说明

支持：

- `mysql`
- `mariadb`
- `postgresql`
- `sqlite`

说明：

- `sqlite` 适合单服或本地测试
- 需要真正跨服时，推荐 `mysql` / `mariadb` / `postgresql`
- schema 初始化会按前缀创建表和索引，例如 `getout_bans`、`getout_idx_bans_uuid_active`

## Maven 与打包

- `paper-api`、`placeholderapi` 使用 `provided`
- 运行时依赖通过 `maven-shade-plugin` 打进最终 jar
- `HikariCP` 做了 relocate，避免和服务端其他插件的类冲突

本地构建：

```bash
mvn clean verify
```

产物：

- `target/getout-1.0.0.jar`
- `target/getout-1.0.0-shaded.jar`

## 自动构建

仓库已包含 GitHub Actions 工作流：

- 触发：`push`、`pull_request`
- Java: `17`
- 任务：`mvn -B verify`

## 测试覆盖

当前仓库包含以下测试方向：

- `TimeParser`
- `SchemaInitializer`
- `EventProcessor`

这些测试主要覆盖时间解析、schema 索引前缀行为，以及同步事件处理。
