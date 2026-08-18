<template>
  <div class="product-edit">
    <!-- 面包屑 -->
    <div class="breadcrumb-custom">
      <router-link to="/products">商品管理</router-link>
      <span class="separator">/</span>
      <span class="current">{{ isNew ? '新增商品' : '编辑商品' }}</span>
    </div>

    <div class="page-title-area">
      <h1>{{ isNew ? '新增商品' : '编辑商品' }}</h1>
      <p>字段结构对齐 WooCommerce 商品编辑页（General / Inventory / Shipping / Linked / Attributes / Variations / Advanced）</p>
    </div>

    <!-- 顶部：商品类型选择器（对齐 WC type_box） -->
    <div class="type-box">
      <label class="type-label">
        <span class="type-label-text">商品类型 (Product type)</span>
        <select v-model="form.productType" class="type-select" @change="onTypeChange">
          <option value="simple">Simple — 简单商品</option>
          <option value="variable">Variable — 可变商品（含规格）</option>
          <option value="grouped">Grouped — 组合商品</option>
          <option value="external">External — 外部/附属商品</option>
        </select>
        <span class="type-hint">WooCommerce: type</span>
      </label>

      <label class="type-check">
        <input type="checkbox" v-model="form.isVirtual" @change="syncVirtualDownloadable" />
        <span>Virtual</span>
        <span class="type-hint">虚拟商品（无物流）</span>
      </label>
      <label class="type-check">
        <input type="checkbox" v-model="form.isDownloadable" @change="syncVirtualDownloadable" />
        <span>Downloadable</span>
        <span class="type-hint">可下载商品</span>
      </label>
    </div>

    <div class="edit-layout">
      <!-- 左侧：商品表单（7 选项卡） -->
      <div class="edit-main">
        <div class="wc-tabs">
          <button
            v-for="t in tabs"
            :key="t.key"
            class="wc-tab"
            :class="{ active: activeTab === t.key, hidden: t.showIf && !t.showIf() }"
            @click="activeTab = t.key"
          >{{ t.label }}</button>
        </div>

        <div v-show="activeTab === 'general'" class="tab-pane">
          <TabGeneral :form="form" :categories="categories" @permalink-change="onPermalinkChange" />
        </div>
        <div v-show="activeTab === 'inventory'" class="tab-pane">
          <TabInventory :form="form" />
        </div>
        <div v-show="activeTab === 'shipping'" class="tab-pane">
          <TabShipping :form="form" :dimensions="dimensions" :shippingClasses="shippingClasses" />
        </div>
        <div v-show="activeTab === 'linked'" class="tab-pane">
          <TabLinked
            :form="form"
            @open-upsell="openProductPicker('upsell')"
            @open-cross="openProductPicker('cross')"
          />
        </div>
        <div v-show="activeTab === 'attributes'" class="tab-pane">
          <TabAttributes :form="form" @regenerate-variations="regenerateVariations" />
        </div>
        <div v-show="activeTab === 'variations'" class="tab-pane">
          <TabVariations
            :form="form"
            :categories="categories"
            @open-upsell="openProductPicker('upsell')"
          />
        </div>
        <div v-show="activeTab === 'advanced'" class="tab-pane">
          <TabAdvanced :form="form" />
        </div>
      </div>

      <!-- 右侧：WooCommerce 同步面板 -->
      <div class="edit-sidebar">
        <div class="form-card">
          <div class="form-card-header" style="background:var(--brand-50)">
            <span class="form-card-icon">🔄</span>
            <span>WooCommerce 同步</span>
          </div>
          <div class="form-card-body">
            <div class="wc-info-row">
              <span class="wc-info-label">WC 商品 ID</span>
              <span class="wc-info-value">
                <template v-if="form.wooProductId">
                  <a :href="wooProductUrl" target="_blank" class="wc-link">{{ form.wooProductId }} ↗</a>
                </template>
                <span v-else class="wc-na">未同步</span>
              </span>
            </div>
            <div class="wc-info-row">
              <span class="wc-info-label">同步状态</span>
              <span class="wc-info-value">
                <span v-if="form.wooProductId" class="tag tag-green">已关联</span>
                <span v-else class="tag tag-gray">未关联</span>
              </span>
            </div>
            <div class="wc-info-row">
              <span class="wc-info-label">最后同步</span>
              <span class="wc-info-value">
                <template v-if="form.wooModified">{{ formatTime(form.wooModified) }}</template>
                <span v-else class="wc-na">从未同步</span>
              </span>
            </div>

            <div v-if="pullResult" class="wc-data-preview">
              <div class="wc-data-title">上次拉取数据</div>
              <div class="wc-data-row"><span>名称</span><span>{{ pullResult.name }}</span></div>
              <div class="wc-data-row"><span>原价</span><span>${{ pullResult.regularPrice || '-' }}</span></div>
              <div class="wc-data-row"><span>售价</span><span>${{ pullResult.salePrice || '-' }}</span></div>
              <div class="wc-data-row"><span>库存</span><span>{{ pullResult.stockQuantity ?? '-' }}</span></div>
              <div class="wc-data-row"><span>SKU</span><span>{{ pullResult.sku || '-' }}</span></div>
              <div class="wc-data-row"><span>类型</span><span>{{ pullResult.type || '-' }}</span></div>
              <div class="wc-data-row"><span>重量</span><span>{{ pullResult.weight || '-' }} kg</span></div>
            </div>

            <div class="wc-actions">
              <button class="btn btn-primary btn-block" :disabled="syncing" @click="handlePushToWoo">
                {{ syncing ? '同步中...' : (form.wooProductId ? '推送到 WooCommerce' : '首次同步到 WC') }}
              </button>
              <button v-if="form.wooProductId" class="btn btn-pull btn-block" :disabled="syncing" @click="handlePullFromWoo">
                {{ syncing ? '拉取中...' : '从 WooCommerce 拉取更新' }}
              </button>
              <button v-if="form.wooProductId" class="btn btn-stock-sync btn-block" :disabled="syncing" @click="handleSyncStock">
                {{ syncing ? '同步中...' : '仅拉取库存' }}
              </button>
              <div v-if="syncMessage" :class="'sync-message sync-' + syncStatus">{{ syncMessage }}</div>
            </div>
          </div>
        </div>

        <div class="form-card">
          <div class="form-card-header">
            <span class="form-card-icon">📐</span>
            <span>WooCommerce 字段映射</span>
          </div>
          <div class="form-card-body">
            <div class="field-map-list">
              <div class="field-map-item"><code>name</code><span class="map-arrow">←</span><span>商品名称</span></div>
              <div class="field-map-item"><code>type</code><span class="map-arrow">←</span><span>商品类型</span></div>
              <div class="field-map-item"><code>sku</code><span class="map-arrow">←</span><span>SKU 编码</span></div>
              <div class="field-map-item"><code>global_unique_id</code><span class="map-arrow">←</span><span>GTIN/UPC/EAN/ISBN</span></div>
              <div class="field-map-item"><code>regular_price</code><span class="map-arrow">←</span><span>原价</span></div>
              <div class="field-map-item"><code>sale_price</code><span class="map-arrow">←</span><span>售价</span></div>
              <div class="field-map-item"><code>date_on_sale_from/to</code><span class="map-arrow">←</span><span>销售日期</span></div>
              <div class="field-map-item"><code>tax_status / tax_class</code><span class="map-arrow">←</span><span>税状态/税类</span></div>
              <div class="field-map-item"><code>manage_stock</code><span class="map-arrow">←</span><span>库存管理</span></div>
              <div class="field-map-item"><code>stock_quantity / backorders</code><span class="map-arrow">←</span><span>库存/缺货订购</span></div>
              <div class="field-map-item"><code>low_stock_amount</code><span class="map-arrow">←</span><span>低库存阈值</span></div>
              <div class="field-map-item"><code>sold_individually</code><span class="map-arrow">←</span><span>单独出售</span></div>
              <div class="field-map-item"><code>weight / dimensions</code><span class="map-arrow">←</span><span>重量/尺寸</span></div>
              <div class="field-map-item"><code>shipping_class</code><span class="map-arrow">←</span><span>运费类</span></div>
              <div class="field-map-item"><code>upsell_ids / cross_sell_ids</code><span class="map-arrow">←</span><span>关联商品</span></div>
              <div class="field-map-item"><code>attributes / variations</code><span class="map-arrow">←</span><span>属性/变体</span></div>
              <div class="field-map-item"><code>purchase_note / menu_order</code><span class="map-arrow">←</span><span>购买须知/排序</span></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部操作栏 -->
    <div class="form-footer">
      <button class="btn btn-primary btn-lg" @click="handleSave" :disabled="saving">
        {{ saving ? '保存中...' : '保存' }}
      </button>
      <button class="btn btn-outline btn-lg" @click="handleCancel">取消</button>
    </div>

    <!-- 关联商品选择弹窗 -->
    <ProductPickerDialog
      v-model="pickerVisible"
      :title="pickerTitle"
      :exclude-id="form.id"
      @confirm="onPickerConfirm"
    />
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getProductDetail, createProduct, updateProduct,
  getCategoryList, pushProductToWoo, pullProductFromWoo,
  syncProductStock
} from '../api/admin'

