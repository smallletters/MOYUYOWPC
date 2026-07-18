<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>流量分析</h2>
      <div class="header-actions">
        <el-button @click="handleExport">导出数据</el-button>
      </div>
    </div>
    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">今日访客</div>
            <div class="kpi-value">{{ kpi.todayVisitors }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">今日浏览量</div>
            <div class="kpi-value">{{ kpi.todayPageViews }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">跳出率</div>
            <div class="kpi-value" style="color:#e67e22">{{ kpi.bounceRate }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">平均停留时长</div>
            <div class="kpi-value">{{ kpi.avgDuration }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <!-- 渠道来源分析 -->
    <el-card shadow="never">
      <template #header><span>渠道来源分析</span></template>
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="channel" label="渠道名称" min-width="160" />
        <el-table-column prop="visitors" label="访客数" width="120" />
        <el-table-column prop="pageViews" label="浏览量" width="120" />
        <el-table-column prop="percentage" label="占比" width="100">
          <template #default="{ row }">{{ row.percentage }}%</template>
        </el-table-column>
        <el-table-column prop="bounceRate" label="跳出率" width="100">
          <template #default="{ row }">{{ row.bounceRate }}%</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getTrafficAnalysis } from '../api/admin'

const kpi = ref({ todayVisitors: '—', todayPageViews: '—', bounceRate: '—', avgDuration: '—' })
const tableData = ref([])

async function loadData() {
  try {
    const res = await getTrafficAnalysis()
    if (res) {
      kpi.value = {
        todayVisitors: res.todayVisitors ?? '—',
        todayPageViews: res.todayPageViews ?? '—',
        bounceRate: res.bounceRate ?? '—',
        avgDuration: res.avgDuration ?? '—'
      }
      tableData.value = res.list || []
    }
  } catch (err) {
    console.error('获取流量数据失败', err)
  }
}

function handleExport() {
  ElMessage.success('正在导出流量数据...')
}

onMounted(() => loadData())
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
