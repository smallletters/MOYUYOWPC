<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>订单改价</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新增改价</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="订单编号">
          <el-input v-model="filters.keyword" placeholder="请输入订单编号" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderNo" label="订单编号" width="170" />
        <el-table-column prop="product" label="商品" min-width="160" />
        <el-table-column prop="originalAmount" label="原金额" width="100">
          <template #default="{ row }">¥{{ row.originalAmount }}</template>
        </el-table-column>
        <el-table-column prop="adjustAmount" label="调整金额" width="100">
          <template #default="{ row }">
            <span :style="{ color: row.adjustAmount < 0 ? '#67c23a' : '#f56c6c' }">{{ row.adjustAmount > 0 ? '+' : '' }}{{ row.adjustAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="finalAmount" label="最终金额" width="100">
          <template #default="{ row }">¥{{ row.finalAmount }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="改价原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="110" />
        <el-table-column prop="modifyTime" label="改价时间" width="170" />
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
    <el-dialog v-model="dialogVisible" title="新增改价" width="600px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="订单编号" required>
          <el-input v-model="editForm.orderNo" placeholder="请输入订单编号" />
        </el-form-item>
        <el-form-item label="商品">
          <el-input v-model="editForm.product" disabled />
        </el-form-item>
        <el-form-item label="原金额" required>
          <el-input-number v-model="editForm.originalAmount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="调整金额" required>
          <el-input-number v-model="editForm.adjustAmount" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="改价原因" required>
          <el-input v-model="editForm.reason" type="textarea" :rows="3" placeholder="请输入改价原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)

const filters = reactive({
  keyword: ''
})

const editForm = reactive({
  id: null,
  orderNo: '',
  product: '',
  originalAmount: 0,
  adjustAmount: 0,
  reason: '',
  operator: '',
  modifyTime: ''
})

const mockData = [
  { id: 1, orderNo: 'MOY20260710001', product: '无线蓝牙耳机 Pro', originalAmount: 298, adjustAmount: -30, finalAmount: 268, reason: '老客户优惠', operator: '管理员A', modifyTime: '2026-07-10 14:30:00' },
  { id: 2, orderNo: 'MOY20260711002', product: '智能手表S3', originalAmount: 678, adjustAmount: -50, finalAmount: 628, reason: '会员折扣', operator: '管理员B', modifyTime: '2026-07-11 09:20:00' },
  { id: 3, orderNo: 'MOY20260712003', product: '运动休闲鞋', originalAmount: 398, adjustAmount: -20, finalAmount: 378, reason: '补偿配送延迟', operator: '管理员A', modifyTime: '2026-07-12 16:45:00' },
  { id: 4, orderNo: 'MOY20260713004', product: '不锈钢保温杯', originalAmount: 118, adjustAmount: 10, finalAmount: 128, reason: '客户要求加购配件', operator: '管理员C', modifyTime: '2026-07-13 11:10:00' },
  { id: 5, orderNo: 'MOY20260714005', product: '轻薄笔记本电脑包', originalAmount: 377, adjustAmount: -77, finalAmount: 300, reason: '大客户优惠', operator: '管理员B', modifyTime: '2026-07-14 08:30:00' }
]

const tableData = ref([])

function loadData() {
  const start = (currentPage.value - 1) * pageSize.value
  const list = mockData.filter(d => {
    const kw = filters.keyword.toLowerCase()
    if (kw && !d.orderNo.toLowerCase().includes(kw)) return false
    return true
  })
  total.value = list.length
  tableData.value = list.slice(start, start + pageSize.value)
}

function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }

function handleAdd() {
  isEdit.value = false
  editForm.id = null
  editForm.orderNo = ''
  editForm.product = ''
  editForm.originalAmount = 0
  editForm.adjustAmount = 0
  editForm.reason = ''
  dialogVisible.value = true
}

function handleSave() {
  if (!editForm.orderNo || !editForm.reason) {
    ElMessage.warning('请填写必要信息')
    return
  }
  const newId = Math.max(...mockData.map(d => d.id)) + 1
  mockData.push({
    id: newId,
    orderNo: editForm.orderNo,
    product: editForm.product || '待填充',
    originalAmount: editForm.originalAmount,
    adjustAmount: editForm.adjustAmount,
    finalAmount: editForm.originalAmount + editForm.adjustAmount,
    reason: editForm.reason,
    operator: '当前管理员',
    modifyTime: new Date().toLocaleString('zh-CN', { hour12: false })
  })
  dialogVisible.value = false
  loadData()
  ElMessage.success('改价成功')
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
