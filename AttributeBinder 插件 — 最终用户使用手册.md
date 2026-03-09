## 主要功能
#### [3. 指令详解](#EDw4h)
## 可选参数
#### [2. 核心概念](#Fj3Id)


---

## 1. 功能概述
**DrcomoAttributeBinder** 是一款基于 MythicLib 属性系统的 Minecraft 插件，旨在为服务器管理者提供一套通过指令动态、精确地给予或移除玩家属性加成的解决方案。

与传统的通过物品、装备或权限组给予属性的方式不同，本插件实现了属性的**“绑定”**与**“解绑”**，允许将任意数值的属性加成与玩家的特定**“身份标识（KeyID）”**关联。这使得属性管理脱离了具体的物品或权限，变得更加灵活和自动化，尤其适用于需要精细化、多来源、可临时控制属性的服务器环境。

### 核心特性
+ **动态属性管理**：通过指令实时给予、替换或移除玩家的属性，无需重载或重启。
+ **多源属性分离**：使用 `KeyID` 区分不同来源的属性加成，避免互相冲突。例如，可以同时存在来自“公会Buff”、“VIP特权”和“临时活动”的攻击力加成，并能独立移除其中任意一个。
+ **临时属性支持**：可以为属性设置精确到 tick 的生效时长（`--exp`），实现限时Buff或Debuff效果。
+ **持久化与内存化**：可选择将属性加成持久化存储（跨服、重启保留）或仅在内存中临时存在（重启后消失），通过 `--mem` 参数控制。
+ **强大的查询与调试**：提供多种 `/abb list` 模式，用于查询玩家当前的属性构成、来源、具体修饰符和统计信息。
+ **PlaceholderAPI 集成**：开放了丰富的占位符，便于在其他插件（如计分板、菜单、提示）中实时展示玩家的详细属性数据。

---

## 2. 核心概念
理解以下几个核心概念，是精确使用本插件的关键。

### 2.1 KeyID (属性标识符)
+ **定义**：`KeyID` 是一个由服主自定义的字符串，用于唯一标识一组属性的来源或原因。它是本插件实现属性分离的核心机制。
+ **作用**：将每一次属性操作（`give`, `replace`, `remove`）与一个特定的 `KeyID` 关联。所有拥有相同 `KeyID` 的属性被视为一个独立的“属性包”。
+ **示例**：
    - 公会每日福利给予的属性，其 `KeyID` 可设为 `guild_daily_bonus`。
    - 玩家购买的月卡特权，其 `KeyID` 可设为 `vip_card_monthly`。
    - 周末活动提供的临时增益，其 `KeyID` 可设为 `weekend_event_buff`。
+ **默认值**：如果在指令中不指定 `KeyID` (通过<font style="color:#DF2A3F;"> </font>`**<font style="color:#DF2A3F;">--k:</font>**`<font style="color:#DF2A3F;"> </font>参数)，系统会使用默认值 `DEFAULT`。

### 2.2 MemoryOnly (仅内存)
+ **定义**：一个布尔值（`true` 或 `false`），通过 `**<font style="color:#DF2A3F;">--mem</font>**` 参数设定，用于决定属性是临时存在还是持久化存储。
+ `false`** (默认)**：属性会被存入数据库。玩家下线、服务器重启后，该属性依然存在。
+ `true`：属性仅存在于服务器内存中。玩家下线或服务器重启后，该属性会消失。
+ **适用场景**：
    - **持久化 (**`false`**)**：适用于永久性的加成，如VIP特权、成就奖励、通过任务获得的永久能力提升等。
    - **仅内存 (**`true`**)**：适用于副本内的临时Buff、一次性药水效果、活动区域限定的增益等。

### 2.3 ExpireTicks (过期时间)
+ **定义**：一个整数，通过 `**<font style="color:#DF2A3F;">--exp</font>**` 参数设定，用于指定属性的有效时长，单位为 `ticks` (1秒 = 20 ticks)。
+ **数值含义**：
    - `-1`** (默认)**：永久有效。
    - `0`：立即移除/不生效。
    - `> 0`：属性将在指定的 `ticks` 之后自动被插件移除。
+ **叠加模式 (**`--md`** 参数)**：
    - `give`** (默认)**：叠加模式。当使用 `/abb give` 指令时，若旧属性有剩余时间，新设定的时间会与旧时间**相加**。
    - `replace`：覆盖模式。当使用 `/abb replace` 指令时，无论旧属性剩余多少时间，都将**直接设置为**新的时间。

