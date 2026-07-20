<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>黑名单管理</h2>
      <div class="header-actions">
        <el-select v-model="typeFilter" placeholder="拉黑类型" clearable style="width:140px" @change="loadData">
          <el-option label="用户" value="user" />
          <el-option label="IP" value="ip" />
          <el-option label="设备" value="device" />
          <el-option label="地址" value="address" />
        </el-select>
        <el-button type="primary" @click="handleAdd">新建</el-button>
      </div>
    </div>
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" size="small">{{ typeMap(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="value" label="值" min-width="180" show-overflow-tooltip />
        <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="操作人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column prop="expireAt" label="过期时间" width="170">
          <template #default="{ row }">
            {{ row.expireAt || '永久' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑黑名单' : '新建黑名单'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="类型" required>
          <el-select v-model="form.type" placeholder="选择类型">
            <el-option label="用户" value="user" />
            <el-option label="IP" value="ip" />
            <el-option label="设备" value="device" />
            <el-option label="地址" value="address" />
          </el-select>
        </el-form-item>
        <el-form-item label="值" required>
          <el-input v-model="form.value" :placeholder="valuePlaceholder" />
        </el-form-item>
        <el-form-item label="原因" required>
          <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="请输入拉黑原因" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker v-model="form.expireAt" type="datetime" placeholder="留空为永久" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBlacklistList, createBlacklist, updateBlacklist, deleteBlacklist } from '../api/admin'

const tableData = ref([])
const typeFilter = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)

const form = reactive({
  type: 'user',
  value: '',
  reason: '',
  expireAt: null,
})

const valuePlaceholder = computed(() => {
  const map = { user: '用户ID', ip: 'IP地址', device: '设备ID', address: '收货地址' }
  return '请输入' + (map[form.type] || '')
})

function typeTag(type) {
  const map = { user: 'danger', ip: 'warning', device: 'primary', address: 'info' }
  return map[type] || 'info'
}

function typeMap(type) {
  const map = { user: '用户', ip: 'IP', device: '设备', address: '地址' }
  return map[type] || type
}

function resetForm() {
  form.type = 'user'
  form.value = ''
  form.reason = ''
  form.expireAt = null
}

async function loadData() {
  try {
    const params = {}
    if (typeFilter.value) params.type = typeFilter.value
    const res = await getBlacklistList(params)
    tableData.value = res.records || res || []
  } catch (e) {
    ElMessage.error('获取黑名单列表失败')
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
  form.type = row.type
  form.value = row.value
  form.reason = row.reason
  form.expireAt = row.expireAt
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value || !form.reason) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    if (isEdit.value) {
      await updateBlacklist(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createBlacklist({ ...form })
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
    await ElMessageBox.confirm('确定移出黑名单吗？', '提示')
    await deleteBlacklist(row.id)
    ElMessage.success('已删除')
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
