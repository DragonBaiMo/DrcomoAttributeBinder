# Draft: MMOCore 自定义属性加成异常排查

## Requirements (confirmed)
- 问题现象：新增 STR、STM 等自定义属性后，使用 `ADDITION_STR` 时预期物理加成未生效。
- 用户怀疑：可能属性映射错误，或 MMOCore 内部处理问题。
- 线索：发现 `REQUIRED_STR` 这类 MMOCore 内部属性也被映射出来，怀疑映射层把“需求属性”与“加成属性”混淆。
- 指定代码范围：
  - `I:\CustomBuild\Minecraft\Other\mmoitems-2025-7-13-持续更新`
  - `I:\CustomBuild\Minecraft\Other\mythiclib-12月12日-持续更新`
  - `I:\CustomBuild\Minecraft\Other\MMOCore-12月12-持续更新`
- 用户要求：最大化检索力度；并行使用 explore/librarian + Grep/rg/ast-grep；不要止步于首个结果。
- 用户补充：属性配置“确定无误”，问题仍复现，倾向于运行时链路/内部机制异常而非配置拼写。
- 用户最新证据：占位符解析侧也观察到“属性未增加”。
- 用户最终复现场景确认：执行 MMOItems 重载后，ABB 已加的 `ADDITIONAL_*` 关联效果在 MMOCore 侧丢失。

## Technical Decisions
- 先做跨仓库证据收集，再给出归因判断（配置错误 vs 内部实现 vs 版本兼容）。
- 重点追踪 `ADDITION_*` 与 `REQUIRED_*` 的声明、注册、消费链路。

## Research Findings
- 本地源码已定位到关键命名差异：
  - MMOItems 对 MMOCore 额外属性使用的是 `ADDITIONAL_` 前缀，不是 `ADDITION_`。
  - 证据：`...\mmoitems\comp\mmocore\stat\ExtraAttribute.java:12`
    - `super("ADDITIONAL_" + attribute.getId().toUpperCase().replace("-", "_"), ...)`
- MMOCore 在属性总值计算中消费的也是 `ADDITIONAL_` 与 `ADDITIONAL_*_PERCENT`：
  - 证据：`...\mmocore\api\player\attribute\PlayerAttributes.java:235,241`
    - `getStat("ADDITIONAL_" + enumName)`
    - `getStat("ADDITIONAL_" + enumName + "_PERCENT")`
- `REQUIRED_*` 被映射出来是预期行为：
  - MMOItems 启动时会为每个 MMOCore 属性同时注册：
    - 使用需求：`new RequiredAttribute(attribute)`
    - 额外加点：`new ExtraAttribute(attribute)`
  - 证据：`...\mmoitems\comp\mmocore\MMOCoreHook.java:42-50`
- `RequiredAttribute` 的确走 `REQUIRED_` 语义且仅用于可用性校验（是否可穿戴/可使用），不是战斗增益：
  - 证据：`...\mmoitems\comp\mmocore\stat\RequiredAttribute.java:18,24-30`
- 你自己的 AttributeBinder 命令补全来源已确认：
  - `CommandUtils.getStatSuggestions()` 直接读取 `MMOItems.plugin.getStats().getAll()`，并把所有 `DoubleStat` 的 `id` 作为 TAB 建议。
  - 证据：`...\DrcomoAttributeBinder\command\CommandUtils.java:137-145`
- 你自己的插件不会做 `ADDITION -> ADDITIONAL` 自动纠错：
  - 命令参数仅 `toUpperCase()` 后原样下发为 MythicLib Stat 名称。
  - 证据：`GiveCommand/ReplaceCommand` 的 `stat = args[2].toUpperCase()`；`AttributeApplier.apply` 用 `statUpper` 直接注册 `StatModifier`。
  - 相关文件：
    - `...\command\sub\GiveCommand.java:32,94`
    - `...\command\sub\ReplaceCommand.java:32,89`
    - `...\service\AttributeApplier.java:37-50`
