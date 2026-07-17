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
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="内容标题" width="200" show-overflow-tooltip />
        <el-table-column label="内容类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.contentType === '评论' ? 'info' : row.contentType === '视频' ? 'warning' : 'primary'">{{ row.contentType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitter" label="提交人" width="100" />
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.reviewStatus === '已通过' ? 'success' : row.reviewStatus === '已驳回' ? 'danger' : 'warning'">{{ row.reviewStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitTime" label="提交时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="success" link size="small" :disabled="row.reviewStatus !== '待审核'" @click="handleApprove(row)">通过</el-button>
            <el-button type="danger" link size="small" :disabled="row.reviewStatus !== '待审核'" @click="handleReject(row)">驳回</el-button>
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
import { ElMessage } from 'element-plus'

const pageTitle = '内容审核详情'
const filters = reactive({ reviewer: '', status: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)

const mockData = [
  { id: 1, title: '这款深海鱼油效果真好！', contentType: '评论', submitter: '用户A', reviewStatus: '待审核', submitTime: '2026-07-17 08:30' },
  { id: 2, title: '跨境购物攻略分享', contentType: '图文', submitter: '用户B', reviewStatus: '已通过', submitTime: '2026-07-16 14:20' },
  { id: 3, title: '开箱测评视频', contentType: '视频', submitter: '用户C', reviewStatus: '待审核', submitTime: '2026-07-17 10:00' },
  { id: 4, title: '产品质量有问题', contentType: '评论', submitter: '用户D', reviewStatus: '已驳回', submitTime: '2026-07-15 09:45' },
  { id: 5, title: '保健品选购指南', contentType: '图文', submitter: '商家小张', reviewStatus: '已通过', submitTime: '2026-07-14 16:30' },
  { id: 6, title: '促销活动宣传视频', contentType: '视频', submitter: '商家小李', reviewStatus: '待审核', submitTime: '2026-07-17 11:15' }
]

function loadData() {
  let filtered = [...mockData]
  if (filters.reviewer) {
    filtered = filtered.filter(item => item.submitter.includes(filters.reviewer))
  }
  if (filters.status) {
    filtered = filtered.filter(item => item.reviewStatus === filters.status)
  }
  tableData.value = filtered
  total.value = filtered.length
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.reviewer = ''; filters.status = ''; handleSearch() }
function handleApprove(row) {
  ElMessage.success('已通过：' + row.title)
  row.reviewStatus = '已通过'
  loadData()
}
function handleReject(row) {
  ElMessage.warning('已驳回：' + row.title)
  row.reviewStatus = '已驳回'
  loadData()
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
