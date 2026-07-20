<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>库存调拨</h2>
      <div class="header-actions">
        <el-select v-model="statusFilter" placeholder="状态" clearable style="width:130px" @change="loadData">
          <el-option label="待审核" value="pending" />
          <el-option label="已通过" value="approved" />
          <el-option label="已完成" value="completed" />
          <el-option label="已驳回" value="rejected" />
        </el-select>
        <el-button type="primary" @click="handleAdd">新建调拨</el-button>
      </div>
    </div>
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="productName" label="商品" min-width="140" show-overflow-tooltip />
        <el-table-column prop="fromWarehouse" label="调出仓库" width="140" show-overflow-tooltip />
        <el-table-column prop="toWarehouse" label="调入仓库" width="140" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusMap(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'pending'" type="success" link size="small" @click="handleApprove(row)">审批通过</el-button>
            <el-button v-if="row.status === 'pending'" type="warning" link size="small" @click="handleReject(row)">驳回</el-button>
            <el-button v-if="row.status === 'approved'" type="primary" link size="small" @click="handleComplete(row)">完成</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <!-- 新建对话框 -->
    <el-dialog v-model="dialogVisible" title="新建调拨" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="商品ID" required>
          <el-input-number v-model="form.productId" :min="1" />
        </el-form-item>
        <el-form-item label="调出仓库" required>
          <el-input v-model="form.fromWarehouse" placeholder="调出仓库名称" />
        </el-form-item>
        <el-form-item label="调入仓库" required>
          <el-input v-model="form.toWarehouse" placeholder="调入仓库名称" />
        </el-form-item>
        <el-form-item label="数量" required>
          <el-input-number v-model="form.quantity" :min="1" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="调拨原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getInventoryTransferList, createInventoryTransfer, approveInventoryTransfer, rejectInventoryTransfer, completeInventoryTransfer } from '../api/admin'

const tableData = ref([])
const statusFilter = ref('')
const dialogVisible = ref(false)

const form = reactive({
  productId: null,
  fromWarehouse: '',
  toWarehouse: '',
  quantity: 0,
  reason: '',
})

function statusTag(status) {
  if (status === 'approved') return 'success'
  if (status === 'completed') return 'primary'
  if (status === 'rejected') return 'danger'
  return 'warning'
}

function statusMap(status) {
  if (status === 'pending') return '待审核'
  if (status === 'approved') return '已通过'
  if (status === 'completed') return '已完成'
  if (status === 'rejected') return '已驳回'
  return status
}

function resetForm() {
  form.productId = null
  form.fromWarehouse = ''
  form.toWarehouse = ''
  form.quantity = 0
  form.reason = ''
}

async function loadData() {
  try {
    const params = {}
    if (statusFilter.value) params.status = statusFilter.value
    const res = await getInventoryTransferList(params)
    tableData.value = res.records || res || []
  } catch (e) {
    ElMessage.error('获取调拨列表失败')
  }
}

function handleAdd() {
  resetForm()
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.productId || !form.fromWarehouse || !form.toWarehouse || !form.quantity) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    await createInventoryTransfer({ ...form })
    ElMessage.success('调拨申请已提交')
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('提交失败')
  }
}

async function handleApprove(row) {
  try {
    await ElMessageBox.confirm('确认通过该调拨申请吗？', '提示')
    await approveInventoryTransfer(row.id)
    ElMessage.success('已审批通过')
    await loadData()
  } catch (e) {
    // 用户取消不处理
  }
}

async function handleReject(row) {
  try {
    await ElMessageBox.confirm('确认驳回该调拨申请吗？', '提示')
    await rejectInventoryTransfer(row.id, { reason: '管理员驳回' })
    ElMessage.success('已驳回')
    await loadData()
  } catch (e) {
    // 用户取消不处理
  }
}

async function handleComplete(row) {
  try {
    await ElMessageBox.confirm('确认完成该调拨吗？', '提示')
    await completeInventoryTransfer(row.id)
    ElMessage.success('调拨已完成')
    await loadData()
  } catch (e) {
    // 用户取消不处理
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }
</style>
