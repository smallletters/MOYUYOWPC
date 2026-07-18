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
import { getReviewList, approveReview, rejectReview } from '../api/admin'

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedIds = ref([])

const filters = reactive({
  keyword: '',
  auditStatus: ''
})

const tableData = ref([])

function auditTag(status) {
  const map = { '待审核': 'warning', '已通过': 'success', '已驳回': 'danger' }
  return map[status] || ''
}

// 加载商品评价列表
async function loadData() {
  try {
    const res = await getReviewList()
    const records = res.records || res || []
      // 客户端筛选
      let list = [...records]
      const kw = filters.keyword.toLowerCase()
      if (kw) {
        list = list.filter(d =>
          (d.productName || '').toLowerCase().includes(kw) ||
          (d.userName || '').includes(kw)
        )
      }
      if (filters.auditStatus) {
        list = list.filter(d => d.auditStatus === filters.auditStatus)
      }
      total.value = list.length
      const start = (currentPage.value - 1) * pageSize.value
      tableData.value = list.slice(start, start + pageSize.value)
    }
  } catch (e) {
    ElMessage.error('获取评价列表失败')
  }
}

function handleSearch() { currentPage.value = 1; loadData() }

function handleReset() { filters.keyword = ''; filters.auditStatus = ''; handleSearch() }

function handleSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

async function handlePass(row) {
  try {
    await approveReview(row.id)
    ElMessage.success('审核通过')
    await loadData()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function handleReject(row) {
  try {
    await ElMessageBox.confirm('确认驳回该评价吗？', '提示', { type: 'warning' })
    await rejectReview(row.id)
    ElMessage.success('已驳回')
    await loadData()
  } catch (e) {
    // 用户取消不处理
  }
}

async function handleBatchPass() {
  const count = selectedIds.value.length
  try {
    // 批量通过选中的评价
    for (const id of selectedIds.value) {
      await approveReview(id)
    }
    ElMessage.success('批量通过 ' + count + ' 条评价')
    await loadData()
  } catch (e) {
    ElMessage.error('批量操作失败')
  }
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
