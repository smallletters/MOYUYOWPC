<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>风控规则引擎</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建规则</el-button>
      </div>
    </div>
    <!-- 工作流展示 -->
    <el-row :gutter="16" class="workflow-row">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span>① 数据采集</span></template>
          <div class="engine-desc">实时采集用户行为、交易数据、设备指纹等信息</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span>② 规则匹配</span></template>
          <div class="engine-desc">加载当前启用的规则列表，按优先级依次匹配</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span>③ 风险处置</span></template>
          <div class="engine-desc">根据匹配结果执行阻断/审核/预警/放行等动作</div>
        </el-card>
      </el-col>
    </el-row>
    <!-- 规则优先级列表 -->
    <el-card shadow="never" style="margin-top:16px">
      <template #header><span>规则优先级配置</span></template>
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="规则名称" />
        <el-table-column prop="priority" label="优先级" width="90">
          <template #default="{ row }">
            <el-tag :type="row.priority <= 1 ? 'danger' : row.priority <= 3 ? 'warning' : 'info'" size="small">P{{ row.priority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="condition" label="条件" min-width="200" show-overflow-tooltip />
        <el-table-column prop="action" label="动作" width="140" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link size="small" @click="handleToggle(row)">{{ row.status === '启用' ? '禁用' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const tableData = ref([
  { id: 1, name: '高频下单检测', priority: 1, condition: '同一用户1小时内下单超过5次', action: '阻断交易', status: '启用' },
  { id: 2, name: '异地登录预警', priority: 2, condition: '用户登录IP与常用地不一致', action: '二次验证', status: '启用' },
  { id: 3, name: '大额交易审核', priority: 3, condition: '单笔交易金额超过5000元', action: '人工审核', status: '启用' },
  { id: 4, name: '频繁退款检测', priority: 4, condition: '用户30天内退款率超过30%', action: '标记观察', status: '禁用' },
  { id: 5, name: '新设备登录提醒', priority: 5, condition: '用户从未知设备登录', action: '发送通知', status: '启用' },
  { id: 6, name: '批量注册检测', priority: 6, condition: '同一IP注册超过3个账号', action: '限制注册', status: '启用' },
])

function handleAdd() { ElMessage.info('新建规则') }
function handleEdit(row) { ElMessage.info('编辑规则：' + row.name) }
function handleToggle(row) {
  row.status = row.status === '启用' ? '禁用' : '启用'
  ElMessage.success('规则已' + row.status)
}
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }
.workflow-row { margin-bottom: 0; }
.engine-desc { font-size: 13px; color: var(--text-500); line-height: 1.6; }
</style>
