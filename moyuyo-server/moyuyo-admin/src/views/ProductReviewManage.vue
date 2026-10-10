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
            <el-option label="待审核" value="PENDING" />
            <el-option label="已审核" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
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
        <el-table-column label="图片" width="120">
          <template #default="{ row }">
            <div v-if="row.images && row.images.length" class="thumb-list">
              <el-image
                v-for="(img, idx) in row.images.slice(0, 3)"
                :key="idx"
                :src="img"
                :preview-src-list="row.images"
                :initial-index="idx"
                fit="cover"
                class="thumb-image"
              >
                <template #error>
                  <div class="image-error">!</div>
                </template>
              </el-image>
              <span v-if="row.images.length > 3" class="thumb-more">+{{ row.images.length - 3 }}</span>
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="审核状态" width="110">
          <template #default="{ row }">
            <el-tag :type="auditTag(row.status)" size="small">{{ auditLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="handlePass(row)" :disabled="row.status !== 'PENDING'">通过</el-button>
            <el-button size="small" type="danger" @click="handleReject(row)" :disabled="row.status !== 'PENDING'">驳回</el-button>
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
import { getReviewList, approveReview, rejectReview, batchApproveReview } from '../api/admin'
import { toArray } from '../utils/safeArray'

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
  const map = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
  return map[status] || ''
}

function auditLabel(status) {
  const map = { PENDING: '待审核', APPROVED: '已审核', REJECTED: '已驳回' }
  return map[status] || status || '-'
}

// 加载商品评价列表
async function loadData() {
  try {
    // 后端按 status 精确过滤并真分页,客户端仅做 keyword 模糊匹配。
    // total 必须取后端真实总数,客户端 keyword 过滤不影响 total,保证分页器正确显示"共 N 条"。
    const res = await getReviewList({
      page: currentPage.value,
      size: pageSize.value,
      status: filters.auditStatus || undefined,
    })
    const records = toArray(res)
    total.value = Number(res?.total) || records.length
    let list = [...records]
    const kw = filters.keyword.toLowerCase()
    if (kw) {
      list = list.filter(d =>
        (d.productName || '').toLowerCase().includes(kw) ||
        (d.userName || '').includes(kw)
      )
    }
    tableData.value = list
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
  if (count === 0) {
    return
  }
  try {
    // 单次请求完成批量审核，避免 N 次串行 PUT。已审结的评价会被后端自动忽略。
    const res = await batchApproveReview(selectedIds.value)
    const approved = Number(res?.approved ?? count)
    ElMessage.success('批量通过 ' + approved + ' 条评价')
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

/* 评价图片缩略图（与 ReviewManage.vue 保持一致） */
.thumb-list { display: flex; align-items: center; gap: 6px; }
.thumb-image { width: 40px; height: 40px; border-radius: 4px; border: 1px solid var(--border); cursor: pointer; }
/* 强制内部 <img> 撑满,避免 fit cover 时只显示一角 */
.thumb-image :deep(.el-image__inner) { width: 100%; height: 100%; }
.thumb-more { font-size: 12px; color: var(--text-400); }
.image-error { display: flex; align-items: center; justify-content: center; width: 100%; height: 100%; font-size: 12px; color: var(--text-400); background: var(--bg-50); }
.text-muted { color: var(--text-400); }
</style>
