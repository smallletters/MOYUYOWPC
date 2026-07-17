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
            <div class="kpi-value">12.5</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">F 平均（次）</div>
            <div class="kpi-value">6.8</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">M 平均（元）</div>
            <div class="kpi-value">1,256.00</div>
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
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const tableData = ref([
  { id: 1, name: '张三', rValue: 3, fValue: 15, mValue: 5200, level: '高价值' },
  { id: 2, name: '李四', rValue: 8, fValue: 8, mValue: 3200, level: '重要发展' },
  { id: 3, name: '王五', rValue: 15, fValue: 5, mValue: 1800, level: '重要保持' },
  { id: 4, name: '赵六', rValue: 25, fValue: 3, mValue: 800, level: '一般' },
  { id: 5, name: '孙七', rValue: 6, fValue: 10, mValue: 4500, level: '高价值' },
  { id: 6, name: '周八', rValue: 18, fValue: 4, mValue: 1200, level: '一般' },
])

function tagType(level) {
  if (level === '高价值') return 'danger'
  if (level === '重要发展') return 'warning'
  if (level === '重要保持') return 'primary'
  return 'info'
}

function handleEdit(row) {
  ElMessage.info('查看客户：' + row.name)
}
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
