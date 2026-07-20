<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>客服会话</h2>
    </div>
    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">总会话数</span>
            <span class="stat-value">{{ stats.totalSessions || 0 }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">进行中</span>
            <span class="stat-value">{{ stats.activeSessions || 0 }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">今日新增</span>
            <span class="stat-value">{{ stats.todayNew || 0 }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">平均响应(分)</span>
            <span class="stat-value">{{ stats.avgResponseTime || 0 }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <!-- 搜索 -->
    <el-card shadow="never" style="margin-bottom:16px">
      <el-form :model="searchForm" inline>
        <el-form-item label="会话ID">
          <el-input v-model="searchForm.sessionId" placeholder="搜索会话ID" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input v-model="searchForm.userId" placeholder="搜索用户ID" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="选择状态" clearable style="width:130px">
            <el-option label="进行中" value="active" />
            <el-option label="已结束" value="ended" />
            <el-option label="等待中" value="waiting" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <!-- 会话列表 -->
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="会话ID" width="80" />
        <el-table-column prop="userId" label="用户ID" width="120" />
        <el-table-column prop="userName" label="用户名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="agentName" label="客服" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusMap(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="messageCount" label="消息数" width="80" />
        <el-table-column prop="duration" label="持续时长" width="120">
          <template #default="{ row }">{{ row.duration || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="开始时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="会话详情" width="600px">
      <div v-if="detailData" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="会话ID">{{ detailData.id }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ detailData.userId }}</el-descriptions-item>
          <el-descriptions-item label="用户名称">{{ detailData.userName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客服">{{ detailData.agentName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(detailData.status)" size="small">{{ statusMap(detailData.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="消息数">{{ detailData.messageCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ detailData.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="持续时长">{{ detailData.duration || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最后消息" :span="2">
            {{ detailData.lastMessage || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCsSessionList, getCsSessionDetail, getCsSessionStats } from '../api/admin'

const tableData = ref([])
const stats = ref({})
const detailData = ref(null)
const detailVisible = ref(false)

const searchForm = reactive({
  sessionId: '',
  userId: '',
  status: '',
})

function statusTag(status) {
  if (status === 'active') return 'success'
  if (status === 'ended') return 'info'
  if (status === 'waiting') return 'warning'
  return 'info'
}

function statusMap(status) {
  if (status === 'active') return '进行中'
  if (status === 'ended') return '已结束'
  if (status === 'waiting') return '等待中'
  return status
}

async function loadData() {
  try {
    const params = {}
    if (searchForm.sessionId) params.sessionId = searchForm.sessionId
    if (searchForm.userId) params.userId = searchForm.userId
    if (searchForm.status) params.status = searchForm.status
    const res = await getCsSessionList(params)
    tableData.value = res.records || res || []
  } catch (e) {
    ElMessage.error('获取会话列表失败')
  }
}

async function loadStats() {
  try {
    const res = await getCsSessionStats()
    stats.value = res || {}
  } catch (e) {
    // 统计加载失败不阻塞
  }
}

function resetSearch() {
  searchForm.sessionId = ''
  searchForm.userId = ''
  searchForm.status = ''
  loadData()
}

async function handleDetail(row) {
  try {
    const res = await getCsSessionDetail(row.id)
    detailData.value = res || row
    detailVisible.value = true
  } catch (e) {
    ElMessage.error('获取会话详情失败')
  }
}

onMounted(() => { loadData(); loadStats() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }
.stat-item { display: flex; flex-direction: column; align-items: center; }
.stat-label { font-size: 13px; color: #909399; margin-bottom: 4px; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--text-800); }
</style>