- `REQUIRED_*` 出现在 TAB 的源码原因已确认（不是异常注入）：
  - `RequiredAttribute` 继承链：`RequiredAttribute -> RequiredLevelStat -> DoubleStat`。
  - 而你的 TAB 逻辑按“是否 DoubleStat”过滤，不区分“需求类 stat”与“加成类 stat”。
  - 证据：
    - `...\mmoitems\comp\mmocore\stat\RequiredAttribute.java:14`
    - `...\mmoitems\stat\type\RequiredLevelStat.java:26,38`
    - `...\DrcomoAttributeBinder\command\CommandUtils.java:140-145`
- `REQUIRED_*` 在 MMOItems 中是通用设计，不仅 MMOCore 属性：
  - 例如 `REQUIRED_CLASS` / `REQUIRED_BIOMES` / `REQUIRED_POWER` 等均存在。
  - 证据：
    - `...\mmoitems\stat\RequiredClass.java:35`
    - `...\mmoitems\stat\RequiredBiomes.java:22`
    - `...\mmoitems\stat\block\RequiredPower.java:8`

## Technical Interpretation
- 若你通过 TAB 选中了 `ADDITION_STR`，说明 **MMOItems 的 Stat Registry 中确实存在这个 ID**（至少在你运行时环境）。
- 但 MMOCore 属性总值消费链只读 `ADDITIONAL_*`，因此 `ADDITION_*` 会成为“可选但无消费者”的孤立 stat。
- `REQUIRED_*`“会被补全”本身并不奇怪；奇怪的是“补全层没有按语义过滤”，把需求 stat 和增益 stat 混在一起了。

## Preliminary Diagnosis
- 高概率根因：你当前使用的键是 `ADDITION_STR`，但源码链路实际识别的是 `ADDITIONAL_STR`（或对应属性 ID 规范化后的 `ADDITIONAL_{ATTRIBUTE_ID}`）。
- 次级风险：若属性 ID 不是 `str` 而是带连字符（如 `strength-main`），最终键会变成 `ADDITIONAL_STRENGTH_MAIN`。
- 新情况（用户截图）显示已使用 `ADDITIONAL_*`，问题仍存在，需转入二级根因：
  1) **属性 ID 不匹配**：MMOCore 读取 `ADDITIONAL_{enumName(attributeId)}`，若实际属性 ID 是 `strength` 而你加的是 `ADDITIONAL_STR`，则不会生效。
  2) **属性有点数但无有效 buff**：`ADDITIONAL_STR` 只会加“属性点数”，真实物理增益取决于 `attributes/*.yml` 中该属性的 `buff` 配置（如 `weapon_damage/physical_damage`）。
  3) **buff key 非法被忽略**：`PlayerAttribute` 解析 `buff` 时若 stat 名非法会抛 IAE 并记录 warning，导致属性点增加但战斗收益不变。
  4) **观测口径偏差**：看的是 lore/面板，不等于最终伤害链；需结合 mmocore stat 实时值与战斗事件验证。
- 根因收敛（高置信）：**`/mi reload` 导致 MMOItems/MMOCore 桥接注册链路重建，而 ABB 旧修饰符未被等价重放到新链路，表现为 `ADDITIONAL_*` 在占位符与属性总值中丢失**。

## Confirmed Root Cause (user-validated)
- 触发条件：MMOItems 热重载。
- 结果：ABB 已施加的属性仍在本地认知中存在，但 MMOCore 属性消费链不再反映。
- 与源码注释吻合：`MMOCoreHook` 中明确提示相关 stat 注册更新依赖实例化时机，热重载易出现状态不一致。

## Open Questions
- 在“配置无误”前提下，更需要确认是“显示异常”还是“真实战斗链路异常”：
  - 面板属性值变化但伤害不变？
  - 还是面板和伤害都不变？
  - 是否在重登/切装/`/mi reload` 后变化？
- 当前待确认：使用的具体占位符字符串及其来源插件（PlaceholderAPI 扩展）是哪一个键。

## Scope Boundaries
- INCLUDE: 三个指定仓库内的源码级证据、符号映射链路、可能的版本差异点。
- EXCLUDE: 直接修改代码实现（当前仅做诊断与规划）。
