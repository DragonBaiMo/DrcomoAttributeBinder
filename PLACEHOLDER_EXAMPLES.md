# AttributeBinder 占位符示例（已与实现对齐）

## 1. 基础查询

### 当前玩家攻击伤害总值
```text
%attributebinder_attr_ATTACK_DAMAGE%
```

### 当前玩家指定 Key 的攻击伤害
```text
%attributebinder_attr_ATTACK_DAMAGE_sword_enchant%
```

### 指定玩家（显式 player 前缀）
```text
%attributebinder_attr_player:Alice_ATTACK_DAMAGE%
%attributebinder_attr_player:Alice_ATTACK_DAMAGE_sword1%
```

### 指定玩家（显式 uuid 前缀）
```text
%attributebinder_attr_uuid:550e8400-e29b-41d4-a716-446655440000_ATTACK_DAMAGE%
```

---

## 2. 列表与 Key 查询

### 列表
```text
%attributebinder_list%
%attributebinder_list_all%
%attributebinder_list_player:Alice%
%attributebinder_list_player:Alice_all%
```

### keys / keyattrs
```text
%attributebinder_keys_ATTACK_DAMAGE%
%attributebinder_keys_player:Alice_ATTACK_DAMAGE%
%attributebinder_keyattrs_sword_enchant%
%attributebinder_keyattrs_player:Alice_sword_enchant%
```

---

## 3. 修饰符调试

### inspect
```text
%attributebinder_inspect_ATTACK_DAMAGE%
%attributebinder_inspect_player:Alice_ATTACK_DAMAGE%
```

### source（来源过滤）
```text
%attributebinder_source_ATTACK_DAMAGE_MELEE_WEAPON%
%attributebinder_source_player:Alice_ATTACK_DAMAGE_ARMOR%
```

### stats（统计）
```text
%attributebinder_stats_ATTACK_DAMAGE%
%attributebinder_stats_player:Alice_ATTACK_DAMAGE%
```

---

## 4. 在 tellraw / GUI / 条件系统中的用法

### tellraw
```mcfunction
/tellraw @s {"text":"你的攻击伤害：%attributebinder_attr_ATTACK_DAMAGE%"}
/tellraw @s {"text":"%attributebinder_source_ATTACK_DAMAGE_MELEE_WEAPON%"}
```

### Java Lore
```java
List<String> lore = Arrays.asList(
    "&f当前攻击伤害：%attributebinder_attr_ATTACK_DAMAGE%",
    "&f主手来源：%attributebinder_source_ATTACK_DAMAGE_MAINHAND_ITEM%",
    "&f护甲来源：%attributebinder_source_ATTACK_DAMAGE_ARMOR%"
);
```

### 条件示例
```yaml
conditions:
  - "%attributebinder_attr_ATTACK_DAMAGE% > 20"
actions:
  - "tellraw %player% {\"text\":\"%attributebinder_inspect_ATTACK_DAMAGE%\"}"
```

---

## 5. 调试命令

```text
/drcomoattributebinder reload
/papi parse me %attributebinder_attr_ATTACK_DAMAGE%
/papi parse me %attributebinder_source_ATTACK_DAMAGE_MELEE_WEAPON%
```

> 说明：
> - 请使用 `drcomoattributebinder` 命令名。
> - `source` 的来源枚举必须是：ACCESSORY / ARMOR / HAND_ITEM / MAINHAND_ITEM / MELEE_WEAPON / OFFHAND_ITEM / ORNAMENT / OTHER / RANGED_WEAPON / VOID。
