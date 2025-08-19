# AttributeBinder 占位符使用示例

本文档提供了 AttributeBinder 插件新增占位符功能的实际使用示例。

## 基础属性查询示例

### 查看玩家攻击伤害
```
玩家当前攻击伤害：%attributebinder_attr_ATTACK_DAMAGE%
```
输出示例：`玩家当前攻击伤害：+15.5`

### 查看特定装备的攻击伤害加成
```
剑类武器攻击伤害：%attributebinder_attr_ATTACK_DAMAGE_sword_enchant%
```
输出示例：`剑类武器攻击伤害：+8.0`

## 详细修饰符检查示例

### 完整检查攻击伤害修饰符
```
%attributebinder_inspect_ATTACK_DAMAGE%
```
输出示例：
```
=== ATTACK_DAMAGE 属性详细信息 ===
总值: +25.5
修饰符列表:
  sword_base: +10.0 [固定值] (装备) HAND
  armor_set: +15.0% [相对值] (套装加成) CHEST
  skill_buff: +5.5 [固定值] (技能) OTHER
```

### 按来源过滤修饰符
```
近战武器来源的攻击伤害：%attributebinder_source_ATTACK_DAMAGE_MELEE_WEAPON%
```
输出示例：
```
=== ATTACK_DAMAGE 属性 - 近战武器 来源 ===
  sword_base: +10.0 [固定值]
  weapon_enchant: +2.0 [固定值]
```

### 属性统计信息
```
%attributebinder_stats_ATTACK_DAMAGE%
```
输出示例：
```
=== ATTACK_DAMAGE 属性统计 ===
总值: +25.5
修饰符数量: 5
固定值总计: +17.5
相对值总计: +15.0%
乘法值总计: +0.0%

来源分布:
  装备: 3个
  技能: 1个
  套装加成: 1个

类型分布:
  固定值: 4个
  相对值: 1个
```

## 实际应用场景

### 1. 装备检查命令
创建一个命令来检查玩家的装备属性：
```yaml
# 在插件配置中
commands:
  checkgear:
    description: "检查装备属性"
    usage: "/checkgear"
    permission: "drcomoattributebinder.checkgear"
```

命令执行时发送：
```
&a=== 装备属性检查 ===
&f攻击伤害：%attributebinder_attr_ATTACK_DAMAGE%
&f攻击速度：%attributebinder_attr_ATTACK_SPEED%
&f最大生命值：%attributebinder_attr_MAX_HEALTH%

&e详细信息：
%attributebinder_inspect_ATTACK_DAMAGE%
```

### 2. 属性面板GUI
在GUI中显示属性信息：
```java
// 在物品的Lore中使用占位符
List<String> lore = Arrays.asList(
    "&f当前攻击伤害：%attributebinder_attr_ATTACK_DAMAGE%",
    "&f来自近战武器：%attributebinder_source_ATTACK_DAMAGE_MELEE_WEAPON%",
    "&f来自护甲：%attributebinder_source_ATTACK_DAMAGE_ARMOR%"
);
```

### 3. 聊天栏属性显示
玩家输入命令查看属性：
```
/tellraw @s {"text":"%attributebinder_stats_ATTACK_DAMAGE%"}
```

### 4. 调试和管理
管理员检查玩家属性问题：
```
# 检查特定玩家的所有属性
/tellraw @a[name=PlayerName] {"text":"%attributebinder_list_all%"}

# 检查特定属性的详细信息
/tellraw @a[name=PlayerName] {"text":"%attributebinder_inspect_ATTACK_DAMAGE%"}
```

## 高级用法

### 条件显示
结合其他插件的条件系统：
```yaml
# 示例：只有当玩家攻击伤害大于20时才显示详细信息
conditions:
  - "%attributebinder_attr_ATTACK_DAMAGE% > 20"
actions:
  - "tellraw %player% {\"text\":\"%attributebinder_inspect_ATTACK_DAMAGE%\"}"
```

### 自定义格式化
在语言文件中自定义显示格式：
```yaml
# 自定义检查结果格式
placeholder-inspect-header: "&6▬▬▬ {stat} 属性分析 ▬▬▬\n&f总计：&e{total}\n&f详情：\n"
placeholder-inspect-item: "  &8▸ &6{key} &8→ &e{value} &7({type}) &8[{source}]\n"
```

## 性能注意事项

1. **缓存机制**：频繁查询的属性会被缓存，避免重复计算
2. **异步处理**：复杂的统计查询在异步线程中执行
3. **限制使用**：避免在高频事件中使用详细检查占位符
4. **批量查询**：使用 `list_all` 比多次单独查询更高效

## 故障排除

### 常见问题

1. **占位符返回空值**
   - 检查属性名称是否正确（区分大小写）
   - 确认 MythicLib/MMOItems 插件正常运行
   - 验证玩家是否有对应的属性数据

2. **修饰符来源显示为 "OTHER"**
   - 这是正常现象，表示修饰符来源未被明确分类
   - AttributeBinder 添加的修饰符通常显示为 "OTHER"

3. **百分比显示异常**
   - 检查修饰符类型是否正确识别
   - 相对值和乘法值会自动转换为百分比显示

4. **中文显示乱码**
   - 确保语言文件使用 UTF-8 编码
   - 检查服务器控制台编码设置

### 调试命令

```
# 检查插件状态
/attributebinder reload

# 测试占位符
/papi parse me %attributebinder_inspect_ATTACK_DAMAGE%

# 查看原始数据
/mmoitems debug %player%
```