### 2.4 属性值 (Value)
+ **格式**：支持两种格式。
    - **固定值**：直接写入数字，如 `10`, `-5.5`。这对应 MythicLib 中的 `FLAT` 修饰符。
    - **百分比**：在数字后附加 `%`，如 `12%`。这对应 MythicLib 中的 `ADDITIVE_MULTIPLIER`（加法乘数）修饰符。例如，一个 `+10%` 和一个 `+15%` 的同种属性会叠加为 `+25%` 再作用于基础值。

---

## 3. 指令详解
**主指令**: `/drcomoattributebinder`  
**别名**: `abb`, `attributeBinder`

### 3.1 `give` - 给予或叠加属性
+ **功能**：为玩家增加一个属性值。如果该玩家、该`KeyID`下已存在同名属性，则数值会进行**叠加**。
+ **权限**：`drcomoattributebinder.give`
+ **语法**：

```plain
/abb give <玩家> <属性ID> <数值> [--k:<KeyID>] [--mem:<true|false>] [--exp:<ticks>] [--md:(replace|give)] [--mx:<上限>]
```

+ **参数说明**：
    - `<玩家>`: 目标玩家名。
    - `<属性ID>`: MythicLib 中的属性ID，例如 `MAX_HEALTH`, `ATTACK_DAMAGE`。
    - `<数值>`: 固定值 (如 `20`) 或百分比 (如 `10%`)。
    - `--k:<KeyID>`: (可选) 指定属性的KeyID，默认为 `DEFAULT`。
    - `--mem:<true|false>`: (可选) 是否为仅内存属性，默认为 `false` (持久化)。
    - `--exp:<ticks>`: (可选) 过期时间（ticks），-1为永久，默认为 `-1`。
    - `--md:(replace|give)`: (可选) 过期时间叠加策略，`give`命令默认为 `give` (叠加)。
    - `--mx:<上限>`: (可选) 设定一个数值上限，叠加后的总值不会超过此上限。
+ **示例**：
    - 给予玩家 `Steve` 一个永久的、来自 `newbie_gift` 的 `20` 点最大生命值加成：

```bash
/abb give Steve MAX_HEALTH 20 --k:newbie_gift
```

    - 给予玩家 `Alex` 一个临时的、仅内存的、持续 `30分钟` (`30*60*20 = 36000 ticks`) 的 `15%` 攻击伤害加成，来源是 `activity_buff`：

```bash
/abb give Alex ATTACK_DAMAGE 15% --k:activity_buff --mem:true --exp:36000
```

---

### 3.2 `replace` - 替换属性
+ **功能**：直接**覆盖**指定 `KeyID` 下的属性值，无论之前的值是多少。如果不存在，则创建。
+ **权限**：`drcomoattributebinder.replace`
+ **语法**：

```plain
/abb replace <玩家> <属性ID> <数值> [--k:<KeyID>] [--mem:<true|false>] [--exp:<ticks>] [--md:(replace|give)]
```

+ **参数说明**：
    - 参数与 `give` 基本相同。
    - `--md` 在 `replace` 命令中默认为 `replace` (覆盖过期时间)。
+ **示例**：
    - 将玩家 `Steve` 在 `vip_level_1` 这个KeyID下的最大生命值加成**直接设置为** `50`：

```bash
/abb replace Steve MAX_HEALTH 50 --k:vip_level_1
```

---

### 3.3 `remove` - 移除属性
+ **功能**：移除玩家的属性。支持多种移除模式。
+ **权限**：`drcomoattributebinder.remove`
+ **语法**：

```plain
/abb remove <玩家|*> <属性ID|all|key> [KeyID]
```

+ **参数说明**：
    - `<玩家|*>`: 目标玩家名，`*` 代表所有在线玩家。
    - `<属性ID|all|key>`:
        * `<属性ID>`: 移除指定属性。如果`[KeyID]`未提供，则移除该属性下所有KeyID的加成；如果提供了`[KeyID]`，则只移除该KeyID下的。
        * `all`: 移除该玩家由本插件添加的**所有**属性。
        * `key`: 必须与`[KeyID]`参数连用，移除指定KeyID下的所有属性。
    - `[KeyID]`: (可选) 配合 `<属性ID>` 或 `key` 模式使用。
+ **示例**：
    - 移除 `Steve` 来自 `newbie_gift` 的 `MAX_HEALTH` 属性：

