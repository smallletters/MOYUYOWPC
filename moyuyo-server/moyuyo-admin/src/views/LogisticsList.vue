<template>
  <div class="logistics-list">
    <!-- 警告横幅 -->
    <div class="alert-banner" v-if="inventoryAlerts.length > 0">
      ⚠️ {{ inventoryAlerts.length }}个商品库存低于预警线
    </div>

    <h2 class="page-title">物流管理</h2>

    <!-- KPI 卡片 -->
    <div class="kpi-row">
      <div class="kpi-card">
        <div class="kpi-value yellow">{{ kpiData.pendingShip }}</div>
        <div class="kpi-label">待发货</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value blue">{{ kpiData.inTransit }}</div>
        <div class="kpi-label">运输中</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value red">{{ kpiData.abnormal }}</div>
        <div class="kpi-label">异常</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value">{{ kpiData.avgTime }}</div>
        <div class="kpi-label">平均时效（天）</div>
      </div>
    </div>

    <!-- 仓库标签 -->
    <div class="tab-switcher">
      <button
        v-for="wh in warehouses"
        :key="wh.key"
        class="tab-switcher-item"
        :class="{ active: activeWarehouse === wh.key }"
        @click="activeWarehouse = wh.key"
      >
        {{ wh.label }}
      </button>
    </div>

    <div class="logistics-grid">
      <!-- 左栏：运输中的包裹 -->
      <div class="logistics-main">
        <div class="data-table-wrapper">
          <table class="data-table">
            <thead>
              <tr>
                <th>运单号</th>
                <th>承运商</th>
                <th>运输路线</th>
                <th>状态</th>
                <th>发货时间</th>
                <th>预计到达</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="pkg in packages" :key="pkg.trackingNo">
                <td><span class="table-link">{{ pkg.trackingNo }}</span></td>
                <td><span class="tag" :class="pkg.carrierClass">{{ pkg.carrier }}</span></td>
                <td>{{ pkg.route }}</td>
                <td><span class="tag" :class="pkg.statusClass">{{ pkg.statusLabel }}</span></td>
                <td>{{ pkg.shipTime }}</td>
                <td>{{ pkg.eta }}</td>
                <td class="cell-actions">
                  <span class="table-link" @click="handleTracking(pkg)">追踪</span>
                  <span class="table-link" @click="handleDetail(pkg)">详情</span>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="pagination">
            <div class="pagination-info">共 {{ totalPackages }} 条</div>
            <div class="pagination-btns">
              <button class="pagination-btn" :disabled="currentPage <= 1" @click="goToPage(currentPage - 1)">上一页</button>
              <button
                v-for="page in displayedPages"
                :key="page"
                class="pagination-btn"
                :class="{ active: currentPage === page }"
                @click="goToPage(page)"
              >{{ page }}</button>
              <button class="pagination-btn" :disabled="currentPage >= totalPages" @click="goToPage(currentPage + 1)">下一页</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏：库存预警 + 承运占比 -->
      <div class="logistics-side">
        <!-- 库存预警 -->
        <div class="card">
          <div class="card-header">
            <h3>库存预警</h3>
          </div>
          <div class="card-body">
            <div class="alert-list">
              <div class="alert-item" v-for="alert in inventoryAlerts" :key="alert.sku">
                <div class="alert-info">
                  <div class="alert-name">{{ alert.name }}</div>
                  <div class="alert-sku">{{ alert.sku }}</div>
                </div>
                <div class="alert-stock">
                  <span class="stock-count">{{ alert.stock }}</span>
                  <span class="stock-unit">件</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 承运商分布 -->
        <div class="card">
          <div class="card-header">
            <h3>承运商分布</h3>
          </div>
          <div class="card-body">
            <div class="carrier-dist">
              <div class="carrier-item" v-for="c in carrierDist" :key="c.name">
                <div class="carrier-header">
                  <span>{{ c.name }}</span>
                  <span class="carrier-pct">{{ c.percent }}%</span>
                </div>
                <div class="bar-track">
                  <div class="bar-fill-cd" :style="{ width: c.percent + '%', background: c.color }"></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 包裹详情弹窗 -->
    <el-dialog v-model="packageDetailDialogVisible" title="包裹详情" width="480px">
      <el-descriptions v-if="detailPackage" :column="1" border>
        <el-descriptions-item label="包裹号">{{ detailPackage.trackingNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="承运商">{{ detailPackage.carrier || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detailPackage.status || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发出时间">{{ detailPackage.shippedAt || detailPackage.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="重量">{{ detailPackage.weight || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWarehouses, getInventoryAlerts, getCarriers, getLogisticsKpi, getLogisticsPackages } from '../api/admin'
import { toArray } from '../utils/safeArray'

const router = useRouter()

const activeWarehouse = ref('')
const loading = ref(false)
const currentPage = ref(1)
const pageSize = 10
const totalPackages = ref(0)

const warehouses = ref([])
const packages = ref([])
const inventoryAlerts = ref([])
const carrierDist = ref([])

// 计算总页数
const totalPages = computed(() => Math.ceil(totalPackages.value / pageSize) || 1)

// 显示页码（最多显示5个）
const displayedPages = computed(() => {
  const total = totalPages.value
  const current = currentPage.value
  if (total <= 5) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }
  if (current <= 3) return [1, 2, 3, 4, 5]
  if (current >= total - 2) return [total - 4, total - 3, total - 2, total - 1, total]
  return [current - 2, current - 1, current, current + 1, current + 2]
})

// KPI数据
const kpiData = ref({
  pendingShip: 0,
  inTransit: 0,
  abnormal: 0,
  avgTime: 0
})

// 获取物流KPI数据
async function fetchKpi() {
  try {
    const res = await getLogisticsKpi()
    if (res) {
      kpiData.value = {
        pendingShip: res.pendingPick || 0,
        inTransit: res.inTransit || 0,
        abnormal: res.abnormal || 0,
        avgTime: res.avgDeliveryHours ? (res.avgDeliveryHours / 24).toFixed(1) : 0
      }
    }
  } catch (err) {
    console.error('获取物流KPI失败:', err)
  }
}

// 获取仓库列表
async function fetchWarehouses() {
  try {
    const res = await getWarehouses()
    if (res) {
      warehouses.value = toArray(res).map(w => ({
        key: String(w.id),
        label: w.name,
        ...w
      }))
      if (warehouses.value.length > 0 && !activeWarehouse.value) {
        activeWarehouse.value = warehouses.value[0].key
      }
    }
  } catch (err) {
    console.error('获取仓库列表失败:', err)
  }
}

// 获取包裹列表（分页）
async function fetchPackages() {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize
    }
    if (activeWarehouse.value) params.warehouse = activeWarehouse.value
    const res = await getLogisticsPackages(params)
    if (res) {
      const list = toArray(res)
      packages.value = list.map(p => ({
        ...p,
        trackingNo: p.trackingNo || p.trackingNumber || '',
        route: [p.origin, p.destination].filter(Boolean).join(' → ') || '',
        statusLabel: ({'PENDING':'待发货','IN_TRANSIT':'运输中','DELIVERED':'已签收'})[p.status] || p.status,
        statusClass: ({'PENDING':'tag-yellow','IN_TRANSIT':'tag-blue','DELIVERED':'tag-green'})[p.status] || '',
        carrierClass: 'tag-blue',
        shipTime: p.estimatedDelivery ? new Date(p.estimatedDelivery).toLocaleDateString() : '',
        eta: p.estimatedDelivery ? new Date(p.estimatedDelivery).toLocaleDateString() : ''
      }))
      totalPackages.value = res.total || 0
    }
  } catch (err) {
    console.error('获取包裹列表失败:', err)
    ElMessage.error('获取包裹列表失败')
  } finally {
    loading.value = false
  }
}

// 页码切换
function goToPage(page) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  fetchPackages()
}

