<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>GDPR 合规</h2>
        <p class="page-subtitle">管理隐私政策、数据主体请求与用户同意记录</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openCreateDialog">新建隐私政策</el-button>
      </div>
    </div>

    <!-- 隐私政策版本 + 合规概览 -->
    <el-row :gutter="16" class="policy-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header><span>当前隐私政策</span></template>
          <div class="policy-info">
            <div class="policy-version">版本 {{ policy.version }}</div>
            <div class="policy-date">生效日期：{{ policy.effectiveDate }}</div>
            <div class="policy-desc">{{ policy.description }}</div>
            <el-button type="primary" link @click="openPolicyDialog">查看全文</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
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

    <!-- 数据主体请求工单（设计稿融合） -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header-row">
          <span>数据请求工单</span>
          <div class="request-stats">
            <span class="request-stat">待处理 <b>{{ pendingCount }}</b></span>
            <span class="request-stat">处理中 <b>{{ processingCount }}</b></span>
            <span class="request-stat">已逾期 <b class="text-error">{{ overdueCount }}</b></span>
          </div>
        </div>
      </template>
      <!-- 请求类型筛选 -->
      <div class="filter-tabs">
        <button
          v-for="tab in requestTabs"
          :key="tab.value"
          class="filter-tab"
          :class="{ active: requestFilter === tab.value }"
          @click="requestFilter = tab.value"
        >{{ tab.label }}</button>
      </div>
      <!-- 请求列表 -->
      <div v-if="filteredRequests.length === 0" class="empty-state">暂无数据请求</div>
      <div v-for="req in filteredRequests" :key="req.id" class="request-item">
        <div class="request-header">
          <div class="request-title-area">
            <span class="request-no">#R{{ req.id }}</span>
            <span :class="['badge', statusClass(req.status)]">{{ statusLabel(req.status) }}</span>
          </div>
          <div class="request-meta">提交：{{ formatTime(req.createTime) }}</div>
        </div>
        <div class="request-tags">
          <span class="badge badge-gdpr">GDPR</span>
          <span class="badge badge-type">{{ typeLabel(req.requestType) }}</span>
        </div>
        <div class="request-footer">
          <span class="request-user">用户ID: {{ req.userId }}</span>
          <span class="request-handler">处理人: {{ req.processedBy || '未分配' }}</span>
          <div class="request-actions">
            <el-button
              v-if="req.status === 'PENDING' || req.status === 'PROCESSING'"
              size="small"
              type="primary"
              @click="openProcessDialog(req)"
            >处理</el-button>
            <el-button size="small" @click="openDetailDialog(req)">查看详情</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 用户同意记录 -->
    <el-card shadow="never" class="section-card">
      <template #header><span>用户同意记录</span></template>
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="consentType" label="同意类型" width="160">
          <template #default="{ row }">
            <el-tag :type="consentTag(row.consentType)" size="small">{{ row.consentType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="granted" label="同意状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.granted ? 'success' : 'danger'" size="small">{{ row.granted ? '已同意' : '已拒绝' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP 地址" width="150" />
        <el-table-column prop="createTime" label="时间" width="180" />
      </el-table>
    </el-card>

    <!-- 快速操作（示例数据：操作入口为前端演示，无真实后端接口） -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header-row">
          <span>快速操作</span>
          <span class="quick-action-hint">点击操作将弹出确认提示</span>
        </div>
      </template>
      <div class="quick-action-grid">
        <div
          v-for="action in quickActions"
          :key="action.key"
          class="quick-action-item"
          @click="handleQuickAction(action)"
        >
          <div class="quick-action-icon" :style="{ background: action.bg, color: action.color }">
            <el-icon :size="20"><component :is="action.icon" /></el-icon>
          </div>
          <div class="quick-action-text">
            <span class="quick-action-title">{{ action.title }}</span>
            <span class="quick-action-desc">{{ action.desc }}</span>
          </div>
          <el-icon class="quick-action-arrow" :size="14"><ArrowRight /></el-icon>
        </div>
      </div>
    </el-card>

    <!-- 未成年人保护（示例数据：年龄验证统计与列表为演示数据） -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header-row">
          <span class="minor-title">
            <el-icon :size="16" style="color: var(--brand-500)"><UserFilled /></el-icon>
            未成年人保护
          </span>
        </div>
      </template>

      <!-- 规则说明卡片 -->
      <div class="minor-rules">
        <div class="minor-rules-icon"><el-icon :size="20"><Warning /></el-icon></div>
        <div class="minor-rules-text">
          <p>依据 <b>COPPA</b>（美国儿童在线隐私保护法）与 <b>GDPR 第 8 条</b>，未满 13 周岁用户禁止收集个人信息，13–16 周岁用户须取得监护人同意后方可处理数据。</p>
          <p>系统对注册用户进行年龄验证，未通过验证的账号将被限制数据收集、广告追踪与个性化推送。</p>
        </div>
      </div>

      <!-- 年龄标记统计 -->
      <div class="minor-stats">
        <div class="minor-stat">
          <span class="minor-stat-value" style="color: var(--state-warning)">{{ minorStats.under13 }}</span>
          <span class="minor-stat-label">13岁以下</span>
          <span class="minor-stat-sub">用户标记</span>
        </div>
        <div class="minor-stat">
          <span class="minor-stat-value" style="color: var(--brand-500)">{{ minorStats.under16 }}</span>
          <span class="minor-stat-label">16岁以下</span>
          <span class="minor-stat-sub">用户标记</span>
        </div>
      </div>

      <!-- 家长同意凭证存档入口 -->
      <div class="minor-archive" @click="handleArchiveConsent">
        <el-icon :size="16"><FolderOpened /></el-icon>
        <span>家长同意凭证存档</span>
        <el-icon :size="14"><ArrowRight /></el-icon>
      </div>

      <!-- 年龄验证状态列表 -->
      <div class="minor-table-title">
        <span>年龄验证状态</span>
      </div>
      <el-table :data="minorList" stripe style="width: 100%">
        <el-table-column prop="userId" label="用户ID" width="130" />
        <el-table-column label="年龄验证状态" width="140">
          <template #default="{ row }">
            <el-tag :type="verifyTag(row.verifyStatus)" size="small">{{ verifyLabel(row.verifyStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="监护人同意" width="130">
          <template #default="{ row }">
            <el-tag :type="guardianTag(row.guardianConsent)" size="small">{{ row.guardianConsent }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="110">
          <template #default="{ row }">
            <el-tag :type="riskTag(row.riskLevel)" size="small">{{ row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastCheck" label="最近验证时间" width="170" />
        <el-table-column label="操作" min-width="150">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="viewMinor(row)">查看</el-button>
            <el-button size="small" link @click="reVerifyMinor(row)">重新验证</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建隐私政策弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建隐私政策" width="560px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="版本号" required>
          <el-input v-model="createForm.version" placeholder="如 3.1" />
        </el-form-item>
        <el-form-item label="政策标题" required>
          <el-input v-model="createForm.title" placeholder="如 隐私政策" />
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker v-model="createForm.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="选择生效日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="政策内容" required>
          <el-input v-model="createForm.content" type="textarea" :rows="6" placeholder="输入政策内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 查看政策全文弹窗 -->
    <el-dialog v-model="policyDialogVisible" title="隐私政策全文" width="600px">
      <div class="policy-full">
        <h3>{{ policy.title }}（版本 {{ policy.version }}）</h3>
        <p>生效日期：{{ policy.effectiveDate }}</p>
        <div class="policy-content">{{ policy.description }}</div>
      </div>
    </el-dialog>

    <!-- 处理请求弹窗 -->
    <el-dialog v-model="processDialogVisible" title="处理数据请求" width="480px">
      <el-form label-width="90px">
        <el-form-item label="请求编号"><span>#R{{ currentRequest?.id }}</span></el-form-item>
        <el-form-item label="请求类型">
          <el-tag>{{ currentRequest ? typeLabel(currentRequest.requestType) : '' }}</el-tag>
        </el-form-item>
        <el-form-item label="处理结果" required>
          <el-select v-model="processResult" style="width: 100%">
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="processNote" type="textarea" :rows="3" placeholder="填写处理说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="processDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="processing" @click="submitProcess">确认处理</el-button>
      </template>
    </el-dialog>

    <!-- 请求详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="数据请求详情" width="520px">
      <el-descriptions v-if="currentRequest" :column="1" border>
        <el-descriptions-item label="请求编号">#R{{ currentRequest.id }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ currentRequest.userId }}</el-descriptions-item>
        <el-descriptions-item label="请求类型">{{ typeLabel(currentRequest.requestType) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(currentRequest.status) }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ formatTime(currentRequest.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="处理人">{{ currentRequest.processedBy || '未分配' }}</el-descriptions-item>
        <el-descriptions-item label="处理时间">{{ formatTime(currentRequest.processedTime) }}</el-descriptions-item>
        <el-descriptions-item label="处理备注">{{ currentRequest.responseNote || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="currentRequest.detailJson" label="详情">{{ detailSummary(currentRequest.detailJson) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Download, Delete, Document, RefreshLeft, ChatDotRound,
  UserFilled, FolderOpened, ArrowRight, Warning
} from '@element-plus/icons-vue'
import {
  getGdprConsentRecords, getGdprDataRequests, getActivePolicy,
  processGdprRequest, createGdprPolicy,
  getGdprMinorStats, getGdprMinorList, reverifyGdprMinor,
  getGdprArchiveOverview, triggerGdprQuickAction
} from '../api/admin'
import { toArray } from '../utils/safeArray'

const compliance = ref({ consentRecords: '—', dataExportRequests: '—', dataDeleteRequests: '—', consentRate: '—' })
const policy = ref({ version: '3.0', effectiveDate: '2026-07-01', description: '涵盖了用户数据收集、存储、使用和分享的完整政策说明，符合 GDPR 最新要求。' })
const tableData = ref([])

// ---- 数据请求工单 ----
const requests = ref([])
const requestFilter = ref('ALL')
const requestTabs = [
  { label: '全部', value: 'ALL' },
  { label: '数据访问', value: 'ACCESS' },
  { label: '账号删除', value: 'DELETE' },
  { label: '数据导出', value: 'PORTABILITY' },
  { label: '数据修正', value: 'RECTIFY' }
]

// ---- 快速操作（示例数据：前端演示入口，无真实后端接口） ----
const quickActions = [
  {
    key: 'export',
    title: '一键导出用户数据包',
    desc: '支持 JSON / CSV 格式导出',
    icon: Download,
    bg: 'var(--brand-50)',
    color: 'var(--brand-500)',
    confirm: '将为指定用户生成数据包并发送下载链接，是否继续？'
  },
  {
    key: 'delete',
    title: '一键执行账号删除',
    desc: '含 30 天宽限期，可撤销操作',
    icon: Delete,
    bg: 'var(--state-error-surface)',
    color: 'var(--state-error)',
    confirm: '账号删除后将进入 30 天宽限期，期间可撤销，是否继续？'
  },
  {
    key: 'consent',
    title: '同意记录管理',
    desc: '查看与导出用户同意记录',
    icon: Document,
    bg: 'var(--background-200)',
    color: 'var(--text-600)',
    confirm: '将跳转至用户同意记录列表，是否继续？'
  },
  {
    key: 'revoke',
    title: '撤销同意',
    desc: '批量撤销指定用户的授权',
    icon: RefreshLeft,
    bg: 'var(--state-warning-surface)',
    color: 'var(--state-warning)',
    confirm: '撤销后相关数据收集与处理将立即停止，是否继续？'
  },
  {
    key: 'complaint',
    title: '投诉渠道',
    desc: '监管机构与用户投诉入口',
    icon: ChatDotRound,
    bg: '#f0e6ff',
    color: '#7c3aed',
    confirm: '将跳转至投诉处理页面，是否继续？'
  }
]

// ---- 未成年人保护（真实后端驱动） ----
const minorStats = reactive({ under13: 0, under16: 0 })
const minorList = ref([])
const archiveOverview = reactive({ total: 0, active: 0, recent: [] })

// ---- 弹窗状态 ----
const createDialogVisible = ref(false)
const creating = ref(false)
const createForm = reactive({ version: '', title: '', effectiveDate: '', content: '' })
const policyDialogVisible = ref(false)
const processDialogVisible = ref(false)
const processing = ref(false)
const processResult = ref('COMPLETED')
const processNote = ref('')
const currentRequest = ref(null)
const detailDialogVisible = ref(false)

// ---- 计算属性 ----
const filteredRequests = computed(() => {
  if (requestFilter.value === 'ALL') return requests.value
  return requests.value.filter(r => r.requestType === requestFilter.value)
})

const pendingCount = computed(() => requests.value.filter(r => r.status === 'PENDING').length)
const processingCount = computed(() => requests.value.filter(r => r.status === 'PROCESSING').length)
const overdueCount = computed(() => requests.value.filter(r => r.expireTime && new Date(r.expireTime) < new Date() && r.status === 'PENDING').length)

// ---- 请求加载 ----
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
          description: policyRes.content || policyRes.description || '涵盖了用户数据收集、存储、使用和分享的完整政策说明，符合 GDPR 最新要求。'
        }
      }
    } catch (e) {
      console.error('获取隐私政策失败，使用默认值', e)
    }
    // 加载数据请求列表（后端返回分页结构 { records, total }）
    const reqList = requestRes?.records || toArray(requestRes)
    requests.value = reqList.map(r => ({
      id: r.id,
      userId: r.userId,
      requestType: r.requestType,
      status: r.status,
      detailJson: r.detailJson,
      processedBy: r.processedBy,
      processedTime: r.processedTime,
      responseNote: r.responseNote,
      expireTime: r.expireTime,
      createTime: r.createTime
    }))
    if (consentRes) {
      const exportCount = (requestRes && requestRes.dataExportRequests) ?? (consentRes.dataExportRequests ?? '—')
      const deleteCount = (requestRes && requestRes.dataDeleteRequests) ?? (consentRes.dataDeleteRequests ?? '—')
      compliance.value = {
        consentRecords: consentRes.consentRecords ?? '—',
        dataExportRequests: exportCount,
        dataDeleteRequests: deleteCount,
        consentRate: consentRes.consentRate ?? '—'
      }
      tableData.value = toArray(consentRes)
    }
  } catch (err) {
      console.error('获取GDPR数据失败', err)
    }
    // 未成年人保护数据独立加载（不阻塞主流程）
    loadMinor()
}

// ---- 未成年人数据加载 ----
async function loadMinor() {
  try {
    const [stats, list] = await Promise.all([
      getGdprMinorStats(),
      getGdprMinorList(20)
    ])
    minorStats.under13 = stats?.under13 ?? 0
    minorStats.under16 = stats?.under16 ?? 0
    minorList.value = Array.isArray(list) ? list : []
  } catch (err) {
    console.error('获取未成年人保护数据失败', err)
  }
}

// ---- 类型/状态映射 ----
function typeLabel(type) {
  const map = { ACCESS: '数据访问', DELETE: '账号删除', PORTABILITY: '数据导出', RECTIFY: '数据修正' }
  return map[type] || type || '-'
}

function statusLabel(status) {
  const map = { PENDING: '待处理', PROCESSING: '处理中', COMPLETED: '已完成', REJECTED: '已拒绝' }
  return map[status] || status || '-'
}

function statusClass(status) {
  if (status === 'PENDING') return 'badge-pending'
  if (status === 'PROCESSING') return 'badge-processing'
  if (status === 'COMPLETED') return 'badge-completed'
  return 'badge-rejected'
}

function consentTag(type) {
  if (type === '隐私政策' || type === 'PRIVACY_POLICY') return 'primary'
  if (type === '数据收集' || type === 'MARKETING') return 'warning'
  return 'info'
}

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

function detailSummary(json) {
  try {
    return JSON.stringify(JSON.parse(json))
  } catch {
    return json
  }
}

// ---- 快速操作处理（真实后端：写入 GDPR 快速操作流水） ----
async function handleQuickAction(action) {
  try {
    await ElMessageBox.confirm(action.confirm, action.title, {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    const res = await triggerGdprQuickAction({
      actionType: action.key === 'export' ? 'EXPORT'
        : action.key === 'delete' ? 'DELETE'
        : action.key === 'consent' ? 'CONSENT_EXPORT'
        : action.key === 'revoke' ? 'REVOKE'
        : 'COMPLAINT',
      format: action.key === 'export' ? 'JSON' : undefined
    })
    const id = res?.id ? `#${res.id}` : ''
    if (res?.downloadUrl) {
      ElMessage.success(`「${action.title}」已受理 ${id}，下载链接：${res.downloadUrl}`)
    } else if (res?.gracePeriodEnd) {
      ElMessage.success(`「${action.title}」已受理 ${id}，宽限期至 ${res.gracePeriodEnd}`)
    } else {
      ElMessage.success(`「${action.title}」已受理 ${id}`)
    }
    await loadData()
  } catch (err) {
    ElMessage.error('操作失败：' + (err?.message || '未知错误'))
  }
}

// ---- 未成年人保护 ----
function verifyLabel(status) {
  const map = { VERIFIED: '已验证', PENDING: '待验证', FAILED: '验证失败' }
  return map[status] || status || '-'
}

function verifyTag(status) {
  if (status === 'VERIFIED') return 'success'
  if (status === 'PENDING') return 'warning'
  return 'danger'
}

function guardianTag(consent) {
  if (consent === '已取得') return 'success'
  if (consent === '未取得') return 'danger'
  return 'info'
}

function riskTag(level) {
  if (level === '低') return 'success'
  if (level === '中') return 'warning'
  return 'danger'
}

// 家长同意凭证存档入口（真实后端：拉取凭证聚合 + 最近凭证列表）
async function handleArchiveConsent() {
  try {
    const res = await getGdprArchiveOverview()
    archiveOverview.total = res?.total ?? 0
    archiveOverview.active = res?.active ?? 0
    archiveOverview.recent = res?.recent ?? []
    const recent = (res?.recent || []).map(r => `U${r.userId}/${r.relationship || '监护人'}`).join('、')
    ElMessage.success(`已加载家长凭证存档：共 ${res?.total ?? 0} 份（活跃 ${res?.active ?? 0}），最近：${recent || '暂无'}`)
  } catch (err) {
    ElMessage.error('加载家长凭证存档失败：' + (err?.message || '未知错误'))
  }
}

// 查看年龄验证详情（真实后端：拉取该用户的凭证列表）
async function viewMinor(row) {
  const userId = String(row.userId || '').replace(/^U/, '')
  try {
    const list = await getGdprConsentProofs(userId ? Number(userId) : null)
    const summary = (list || []).map(p => `${p.guardianName || '监护人'}（${p.relationship || '-'}，${p.status || '-'}）`).join('；')
    ElMessage.success(`用户 ${row.userId} 已存档 ${list?.length || 0} 份凭证：${summary || '暂无'}`)
  } catch (err) {
    ElMessage.error('查看凭证失败：' + (err?.message || '未知错误'))
  }
}

// 重新发起年龄验证（真实后端：刷新 verified_time / next_check_time）
async function reVerifyMinor(row) {
  const userId = String(row.userId || '').replace(/^U/, '')
  if (!userId) {
    ElMessage.warning('用户ID无效')
    return
  }
  try {
    await ElMessageBox.confirm(`将重新发起用户 ${row.userId} 的年龄验证流程，是否继续？`, '重新验证', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await reverifyGdprMinor(Number(userId))
    ElMessage.success(`用户 ${row.userId} 已发起重新验证`)
    await loadMinor()
  } catch (err) {
    ElMessage.error('重新验证失败：' + (err?.message || '未知错误'))
  }
}

// ---- 新建隐私政策 ----
function openCreateDialog() {
  createForm.version = ''
  createForm.title = ''
  createForm.effectiveDate = ''
  createForm.content = ''
  createDialogVisible.value = true
}

async function submitCreate() {
  if (!createForm.version.trim() || !createForm.title.trim() || !createForm.content.trim()) {
    ElMessage.warning('请填写版本号、标题与政策内容')
    return
  }
  creating.value = true
  try {
    const res = await createGdprPolicy({
      version: createForm.version.trim(),
      title: createForm.title.trim(),
      content: createForm.content.trim(),
      effectiveDate: createForm.effectiveDate || undefined
    })
    ElMessage.success(`隐私政策 v${res.version} 已创建并生效`)
    createDialogVisible.value = false
    loadData()
  } catch (err) {
    console.error('创建隐私政策失败', err)
    ElMessage.error('创建隐私政策失败：' + (err?.message || '未知错误'))
  } finally {
    creating.value = false
  }
}

function openPolicyDialog() {
  policyDialogVisible.value = true
}

// ---- 处理数据请求 ----
function openProcessDialog(req) {
  currentRequest.value = req
  processResult.value = 'COMPLETED'
  processNote.value = ''
  processDialogVisible.value = true
}

async function submitProcess() {
  if (!currentRequest.value) return
  processing.value = true
  try {
    await processGdprRequest(currentRequest.value.id, {
      result: processResult.value,
      note: processNote.value
    })
    ElMessage.success('数据请求已处理')
    processDialogVisible.value = false
    loadData()
  } catch (err) {
    console.error('处理数据请求失败', err)
    ElMessage.error('处理失败：' + (err?.message || '未知错误'))
  } finally {
    processing.value = false
  }
}

function openDetailDialog(req) {
  currentRequest.value = req
  detailDialogVisible.value = true
}

onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.page-subtitle { font-size: 13px; color: var(--text-400); margin: 6px 0 0; }
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

/* 数据请求工单 */
.section-card { margin-top: 16px; }
.card-header-row { display: flex; align-items: center; justify-content: space-between; }
.request-stats { display: flex; gap: 14px; font-size: 12px; color: var(--text-500); }
.request-stats b { color: var(--text-800); }
.request-stat .text-error { color: var(--state-error); }
.filter-tabs { display: flex; gap: 8px; margin-bottom: 14px; flex-wrap: wrap; }
.filter-tab {
  height: 30px; padding: 0 14px; border-radius: 999px;
  border: 1px solid var(--border); background: var(--card);
  color: var(--text-500); font-size: 12px; font-weight: 500;
  font-family: inherit; cursor: pointer; transition: all 0.15s ease;
}
.filter-tab:hover { border-color: var(--primary); color: var(--primary); }
.filter-tab.active { background: var(--primary); border-color: var(--primary); color: var(--primary-foreground); }
.empty-state { padding: 32px 0; text-align: center; color: var(--text-400); font-size: 13px; }
.request-item {
  border: 1px solid var(--border); border-radius: var(--radius);
  padding: 14px 16px; margin-bottom: 12px; transition: box-shadow 0.15s ease;
}
.request-item:hover { box-shadow: var(--shadow-sm); }
.request-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.request-title-area { display: flex; align-items: center; gap: 10px; }
.request-no { font-size: 14px; font-weight: 600; color: var(--text-800); font-family: var(--font-mono); }
.request-meta { font-size: 12px; color: var(--text-400); }
.request-tags { display: flex; gap: 8px; margin-bottom: 8px; }
.badge {
  display: inline-flex; align-items: center; height: 22px; padding: 0 10px;
  border-radius: 999px; font-size: 11px; font-weight: 600;
}
.badge-gdpr { background: var(--brand-50); color: var(--brand-600); }
.badge-type { background: var(--background-200); color: var(--text-600); }
.badge-pending { background: #fff4e5; color: var(--state-warning); }
.badge-processing { background: var(--brand-50); color: var(--brand-600); }
.badge-completed { background: var(--state-success-surface); color: var(--state-success); }
.badge-rejected { background: var(--state-error-surface); color: var(--state-error); }
.request-footer { display: flex; align-items: center; gap: 16px; font-size: 12px; color: var(--text-400); }
.request-handler { flex: 1; }
.request-actions { display: flex; gap: 8px; }

/* 快速操作（设计稿 quick-action 卡片形态） */
.quick-action-hint { font-size: 12px; color: var(--text-400); font-weight: 400; }
.quick-action-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.quick-action-item {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 16px; border-radius: var(--radius);
  background: var(--card); border: 1px solid var(--border);
  box-shadow: var(--shadow-xs); cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.quick-action-item:hover { transform: translateY(-1px); box-shadow: var(--shadow-md); }
.quick-action-icon {
  width: 40px; height: 40px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.quick-action-text { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
.quick-action-title { font-size: 14px; font-weight: 500; color: var(--text-800); }
.quick-action-desc { font-size: 11px; color: var(--text-400); }
.quick-action-arrow { color: var(--text-300); flex-shrink: 0; }

/* 未成年人保护 */
.minor-title { display: inline-flex; align-items: center; gap: 6px; }
.minor-rules {
  display: flex; gap: 12px; align-items: flex-start;
  padding: 14px 16px; border-radius: var(--radius); margin-bottom: 16px;
  background: linear-gradient(135deg, var(--brand-50) 0%, var(--background-50) 100%);
  border: 1px solid var(--brand-200);
}
.minor-rules-icon { flex-shrink: 0; color: var(--brand-500); padding-top: 2px; }
.minor-rules-text { font-size: 13px; color: var(--text-600); line-height: 1.7; }
.minor-rules-text p { margin: 0; }
.minor-rules-text p + p { margin-top: 4px; }
.minor-rules-text b { color: var(--text-800); }
.minor-stats { display: flex; gap: 12px; margin-bottom: 16px; }
.minor-stat {
  flex: 1; border-radius: var(--radius); padding: 12px;
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  background: var(--card); border: 1px solid var(--border);
}
.minor-stat-value { font-size: 22px; font-weight: 700; }
.minor-stat-label { font-size: 12px; font-weight: 500; color: var(--text-500); }
.minor-stat-sub { font-size: 11px; color: var(--text-400); }
.minor-archive {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 12px; border-radius: var(--radius); margin-bottom: 20px;
  background: var(--card); border: 1px solid var(--border);
  font-size: 13px; font-weight: 500; color: var(--brand-500);
  cursor: pointer; transition: all 0.15s ease;
}
.minor-archive:hover { border-color: var(--brand-300); background: var(--brand-50); }
.minor-table-title {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 12px; font-size: 14px; font-weight: 600; color: var(--text-800);
}

/* 政策全文弹窗 */
.policy-full h3 { margin: 0 0 8px; font-size: 16px; color: var(--text-800); }
.policy-full p { margin: 0 0 12px; font-size: 12px; color: var(--text-400); }
.policy-content { font-size: 14px; color: var(--text-600); line-height: 1.8; white-space: pre-wrap; }

/* 窄屏快速操作单列 */
@media (max-width: 900px) {
  .quick-action-grid { grid-template-columns: 1fr; }
}
</style>
