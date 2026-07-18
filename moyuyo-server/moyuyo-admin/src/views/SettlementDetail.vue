<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建明细</el-button>
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

const pageTitle = '结算详情'
const filters = reactive({ keyword: '' })
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

// 从API加载结算详情数据
async function loadData() {
  try {
    const res = await getSettlementDetail()
    const data = res || {}
    // 填充摘要信息
    if (data.summary) {
      Object.assign(summary, data.summary)
    }
    // 填充明细列表
    const list = data.records || data.items || []
    tableData.value = list
    total.value = list.length
  } catch (e) {
    console.error('加载结算详情失败:', e)
    ElMessage.error('加载结算详情失败')
  }
}
function handleAdd() { ElMessage.warning('新增功能开发中') }
function handleDetail(row) { ElMessage.info('查看订单 ' + row.orderNo + ' 详情') }
onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.summary-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
