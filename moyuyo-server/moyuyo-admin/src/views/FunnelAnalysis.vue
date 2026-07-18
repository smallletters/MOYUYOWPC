<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>漏斗分析</h2>
    </div>
    <el-card shadow="never">
      <div class="funnel-chart">
        <div v-for="(step, index) in funnelData" :key="step.name" class="funnel-step">
          <div class="funnel-label">{{ step.name }}</div>
          <div class="funnel-bar-wrapper">
            <div class="funnel-bar" :style="{ width: step.percent + '%', background: getColor(index) }">
              <span class="funnel-count">{{ step.count.toLocaleString() }}</span>
            </div>
          </div>
          <div class="funnel-rate">{{ step.rate }}%</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getFunnelAnalysis } from '../api/admin'

const funnelData = ref([])

async function loadData() {
  try {
    const res = await getFunnelAnalysis()
    funnelData.value = res && res.steps ? res.steps : []
  } catch (err) {
    console.error('获取漏斗数据失败', err)
  }
}

function getColor(index) {
  const colors = ['#007aff','#2e8dff','#66abff','#9fcbff','#cfe5ff']
  return colors[index]
}

onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.funnel-chart { display: flex; flex-direction: column; gap: 16px; padding: 20px; }
.funnel-step { display: flex; align-items: center; gap: 16px; }
.funnel-label { width: 100px; font-size: 14px; font-weight: 500; color: var(--text-600); text-align: right; }
.funnel-bar-wrapper { flex: 1; height: 44px; background: var(--background-100); border-radius: 8px; overflow: hidden; }
.funnel-bar { height: 100%; border-radius: 8px; display: flex; align-items: center; justify-content: flex-end; padding: 0 16px; transition: width 0.5s ease; min-width: 60px; }
.funnel-count { color: #fff; font-size: 14px; font-weight: 600; }
.funnel-rate { width: 60px; font-size: 14px; font-weight: 600; color: var(--text-500); }
</style>