```bash
/abb remove Steve MAX_HEALTH newbie_gift
```

    - 移除 `Alex` 的所有 `ATTACK_DAMAGE` 属性，无论来自哪个KeyID：

```bash
/abb remove Alex ATTACK_DAMAGE
```

    - 移除 `Steve` 来自 `vip_level_1` 这个KeyID的所有属性：

```bash
/abb remove Steve key vip_level_1
```

    - 移除所有在线玩家的所有属性：

```bash
/abb remove * all
```

---

### 3.4 `list` - 查询属性
+ **功能**：查询玩家当前拥有的属性加成，提供多种维度的详细信息。
+ **权限**：`drcomoattributebinder.list`
+ **语法**：

```plain
/abb list <玩家> [模式] [附加参数...]
```

+ **模式详解**：
    - **(默认模式)** `/abb list <玩家>`
        * 显示该玩家由本插件添加的所有属性，并按 `KeyID` 分组。
    - `all` `/abb list <玩家> all`
        * 显示玩家所有来源的属性（包括本插件、MMOItems装备、MythicLib技能等），并区分显示。
    - `keys <属性ID>` `/abb list <玩家> keys <属性ID>`
        * 显示指定属性由哪些 `KeyID` 提供。
    - `keyattrs <KeyID>` `/abb list <玩家> keyattrs <KeyID>`
        * 显示指定 `KeyID` 下包含的所有属性及其值。
    - `inspect <属性ID>` `/abb list <玩家> inspect <属性ID>`
        * 深度检查一个属性，列出构成该属性总值的所有**修饰符 (Modifier)** 的详细信息，包括来源、类型、Key等。这是非常强大的调试工具。
    - `source <属性ID> <来源>` `/abb list <玩家> source <属性ID> <来源>`
        * 检查并只显示来自特定来源（如 `ARMOR`, `HAND_ITEM`, `OTHER`）的修饰符。
    - `stats <属性ID>` `/abb list <玩家> stats <属性ID>`
        * 显示指定属性的统计信息，包括总值、修饰符数量、各类修饰符的总和以及来源分布。

---

### 3.5 `reload` - 重载配置
+ **功能**：重载 `config.yml` 和 `lang.yml` 文件。
+ **权限**：`drcomoattributebinder.admin`
+ **语法**：

```plain
/abb reload
```

---

### 3.6 `flush` - 强制同步数据
+ **功能**：将内存中的所有玩家属性数据立即写入数据库。
+ **权限**：`drcomoattributebinder.admin`
+ **语法**：

```plain
/abb flush
```

---

### 3.7 `help` - 获取帮助
+ **功能**：显示所有指令的用法帮助信息。
+ **权限**：无
+ **语法**：

```plain
/abb help
```

---

## 4. 配置文件 (`config.yml`)
`config.yml` 用于配置插件的核心功能，主要是日志等级和数据存储方式。

```yaml
# 日志输出等级。可选值：INFO, DEBUG, WARN, ERROR。
# - INFO: 默认等级，只输出关键信息。
# - DEBUG: 用于排查问题，会输出更详细的操作日志。
debug-level: INFO

# 数据存储配置
storage:
  # 存储类型。可选值: sqlite | mysql
  # - sqlite: 轻量级单文件数据库，无需额外配置，适合中小型服务器。
  # - mysql: 关系型数据库，性能更强，适合大型或跨服服务器。
  type: sqlite
  
  # 自定义数据库表名，默认为 'drcomoattributebinder'。
  table-name: drcomoattributebinder

  # --- SQLite 专属设置 ---
  # 当 type 设置为 sqlite 时生效。
  # 数据库文件名，存放于 plugins/DrcomoAttributeBinder/ 目录下。
  sqlite-file: drcomoattributebinder.db

  # --- MySQL 专属设置 ---
  # 当 type 设置为 mysql 时生效。
  mysql:
    host: localhost
    port: 3306
    database: drcomoattributebinder
    user: root
    password: password

# 内存缓存自动写入数据库的周期（单位：分钟）。
# 插件会定期将内存中的数据同步到数据库，以防意外关服导致数据丢失。
sync-interval-minutes: 10
```

---

## 5. 语言文件 (`lang.yml`)
`lang.yml` 包含了插件所有对玩家显示的文本信息。您可以修改此文件来自定义插件的提示、帮助和查询结果的格式。

