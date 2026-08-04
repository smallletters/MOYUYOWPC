<template>
  <div class="analytics">
    <!-- 标题栏 -->
    <div class="page-header">
      <h2 class="page-title">数据分析</h2>
      <div class="time-selector">
        <button
          v-for="opt in timeOptions"
          :key="opt.key"
          class="time-btn"
          :class="{ active: activeTime === opt.key }"
          @click="activeTime = opt.key"
        >
          {{ opt.label }}
        </button>
      </div>
    </div>

    <div class="analytics-grid">
      <!-- 左栏：漏斗 -->
      <div class="analytics-main">
        <!-- 漏斗图 -->
        <div class="card">
          <div class="card-header">
            <h3>转化漏斗</h3>
            <button class="btn btn-sm btn-outline" @click="openCustomFunnelDialog">自定义漏斗</button>
          </div>
          <div class="card-body">
            <div class="funnel-chart">
              <div v-for="(step, idx) in funnelSteps" :key="step.key" class="funnel-step">
                <div class="funnel-label">
                  <span>{{ step.label }}</span>
                  <span class="funnel-count">{{ step.count }}</span>
                </div>
                <div class="funnel-bar-wrap">
                  <div
                    class="funnel-bar"
                    :style="{ width: step.width + '%' }"
                  >
                    <span class="funnel-rate">{{ step.rate }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 渠道对比 -->
        <div class="card">
          <div class="card-header">
            <h3>渠道转化对比</h3>
          </div>
          <div class="card-body">
            <div class="channel-compare">
              <div class="channel-group" v-for="ch in channels" :key="ch.name">
                <div class="channel-header">
                  <span class="channel-name">{{ ch.name }}</span>
                  <span class="channel-value">{{ ch.value }}</span>
                </div>
                <div class="channel-bars">
                  <div class="channel-bar-row">
                    <span class="bar-label">曝光</span>
                    <div class="bar-track">
                      <div class="bar-fill-ch" :style="{ width: ch.impression + '%', background: ch.color }"></div>
                    </div>
                    <span class="bar-num">{{ ch.impression }}k</span>
                  </div>
                  <div class="channel-bar-row">
                    <span class="bar-label">点击</span>
                    <div class="bar-track">
                      <div class="bar-fill-ch" :style="{ width: ch.click + '%', background: ch.color }"></div>
                    </div>
                    <span class="bar-num">{{ ch.click }}k</span>
                  </div>
                  <div class="channel-bar-row">
                    <span class="bar-label">转化</span>
                    <div class="bar-track">
                      <div class="bar-fill-ch" :style="{ width: ch.conversion + '%', background: ch.color }"></div>
                    </div>
                    <span class="bar-num">{{ ch.conversion }}k</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏：流失分析 -->
      <div class="analytics-side">
        <div class="card">
          <div class="card-header">
            <h3>流失分析</h3>
          </div>
          <div class="card-body">
            <div class="churn-point">
              <div class="churn-badge">最大流失</div>
              <div class="churn-step">{{ churnStep }}</div>
              <div class="churn-desc">此步骤流失率高达 {{ churnRate }}%，建议优化结算流程</div>
            </div>
            <div class="churn-reasons">
              <h4>主要原因</h4>
              <ul>
                <li v-for="(reason, idx) in churnReasons" :key="idx">{{ reason }}</li>
              </ul>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-header">
            <h3>复购率</h3>
          </div>
          <div class="card-body">
            <div class="repurchase-rate">
              <span class="rate-number">{{ repurchaseRate }}%</span>
              <span class="rate-trend up">↑ {{ repurchaseTrend }}%</span>
            </div>
            <div class="rate-subtitle">30天复购率</div>
            <div class="rate-bar">
              <div class="rate-bar-fill" :style="{ width: repurchaseRate + '%' }"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 自定义漏斗对话框 -->
    <el-dialog v-model="customFunnelVisible" title="自定义漏斗" width="520px">
      <el-form label-width="90px">
        <el-form-item label="漏斗名称">
          <el-input v-model="customFunnel.name" placeholder="例如：新用户转化漏斗" />
        </el-form-item>
        <el-form-item label="起始步骤">
          <el-select v-model="customFunnel.startStep" placeholder="选择起始事件" style="width:100%">
            <el-option v-for="opt in funnelStepOptions" :key="opt.key" :label="opt.label" :value="opt.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="结束步骤">
          <el-select v-model="customFunnel.endStep" placeholder="选择结束事件" style="width:100%">
            <el-option v-for="opt in funnelStepOptions" :key="opt.key" :label="opt.label" :value="opt.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-radio-group v-model="customFunnel.timeRange">
            <el-radio-button label="7d">近 7 天</el-radio-button>
            <el-radio-button label="30d">近 30 天</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="customFunnelVisible = false">取消</el-button>
        <el-button type="primary" @click="applyCustomFunnel">应用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getFunnelAnalysis, getMarketingEffects } from '../api/admin'