import TabGeneral from './ProductEdit/TabGeneral.vue'
import TabInventory from './ProductEdit/TabInventory.vue'
import TabShipping from './ProductEdit/TabShipping.vue'
import TabLinked from './ProductEdit/TabLinked.vue'
import TabAttributes from './ProductEdit/TabAttributes.vue'
import TabVariations from './ProductEdit/TabVariations.vue'
import TabAdvanced from './ProductEdit/TabAdvanced.vue'
import ProductPickerDialog from './ProductEdit/ProductPickerDialog.vue'

const router = useRouter()
const route = useRoute()

const saving = ref(false)
const syncing = ref(false)
const syncMessage = ref('')
const syncStatus = ref('')
const categories = ref([])
const pullResult = ref(null)
const activeTab = ref('general')

// 关联商品弹窗状态
const pickerVisible = ref(false)
const pickerTitle = ref('')
const pickerTarget = ref('') // upsell | cross

// 默认 form 字段（含本次新增）
function blankForm() {
  return {
    // 原有
    id: null,
    name: '',
    categoryId: '',
    spuCode: '',
    productType: 'simple',
    tags: '',
    price: '',
    originalPrice: '',
    stock: '',
    sales: '',
    stockStatus: 'IN_STOCK',
    manageStock: false,
    weight: '',
    detail: '',
    shortDetail: '',
    mainImage: '',
    images: [],     // 商品图库：[{ url, name }]，首张为封面（对齐 WC images[]）
    onSale: true,
    wooProductId: null,
    wooModified: null,
    brandId: null,
    brandIpId: null,
    attributes: null,
    // 新增 - 对齐 WC
    slug: '',
    salePriceDatesFrom: '',
    salePriceDatesTo: '',
    taxStatus: 'taxable',
    taxClass: 'standard',
    globalUniqueId: '',
    backorders: 'no',
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
    upsellIds: [],
    crossSellIds: [],
    customAttributes: [],
    variations: []
  }
}

