📄 **AttributeBinder 插件 — 最终用户使用手册**

---

## 1. 功能简介

* **核心功能**：

  1. **属性加成**：通过命令为玩家动态增加或减少 MMOItems 游戏内属性。
  2. **属性撤销**：移除单项或全部属性加成。
  3. **属性查询**：查看玩家当前所有加成属性及数值。
  4. **状态持久**：加成值保存在后台，玩家重进或服务器重启后自动恢复。
* **解决问题**：方便服务器管理员或活动策划，无需开发背景即可管理玩家属性。
* **适用场景**：活动加成、测试调优、自定义职业技能等。

---

## 2. 功能入口说明

* **命令行入口**（控制台 / 游戏内聊天栏）
* **命令前缀**：`/ab` 或 `/attributebinder`

> **贴心 Tab 补全**
> 在输入过程中，插件会：
> • 在第 2 个参数位置自动列出"当前在线玩家"列表；
> • 在第 3 个参数位置自动列出 _MMOItems_ 已注册且可数值化的 **DoubleStat** 属性 ID；
> • `/ab remove` 还会额外提供 `all` 选项；
> • 补全结果会根据已输入前缀动态模糊匹配并排序。

| 功能   | 命令格式                                            | 描述                                        |
| ---- | ------------------------------------------------- | ----------------------------------------- |
| 赋能   | `/ab give <玩家> <属性ID> <数值> [KeyID] [memoryOnly] [ticks]`       | 为玩家增加或减少属性值，`ticks` 为持续时长，默认永久 |
| 覆盖   | `/ab replace <玩家> <属性ID> <数值> [KeyID] [memoryOnly] [ticks]`   | 将玩家属性直接设置为目标值，`ticks` 为持续时长，默认永久 |
| 撤销   | `/ab remove <玩家> <属性ID> [KeyID]`    | 移除玩家单个属性加成       |
| 按Key撤销 | `/ab remove <玩家> key <KeyID>` | 移除玩家指定 Key 下所有属性 |
| 全部撤销 | `/ab remove <玩家> all`       | 移除玩家所有属性加成       |
| 查询   | `/ab list <玩家>`             | 查看玩家所有当前加成属性     |
| 热重载  | `/attributebinder reload`   | 重新加载配置并保存内存缓存    |
| 手动写库 | `/attributebinder flush`    | 强制将内存中所有变更保存到数据库 |

---

## 3. 操作流程详解

### 3.1 为玩家赋能

1. 输入命令：

   ```
   /ab give 张三 ATTACK_DAMAGE 5
   ```
2. 系统反馈：

   > "已成功为 张三 增加 攻击伤害 ATTACK_DAMAGE：当前总加成为 5.0"
3. 减少属性：

   ```
   /ab give 张三 ATTACK_DAMAGE -2
   ```
4. 缓存更新后，稍后自动或通过 `/attributebinder flush` 写入数据库。
5. 可选指定持续时长（ticks），例如：
   ```
   /ab give 张三 ATTACK_DAMAGE 5 600
   ```

### 3.1.1 覆盖属性

若需要直接将某属性设置为指定值，而非增量修改，可使用 `replace`：

```
/ab replace 张三 ATTACK_DAMAGE 10
```

此操作会将张三的 ATTACK_DAMAGE 加成值直接设为 10（若带 `%` 则代表百分比）。

### 3.2 移除单个属性

1. 输入：

   ```
   /ab remove 张三 ATTACK_DAMAGE
   ```
2. 系统反馈：

   > "已移除 张三 的属性 ATTACK_DAMAGE"

### 3.3 移除所有属性

1. 输入：

   ```
   /ab remove 张三 all
   ```
2. 系统反馈：

   > "已移除 张三 的所有属性加成"

### 3.4 查询玩家属性

1. 输入：

   ```
   /ab list 张三
   ```
2. 系统返回示例列表：

   ```
   张三 当前属性加成：
     - ATTACK_DAMAGE：5.0
     - SPEED：1.5
     - JUMP_HEIGHT：-0.3
   ```
3. 若玩家无任何加成，则提示：

   > "张三 当前无任何属性加成"

---

## 4. 配置与个性化

### 4.1 config.yml