// 追踪物流：根据不同的承运商跳转到对应查询页
const carrierTrackUrls = {
  ups: 'https://www.ups.com/track?tracknum=',
  fedex: 'https://www.fedex.com/fedextrack/?trknbr=',
  usps: 'https://tools.usps.com/go/TrackConfirmAction?tLabels=',
  dhl: 'https://www.dhl.com/en/express/tracking.html?AWB=',
  yto: 'https://www.yto.net.cn/', // 圆通
  sto: 'https://www.sto.cn/', // 申通
  zto: 'https://www.zto.com/', // 中通
  yunda: 'https://www.yundaex.com/' // 韵达
}

function detectCarrier(carrier) {
  if (!carrier) return null
  const c = String(carrier).toLowerCase()
  for (const key in carrierTrackUrls) {
    if (c.includes(key)) return key
  }
  return null
}

function handleTracking(pkg) {
  if (!pkg.trackingNo) {
    ElMessage.warning('该包裹暂无运单号')
    return
  }
  const carrierKey = detectCarrier(pkg.carrier) || detectCarrier(pkg.carrierCode)
  if (carrierKey && carrierTrackUrls[carrierKey]) {
    // 拼接 URL 跳转查询
    const baseUrl = carrierTrackUrls[carrierKey]
    const url = baseUrl.includes('?') ? baseUrl + pkg.trackingNo : baseUrl + pkg.trackingNo
    window.open(url, '_blank', 'noopener,noreferrer')
    ElMessage.success('已打开物流追踪')
  } else {
    // 识别不到承运商时弹出对话框显示运单信息
    ElMessageBox.alert(
      '运单号：' + pkg.trackingNo + '\n承运商：' + (pkg.carrier || '未知') + '\n请到对应承运商官网查询',
      '物流追踪',
      { confirmButtonText: '我知道了' }
    )
  }
}

