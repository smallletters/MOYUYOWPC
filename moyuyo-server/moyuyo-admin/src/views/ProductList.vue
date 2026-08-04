<template>
  <div class="product-list-page">
    <!-- 页面标题区域 -->
    <div class="page-title-area">
      <h1>商品列表</h1>
      <p>管理商品信息，维护库存与价格，并支持与 WooCommerce 双向同步</p>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <div class="action-bar-left">
        <div class="tab-switcher">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            class="tab-switcher-item"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
            <span class="tab-count">{{ tab.count }}</span>
          </button>
        </div>
      </div>
      <div class="action-bar-right">
        <button class="btn btn-outline" :disabled="wooSyncing" @click="handleSyncFromWoo">
          <span class="btn-icon-text">↻</span>
          {{ wooSyncing ? '拉取中…' : '从 WC 拉取' }}
        </button>
        <button class="btn btn-outline" :disabled="wooSyncing" @click="handlePushAllToWoo">
          <span class="btn-icon-text">⇪</span>
          {{ wooSyncing ? '推送中…' : '全量推送 WC' }}
        </button>
        <button class="btn btn-outline" :disabled="wooSyncing" @click="handleSyncAllStocks">
          <span class="btn-icon-text">⟳</span>
          {{ wooSyncing ? '同步中…' : '全量拉取 WC 库存' }}
        </button>
        <button class="btn btn-primary" @click="handleAddProduct">
          <span class="btn-icon-text">+</span>
          新增商品
        </button>
      </div>
    </div>

    <!-- 筛选面板 -->
    <div class="query-panel">
      <div class="form-row">
        <div class="form-group">
          <label>关键词搜索</label>
          <input v-model="filters.search" type="text" placeholder="商品名称 / SKU" />
        </div>
        <div class="form-group">
          <label>商品状态</label>
          <select v-model="filters.status" class="select-wrapper">
            <option value="">全部状态</option>
            <option value="active">在售</option>
            <option value="inactive">已下架</option>
            <option value="draft">草稿</option>
            <option value="pending">待审核</option>
          </select>
        </div>
        <div class="form-group">
          <label>商品分类</label>
          <select v-model="filters.category" class="select-wrapper">
            <option value="">全部分类</option>
            <option v-for="cat in categories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>库存状态</label>
          <select v-model="filters.stock" class="select-wrapper">
            <option value="">全部库存</option>
            <option value="low">库存紧张</option>
            <option value="normal">库存正常</option>
            <option value="out">缺货</option>
          </select>
        </div>
        <div class="form-actions">
          <button class="btn btn-primary" @click="handleSearch">搜索</button>
          <button class="btn btn-outline" @click="handleReset">重置</button>
        </div>
      </div>
    </div>

    <!-- 批量操作栏 -->
    <div class="batch-bar" v-if="selectedIds.length > 0">
      <span>已选择 {{ selectedIds.length }} 项</span>
      <button class="btn btn-sm btn-outline" @click="handleBatchAction('shelf')">上架</button>
      <button class="btn btn-sm btn-outline" @click="handleBatchAction('unshelf')">下架</button>
      <button class="btn btn-sm btn-danger" @click="handleBatchAction('delete')">删除</button>
    </div>

    <!-- 数据表格 -->
    <div class="data-table-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th class="checkbox-cell"><input type="checkbox" v-model="selectAll" @change="toggleSelectAll" /></th>
            <th>商品</th>
            <th>SKU</th>
            <th>价格</th>
            <th>库存</th>
            <th>销量</th>
            <th>状态</th>
            <th>WC 同步</th>
            <th style="min-width: 200px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="product in filteredProducts" :key="product.id">
            <td class="checkbox-cell"><input type="checkbox" v-model="selectedIds" :value="product.id" /></td>
            <td>
              <div class="product-cell">
                <div class="thumb" :style="{ backgroundColor: product.color }">
                  <span v-if="!product.color" class="thumb-placeholder">📦</span>
                </div>
                <div>
                  <div class="product-name">{{ product.name }}</div>
                  <div v-if="product.categoryName" class="product-sku">{{ product.categoryName }}</div>
                </div>
              </div>
            </td>
            <td class="sku-cell">{{ product.sku }}</td>
            <td class="money">¥{{ product.price }}</td>
            <td>
              <span :class="getStockClass(product.stock)">{{ product.stock }}</span>
            </td>
            <td>{{ product.sales || 0 }}</td>
            <td>
              <span :class="product.statusClass === 'green' ? 'tag tag-green' : 'tag tag-gray'">
                {{ product.status }}
              </span>
            </td>
            <td>
              <div v-if="product.wooProductId">
                <span class="tag tag-green" style="margin-bottom:2px;">
                  <span class="status-dot green"></span> WC #{{ product.wooProductId }}
                </span>
                <div v-if="product.wooModified" class="wc-sync-time">
                  {{ formatWcTime(product.wooModified) }}
                </div>
              </div>
              <span v-else class="tag tag-gray">
                <span class="status-dot gray"></span> 未同步
              </span>
            </td>
            <td>
              <div class="cell-actions">
                <button class="btn btn-sm btn-outline" @click="handleEdit(product)">编辑</button>
                <button class="btn btn-sm btn-outline" @click="handleToggleStatus(product)">
                  {{ product.statusClass === 'green' ? '下架' : '上架' }}
                </button>
                <button class="btn btn-sm btn-outline" :disabled="pushingIds.includes(product.id)"
                  @click="handlePushOneToWoo(product)">
                  {{ pushingIds.includes(product.id) ? '推送中…' : '推 WC' }}
                </button>
                <button
                  v-if="product.wooProductId"
                  class="btn btn-sm btn-pull-woo"
                  :disabled="pullingStockIds.includes(product.id)"
                  @click="handlePullStock(product)"
                >
                  {{ pullingStockIds.includes(product.id) ? '…' : '拉取库存' }}
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="filteredProducts.length === 0">
            <td colspan="9">
              <div class="empty-state">
                <div class="empty-state-icon">📦</div>
                <div class="empty-state-text">暂无商品数据</div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div class="pagination" v-if="total > 0">
        <span class="pagination-info">共 {{ total }} 条 · 第 {{ currentPage }} / {{ totalPages }} 页</span>
        <div class="pagination-btns">
          <button class="pagination-btn" :disabled="currentPage <= 1" @click="currentPage--">‹ 上一页</button>
          <button class="pagination-btn" :disabled="currentPage >= totalPages" @click="currentPage++">下一页 ›</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProductList, toggleProductStatus, batchProductAction,
  syncProductFromWoo, pushProductToWoo, pushAllProductsToWoo,
  syncProductStock, syncAllStocksFromWoo, getCategoryList } from '../api/admin'
