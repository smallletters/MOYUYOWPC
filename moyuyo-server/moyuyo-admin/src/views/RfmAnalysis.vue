<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>RFM 分析</h2>
    </div>
    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="8">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">R 平均（天）</div>
            <div class="kpi-value">{{ kpi.avgR }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">F 平均（次）</div>
            <div class="kpi-value">{{ kpi.avgF }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">M 平均（元）</div>
            <div class="kpi-value">{{ kpi.avgM }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <!-- 表格 -->
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="客户名称" />
        <el-table-column prop="rValue" label="R 值（天）" width="120" />
        <el-table-column prop="fValue" label="F 值（次）" width="120" />
        <el-table-column prop="mValue" label="M 值（元）" width="130" />
        <el-table-column prop="level" label="RFM 等级" width="150">
          <template #default="{ row }">
            <el-tag :type="tagType(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRfmAnalysis } from '../api/admin'

const kpi = ref({ avgR: '—', avgF: '—', avgM: '—' })
const tableData = ref([])

async function loadData() {
  try {
    const res = await getRfmAnalysis()
    // 后端返回扁平数组 [{ customerName, rDays, fCount, mAmount, rfmLevel }, ...]
    if (res && Array.isArray(res) && res.length > 0) {
      // 从列表数据计算平均值
      const avgR = (res.reduce((sum, r) => sum + Number(r.rDays || 0), 0) / res.length).toFixed(1)
      const avgF = (res.reduce((sum, r) => sum + Number(r.fCount || 0), 0) / res.length).toFixed(1)
      const avgM = (res.reduce((sum, r) => sum + Number(r.mAmount || 0), 0) / res.length).toFixed(2)
      kpi.value = { avgR, avgF, avgM }
      // 映射字段以匹配表格的 prop
      tableData.value = res.map((item, idx) => ({
        id: idx + 1,
        name: item.customerName || '',
        rValue: item.rDays || 0,
        fValue: item.fCount || 0,
        mValue: item.mAmount || 0,
        level: item.rfmLevel || ''
      }))
    } else {
      kpi.value = { avgR: '—', avgF: '—', avgM: '—' }
      tableData.value = []
    }
  } catch (err) {
    console.error('获取RFM数据失败', err)
  }
}

function tagType(level) {
  if (level === '高价值') return 'danger'
  if (level === '重要发展') return 'warning'
  if (level === '重要保持') return 'primary'
  return 'info'
}

function handleEdit(row) {
  ElMessage.warning('客户详情功能开发中')
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
</style>
