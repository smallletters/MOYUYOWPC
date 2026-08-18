# 商品编辑页重构设计（对齐 WooCommerce）

## 背景

`d:\MOYUYOWPC\moyuyo-server\moyuyo-admin\src\views\ProductEdit.vue` 当前仅覆盖基础信息、定价、库存、物流、图片、发布状态 6 个区块，缺失大量 WooCommerce 标准字段，且没有：

- 商品类型切换（Simple / Variable / Grouped / External / Virtual / Downloadable）的联动可见性
- 销售日期区间（schedule sale price）
- 允许缺货订购（backorders）
- 单独出售（sold individually）
- 税状态 / 税类（tax status / tax class）
- 产品 URL / 按钮文本（external product）
- 下载文件 / 下载限制 / 下载到期（downloadable product）
- GTIN / UPC / EAN / ISBN 全球唯一标识
- 商品属性（attributes panel，可勾选“用于变体”）
- 变体表（variations）
- 关联商品（upsells / cross-sells）
- 高级选项卡：购买须知、菜单顺序、评论开关、作者等

改造目标：在保留现有 WC 双向同步能力基础上，将该页面结构与字段对齐 WooCommerce 商品编辑页（参考 `D:\xampp\htdocs\wordpress\wp-content\plugins\woocommerce\includes\admin\meta-boxes\views/`）。

## 方案概览

保留现有路由（`/products/edit/:id` 与 `/products/add`），不改动后端 API contract，把前端改造为：

- **顶部**：商品类型切换器（hidden `type_box`），仅在 `simple / variable / grouped / external` 间切换，并联动后端 show_if_*
- **中部**：7 个选项卡（与 WooCommerce 一致）：常规 / 库存 / 物流 / 关联商品 / 属性 / 变体 / 高级
- **底部**：底部操作栏（保存、取消、推送到 WC、从 WC 拉取、仅同步库存）
- **右侧**：WooCommerce 同步状态卡片 + 字段映射速查表（保留）

数据全部保存到 `form.attributes` JSON 中（不新增后端字段），向后兼容现有持久化结构。

## 选项卡字段矩阵

| 选项卡 | WC 字段 | UI 控件 | 联动规则 |
|---|---|---|---|
| 常规 | name | input | - |
| 常规 | slug / permalink | input | - |
| 常规 | description | textarea | - |
| 常规 | short_description | textarea | - |
| 常规 | regular_price | input number | show_if_simple / show_if_external |
| 常规 | sale_price | input number | show_if_simple / show_if_external |
| 常规 | sale_price_dates_from / _to | el-date-picker daterange | show_if_simple |
| 常规 | product_url | input | show_if_external |
| 常规 | button_text | input | show_if_external |
| 常规 | tax_status | select（taxable / shipping / none） | show_if_simple / show_if_external / show_if_variable |
| 常规 | tax_class | select（standard / reduced-rate / zero-rate） | 同上 |
| 库存 | sku | input | - |
| 库存 | global_unique_id (GTIN/UPC/EAN/ISBN) | input | - |
| 库存 | manage_stock | switch | - |
| 库存 | stock_quantity | input number | dependsOn manage_stock |
| 库存 | backorders | radio（no / notify / yes） | dependsOn manage_stock |
| 库存 | low_stock_amount | input number | dependsOn manage_stock |
| 库存 | stock_status | radio（instock / outofstock / onbackorder） | - |
| 库存 | sold_individually | checkbox | - |
| 物流 | weight | input number | show_if_simple / show_if_variable |
| 物流 | dimensions (length/width/height) | 3 inputs | 同上 |
| 物流 | shipping_class | select（预置：标准 / 冷链 / 大件 / 海外直邮） | 同上 |
| 关联商品 | upsell_ids | el-select multiple + 产品选择弹窗 | - |
| 关联商品 | cross_sell_ids | el-select multiple + 产品选择弹窗 | - |
| 属性 | 自定义属性表 | 动态行（name / values / visible / variation） | - |
| 变体 | variations | 变体表 + 单条变体展开 | show_if_variable |
| 高级 | purchase_note | textarea | - |
| 高级 | menu_order | input number | - |
| 高级 | reviews_allowed | switch | - |
| 高级 | virtual | checkbox（来自 type_box） | - |
| 高级 | downloadable | checkbox | - |

## 商品类型切换器（type_box）

```
[下拉选择: simple | variable | grouped | external]
[ ] Virtual     [ ] Downloadable
```

- `virtual` 与 `downloadable` 在 type_box 右侧以复选框显示
- 切换商品类型 → 自动隐藏/显示对应选项卡的字段
- 切换为 `variable` 时，强制启用 `manage_stock = false`（WC 行为：变体级别库存）
- 切换为 `external` 时禁用库存相关字段
- `grouped` 隐藏常规定价

## 关联商品选择弹窗

- 点击"添加商品"按钮打开 `el-dialog`
- 顶部搜索框 + 表格（产品名 / SKU / 价格 / 库存）
- 多选 checkbox，确定后回填到 `upsell_ids` / `cross_sell_ids`
- 已选商品以 tag 形式展示，可单独移除

