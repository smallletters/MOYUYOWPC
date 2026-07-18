<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>满意度管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">发起调查</el-button>
      </div>
    </div>
    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">满意度评分</div>
            <div class="kpi-value" style="color:#f59e0b">{{ kpi.avgScore }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">评价总数</div>
            <div class="kpi-value">{{ kpi.totalReviews }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">好评率</div>
            <div class="kpi-value" style="color:#10b981">{{ kpi.positiveRate }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">回复率</div>
            <div class="kpi-value">{{ kpi.replyRate }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <!-- 表格 -->
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="user" label="用户" width="120" />
        <el-table-column prop="content" label="评价内容" min-width="250" show-overflow-tooltip />
        <el-table-column prop="score" label="评分" width="100">
          <template #default="{ row }">
            <el-rate v-model="row.score" disabled :colors="rateColors" score-template="{value}" :texts="['','','','','']" />
          </template>
        </el-table-column>
        <el-table-column prop="replyStatus" label="回复状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.replyStatus === '已回复' ? 'success' : 'danger'" size="small">{{ row.replyStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="评价时间" width="180" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.replyStatus === '未回复'" type="primary" link size="small" @click="handleReply(row)">回复</el-button>
            <el-button type="primary" link size="small" @click="handleView(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSatisfactionStats, getSatisfactionList } from '../api/admin'

const rateColors = ['#f56c6c', '#e6a23c', '#5cb87a', '#1989fa', '#f59e0b']

const kpi = ref({ avgScore: '—', totalReviews: '—', positiveRate: '—', replyRate: '—' })
const tableData = ref([])

async function loadStats() {
  try {
    const res = await getSatisfactionStats()
    if (res) {
      kpi.value = {
        avgScore: res.avgScore ?? '—',
        totalReviews: res.totalReviews ?? '—',
        positiveRate: res.positiveRate ?? '—',
        replyRate: res.replyRate ?? '—'
      }
    }
  } catch (err) {
    console.error('获取满意度统计失败', err)
  }
}

async function loadList() {
  try {
    const res = await getSatisfactionList()
    tableData.value = res || []
  } catch (err) {
    console.error('获取满意度列表失败', err)
  }
}

function handleAdd() { ElMessage.warning('满意度调查功能开发中') }
function handleReply(row) { ElMessage.warning('回复功能开发中') }
function handleView(row) { ElMessage.warning('详情功能开发中') }

onMounted(() => {
  loadStats()
  loadList()
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.kpi-row { margin-bottom: 16px; }
.kpi-card-content { text-align: center; padding: 8px 0; }
.kpi-label { font-size: 14px; color: var(--text-400); margin-bottom: 8px; }
.kpi-value { font-size: 28px; font-weight: 700; color: var(--text-800); }
.header-actions { display: flex; gap: 8px; }
</style>
