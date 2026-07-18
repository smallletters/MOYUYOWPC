<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>审计日志</h2>
      <div class="header-actions">
        <el-button @click="handleExport">导出日志</el-button>
      </div>
    </div>
    <!-- 筛选 -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="操作人">
          <el-input v-model="filters.operator" placeholder="操作人" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="filters.actionType" placeholder="全部" clearable style="width:140px">
            <el-option label="新增" value="新增" />
            <el-option label="修改" value="修改" />
            <el-option label="删除" value="删除" />
            <el-option label="查询" value="查询" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width:240px"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <!-- 表格 -->
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="operator" label="操作人" width="120" />
        <el-table-column prop="actionType" label="操作类型" width="100">
          <template #default="{ row }">
            <el-tag :type="actionTag(row.actionType)" size="small">{{ row.actionType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="target" label="操作对象" width="160" />
        <el-table-column prop="detail" label="操作详情" min-width="250" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP 地址" width="150" />
        <el-table-column prop="createTime" label="操作时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAuditLogList } from '../api/admin'

const filters = reactive({
  operator: '',
  actionType: '',
  dateRange: null,
})

const tableData = ref([])

function actionTag(type) {
  if (type === '新增') return 'success'
  if (type === '修改') return 'warning'
  if (type === '删除') return 'danger'
  return 'info'
}

// 从API加载审计日志数据
async function loadData() {
  try {
    const res = await getAuditLogList()
    const list = res || []
    // 前端筛选
    let filtered = list.filter(item => {
      if (filters.operator && !item.operator.includes(filters.operator)) return false
      if (filters.actionType && item.actionType !== filters.actionType) return false
      return true
    })
    tableData.value = filtered
  } catch (e) {
    console.error('加载审计日志失败:', e)
    ElMessage.error('加载审计日志失败')
  }
}
function handleSearch() { loadData() }
function handleReset() { filters.operator = ''; filters.actionType = ''; filters.dateRange = null; loadData() }
function handleExport() { ElMessage.success('正在导出审计日志...') }

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