const packageDetailDialogVisible = ref(false)
const detailPackage = ref(null)
function handleDetail(pkg) {
  if (pkg.orderId) {
    router.push(`/orders/${pkg.orderId}`)
    return
  }
  // 无 orderId 时弹窗显示包裹信息
  detailPackage.value = pkg
  packageDetailDialogVisible.value = true
}

// 获取库存预警
async function fetchInventoryAlerts() {
  try {
    const res = await getInventoryAlerts()
    if (res) {
      const list = toArray(res)
      inventoryAlerts.value = list.map(a => ({
        sku: a.sku || a.productCode || '',
        name: a.name || a.productName || '',
        stock: a.stock || a.currentStock || 0
      }))
    }
  } catch (err) {
    console.error('获取库存预警失败:', err)
  }
}

// 获取承运商分布
async function fetchCarriers() {
  try {
    const res = await getCarriers()
    if (res) {
      carrierDist.value = res
    }
  } catch (err) {
    console.error('获取承运商分布失败:', err)
  }
}

// 切换仓库时重新加载包裹
watch(activeWarehouse, () => {
  fetchPackages()
})

onMounted(() => {
  fetchKpi()
  fetchWarehouses()
  fetchPackages()
  fetchInventoryAlerts()
  fetchCarriers()
})
</script>

<style scoped lang="css">
.alert-banner {
  padding: 10px 16px;
  background: var(--state-warning-surface);
  border: 1px solid var(--state-warning);
  border-radius: var(--radius-sm);
  color: var(--state-warning);
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 16px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 20px;
}

/* KPI */
.kpi-row {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.kpi-card {
  flex: 1;
  padding: 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-800);
  margin-bottom: 4px;
}

.kpi-value.yellow { color: var(--state-warning); }
.kpi-value.blue { color: var(--primary); }
.kpi-value.red { color: var(--state-error); }

.kpi-label {
  font-size: 13px;
  color: var(--text-400);
}

.logistics-grid {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  margin-top: 16px;
}

.logistics-main {
  flex: 1;
}

.logistics-side {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 库存预警 */
.alert-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.alert-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--state-error-surface);
  border-radius: var(--radius-sm);
}

.alert-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-800);
  margin-bottom: 2px;
}

.alert-sku {
  font-size: 11px;
  color: var(--text-400);
}

.stock-count {
  font-size: 20px;
  font-weight: 700;
  color: var(--state-error);
}

.stock-unit {
  font-size: 11px;
  color: var(--text-400);
  margin-left: 2px;
}

/* 承运商分布 */
.carrier-dist {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.carrier-header {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-600);
  margin-bottom: 4px;
}

.carrier-pct {
  font-weight: 600;
  color: var(--text-800);
}

.bar-track {
  height: 8px;
  border-radius: 4px;
  background: var(--background-100);
  overflow: hidden;
}

.bar-fill-cd {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s ease;
}
</style>
