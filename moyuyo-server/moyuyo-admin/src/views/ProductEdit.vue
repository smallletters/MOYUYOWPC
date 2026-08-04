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
      <p>{{ isNew ? '创建新商品，可后续同步到 WooCommerce' : '编辑商品信息，字段对齐 WooCommerce 商品编辑页' }}</p>
    </div>

    <div class="edit-layout">
      <!-- 左侧：商品表单 -->
      <div class="edit-main">
        <!-- 基础信息 -->
        <div class="form-card">
          <div class="form-card-header">
            <span class="form-card-icon">📋</span>
            <span>基础信息</span>
          </div>
          <div class="form-card-body">
            <div class="form-group">
              <label>商品名称 <span class="required">*</span></label>
              <input v-model="form.name" placeholder="输入商品名称 (WooCommerce: name)" />
            </div>

            <div class="form-row">
              <div class="form-group">
                <label>商品分类 <span class="required">*</span></label>
                <select v-model="form.categoryId">
                  <option value="">请选择分类</option>
                  <option v-for="cat in categories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
                </select>
                <span class="field-hint">WooCommerce: categories[0].id</span>
              </div>
              <div class="form-group">
                <label>SKU 编码</label>
                <input v-model="form.spuCode" placeholder="唯一 SKU (WooCommerce: sku)" />
              </div>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label>产品类型 (product_type)</label>
                <select v-model="form.productType">
                  <option value="simple">Simple — 简单商品</option>
                  <option value="variable">Variable — 可变商品（含规格）</option>
                  <option value="grouped">Grouped — 组合商品</option>
                  <option value="external">External — 外部/附属商品</option>
                </select>
                <span class="field-hint">WooCommerce: type</span>
              </div>
              <div class="form-group">
                <label>商品标签</label>
                <input v-model="form.tags" placeholder="多个标签用逗号分隔" />
                <span class="field-hint">WooCommerce: tags[].name，用逗号分隔</span>
              </div>
            </div>

            <div class="form-group">
              <label>商品描述 (description)</label>
              <textarea v-model="form.detail" rows="4" placeholder="商品详细描述 (WooCommerce: description)"></textarea>
            </div>

            <div class="form-group">
              <label>简短描述 (short_description)</label>
              <textarea v-model="form.shortDetail" rows="2" placeholder="商品摘要，显示在列表/搜索结果中 (WooCommerce: short_description)"></textarea>
            </div>
          </div>
        </div>

        <!-- 定价 -->
        <div class="form-card">
          <div class="form-card-header">
            <span class="form-card-icon">💰</span>
            <span>定价</span>
          </div>
          <div class="form-card-body">
            <div class="form-row">
              <div class="form-group">
                <label>原价 (regular_price) <span class="required">*</span></label>
                <input v-model="form.originalPrice" type="number" step="0.01" min="0" placeholder="0.00" />
              </div>
              <div class="form-group">
                <label>售价 (sale_price)</label>
                <input v-model="form.price" type="number" step="0.01" min="0" placeholder="0.00" />
                <span class="field-hint">留空则使用原价出售</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 库存 -->
        <div class="form-card">
          <div class="form-card-header">
            <span class="form-card-icon">📦</span>
            <span>库存管理</span>
          </div>
          <div class="form-card-body">
            <div class="form-row">
              <div class="form-group">
                <label>库存数量 (stock_quantity)</label>
                <input v-model="form.stock" type="number" min="0" placeholder="0" />
              </div>
              <div class="form-group">
                <label>已售数量</label>
                <input v-model="form.sales" type="number" min="0" placeholder="0" disabled />
                <span class="field-hint" style="color:var(--text-400)">只读，系统自动累计</span>
              </div>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label>启用库存管理 (manage_stock)</label>
                <div class="status-toggle" style="margin-top:6px">
                  <label class="toggle-switch">
                    <input type="checkbox" v-model="form.manageStock" />
                    <span class="toggle-slider"></span>
                  </label>
                  <span class="status-text">{{ form.manageStock ? '已启用' : '未启用' }}</span>
                </div>
              </div>
              <div class="form-group">
                <label>库存状态 (stock_status)</label>
                <select v-model="form.stockStatus">
                  <option value="IN_STOCK">有货 (in_stock)</option>
                  <option value="OUT_OF_STOCK">缺货 (out_of_stock)</option>
                  <option value="ON_BACKORDER">预售 (on_backorder)</option>
                </select>
              </div>
            </div>
          </div>
        </div>

        <!-- 物流 -->
        <div class="form-card">
          <div class="form-card-header">
            <span class="form-card-icon">🚚</span>
            <span>物流信息</span>
          </div>
          <div class="form-card-body">
            <div class="form-group">
              <label>商品重量 (weight)</label>
              <input v-model="form.weight" type="number" step="0.001" min="0" placeholder="单位为 kg" />
              <span class="field-hint">WooCommerce: weight，单位公斤 (kg)</span>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label>长度 (length)</label>
                <input v-model="dimensions.length" type="number" step="0.01" min="0" placeholder="cm" />
              </div>
              <div class="form-group">
                <label>宽度 (width)</label>
                <input v-model="dimensions.width" type="number" step="0.01" min="0" placeholder="cm" />
              </div>
              <div class="form-group">
                <label>高度 (height)</label>
                <input v-model="dimensions.height" type="number" step="0.01" min="0" placeholder="cm" />
              </div>
            </div>
            <span class="field-hint">WooCommerce: dimensions {length, width, height}，单位厘米</span>
          </div>
        </div>

        <!-- 图片 -->
        <div class="form-card">
          <div class="form-card-header">
            <span class="form-card-icon">🖼️</span>
            <span>商品图片</span>
          </div>
          <div class="form-card-body">
            <div class="form-group">
              <label>主图 URL (images[0].src)</label>
              <input v-model="form.mainImage" placeholder="https://cdn.moyuyo.com/product/xxx.jpg" />
            </div>
            <div class="image-preview" v-if="form.mainImage">
              <img :src="form.mainImage" alt="商品主图预览" @error="onImageError" />
            </div>
          </div>
        </div>

        <!-- 状态 -->
        <div class="form-card">
          <div class="form-card-header">
            <span class="form-card-icon">📌</span>
            <span>发布状态</span>
          </div>
          <div class="form-card-body">
            <div class="form-group">
              <label>商品状态 (status)</label>
              <div class="status-toggle">
                <label class="toggle-switch">
                  <input type="checkbox" v-model="form.onSale" />
                  <span class="toggle-slider"></span>
                </label>
                <span class="status-text">{{ form.onSale ? '上架 — publish' : '下架 — draft' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：WooCommerce 同步面板 -->
      <div class="edit-sidebar">
        <!-- WC 同步状态卡片 -->
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
                <template v-if="form.wooModified">
                  {{ formatTime(form.wooModified) }}
                </template>
                <span v-else class="wc-na">从未同步</span>
              </span>
            </div>

            <!-- WC 商品简要数据（拉取后显示） -->
            <div v-if="pullResult" class="wc-data-preview">
              <div class="wc-data-title">上次拉取数据</div>
              <div class="wc-data-row"><span>名称</span><span>{{ pullResult.name }}</span></div>
              <div class="wc-data-row"><span>原价</span><span>${{ pullResult.regularPrice || '-' }}</span></div>
              <div class="wc-data-row"><span>售价</span><span>${{ pullResult.salePrice || '-' }}</span></div>
              <div class="wc-data-row"><span>库存</span><span>{{ pullResult.stockQuantity ?? '-' }}</span></div>
              <div class="wc-data-row"><span>SKU</span><span>{{ pullResult.sku || '-' }}</span></div>
              <div class="wc-data-row"><span>类型</span><span>{{ pullResult.type || '-' }}</span></div>
              <div class="wc-data-row"><span>重量</span><span>{{ pullResult.weight || '-' }} kg</span></div>
              <div class="wc-data-row"><span>标签</span><span>{{ pullResult.tags || '-' }}</span></div>
              <div class="wc-data-row"><span>简短描述</span><span class="wc-data-ellipsis">{{ pullResult.shortDescription || '-' }}</span></div>
              <div class="wc-data-row" v-if="pullResult.dimensions">
                <span>尺寸</span><span>{{ pullResult.dimensions.length }} × {{ pullResult.dimensions.width }} × {{ pullResult.dimensions.height }} cm</span>
              </div>
            </div>

            <div class="wc-actions">
              <button
                class="btn btn-primary btn-block"
                :disabled="syncing"
                @click="handlePushToWoo"
              >
                {{ syncing ? '同步中...' : (form.wooProductId ? '推送到 WooCommerce' : '首次同步到 WC') }}
              </button>

              <button
                v-if="form.wooProductId"
                class="btn btn-pull btn-block"
                :disabled="syncing"
                @click="handlePullFromWoo"
              >
                {{ syncing ? '拉取中...' : '从 WooCommerce 拉取更新' }}
              </button>

              <button
                v-if="form.wooProductId"
                class="btn btn-stock-sync btn-block"
                :disabled="syncing"
                @click="handleSyncStock"
              >
                {{ syncing ? '同步中...' : '仅拉取库存' }}
              </button>

              <div v-if="syncMessage" :class="'sync-message sync-' + syncStatus">
                {{ syncMessage }}
              </div>
            </div>
          </div>
        </div>

        <!-- 字段映射速查表 -->
        <div class="form-card">
          <div class="form-card-header">
            <span class="form-card-icon">📐</span>
            <span>WooCommerce 字段映射</span>
          </div>
          <div class="form-card-body">
            <div class="field-map-list">
              <div class="field-map-item"><code>name</code><span class="map-arrow">←</span><span>商品名称</span></div>
              <div class="field-map-item"><code>description</code><span class="map-arrow">←</span><span>商品描述</span></div>
              <div class="field-map-item"><code>short_description</code><span class="map-arrow">←</span><span>简短描述</span></div>
              <div class="field-map-item"><code>sku</code><span class="map-arrow">←</span><span>SKU 编码</span></div>
              <div class="field-map-item"><code>type</code><span class="map-arrow">←</span><span>产品类型</span></div>
              <div class="field-map-item"><code>regular_price</code><span class="map-arrow">←</span><span>原价</span></div>
              <div class="field-map-item"><code>sale_price</code><span class="map-arrow">←</span><span>售价</span></div>
              <div class="field-map-item"><code>stock_quantity</code><span class="map-arrow">←</span><span>库存数量</span></div>
              <div class="field-map-item"><code>manage_stock</code><span class="map-arrow">←</span><span>库存管理</span></div>
              <div class="field-map-item"><code>stock_status</code><span class="map-arrow">←</span><span>库存状态</span></div>
              <div class="field-map-item"><code>weight</code><span class="map-arrow">←</span><span>商品重量</span></div>
              <div class="field-map-item"><code>dimensions</code><span class="map-arrow">←</span><span>长×宽×高(cm)</span></div>
              <div class="field-map-item"><code>images[0].src</code><span class="map-arrow">←</span><span>主图</span></div>
              <div class="field-map-item"><code>categories[0].id</code><span class="map-arrow">←</span><span>分类</span></div>
              <div class="field-map-item"><code>tags[].name</code><span class="map-arrow">←</span><span>标签</span></div>
              <div class="field-map-item"><code>status</code><span class="map-arrow">←</span><span>发布状态</span></div>
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

const router = useRouter()
const route = useRoute()

const saving = ref(false)
const syncing = ref(false)
const syncMessage = ref('')
const syncStatus = ref('')
const categories = ref([])
const pullResult = ref(null)  // 上次从 WC 拉取的数据摘要

const form = reactive({
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
  onSale: true,
  wooProductId: null,
  wooModified: null,
  brandId: null,
  brandIpId: null,
  attributes: null
})

// 尺寸字段（从 attributes JSON 中解析/序列化）
const dimensions = reactive({
  length: '',
  width: '',
  height: ''
})

const productId = computed(() => route.params.id)
const isNew = computed(() => !productId.value || productId.value === 'new')

const wooProductUrl = computed(() => {
  if (!form.wooProductId) return '#'
  return `https://your-woocommerce-store.com/wp-admin/post.php?post=${form.wooProductId}&action=edit`
})

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
      Object.assign(form, {
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
        onSale: res.onSale !== undefined ? res.onSale : true,
        wooProductId: res.wooProductId || null,
        wooModified: res.wooModified || null,
        brandId: res.brandId || null,
        brandIpId: res.brandIpId || null,
        attributes: res.attributes || null
      })
      // 解析 dimensions 从 attributes JSON
      parseDimensions(res.attributes)
    }
  } catch (err) {
    console.error('获取商品详情失败:', err)
    ElMessage.error('获取商品详情失败')
  }
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
      mainImage: form.mainImage || null,
      onSale: form.onSale,
      attributes: serializeAttributes(),
      // 兼容 snake_case 后端字段
      spu_code: form.spuCode || null,
      main_image: form.mainImage || null,
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

