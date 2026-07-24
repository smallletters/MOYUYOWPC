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
        <el-form-item label="规则类型">
          <el-select v-model="filters.ruleType" placeholder="全部" clearable style="width:140px">
            <el-option label="登录" value="LOGIN" />
            <el-option label="下单" value="ORDER" />
            <el-option label="支付" value="PAYMENT" />
            <el-option label="优惠券" value="COUPON" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.enabled" placeholder="全部" clearable style="width:140px">
            <el-option label="启用" value="true" />
            <el-option label="禁用" value="false" />
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
        <el-table-column prop="ruleName" label="规则名称" />
        <el-table-column prop="ruleType" label="规则类型" width="100">
          <template #default="{ row }">
            <el-tag :type="ruleTypeTag(row.ruleType)" size="small">{{ ruleTypeLabel(row.ruleType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="conditionJson" label="触发条件" min-width="200" show-overflow-tooltip />
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link size="small" @click="handleToggle(row)">{{ row.enabled ? '禁用' : '启用' }}</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRiskRules, deleteRiskRule } from '../api/admin'

const filters = reactive({ ruleType: '', enabled: '' })
const tableData = ref([])

function ruleTypeTag(type) {
  if (type === 'LOGIN') return ''
  if (type === 'ORDER') return 'warning'
  if (type === 'PAYMENT') return 'danger'
  if (type === 'COUPON') return 'success'
  return 'info'
}

function ruleTypeLabel(type) {
  const map = { LOGIN: '登录', ORDER: '下单', PAYMENT: '支付', COUPON: '优惠券' }
  return map[type] || type
}

// 加载风控规则列表
async function loadData() {
  try {
    const res = await getRiskRules()
    // 根据筛选条件过滤
    let list = res.records || res || []
      if (filters.ruleType) {
        list = list.filter(item => item.ruleType === filters.ruleType)
      }
      if (filters.enabled) {
        list = list.filter(item => String(item.enabled) === filters.enabled)
      }
      tableData.value = list
  } catch (e) {
    ElMessage.error('获取风控规则失败')
  }
}

function handleSearch() { loadData() }
function handleReset() { filters.ruleType = ''; filters.enabled = ''; loadData() }
function handleAdd() { ElMessage.warning('新建功能开发中') }
function handleEdit(row) { ElMessage.info('编辑规则：' + row.ruleName) }
async function handleToggle(row) {
  try {
    const newEnabled = !row.enabled
    const { toggleRiskRule } = await import('../api/admin')
    await toggleRiskRule(row.id, { enabled: newEnabled })
    ElMessage.success('规则已' + (newEnabled ? '启用' : '禁用'))
    await loadData()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除规则「' + row.ruleName + '」吗？', '提示')
    await deleteRiskRule(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch (e) {
    // 用户取消或删除失败均不处理
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