+ `prefix`: 所有消息的统一前缀。
+ `cmd`: 包含所有指令反馈消息的配置。
+ `placeholder-`**开头的键**: 用于配置 PlaceholderAPI 占位符返回内容的格式。
+ `modifier-`**开头的键**: 用于配置 `list inspect` 等指令中修饰符类型和来源的显示名称。

**修改示例**:  
如果您想将插件前缀修改为红色，可以这样设置：

```yaml
prefix: "&c[属性大师]&r "
```

修改后，使用 `/abb reload` 指令即可生效。

---

## 6. 权限节点
+ `drcomoattributebinder.*`: 拥有所有 DrcomoAttributeBinder 插件的权限。
+ `drcomoattributebinder.admin`: 允许使用 `/abb reload` 和 `/abb flush` 指令。 (默认OP)
+ `drcomoattributebinder.give`: 允许使用 `/abb give` 指令。 (默认OP)
+ `drcomoattributebinder.remove`: 允许使用 `/abb remove` 指令。 (默认OP)
+ `drcomoattributebinder.list`: 允许使用 `/abb list` 指令。 (默认所有玩家)
+ `drcomoattributebinder.replace`: 允许使用 `/abb replace` 指令。 (默认OP)

---

## 7. PlaceholderAPI 占位符
本插件提供了一套强大的占位符，用于在其他插件中显示属性数据。

+ **支持目标玩家**: 所有占位符都支持 `<玩家名>_` 前缀来获取其他玩家的数据。例如：`%attributebinder_attr_Steve_MAX_HEALTH%`。

### 占位符列表
+ `%attributebinder_attr_<属性ID>%`
    - **功能**: 获取指定属性的总值（所有KeyID的总和）。
    - **示例**: `%attributebinder_attr_MAX_HEALTH%` 返回当前玩家的最大生命值总加成。
+ `%attributebinder_attr_<属性ID>_<KeyID>%`
    - **功能**: 获取指定属性在特定 `KeyID` 下的值。
    - **示例**: `%attributebinder_attr_MAX_HEALTH_vip_bonus%` 返回玩家 `vip_bonus` 这个Key下的生命加成。
+ `%attributebinder_list%`
    - **功能**: 以格式化的列表显示玩家所有由本插件添加的属性，按KeyID分组。
    - **示例**: 在菜单或全息图中显示玩家的属性来源。
+ `%attributebinder_list_all%`
    - **功能**: 以格式化的列表显示玩家所有来源的属性（包括装备、技能等）。
    - **示例**: 创建一个全面的个人属性面板。
+ `%attributebinder_keys_<属性ID>%`
    - **功能**: 返回一个由逗号分隔的字符串，列出为指定属性提供加成的所有 `KeyID`。
    - **示例**: `%attributebinder_keys_ATTACK_DAMAGE%` 可能返回 `vip, guild, event`。
+ `%attributebinder_keyattrs_<KeyID>%`
    - **功能**: 格式化显示指定 `KeyID` 下的所有属性及其值。
    - **示例**: `%attributebinder_keyattrs_vip_bonus%` 会列出 `vip_bonus` 下的所有属性加成。
+ `%attributebinder_inspect_<属性ID>%`
    - **功能**: 返回与 `/abb list <玩家> inspect <属性ID>` 指令输出相同的详细修饰符列表。
    - **示例**: 用于为管理员创建高级调试界面。
+ `%attributebinder_source_<属性ID>_<来源>%`
    - **功能**: 返回按来源过滤后的修饰符列表。来源名称为大写，如 `ARMOR`。
    - **示例**: `%attributebinder_source_MAX_HEALTH_ARMOR%` 只显示来自护甲的生命值加成。
+ `%attributebinder_stats_<属性ID>%`
    - **功能**: 返回与 `/abb list <玩家> stats <属性ID>` 指令输出相同的统计信息。
    - **示例**: `%attributebinder_stats_ATTACK_DAMAGE%` 显示攻击伤害的详细统计数据。

## 8. 实际应用场景与示例
### 场景一：VIP 等级福利
**目标**: 为不同等级的VIP玩家提供不同的、可随时更新的属性福利。

1. **给予VIP1福利**: 玩家购买VIP1后，执行指令：

```bash
/abb give <玩家名> MAX_HEALTH 20 --k:vip_benefits
/abb give <玩家名> MOVEMENT_SPEED 5% --k:vip_benefits
```