// 推送到 WooCommerce
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

// 从 WooCommerce 拉取并覆盖本地
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
      // 保存拉取到的 WC 数据摘要供右侧面板展示
      pullResult.value = {
        name: res.name,
        regularPrice: res.regularPrice || res.regular_price,
        salePrice: res.salePrice || res.sale_price,
        stockQuantity: res.stockQuantity || res.stock_quantity,
        sku: res.sku,
        type: res.type,
        weight: res.weight,
        tags: res.tags,
        shortDescription: res.shortDescription || res.short_description,
        dimensions: res.dimensions
      }
      // 刷新本地表单数据
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

// 仅拉取 WooCommerce 库存（不影响其他字段）
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
      // 刷新本地表单
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

// 从 attributes JSON 解析尺寸
function parseDimensions(attrsJson) {
  dimensions.length = ''
  dimensions.width = ''
  dimensions.height = ''
  if (!attrsJson) return
  try {
    const attrs = typeof attrsJson === 'string' ? JSON.parse(attrsJson) : attrsJson
    if (attrs && attrs.dimensions) {
      const d = attrs.dimensions
      dimensions.length = d.length != null ? String(d.length) : ''
      dimensions.width = d.width != null ? String(d.width) : ''
      dimensions.height = d.height != null ? String(d.height) : ''
    }
  } catch (e) { /* ignore parse errors */ }
}