import { toArray } from '../utils/safeArray'

const router = useRouter()

const activeTab = ref('all')
const selectAll = ref(false)
const selectedIds = ref([])
const currentPage = ref(1)
const pageSize = 10
const loading = ref(false)
const total = ref(0)

const tabs = ref([
  { key: 'all', label: '全部', count: 0 },
  { key: 'active', label: '在售', count: 0 },
  { key: 'inactive', label: '已下架', count: 0 },
  { key: 'draft', label: '草稿', count: 0 },
  { key: 'pending', label: '待审核', count: 0 }
])

const filters = reactive({
  search: '',
  status: '',
  category: '',
  stock: ''
})

const productList = ref([])
const categories = ref([])  // 动态商品分类列表

// WooCommerce 同步相关状态：避免重复点击
const wooSyncing = ref(false)
const pushingIds = ref([])
const pullingStockIds = ref([])

// 获取商品列表
async function fetchProducts() {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize,
      keyword: filters.search || undefined
    }
    // 根据 activeTab 设置状态筛选
    if (activeTab.value !== 'all') {
      params.status = activeTab.value
    }
    // 传递分类筛选参数
    if (filters.category) {
      params.categoryId = filters.category
    }
    // 传递库存状态筛选参数
    if (filters.stock) {
      params.stockStatus = filters.stock
    }
    // 删除空值
    Object.keys(params).forEach(k => {
      if (!params[k]) delete params[k]
    })
    const res = await getProductList(params)
    if (res) {
      const list = toArray(res)
      // 字段映射：后端 ProductEntity 用 onSale(Boolean)，前端期望 status/statusClass
      // 这里将 onSale 转为 status 显示字段，避免 UI 显示空状态
      productList.value = list.map(p => {
        const isOnSale = p.onSale === true || p.onSale === 1 || p.onSale === 'true'
        return {
          ...p,
          status: isOnSale ? '在售' : '已下架',
          statusClass: isOnSale ? 'green' : 'gray'
        }
      })
      total.value = res.total || 0
      // 更新Tab标签计数
      if (res.tabCounts) {
        tabs.value = tabs.value.map(t => ({
          ...t,
          count: res.tabCounts[t.key] || 0
        }))
      }
    }
  } catch (err) {
    console.error('获取商品列表失败:', err)
    ElMessage.error('获取商品列表失败')
  } finally {
    loading.value = false
  }
}