2. **玩家升级到VIP2**: 福利需要变更，此时使用 `replace` 指令覆盖旧的福利。

```bash
/abb replace <玩家名> MAX_HEALTH 40 --k:vip_benefits
/abb replace <玩家名> MOVEMENT_SPEED 10% --k:vip_benefits
/abb replace <玩家名> ATTACK_DAMAGE 5% --k:vip_benefits
```

_使用 _`replace`_ 可以很方便地处理新增或修改的属性，无需先移除旧的。_

3. **VIP过期**: 执行移除指令，精确移除VIP福利，不影响其他来源的属性。

```bash
/abb remove <玩家名> key vip_benefits
```

### 场景二：周末双倍经验活动 Buff
**目标**: 在周末期间，为所有在线玩家提供一个临时的、重启后消失的增益 Buff。

1. **活动开始**: 使用脚本或手动执行，为所有在线玩家添加一个持续48小时的临时Buff。

```bash
# 48小时 = 48 * 3600 * 20 = 3456000 ticks
/abb give * MMOITEMS_EXP_BOOST 100% --k:weekend_event --mem:true --exp:3456000
```

    - `*` 代表所有在线玩家。
    - `--mem:true` 确保这个Buff是临时的，不会写入数据库。
    - `--exp` 确保Buff到期后自动移除。
2. **活动结束/中途移除**: 如果需要提前结束活动，可以精确地移除这个Buff。

```bash
/abb remove * key weekend_event
```

### 场景三：副本专用药水
**目标**: 制作一种药水，饮用后在10分钟内获得强大的力量和速度，且效果仅限本次在线，下线即失效。

1. **配置物品**: 使用其他物品插件（如 MMOItems）创建一个药水，其使用效果绑定以下指令：

```plain
# 10分钟 = 10 * 60 * 20 = 12000 ticks
# @player 是执行指令的玩家占位符
execute command: "abb give @player ATTACK_DAMAGE 50 --k:potion_buff --mem:true --exp:12000"
execute command: "abb give @player MOVEMENT_SPEED 20% --k:potion_buff --mem:true --exp:12000"
```

    - `--mem:true` 使得这个效果在玩家下线后自动消失，符合“一次性药水”的逻辑。
    - `--exp` 控制了药水的持续时间。

---

## 示例配置文件
以下提供几个 `config.yml` 的配置示例，以展示不同的用法。

### 示例一：基础 SQLite 配置 (默认)
这是最常见的配置，无需额外设置，适合绝大多数服务器。

```yaml
# config.yml

# 日志等级设为 INFO，只看重要信息
debug-level: INFO

storage:
  # 使用 SQLite 数据库
  type: sqlite
  
  # 表名保持默认
  table-name: drcomoattributebinder

  # SQLite 数据库文件名
  sqlite-file: drcomoattributebinder.db

  # MySQL 设置被忽略
  mysql:
    host: localhost
    port: 3306
    database: drcomoattributebinder
    user: root
    password: password

# 每 10 分钟自动将内存数据存入数据库
sync-interval-minutes: 10
```

### 示例二：使用 MySQL 数据库
适用于需要将数据集中管理、或有跨服需求的大型服务器。

```yaml
# config.yml

debug-level: INFO

storage:
  # 切换存储类型为 mysql
  type: mysql
  
  # 可以为不同服务器设置不同的表前缀，方便管理
  table-name: survival_server_attributes

  # SQLite 文件名设置被忽略
  sqlite-file: drcomoattributebinder.db

  # 填写你的 MySQL 数据库信息
  mysql:
    host: 127.0.0.1
    port: 3306
    database: my_minecraft_db
    user: mc_server_user
    password: "your_strong_password"

# 缩短同步周期为 5 分钟，提高数据安全性
sync-interval-minutes: 5
```

### 示例三：调试与开发配置
当插件出现问题或需要观察其详细行为时，可以启用 DEBUG 模式。

```yaml
# config.yml

# 开启 DEBUG 模式，控制台会输出大量详细日志
debug-level: DEBUG

storage:
  # 在调试期间，可以使用 SQLite 以简化部署
  type: sqlite
  
  table-name: drcomoattributebinder_dev_test
  sqlite-file: dev_test.db

  mysql:
    host: localhost
    port: 3306
    database: drcomoattributebinder
    user: root
    password: password

# 在调试时，可以适当延长同步周期，避免日志刷屏
sync-interval-minutes: 30
```

