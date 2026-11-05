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

    <!-- 消费返积分规则（后台可配置，保存到 mo_system_config） -->
    <section class="section-block">
      <div class="section-header">
        <h2 class="section-title">消费返积分规则</h2>
        <button class="btn btn-primary" :disabled="ruleSaving" @click="savePointsRule">
          {{ ruleSaving ? '保存中...' : '保存规则' }}
        </button>
      </div>
      <p class="rule-desc">
        订单支付成功后发放：实付金额 × 每美元积分 × 首单倍率 × 会员倍率，四舍五入取整（1.50 美元 × 10 = 15 积分）。
      </p>
      <div class="rule-grid">
        <div class="form-group">
          <label>每 1 美元获得积分</label>
          <input v-model.number="pointsRule.usdRate" type="number" min="1" step="1" class="input" />
        </div>
        <div class="form-group">
          <label>首单倍率</label>
          <input v-model.number="pointsRule.firstOrderMultiplier" type="number" min="1" step="1" class="input" />
        </div>
        <div class="form-group">
          <label>会员等级倍率</label>
          <select v-model="pointsRule.memberRateEnabled" class="input">
            <option :value="true">启用（按下方五档倍率计算）</option>
            <option :value="false">关闭（所有会员按 1.0 计算）</option>
          </select>
        </div>
      </div>

      <!-- 五档会员倍率：逐档可配置（仅“会员等级倍率”启用时生效） -->
      <div class="level-rate-title">五档会员倍率</div>
      <div class="level-rate-grid">
        <div v-for="field in levelRateFields" :key="field.code" class="form-group">
          <label>{{ field.label }}</label>
          <input
            v-model.number="pointsRule.levelRates[field.code]"
            type="number"
            min="1"
            max="10"
            step="0.1"
            class="input"
          />
        </div>
      </div>
    </section>

    <!-- 会员规则配置（签到/评价/邀请/补签/抵扣，保存到 mo_system_config） -->
    <section class="section-block">
      <div class="section-header">
        <h2 class="section-title">会员规则配置</h2>
        <button class="btn btn-primary" :disabled="membershipRuleSaving" @click="saveMembershipRule">
          {{ membershipRuleSaving ? '保存中...' : '保存规则' }}
        </button>
      </div>
      <p class="rule-desc">
        以下数值同时作用于 C 端会员规则页与实际发放逻辑，修改后即时生效；未配置时使用系统默认值。
      </p>
      <div class="rule-grid">
        <div v-for="f in membershipRuleFields" :key="f.key" class="form-group">
          <label>{{ f.label }}</label>
          <input
            v-model.number="membershipRule[f.field]"
            type="number"
            :min="f.min"
            :max="f.max"
            step="1"
            class="input"
          />
        </div>
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
import { getPointsActivities, createPointsActivity, deletePointsActivity, getPointsLogs, getPointsStats, getSystemConfig, saveSystemConfig } from '../api/admin'
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

// ---- 消费返积分规则（mo_system_config：points.*） ----
const pointsRule = ref({
  usdRate: 10,
  firstOrderMultiplier: 2,
  memberRateEnabled: true,
  // 五档会员倍率，键与后端 MemberEntity.Level 一致
  levelRates: { NORMAL: 1.0, SILVER: 1.1, GOLD: 1.2, PLATINUM: 1.5, DIAMOND: 2.0 }
})
const ruleSaving = ref(false)

// ---- 会员规则配置（mo_system_config：points.checkin/review/invite/makeup/deduct.*） ----
const membershipRule = ref({
  checkinPoints: 5,
  checkinStreakDays: 7,
  checkinStreakMultiplier: 2,
  reviewPoints: 10,
  inviteRewardPoints: 200,
  inviteMonthlyLimit: 20,
  makeupFreePerMonth: 1,
  makeupPointsCost: 50,
  pointsPerCurrencyUnit: 100,
  maxDeductPercent: 30,
  rankBonus1: 1000,
  rankBonus2: 700,
  rankBonus3: 500,
  rankBonus4To10: 200
})
const membershipRuleSaving = ref(false)
// 会员规则配置项：与后端 MembershipRuleConfigService 的 key 一一对应
const membershipRuleFields = [
  { field: 'checkinPoints', key: 'points.checkin.points', label: '每日签到积分', min: 1 },
  { field: 'checkinStreakDays', key: 'points.checkin.streak_days', label: '连续签到天数', min: 1 },
  { field: 'checkinStreakMultiplier', key: 'points.checkin.streak_multiplier', label: '连续签到奖励倍数', min: 1 },
  { field: 'reviewPoints', key: 'points.review.points', label: '评价奖励积分', min: 1 },
  { field: 'inviteRewardPoints', key: 'points.invite.reward_points', label: '邀请首单奖励（每人）', min: 1 },
  { field: 'inviteMonthlyLimit', key: 'points.invite.monthly_limit', label: '每月邀请计奖上限（条）', min: 1 },
  { field: 'makeupFreePerMonth', key: 'points.makeup.free_per_month', label: '每月免费补签次数', min: 0 },
  { field: 'makeupPointsCost', key: 'points.makeup.points_cost', label: '补签消耗积分（每次）', min: 1 },
  { field: 'pointsPerCurrencyUnit', key: 'points.deduct.points_per_unit', label: '积分抵扣换算（N 积分 = 1 美元）', min: 1 },
  { field: 'maxDeductPercent', key: 'points.deduct.max_percent', label: '最高抵扣比例（%）', min: 1, max: 100 },
  { field: 'rankBonus1', key: 'points.invite.rank_bonus_1', label: '月度排行第 1 名奖励', min: 0 },
  { field: 'rankBonus2', key: 'points.invite.rank_bonus_2', label: '月度排行第 2 名奖励', min: 0 },
  { field: 'rankBonus3', key: 'points.invite.rank_bonus_3', label: '月度排行第 3 名奖励', min: 0 },
  { field: 'rankBonus4To10', key: 'points.invite.rank_bonus_4_10', label: '月度排行第 4-10 名奖励', min: 0 }
]
// 五档展示顺序与默认倍率（后端未配置时使用）
const levelRateFields = [
  { code: 'NORMAL', label: '普通会员', defaultRate: 1.0 },
  { code: 'SILVER', label: '银卡会员', defaultRate: 1.1 },
  { code: 'GOLD', label: '金卡会员', defaultRate: 1.2 },
  { code: 'PLATINUM', label: '铂金会员', defaultRate: 1.5 },
  { code: 'DIAMOND', label: '钻石会员', defaultRate: 2.0 }
]

