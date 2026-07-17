<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建推送</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="推送任务">
          <el-input v-model="filters.taskName" placeholder="请输入推送任务名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="请选择" clearable style="width:120px">
            <el-option label="发送中" value="发送中" />
            <el-option label="已完成" value="已完成" />
            <el-option label="失败" value="失败" />
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
        <el-table-column prop="taskName" label="推送任务" width="180" />
        <el-table-column label="推送平台" width="130">
          <template #default="{ row }">
            <el-tag :type="row.platform === '全部' ? 'primary' : row.platform === 'iOS' ? 'info' : 'success'">{{ row.platform }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sendCount" label="发送数" width="80" />
        <el-table-column prop="reachCount" label="到达数" width="80" />
        <el-table-column prop="clickCount" label="点击数" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '已完成' ? 'success' : row.status === '失败' ? 'danger' : 'warning'">{{ row.status }}</el-tag>
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
        <el-form-item label="推送任务">
          <el-input v-model="editForm.taskName" placeholder="请输入推送任务名称" />
        </el-form-item>
        <el-form-item label="推送平台">
          <el-select v-model="editForm.platform">
            <el-option label="iOS" value="iOS" />
            <el-option label="Android" value="Android" />
            <el-option label="全部" value="全部" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="发送中" value="发送中" />
            <el-option label="已完成" value="已完成" />
            <el-option label="失败" value="失败" />
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

const pageTitle = '推送详情'
const filters = reactive({ taskName: '', status: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  taskName: '',
  platform: '全部',
  status: '发送中'
})

const mockData = [
  { id: 1, taskName: '618大促活动推送', platform: '全部', sendCount: 50000, reachCount: 48500, clickCount: 12580, status: '已完成' },
  { id: 2, taskName: '新用户注册提醒', platform: 'iOS', sendCount: 12000, reachCount: 11500, clickCount: 3200, status: '已完成' },
  { id: 3, taskName: '订单物流通知', platform: 'Android', sendCount: 8000, reachCount: 7800, clickCount: 2100, status: '发送中' },
  { id: 4, taskName: '优惠券到期提醒', platform: '全部', sendCount: 35000, reachCount: 34200, clickCount: 5600, status: '已完成' },
  { id: 5, taskName: '直播开播提醒', platform: 'iOS', sendCount: 15000, reachCount: 0, clickCount: 0, status: '失败' },
  { id: 6, taskName: '商品降价通知', platform: 'Android', sendCount: 22000, reachCount: 21500, clickCount: 4800, status: '发送中' }
]

function loadData() {
  let filtered = [...mockData]
  if (filters.taskName) {
    filtered = filtered.filter(item => item.taskName.includes(filters.taskName))
  }
  if (filters.status) {
    filtered = filtered.filter(item => item.status === filters.status)
  }
  tableData.value = filtered
  total.value = filtered.length
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.taskName = ''; filters.status = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建推送'; editForm.taskName = ''; editForm.platform = '全部'; editForm.status = '发送中'; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑推送'; Object.assign(editForm, row); dialogVisible.value = true }
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
