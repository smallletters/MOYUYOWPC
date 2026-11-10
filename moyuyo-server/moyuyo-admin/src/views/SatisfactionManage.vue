<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>满意度管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">发起调查</el-button>
      </div>
    </div>

    <!-- ===== 调研概览 KPI（2x2 网格） ===== -->
    <section class="block-section">
      <div class="section-header">
        <h3 class="section-title">调研概览</h3>
      </div>
      <div class="kpi-grid">
        <div class="kpi-card">
          <span class="kpi-label">总调研数</span>
          <span class="kpi-value">{{ kpi.totalCount }}</span>
          <span class="kpi-trend up">实时统计</span>
        </div>
        <div class="kpi-card">
          <span class="kpi-label">问卷回收率</span>
          <span class="kpi-value">{{ surveyKpi.rate }}</span>
          <span class="kpi-trend up">近30天完成订单口径</span>
        </div>
        <div class="kpi-card">
          <span class="kpi-label">平均满意度</span>
          <span class="kpi-value">{{ kpi.avgScore }}</span>
          <span class="kpi-trend up">实时统计</span>
        </div>
        <div class="kpi-card">
          <span class="kpi-label">低分预警数</span>
          <span class="kpi-value danger">{{ surveyKpi.lowScore }}</span>
          <span class="kpi-trend down">近30天评分≤3</span>
        </div>
      </div>
    </section>

    <!-- ===== 调研触发规则 ===== -->
    <section class="block-section">
      <div class="section-header">
        <h3 class="section-title">调研触发规则</h3>
      </div>
      <div class="admin-card">
        <!-- 触发时机开关 -->
        <div v-for="rule in triggerRules" :key="rule.id" class="rule-item">
          <div>
            <div class="rule-label">{{ rule.label }}</div>
            <div class="rule-desc">{{ rule.desc }}</div>
          </div>
          <el-switch v-model="rule.enabled" @change="onRuleToggle(rule)" />
        </div>
        <!-- 触发延迟 -->
        <div class="rule-item rule-column">
          <div class="rule-row">
            <div>
              <div class="rule-label">触发延迟</div>
              <div class="rule-desc">事件发生后等待多久发送问卷</div>
            </div>
            <div class="rule-row-inline">
              <el-input-number v-model="delayHours" :min="1" :max="168" size="small" controls-position="right" @change="saveSettings" />
              <span class="rule-unit">小时后</span>
            </div>
          </div>
        </div>
        <!-- 发送渠道 -->
        <div class="rule-item rule-column">
          <div class="rule-row">
            <div>
              <div class="rule-label">发送渠道</div>
              <div class="rule-desc">选择问卷发送方式（可多选）</div>
            </div>
            <div class="channel-group">
              <span
                v-for="ch in channelOptions"
                :key="ch"
                :class="['channel-tag', { selected: selectedChannels.includes(ch) }]"
                @click="toggleChannel(ch)"
              >
                {{ ch }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 问卷模板 ===== -->
    <section class="block-section">
      <div class="section-header">
        <h3 class="section-title">问卷模板</h3>
        <el-link type="primary" :underline="false" @click="onViewAllTemplates">查看全部</el-link>
      </div>
      <div class="admin-card">
        <div v-for="tpl in surveyTemplates" :key="tpl.id" class="template-row">
          <div class="template-top">
            <div>
              <div class="template-name">{{ tpl.name }}</div>
              <div class="template-desc">{{ tpl.desc }}</div>
            </div>
            <span :class="['status-badge', tpl.status]">{{ tpl.statusText }}</span>
          </div>
          <div class="template-meta">
            <span>{{ tpl.questions }} 道题目</span>
            <span>约 {{ tpl.minutes }} 分钟</span>
          </div>
          <div class="action-group">
            <el-button type="primary" link size="small" @click="onEditTemplate(tpl)">编辑</el-button>
            <el-button type="primary" link size="small" @click="onPreviewTemplate(tpl)">预览</el-button>
            <el-button type="primary" link size="small" :disabled="tpl.status === 'draft'" @click="onTemplateAnalytics(tpl)">数据分析</el-button>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 近 30 天满意度趋势（纯 CSS 柱状图 + SVG 折线，未引入 ECharts） ===== -->
    <section class="block-section">
      <div class="section-header">
        <h3 class="section-title">近 30 天满意度趋势</h3>
        <el-link type="primary" :underline="false" @click="onTrendDetail">详情</el-link>
      </div>
      <div class="admin-card">
        <!-- 纵轴刻度 -->
        <div class="trend-y-axis">
          <span>5.0</span>
          <span>0.0</span>
        </div>
        <!-- 图表主体：CSS 柱状图（回收量）+ SVG 折线（满意度评分） -->
        <div class="trend-chart">
          <div class="trend-bars">
            <div v-for="item in trendData" :key="item.date" class="trend-bar">
              <div class="trend-bar-inner" :style="{ height: barHeight(item) }"></div>
            </div>
            <svg class="trend-line" viewBox="0 0 340 150" preserveAspectRatio="none">
              <line x1="0" y1="0" x2="340" y2="0" stroke="var(--border)" stroke-width="0.5" stroke-dasharray="4,4" />
              <line x1="0" y1="37" x2="340" y2="37" stroke="var(--border)" stroke-width="0.5" stroke-dasharray="4,4" />
              <line x1="0" y1="74" x2="340" y2="74" stroke="var(--border)" stroke-width="0.5" stroke-dasharray="4,4" />
              <line x1="0" y1="111" x2="340" y2="111" stroke="var(--border)" stroke-width="0.5" stroke-dasharray="4,4" />
              <line x1="0" y1="150" x2="340" y2="150" stroke="var(--border)" stroke-width="0.5" />
              <polyline :points="linePoints" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </div>
          <!-- X 轴日期 -->
          <div class="trend-x-axis">
            <span v-for="label in axisLabels" :key="label">{{ label }}</span>
          </div>
          <!-- 图例 -->
          <div class="trend-legend">
            <div class="legend-item">
              <div class="legend-line"></div>
              <span>满意度评分</span>
            </div>
            <div class="legend-item">
              <div class="legend-bar"></div>
              <span>问卷回收量</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 最新低分反馈 ===== -->
    <section class="block-section">
      <div class="section-header">
        <h3 class="section-title">最新低分反馈</h3>
        <el-link type="primary" :underline="false" @click="onViewAllFeedbacks">查看全部</el-link>
      </div>
      <div class="admin-card">
        <div v-for="fb in lowScoreFeedbacks" :key="fb.id" class="feedback-row">
          <div class="feedback-top">
            <div class="feedback-user">
              <div class="feedback-avatar">{{ fb.user.slice(0, 2) }}</div>
              <div>
                <div class="feedback-name">{{ fb.user }}</div>
                <div class="feedback-time">{{ fb.time }}</div>
              </div>
            </div>
            <div class="star-rating">
              <span v-for="n in 5" :key="n" :class="['star', n <= fb.score ? 'filled' : 'empty']">★</span>
            </div>
          </div>
          <div class="feedback-content">{{ fb.content }}</div>
          <div class="feedback-bottom">
            <div class="feedback-source">{{ fb.source }}</div>
            <div class="feedback-actions">
              <span :class="['status-badge', fb.status]">{{ fb.statusText }}</span>
              <el-button type="primary" link size="small" @click="onViewFeedback(fb)">查看详情</el-button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 评价记录（保留原有功能） ===== -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>评价记录</span>
        </div>
      </template>
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="userId" label="用户ID" width="120" />
        <el-table-column prop="comment" label="评价内容" min-width="250" show-overflow-tooltip />
        <el-table-column prop="score" label="评分" width="140">
          <template #default="{ row }">
            <el-rate v-model="row.score" disabled :colors="rateColors" score-template="{value}" :texts="['','','','','']" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="评价时间" width="180" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleReply(row)">回复</el-button>
            <el-button type="primary" link size="small" @click="handleView(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 发起调查弹窗（保留原有功能） -->
    <el-dialog v-model="createDialogVisible" title="发起满意度调查" width="520px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="用户ID" required>
          <el-input v-model="createForm.userId" placeholder="输入用户ID" />
        </el-form-item>
        <el-form-item label="关联分类">
          <el-select v-model="createForm.category" placeholder="选择分类" clearable style="width: 100%">
            <el-option label="服务" value="SERVICE" />
            <el-option label="商品质量" value="QUALITY" />
            <el-option label="物流" value="LOGISTICS" />
          </el-select>
        </el-form-item>
        <el-form-item label="评分" required>
          <el-rate v-model="createForm.score" :colors="rateColors" />
        </el-form-item>
        <el-form-item label="评价内容">
          <el-input v-model="createForm.comment" type="textarea" :rows="3" placeholder="输入评价内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 回复评价弹窗（保留原有功能） -->
    <el-dialog v-model="replyDialogVisible" title="回复评价" width="480px">
      <el-form label-width="90px">
        <el-form-item label="原评价">
          <div class="reply-origin">{{ currentRow?.comment || '-' }}</div>
        </el-form-item>
        <el-form-item label="回复内容" required>
          <el-input v-model="replyText" type="textarea" :rows="4" placeholder="输入回复内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="replying" @click="submitReply">提交回复</el-button>
      </template>
    </el-dialog>

    <!-- 评价详情弹窗（保留原有功能） -->
    <el-dialog v-model="viewDialogVisible" title="评价详情" width="520px">
      <el-descriptions v-if="currentRow" :column="1" border>
        <el-descriptions-item label="用户ID">{{ currentRow.userId }}</el-descriptions-item>
        <el-descriptions-item label="评分">{{ currentRow.score }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ currentRow.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="评价内容">{{ currentRow.comment || '-' }}</el-descriptions-item>
        <el-descriptions-item label="评价时间">{{ currentRow.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 模板编辑 / 预览 / 数据分析 弹窗 -->
    <el-dialog
      v-model="templateDialogVisible"
      :title="templateDialogMode === 'edit' ? '编辑模板' : templateDialogMode === 'analytics' ? '模板数据分析' : '预览模板'"
      width="560px"
    >
      <div v-if="currentTemplate">
        <template v-if="templateDialogMode === 'preview'">
          <p><strong>模板名称：</strong>{{ currentTemplate.name }}</p>
          <p><strong>描述：</strong>{{ currentTemplate.desc }}</p>
          <p><strong>题数：</strong>{{ currentTemplate.questions }}</p>
          <p><strong>预计用时：</strong>约 {{ currentTemplate.minutes }} 分钟</p>
          <p><strong>状态：</strong>{{ currentTemplate.statusText }}</p>
        </template>
        <template v-else-if="templateDialogMode === 'edit'">
          <el-form label-width="90px">
            <el-form-item label="模板名称">
              <el-input v-model="currentTemplate.name" />
            </el-form-item>
            <el-form-item label="模板说明">
              <el-input v-model="currentTemplate.desc" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="题目数">
              <el-input-number v-model="currentTemplate.questions" :min="1" :max="30" />
            </el-form-item>
          </el-form>
        </template>
        <template v-else>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="发送量">{{ templateAnalytics.sent }}</el-descriptions-item>
            <el-descriptions-item label="回收量">{{ templateAnalytics.replies }}</el-descriptions-item>
            <el-descriptions-item label="平均评分">{{ templateAnalytics.avgScore }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
      <template #footer>
        <el-button @click="templateDialogVisible = false">关闭</el-button>
        <el-button v-if="templateDialogMode === 'edit'" type="primary" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 趋势详情 -->
    <el-dialog v-model="trendDialogVisible" title="近 30 天满意度趋势明细" width="640px">
      <el-table :data="trendData" stripe>
        <el-table-column prop="date" label="日期" width="120" />
        <el-table-column prop="score" label="满意度评分" width="120" />
        <el-table-column prop="count" label="问卷回收量" />
      </el-table>
    </el-dialog>

    <!-- 反馈全部列表 -->
    <el-dialog v-model="allFeedbacksDialogVisible" title="全部低分反馈" width="640px">
      <el-table :data="allFeedbacks" stripe>
        <el-table-column prop="user" label="用户" width="120" />
        <el-table-column prop="content" label="反馈内容" min-width="200" />
        <el-table-column prop="score" label="评分" width="80" />
      </el-table>
    </el-dialog>

    <!-- 单条反馈详情 -->
    <el-dialog v-model="feedbackDialogVisible" title="反馈详情" width="480px">
      <el-descriptions v-if="currentFeedback" :column="1" border>
        <el-descriptions-item label="用户">{{ currentFeedback.user }}</el-descriptions-item>
        <el-descriptions-item label="评分">{{ currentFeedback.score }}</el-descriptions-item>
        <el-descriptions-item label="内容">{{ currentFeedback.content }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ currentFeedback.source }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ currentFeedback.time }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getSatisfactionStats, getSatisfactionList, createSatisfactionSurvey, replySatisfactionSurvey,
  getSatisfactionTemplates, updateSatisfactionTemplate, getSatisfactionRules,
  toggleSatisfactionRule, saveSatisfactionRuleSettings, getSatisfactionTrend,
  getSatisfactionLowScore, getSatisfactionOverview
} from '../api/admin'
import { toArray } from '../utils/safeArray'

const rateColors = ['#f56c6c', '#e6a23c', '#5cb87a', '#1989fa', '#f59e0b']

// ---- 真实 API：满意度统计 ----
const kpi = ref({ avgScore: '—', totalCount: '—' })
const tableData = ref([])

// ---- 真实 API：调研概览补充指标 ----
const surveyKpi = reactive({ rate: '0%', lowScore: 0, surveyCount: 0, completedOrders: 0 })

// 调研触发规则（来自 /satisfaction/rules）
const triggerRules = ref([])
const delayHours = ref(24)
const channelOptions = ['App 内', '邮件', '短信']
const selectedChannels = ref([])

// 问卷模板列表（来自 /satisfaction/templates）
const surveyTemplates = ref([])

// 近 30 天满意度趋势（来自 /satisfaction/trend）
const trendData = ref([])

// 趋势图最大值与坐标（随真实数据变化）
const MAX_COUNT = computed(() => Math.max(...trendData.value.map(t => t.count), 1))

// 柱状图高度（百分比，基于最大值）
function barHeight(item) {
  return ((item.count / MAX_COUNT.value) * 100).toFixed(1) + '%'
}

// SVG 折线坐标（viewBox 0 0 340 150，分数 5.0 对应顶部）
const linePoints = computed(() => {
  const list = trendData.value
  if (list.length < 2) return ''
  return list.map((item, i) => {
    const x = (i / (list.length - 1)) * 340
    const y = (5 - Number(item.score || 0)) * 26 + 8
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
})

// X 轴刻度：取首、1/4、1/2、3/4、末 五个日期
const axisLabels = computed(() => {
  const list = trendData.value
  const n = list.length
  if (!n) return []
  return [0, Math.floor(n / 4), Math.floor(n / 2), Math.floor(n * 3 / 4), n - 1].map(i => list[i].date)
})

// 最新低分反馈（来自 /satisfaction/low-score）
const lowScoreFeedbacks = ref([])

// ---- 加载调研概览补充指标 ----
async function loadOverview() {
  try {
    const res = await getSatisfactionOverview()
    if (res) {
      surveyKpi.rate = (res.recycleRate ?? 0) + '%'
      surveyKpi.lowScore = res.lowScore ?? 0
      surveyKpi.surveyCount = res.surveyCount ?? 0
      surveyKpi.completedOrders = res.completedOrders ?? 0
    }
  } catch (e) {
    console.error('获取调研概览失败', e)
  }
}

// ---- 加载触发规则与发送设置 ----
async function loadRules() {
  try {
    const res = await getSatisfactionRules()
    triggerRules.value = (res?.rules || []).map(r => ({ ...r }))
    const settings = res?.settings || {}
    delayHours.value = settings.delayHours ?? 24
    selectedChannels.value = Array.isArray(settings.channels) ? settings.channels : []
  } catch (e) {
    console.error('获取调研规则失败', e)
    triggerRules.value = []
  }
}

// 规则开关切换：真实保存
async function onRuleToggle(rule) {
  try {
    await toggleSatisfactionRule(rule.id, rule.enabled)
    ElMessage.success(`规则「${rule.label}」已${rule.enabled ? '启用' : '禁用'}`)
  } catch (e) {
    rule.enabled = !rule.enabled
    ElMessage.error('更新规则失败：' + (e.message || '未知错误'))
  }
}

// 保存延迟与渠道设置
async function saveSettings() {
  try {
    await saveSatisfactionRuleSettings({
      delayHours: delayHours.value,
      channels: selectedChannels.value
    })
    ElMessage.success('发送设置已保存')
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '未知错误'))
  }
}

// ---- 加载问卷模板 ----
async function loadTemplates() {
  try {
    const res = await getSatisfactionTemplates()
    surveyTemplates.value = toArray(res)
  } catch (e) {
    console.error('获取问卷模板失败', e)
    surveyTemplates.value = []
  }
}

// ---- 加载近 30 天趋势 ----
async function loadTrend() {
  try {
    const res = await getSatisfactionTrend(30)
    trendData.value = toArray(res)
  } catch (e) {
    console.error('获取满意度趋势失败', e)
    trendData.value = []
  }
}

// ---- 加载最新低分反馈 ----
async function loadLowScore() {
  try {
    const res = await getSatisfactionLowScore(10)
    lowScoreFeedbacks.value = toArray(res)
  } catch (e) {
    console.error('获取低分反馈失败', e)
    lowScoreFeedbacks.value = []
  }
}

// 弹窗状态
const createDialogVisible = ref(false)
const creating = ref(false)
const createForm = reactive({ userId: '', category: '', score: 5, comment: '' })
const replyDialogVisible = ref(false)
const replying = ref(false)
const currentRow = ref(null)
const replyText = ref('')
const viewDialogVisible = ref(false)

// ---- 真实 API：统计与列表 ----
async function loadStats() {
  try {
    const res = await getSatisfactionStats()
    if (res) {
      kpi.value = {
        avgScore: res.avgScore ?? '—',
        totalCount: res.totalCount ?? '—'
      }
    }
  } catch (err) {
    console.error('获取满意度统计失败', err)
  }
}

async function loadList() {
  try {
    // 后端返回 Page<Map>：{records, total, current, size}
    const res = await getSatisfactionList({ page: 1, size: 50 })
    if (res && Array.isArray(res.records)) {
      tableData.value = res.records
    } else {
      tableData.value = toArray(res)
    }
  } catch (err) {
    console.error('获取满意度列表失败', err)
    ElMessage.error('评价列表加载失败')
    tableData.value = []
  }
}

// ---- 调研触发规则：渠道多选（变更后立即保存） ----
function toggleChannel(ch) {
  if (selectedChannels.value.includes(ch)) {
    selectedChannels.value = selectedChannels.value.filter(c => c !== ch)
  } else {
    selectedChannels.value = [...selectedChannels.value, ch]
  }
  saveSettings()
}

// ---- 问卷模板详情查看 ----
// 模板保存（真实更新 mo_satisfaction_template）
async function saveTemplate() {
  if (!currentTemplate.value?.name) {
    ElMessage.warning('请输入模板名称')
    return
  }
  try {
    await updateSatisfactionTemplate(currentTemplate.value.id, {
      name: currentTemplate.value.name,
      desc: currentTemplate.value.desc,
      questions: currentTemplate.value.questions,
      minutes: currentTemplate.value.minutes,
      status: currentTemplate.value.status
    })
    ElMessage.success('模板已保存：' + currentTemplate.value.name)
    templateDialogVisible.value = false
    await loadTemplates()
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '未知错误'))
  }
}

// 模板详情弹窗
const templateDialogVisible = ref(false)
const templateDialogMode = ref('preview') // preview / analytics
const currentTemplate = ref(null)
const currentFeedback = ref(null)
const feedbackDialogVisible = ref(false)

// 趋势详情：打开 Dialog 显示 30 天明细
const trendDialogVisible = ref(false)

// 反馈全部列表
const allFeedbacksDialogVisible = ref(false)
const allFeedbacks = ref([])

function onViewAllTemplates() {
  // 真实场景：跳转到问卷模板管理页 / 打开模板列表弹窗
  ElMessageBox.alert('问卷模板共 ' + surveyTemplates.value.length + ' 份，可在「编辑」中进行修改', '模板列表', {
    confirmButtonText: '我知道了'
  })
}

function onEditTemplate(tpl) {
  currentTemplate.value = tpl
  templateDialogMode.value = 'edit'
  templateDialogVisible.value = true
}

function onPreviewTemplate(tpl) {
  currentTemplate.value = tpl
  templateDialogMode.value = 'preview'
  templateDialogVisible.value = true
}

function onTemplateAnalytics(tpl) {
  currentTemplate.value = tpl
  templateDialogMode.value = 'analytics'
  templateDialogVisible.value = true
  loadTemplateAnalytics(tpl)
}

function onTrendDetail() {
  trendDialogVisible.value = true
}

async function onViewAllFeedbacks() {
  allFeedbacksDialogVisible.value = true
  // 低分反馈真实数据（score <= 3）
  try {
    allFeedbacks.value = await getSatisfactionLowScore(100)
  } catch (e) {
    console.error('获取低分反馈失败', e)
    allFeedbacks.value = []
  }
}

function onViewFeedback(fb) {
  currentFeedback.value = fb
  feedbackDialogVisible.value = true
}

// 模板数据分析：按近 30 天整体调研数据展示（模板级明细待埋点接入）
const templateAnalytics = ref({ sent: 0, replies: 0, avgScore: 0 })
async function loadTemplateAnalytics(tpl) {
  templateAnalytics.value = {
    sent: surveyKpi.completedOrders,
    replies: surveyKpi.surveyCount,
    avgScore: Number(kpi.value.avgScore) || 0
  }
}

// ---- 发起调查 ----
function handleAdd() {
  createForm.userId = ''
  createForm.category = ''
  createForm.score = 5
  createForm.comment = ''
  createDialogVisible.value = true
}

async function submitCreate() {
  if (!createForm.userId.trim()) {
    ElMessage.warning('请输入用户ID')
    return
  }
  creating.value = true
  try {
    await createSatisfactionSurvey({
      userId: createForm.userId.trim(),
      category: createForm.category || undefined,
      score: createForm.score,
      comment: createForm.comment
    })
    ElMessage.success('满意度调查已创建')
    createDialogVisible.value = false
    loadStats()
    loadList()
  } catch (err) {
    console.error('创建满意度调查失败', err)
    ElMessage.error('创建失败：' + (err?.message || '未知错误'))
  } finally {
    creating.value = false
  }
}

// ---- 回复评价 ----
function handleReply(row) {
  currentRow.value = row
  replyText.value = ''
  replyDialogVisible.value = true
}

async function submitReply() {
  if (!replyText.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  replying.value = true
  try {
    await replySatisfactionSurvey(currentRow.value.id, { reply: replyText.value.trim() })
    ElMessage.success('回复成功')
    replyDialogVisible.value = false
    loadList()
  } catch (err) {
    console.error('回复失败', err)
    ElMessage.error('回复失败：' + (err?.message || '未知错误'))
  } finally {
    replying.value = false
  }
}

// ---- 查看详情 ----
function handleView(row) {
  currentRow.value = row
  viewDialogVisible.value = true
}

onMounted(() => {
  loadStats()
  loadList()
  loadOverview()
  loadRules()
  loadTemplates()
  loadTrend()
  loadLowScore()
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }

/* ===== 区块通用 ===== */
.block-section { margin-bottom: 20px; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.section-title { font-size: 16px; font-weight: 700; color: var(--text-800); margin: 0; }
.admin-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 16px;
}

/* ===== 调研概览 KPI ===== */
.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.kpi-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kpi-card .kpi-label { font-size: 12px; font-weight: 600; color: var(--text-500); }
.kpi-card .kpi-value { font-size: 26px; font-weight: 700; color: var(--text-800); line-height: 1.2; }
.kpi-card .kpi-value.danger { color: var(--state-error); }
.kpi-card .kpi-trend { font-size: 11px; font-weight: 500; }
.kpi-trend.up { color: var(--state-success); }
.kpi-trend.down { color: var(--state-error); }

/* ===== 调研触发规则 ===== */
.rule-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}
.rule-item:last-child { border-bottom: none; padding-bottom: 0; }
.rule-item.rule-column { flex-direction: column; align-items: flex-start; gap: 10px; }
.rule-row { display: flex; align-items: center; justify-content: space-between; width: 100%; gap: 12px; }
.rule-row-inline { display: flex; align-items: center; gap: 8px; }
.rule-unit { font-size: 13px; color: var(--text-500); font-weight: 500; white-space: nowrap; }
.rule-label { font-size: 14px; font-weight: 500; color: var(--text-800); }
.rule-desc { font-size: 12px; color: var(--text-400); margin-top: 2px; }
.channel-group { display: flex; gap: 8px; flex-wrap: wrap; }
.channel-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
  border: 1px solid var(--border);
  color: var(--text-600);
  background: var(--background);
  cursor: pointer;
  transition: all 0.15s ease;
}
.channel-tag.selected { border-color: var(--primary); background: var(--brand-50); color: var(--primary); }

/* ===== 问卷模板 ===== */
.template-row { padding: 14px 0; border-bottom: 1px solid var(--border); }
.template-row:first-child { padding-top: 0; }
.template-row:last-child { border-bottom: none; padding-bottom: 0; }
.template-top { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 6px; gap: 12px; }
.template-name { font-size: 15px; font-weight: 600; color: var(--text-800); }
.template-desc { font-size: 12px; color: var(--text-400); margin-top: 2px; }
.template-meta { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; font-size: 12px; color: var(--text-500); }
.action-group { display: flex; gap: 4px; }

/* 状态标签 */
.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.status-badge.active { background: var(--state-success-surface); color: var(--state-success); }
.status-badge.disabled { background: var(--background-200); color: var(--text-500); }
.status-badge.draft { background: var(--brand-50); color: var(--brand-600); }
.status-badge.pending { background: var(--state-error-surface); color: var(--state-error); }

/* ===== 近 30 天满意度趋势（纯 CSS 柱状图 + SVG 折线） ===== */
.trend-y-axis { display: flex; align-items: flex-end; justify-content: space-between; height: 16px; margin-bottom: 6px; }
.trend-y-axis span { font-size: 11px; color: var(--text-400); }
.trend-chart { position: relative; }
.trend-bars {
  position: relative;
  height: 150px;
  display: flex;
  align-items: flex-end;
  gap: 3px;
}
.trend-bar { flex: 1; height: 100%; display: flex; align-items: flex-end; }
.trend-bar-inner {
  width: 100%;
  border-radius: 3px 3px 0 0;
  background: var(--brand-200);
  opacity: 0.5;
  min-height: 2px;
}
.trend-line { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
.trend-x-axis { display: flex; justify-content: space-between; padding-top: 6px; }
.trend-x-axis span { font-size: 10px; color: var(--text-400); }
.trend-legend { display: flex; align-items: center; justify-content: center; gap: 20px; margin-top: 10px; }
.legend-item { display: flex; align-items: center; gap: 4px; font-size: 11px; color: var(--text-500); }
.legend-line { width: 12px; height: 3px; border-radius: 2px; background: var(--primary); }
.legend-bar { width: 8px; height: 10px; border-radius: 2px; background: var(--brand-200); opacity: 0.7; }

/* ===== 最新低分反馈 ===== */
.feedback-row { padding: 14px 0; border-bottom: 1px solid var(--border); }
.feedback-row:first-child { padding-top: 0; }
.feedback-row:last-child { border-bottom: none; padding-bottom: 0; }
.feedback-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.feedback-user { display: flex; align-items: center; gap: 8px; }
.feedback-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--secondary);
  color: var(--text-500);
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.feedback-name { font-size: 13px; font-weight: 600; color: var(--text-800); }
.feedback-time { font-size: 11px; color: var(--text-400); }
.feedback-content {
  font-size: 13px;
  color: var(--text-600);
  line-height: 1.5;
  margin-bottom: 8px;
  padding: 8px 10px;
  background: var(--secondary);
  border-radius: var(--radius-sm);
}
.feedback-bottom { display: flex; align-items: center; justify-content: space-between; }
.feedback-source { font-size: 11px; color: var(--text-400); }
.feedback-actions { display: flex; align-items: center; gap: 8px; }

/* 星级评分 */
.star-rating { display: inline-flex; gap: 2px; }
.star-rating .star { font-size: 14px; line-height: 1; }
.star-rating .star.filled { color: var(--state-warning); }
.star-rating .star.empty { color: var(--background-400); }

/* ===== 评价记录卡片头 ===== */
.card-header { font-size: 15px; font-weight: 600; color: var(--text-800); }

.reply-origin {
  max-height: 90px; overflow-y: auto; width: 100%;
  font-size: 13px; color: var(--text-600); line-height: 1.6;
  white-space: pre-wrap; background: var(--background-100);
  border-radius: var(--radius); padding: 8px 10px;
}
</style>
