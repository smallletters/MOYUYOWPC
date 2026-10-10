<template>
  <div class="page-wrapper">
    <!-- 页面标题 + 顶部时间范围选择器 -->
    <div class="page-header">
      <div>
        <h2>商品分析</h2>
        <p class="page-subtitle">商品表现 · 流转 · 评价 · 库存健康度综合分析</p>
      </div>
      <!-- 时间范围选择器：快捷范围 + 自定义日期区间 -->
      <div class="time-range-picker">
        <el-select v-model="timeRange" style="width: 140px" @change="handleTimeChange">
          <el-option v-for="opt in timeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-date-picker
          v-if="timeRange === 'custom'"
          v-model="customRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 260px"
          @change="handleTimeChange"
        />
      </div>
    </div>

    <!-- KPI 卡片（接口数据，保留原功能） -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">总商品数</div>
            <div class="kpi-value">{{ kpiData.totalProducts }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">在售商品</div>
            <div class="kpi-value" style="color:var(--brand-500)">{{ kpiData.activeProducts }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">总浏览量</div>
            <div class="kpi-value" style="color:var(--state-warning)">{{ kpiData.totalViews }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">总销量</div>
            <div class="kpi-value" style="color:var(--state-success)">{{ kpiData.totalSales }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索栏（保留原功能） -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="商品名称">
          <el-input v-model="filters.keyword" placeholder="请输入关键词" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ============ 按设计稿补齐的分析区块 ============ -->
    <div class="analysis-grid">

      <!-- Top 10 商品排行（基于接口数据按销量排序计算，跨两列） -->
      <el-card shadow="never" class="analysis-card span-2">
        <template #header>
          <div class="card-header">
            <span class="card-title">Top 10 商品排行</span>
            <span class="card-subtitle">按销量排序 · 水平条形图</span>
          </div>
        </template>
        <div v-if="top10Products.length" class="rank-list">
          <div v-for="item in top10Products" :key="item.id" class="rank-item">
            <span class="rank-badge" :class="rankClass(item.rank)">{{ item.rank }}</span>
            <div class="rank-info">
              <div class="rank-name">{{ item.productName }}</div>
              <div class="rank-stats">
                <span class="rank-stat-label">销量 <span class="rank-stat-value sales">{{ item.sales.toLocaleString() }}</span></span>
                <span class="rank-stat-label">销售额 <span class="rank-stat-value revenue">¥{{ item.revenue.toLocaleString() }}</span></span>
              </div>
            </div>
            <!-- 水平条形图：宽度为该商品销量占 Top1 销量的比例 -->
            <div class="mini-bar-track" :title="'销量占比 ' + salesPercent(item.sales)">
              <div class="mini-bar-fill" :style="{ width: salesPercent(item.sales) }"></div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无排行数据" :image-size="80" />
      </el-card>

      <!-- 新品表现追踪（KPI 卡片 + 列表） -->
      <el-card shadow="never" class="analysis-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">新品表现追踪</span>
            <div class="period-tabs">
              <button
                v-for="p in newProductPeriods"
                :key="p"
                class="period-tab"
                :class="{ active: newProductPeriod === p }"
                @click="newProductPeriod = p"
              >{{ p }}</button>
            </div>
          </div>
        </template>
        <div class="metric-grid">
          <div v-for="m in newProductTracking.metrics" :key="m.label" class="metric-card">
            <div class="metric-label">{{ m.label }}</div>
            <div class="metric-value">{{ m.value }}</div>
            <div class="metric-change" :class="m.up ? 'up' : 'down'">{{ m.change }}</div>
            <!-- CSS 趋势柱状图：归一化到 4%~100%，避免原始销量数值溢出 -->
            <div class="trend-line">
              <div v-for="(h, i) in m.trend" :key="i" class="trend-bar" :style="{ height: trendBarHeight(h, m.trend) }"></div>
            </div>
          </div>
        </div>
        <div class="new-product-list">
          <div v-for="item in newProductTracking.list" :key="item.name" class="new-product-item">
            <div class="new-product-info">
              <div class="new-product-name">{{ item.name }}</div>
              <div class="new-product-meta">上架 {{ item.launchAt }} · 销量 {{ item.sales }}</div>
            </div>
            <span class="new-product-rate">转化 {{ item.conversion }}</span>
          </div>
        </div>
      </el-card>

      <!-- 滞销商品预警（低销量商品列表，红色标签） -->
      <el-card shadow="never" class="analysis-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">滞销商品预警</span>
            <span class="warning-badge">{{ slowMovingProducts.length }}</span>
          </div>
        </template>
        <div class="slow-moving-list">
          <div v-for="item in slowMovingProducts" :key="item.name" class="slow-moving-item">
            <div class="slow-moving-info">
              <div class="slow-moving-name">{{ item.name }}</div>
              <div class="slow-moving-meta">
                <span>库存: {{ item.stock }}</span>
                <span>近30天售出: {{ item.sales30 }}</span>
              </div>
            </div>
            <span class="slow-moving-days" :class="item.level">{{ formatTurnoverDays(item.days) }}</span>
          </div>
        </div>
      </el-card>

      <!-- 流转率概览（KPI + 分类排行） -->
      <el-card shadow="never" class="analysis-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">流转率概览</span>
          </div>
        </template>
        <div class="turnover-metrics">
          <div v-for="m in turnoverOverview.metrics" :key="m.label" class="turnover-metric">
            <div class="turnover-label">{{ m.label }}</div>
            <div class="turnover-value">{{ m.value }}</div>
          </div>
        </div>
        <div class="category-list">
          <div v-for="(c, i) in turnoverOverview.categories" :key="c.name" class="rank-item">
            <span class="rank-badge" :class="rankClass(i + 1)">{{ i + 1 }}</span>
            <div class="rank-info">
              <div class="rank-name">{{ c.name }}</div>
              <div class="rank-stats">
                <span class="rank-stat-label">浏览 <span class="rank-stat-value sales">{{ c.views }}</span></span>
                <span class="rank-stat-label">成交 <span class="rank-stat-value revenue">{{ c.deals }}</span></span>
              </div>
            </div>
            <span class="rank-profit-tag" :class="c.level">{{ c.rate }}</span>
          </div>
        </div>
      </el-card>

      <!-- 热门搜索词 Top 10（水平条形图） -->
      <el-card shadow="never" class="analysis-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">热门搜索词 Top 10</span>
          </div>
        </template>
        <div class="keyword-list">
          <div v-for="kw in hotSearchKeywords" :key="kw.keyword" class="keyword-item">
            <div class="keyword-header">
              <span class="keyword-name">{{ kw.keyword }}</span>
              <div class="keyword-stats">
                <span class="keyword-count">{{ kw.count }}次</span>
                <span class="keyword-rate">加购率 {{ kw.cartRate }}</span>
              </div>
            </div>
            <div class="bar-track">
              <div class="bar-fill" :style="{ width: kw.percent + '%' }"></div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 评价分析概览（KPI + 评分分布条形图） -->
      <el-card shadow="never" class="analysis-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">评价分析概览</span>
          </div>
        </template>
        <div class="review-summary">
          <div v-for="r in reviewItems" :key="r.label" class="review-row">
            <div class="review-icon" :class="r.color">{{ r.icon }}</div>
            <div class="review-info">
              <div class="review-label">{{ r.label }}</div>
              <div class="review-desc">{{ r.desc }}</div>
            </div>
            <span class="review-value" :style="{ color: stateColor(r.color) }">{{ r.value }}</span>
          </div>
        </div>
        <!-- 评分分布条形图 -->
        <div class="rating-distribution">
          <div class="rating-title">评分分布</div>
          <div v-for="d in reviewAnalysis.distribution || []" :key="d.stars" class="rating-row">
            <span class="rating-stars">{{ d.stars }}星</span>
            <div class="bar-track">
              <div class="bar-fill rating" :style="{ width: d.percent + '%' }"></div>
            </div>
            <span class="rating-percent">{{ d.percent }}%</span>
          </div>
        </div>
      </el-card>

      <!-- 高频评价关键词（标签云） -->
      <el-card shadow="never" class="analysis-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">高频评价关键词</span>
          </div>
        </template>
        <div class="keyword-cloud">
          <el-tag
            v-for="k in reviewKeywords"
            :key="k.keyword"
            :type="k.type || 'info'"
            effect="light"
            class="cloud-tag"
          >{{ k.keyword }} <span v-if="k.count" style="opacity:.6;font-size:11px;margin-left:2px">({{ k.count }})</span></el-tag>
        </div>
      </el-card>

      <!-- 库存健康度概览（KPI + 健康度条形图） -->
      <el-card shadow="never" class="analysis-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">库存健康度概览</span>
          </div>
        </template>
        <div class="inventory-kpis">
          <div class="inventory-kpi">
            <div class="inventory-kpi-label">总库存</div>
            <div class="inventory-kpi-value">{{ inventoryHealth.totalStock }}</div>
          </div>
          <div class="inventory-kpi">
            <div class="inventory-kpi-label">健康占比</div>
            <div class="inventory-kpi-value" style="color: var(--state-success)">{{ inventoryHealth.healthRate }}</div>
          </div>
        </div>
        <div class="inventory-health">
          <div v-for="item in inventoryHealth.items || []" :key="item.label" class="inventory-health-row">
            <span class="inventory-health-label" :style="{ color: stateColor(item.level) }">{{ item.label }}</span>
            <div class="inventory-health-track">
              <div class="inventory-health-fill" :class="item.level" :style="{ width: item.percent + '%' }">{{ item.count }}</div>
            </div>
            <span class="inventory-health-percent" :style="{ color: stateColor(item.level) }">{{ item.percent }}%</span>
          </div>
        </div>
      </el-card>

      <!-- 库存周转天数排行（表格） -->
      <el-card shadow="never" class="analysis-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">库存周转天数排行</span>
          </div>
        </template>
        <el-table :data="inventoryTurnoverRanking" size="small">
          <el-table-column label="排名" width="60">
            <template #default="{ $index }">
              <span class="rank-badge" :class="rankClass($index + 1)">{{ $index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="商品名称" min-width="150" />
          <el-table-column prop="stock" label="库存" width="80" />
          <el-table-column label="周转天数" width="100">
            <template #default="{ row }">
              <span :style="{ color: stateColor(row.level) }">{{ formatTurnoverDays(row.days) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 商品浏览/收藏/销量明细表格（保留原功能） -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">商品浏览 / 收藏 / 销量明细</span>
          <span class="card-subtitle">共 {{ total }} 条</span>
        </div>
      </template>
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="productName" label="商品名称" min-width="160" />
        <el-table-column prop="views" label="浏览量" width="100" sortable />
        <el-table-column prop="favorites" label="收藏量" width="100" sortable />
        <el-table-column prop="cartAdds" label="加购量" width="100" sortable />
        <el-table-column prop="sales" label="销量" width="100" sortable />
        <el-table-column prop="revenue" label="销售额" width="120" sortable>
          <template #default="{ row }">¥{{ row.revenue.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:16px 0 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getProductAnalysisKpi,
  getProductAnalysisList,
  getNewProductTracking,
  getSlowMovingProducts,
  getTurnoverOverview,
  getHotSearchKeywords,
  getReviewAnalysis,
  getReviewKeywords,
  getInventoryHealth,
  getInventoryTurnoverRanking
} from '../api/admin'

// ===== 现有分页与筛选状态（保留） =====
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  keyword: ''
})

// ===== KPI数据，从API获取（保留） =====
const kpiData = reactive({
  totalProducts: 0,
  activeProducts: 0,
  totalViews: 0,
  totalSales: 0
})

const tableData = ref([])
// 过滤后的完整列表，供 Top 10 排行计算与分页使用
const allFiltered = ref([])

// ===== 顶部时间范围选择器 =====
const timeRange = ref('7d')
const customRange = ref([])
const timeOptions = [
  { label: '今日', value: 'today' },
  { label: '近7天', value: '7d' },
  { label: '近30天', value: '30d' },
  { label: '近90天', value: '90d' },
  { label: '近半年', value: 'half-year' },
  { label: '自定义', value: 'custom' }
]

// 时间范围变化：刷新数据 + 传递时间参数到 KPI / 列表接口
function handleTimeChange() {
  // 串行：先拉基础列表（始终走 list 接口，保证浏览/收藏/加购不变），
  // 再按时间范围拉 report，覆盖区间销量与收入。
  loadData().then(loadTimeRangeData)
}

// ===== 各分析区块响应式数据（替换原硬编码示例） =====
const newProductTracking = ref({ metrics: [], list: [] })
const slowMovingProducts = ref([])
const turnoverOverview = ref({ metrics: [], categories: [] })
const hotSearchKeywords = ref([])
const reviewAnalysis = ref({ positive: '0%', neutral: '0%', negative: '0%', distribution: [] })
const reviewKeywords = ref([])
const inventoryHealth = ref({ totalStock: 0, healthRate: '0%', items: [] })
const inventoryTurnoverRanking = ref([])

// 把后端 reviewAnalysis 转换为模板用的 items 列表
const reviewItems = computed(() => [
  { label: '好评', desc: '评分 ≥ 4 星', value: reviewAnalysis.value.positive || '0%', color: 'success', icon: '😊' },
  { label: '中评', desc: '评分 = 3 星', value: reviewAnalysis.value.neutral || '0%', color: 'warning', icon: '😐' },
  { label: '差评', desc: '评分 ≤ 2 星', value: reviewAnalysis.value.negative || '0%', color: 'error', icon: '🙁' }
])

// 新品追踪周期切换：周期变化重新拉数据
const newProductPeriods = ['近 7 天', '近 14 天', '近 30 天']
const newProductPeriod = ref('近 7 天')
const newProductPeriodDays = computed(() => {
  if (newProductPeriod.value === '近 14 天') return 14
  if (newProductPeriod.value === '近 30 天') return 30
  return 7
})

// 解析时间范围 value -> 起止日期（用于筛选报表）
const timeRangeDates = computed(() => {
  const today = new Date()
  // 用本地日期格式化，避免 UTC 时区把"今天"算成昨天（凌晨 0-8 点会触发）
  const fmt = (d) => {
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${y}-${m}-${day}`
  }
  const end = today
  let start = new Date(today)
  switch (timeRange.value) {
    case 'today': break
    case '7d': start.setDate(today.getDate() - 7); break
    case '30d': start.setDate(today.getDate() - 30); break
    case '90d': start.setDate(today.getDate() - 90); break
    case 'half-year': start.setDate(today.getDate() - 183); break
    case 'custom':
      if (customRange.value && customRange.value.length === 2) {
        return { startDate: fmt(customRange.value[0]), endDate: fmt(customRange.value[1]) }
      }
      return { startDate: null, endDate: null }
    default: start.setDate(today.getDate() - 7)
  }
  return { startDate: fmt(start), endDate: fmt(end) }
})

// 加载受时间范围影响的数据（新品追踪 / 报表）
function loadTimeRangeData() {
  const { startDate, endDate } = timeRangeDates.value
  const hasRange = !!(startDate && endDate)
  // 新品追踪：根据周期重新拉
  getNewProductTracking(newProductPeriodDays.value).then(res => {
    newProductTracking.value = res || { metrics: [], list: [] }
  }).catch(e => console.error('加载新品追踪失败:', e))
  // 报表传时间区间，覆盖 allFiltered 的 periodSales 与 revenue；浏览/收藏/加购来自 list 不变
  getProductAnalysisReport({ startDate, endDate }).then(res => {
    const reportList = Array.isArray(res) ? res : (res && res.records) || []
    if (reportList.length === 0 && hasRange) {
      // 有区间但 report 无数据：保留 list 数据，提示用户，期间销量清零
      ElMessage.warning(`所选区间 ${startDate} ~ ${endDate} 无订单数据，期间销量按 0 显示`)
      allFiltered.value = allFiltered.value.map(d => ({ ...d, periodSales: 0, revenue: 0 }))
      total.value = allFiltered.value.length
      const s = (currentPage.value - 1) * pageSize.value
      tableData.value = allFiltered.value.slice(s, s + pageSize.value)
      return
    }
    if (reportList.length === 0) {
      // 无区间时 report 也空：保持原样
      return
    }
    // 按 id 把 report 区间销量合入 list
    const reportMap = new Map(reportList.map(d => [d.id, d]))
    allFiltered.value = allFiltered.value.map(d => {
      const r = reportMap.get(d.id)
      if (!r) return { ...d, periodSales: 0, revenue: 0 }
      return {
        ...d,
        periodSales: r.periodSales ?? r.sales ?? 0,
        revenue: r.revenue ?? 0
      }
    })
    total.value = allFiltered.value.length
    const s = (currentPage.value - 1) * pageSize.value
    tableData.value = allFiltered.value.slice(s, s + pageSize.value)
  }).catch(e => {
    console.error('加载商品报表失败:', e)
    ElMessage.error('加载商品报表失败，已显示全量商品')
  })
}

// 加载不受时间影响的静态分析数据
async function loadStaticAnalysisData() {
  const tasks = [
    getSlowMovingProducts().then(r => { slowMovingProducts.value = r || [] }).catch(e => console.error('滞销加载失败:', e)),
    getTurnoverOverview().then(r => { turnoverOverview.value = r || { metrics: [], categories: [] } }).catch(e => console.error('流转率加载失败:', e)),
    getHotSearchKeywords().then(r => { hotSearchKeywords.value = r || [] }).catch(e => console.error('搜索词加载失败:', e)),
    getReviewAnalysis().then(r => { reviewAnalysis.value = r || {} }).catch(e => console.error('评价分析加载失败:', e)),
    getReviewKeywords().then(r => { reviewKeywords.value = r || [] }).catch(e => console.error('评价关键词加载失败:', e)),
    getInventoryHealth().then(r => { inventoryHealth.value = r || { totalStock: 0, healthRate: '0%', items: [] } }).catch(e => console.error('库存健康度加载失败:', e)),
    getInventoryTurnoverRanking().then(r => { inventoryTurnoverRanking.value = r || [] }).catch(e => console.error('库存周转加载失败:', e))
  ]
  await Promise.all(tasks)
}

// 监听新品周期切换
watch(newProductPeriodDays, () => {
  getNewProductTracking(newProductPeriodDays.value).then(res => {
    newProductTracking.value = res || { metrics: [], list: [] }
  }).catch(e => console.error('加载新品追踪失败:', e))
})

// ===== Top 10 商品排行（基于接口数据按销量排序计算） =====
// 有时间范围时按区间销量排序；无时间范围时按总销量排序。
const top10Products = computed(() => {
  const hasRange = (() => {
    const { startDate, endDate } = timeRangeDates.value
    return !!(startDate && endDate)
  })()
  return [...allFiltered.value]
    .sort((a, b) => {
      const av = hasRange ? (a.periodSales || 0) : (a.sales || 0)
      const bv = hasRange ? (b.periodSales || 0) : (b.sales || 0)
      return bv - av
    })
    .slice(0, 10)
    .map((d, i) => ({ ...d, rank: i + 1 }))
})

// 周转天数显示上限：超过 999 天显示为 999+，避免 UI 撑爆
function formatTurnoverDays(days) {
  const n = Number(days) || 0
  if (n >= 999) return '999+天'
  return n + '天'
}

// 趋势柱状图：把后端原始销量数值归一化到 4~100%，避免 height: 120% 撑破容器
function trendBarHeight(h, trend) {
  const arr = Array.isArray(trend) ? trend : []
  if (!arr.length) return '4%'
  const max = Math.max(...arr, 0)
  if (!max) return '4%'
  // 最低 4%，最高 100%
  return Math.max((Number(h) || 0) * 100 / max, 4) + '%'
}

// 计算某商品销量占 Top1 销量的比例，用于水平条形图宽度
function salesPercent(sales) {
  const max = top10Products.value.length ? top10Products.value[0].sales : 0
  if (!max) return '0%'
  return Math.max((sales / max) * 100, 4) + '%'
}

// 排名徽标样式：前三名金/银/铜
function rankClass(rank) {
  return rank === 1 ? 'gold' : rank === 2 ? 'silver' : rank === 3 ? 'bronze' : ''
}

// 状态等级映射为设计令牌颜色变量（success/warning/error 与库存 slow/normal/low）
// 紧缺(low/critical)用更醒目的 critical 色，提示优先级高于滞销
function stateColor(level) {
  const map = {
    success: 'var(--state-success)',
    warning: 'var(--state-warning)',
    error: 'var(--state-error)',
    danger: 'var(--state-error)',
    critical: 'var(--state-error)',
    slow: 'var(--state-error)',
    normal: 'var(--state-success)',
    low: 'var(--state-error)'
  }
  return map[level] || 'var(--text-800)'
}

// ===== 以下区块数据来自后端接口（已替换原硬编码示例） =====
// 各 ref 已在文件上方声明并通过 loadStaticAnalysisData / loadTimeRangeData 填充

// ===== 从API加载KPI和列表数据（保留原逻辑，修复 KPI 总浏览量字段错配）=====
async function loadData() {
  try {
    // 始终走 list 接口：保证浏览/收藏/加购不受时间筛选影响，且不会被 report 接口的合并逻辑覆盖
    const [kpiRes, listRes] = await Promise.all([
      getProductAnalysisKpi(),
      getProductAnalysisList()
    ])
    // 填充KPI（修复原 bug：之前用 totalFavorites 显示为"总浏览量"，现在用后端真实返回的 totalViews）
    if (kpiRes) {
      kpiData.totalProducts = kpiRes.totalProductCount ?? 0
      kpiData.activeProducts = kpiRes.activeProductCount ?? 0
      kpiData.totalViews = kpiRes.totalViews ?? 0
      kpiData.totalSales = kpiRes.totalSales ?? 0
    }
    // 填充列表
    const list = (listRes && listRes.records) || listRes || []
    // 映射后端字段到前端期望的字段名
    const mapped = list.map(d => ({
      id: d.id,
      productName: d.name || d.productName,
      views: d.views || 0,
      favorites: d.favorites || 0,
      cartAdds: d.cartAdds || 0,
      sales: d.sales || 0,
      // 期间销量与收入由 loadTimeRangeData 用 report 接口覆盖；初始为空区间标识 0
      periodSales: 0,
      revenue: 0
    }))
    const filtered = mapped.filter(d => {
      const kw = filters.keyword.toLowerCase()
      if (kw && !d.productName.toLowerCase().includes(kw)) return false
      return true
    })
    // 保存过滤后的完整列表，供 Top 10 排行计算
    allFiltered.value = filtered
    total.value = filtered.length
    const start = (currentPage.value - 1) * pageSize.value
    tableData.value = filtered.slice(start, start + pageSize.value)
  } catch (e) {
    console.error('加载商品分析数据失败:', e)
    ElMessage.error('加载商品分析数据失败')
  }
}

function handleSearch() { currentPage.value = 1; loadData() }

function handleReset() { filters.keyword = ''; handleSearch() }

function handleDetail(row) {
  ElMessage.info('查看商品详情：' + row.productName)
}

onMounted(() => {
  // 串行执行：loadData 先拉基础数据 → loadTimeRangeData 用它合并时间范围内的销量 → 再并行拉静态分析
  loadData()
    .then(loadTimeRangeData)
    .then(loadStaticAnalysisData)
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
.page-header h2 { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0; }
.page-subtitle { font-size: 13px; color: var(--text-400); margin: 4px 0 0; }
.time-range-picker { display: flex; gap: 8px; align-items: center; }
.kpi-row { margin-bottom: 16px; }
.kpi-card { text-align: center; padding: 8px 0; }
.kpi-label { font-size: 14px; color: var(--text-400); margin-bottom: 8px; }
.kpi-value { font-size: 28px; font-weight: 700; color: var(--text-800); }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }

/* ===== 分析区块双列网格布局 ===== */
.analysis-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-bottom: 16px; }
.analysis-card { border: 1px solid var(--background-200); }
.analysis-card.span-2 { grid-column: 1 / -1; }

/* 卡片头部 */
.card-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.card-title { font-size: 15px; font-weight: 600; color: var(--text-800); }
.card-subtitle { font-size: 12px; color: var(--text-400); }

/* ===== 通用排名列表 ===== */
.rank-list { padding: 0 4px; }
.rank-item { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--background-200); }
.rank-item:last-child { border-bottom: none; }
.rank-badge { width: 24px; height: 24px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; background: var(--background-200); color: var(--text-500); flex-shrink: 0; }
.rank-badge.gold { background: #FFF2CC; color: #C49A1A; }
.rank-badge.silver { background: #EDEDF0; color: #8A8A8E; }
.rank-badge.bronze { background: #F5DEB3; color: #A67C52; }
.rank-info { flex: 1; min-width: 0; }
.rank-name { font-size: 14px; font-weight: 500; color: var(--text-800); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rank-stats { display: flex; gap: 16px; margin-top: 4px; }
.rank-stat-label { font-size: 11px; color: var(--text-400); }
.rank-stat-value { font-size: 12px; font-weight: 600; font-variant-numeric: tabular-nums; }
.rank-stat-value.sales { color: var(--text-800); }
.rank-stat-value.revenue { color: var(--brand-500); }
.rank-profit-tag { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 999px; white-space: nowrap; }
.rank-profit-tag.high { background: var(--state-success-surface); color: var(--state-success); }
.rank-profit-tag.medium { background: var(--state-warning-surface); color: var(--state-warning); }
.rank-profit-tag.low { background: var(--state-error-surface); color: var(--state-error); }

/* Top10 商品排行迷你条形图 */
.mini-bar-track { width: 110px; height: 6px; background: var(--background-200); border-radius: 999px; overflow: hidden; flex-shrink: 0; }
.mini-bar-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, var(--brand-400), var(--brand-500)); transition: width 0.5s ease; }

/* ===== 新品表现追踪 ===== */
/* 周期切换（与设计稿 admin-product-analysis.html 一致：active 文字变色 + 底部下划线） */
.period-tabs { display: flex; gap: 4px; }
.period-tab { height: 32px; padding: 0 12px; font-size: 12px; font-weight: 500; color: var(--text-400); background: transparent; border: none; cursor: pointer; border-radius: 0; position: relative; transition: color 0.15s ease; }
.period-tab.active { color: var(--primary); font-weight: 600; }
.period-tab.active::after {
  content: '';
  position: absolute;
  bottom: 2px;
  left: 50%;
  transform: translateX(-50%);
  width: 16px;
  height: 2px;
  border-radius: 2px;
  background: var(--primary);
}
.period-tab:hover:not(.active) { color: var(--text-600); }
.metric-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.metric-card { background: var(--background-100); border-radius: calc(var(--radius) * 0.7); padding: 12px; }
.metric-label { font-size: 11px; color: var(--text-400); margin-bottom: 4px; }
.metric-value { font-size: 22px; font-weight: 700; color: var(--text-800); font-variant-numeric: tabular-nums; line-height: 1.2; }
.metric-change { display: inline-flex; align-items: center; gap: 2px; font-size: 11px; font-weight: 600; margin-top: 4px; }
.metric-change.up { color: var(--state-success); }
.metric-change.down { color: var(--state-error); }
/* CSS 简化趋势柱状图 */
.trend-line { display: flex; align-items: flex-end; gap: 3px; height: 32px; margin-top: 8px; }
.trend-bar { flex: 1; border-radius: 2px; background: var(--brand-200); transition: height 0.3s ease; min-height: 4px; }
.trend-bar:last-child { background: var(--brand-500); }
.new-product-list { margin-top: 12px; }
.new-product-item { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--background-200); }
.new-product-item:last-child { border-bottom: none; }
.new-product-info { flex: 1; min-width: 0; }
.new-product-name { font-size: 13px; font-weight: 500; color: var(--text-800); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.new-product-meta { font-size: 11px; color: var(--text-400); margin-top: 2px; }
.new-product-rate { font-size: 12px; font-weight: 600; color: var(--brand-500); }

/* ===== 滞销商品预警 ===== */
.warning-badge { display: inline-flex; align-items: center; justify-content: center; min-width: 20px; height: 20px; padding: 0 6px; background: var(--state-error); color: var(--state-error-foreground); border-radius: 999px; font-size: 11px; font-weight: 600; }
.slow-moving-list { display: flex; flex-direction: column; gap: 8px; }
.slow-moving-item { display: flex; align-items: center; gap: 12px; padding: 10px 12px; background: var(--background-100); border-radius: calc(var(--radius) * 0.5); }
.slow-moving-info { flex: 1; min-width: 0; }
.slow-moving-name { font-size: 13px; font-weight: 500; color: var(--text-800); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.slow-moving-meta { display: flex; gap: 12px; margin-top: 2px; }
.slow-moving-meta span { font-size: 11px; color: var(--text-400); }
.slow-moving-days { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: 999px; white-space: nowrap; }
.slow-moving-days.danger { background: var(--state-error-surface); color: var(--state-error); }
.slow-moving-days.warning { background: var(--state-warning-surface); color: var(--state-warning); }

/* ===== 流转率概览 ===== */
.turnover-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 8px; }
.turnover-metric { background: var(--background-100); border-radius: calc(var(--radius) * 0.5); padding: 10px; text-align: center; }
.turnover-label { font-size: 11px; color: var(--text-400); margin-bottom: 4px; }
.turnover-value { font-size: 18px; font-weight: 700; color: var(--text-800); font-variant-numeric: tabular-nums; }
.category-list { padding: 0 4px; }

/* ===== 热门搜索词水平条形图 ===== */
.keyword-list { display: flex; flex-direction: column; gap: 14px; }
.keyword-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.keyword-name { font-size: 13px; font-weight: 500; color: var(--text-800); }
.keyword-stats { display: flex; gap: 12px; }
.keyword-count { font-size: 11px; color: var(--text-400); font-variant-numeric: tabular-nums; }
.keyword-rate { font-size: 11px; font-weight: 600; color: var(--brand-500); font-variant-numeric: tabular-nums; }
.bar-track { width: 100%; height: 8px; background: var(--background-200); border-radius: 999px; overflow: hidden; }
.bar-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, var(--brand-400), var(--brand-500)); transition: width 0.6s ease; }
.bar-fill.rating { background: linear-gradient(90deg, var(--state-success), var(--brand-500)); }

/* ===== 评价分析概览 ===== */
.review-summary { margin-bottom: 12px; }
.review-row { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--background-200); }
.review-row:last-child { border-bottom: none; }
.review-icon { width: 36px; height: 36px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 18px; flex-shrink: 0; }
.review-icon.success { background: var(--state-success-surface); }
.review-icon.warning { background: var(--state-warning-surface); }
.review-icon.error { background: var(--state-error-surface); }
.review-info { flex: 1; min-width: 0; }
.review-label { font-size: 13px; font-weight: 500; color: var(--text-800); }
.review-desc { font-size: 11px; color: var(--text-400); margin-top: 2px; }
.review-value { font-size: 15px; font-weight: 700; font-variant-numeric: tabular-nums; }
.rating-distribution { background: var(--background-100); border-radius: calc(var(--radius) * 0.5); padding: 12px; }
.rating-title { font-size: 12px; color: var(--text-400); margin-bottom: 8px; }
.rating-row { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.rating-row:last-child { margin-bottom: 0; }
.rating-stars { width: 34px; font-size: 12px; color: var(--text-600); flex-shrink: 0; }
.rating-percent { width: 40px; text-align: right; font-size: 12px; font-weight: 600; color: var(--text-600); font-variant-numeric: tabular-nums; flex-shrink: 0; }

/* ===== 高频评价关键词标签云 ===== */
.keyword-cloud { display: flex; flex-wrap: wrap; gap: 10px; }
.cloud-tag { font-size: 13px; }

/* ===== 库存健康度概览 ===== */
.inventory-kpis { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-bottom: 12px; }
.inventory-kpi { background: var(--background-100); border-radius: calc(var(--radius) * 0.5); padding: 10px; text-align: center; }
.inventory-kpi-label { font-size: 11px; color: var(--text-400); margin-bottom: 4px; }
.inventory-kpi-value { font-size: 18px; font-weight: 700; color: var(--text-800); font-variant-numeric: tabular-nums; }
.inventory-health { display: flex; flex-direction: column; gap: 10px; }
.inventory-health-row { display: flex; align-items: center; gap: 12px; }
.inventory-health-label { width: 48px; font-size: 12px; font-weight: 500; color: var(--text-500); flex-shrink: 0; }
.inventory-health-track { flex: 1; height: 20px; background: var(--background-200); border-radius: 999px; overflow: hidden; }
.inventory-health-fill { height: 100%; border-radius: 999px; display: flex; align-items: center; justify-content: flex-end; padding-right: 10px; font-size: 11px; font-weight: 700; color: var(--background-50); transition: width 0.6s ease; }
.inventory-health-fill.slow { background: var(--state-error); }
.inventory-health-fill.normal { background: var(--state-success); }
/* 紧缺用渐变，比 warning 更醒目；零库存场景（最紧急）会用更红的 critical */
.inventory-health-fill.low { background: linear-gradient(90deg, var(--state-warning), var(--state-error)); }
.inventory-health-fill.critical { background: var(--state-error); }
.inventory-health-percent { width: 36px; text-align: right; font-size: 13px; font-weight: 600; color: var(--text-800); font-variant-numeric: tabular-nums; flex-shrink: 0; }

/* 明细表格 */
.table-card { margin-top: 0; }

/* 窄屏降为单列 */
@media (max-width: 1200px) {
  .analysis-grid { grid-template-columns: 1fr; }
}
</style>
