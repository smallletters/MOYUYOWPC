<template>
  <div class="page-wrapper">
    <div class="page-title-section">
      <h1 class="page-title">积分兑换记录</h1>
      <p class="page-desc">查看用户积分兑换的商品、消耗积分与对应订单的履约状态</p>
    </div>

    <section class="section-block">
      <div class="section-header">
        <h2 class="section-title">兑换记录</h2>
        <div class="section-filter">
          <select v-model="filters.orderStatus" class="input-sm" @change="handleSearch">
            <option value="">全部订单状态</option>
            <option value="PENDING_SHIP">待发货</option>
            <option value="SHIPPED">已发货</option>
            <option value="RECEIVED">已收货</option>
            <option value="COMPLETED">已完成</option>
            <option value="REFUNDING">退款中</option>
            <option value="REFUNDED">已退款</option>
            <option value="CANCELLED">已取消</option>
          </select>
          <input v-model="filters.userId" placeholder="用户ID" class="input-sm" />
          <button class="btn btn-outline btn-sm" @click="handleSearch">查询</button>
        </div>
      </div>

      <div class="data-table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>兑换单ID</th>
              <th>用户</th>
              <th>兑换商品</th>
              <th>消耗积分</th>
              <th>关联订单</th>
              <th>订单状态</th>
              <th>收货人</th>
              <th>联系电话</th>
              <th>收货地址</th>
              <th>物流单号</th>
              <th>兑换时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in records" :key="item.id">
              <td>{{ item.id }}</td>
              <td>{{ userNameText(item) }}</td>
              <td>{{ item.goodsName || '-' }}</td>
              <td>{{ item.pointsCost || 0 }}</td>
              <td>
                <!-- 兑换履约走订单流程，点击订单号进入订单详情发货 -->
                <router-link v-if="item.orderId" :to="`/orders/${item.orderId}`" class="order-link">
                  {{ item.orderNo || ('#' + item.orderId) }}
                </router-link>
                <span v-else>-</span>
              </td>
              <td>
                <span :class="['status-tag', statusClass(item.orderStatus)]">
                  {{ orderStatusText(item.orderStatus) }}
                </span>
              </td>
              <td>{{ item.receiverName || '-' }}</td>
              <td>{{ item.receiverPhone || '-' }}</td>
              <td class="td-address" :title="item.receiverAddress || ''">
                {{ item.receiverAddress || '-' }}
              </td>
              <td>{{ item.trackingNo || '-' }}</td>
              <td>{{ formatTime(item.createTime) }}</td>
            </tr>
            <tr v-if="!loading && records.length === 0">
              <td colspan="11" class="td-empty">暂无兑换记录</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination-bar">
        <span class="pagination-info">共 {{ total }} 条</span>
        <button class="btn btn-outline btn-sm" :disabled="page <= 1" @click="changePage(-1)">上一页</button>
        <span class="pagination-page">{{ page }}</span>
        <button class="btn btn-outline btn-sm" :disabled="page * size >= total" @click="changePage(1)">下一页</button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPointsExchanges } from '../api/admin'
import { orderStatusText, orderStatusTone } from '../constants/orderStatus'

const records = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filters = ref({ orderStatus: '', userId: '' })

// 状态标签样式：颜色语义来自共享字典，具体 class 由本页样式定义
function statusClass(status) {
  return `status-${orderStatusTone(status)}`
}

function userNameText(item) {
  if (item.userName) return item.userName
  if (item.userEmail) return item.userEmail
  return item.userId ? `#${item.userId}` : '-'
}

function formatTime(time) {
  if (!time) return '-'
  return String(time).replace('T', ' ').slice(0, 19)
}

async function fetchRecords() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (filters.value.orderStatus) params.orderStatus = filters.value.orderStatus
    if (filters.value.userId) params.userId = filters.value.userId
    const res = await getPointsExchanges(params)
    records.value = res?.list || []
    total.value = res?.total || 0
  } catch (err) {
    console.error('获取积分兑换记录失败:', err)
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  // 用户ID 是 Long 型，非数字会让后端参数绑定失败，这里提前拦截
  const uid = String(filters.value.userId || '').trim()
  if (uid && !/^\d+$/.test(uid)) {
    ElMessage.warning('用户ID需为数字')
    return
  }
  filters.value.userId = uid
  page.value = 1
  fetchRecords()
}

function changePage(delta) {
  page.value += delta
  fetchRecords()
}

onMounted(fetchRecords)
</script>

<style scoped>
.page-wrapper { padding: 24px; }
.page-title-section { margin-bottom: 24px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0 0 4px; }
.page-desc { color: var(--text-500); font-size: 14px; margin: 0; }

.section-block { background: var(--card); border: 1px solid var(--border); border-radius: var(--radius); padding: 20px; margin-bottom: 24px; box-shadow: var(--shadow-xs); }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.section-title { font-size: 16px; font-weight: 600; color: var(--text-800); margin: 0; }
.section-filter { display: flex; gap: 8px; align-items: center; }

.input-sm { padding: 6px 12px; border: 1px solid var(--border); border-radius: var(--radius-sm); font-size: 13px; width: 160px; }

.data-table-wrapper { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th { text-align: left; padding: 10px 12px; font-size: 12px; font-weight: 600; color: var(--text-500); background: var(--background-200); border-bottom: 1px solid var(--border); }
.data-table td { padding: 12px; font-size: 13px; color: var(--text-700); border-bottom: 1px solid var(--background-100); }
.td-empty { text-align: center; color: var(--text-400); padding: 32px !important; }

.btn { padding: 8px 16px; border-radius: 6px; font-size: 13px; cursor: pointer; border: none; }
.btn-outline { background: var(--card); color: var(--text-700); border: 1px solid var(--border); }
.btn-sm { padding: 6px 12px; font-size: 12px; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

.status-tag { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; }
.status-success { background: var(--state-success-surface); color: var(--state-success); }
.status-danger { background: var(--state-error-surface); color: var(--state-error); }
.status-warning { background: var(--state-warning-surface, #fff7e6); color: var(--state-warning, #d48806); }
.status-info { background: var(--background-200); color: var(--text-600); }

.td-address {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.order-link {
  color: var(--primary, #409eff);
  text-decoration: none;
}
.order-link:hover {
  text-decoration: underline;
}
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 0;
}
.pagination-info,
.pagination-page {
  font-size: 12px;
  color: var(--text-500);
}
</style>
