<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>订单导出</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建导出任务</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="订单编号">
          <el-input v-model="filters.keyword" placeholder="请输入订单编号" clearable />
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item label="导出状态">
          <el-select v-model="filters.exportStatus" placeholder="全部" clearable style="width:140px">
            <el-option label="全部" value="" />
            <el-option label="进行中" value="进行中" />
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
        <el-table-column prop="taskName" label="导出任务名称" min-width="180" />
        <el-table-column prop="orderScope" label="订单范围" min-width="160" />
        <el-table-column prop="format" label="导出格式" width="100" />
        <el-table-column prop="exportStatus" label="导出状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.exportStatus)" size="small">{{ row.exportStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="handleDownload(row)" :disabled="row.exportStatus !== '已完成'">下载</el-button>
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
    <el-dialog v-model="dialogVisible" title="新建导出任务" width="600px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="editForm.taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="订单范围">
          <el-select v-model="editForm.orderScope" style="width:100%">
            <el-option label="全部订单" value="全部订单" />
            <el-option label="本月订单" value="本月订单" />
            <el-option label="上周订单" value="上周订单" />
            <el-option label="自定义" value="自定义" />
          </el-select>
        </el-form-item>
        <el-form-item label="导出格式">
          <el-radio-group v-model="editForm.format">
            <el-radio value="Excel">Excel</el-radio>
            <el-radio value="CSV">CSV</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const dateRange = ref(null)

const filters = reactive({
  keyword: '',
  exportStatus: ''
})

const editForm = reactive({
  taskName: '',
  orderScope: '全部订单',
  format: 'Excel'
})

const mockData = [
  { id: 1, taskName: '618订单数据导出', orderScope: '2026-06-01 ~ 2026-06-18', format: 'Excel', exportStatus: '已完成', createTime: '2026-07-01 10:30:00' },
  { id: 2, taskName: '7月首周订单报表', orderScope: '2026-07-01 ~ 2026-07-07', format: 'CSV', exportStatus: '已完成', createTime: '2026-07-08 09:00:00' },
  { id: 3, taskName: '异常订单导出', orderScope: '2026-07-01 ~ 2026-07-15', format: 'Excel', exportStatus: '进行中', createTime: '2026-07-15 14:20:00' },
  { id: 4, taskName: '退款订单明细', orderScope: '2026-06-01 ~ 2026-07-01', format: 'Excel', exportStatus: '失败', createTime: '2026-07-02 11:15:00' },
  { id: 5, taskName: '昨日订单快照', orderScope: '2026-07-16', format: 'CSV', exportStatus: '已完成', createTime: '2026-07-17 00:30:00' }
]

const tableData = ref([])

function statusTag(status) {
  const map = { '进行中': 'warning', '已完成': 'success', '失败': 'danger' }
  return map[status] || ''
}

function loadData() {
  const start = (currentPage.value - 1) * pageSize.value
  const list = mockData.filter(d => {
    const kw = filters.keyword.toLowerCase()
    if (kw && !d.taskName.toLowerCase().includes(kw)) return false
    if (filters.exportStatus && d.exportStatus !== filters.exportStatus) return false
    return true
  })
  total.value = list.length
  tableData.value = list.slice(start, start + pageSize.value)
}

function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; filters.exportStatus = ''; dateRange.value = null; handleSearch() }

function handleAdd() {
  editForm.taskName = ''
  editForm.orderScope = '全部订单'
  editForm.format = 'Excel'
  dialogVisible.value = true
}

function handleSave() {
  if (!editForm.taskName) {
    ElMessage.warning('请输入任务名称')
    return
  }
  const newId = Math.max(...mockData.map(d => d.id)) + 1
  mockData.push({
    id: newId,
    taskName: editForm.taskName,
    orderScope: editForm.orderScope,
    format: editForm.format,
    exportStatus: '进行中',
    createTime: new Date().toLocaleString('zh-CN', { hour12: false })
  })
  dialogVisible.value = false
  loadData()
  ElMessage.success('导出任务已创建')
}

function handleDownload(row) {
  ElMessage.success('文件下载中：' + row.taskName)
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