// 直接使用 productList，无需额外 computed 包装
const filteredProducts = productList

const totalPages = computed(() => Math.ceil(total.value / pageSize) || 1)

// 根据库存数量返回样式类
function getStockClass(stock) {
  const n = Number(stock) || 0
  if (n === 0) return 'tag tag-red'
  if (n < 20) return 'tag tag-yellow'
  return 'tag tag-gray'
}

function toggleSelectAll() {
  if (selectAll.value) {
    selectedIds.value = filteredProducts.value.map(p => p.id)
  } else {
    selectedIds.value = []
  }
}

function handleSearch() {
  currentPage.value = 1
  fetchProducts()
}

function handleReset() {
  filters.search = ''
  filters.status = ''
  filters.category = ''
  filters.stock = ''
  currentPage.value = 1
  fetchProducts()
}

// 跳转到新增商品页
function handleAddProduct() {
  router.push('/products/add')
}

// 跳转到编辑商品页，携带商品 ID
function handleEdit(product) {
  router.push(`/products/edit/${product.id}`)
}

// 发送 API 请求切换商品状态（上架/下架）
async function handleToggleStatus(product) {
  try {
    await toggleProductStatus(product.id)
    ElMessage.success(`商品「${product.name}」状态已更新`)
    fetchProducts()
  } catch (err) {
    console.error('切换商品状态失败:', err)
    ElMessage.error('切换状态失败')
  }
}

// 发送 API 请求执行批量操作
async function handleBatchAction(action) {
  try {
    await batchProductAction({ action, ids: selectedIds.value })
    ElMessage.success(`批量${action === 'shelf' ? '上架' : action === 'unshelf' ? '下架' : '删除'}成功`)
    selectedIds.value = []
    fetchProducts()
  } catch (err) {
    console.error('批量操作失败:', err)
    ElMessage.error('批量操作失败')
  }
}

// 从 WooCommerce 拉取全量商品到本地（异步任务，立即返回）
async function handleSyncFromWoo() {
  if (wooSyncing.value) return
  wooSyncing.value = true
  try {
    await syncProductFromWoo()
    ElMessage.success('WooCommerce 拉取任务已启动，请稍后刷新列表')
    // 5 秒后自动刷新一次，让用户看到拉取结果
    setTimeout(() => fetchProducts(), 5000)
  } catch (err) {
    console.error('启动 WC 拉取任务失败:', err)
    ElMessage.error('启动 WC 拉取任务失败')
  } finally {
    wooSyncing.value = false
  }
}

// 批量推送本地未同步商品到 WooCommerce
async function handlePushAllToWoo() {
  if (wooSyncing.value) return
  wooSyncing.value = true
  try {
    const res = await pushAllProductsToWoo()
    const stat = res?.data || res
    ElMessage.success(`批量推送完成：成功 ${stat?.success || 0}，失败 ${stat?.failed || 0}，跳过 ${stat?.skipped || 0}`)
    fetchProducts()
  } catch (err) {
    console.error('批量推送 WC 失败:', err)
    ElMessage.error('批量推送 WC 失败')
  } finally {
    wooSyncing.value = false
  }
}

