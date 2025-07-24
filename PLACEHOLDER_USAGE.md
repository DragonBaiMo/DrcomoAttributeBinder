# AttributeBinder 占位符使用指南

本文档详细说明了 AttributeBinder 插件扩展后的占位符功能。

## 代码架构说明

为了提高代码的模块化和可维护性，占位符相关的功能已从主类 `AttributeBinder.java` 中提取到独立的 `PlaceholderHandler.java` 类中。这样的设计有以下优势：

- **职责分离**：主类专注于插件的生命周期管理，占位符处理器专注于占位符逻辑
- **代码复用**：占位符处理逻辑可以更容易地被其他组件使用
- **易于维护**：占位符相关的修改只需要关注 `PlaceholderHandler` 类
- **测试友好**：可以独立测试占位符功能

## 新增占位符功能
> 所有占位符均支持在参数前指定玩家名称或UUID，如：`%attributebinder_attr_<player>_<stat>%` 或 `%attributebinder_inspect_<player>_<stat>%` 等。

### 1. 基础属性查询
-- `%attributebinder_attr_<PLAYER>_<STAT>%` - 获取指定玩家的属性总值
-- `%attributebinder_attr_<PLAYER>_<STAT>_<KEY>%` - 获取指定玩家在key下的属性值

示例：
示例：
- `%attributebinder_attr_Alice_ATTACK_DAMAGE%` - 获取玩家 Alice 的攻击伤害总值
- `%attributebinder_attr_Alice_ATTACK_DAMAGE_sword1%` - 获取玩家 Alice key 为"sword1"的攻击伤害值

### 2. 属性列表显示
- `%attributebinder_list_<PLAYER>%` - 显示指定玩家 AttributeBinder 管理的属性，按key分组
- `%attributebinder_list_<PLAYER>_all%` - 显示指定玩家所有属性（包括非AttributeBinder的）

### 3. Key相关查询
- `%attributebinder_keys_<PLAYER>_<STAT>%` - 列出指定玩家指定属性的所有key
- `%attributebinder_keyattrs_<PLAYER>_<KEY>%` - 显示指定玩家在指定key下的所有属性

示例：
- `%attributebinder_keys_Alice_ATTACK_DAMAGE%` - 列出玩家 Alice 攻击伤害的所有key
- `%attributebinder_keyattrs_Alice_sword1%` - 显示玩家 Alice key为"sword1"下的所有属性

### 4. 详细修饰符检查（新增）
- `%attributebinder_inspect_<PLAYER>_<STAT>%` - 详细检查指定玩家属性的所有修饰符信息
- `%attributebinder_source_<PLAYER>_<STAT>_<SOURCE>%` - 按来源过滤显示指定玩家属性的修饰符
- `%attributebinder_stats_<PLAYER>_<STAT>%` - 显示指定玩家属性的修饰符统计

示例：
- `%attributebinder_inspect_Alice_ATTACK_DAMAGE%` - 详细检查玩家 Alice 攻击伤害的所有修饰符
- `%attributebinder_source_Alice_ATTACK_DAMAGE_EQUIPMENT%` - 显示玩家 Alice 来自装备的攻击伤害修饰符
- `%attributebinder_stats_Alice_ATTACK_DAMAGE%` - 显示玩家 Alice 攻击伤害的统计信息

#### 支持的修饰符来源类型：
- `ACCESSORY` - 饰品
- `ARMOR` - 护甲
- `HAND_ITEM` - 手持物品
- `MAINHAND_ITEM` - 主手物品
- `MELEE_WEAPON` - 近战武器
- `OFFHAND_ITEM` - 副手物品
- `ORNAMENT` - 装饰品
- `OTHER` - 其他
- `RANGED_WEAPON` - 远程武器
- `VOID` - 虚空

## 格式化配置

在 `lang.yml` 中可以自定义以下格式：

