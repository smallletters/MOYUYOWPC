<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>风控告警</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建告警配置</el-button>
        <el-button @click="showHistory = !showHistory">{{ showHistory ? '查看配置' : '告警历史' }}</el-button>
      </div>
    </div>
    <!-- 告警配置列表 -->
    <el-card v-if="!showHistory" shadow="never">
      <el-table :data="configData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="告警名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="type" label="规则类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="threshold" label="阈值" width="100" />
        <el-table-column prop="notifyChannels" label="通知方式" width="120" />
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <!-- 告警历史 -->
    <el-card v-else shadow="never">
      <el-table :data="historyData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="type" label="规则类型" width="110" />
        <el-table-column prop="message" label="告警信息" min-width="220" show-overflow-tooltip />
        <el-table-column prop="level" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="触发时间" width="170" />
      </el-table>
    </el-card>
    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑告警配置' : '新建告警配置'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="告警名称" required>
          <el-input v-model="form.name" placeholder="告警名称" />
        </el-form-item>
        <el-form-item label="规则类型" required>
          <el-input v-model="form.type" placeholder="如：order_failure_rate" />
        </el-form-item>
        <el-form-item label="阈值" required>
          <el-input-number v-model="form.threshold" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="通知方式">
          <el-input v-model="form.notifyChannels" placeholder="如：email,sms" />
        </el-form-item>
        <el-form-item label="通知用户">
          <el-input v-model="form.notifyUsers" placeholder="用户ID，逗号分隔" />
        </el-form-item>
        <el-form-item v-if="isEdit" label="启用">
          <el-switch v-model="form.enabled" />
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
import { getRiskAlertConfigs, createRiskAlertConfig, updateRiskAlertConfig, deleteRiskAlertConfig, getRiskAlertHistory } from '../api/admin'

const configData = ref([])
const historyData = ref([])
const showHistory = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)

const form = reactive({
  name: '',
  type: '',
  threshold: 0,
  notifyChannels: '',
  notifyUsers: '',
  enabled: true,
})

function levelTag(level) {
  if (level === 'critical' || level === '严重') return 'danger'
  if (level === 'warning' || level === '警告') return 'warning'
  return 'info'
}

function resetForm() {
  form.name = ''
  form.type = ''
  form.threshold = 0
  form.notifyChannels = ''
  form.notifyUsers = ''
  form.enabled = true
}

async function loadData() {
  try {
    const res = await getRiskAlertConfigs()
    configData.value = res.records || res || []
  } catch (e) {
    ElMessage.error('获取告警配置失败')
  }
}

async function loadHistory() {
  try {
    const res = await getRiskAlertHistory()
    historyData.value = res.list || res || []
  } catch (e) {
    ElMessage.error('获取告警历史失败')
  }
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  editId.value = row.id
  form.name = row.name
  form.type = row.type || ''
  form.threshold = row.threshold
  form.notifyChannels = row.notifyChannels || ''
  form.notifyUsers = row.notifyUsers || ''
  form.enabled = row.enabled !== undefined ? row.enabled : true
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name || !form.type) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    if (isEdit.value) {
      await updateRiskAlertConfig({ id: editId.value, ...form })
      ElMessage.success('编辑成功')
    } else {
      await createRiskAlertConfig({
        name: form.name,
        type: form.type,
        threshold: form.threshold,
        notifyChannels: form.notifyChannels,
        notifyUsers: form.notifyUsers,
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除告警配置「' + row.name + '」吗？', '提示')
    await deleteRiskAlertConfig(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch (e) {
    // 用户取消不处理
  }
}

onMounted(() => { loadData(); loadHistory() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }
</style>