const form = reactive(blankForm())

const dimensions = reactive({ length: '', width: '', height: '' })

// 运费类（WC 默认 none / standard / cold / bulky / oversea）
const shippingClasses = [
  { value: '', label: '无运费类 (No shipping class)' },
  { value: 'standard', label: '标准配送' },
  { value: 'cold', label: '冷链配送' },
  { value: 'bulky', label: '大件物流' },
  { value: 'oversea', label: '海外直邮' }
]

const productId = computed(() => route.params.id)
const isNew = computed(() => !productId.value || productId.value === 'new')

const wooProductUrl = computed(() => {
  if (!form.wooProductId) return '#'
  return `https://your-woocommerce-store.com/wp-admin/post.php?post=${form.wooProductId}&action=edit`
})

// 选项卡定义（showIf 控制显示）
const tabs = computed(() => [
  { key: 'general', label: '常规 General' },
  { key: 'inventory', label: '库存 Inventory' },
  { key: 'shipping', label: '物流 Shipping', showIf: () => !form.isVirtual && form.productType !== 'grouped' && form.productType !== 'external' },
  { key: 'linked', label: '关联商品 Linked Products' },
  { key: 'attributes', label: '属性 Attributes' },
  { key: 'variations', label: '变体 Variations', showIf: () => form.productType === 'variable' },
  { key: 'advanced', label: '高级 Advanced' }
])

