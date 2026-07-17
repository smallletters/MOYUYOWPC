<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入结算单号/商家" clearable />
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
        <el-table-column prop="settleNo" label="结算单号" width="160" />
        <el-table-column prop="period" label="周期" width="120" />
        <el-table-column prop="merchant" label="商家" width="140" />
        <el-table-column label="结算金额" width="120">
          <template #default="{ row }">￥{{ row.amount.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === '已结算' ? 'success' : row.status === '已确认' ? 'primary' : 'warning'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="结算单号">
          <el-input v-model="editForm.settleNo" placeholder="请输入结算单号" />
        </el-form-item>
        <el-form-item label="周期">
          <el-input v-model="editForm.period" placeholder="如 2026-07上" />
        </el-form-item>
        <el-form-item label="商家">
          <el-input v-model="editForm.merchant" placeholder="请输入商家名称" />
        </el-form-item>
        <el-form-item label="结算金额">
          <el-input-number v-model="editForm.amount" :min="0" :step="100" :precision="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="待确认" value="待确认" />
            <el-option label="已确认" value="已确认" />
            <el-option label="已结算" value="已结算" />
          </el-select>
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

const pageTitle = '结算管理'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  settleNo: '',
  period: '',
  merchant: '',
  amount: 0,
  status: '待确认'
})

const mockData = [
  { id: 1, settleNo: 'ST-20260701-001', period: '2026-07上', merchant: '全球购旗舰店', amount: 128500.00, status: '已结算' },
  { id: 2, settleNo: 'ST-20260701-002', period: '2026-07上', merchant: '保健品专营店', amount: 86200.50, status: '已确认' },
  { id: 3, settleNo: 'ST-20260701-003', period: '2026-07上', merchant: '美妆海外店', amount: 54300.00, status: '待确认' },
  { id: 4, settleNo: 'ST-20260702-001', period: '2026-07下', merchant: '全球购旗舰店', amount: 156800.00, status: '已结算' },
  { id: 5, settleNo: 'ST-20260702-002', period: '2026-07下', merchant: '母婴用品店', amount: 42100.00, status: '已确认' },
  { id: 6, settleNo: 'ST-20260702-003', period: '2026-07下', merchant: '家居生活馆', amount: 32500.00, status: '待确认' }
]

function loadData() {
  let filtered = [...mockData]
  if (filters.keyword) {
    filtered = filtered.filter(item => item.settleNo.includes(filters.keyword) || item.merchant.includes(filters.keyword))
  }
  tableData.value = filtered
  total.value = filtered.length
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建结算单'; editForm.settleNo = ''; editForm.period = ''; editForm.merchant = ''; editForm.amount = 0; editForm.status = '待确认'; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑结算单'; Object.assign(editForm, row); dialogVisible.value = true }
function handleDelete(row) { ElMessageBox.confirm('确定删除？','提示').then(() => { ElMessage.success('删除成功'); loadData() }) }
function handleSave() { ElMessage.success('保存成功'); dialogVisible.value = false; loadData() }
onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