### 基础格式
- `placeholder-list-empty` - 无属性时的显示文本
- `placeholder-list-separator` - 不同部分之间的分隔符

### AttributeBinder属性格式
- `placeholder-list-key-header` - key标题格式，支持 `{key}` 变量
- `placeholder-list-item` - 属性项格式，支持 `{attribute}`, `{value}`, `{key}` 等变量

### 所有属性格式
- `placeholder-list-ab-header` - AttributeBinder属性区域标题
- `placeholder-list-other-header` - 其他来源属性区域标题
- `placeholder-list-other-item` - 其他来源属性项格式，支持 `{attribute}`, `{value}`, `{total}` 变量

### 指定key属性格式
- `placeholder-key-empty` - 指定key无属性时的显示，支持 `{key}` 变量
- `placeholder-key-item` - key属性项格式

### 详细检查占位符格式（新增）
- `placeholder-inspect-header` - 检查结果标题，支持 `{stat}`, `{total}` 变量
- `placeholder-inspect-item` - 修饰符项格式，支持 `{key}`, `{value}`, `{type}`, `{source}`, `{slot}`, `{uuid}` 变量
- `placeholder-inspect-empty` - 无修饰符时的显示
- `placeholder-inspect-error` - 检查出错时的显示，支持 `{error}` 变量

### 按来源过滤占位符格式（新增）
- `placeholder-source-header` - 过滤结果标题，支持 `{stat}`, `{source}` 变量
- `placeholder-source-item` - 过滤修饰符项格式，支持 `{key}`, `{value}`, `{type}` 变量
- `placeholder-source-empty` - 无匹配修饰符时的显示
- `placeholder-source-invalid` - 无效来源时的显示，支持 `{source}` 变量
- `placeholder-source-error` - 过滤出错时的显示，支持 `{error}` 变量

### 统计信息占位符格式（新增）
- `placeholder-stats-template` - 统计信息模板，支持多个变量
- `placeholder-stats-source-item` - 来源统计项格式
- `placeholder-stats-type-item` - 类型统计项格式
- `placeholder-stats-error` - 统计出错时的显示

### 修饰符类型和来源显示名称（新增）
- `modifier-type-*` - 修饰符类型的中文显示名称
- `modifier-source-*` - 修饰符来源的中文显示名称

## 使用示例

### 在聊天中显示属性
```
/tellraw @a {"text":"你的攻击伤害：%attributebinder_attr_ATTACK_DAMAGE%"}
```

### 显示详细属性列表
```
/tellraw @a {"text":"%attributebinder_list%"}
```

### 显示所有属性（包括其他插件的）
```
/tellraw @a {"text":"%attributebinder_list_all%"}
```

### 详细检查属性修饰符（新增）
```
/tellraw @a {"text":"%attributebinder_inspect_ATTACK_DAMAGE%"}
```

### 按来源过滤显示修饰符（新增）
```
/tellraw @a {"text":"%attributebinder_source_ATTACK_DAMAGE_EQUIPMENT%"}
```

### 显示属性统计信息（新增）
```
/tellraw @a {"text":"%attributebinder_stats_ATTACK_DAMAGE%"}
```

## 向后兼容性

原有的占位符格式仍然支持：
- `%attributebinder_attr_<STAT>%` - 继续工作
- `%attributebinder_list%` - 现在显示更详细的按key分组信息

## 注意事项

1. 新的占位符需要PlaceholderAPI插件支持
2. `list_all` 和详细检查占位符需要MMOItems/MythicLib插件正常运行
3. 所有格式化文本支持Minecraft颜色代码（&符号）
4. 属性值会自动添加正负号显示（正数显示+号）
5. 百分比属性会自动添加%符号
6. 详细检查功能会显示修饰符的完整信息，包括来源、类型、槽位等
7. 修饰符来源和类型支持中文显示，可在语言文件中自定义
8. 统计功能会按来源和类型对修饰符进行分组统计