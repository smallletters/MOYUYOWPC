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
            <button class="btn btn-sm btn-outline">自定义漏斗</button>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getFunnelAnalysis, getMarketingEffects } from '../api/admin'

const activeTime = ref('7d')

const timeOptions = [
  { key: '7d', label: '近7天' },
  { key: '30d', label: '近30天' },
  { key: 'custom', label: '自定义' }
]

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

// 加载漏斗数据
async function loadFunnelData() {
  try {
    const res = await getFunnelAnalysis()
    if (res && Array.isArray(res)) {
      funnelSteps.length = 0
      const maxCount = Math.max(...res.map(s => s.userCount || 0), 1)
      funnelSteps.push(...res.map((s, idx) => ({
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
    const res = await getMarketingEffects()
    // 后端返回 { totalGmv, campaignGmv, campaignRatio, totalOrders, ... }
    // 目前无 channels 数据，保留空数组
    if (res && Array.isArray(res.channels)) {
      channels.length = 0
      channels.push(...res.channels)
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
  font-size: 20px;
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