// 商品类型切换：联动简单行为
function onTypeChange() {
  // 切到 variable：WC 行为是变体级库存，但保留用户设置
  if (form.productType === 'external') {
    form.manageStock = false
  }
}

// virtual/downloadable 切换：虚拟商品隐藏物流
function syncVirtualDownloadable() {
  // 仅 UI 联动，由 tabs.showIf 控制
}

// 当常规选项卡名称变化时更新 permalink
function onPermalinkChange(name) {
  if (!form.slug || form.slug === slugify(form.name)) {
    form.slug = slugify(name)
  }
}

function slugify(s) {
  if (!s) return ''
  return String(s).toLowerCase().trim().replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '-').replace(/^-+|-+$/g, '')
}

async function fetchCategories() {
  try {
    const list = await getCategoryList()
    categories.value = list || []
  } catch (e) {
    console.error('获取分类列表失败:', e)
  }
}

async function fetchProduct() {
  const id = productId.value
  if (!id || isNew.value) return
  try {
    const res = await getProductDetail(id)
    if (res) {
      // 先填基本字段
      Object.assign(form, blankForm(), {
        id: res.id,
        name: res.name || '',
        categoryId: res.categoryId || '',
        spuCode: res.spuCode || '',
        productType: res.productType || 'simple',
        tags: res.tags || '',
        price: res.price != null ? String(res.price) : '',
        originalPrice: res.originalPrice != null ? String(res.originalPrice) : '',
        stock: res.stock != null ? String(res.stock) : '',
        sales: res.sales != null ? String(res.sales) : '',
        stockStatus: res.stockStatus || 'IN_STOCK',
        manageStock: res.manageStock || false,
        weight: res.weight != null ? String(res.weight) : '',
        detail: res.detail || '',
        shortDetail: res.shortDetail || '',
        mainImage: res.mainImage || '',
        images: Array.isArray(res.images) ? res.images : [],
        onSale: res.onSale !== undefined ? res.onSale : true,
        wooProductId: res.wooProductId || null,
        wooModified: res.wooModified || null,
        brandId: res.brandId || null,
        brandIpId: res.brandIpId || null,
        attributes: res.attributes || null
      })
      // 解析 attributes JSON（dimensions + WC 新字段 + 自定义属性 + 变体）
      parseAllFromAttributes(res.attributes)
    }
  } catch (err) {
    console.error('获取商品详情失败:', err)
    ElMessage.error('获取商品详情失败')
  }
}

