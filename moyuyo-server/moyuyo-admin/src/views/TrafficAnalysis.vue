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
import { exportCsv } from '../utils/exportCsv'

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
        avgDuration: res.avgStayDuration ?? '—'
      }
      // 后端返回 channels，映射为前端期望的字段名
      const channels = res.channels || []
      const totalVisits = channels.reduce((sum, c) => sum + (c.visits || 0), 0)
      tableData.value = channels.map((c, i) => ({
        id: i + 1,
        channel: c.channel,
        visitors: c.visits || 0,
        pageViews: c.pv || 0,
        percentage: totalVisits > 0 ? Math.round((c.visits || 0) / totalVisits * 100) : 0,
        bounceRate: 0
      }))
    }
  } catch (err) {
    console.error('获取流量数据失败', err)
  }
}

// 导出渠道分析数据到 CSV
function handleExport() {
  const rows = [
    { channel: '今日访客', visits: kpi.value.todayVisitors },
    { channel: '今日浏览量', visits: kpi.value.todayPageViews },
    { channel: '跳出率', visits: kpi.value.bounceRate },
    { channel: '平均停留时长', visits: kpi.value.avgDuration },
    ...tableData.value.map(row => ({ channel: row.channel, visits: row.visitors }))
  ]
  const ok = exportCsv(rows, [
    { key: 'channel', label: '指标/渠道' },
    { key: 'visits', label: '数值' }
  ], `流量分析_${new Date().toISOString().slice(0, 10)}.csv`)
  if (ok) {
    ElMessage.success('流量数据已导出')
  } else {
    ElMessage.warning('暂无可导出的数据')
  }
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