// 读取规则配置；缺失时保留后端默认值（10 积分/美元、首单 2 倍、会员倍率启用）
async function fetchPointsRule() {
  try {
    const list = toArray(await getSystemConfig())
    const map = {}
    list.forEach((item) => {
      if (item && item.key) map[item.key] = item.value
    })
    const rate = Number(map['points.usd_rate'])
    if (Number.isFinite(rate) && rate > 0) pointsRule.value.usdRate = rate
    const first = Number(map['points.first_order_multiplier'])
    if (Number.isFinite(first) && first > 0) pointsRule.value.firstOrderMultiplier = first
    pointsRule.value.memberRateEnabled = String(map['points.member_rate_enabled'] ?? 'true') !== 'false'
    // 五档倍率：逐档读取 points.level_rate.<LEVEL>
    levelRateFields.forEach((field) => {
      const value = Number(map[`points.level_rate.${field.code}`])
      pointsRule.value.levelRates[field.code] = Number.isFinite(value) && value > 0 ? value : field.defaultRate
    })
    // 会员规则：缺失/非法时保留页面默认值
    membershipRuleFields.forEach((field) => {
      const raw = map[field.key]
      if (raw == null || String(raw).trim() === '') return
      const value = Number(raw)
      if (Number.isFinite(value) && value >= field.min && (field.max == null || value <= field.max)) {
        membershipRule.value[field.field] = value
      }
    })
  } catch (err) {
    console.error('获取积分规则配置失败:', err)
  }
}

// 保存规则：只提交 points.* 配置键，不会覆盖其它系统配置
async function savePointsRule() {
  const rate = Math.floor(Number(pointsRule.value.usdRate))
  const first = Math.floor(Number(pointsRule.value.firstOrderMultiplier))
  if (!Number.isFinite(rate) || rate <= 0) {
    ElMessage.warning('每美元积分必须是大于 0 的整数')
    return
  }
  if (!Number.isFinite(first) || first <= 0) {
    ElMessage.warning('首单倍率必须是大于 0 的整数')
    return
  }
  // 五档倍率校验：1.0 ~ 10.0，最多两位小数
  const levelPayload = []
  for (const field of levelRateFields) {
    const value = Number(pointsRule.value.levelRates[field.code])
    if (!Number.isFinite(value) || value < 1 || value > 10) {
      ElMessage.warning(`${field.label}倍率需在 1.0 ~ 10.0 之间`)
      return
    }
    levelPayload.push({ key: `points.level_rate.${field.code}`, value: String(Number(value.toFixed(2))) })
  }
  ruleSaving.value = true
  try {
    await saveSystemConfig([
      { key: 'points.usd_rate', value: String(rate) },
      { key: 'points.first_order_multiplier', value: String(first) },
      { key: 'points.member_rate_enabled', value: pointsRule.value.memberRateEnabled ? 'true' : 'false' },
      ...levelPayload
    ])
    ElMessage.success('消费返积分规则已保存')
  } catch (err) {
    console.error('保存积分规则失败', err)
    ElMessage.error('保存失败：' + (err?.message || '未知错误'))
  } finally {
    ruleSaving.value = false
  }
}

// 保存会员规则：只提交会员规则配置键，不会覆盖其它系统配置
async function saveMembershipRule() {
  for (const field of membershipRuleFields) {
    const value = Number(membershipRule.value[field.field])
    const range = field.max != null ? `${field.min} ~ ${field.max}` : `${field.min} 及以上`
    if (!Number.isInteger(value) || value < field.min || (field.max != null && value > field.max)) {
      ElMessage.warning(`${field.label}需为${range}的整数`)
      return
    }
  }
  membershipRuleSaving.value = true
  try {
    await saveSystemConfig(
      membershipRuleFields.map((field) => ({
        key: field.key,
        value: String(membershipRule.value[field.field])
      }))
    )
    ElMessage.success('会员规则已保存，C 端即时生效')
  } catch (err) {
    console.error('保存会员规则失败', err)
    ElMessage.error('保存失败：' + (err?.message || '未知错误'))
  } finally {
    membershipRuleSaving.value = false
  }
}

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
  fetchPointsRule()
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

/* 消费返积分规则：三项配置横排，窄屏自动换行 */
.rule-desc { margin: 0 0 16px; font-size: 13px; color: var(--text-500); line-height: 1.6; }
.rule-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
/* 五档会员倍率：桌面五列，窄屏两列 */
.level-rate-title { margin: 18px 0 8px; font-size: 13px; font-weight: 600; color: var(--text-700); }
.level-rate-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
@media (max-width: 1200px) {
  .level-rate-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 900px) {
  .rule-grid { grid-template-columns: 1fr; }
}

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