import { toArray } from '../utils/safeArray'

const activeTime = ref('7d')

const timeOptions = [
  { key: '7d', label: '近7天' },
  { key: '30d', label: '近30天' },
  { key: 'custom', label: '自定义' }
]

// 时间范围切换时重新加载数据
watch(activeTime, () => {
  loadFunnelData()
  loadChannelData()
})

const funnelSteps = reactive([])
const channels = reactive([])

// 流失分析数据
const churnRate = ref(42) // 流失率（百分比）
const churnStep = ref('结算 → 提交订单') // 流失步骤
const churnReasons = ref([
  '运费过高导致用户放弃',
  '支付方式不够丰富',
  '登录流程繁琐'
]) // 流失原因列表
// 复购率数据
const repurchaseRate = ref(23) // 复购率（百分比）
const repurchaseTrend = ref(2.3) // 复购率涨幅（百分比）

// 自定义漏斗：对话框 & 表单
const customFunnelVisible = ref(false)
const customFunnel = reactive({
  name: '',
  startStep: '',
  endStep: '',
  timeRange: '7d'
})

// 漏斗可选步骤
const funnelStepOptions = [
  { key: 'visit', label: '访问' },
  { key: 'view_product', label: '浏览商品' },
  { key: 'add_cart', label: '加入购物车' },
  { key: 'checkout', label: '结算' },
  { key: 'submit_order', label: '提交订单' },
  { key: 'pay', label: '完成支付' }
]

function openCustomFunnelDialog() {
  customFunnel.name = ''
  customFunnel.startStep = ''
  customFunnel.endStep = ''
  customFunnel.timeRange = '7d'
  customFunnelVisible.value = true
}

async function applyCustomFunnel() {
  if (!customFunnel.name.trim()) {
    ElMessage.warning('请输入漏斗名称')
    return
  }
  if (!customFunnel.startStep || !customFunnel.endStep) {
    ElMessage.warning('请选择起止步骤')
    return
  }
  try {
    // 调用后端：POST /analysis/custom-funnel
    const days = customFunnel.timeRange === '30d' ? 30 : 7
    const res = await getFunnelAnalysis({ days, from: customFunnel.startStep, to: customFunnel.endStep })
    const funnelList = toArray(res)
    if (funnelList.length > 0) {
      const maxCount = Math.max(...funnelList.map(s => s.userCount || 0), 1)
      funnelSteps.splice(0, funnelSteps.length, ...funnelList.map((s, idx) => ({
        key: s.key || s.id || idx,
        label: s.label || s.name || funnelStepOptions.find(o => o.key === s.key)?.label || '步骤',
        count: s.userCount || 0,
        width: Math.round(((s.userCount || 0) / maxCount) * 100),
        rate: idx === 0 ? '100%' : Math.round(((s.userCount || 0) / funnelList[0].userCount || 0) * 100) + '%'
      })))
    }
    ElMessage.success('已应用自定义漏斗：' + customFunnel.name)
    customFunnelVisible.value = false
  } catch (e) {
    // 本地降级：基于现有 funnelSteps 裁剪
    const fromIdx = funnelStepOptions.findIndex(o => o.key === customFunnel.startStep)
    const toIdx = funnelStepOptions.findIndex(o => o.key === customFunnel.endStep)
    if (fromIdx >= 0 && toIdx >= 0 && fromIdx <= toIdx && funnelSteps.length > 0) {
      const slice = funnelSteps.slice(fromIdx, toIdx + 1)
      funnelSteps.splice(0, funnelSteps.length, ...slice)
      ElMessage.success('已应用自定义漏斗（本地模式）：' + customFunnel.name)
    } else {
      ElMessage.info('已应用：' + customFunnel.name)
    }
    customFunnelVisible.value = false
  }
}

