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
            <div class="kpi-value">45,680</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">搜索用户数</div>
            <div class="kpi-value">18,290</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">无结果率</div>
            <div class="kpi-value" style="color:#e67e22">8.3%</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">平均搜索次数</div>
            <div class="kpi-value">2.5</div>
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
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const tableData = ref([
  { id: 1, keyword: 'T恤', searchCount: 8920, resultCount: 256, userCount: 5230, conversionRate: 12.5 },
  { id: 2, keyword: '蓝牙耳机', searchCount: 6540, resultCount: 89, userCount: 3890, conversionRate: 15.2 },
  { id: 3, keyword: '充电宝', searchCount: 5210, resultCount: 67, userCount: 3120, conversionRate: 10.8 },
  { id: 4, keyword: '机械键盘', searchCount: 4890, resultCount: 45, userCount: 2780, conversionRate: 18.3 },
  { id: 5, keyword: '保温杯', searchCount: 3560, resultCount: 78, userCount: 2150, conversionRate: 8.6 },
  { id: 6, keyword: '双肩背包', searchCount: 2890, resultCount: 34, userCount: 1690, conversionRate: 14.1 },
  { id: 7, keyword: '鼠标垫', searchCount: 2340, resultCount: 56, userCount: 1450, conversionRate: 6.4 },
  { id: 8, keyword: '扩展坞', searchCount: 1890, resultCount: 23, userCount: 1120, conversionRate: 9.7 },
])

function handleView(row) { ElMessage.info('查看搜索词详情：' + row.keyword) }
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