// 统一从 attributes JSON 反序列化所有新增字段（向后兼容）
function parseAllFromAttributes(attrsJson) {
  if (!attrsJson) return
  let attrs = null
  try {
    attrs = typeof attrsJson === 'string' ? JSON.parse(attrsJson) : attrsJson
  } catch (e) {
    return
  }
  if (!attrs || typeof attrs !== 'object') return

  // dimensions
  if (attrs.dimensions) {
    const d = attrs.dimensions
    dimensions.length = d.length != null ? String(d.length) : ''
    dimensions.width = d.width != null ? String(d.width) : ''
    dimensions.height = d.height != null ? String(d.height) : ''
  }
  // 新增 WC 字段
  form.slug = attrs.slug || ''
  form.salePriceDatesFrom = attrs.sale_price_dates_from || ''
  form.salePriceDatesTo = attrs.sale_price_dates_to || ''
  form.taxStatus = attrs.tax_status || 'taxable'
  form.taxClass = attrs.tax_class || 'standard'
  form.globalUniqueId = attrs.global_unique_id || ''
  form.backorders = attrs.backorders || 'no'
  form.lowStockAmount = attrs.low_stock_amount != null ? String(attrs.low_stock_amount) : ''
  form.soldIndividually = !!attrs.sold_individually
  form.shippingClass = attrs.shipping_class || ''
  form.isVirtual = !!attrs.is_virtual
  form.isDownloadable = !!attrs.is_downloadable
  form.productUrl = attrs.product_url || ''
  form.buttonText = attrs.button_text || ''
  form.purchaseNote = attrs.purchase_note || ''
  form.menuOrder = attrs.menu_order != null ? Number(attrs.menu_order) : 0
  form.reviewsAllowed = attrs.reviews_allowed !== false
  form.upsellIds = Array.isArray(attrs.upsell_ids) ? attrs.upsell_ids : []
  form.crossSellIds = Array.isArray(attrs.cross_sell_ids) ? attrs.cross_sell_ids : []
  form.customAttributes = Array.isArray(attrs.custom_attributes) ? attrs.custom_attributes : []
  form.variations = Array.isArray(attrs.variations) ? attrs.variations : []
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('商品名称不能为空')
    return
  }
  if (!form.categoryId) {
    ElMessage.warning('请选择商品分类')
    return
  }

  saving.value = true
  try {
    const body = {
      name: form.name,
      categoryId: form.categoryId ? Number(form.categoryId) : null,
      spuCode: form.spuCode || null,
      productType: form.productType || 'simple',
      tags: form.tags || null,
      price: form.price ? Number(form.price) : null,
      originalPrice: form.originalPrice ? Number(form.originalPrice) : null,
      stock: form.stock ? Number(form.stock) : 0,
      stockStatus: form.stockStatus || 'IN_STOCK',
      manageStock: form.manageStock,
      weight: form.weight ? Number(form.weight) : null,
      detail: form.detail || null,
      shortDetail: form.shortDetail || null,
      mainImage: form.mainImage || (form.images[0]?.url) || null,
      // 多图上传：images 数组持久化到 attributes JSON
      images: form.images,
      onSale: form.onSale,
      attributes: serializeAllAttributes(),
      // 兼容 snake_case 后端字段
      spu_code: form.spuCode || null,
      main_image: form.mainImage || (form.images[0]?.url) || null,
      original_price: form.originalPrice ? Number(form.originalPrice) : null,
      stock_status: form.stockStatus || 'IN_STOCK',
      manage_stock: form.manageStock,
      product_type: form.productType || 'simple',
      short_detail: form.shortDetail || null,
      on_sale: form.onSale,
      brandId: form.brandId || null,
      brandIpId: form.brandIpId || null
    }

    const id = productId.value
    if (id && !isNew.value) {
      await updateProduct(id, body)
      ElMessage.success('商品已更新')
    } else {
      const result = await createProduct(body)
      ElMessage.success('商品已创建')
      if (result && result.id) {
        router.replace(`/products/edit/${result.id}`)
        return
      }
    }
    router.push('/products')
  } catch (err) {
    console.error('保存商品失败:', err)
    ElMessage.error('保存商品失败')
  } finally {
    saving.value = false
  }
}