// 推送单个商品到 WooCommerce
async function handlePushOneToWoo(product) {
  if (pushingIds.value.includes(product.id)) return
  pushingIds.value.push(product.id)
  try {
    const res = await pushProductToWoo(product.id)
    const data = res?.data || res
    if (data?.wooProductId) {
      ElMessage.success(`「${product.name}」已推送到 WC，wooProductId=${data.wooProductId}`)
    } else {
      ElMessage.warning(`「${product.name}」推送失败：${data?.message || '未知原因'}`)
    }
    fetchProducts()
  } catch (err) {
    console.error('推送商品到 WC 失败:', err)
    ElMessage.error('推送商品到 WC 失败')
  } finally {
    pushingIds.value = pushingIds.value.filter(id => id !== product.id)
  }
}

// 全量拉取 WooCommerce 库存
async function handleSyncAllStocks() {
  if (wooSyncing.value) return
  wooSyncing.value = true
  try {
    const res = await syncAllStocksFromWoo()
    const data = res?.data || res
    ElMessage.success(`库存同步完成：共 ${data?.total || 0} 个关联商品，更新 ${data?.updated || 0} 个`)
    fetchProducts()
  } catch (err) {
    console.error('全量库存同步失败:', err)
    ElMessage.error('全量库存同步失败')
  } finally {
    wooSyncing.value = false
  }
}

// 拉取单个商品的 WooCommerce 库存
async function handlePullStock(product) {
  if (pullingStockIds.value.includes(product.id)) return
  pullingStockIds.value.push(product.id)
  try {
    const res = await syncProductStock(product.id)
    const data = res?.data || res
    if (data?.stock !== undefined) {
      ElMessage.success(`「${product.name}」库存已同步：${data.stock} (${data.stockStatus})`)
    } else {
      ElMessage.warning(`「${product.name}」库存同步失败：${data?.message || '未知原因'}`)
    }
    fetchProducts()
  } catch (err) {
    console.error('拉取商品库存失败:', err)
    ElMessage.error('拉取商品库存失败')
  } finally {
    pullingStockIds.value = pullingStockIds.value.filter(id => id !== product.id)
  }
}

// 格式化 WC 同步时间
function formatWcTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const diffMs = now - d
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚同步'
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时前`
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// 获取商品分类列表（动态填充筛选下拉）
async function fetchCategories() {
  try {
    const list = await getCategoryList()
    categories.value = list || []
  } catch (e) {
    console.error('获取分类列表失败:', e)
  }
}

// 切换 tab 时重新加载数据
watch(activeTab, () => {
  currentPage.value = 1
  fetchProducts()
})

// 监听页码变化，重新加载数据
watch(currentPage, () => {
  fetchProducts()
})

onMounted(() => {
  fetchProducts()
  fetchCategories()
})
</script>

<style scoped>
/* 页面专属扩展：保留设计系统中的卡片、按钮、标签等基础类，仅补少量微调 */

/* 顶部操作栏 */
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.action-bar-left {
  flex: 1;
  min-width: 0;
}

.action-bar-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.btn-icon-text {
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
}

/* Tab 切换器中嵌入计数 */
.tab-switcher-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 18px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 999px;
  background: var(--background-200);
  color: var(--text-500);
}

.tab-switcher-item.active .tab-count {
  background: var(--brand-50);
  color: var(--brand-600);
}

/* SKU 单元格 */
.sku-cell {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-500);
}

/* 表内空状态 */
.data-table tbody tr td .empty-state {
  padding: 40px 20px;
}

/* WC 同步时间 */
.wc-sync-time {
  font-size: 11px;
  color: var(--text-400);
  margin-top: 2px;
}

/* 拉取库存按钮 */
.btn-pull-woo {
  color: #1e8449;
  border-color: #1e8449;
}
.btn-pull-woo:hover:not(:disabled) {
  background: #eafaf1;
  border-color: #176b3a;
  color: #176b3a;
}
</style>