// 将尺寸序列化到 attributes JSON
function serializeAttributes() {
  const hasDimensions = dimensions.length || dimensions.width || dimensions.height
  if (!hasDimensions && !form.attributes) return form.attributes
  let attrs = {}
  try {
    if (form.attributes) {
      attrs = typeof form.attributes === 'string' ? JSON.parse(form.attributes) : form.attributes
    }
  } catch (e) { attrs = {} }
  if (hasDimensions) {
    attrs.dimensions = {
      length: dimensions.length || '0',
      width: dimensions.width || '0',
      height: dimensions.height || '0'
    }
  }
  return JSON.stringify(attrs)
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
  max-width: 1100px;
  padding: 28px 28px 40px;
}

/* 布局 */
.edit-layout {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 24px;
  align-items: start;
}

.edit-main {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.edit-sidebar {
  display: flex;
  flex-direction: column;
  gap: 20px;
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
  padding: 14px 20px;
  border-bottom: 1px solid var(--border);
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
  background: var(--background-50);
}

.form-card-icon { font-size: 16px; }

.form-card-body { padding: 20px; }

.form-group { margin-bottom: 18px; }
.form-group:last-child { margin-bottom: 0; }

.form-row {
  display: flex;
  gap: 16px;
  margin-bottom: 18px;
}
.form-row .form-group { flex: 1; margin-bottom: 0; }

label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-700);
  margin-bottom: 6px;
}