// 加载漏斗数据
async function loadFunnelData() {
  try {
    // 将时间范围参数传递给API
    const days = activeTime.value === '30d' ? 30 : 7
    const res = await getFunnelAnalysis({ days })
    const funnelList = toArray(res)
    if (funnelList.length > 0) {
      funnelSteps.length = 0
      const maxCount = Math.max(...funnelList.map(s => s.userCount || 0), 1)
      funnelSteps.push(...funnelList.map((s, idx) => ({
        key: s.stage || idx,
        label: s.stage || '',
        count: s.userCount || 0,
        width: (s.userCount || 0) / maxCount * 100,
        rate: (s.conversionRate != null ? s.conversionRate + '%' : '0%')
      })))
    }
    // 从 API 返回值中提取流失分析与复购率数据（如果后端返回了这些字段则覆盖默认值）
    if (res && typeof res === 'object') {
      if (res.churnRate != null) churnRate.value = res.churnRate
      if (res.churnStep) churnStep.value = res.churnStep
      if (res.churnReasons) churnReasons.value = res.churnReasons
      if (res.repurchaseRate != null) repurchaseRate.value = res.repurchaseRate
      if (res.repurchaseTrend != null) repurchaseTrend.value = res.repurchaseTrend
    }
  } catch (err) {
    console.error('获取漏斗数据失败', err)
  }
}

// 加载渠道数据
async function loadChannelData() {
  try {
    const days = activeTime.value === '30d' ? 30 : 7
    const res = await getMarketingEffects({ days })
    // 后端返回 { totalGmv, campaignGmv, campaignRatio, totalOrders, channels: [...] }
    if (res) {
      const chList = toArray(res, 'channels')
      if (chList.length > 0) {
        channels.length = 0
        channels.push(...chList)
      } else if (toArray(res, 'campaigns').length > 0) {
        channels.length = 0
        channels.push(...toArray(res, 'campaigns').map(c => ({
          name: c.name || c.channel || '未知渠道',
          value: c.gmv ? `$${c.gmv}` : (c.value || '-'),
          impression: c.impression || 0,
          click: c.click || 0,
          conversion: c.conversion || 0,
          color: c.color || '#2563eb'
        })))
      }
    }
  } catch (err) {
    console.error('获取渠道数据失败', err)
  }
}

onMounted(() => {
  loadFunnelData()
  loadChannelData()
})
</script>

<style scoped lang="css">
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0;
}

.time-selector {
  display: flex;
  gap: 4px;
  background: var(--background-200);
  border-radius: var(--radius-sm);
  padding: 3px;
}

.time-btn {
  padding: 6px 16px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-500);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.time-btn.active {
  background: var(--card);
  color: var(--text-800);
  box-shadow: var(--shadow-xs);
}

.time-btn:hover:not(.active) {
  color: var(--text-600);
}

.analytics-grid {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.analytics-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.analytics-side {
  width: 340px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 漏斗 */
.funnel-chart {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.funnel-step {
  display: flex;
  align-items: center;
  gap: 16px;
}

.funnel-label {
  width: 100px;
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-600);
  flex-shrink: 0;
}

.funnel-count {
  font-weight: 600;
  color: var(--text-800);
}

.funnel-bar-wrap {
  flex: 1;
  height: 32px;
  display: flex;
  align-items: center;
}

.funnel-bar {
  height: 28px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--brand-500), var(--primary));
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 10px;
  transition: width 0.3s ease;
  min-width: 40px;
}

.funnel-rate {
  font-size: 11px;
  font-weight: 600;
  color: #fff;
}

/* 渠道对比 */
.channel-compare {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.channel-group {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}

.channel-group:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.channel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.channel-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
}

.channel-value {
  font-size: 12px;
  color: var(--text-400);
}

.channel-bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.channel-bar-row .bar-label {
  width: 32px;
  font-size: 11px;
  color: var(--text-400);
  flex-shrink: 0;
}

.bar-track {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: var(--background-100);
  overflow: hidden;
}

.bar-fill-ch {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s ease;
}

.bar-num {
  width: 36px;
  font-size: 11px;
  color: var(--text-400);
  text-align: right;
  flex-shrink: 0;
}

/* 流失分析 */
.churn-point {
  padding: 16px;
  background: var(--state-error-surface);
  border-radius: var(--radius-sm);
  margin-bottom: 16px;
}

.churn-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--state-error);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  margin-bottom: 8px;
}

.churn-step {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-800);
  margin-bottom: 4px;
}

.churn-desc {
  font-size: 12px;
  color: var(--text-500);
}

.churn-reasons h4 {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-700);
  margin: 0 0 8px;
}

.churn-reasons ul {
  margin: 0;
  padding-left: 16px;
  font-size: 12px;
  color: var(--text-500);
  line-height: 2;
}

/* 复购率 */
.repurchase-rate {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 4px;
}

.rate-number {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-800);
}

.rate-trend {
  font-size: 13px;
  font-weight: 600;
}

.rate-trend.up {
  color: var(--state-success);
}

.rate-subtitle {
  font-size: 12px;
  color: var(--text-400);
  margin-bottom: 12px;
}

.rate-bar {
  height: 8px;
  border-radius: 4px;
  background: var(--background-100);
  overflow: hidden;
}

.rate-bar-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--state-success);
  transition: width 0.3s ease;
}
</style>