// 序列化所有 attributes JSON（dimensions + 新增 WC 字段）
function serializeAllAttributes() {
  const hasDimensions = dimensions.length || dimensions.width || dimensions.height
  const hasNewFields =
    form.slug || form.salePriceDatesFrom || form.salePriceDatesTo ||
    form.taxStatus !== 'taxable' || form.taxClass !== 'standard' ||
    form.globalUniqueId || form.backorders !== 'no' || form.lowStockAmount ||
    form.soldIndividually || form.shippingClass ||
    form.isVirtual || form.isDownloadable ||
    form.productUrl || form.buttonText ||
    form.purchaseNote || form.menuOrder !== 0 || !form.reviewsAllowed ||
    form.upsellIds.length || form.crossSellIds.length ||
    form.customAttributes.length || form.variations.length

  if (!hasDimensions && !hasNewFields && !form.attributes) return form.attributes

  let attrs = {}
  try {
    if (form.attributes) {
      attrs = typeof form.attributes === 'string' ? JSON.parse(form.attributes) : form.attributes
      if (!attrs || typeof attrs !== 'object') attrs = {}
    }
  } catch (e) { attrs = {} }

  if (hasDimensions) {
    attrs.dimensions = {
      length: dimensions.length || '0',
      width: dimensions.width || '0',
      height: dimensions.height || '0'
    }
  }
  if (hasNewFields) {
    attrs.slug = form.slug || ''
    attrs.sale_price_dates_from = form.salePriceDatesFrom || ''
    attrs.sale_price_dates_to = form.salePriceDatesTo || ''
    attrs.tax_status = form.taxStatus
    attrs.tax_class = form.taxClass
    attrs.global_unique_id = form.globalUniqueId || ''
    attrs.backorders = form.backorders
    attrs.low_stock_amount = form.lowStockAmount || ''
    attrs.sold_individually = form.soldIndividually
    attrs.shipping_class = form.shippingClass || ''
    attrs.is_virtual = form.isVirtual
    attrs.is_downloadable = form.isDownloadable
    attrs.product_url = form.productUrl || ''
    attrs.button_text = form.buttonText || ''
    attrs.purchase_note = form.purchaseNote || ''
    attrs.menu_order = form.menuOrder || 0
    attrs.reviews_allowed = form.reviewsAllowed
    attrs.upsell_ids = form.upsellIds
    attrs.cross_sell_ids = form.crossSellIds
    attrs.custom_attributes = form.customAttributes
    attrs.variations = form.variations
  }
  return JSON.stringify(attrs)
}

// 变体生成（笛卡尔积）
function regenerateVariations() {
  const variationAttrs = (form.customAttributes || []).filter(a => a && a.variation && a.options && a.options.length)
  if (!variationAttrs.length) {
    form.variations = []
    return
  }
  // 笛卡尔积
  const cartesian = (arrs) => arrs.reduce((a, b) => a.flatMap(x => b.map(y => [...x, y])), [[]])
  const attrValues = variationAttrs.map(a => a.options.map(o => ({ name: a.name, value: o })))
  const combos = cartesian(attrValues)

  // 保留已有变体的可编辑字段（按 attributes 签名匹配）
  const existing = form.variations || []
  const sigOf = (v) => JSON.stringify((v.attributes || []).slice().sort((a, b) => a.name.localeCompare(b.name)))

  form.variations = combos.map(combo => {
    const sig = JSON.stringify(combo.slice().sort((a, b) => a.name.localeCompare(b.name)))
    const found = existing.find(v => sigOf(v) === sig)
    return found || {
      id: 'tmp_' + Math.random().toString(36).slice(2, 10),
      attributes: combo,
      sku: '',
      regularPrice: '',
      salePrice: '',
      manageStock: false,
      stockQuantity: 0,
      stockStatus: 'instock',
      weight: '',
      dimensions: { length: '', width: '', height: '' },
      enabled: true
    }
  })
}

// 关联商品选择
function openProductPicker(target) {
  pickerTarget.value = target
  pickerTitle.value = target === 'upsell' ? '选择 Upsell 商品（加价销售）' : '选择 Cross-sell 商品（交叉销售）'
  pickerVisible.value = true
}

function onPickerConfirm(selected) {
  if (pickerTarget.value === 'upsell') {
    form.upsellIds = selected.map(p => p.id)
  } else {
    form.crossSellIds = selected.map(p => p.id)
  }
}

