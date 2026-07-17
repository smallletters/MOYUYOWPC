<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>风控管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建规则</el-button>
      </div>
    </div>
    <!-- 筛选 -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="风险等级">
          <el-select v-model="filters.level" placeholder="全部" clearable style="width:140px">
            <el-option label="高" value="高" />
            <el-option label="中" value="中" />
            <el-option label="低" value="低" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width:140px">
            <el-option label="启用" value="启用" />
            <el-option label="禁用" value="禁用" />
          </el-select>
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
        <el-table-column prop="name" label="规则名称" />
        <el-table-column prop="level" label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hitCount" label="命中次数" width="100" />
        <el-table-column prop="triggerCondition" label="触发条件" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link size="small" @click="handleToggle(row)">{{ row.status === '启用' ? '禁用' : '启用' }}</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const filters = reactive({ level: '', status: '' })

const tableData = ref([
  { id: 1, name: '高频下单检测', level: '高', hitCount: 156, triggerCondition: '同一用户1小时内下单超过5次', status: '启用', updateTime: '2026-07-17 10:30' },
  { id: 2, name: '异地登录预警', level: '高', hitCount: 89, triggerCondition: '用户登录IP与常用地不一致', status: '启用', updateTime: '2026-07-16 14:20' },
  { id: 3, name: '大额交易审核', level: '中', hitCount: 234, triggerCondition: '单笔交易金额超过5000元', status: '启用', updateTime: '2026-07-15 09:00' },
  { id: 4, name: '频繁退款检测', level: '中', hitCount: 45, triggerCondition: '用户30天内退款率超过30%', status: '禁用', updateTime: '2026-07-12 16:45' },
  { id: 5, name: '新设备登录提醒', level: '低', hitCount: 312, triggerCondition: '用户从未知设备登录', status: '启用', updateTime: '2026-07-10 11:30' },
  { id: 6, name: '批量注册检测', level: '高', hitCount: 67, triggerCondition: '同一IP地址注册超过3个账号', status: '启用', updateTime: '2026-07-08 08:15' },
])

function levelTag(level) {
  if (level === '高') return 'danger'
  if (level === '中') return 'warning'
  return 'info'
}

function handleSearch() { ElMessage.success('搜索完成') }
function handleReset() { filters.level = ''; filters.status = '' }
function handleAdd() { ElMessage.info('新建风控规则') }
function handleEdit(row) { ElMessage.info('编辑规则：' + row.name) }
function handleToggle(row) {
  row.status = row.status === '启用' ? '禁用' : '启用'
  ElMessage.success('规则已' + row.status)
}
function handleDelete(row) {
  ElMessageBox.confirm('确定删除规则「' + row.name + '」吗？', '提示').then(() => {
    tableData.value = tableData.value.filter(item => item.id !== row.id)
    ElMessage.success('已删除')
  }).catch(() => {})
}
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
