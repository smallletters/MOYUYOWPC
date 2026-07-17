<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>商品评价审核</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleBatchPass" :disabled="selectedIds.length === 0">批量通过</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="商品名称/评价用户" clearable />
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="filters.auditStatus" placeholder="全部" clearable style="width:140px">
            <el-option label="全部" value="" />
            <el-option label="待审核" value="待审核" />
            <el-option label="已通过" value="已通过" />
            <el-option label="已驳回" value="已驳回" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <el-table :data="tableData" stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="productName" label="商品名称" min-width="160" />
        <el-table-column prop="userName" label="评价用户" width="120" />
        <el-table-column prop="rating" label="评分" width="80">
          <template #default="{ row }">
            <span :style="{ color: '#f59e0b' }">{{ row.rating }}星</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="评价内容" min-width="220" show-overflow-tooltip />
        <el-table-column prop="auditStatus" label="审核状态" width="110">
          <template #default="{ row }">
            <el-tag :type="auditTag(row.auditStatus)" size="small">{{ row.auditStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitTime" label="提交时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="handlePass(row)" :disabled="row.auditStatus !== '待审核'">通过</el-button>
            <el-button size="small" type="danger" @click="handleReject(row)" :disabled="row.auditStatus !== '待审核'">驳回</el-button>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedIds = ref([])

const filters = reactive({
  keyword: '',
  auditStatus: ''
})

const mockData = [
  { id: 1, productName: '无线蓝牙耳机 Pro', userName: '张三', rating: 5, content: '音质非常好，佩戴舒适，续航也很给力，推荐购买！', auditStatus: '已通过', submitTime: '2026-07-10 14:30:00' },
  { id: 2, productName: '智能手表S3', userName: '李四', rating: 3, content: '功能丰富但续航有待提升，希望后续固件优化', auditStatus: '待审核', submitTime: '2026-07-11 09:20:00' },
  { id: 3, productName: '运动休闲鞋', userName: '王五', rating: 4, content: '鞋子穿起来很舒适，码数标准，推荐购买', auditStatus: '已通过', submitTime: '2026-07-12 16:45:00' },
  { id: 4, productName: '不锈钢保温杯', userName: '赵六', rating: 2, content: '保温效果一般，外观有轻微划痕，不太满意', auditStatus: '待审核', submitTime: '2026-07-13 11:10:00' },
  { id: 5, productName: '轻薄笔记本电脑包', userName: '孙七', rating: 5, content: '包包很轻便，质量很好，出差用非常合适', auditStatus: '已驳回', submitTime: '2026-07-14 08:30:00' }
]

const tableData = ref([])

function auditTag(status) {
  const map = { '待审核': 'warning', '已通过': 'success', '已驳回': 'danger' }
  return map[status] || ''
}

function loadData() {
  const start = (currentPage.value - 1) * pageSize.value
  const list = mockData.filter(d => {
    const kw = filters.keyword.toLowerCase()
    if (kw && !d.productName.toLowerCase().includes(kw) && !d.userName.includes(kw)) return false
    if (filters.auditStatus && d.auditStatus !== filters.auditStatus) return false
    return true
  })
  total.value = list.length
  tableData.value = list.slice(start, start + pageSize.value)
}

function handleSearch() { currentPage.value = 1; loadData() }

function handleReset() { filters.keyword = ''; filters.auditStatus = ''; handleSearch() }

function handleSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

function handlePass(row) {
  row.auditStatus = '已通过'
  loadData()
  ElMessage.success('审核通过')
}

function handleReject(row) {
  ElMessageBox.confirm('确认驳回该评价吗？', '提示', { type: 'warning' }).then(() => {
    row.auditStatus = '已驳回'
    loadData()
    ElMessage.success('已驳回')
  }).catch(() => {})
}

function handleBatchPass() {
  const count = selectedIds.value.length
  mockData.forEach(d => {
    if (selectedIds.value.includes(d.id) && d.auditStatus === '待审核') {
      d.auditStatus = '已通过'
    }
  })
  loadData()
  ElMessage.success('批量通过 ' + count + ' 条评价')
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
