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
                  <span class="table-link">追踪</span>
                  <span class="table-link">详情</span>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="pagination">
            <div class="pagination-info">共 {{ packages.length }} 条</div>
            <div class="pagination-btns">
              <button class="pagination-btn">上一页</button>
              <button class="pagination-btn active">1</button>
              <button class="pagination-btn">2</button>
              <button class="pagination-btn">3</button>
              <button class="pagination-btn">下一页</button>
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
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import api from '../api/index'
import { getWarehouses, getInventoryAlerts, getCarriers } from '../api/admin'
import { ElMessage } from 'element-plus'

const activeWarehouse = ref('')
const loading = ref(false)

const warehouses = ref([])
const packages = ref([])
const inventoryAlerts = ref([])
const carrierDist = ref([])

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
    const res = await api.get('/logistics/kpi')
    if (res) {
      kpiData.value = res
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
      warehouses.value = res
      if (warehouses.value.length > 0 && !activeWarehouse.value) {
        activeWarehouse.value = warehouses.value[0].key
      }
    }
  } catch (err) {
    console.error('获取仓库列表失败:', err)
  }
}

// 获取包裹列表
async function fetchPackages() {
  loading.value = true
  try {
    const params = {}
    if (activeWarehouse.value) params.warehouse = activeWarehouse.value
    const res = await api.get('/logistics/packages', { params })
    if (res) {
      packages.value = res.records || res.list || res
    }
  } catch (err) {
    console.error('获取包裹列表失败:', err)
    ElMessage.error('获取包裹列表失败')
  } finally {
    loading.value = false
  }
}

// 获取库存预警
async function fetchInventoryAlerts() {
  try {
    const res = await getInventoryAlerts()
    if (res) {
      inventoryAlerts.value = res.records || res.list || res
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
  font-size: 20px;
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
