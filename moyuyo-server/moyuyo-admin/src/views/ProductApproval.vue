<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>商品审核</h2>
      <div class="header-actions">
        <el-select v-model="statusFilter" placeholder="审核状态" clearable style="width:140px" @change="loadData">
          <el-option label="待审核" value="pending" />
          <el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" />
        </el-select>
      </div>
    </div>
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="productId" label="商品ID" width="120" />
        <el-table-column prop="productName" label="商品名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.type === 'new' ? '新品上架' : '信息修改' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusMap(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitTime" label="提交时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'pending'" type="success" link size="small" @click="handleApprove(row)">通过</el-button>
            <el-button v-if="row.status === 'pending'" type="warning" link size="small" @click="handleReject(row)">驳回</el-button>
            <el-button v-if="row.status === 'pending'" type="primary" link size="small" @click="handleUrgent(row)">加急</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectDialogVisible" title="驳回原因" width="400px">
      <el-input v-model="rejectReason" type="textarea" :rows="4" placeholder="请输入驳回原因" />
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProductApprovalList, approveProductApproval, rejectProductApproval, setProductApprovalUrgent } from '../api/admin'

const tableData = ref([])
const statusFilter = ref('')
const rejectDialogVisible = ref(false)
const rejectReason = ref('')
const currentRejectId = ref(null)

function statusTag(status) {
  if (status === 'approved') return 'success'
  if (status === 'rejected') return 'danger'
  return 'warning'
}

function statusMap(status) {
  if (status === 'approved') return '已通过'
  if (status === 'rejected') return '已驳回'
  return '待审核'
}

async function loadData() {
  try {
    const params = {}
    if (statusFilter.value) params.status = statusFilter.value
    const res = await getProductApprovalList(params)
    tableData.value = res.records || res || []
  } catch (e) {
    ElMessage.error('获取审核列表失败')
  }
}

async function handleApprove(row) {
  try {
    await ElMessageBox.confirm('确认通过该商品审核吗？', '提示')
    await approveProductApproval(row.id)
    ElMessage.success('已通过')
    await loadData()
  } catch (e) {
    // 用户取消不处理
  }
}

function handleReject(row) {
  currentRejectId.value = row.id
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

async function confirmReject() {
  if (!rejectReason.value) {
    ElMessage.warning('请输入驳回原因')
    return
  }
  try {
    await rejectProductApproval(currentRejectId.value, { reason: rejectReason.value })
    ElMessage.success('已驳回')
    rejectDialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('驳回失败')
  }
}

async function handleUrgent(row) {
  try {
    await ElMessageBox.confirm('确认将该审核标记为加急吗？', '提示')
    await setProductApprovalUrgent(row.id)
    ElMessage.success('已标记加急')
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
