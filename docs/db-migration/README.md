# MOYUYO 数据库迁移脚本

> 工具：Flyway 9.x+  
> 数据库：MySQL 8.0  
> 字符集：utf8mb4  
> 命名：`V{version}__{description}.sql`

---

## 迁移文件清单

| 版本 | 文件 | 说明 |
| :---: | :--- | :--- |
| V1 | `V1__init_user_and_oauth.sql` | 用户表改造 + 7 张 GDPR / OAuth / 2FA / 登录日志表 |
| V2 | `V2__init_business_tables.sql` | 商品 / 订单 / 支付 / 营销 / 社区 / 同步等 25 张业务表 |
| V3 | `V3__seed_data.sql` | 分类、品牌、协议版本、同意日志、同步位点初始化 |
| V4 | `V4__indexes_and_partition.sql` | 性能索引 + 订单分区（可选） |
| V5 | `V5__init_brand_and_ip.sql` | 4 大产品线表 `mo_collection`、4 大 IP 表 `mo_brand_ip` / `mo_product_ip`；`mo_category` 新增 `collection_id` 字段、`mo_product` 新增 `brand_ip_id` 字段 |
| V6 | `V6__init_pet_hub.sql` | Pet Hub 相关表：`mo_growth_record`（成长记录）、`mo_pet_reminder`（护理提醒）、`mo_pet_achievement`（成就徽章）、`mo_pet_scene`（3D 场景）；`mo_pet` 表新增护理字段（洗澡 / 疫苗 / 驱虫） |
| V7 | `V7__init_extra_tables.sql` | 补充业务表：收藏夹（`mo_favorites` / `mo_favorites_group`）、会员体系（`mo_member_wallet` / `mo_member_prime` / `mo_member_task`）、营销活动（`mo_marketing_activity` / `mo_invite_record`）、售后（`mo_return_request` / `mo_feedback`）、健康日历（`mo_pet_health_calendar`）、社区（`mo_community_topic` / `mo_community_follow`）；`mo_sync_checkpoint` 补充 `uk_biz_type` 唯一索引 |

> 各表字段定义、ER 关系与索引细节请参见根目录 [数据库设计.md](../数据库设计.md) §2（表结构）与 §2.1.7～§2.1.9（品牌 IP 表）、Pet Hub 相关章节。本目录脚本仅负责 DDL 落地，字段语义以设计文档为准。

---

## Flyway 配置（application.yml）

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
    out-of-order: false
    placeholder-replacement: false
    schemas: moyuyo
    table: schema_history
```

---

## 执行顺序

```
V1（用户与鉴权）  →  V2（业务表）  →  V3（种子数据）  →  V4（性能优化）  →  V5（品牌 IP / 产品线）  →  V6（Pet Hub）
```

---

## 回滚策略

> Flyway 不支持自动回滚，建议在每次迁移前备份：

```bash
# 备份
mysqldump -u root -p moyuyo > backup_before_v1_$(date +%Y%m%d).sql

# 回滚（手动）
mysql -u root -p moyuyo < backup_before_v1_20260702.sql
```

> 生产环境强烈建议在迁移前进行**全量备份 + 灰度执行**。

---

## 老系统迁移指南

如果已有部署的 MOYUYO 系统（基于国内手机号登录），需要按以下步骤平滑升级：

1. **V1 迁移前**：先执行 `SELECT COUNT(*) FROM mo_user WHERE email IS NULL` 确认影响行数。
2. **V1 中已处理**：将 `email IS NULL` 的用户自动填充为 `legacy_<id>@moyuyo.local`（占位邮箱）。
3. **后续引导**：用户首次登录时强制引导绑定真实邮箱。

---

## 注意事项

1. **生产环境执行窗口**：建议在低峰期（凌晨 03:00 - 05:00）执行。
2. **分库分表**：如使用 ShardingSphere，V4 的订单分区需调整为按 user_id 哈希分片。
3. **索引**：不要在 V1 / V2 中过度加索引，会影响写入性能，索引优化统一在 V4 中处理。
4. **种子数据**：V3 的种子数据仅适用于测试 / 预发环境，生产环境需改为运维手动初始化。
