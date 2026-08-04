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
          <div v-for="m in NEW_PRODUCT_TRACKING.metrics" :key="m.label" class="metric-card">
            <div class="metric-label">{{ m.label }}</div>
            <div class="metric-value">{{ m.value }}</div>
            <div class="metric-change" :class="m.up ? 'up' : 'down'">{{ m.change }}</div>
            <!-- CSS 趋势柱状图 -->
            <div class="trend-line">
              <div v-for="(h, i) in m.trend" :key="i" class="trend-bar" :style="{ height: h + '%' }"></div>
            </div>
          </div>
        </div>
        <div class="new-product-list">
          <div v-for="item in NEW_PRODUCT_TRACKING.list" :key="item.name" class="new-product-item">
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
            <span class="warning-badge">{{ SLOW_MOVING_PRODUCTS.length }}</span>
          </div>
        </template>
        <div class="slow-moving-list">
          <div v-for="item in SLOW_MOVING_PRODUCTS" :key="item.name" class="slow-moving-item">
            <div class="slow-moving-info">
              <div class="slow-moving-name">{{ item.name }}</div>
              <div class="slow-moving-meta">
                <span>库存: {{ item.stock }}</span>
                <span>近30天售出: {{ item.sales30 }}</span>
              </div>
            </div>
            <span class="slow-moving-days" :class="item.level">{{ item.days }}天</span>
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
          <div v-for="m in TURNOVER_RATE.metrics" :key="m.label" class="turnover-metric">
            <div class="turnover-label">{{ m.label }}</div>
            <div class="turnover-value">{{ m.value }}</div>
          </div>
        </div>
        <div class="category-list">
          <div v-for="(c, i) in TURNOVER_RATE.categories" :key="c.name" class="rank-item">
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
          <div v-for="kw in HOT_SEARCH_KEYWORDS" :key="kw.keyword" class="keyword-item">
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
          <div v-for="r in REVIEW_ANALYSIS.items" :key="r.label" class="review-row">
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
          <div v-for="d in REVIEW_ANALYSIS.distribution" :key="d.stars" class="rating-row">
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
            v-for="k in HIGH_FREQ_REVIEW_KEYWORDS"
            :key="k.keyword"
            :type="k.type || 'info'"
            effect="light"
            class="cloud-tag"
          >{{ k.keyword }}</el-tag>
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
            <div class="inventory-kpi-value">{{ INVENTORY_HEALTH.totalStock }}</div>
          </div>
          <div class="inventory-kpi">
            <div class="inventory-kpi-label">健康占比</div>
            <div class="inventory-kpi-value" style="color: var(--state-success)">{{ INVENTORY_HEALTH.healthRate }}</div>
          </div>
        </div>
        <div class="inventory-health">
          <div v-for="item in INVENTORY_HEALTH.items" :key="item.label" class="inventory-health-row">
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
        <el-table :data="INVENTORY_TURNOVER_RANKING" size="small">
          <el-table-column label="排名" width="60">
            <template #default="{ $index }">
              <span class="rank-badge" :class="rankClass($index + 1)">{{ $index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="商品名称" min-width="150" />
          <el-table-column prop="stock" label="库存" width="80" />
          <el-table-column label="周转天数" width="100">
            <template #default="{ row }">
              <span :style="{ color: stateColor(row.level) }">{{ row.days }}天</span>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProductAnalysisKpi, getProductAnalysisList } from '../api/admin'

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

// 时间范围变化：刷新数据（后端接口暂不支持时间参数，仅预留交互）
function handleTimeChange() {
  loadData()
}

// ===== Top 10 商品排行（基于接口数据按销量排序计算） =====
const top10Products = computed(() => {
  return [...allFiltered.value]
    .sort((a, b) => (b.sales || 0) - (a.sales || 0))
    .slice(0, 10)
    .map((d, i) => ({ ...d, rank: i + 1 }))
})

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
function stateColor(level) {
  const map = {
    success: 'var(--state-success)',
    warning: 'var(--state-warning)',
    error: 'var(--state-error)',
    danger: 'var(--state-error)',
    slow: 'var(--state-error)',
    normal: 'var(--state-success)',
    low: 'var(--state-warning)'
  }
  return map[level] || 'var(--text-800)'
}

// ===== 以下区块为示例数据（后端暂无对应接口，结构与设计稿一致，接入真实 API 后替换） =====
// 新品表现追踪示例数据：KPI 指标（含 CSS 趋势柱状图）+ 新品列表
const NEW_PRODUCT_TRACKING = {
  metrics: [
    { label: '新品销量', value: '3,462', change: '+12.5%', up: true, trend: [45, 60, 40, 72, 55, 80, 92] },
    { label: '新品转化率', value: '6.8%', change: '+0.3%', up: true, trend: [50, 55, 48, 65, 58, 70, 85] }
  ],
  list: [
    { name: '智能宠物饮水机 Pro', launchAt: '2026-07-12', sales: 1286, conversion: '7.2%' },
    { name: '自动逗猫机器人', launchAt: '2026-07-05', sales: 864, conversion: '6.5%' },
    { name: '宠物毛发清理滚轮', launchAt: '2026-06-28', sales: 652, conversion: '5.9%' }
  ]
}
// 新品周期切换（示例交互）
const newProductPeriods = ['近 7 天', '近 14 天', '近 30 天']
const newProductPeriod = ref('近 7 天')

// 滞销商品预警示例数据：低销量商品列表（danger=红色标签，warning=橙色标签）
const SLOW_MOVING_PRODUCTS = [
  { name: '复古皮质狗项圈', stock: 328, sales30: 2, days: 96, level: 'danger' },
  { name: '夏季薄款宠物T恤', stock: 215, sales30: 5, days: 72, level: 'danger' },
  { name: '迷你陶瓷宠物食盆', stock: 186, sales30: 8, days: 58, level: 'danger' },
  { name: '猫用电动剃毛器', stock: 142, sales30: 12, days: 45, level: 'warning' },
  { name: '宠物车载安全带', stock: 96, sales30: 6, days: 42, level: 'warning' },
  { name: '宠物冰垫凉席', stock: 267, sales30: 18, days: 38, level: 'warning' }
]

// 流转率概览示例数据：核心 KPI + 分类流转率排行
const TURNOVER_RATE = {
  metrics: [
    { label: '平均流转率', value: '68.4%' },
    { label: '加购率', value: '12.7%' },
    { label: '收藏率', value: '8.3%' }
  ],
  categories: [
    { name: '洗护用品', views: '12.4k', deals: '8.5k', rate: '68.5%', level: 'high' },
    { name: '主粮', views: '18.2k', deals: '11.9k', rate: '65.4%', level: 'high' },
    { name: '装备', views: '8.7k', deals: '5.2k', rate: '59.8%', level: 'medium' },
    { name: '玩具', views: '6.1k', deals: '3.4k', rate: '55.7%', level: 'medium' },
    { name: '家居', views: '4.3k', deals: '2.1k', rate: '48.9%', level: 'low' }
  ]
}

// 热门搜索词 Top 10 示例数据：percent 为相对最大搜索量的条形图宽度
const HOT_SEARCH_KEYWORDS = [
  { keyword: '猫粮', count: 3847, cartRate: '18.2%', percent: 100 },
  { keyword: '狗粮', count: 3126, cartRate: '15.7%', percent: 81.2 },
  { keyword: '猫砂', count: 2845, cartRate: '22.4%', percent: 73.9 },
  { keyword: '胸背带', count: 2198, cartRate: '16.8%', percent: 57.1 },
  { keyword: '宠物洗护', count: 1876, cartRate: '14.3%', percent: 48.8 },
  { keyword: '猫玩具', count: 1654, cartRate: '19.5%', percent: 43.0 },
  { keyword: '狗玩具', count: 1423, cartRate: '17.1%', percent: 37.0 },
  { keyword: '宠物外套', count: 1187, cartRate: '12.8%', percent: 30.9 },
  { keyword: '牵引绳', count: 986, cartRate: '13.5%', percent: 25.6 },
  { keyword: '宠物营养', count: 845, cartRate: '10.6%', percent: 22.0 }
]

// 评价分析概览示例数据：好评/中评/差评 KPI + 评分分布条形图
const REVIEW_ANALYSIS = {
  items: [
    { label: '好评', desc: '占比最高的评价类型', value: '89.2%', color: 'success', icon: '😊' },
    { label: '中评', desc: '需关注改进的商品体验', value: '7.4%', color: 'warning', icon: '😐' },
    { label: '差评', desc: '需要紧急处理的问题', value: '3.4%', color: 'error', icon: '🙁' }
  ],
  distribution: [
    { stars: 5, percent: 72 },
    { stars: 4, percent: 17 },
    { stars: 3, percent: 6 },
    { stars: 2, percent: 2 },
    { stars: 1, percent: 3 }
  ]
}

// 高频评价关键词示例数据（标签云，type 对应 el-tag 状态色）
const HIGH_FREQ_REVIEW_KEYWORDS = [
  { keyword: '质量好', type: 'success' },
  { keyword: '快递快', type: 'success' },
  { keyword: '性价比高', type: 'success' },
  { keyword: '包装精美', type: 'success' },
  { keyword: '耐用', type: 'success' },
  { keyword: '客服好', type: 'success' },
  { keyword: '偏小', type: 'warning' },
  { keyword: '物流慢', type: 'warning' },
  { keyword: '味道大', type: 'danger' },
  { keyword: '尺寸不准', type: 'danger' }
]

// 库存健康度概览示例数据：level 对应 slow=滞销 / normal=正常 / low=紧缺
const INVENTORY_HEALTH = {
  totalStock: 867,
  healthRate: '65%',
  items: [
    { label: '滞销', count: 156, percent: 18, level: 'slow' },
    { label: '正常', count: 564, percent: 65, level: 'normal' },
    { label: '紧缺', count: 147, percent: 17, level: 'low' }
  ]
}

// 库存周转天数排行示例数据
const INVENTORY_TURNOVER_RANKING = [
  { name: '复古皮质狗项圈', stock: 328, days: 96, level: 'danger' },
  { name: '夏季薄款宠物T恤', stock: 215, days: 72, level: 'danger' },
  { name: '迷你陶瓷宠物食盆', stock: 186, days: 58, level: 'warning' },
  { name: '猫用电动剃毛器', stock: 142, days: 45, level: 'warning' },
  { name: '高端宠物洗护套装', stock: 89, days: 12, level: 'success' }
]

// ===== 从API加载KPI和列表数据（保留原逻辑） =====
async function loadData() {
  try {
    // 并行加载KPI和列表数据
    const [kpiRes, listRes] = await Promise.all([
      getProductAnalysisKpi(),
      getProductAnalysisList()
    ])
    // 填充KPI
    if (kpiRes) {
      kpiData.totalProducts = kpiRes.totalProductCount ?? 0
      kpiData.activeProducts = kpiRes.activeProductCount ?? 0
      kpiData.totalViews = kpiRes.totalFavorites ?? 0
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
      revenue: d.revenue || (d.price && d.sales ? d.price * d.sales : 0)
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

onMounted(() => { loadData() })
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
.inventory-health-fill.low { background: var(--state-warning); }
.inventory-health-percent { width: 36px; text-align: right; font-size: 13px; font-weight: 600; color: var(--text-800); font-variant-numeric: tabular-nums; flex-shrink: 0; }

/* 明细表格 */
.table-card { margin-top: 0; }

/* 窄屏降为单列 */
@media (max-width: 1200px) {
  .analysis-grid { grid-template-columns: 1fr; }
}
</style>
