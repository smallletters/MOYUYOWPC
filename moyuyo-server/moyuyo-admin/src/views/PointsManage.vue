<template>
  <div class="page-wrapper">
    <div class="page-title-section">
      <h1 class="page-title">积分管理</h1>
      <p class="page-desc">管理积分活动、查看积分流水和统计数据</p>
    </div>

    <!-- 积分统计卡片 -->
    <section class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-header">
          <span class="kpi-label">总发放积分</span>
        </div>
        <div class="kpi-value">{{ stats.totalIssued || 0 }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-header">
          <span class="kpi-label">总消耗积分</span>
        </div>
        <div class="kpi-value">{{ stats.totalConsumed || 0 }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-header">
          <span class="kpi-label">活跃活动数</span>
        </div>
        <div class="kpi-value">{{ stats.activeActivities || 0 }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-header">
          <span class="kpi-label">参与用户数</span>
        </div>
        <div class="kpi-value">{{ stats.totalUsers || 0 }}</div>
      </div>
    </section>

    <!-- 积分活动管理 -->
    <section class="section-block">
      <div class="section-header">
        <h2 class="section-title">积分活动</h2>
        <button class="btn btn-primary" @click="showCreateDialog = true">新建活动</button>
      </div>

      <div class="data-table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>活动名称</th>
              <th>积分值</th>
              <th>类型</th>
              <th>开始时间</th>
              <th>结束时间</th>
              <th>状态</th>
              <th>参与人数</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in activities" :key="item.id">
              <td>{{ item.name || '-' }}</td>
              <td>{{ item.points || 0 }}</td>
              <td>{{ item.type || '-' }}</td>
              <td>{{ item.startTime || '-' }}</td>
              <td>{{ item.endTime || '-' }}</td>
              <td>
                <span :class="['status-tag', statusClass(item.status)]">{{ item.status || '-' }}</span>
              </td>
              <td>{{ item.participantCount || 0 }}</td>
              <td>
                <button class="action-link-btn" @click="handleDeleteActivity(item.id)">删除</button>
              </td>
            </tr>
            <tr v-if="activities.length === 0">
              <td colspan="8" class="td-empty">暂无积分活动</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- 积分流水 -->
    <section class="section-block">
      <div class="section-header">
        <h2 class="section-title">积分流水</h2>
        <div class="section-filter">
          <input v-model="logFilter.userId" placeholder="用户ID" class="input-sm" />
          <button class="btn btn-outline btn-sm" @click="fetchLogs">查询</button>
        </div>
      </div>

      <div class="data-table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>用户ID</th>
              <th>变动类型</th>
              <th>积分变动</th>
              <th>剩余积分</th>
              <th>描述</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in logs" :key="item.id">
              <td>{{ item.userId || '-' }}</td>
              <td>
                <span :class="['status-tag', item.changeType === 'EARN' ? 'status-active' : 'status-inactive']">
                  {{ item.changeType === 'EARN' ? '获取' : '消耗' }}
                </span>
              </td>
              <td :class="item.changeType === 'EARN' ? 'text-green' : 'text-red'">
                {{ item.changeType === 'EARN' ? '+' : '-' }}{{ item.points || 0 }}
              </td>
              <td>{{ item.balance || 0 }}</td>
              <td>{{ item.description || '-' }}</td>
              <td>{{ item.createTime || '-' }}</td>
            </tr>
            <tr v-if="logs.length === 0">
              <td colspan="6" class="td-empty">暂无积分流水</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- 创建活动弹窗 -->
    <div v-if="showCreateDialog" class="modal-overlay" @click.self="showCreateDialog = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3>新建积分活动</h3>
          <button class="modal-close" @click="showCreateDialog = false">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>活动名称</label>
            <input v-model="createForm.name" class="input" placeholder="请输入活动名称" />
          </div>
          <div class="form-group">
            <label>积分值</label>
            <input v-model.number="createForm.points" type="number" class="input" placeholder="请输入积分值" />
          </div>
          <div class="form-group">
            <label>活动类型</label>
            <select v-model="createForm.type" class="input">
              <option value="">请选择类型</option>
              <option value="SIGN_IN">每日签到</option>
              <option value="PURCHASE">购物返积分</option>
              <option value="INVITE">邀请奖励</option>
              <option value="EVENT">活动奖励</option>
            </select>
          </div>
          <div class="form-group">
            <label>开始时间</label>
            <input v-model="createForm.startTime" type="date" class="input" />
          </div>
          <div class="form-group">
            <label>结束时间</label>
            <input v-model="createForm.endTime" type="date" class="input" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="showCreateDialog = false">取消</button>
          <button class="btn btn-primary" @click="handleCreateActivity">确认创建</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getPointsActivities, createPointsActivity, deletePointsActivity, getPointsLogs, getPointsStats } from '../api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'
import { toArray } from '../utils/safeArray'

// 统计数据
const stats = ref({
  totalIssued: 0,
  totalConsumed: 0,
  activeActivities: 0,
  totalUsers: 0
})

// 活动列表
const activities = ref([])

// 积分流水
const logs = ref([])
const logFilter = ref({ userId: '' })

// 创建活动弹窗
const showCreateDialog = ref(false)
const createForm = ref({
  name: '',
  points: 0,
  type: '',
  startTime: '',
  endTime: ''
})

// 获取统计数据
async function fetchStats() {
  try {
    const res = await getPointsStats()
    if (res) {
      Object.assign(stats.value, res)
    }
  } catch (err) {
    console.error('获取积分统计失败:', err)
  }
}

// 获取活动列表
async function fetchActivities() {
  try {
    const res = await getPointsActivities()
    if (res) {
      activities.value = toArray(res)
    }
  } catch (err) {
    console.error('获取积分活动失败:', err)
  }
}

// 获取积分流水
async function fetchLogs() {
  try {
    const params = {}
    if (logFilter.value.userId) {
      params.userId = logFilter.value.userId
    }
    const res = await getPointsLogs(params)
    if (res) {
      logs.value = toArray(res)
    }
  } catch (err) {
    console.error('获取积分流水失败:', err)
  }
}

// 创建活动
async function handleCreateActivity() {
  if (!createForm.value.name || !createForm.value.points) {
    ElMessage.warning('请填写活动名称和积分值')
    return
  }
  try {
    await createPointsActivity(createForm.value)
    ElMessage.success('积分活动创建成功')
    showCreateDialog.value = false
    createForm.value = { name: '', points: 0, type: '', startTime: '', endTime: '' }
    fetchActivities()
    fetchStats()
  } catch (err) {
    ElMessage.error('创建失败: ' + (err.response?.data?.message || err.message))
  }
}

// 删除活动
async function handleDeleteActivity(id) {
  try {
    await ElMessageBox.confirm('确认删除此积分活动？此操作将删除该类型下的所有积分流水记录。', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deletePointsActivity(id)
    ElMessage.success('积分活动删除成功')
    fetchActivities()
    fetchStats()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('删除失败: ' + (err.response?.data?.message || err.message))
    }
  }
}

// 状态样式
function statusClass(status) {
  if (status === 'ACTIVE' || status === 'active') return 'status-active'
  if (status === 'ENDED' || status === 'ended') return 'status-inactive'
  return ''
}

onMounted(() => {
  fetchStats()
  fetchActivities()
  fetchLogs()
})
</script>

<style scoped>
.page-wrapper { padding: 24px; }
.page-title-section { margin-bottom: 24px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0 0 4px; }
.page-desc { color: var(--text-500); font-size: 14px; margin: 0; }

.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
.kpi-card { background: var(--card); border: 1px solid var(--border); border-radius: var(--radius); padding: 20px; box-shadow: var(--shadow-xs); }
.kpi-header { margin-bottom: 8px; }
.kpi-label { font-size: 13px; color: var(--text-500); }
.kpi-value { font-size: 28px; font-weight: 700; color: var(--text-800); }

.section-block { background: var(--card); border: 1px solid var(--border); border-radius: var(--radius); padding: 20px; margin-bottom: 24px; box-shadow: var(--shadow-xs); }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.section-title { font-size: 16px; font-weight: 600; color: var(--text-800); margin: 0; }
.section-filter { display: flex; gap: 8px; align-items: center; }

.input-sm { padding: 6px 12px; border: 1px solid var(--border); border-radius: var(--radius-sm); font-size: 13px; width: 160px; }

.data-table-wrapper { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th { text-align: left; padding: 10px 12px; font-size: 12px; font-weight: 600; color: var(--text-500); background: var(--background-200); border-bottom: 1px solid var(--border); }
.data-table td { padding: 12px; font-size: 13px; color: var(--text-700); border-bottom: 1px solid var(--background-100); }
.td-empty { text-align: center; color: var(--text-400); padding: 32px !important; }

.status-tag { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; }
.status-active { background: var(--state-success-surface); color: var(--state-success); }
.status-inactive { background: var(--state-error-surface); color: var(--state-error); }

.text-green { color: var(--state-success); }
.text-red { color: var(--state-error); }

.action-link-btn { background: none; border: none; color: var(--primary); cursor: pointer; font-size: 13px; padding: 0; }
.action-link-btn:hover { text-decoration: underline; }

.btn { padding: 8px 16px; border-radius: 6px; font-size: 13px; cursor: pointer; border: none; }
.btn-primary { background: var(--primary); color: #fff; }
.btn-outline { background: var(--card); color: var(--text-700); border: 1px solid var(--border); }
.btn-sm { padding: 6px 12px; font-size: 12px; }

/* 弹窗样式 */
.modal-overlay { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-content { background: var(--card); border-radius: var(--radius); width: 480px; max-height: 80vh; overflow-y: auto; }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid var(--border); }
.modal-header h3 { margin: 0; font-size: 16px; font-weight: 600; }
.modal-close { background: none; border: none; font-size: 20px; cursor: pointer; color: var(--text-400); }
.modal-body { padding: 20px; }
.modal-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 16px 20px; border-top: 1px solid var(--border); }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 6px; font-size: 13px; font-weight: 500; color: var(--text-700); }
.input { width: 100%; padding: 8px 12px; border: 1px solid var(--border); border-radius: var(--radius-sm); font-size: 13px; box-sizing: border-box; }
</style>
