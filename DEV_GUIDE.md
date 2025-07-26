# 开发者使用规范

- 调用 `AggregatedApplier.applyFromCache` 或 `applyAllFromCache` 可根据缓存一次性写入聚合后的属性。
- 若自行汇总数据，可使用 `applyAggregated`，需先在业务侧聚合同一 `uuid + stat + keyId` 的多来源数据。
- **百分比取值必须以百分数传入**，例如 30% 应传入 `30.0`。
- 本插件仅允许一个百分比乘区，禁止直接多次注册百分比修饰符。
