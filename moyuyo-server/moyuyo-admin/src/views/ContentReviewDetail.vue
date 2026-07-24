<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="审核人">
          <el-input v-model="filters.reviewer" placeholder="请输入审核人" clearable />
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="filters.status" placeholder="请选择" clearable style="width:130px">
            <el-option label="待审核" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
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
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="内容标题" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.contentExcerpt || row.title }}
          </template>
        </el-table-column>
        <el-table-column label="内容类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.contentType === '评论' ? 'info' : row.contentType === '视频' ? 'warning' : 'primary'">{{ row.contentType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitter" label="提交人" width="100">
          <template #default="{ row }">
            {{ row.submitter || '用户' + row.userId }}
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.reviewStatus === 'APPROVED' ? 'success' : row.reviewStatus === 'REJECTED' ? 'danger' : 'warning'">{{ row.reviewStatus === 'APPROVED' ? '已通过' : row.reviewStatus === 'REJECTED' ? '已驳回' : '待审核' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitTime" label="提交时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="success" link size="small" :disabled="row.reviewStatus !== 'PENDING'" @click="handleApprove(row)">通过</el-button>
            <el-button type="danger" link size="small" :disabled="row.reviewStatus !== 'PENDING'" @click="handleReject(row)">驳回</el-button>
            <el-button type="primary" link size="small" @click="handleDetail(row)">详情</el-button>
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
import { getContentReviewList, approveContentReview, rejectContentReview } from '../api/admin'

const pageTitle = '内容审核详情'
const filters = reactive({ reviewer: '', status: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)

// 获取审核详情列表
async function loadData() {
  try {
    const res = await getContentReviewList()
    const records = res.records || res.list || res || []
      // 映射为前端需要的格式（状态值直接使用后端的 PENDING/APPROVED/REJECTED）
      let mapped = (Array.isArray(records) ? records : []).map(item => ({
        id: item.id,
        title: item.contentExcerpt || item.content || item.productName || '',
        contentExcerpt: item.contentExcerpt || '',
        contentType: item.contentType || (item.rating ? '评论' : '图文'),
        submitter: '用户' + (item.userId || ''),
        userId: item.userId,
        reviewStatus: item.status || 'PENDING',
        submitTime: item.reviewTime || item.createTime || ''
      }))
      // 客户端筛选
      if (filters.reviewer) {
        mapped = mapped.filter(item => item.submitter.includes(filters.reviewer))
      }
      if (filters.status) {
        mapped = mapped.filter(item => item.reviewStatus === filters.status)
      }
      tableData.value = mapped
      total.value = mapped.length
  } catch (e) {
    ElMessage.error('获取审核列表失败')
  }
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.reviewer = ''; filters.status = ''; handleSearch() }
async function handleApprove(row) {
  try {
    await approveContentReview(row.id)
    ElMessage.success('已通过：' + row.title)
    await loadData()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}
async function handleReject(row) {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入驳回原因', '驳回', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '请输入驳回原因...'
    })
    if (!reason) return
    await rejectContentReview(row.id, { reason })
    ElMessage.warning('已驳回：' + row.title)
    await loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}
function handleDetail(row) { ElMessage.info('查看内容详情 ID: ' + row.id) }
onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
