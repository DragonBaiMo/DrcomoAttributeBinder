# AttributeBinder 占位符使用指南（已修复版）

本文档对应当前实现，重点解决了 `ATTACK_DAMAGE` / `MAX_HEALTH` 等带下划线属性名在旧语法下的歧义问题。

## 1. 总体规则

- 占位符标识符：`attributebinder`
- 基础格式：`%attributebinder_<key>_<args>%`
- 推荐目标玩家前缀（**显式语法，避免歧义**）：
  - `player:<玩家名>_...`
  - `uuid:<UUID>_...`

> 示例：`%attributebinder_attr_player:Alice_ATTACK_DAMAGE%`

---

## 2. 可用占位符

### 2.1 attr（属性值查询）

#### 当前玩家
- `%attributebinder_attr_<STAT>%`：查询当前玩家该属性总值
- `%attributebinder_attr_<STAT>_<KEY>%`：查询当前玩家指定 Key 下的属性值

#### 指定玩家（推荐显式前缀）
- `%attributebinder_attr_player:<PLAYER>_<STAT>%`
- `%attributebinder_attr_player:<PLAYER>_<STAT>_<KEY>%`
- `%attributebinder_attr_uuid:<UUID>_<STAT>%`
- `%attributebinder_attr_uuid:<UUID>_<STAT>_<KEY>%`

> 说明：
> - 当前玩家模式下，系统会优先尝试识别“`<STAT>_<KEY>` 是否真实存在”，若不存在则按 `<STAT>` 总值处理。
> - 指定玩家模式下，`<STAT>_<KEY>` 按“最后一个 `_`”切分（即 key 不能再包含 `_`）。

---

### 2.2 list（属性列表）

- `%attributebinder_list%`：当前玩家的 ABB 属性（按 key 分组）
- `%attributebinder_list_all%`：当前玩家所有属性（含非 ABB 来源）
- `%attributebinder_list_player:<PLAYER>%`
- `%attributebinder_list_player:<PLAYER>_all%`
- `%attributebinder_list_uuid:<UUID>%`
- `%attributebinder_list_uuid:<UUID>_all%`

> 说明：
> - `list_all` 依赖 MMOItems / MythicLib 运行环境。
> - 目标玩家离线时，`list` 仍可返回 ABB 缓存数据；`list_all` 需要在线玩家上下文。

---

### 2.3 keys（列出属性对应 Key）

- `%attributebinder_keys_<STAT>%`
- `%attributebinder_keys_player:<PLAYER>_<STAT>%`
- `%attributebinder_keys_uuid:<UUID>_<STAT>%`

返回示例：`vip, guild, event`

---

### 2.4 keyattrs（列出某 Key 下的所有属性）

- `%attributebinder_keyattrs_<KEY>%`
- `%attributebinder_keyattrs_player:<PLAYER>_<KEY>%`
- `%attributebinder_keyattrs_uuid:<UUID>_<KEY>%`

---

### 2.5 inspect（属性修饰符详情）

- `%attributebinder_inspect_<STAT>%`
- `%attributebinder_inspect_player:<PLAYER>_<STAT>%`
- `%attributebinder_inspect_uuid:<UUID>_<STAT>%`

---

### 2.6 source（按来源过滤修饰符）

- `%attributebinder_source_<STAT>_<SOURCE>%`
- `%attributebinder_source_player:<PLAYER>_<STAT>_<SOURCE>%`
- `%attributebinder_source_uuid:<UUID>_<STAT>_<SOURCE>%`

支持的 `SOURCE`：
- `ACCESSORY`
- `ARMOR`
- `HAND_ITEM`
- `MAINHAND_ITEM`
- `MELEE_WEAPON`
- `OFFHAND_ITEM`
- `ORNAMENT`
- `OTHER`
- `RANGED_WEAPON`
- `VOID`

> 注意：`EQUIPMENT` 不是有效来源值。

---

### 2.7 stats（属性统计）

- `%attributebinder_stats_<STAT>%`
- `%attributebinder_stats_player:<PLAYER>_<STAT>%`
- `%attributebinder_stats_uuid:<UUID>_<STAT>%`

---

## 3. lang.yml 可配置项

- 列表：`placeholder-list-*`
- key 视图：`placeholder-key-*`
- inspect：`placeholder-inspect-*`
- source：`placeholder-source-*`
- stats：`placeholder-stats-*`
- 来源/类型显示名：`modifier-source-*`、`modifier-type-*`

---

## 4. 兼容与迁移建议

- 兼容：`%attributebinder_attr_<STAT>%`、`%attributebinder_list%` 等旧格式仍可用。
- 强烈建议：涉及“指定玩家”时改用 `player:` / `uuid:` 显式前缀，避免名称/下划线冲突。

---

## 5. 常见排查

1. 占位符原样输出（如 `%attributebinder_attr_MAX_HEALTH%`）
   - 执行：`/papi reload`
   - 再测：`/papi parse me %attributebinder_attr_MAX_HEALTH%`

2. 指定玩家不生效
   - 确认写法：`player:<name>_...` 或 `uuid:<uuid>_...`
   - 玩家名请使用准确大小写（建议与在线列表一致）

3. source 返回无效来源
   - 检查是否使用了有效来源枚举（见 2.6）