async function handlePushToWoo() {
  const id = productId.value
  if (!id || isNew.value) return
  syncing.value = true
  syncMessage.value = ''
  pullResult.value = null
  try {
    const res = await pushProductToWoo(id)
    if (res && res.wooProductId) {
      form.wooProductId = res.wooProductId
      form.wooModified = new Date().toISOString()
      syncMessage.value = `推送成功，WC 商品 ID: ${res.wooProductId}`
      syncStatus.value = 'success'
      ElMessage.success('商品已推送到 WooCommerce')
    } else {
      syncMessage.value = res?.message || '推送失败，请检查 WooCommerce 配置'
      syncStatus.value = 'error'
    }
  } catch (e) {
    const msg = e?.response?.data?.message || e.message || 'WooCommerce 同步异常'
    syncMessage.value = msg
    syncStatus.value = 'error'
    ElMessage.error(msg)
  } finally {
    syncing.value = false
  }
}

async function handlePullFromWoo() {
  if (!form.wooProductId) {
    ElMessage.warning('商品尚未关联 WooCommerce，请先推送同步')
    return
  }
  syncing.value = true
  syncMessage.value = ''
  pullResult.value = null
  try {
    const res = await pullProductFromWoo(productId.value)
    if (res && res.message) {
      syncMessage.value = res.message
      syncStatus.value = 'success'
      ElMessage.success(res.message)
      pullResult.value = {
        name: res.name,
        regularPrice: res.regularPrice || res.regular_price,
        salePrice: res.salePrice || res.sale_price,
        stockQuantity: res.stockQuantity || res.stock_quantity,
        sku: res.sku,
        type: res.type,
        weight: res.weight
      }
      await fetchProduct()
    } else {
      syncMessage.value = res?.message || '拉取失败'
      syncStatus.value = 'error'
    }
  } catch (e) {
    const msg = e?.response?.data?.message || e.message || '拉取失败'
    syncMessage.value = msg
    syncStatus.value = 'error'
    ElMessage.error(msg)
  } finally {
    syncing.value = false
  }
}

async function handleSyncStock() {
  if (!form.wooProductId) {
    ElMessage.warning('商品尚未关联 WooCommerce')
    return
  }
  syncing.value = true
  syncMessage.value = ''
  try {
    const res = await syncProductStock(productId.value)
    if (res && res.stock !== undefined) {
      syncMessage.value = `库存已同步：${res.stock} (${res.stockStatus})`
      syncStatus.value = 'success'
      ElMessage.success(syncMessage.value)
      await fetchProduct()
    } else {
      syncMessage.value = res?.message || '库存拉取失败'
      syncStatus.value = 'error'
    }
  } catch (e) {
    const msg = e?.response?.data?.message || e.message || '库存拉取失败'
    syncMessage.value = msg
    syncStatus.value = 'error'
    ElMessage.error(msg)
  } finally {
    syncing.value = false
  }
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function onImageError(e) {
  e.target.style.display = 'none'
}

function handleCancel() {
  router.push('/products')
}

onMounted(() => {
  Promise.all([fetchCategories(), fetchProduct()])
})
</script>

<style scoped>
.product-edit {
  max-width: 1200px;
  padding: 28px 28px 40px;
}

.breadcrumb-custom {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  margin-bottom: 16px;
  color: var(--text-500);
}
.breadcrumb-custom a { color: var(--brand-600); text-decoration: none; }
.breadcrumb-custom .separator { color: var(--text-400); }
.breadcrumb-custom .current { color: var(--text-700); font-weight: 500; }

.page-title-area { margin-bottom: 20px; }
.page-title-area h1 { font-size: 22px; font-weight: 700; margin: 0 0 6px; color: var(--text-900); }
.page-title-area p { margin: 0; color: var(--text-500); font-size: 13px; }

/* type_box */
.type-box {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 14px 18px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  margin-bottom: 20px;
  box-shadow: var(--shadow-xs);
  flex-wrap: wrap;
}
.type-label { display: flex; align-items: center; gap: 8px; }
.type-label-text { font-weight: 600; font-size: 13px; color: var(--text-700); }
.type-select {
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--card);
  font-size: 13px;
  min-width: 240px;
}
.type-hint { color: var(--text-400); font-size: 11px; }
.type-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-700);
  cursor: pointer;
}

