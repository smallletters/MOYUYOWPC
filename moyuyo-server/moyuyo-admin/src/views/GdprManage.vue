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
            <div class="policy-version">版本 {{ policy.version }}</div>
            <div class="policy-date">生效日期：{{ policy.effectiveDate }}</div>
            <div class="policy-desc">{{ policy.description }}</div>
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
              <span class="compliance-value">{{ compliance.consentRecords }}</span>
            </div>
            <div class="compliance-item">
              <span class="compliance-label">数据导出请求</span>
              <span class="compliance-value">{{ compliance.dataExportRequests }}</span>
            </div>
            <div class="compliance-item">
              <span class="compliance-label">数据删除请求</span>
              <span class="compliance-value">{{ compliance.dataDeleteRequests }}</span>
            </div>
            <div class="compliance-item">
              <span class="compliance-label">同意率</span>
              <span class="compliance-value" style="color:#10b981">{{ compliance.consentRate }}</span>
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
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getGdprConsentRecords, getGdprDataRequests, getActivePolicy } from '../api/admin'

const compliance = ref({ consentRecords: '—', dataExportRequests: '—', dataDeleteRequests: '—', consentRate: '—' })
const policy = ref({ version: '3.0', effectiveDate: '2026-07-01', description: '涵盖了用户数据收集、存储、使用和分享的完整政策说明，符合 GDPR 最新要求。' })
const tableData = ref([])

async function loadData() {
  try {
    const [consentRes, requestRes] = await Promise.all([
      getGdprConsentRecords(),
      getGdprDataRequests()
    ])
    // 加载当前隐私政策
    try {
      const policyRes = await getActivePolicy()
      if (policyRes) {
        policy.value = {
          version: policyRes.version || '3.0',
          effectiveDate: policyRes.effectiveDate || '2026-07-01',
          description: policyRes.description || '涵盖了用户数据收集、存储、使用和分享的完整政策说明，符合 GDPR 最新要求。'
        }
      }
    } catch (e) {
      console.error('获取隐私政策失败，使用默认值', e)
    }
    if (consentRes) {
      compliance.value = {
        consentRecords: consentRes.consentRecords ?? '—',
        dataExportRequests: consentRes.dataExportRequests ?? '—',
        dataDeleteRequests: consentRes.dataDeleteRequests ?? '—',
        consentRate: consentRes.consentRate ?? '—'
      }
      tableData.value = consentRes.list || []
    }
  } catch (err) {
    console.error('获取GDPR数据失败', err)
  }
}

function consentTag(type) {
  if (type === '隐私政策') return 'primary'
  if (type === '数据收集') return 'warning'
  return 'info'
}

function handleAdd() { ElMessage.warning('新建功能开发中') }
function handleViewPolicy() { ElMessage.info('查看隐私政策全文') }

onMounted(() => loadData())
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