* **位置**：`plugins/AttributeBinder/config.yml`

* **主要字段**：

  | 字段                      | 含义                            | 默认值       |
  | ----------------------- | ----------------------------- | --------- |
  | `debug-level`           | 日志输出级别（DEBUG、INFO、NONE）       | INFO      |
  | `database.use-mysql`    | 是否启用 MySQL 存储（false = SQLite） | false     |
  | `database.host`         | MySQL 主机地址                    | localhost |
  | `database.port`         | MySQL 端口                      | 3306      |
  | `sync-interval-minutes` | 内存缓存写库间隔（分钟，0=关闭定时同步）         | 5         |

* **修改方式**：

  1. 停服或执行 `/attributebinder reload`；
  2. 编辑并保存 `config.yml`；
  3. 重载或重启生效。

### 4.2 lang.yml

* **位置**：`plugins/AttributeBinder/lang.yml`

* **文本键及示例**：

  | 键                             | 示例文本                                          |
  | ----------------------------- | --------------------------------------------- |
  | `command-give-success`        | `&a已为 {player} 增加属性 {attribute}：当前总值 {value}` |
  | `command-give-invalid-number` | `&c数值格式非法，请输入有效数字`                            |
  | `command-remove-success`      | `&a已移除 {player} 的属性 {attribute}`              |
  | `command-remove-all-success`  | `&a已移除 {player} 的所有属性加成`                      |
  | `command-list-header`         | `&e{player} 当前属性加成：`                          |
  | `command-list-item`           | `&6- {attribute}：{value}`                     |
  | `command-list-empty`          | `&c{player} 当前无任何属性加成`                        |
  | `command-reload-success`      | `&a配置已重新加载`                                   |
  | `command-flush-success`       | `&a已将缓存数据写入数据库`                               |
  | `error-player-not-found`      | `&c玩家 {player} 未找到`                           |
  | `error-no-permission`         | `&c您没有权限执行此命令`                                |

* **修改方式**：同配置文件，保存后执行 `/attributebinder reload` 生效。

---

## 5. 错误提示与异常处理

| 场景       | 系统提示键                              | 示例内容                    | 解决方案                              |
| -------- | ---------------------------------- | ----------------------- | --------------------------------- |
| 玩家不存在    | `error-player-not-found`           | "玩家 张三 未找到"             | 确认玩家在线并拼写正确                       |
| 属性 ID 错误 | `command-give-invalid-number` 或自定义 | "数值格式非法，请输入有效数字"或"无效属性" | 检查属性 ID 与 MMOItems 一致，区分大小写       |
| 数据库连接失败  | 无固定键                               | "数据库操作失败，请查看服务器日志"      | 检查 `config.yml` 数据库配置及网络连通性       |
| 权限不足     | `error-no-permission`              | "您没有权限执行此命令"            | 为用户分配 `attributebinder.*` 或相应权限节点 |

---

## 6. 使用建议与最佳实践

* **小步调试**：每次更改属性前，先在测试账号进行尝试，确认效果后再批量操作。
* **合理刷新**：根据服务器负载调整 `sync-interval-minutes`，高峰期可设置为 0 并手动 `/attributebinder flush`。
* **批量脚本**：结合控制台脚本快速对多名玩家执行 `/ab give` 或 `/ab remove`。
* **定期备份**：使用数据库导出工具备份 `player_attributes` 表，防止误删。

---

## 8. FAQ 常见问题

1. **问：如何自定义提示文本？**
   答：编辑 `plugins/AttributeBinder/lang.yml` 中对应键的文本，保存后执行 `/attributebinder reload`。

2. **问：为什么 `/ab list` 没显示任何内容？**
   答：表示该玩家当前内存缓存或数据库中无加成记录；可先 `/ab give` 赋能后再查询。

3. **问：如何关闭自动同步？**
   答：将 `config.yml` 中 `sync-interval-minutes` 设为 `0`，仅通过 `/attributebinder flush` 手动写库。

4. **问：能否限制命令使用权限？**
   答：可在权限插件中设置 `attributebinder.give`、`attributebinder.remove`、`attributebinder.list`、`attributebinder.reload`、`attributebinder.flush` 等节点。
