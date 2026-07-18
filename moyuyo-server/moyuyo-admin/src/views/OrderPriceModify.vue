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
import { getOrderOpsExport, updateOrderRemark } from '../api/admin'

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

const tableData = ref([])

// 加载改价记录列表
async function loadData() {
  try {
    const res = await getOrderOpsExport()
    let list = res || []
    const kw = filters.keyword.toLowerCase()
    if (kw) {
      list = list.filter(d => (d.orderNo || '').toLowerCase().includes(kw))
    }
    total.value = list.length
    const start = (currentPage.value - 1) * pageSize.value
    tableData.value = list.slice(start, start + pageSize.value)
  } catch (error) {
    console.error('获取改价数据失败:', error)
    ElMessage.error('获取改价数据失败')
  }
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

// 保存改价信息
async function handleSave() {
  if (!editForm.orderNo || !editForm.reason) {
    ElMessage.warning('请填写必要信息')
    return
  }
  try {
    await updateOrderRemark(editForm.id, {
      orderNo: editForm.orderNo,
      originalAmount: editForm.originalAmount,
      adjustAmount: editForm.adjustAmount,
      finalAmount: editForm.originalAmount + editForm.adjustAmount,
      reason: editForm.reason
    })
    dialogVisible.value = false
    loadData()
    ElMessage.success('改价成功')
  } catch (error) {
    console.error('改价失败:', error)
    ElMessage.error('改价失败')
  }
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
