<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="操作人">
          <el-input v-model="filters.operator" placeholder="请输入操作人" clearable />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="filters.operationType" placeholder="请选择" clearable style="width:130px">
            <el-option label="登录" value="登录" />
            <el-option label="新增" value="新增" />
            <el-option label="编辑" value="编辑" />
            <el-option label="删除" value="删除" />
            <el-option label="导出" value="导出" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
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
        <el-table-column prop="operator" label="操作人" width="110" />
        <el-table-column label="操作类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.operationType === '删除' ? 'danger' : row.operationType === '新增' ? 'success' : 'primary'">{{ row.operationType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="操作内容" min-width="250" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP地址" width="140" />
        <el-table-column prop="operationTime" label="操作时间" width="160" />
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
import { getSystemLogs } from '../api/admin'

const pageTitle = '运营日志'
const filters = reactive({ operator: '', operationType: '', dateRange: null })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)

// 获取操作日志列表
async function loadData() {
  try {
    const res = await getSystemLogs()
    const logs = res.records || res || []
      // 客户端筛选
      let filtered = [...logs]
      if (filters.operator) {
        filtered = filtered.filter(item => (item.operator || item.operatorName || '').includes(filters.operator))
      }
      if (filters.operationType) {
        filtered = filtered.filter(item => item.operationType === filters.operationType)
      }
      if (filters.dateRange && filters.dateRange.length === 2) {
        const start = filters.dateRange[0]
        const end = filters.dateRange[1]
        filtered = filtered.filter(item => (item.operationTime || item.createTime) >= start && (item.operationTime || item.createTime) <= end + ' 23:59:59')
      }
      total.value = filtered.length
      // 服务端分页，但日志也支持客户端分页
      const start = (currentPage.value - 1) * pageSize.value
      tableData.value = filtered.slice(start, start + pageSize.value)
    }
  } catch (e) {
    ElMessage.error('获取操作日志失败')
  }
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.operator = ''; filters.operationType = ''; filters.dateRange = null; handleSearch() }
onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