/* 布局 */
.edit-layout {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 20px;
  align-items: start;
}
.edit-main { display: flex; flex-direction: column; gap: 16px; }
.edit-sidebar { display: flex; flex-direction: column; gap: 16px; }

/* 选项卡 */
.wc-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 0;
  flex-wrap: wrap;
  background: var(--card);
  border-radius: var(--radius) var(--radius) 0 0;
  padding: 0 8px;
  border: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
}
.wc-tab {
  padding: 10px 16px;
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-500);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: color 0.15s ease, border-color 0.15s ease;
}
.wc-tab:hover { color: var(--text-700); }
.wc-tab.active { color: var(--brand-600); border-bottom-color: var(--brand-600); font-weight: 600; }
.wc-tab.hidden { display: none; }

.tab-pane {
  background: var(--card);
  border: 1px solid var(--border);
  border-top: none;
  border-radius: 0 0 var(--radius) var(--radius);
  padding: 20px;
}

/* 表单卡片 */
.form-card {
  background: var(--card);
  border-radius: var(--radius);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-xs);
  overflow: hidden;
}
.form-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--background-100);
  font-size: 13px;
  font-weight: 600;
  color: var(--text-700);
  border-bottom: 1px solid var(--border);
}
.form-card-icon { font-size: 14px; }
.form-card-body { padding: 16px; }

/* WC 同步面板 */
.wc-info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px dashed var(--border);
  font-size: 12px;
}
.wc-info-row:last-of-type { border-bottom: none; }
.wc-info-label { color: var(--text-500); }
.wc-info-value { color: var(--text-900); font-weight: 500; }
.wc-link { color: var(--brand-600); text-decoration: none; }
.wc-link:hover { text-decoration: underline; }
.wc-na { color: var(--text-400); }
.wc-data-preview {
  margin-top: 12px;
  padding: 10px;
  background: var(--background-100);
  border-radius: 6px;
}
.wc-data-title { font-size: 11px; color: var(--text-500); margin-bottom: 6px; }
.wc-data-row { display: flex; justify-content: space-between; font-size: 11px; padding: 2px 0; }
.wc-actions { margin-top: 12px; display: flex; flex-direction: column; gap: 8px; }
.sync-message { font-size: 12px; padding: 6px 10px; border-radius: 4px; }
.sync-success { background: var(--state-success-surface); color: var(--state-success); }
.sync-error { background: var(--state-error-surface); color: var(--state-error); }

/* 字段映射 */
.field-map-list { display: grid; grid-template-columns: 1fr; gap: 4px; font-size: 11px; }
.field-map-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  background: var(--background-100);
  border-radius: 4px;
}
.field-map-item code {
  background: var(--card);
  border: 1px solid var(--border);
  padding: 1px 6px;
  border-radius: 3px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  color: var(--brand-600);
}
.map-arrow { color: var(--text-400); }

/* 按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s ease;
  font-family: inherit;
}
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-primary { background: var(--brand-600); color: #fff; border-color: var(--brand-600); }
.btn-primary:hover:not(:disabled) { background: var(--brand-700); }
.btn-outline { background: transparent; color: var(--text-700); border-color: var(--border); }
.btn-outline:hover:not(:disabled) { background: var(--background-100); }
.btn-pull { background: #f0f5ff; color: #1d4ed8; border-color: #c7d7fe; }
.btn-stock-sync { background: #f0fdf4; color: #166534; border-color: #bbf7d0; }
.btn-block { width: 100%; }
.btn-lg { padding: 10px 20px; font-size: 14px; }

.tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}
.tag-green { background: var(--state-success-surface); color: var(--state-success); }
.tag-gray { background: var(--background-200); color: var(--text-500); }

/* 底部 */
.form-footer {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
</style>