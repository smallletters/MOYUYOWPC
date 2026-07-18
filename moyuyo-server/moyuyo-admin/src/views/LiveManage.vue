<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建直播</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入直播标题/主播" clearable />
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
        <el-table-column prop="title" label="直播标题" width="200" show-overflow-tooltip />
        <el-table-column prop="anchor" label="主播" width="100" />
        <el-table-column label="直播状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === '直播中' ? 'danger' : row.status === '预告中' ? 'warning' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="观看人数" width="100" />
        <el-table-column prop="productCount" label="商品数" width="80" />
        <el-table-column prop="startTime" label="开始时间" width="160" />
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
        <el-form-item label="直播标题">
          <el-input v-model="editForm.title" placeholder="请输入直播标题" />
        </el-form-item>
        <el-form-item label="主播">
          <el-input v-model="editForm.anchor" placeholder="请输入主播名称" />
        </el-form-item>
        <el-form-item label="直播状态">
          <el-select v-model="editForm.status">
            <el-option label="预告中" value="预告中" />
            <el-option label="直播中" value="直播中" />
            <el-option label="已结束" value="已结束" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品数">
          <el-input-number v-model="editForm.productCount" :min="0" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="editForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm" placeholder="选择开始时间" />
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
import { getLiveRooms, createLiveRoom, updateLiveRoom, deleteLiveRoom } from '../api/admin'

const pageTitle = '直播管理'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  title: '',
  anchor: '',
  status: '预告中',
  productCount: 0,
  startTime: ''
})

// 原始直播数据（用于本地筛选）
const allRooms = ref([])

// 加载直播列表
async function loadRooms() {
  try {
    const data = await getLiveRooms()
    allRooms.value = (data || []).map(item => ({
      id: item.id,
      title: item.title || '',
      anchor: item.anchor || '',
      status: item.status || '预告中',
      viewCount: item.viewCount ?? 0,
      productCount: item.productCount ?? 0,
      startTime: item.startTime || ''
    }))
    applyFilters()
  } catch (e) {
    console.error('获取直播列表失败', e)
  }
}

// 应用关键词筛选
function applyFilters() {
  let filtered = [...allRooms.value]
  if (filters.keyword) {
    filtered = filtered.filter(item =>
      item.title.includes(filters.keyword) || item.anchor.includes(filters.keyword)
    )
  }
  tableData.value = filtered
  total.value = filtered.length
}

function loadData() {
  applyFilters()
}

function handleSearch() { currentPage.value = 1; applyFilters() }
function handleReset() { filters.keyword = ''; handleSearch() }

function handleAdd() {
  dialogTitle.value = '新建直播'
  editForm.title = ''
  editForm.anchor = ''
  editForm.status = '预告中'
  editForm.productCount = 0
  editForm.startTime = ''
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑直播'
  Object.assign(editForm, row)
  dialogVisible.value = true
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deleteLiveRoom(row.id)
    ElMessage.success('删除成功')
    await loadRooms()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}

async function handleSave() {
  try {
    if (editForm.id) {
      await updateLiveRoom(editForm.id, {
        title: editForm.title,
        anchor: editForm.anchor,
        status: editForm.status,
        productCount: editForm.productCount,
        startTime: editForm.startTime
      })
    } else {
      await createLiveRoom({
        title: editForm.title,
        anchor: editForm.anchor,
        status: editForm.status,
        productCount: editForm.productCount,
        startTime: editForm.startTime
      })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadRooms()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

onMounted(() => loadRooms())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
