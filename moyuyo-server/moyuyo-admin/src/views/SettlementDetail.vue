<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button @click="router.back()">返回</el-button>
      </div>
    </div>
    <!-- 结算单摘要 -->
    <el-card shadow="never" class="summary-card">
      <el-descriptions :column="4" border>
        <el-descriptions-item label="结算单号">{{ summary.settleNo }}</el-descriptions-item>
        <el-descriptions-item label="周期">{{ summary.period }}</el-descriptions-item>
        <el-descriptions-item label="商家">{{ summary.merchant }}</el-descriptions-item>
        <el-descriptions-item label="结算金额">￥{{ summary.amount.toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="订单总数">{{ summary.orderCount }}</el-descriptions-item>
        <el-descriptions-item label="总金额">￥{{ summary.totalAmount.toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="总佣金">￥{{ summary.totalCommission.toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="实际结算">￥{{ summary.actualSettlement.toFixed(2) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
    <el-card shadow="never">
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderNo" label="订单号" width="160" />
        <el-table-column prop="productName" label="商品名称" width="160" />
        <el-table-column prop="quantity" label="数量" width="70" />
        <el-table-column label="金额" width="100">
          <template #default="{ row }">￥{{ row.amount.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="佣金" width="100">
          <template #default="{ row }">￥{{ row.commission.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="实际结算" width="110">
          <template #default="{ row }">￥{{ row.actualSettlement.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">详情</el-button>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSettlementDetail } from '../api/admin'
import { useRoute, useRouter } from 'vue-router'
import { toArray } from '../utils/safeArray'

const pageTitle = '结算详情'
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)

// 结算单摘要信息
const summary = reactive({
  settleNo: '',
  period: '',
  merchant: '',
  amount: 0,
  orderCount: 0,
  totalAmount: 0,
  totalCommission: 0,
  actualSettlement: 0
})

const route = useRoute()
const router = useRouter()

// 从API加载结算详情数据
async function loadData() {
  try {
    const settlementId = route.query.id
    if (!settlementId) {
      ElMessage.warning('缺少结算单ID')
      return
    }
    const res = await getSettlementDetail(settlementId)
    const data = res || {}
    // 填充摘要信息
    if (data.summary) {
      Object.assign(summary, data.summary)
    }
    // 填充明细列表（使用后端返回的数组字段，toArray会自动查找 records/list/data/items）
    const list = toArray(data, 'records')
    tableData.value = list
    total.value = tableData.value.length
  } catch (e) {
    console.error('加载结算详情失败:', e)
    ElMessage.error('加载结算详情失败')
  }
}
function handleDetail(row) {
  // 跳转到订单详情页
  if (row.orderId || row.id) {
    router.push({ path: `/orders/${row.orderId || row.id}` })
  } else {
    ElMessage.info('暂无关联订单信息')
  }
}
onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.summary-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
