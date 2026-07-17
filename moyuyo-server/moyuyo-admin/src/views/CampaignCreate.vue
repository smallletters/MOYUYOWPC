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

const mockData = [
  { id: 1, name: '618年中大促', type: '满减', startTime: '2026-06-01 00:00:00', endTime: '2026-06-18 23:59:59', status: '已结束', description: '618全品类满减活动', budget: 500000 },
  { id: 2, name: '夏日清凉特惠', type: '折扣', startTime: '2026-07-01 00:00:00', endTime: '2026-07-31 23:59:59', status: '进行中', description: '夏季商品折扣促销', budget: 200000 },
  { id: 3, name: '限时秒杀专场', type: '秒杀', startTime: '2026-07-20 00:00:00', endTime: '2026-07-20 23:59:59', status: '未开始', description: '整点秒杀特价商品', budget: 100000 },
  { id: 4, name: '拼团狂欢节', type: '拼团', startTime: '2026-08-01 00:00:00', endTime: '2026-08-15 23:59:59', status: '未开始', description: '多人拼团享优惠', budget: 150000 },
  { id: 5, name: '开学季大促', type: '满减', startTime: '2026-08-20 00:00:00', endTime: '2026-09-10 23:59:59', status: '未开始', description: '开学季满减优惠活动', budget: 300000 }
]

const tableData = ref([])

function typeTag(type) {
  const map = { '满减': 'success', '折扣': 'warning', '秒杀': 'danger', '拼团': 'primary' }
  return map[type] || ''
}

function statusTag(status) {
  const map = { '未开始': 'info', '进行中': 'success', '已结束': 'danger' }
  return map[status] || ''
}

function loadData() {
  const start = (currentPage.value - 1) * pageSize.value
  const list = mockData.filter(d => {
    const kw = filters.keyword.toLowerCase()
    if (kw && !d.name.toLowerCase().includes(kw)) return false
    if (filters.type && d.type !== filters.type) return false
    if (filters.status && d.status !== filters.status) return false
    return true
  })
  total.value = list.length
  tableData.value = list.slice(start, start + pageSize.value)
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

function handleDelete(row) {
  ElMessageBox.confirm('确认删除该活动吗？', '提示', { type: 'warning' }).then(() => {
    const idx = mockData.findIndex(d => d.id === row.id)
    if (idx > -1) mockData.splice(idx, 1)
    loadData()
    ElMessage.success('删除成功')
  }).catch(() => {})
}

function handleSave() {
  if (!editForm.name || !editForm.type) {
    ElMessage.warning('请填写必要信息')
    return
  }
  if (isEdit.value) {
    const item = mockData.find(d => d.id === editForm.id)
    if (item) {
      Object.assign(item, editForm)
      if (dateRange.value) {
        item.startTime = dateRange.value[0].toLocaleString('zh-CN', { hour12: false })
        item.endTime = dateRange.value[1].toLocaleString('zh-CN', { hour12: false })
      }
    }
    ElMessage.success('编辑成功')
  } else {
    const newId = Math.max(...mockData.map(d => d.id)) + 1
    mockData.push({
      id: newId,
      name: editForm.name,
      type: editForm.type,
      startTime: dateRange.value ? dateRange.value[0].toLocaleString('zh-CN', { hour12: false }) : '',
      endTime: dateRange.value ? dateRange.value[1].toLocaleString('zh-CN', { hour12: false }) : '',
      status: '未开始',
      description: editForm.description,
      budget: editForm.budget
    })
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  loadData()
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
