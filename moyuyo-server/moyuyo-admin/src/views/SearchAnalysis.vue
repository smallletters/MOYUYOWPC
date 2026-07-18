<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>搜索分析</h2>
    </div>
    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">总搜索次数</div>
            <div class="kpi-value">{{ kpi.totalSearches }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">搜索用户数</div>
            <div class="kpi-value">{{ kpi.searchUsers }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">无结果率</div>
            <div class="kpi-value" style="color:#e67e22">{{ kpi.noResultRate }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">平均搜索次数</div>
            <div class="kpi-value">{{ kpi.avgSearches }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <!-- 搜索词表格 -->
    <el-card shadow="never">
      <template #header><span>热门搜索词</span></template>
      <el-table :data="tableData" stripe style="width: 100%" :default-sort="{ prop: 'searchCount', order: 'descending' }">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="keyword" label="搜索词" min-width="160" />
        <el-table-column prop="searchCount" label="搜索次数" width="110" sortable />
        <el-table-column prop="resultCount" label="结果数" width="100" />
        <el-table-column prop="userCount" label="用户数" width="100" />
        <el-table-column prop="conversionRate" label="转化率" width="100">
          <template #default="{ row }">{{ row.conversionRate }}%</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSearchAnalysis } from '../api/admin'

const kpi = ref({ totalSearches: '—', searchUsers: '—', noResultRate: '—', avgSearches: '—' })
const tableData = ref([])

async function loadData() {
  try {
    const res = await getSearchAnalysis()
    if (res) {
      kpi.value = {
        totalSearches: res.totalSearches ?? '—',
        searchUsers: res.searchUsers ?? '—',
        noResultRate: res.noResultRate ?? '—',
        avgSearches: res.avgSearches ?? '—'
      }
      tableData.value = res.list || []
    }
  } catch (err) {
    console.error('获取搜索分析数据失败', err)
  }
}

function handleView(row) { ElMessage.info('查看搜索词详情：' + row.keyword) }

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