.required { color: var(--state-error); }

.field-hint {
  display: block;
  font-size: 11px;
  color: var(--text-400);
  margin-top: 4px;
}

input, select, textarea {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--background);
  color: var(--foreground);
  font-size: 14px;
  outline: none;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
  font-family: var(--font-sans);
  box-sizing: border-box;
}

input:focus, select:focus, textarea:focus {
  border-color: var(--ring);
  box-shadow: 0 0 0 1px var(--ring);
}

textarea { resize: vertical; line-height: 1.5; }

input:disabled {
  background: var(--background-100);
  color: var(--text-400);
  cursor: not-allowed;
}

/* 图片预览 */
.image-preview {
  margin-top: 12px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  border: 1px solid var(--border);
  max-width: 200px;
}
.image-preview img { width: 100%; height: auto; display: block; }

/* 状态开关 */
.status-toggle {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-text {
  font-size: 13px;
  color: var(--text-600);
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  cursor: pointer;
  flex-shrink: 0;
}
.toggle-switch input { opacity: 0; width: 0; height: 0; padding: 0; border: none; }
.toggle-slider {
  position: absolute;
  inset: 0;
  background: var(--background-200);
  border-radius: 12px;
  transition: all 0.2s ease;
}
.toggle-slider::before {
  content: '';
  position: absolute;
  width: 20px; height: 20px;
  border-radius: 50%;
  top: 2px; left: 2px;
  background: #fff;
  transition: all 0.2s ease;
  box-shadow: 0 1px 3px rgba(0,0,0,0.15);
}
.toggle-switch input:checked + .toggle-slider { background: var(--state-success); }
.toggle-switch input:checked + .toggle-slider::before { transform: translateX(20px); }

/* WC 信息行 */
.wc-info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--background-200);
}
.wc-info-row:last-of-type { border-bottom: none; }

