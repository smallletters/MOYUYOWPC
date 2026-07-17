<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>GDPR 合规</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建隐私政策</el-button>
      </div>
    </div>
    <!-- 隐私政策版本 -->
    <el-row :gutter="16" class="policy-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span>当前隐私政策</span></template>
          <div class="policy-info">
            <div class="policy-version">版本 3.0</div>
            <div class="policy-date">生效日期：2026-07-01</div>
            <div class="policy-desc">涵盖了用户数据收集、存储、使用和分享的完整政策说明，符合 GDPR 最新要求。</div>
            <el-button type="primary" link @click="handleViewPolicy">查看全文</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span>合规概览</span></template>
          <div class="compliance-grid">
            <div class="compliance-item">
              <span class="compliance-label">用户同意记录</span>
              <span class="compliance-value">12,580 条</span>
            </div>
            <div class="compliance-item">
              <span class="compliance-label">数据导出请求</span>
              <span class="compliance-value">23 条</span>
            </div>
            <div class="compliance-item">
              <span class="compliance-label">数据删除请求</span>
              <span class="compliance-value">15 条</span>
            </div>
            <div class="compliance-item">
              <span class="compliance-label">同意率</span>
              <span class="compliance-value" style="color:#10b981">96.8%</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <!-- 用户同意记录 -->
    <el-card shadow="never" style="margin-top:16px">
      <template #header><span>用户同意记录</span></template>
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="user" label="用户" width="120" />
        <el-table-column prop="consentType" label="同意类型" width="140">
          <template #default="{ row }">
            <el-tag :type="consentTag(row.consentType)" size="small">{{ row.consentType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="consentStatus" label="同意状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.consentStatus === '已同意' ? 'success' : 'danger'" size="small">{{ row.consentStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP 地址" width="150" />
        <el-table-column prop="createTime" label="时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const tableData = ref([
  { id: 1, user: '张三', consentType: '隐私政策', consentStatus: '已同意', ipAddress: '192.168.1.100', createTime: '2026-07-17 10:30:25' },
  { id: 2, user: '李四', consentType: '数据收集', consentStatus: '已同意', ipAddress: '192.168.1.101', createTime: '2026-07-16 14:20:10' },
  { id: 3, user: '王五', consentType: '营销推送', consentStatus: '已拒绝', ipAddress: '192.168.1.102', createTime: '2026-07-15 09:00:00' },
  { id: 4, user: '赵六', consentType: '隐私政策', consentStatus: '已同意', ipAddress: '192.168.1.103', createTime: '2026-07-14 16:45:30' },
  { id: 5, user: '孙七', consentType: '数据收集', consentStatus: '已同意', ipAddress: '192.168.1.104', createTime: '2026-07-13 11:30:15' },
  { id: 6, user: '周八', consentType: '营销推送', consentStatus: '已同意', ipAddress: '192.168.1.105', createTime: '2026-07-12 08:15:45' },
])

function consentTag(type) {
  if (type === '隐私政策') return 'primary'
  if (type === '数据收集') return 'warning'
  return 'info'
}

function handleAdd() { ElMessage.info('新建隐私政策版本') }
function handleViewPolicy() { ElMessage.info('查看隐私政策全文') }
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }
.policy-row { margin-bottom: 0; }
.policy-info { display: flex; flex-direction: column; gap: 8px; }
.policy-version { font-size: 20px; font-weight: 700; color: var(--text-800); }
.policy-date { font-size: 13px; color: var(--text-400); }
.policy-desc { font-size: 14px; color: var(--text-600); line-height: 1.6; }
.compliance-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.compliance-item { display: flex; flex-direction: column; gap: 4px; }
.compliance-label { font-size: 13px; color: var(--text-400); }
.compliance-value { font-size: 22px; font-weight: 700; color: var(--text-800); }
</style>
