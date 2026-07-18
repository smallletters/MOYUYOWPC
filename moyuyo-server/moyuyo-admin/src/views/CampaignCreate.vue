<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>活动创建</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建活动</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入活动名称" clearable />
        </el-form-item>
        <el-form-item label="活动类型">
          <el-select v-model="filters.type" placeholder="全部类型" clearable style="width:140px">
            <el-option label="全部" value="" />
            <el-option label="满减" value="满减" />
            <el-option label="折扣" value="折扣" />
            <el-option label="秒杀" value="秒杀" />
            <el-option label="拼团" value="拼团" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width:140px">
            <el-option label="全部" value="" />
            <el-option label="未开始" value="未开始" />
            <el-option label="进行中" value="进行中" />
            <el-option label="已结束" value="已结束" />
          </el-select>
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
        <el-table-column prop="name" label="活动名称" min-width="160" />
        <el-table-column prop="type" label="活动类型" width="100">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" size="small">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="170" />
        <el-table-column prop="endTime" label="结束时间" width="170" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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
        <el-form-item label="活动名称" required>
          <el-input v-model="editForm.name" placeholder="请输入活动名称" />
        </el-form-item>
        <el-form-item label="活动类型" required>
          <el-select v-model="editForm.type" placeholder="请选择" style="width:100%">
            <el-option label="满减" value="满减" />
            <el-option label="折扣" value="折扣" />
            <el-option label="秒杀" value="秒杀" />
            <el-option label="拼团" value="拼团" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围" required>
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="活动描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="请输入活动描述" />
        </el-form-item>
        <el-form-item label="活动预算">
          <el-input-number v-model="editForm.budget" :min="0" :precision="2" style="width:200px" />
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
import { getCampaigns, createCampaign, updateCampaign, deleteCampaign } from '../api/admin'

const pageTitle = '活动创建'
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const dateRange = ref(null)

const filters = reactive({
  keyword: '',
  type: '',
  status: ''
})

const editForm = reactive({
  id: null,
  name: '',
  type: '',
  description: '',
  budget: 0
})

// 活动数据列表（通过API获取）
const allCampaigns = ref([])
const tableData = ref([])

function typeTag(type) {
  const map = { '满减': 'success', '折扣': 'warning', '秒杀': 'danger', '拼团': 'primary' }
  return map[type] || ''
}

function statusTag(status) {
  const map = { '未开始': 'info', '进行中': 'success', '已结束': 'danger' }
  return map[status] || ''
}

// 从API加载活动列表
async function loadCampaigns() {
  try {
    const data = await getCampaigns()
    allCampaigns.value = (data || []).map(item => ({
      id: item.id,
      name: item.name || '',
      type: item.type || '',
      startTime: item.startTime || '',
      endTime: item.endTime || '',
      status: item.status || '未开始',
      description: item.description || '',
      budget: item.budget ?? 0
    }))
    applyFilters()
  } catch (e) {
    console.error('获取活动列表失败', e)
  }
}

// 根据筛选条件过滤并分页
function applyFilters() {
  const kw = filters.keyword.toLowerCase()
  const filtered = allCampaigns.value.filter(d => {
    if (kw && !d.name.toLowerCase().includes(kw)) return false
    if (filters.type && d.type !== filters.type) return false
    if (filters.status && d.status !== filters.status) return false
    return true
  })
  total.value = filtered.length
  const start = (currentPage.value - 1) * pageSize.value
  tableData.value = filtered.slice(start, start + pageSize.value)
}

function loadData() {
  applyFilters()
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  filters.keyword = ''
  filters.type = ''
  filters.status = ''
  handleSearch()
}

function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '新建活动'
  editForm.id = null
  editForm.name = ''
  editForm.type = ''
  editForm.description = ''
  editForm.budget = 0
  dateRange.value = null
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  dialogTitle.value = '编辑活动'
  Object.assign(editForm, row)
  dateRange.value = [new Date(row.startTime), new Date(row.endTime)]
  dialogVisible.value = true
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确认删除该活动吗？', '提示', { type: 'warning' })
    await deleteCampaign(row.id)
    ElMessage.success('删除成功')
    await loadCampaigns()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}

async function handleSave() {
  if (!editForm.name || !editForm.type) {
    ElMessage.warning('请填写必要信息')
    return
  }
  try {
    if (isEdit.value) {
      await updateCampaign(editForm.id, {
        name: editForm.name,
        type: editForm.type,
        description: editForm.description,
        budget: editForm.budget,
        startTime: dateRange.value ? dateRange.value[0].toISOString() : '',
        endTime: dateRange.value ? dateRange.value[1].toISOString() : ''
      })
      ElMessage.success('编辑成功')
    } else {
      const created = await createCampaign({
        name: editForm.name,
        type: editForm.type,
        description: editForm.description,
        budget: editForm.budget,
        startTime: dateRange.value ? dateRange.value[0].toISOString() : '',
        endTime: dateRange.value ? dateRange.value[1].toISOString() : ''
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadCampaigns()
  } catch (e) {
    ElMessage.error('保存失败，请重试')
  }
}

onMounted(() => { loadCampaigns() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