## 属性面板（attributes）

每个属性行：
- 名称（input）
- 值（多个 tag 输入，`el-select` multiple + `allow-create`）
- 用于变体（checkbox，仅当 type=variable 可见）
- 在商品页面可见（checkbox）
- 用于过滤（checkbox）
- 删除按钮

工具栏：
- 展开 / 收起全部
- 添加自定义属性
- 保存属性

## 变体面板（variations）

- 默认空态：提示"先在属性面板中创建用于变体的属性"
- 当存在 `variation=true` 的属性时，自动生成笛卡尔积变体
- 变体列表：表格
  - 列：缩略图 / 变体属性值（如 红色, M） / SKU / 价格 / 库存 / 状态 / 操作
  - 行可展开：变体级别价格、库存、重量、尺寸、上下架
- 工具栏：
  - 添加手动变体
  - 批量设置价格 / 库存 / 上下架（el-popover + 输入）
  - 展开全部 / 收起全部

## 数据结构

新增 `form` 字段（保留原有字段）：

```js
form = {
  // 原有字段保持不变
  id, name, categoryId, spuCode, productType, price, originalPrice,
  stock, sales, stockStatus, manageStock, weight, detail, shortDetail,
  mainImage, onSale, wooProductId, wooModified, brandId, brandIpId, attributes,

  // 新增
  slug: '',
  permalink: '',
  salePriceDatesFrom: '',
  salePriceDatesTo: '',
  taxStatus: 'taxable',
  taxClass: 'standard',
  globalUniqueId: '',          // GTIN/UPC/EAN/ISBN
  backorders: 'no',            // no | notify | yes
  lowStockAmount: '',
  soldIndividually: false,
  shippingClass: '',
  isVirtual: false,
  isDownloadable: false,
  productUrl: '',
  buttonText: '',
  purchaseNote: '',
  menuOrder: 0,
  reviewsAllowed: true,

  upsellIds: [],               // number[]
  crossSellIds: [],            // number[]
  customAttributes: [],        // { name, options[], visible, variation }
  variations: [],              // { id, attributes: {color,size}, sku, price, stock, manageStock, ... }
}
```

## 持久化策略

不修改后端，所有新字段统一序列化进 `attributes` JSON（key 命名对齐 WC）：

```json
{
  "dimensions": { "length": 0, "width": 0, "height": 0 },
  "global_unique_id": "",
  "backorders": "no",
  "low_stock_amount": "",
  "sold_individually": false,
  "shipping_class": "",
  "tax_status": "taxable",
  "tax_class": "standard",
  "product_url": "",
  "button_text": "",
  "purchase_note": "",
  "menu_order": 0,
  "reviews_allowed": true,
  "is_virtual": false,
  "is_downloadable": false,
  "slug": "",
  "sale_price_dates_from": "",
  "sale_price_dates_to": "",
  "upsell_ids": [],
  "cross_sell_ids": [],
  "custom_attributes": [],
  "variations": []
}
```

向后兼容：现有 `dimensions` 解析逻辑保留并扩展。

## 文件改动

| 操作 | 路径 | 说明 |
|---|---|---|
| 修改 | `moyuyo-admin/src/views/ProductEdit.vue` | 完整重写（约 1200 行） |
| 修改 | `moyuyo-admin/src/api/admin.js` | 新增 `searchProductsLite()` 用于关联商品选择弹窗 |
| 修改 | `moyuyo-admin/src/router/index.js` | 不改（路由不变） |
| 新增 | `moyuyo-admin/src/views/ProductEdit/` | 子组件目录（可选，如需拆分） |

> 实施时优先单文件实现；如超过 1500 行再拆分为 5 个子组件（`TypeBox.vue` / `TabGeneral.vue` / `TabInventory.vue` / `TabShipping.vue` / `TabLinked.vue` / `TabAttributes.vue` / `TabVariations.vue` / `TabAdvanced.vue`）。

## 验证清单

- [ ] `/products/add` 渲染正常，无 JS 报错
- [ ] 切换商品类型 → 字段联动显示/隐藏
- [ ] simple 商品保存后 `attributes` JSON 包含全部新字段
- [ ] 编辑现有商品 → 不丢失原有数据（dimensions、原有字段）
- [ ] variable 商品 → 变体表生成笛卡尔积、变体可展开编辑
- [ ] 关联商品弹窗 → 多选 + 搜索可用
- [ ] WC 推送 / 拉取 / 库存同步按钮仍正常工作
- [ ] 控制台无 Vue 警告（key 缺失、props 类型错误等）

## 范围外

- 后端字段扩展（不在本次范围；如需持久化更多字段需要新增 SQL 迁移）
- 真正的可下载文件管理（仅保留 downloadable 标记）
- Grouped 商品的子商品管理（UI 仅展示 product_type 切换，不实现子商品编辑）
- WooCommerce 插件同步（属性 → WC attribute taxonomies 的实时映射；后续单独任务）