.wc-info-label { font-size: 12px; color: var(--text-500); font-weight: 500; }
.wc-info-value { font-size: 13px; color: var(--text-700); font-weight: 500; }

.wc-link { color: var(--primary); text-decoration: none; font-family: 'SF Mono', 'Menlo', monospace; }
.wc-link:hover { text-decoration: underline; }

.wc-na { color: var(--text-400); font-style: italic; font-weight: 400; }

/* WC 拉取数据预览 */
.wc-data-preview {
  margin-top: 12px;
  padding: 12px;
  background: var(--background-50);
  border-radius: var(--radius-sm);
  border: 1px solid var(--background-200);
}
.wc-data-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-600);
  margin-bottom: 8px;
}
.wc-data-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-500);
  padding: 3px 0;
}
.wc-data-row span:last-child {
  color: var(--text-700);
  font-weight: 500;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wc-data-ellipsis {
  display: inline-block;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wc-actions {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sync-message {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  line-height: 1.4;
  margin-top: 4px;
}
.sync-success { background: var(--state-success-surface); color: var(--state-success); }
.sync-error { background: #fef2f2; color: var(--state-error); }

/* 字段映射 */
.field-map-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.field-map-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 3px 0;
}
.field-map-item code {
  font-size: 11px;
  background: var(--background-100);
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--brand-600);
  font-family: 'SF Mono', 'Menlo', monospace;
  min-width: 100px;
  text-align: right;
}
.map-arrow { color: var(--text-300); font-size: 12px; }

/* 底部 */
.form-footer {
  margin-top: 28px;
  display: flex;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}

/* 按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 20px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid transparent;
  font-family: var(--font-sans);
  text-decoration: none;
}
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-primary { background: var(--primary); color: #fff; }
.btn-primary:hover:not(:disabled) { background: var(--brand-600); }

.btn-outline { background: var(--card); color: var(--text-600); border-color: var(--border); }
.btn-outline:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); background: var(--brand-50); }

.btn-pull {
  background: #1e8449;
  color: #fff;
  border-color: #1e8449;
}
.btn-pull:hover:not(:disabled) {
  background: #176b3a;
  border-color: #176b3a;
}

.btn-stock-sync {
  background: #fff;
  color: #1e8449;
  border-color: #1e8449;
}
.btn-stock-sync:hover:not(:disabled) {
  background: #eafaf1;
  border-color: #176b3a;
  color: #176b3a;
}

.btn-lg { padding: 12px 28px; font-size: 15px; }
.btn-block { width: 100%; }

/* 响应式 */
@media (max-width: 900px) {
  .edit-layout { grid-template-columns: 1fr; }
}
</style>
