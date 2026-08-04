<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-title-area">
      <h1>营销效果分析</h1>
      <p>优惠券、活动、秒杀、分销多维度营销效果追踪与 ROI 分析</p>
    </div>

    <!-- 维度切换 Tab -->
    <div class="dim-tabs">
      <button
        v-for="tab in dimTabs"
        :key="tab.key"
        class="dim-tab"
        :class="{ active: activeDim === tab.key }"
        @click="switchDim(tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- ==================== 优惠券效果面板 ==================== -->
    <template v-if="activeDim === 'coupon'">
      <!-- 核心指标 -->
      <div class="metric-grid">
        <div class="metric-card" v-for="m in couponMetrics" :key="m.label">
          <div class="metric-label">{{ m.label }}</div>
          <div class="metric-value" :style="m.color ? { color: m.color } : {}">{{ m.value }}</div>
          <div class="metric-sub">{{ m.sub }}</div>
        </div>
      </div>

      <div class="two-col-layout">
        <!-- 优惠券效果明细 -->
        <div class="admin-card">
          <div class="card-head">
            <h2>优惠券效果明细</h2>
            <span class="card-hint">共6张券</span>
          </div>
          <div class="list-header">
            <span style="flex:2.5">券名称</span>
            <span style="flex:0.8;text-align:center">面额</span>
            <span style="flex:1.5;text-align:center">核销率</span>
            <span style="flex:0.6;text-align:right">ROI</span>
            <span style="flex:1;text-align:right">发放/使用</span>
          </div>
          <div class="coupon-item" v-for="c in couponList" :key="c.name">
            <div class="coupon-row">
              <span class="coupon-name" style="flex:2.5">{{ c.name }}</span>
              <span class="coupon-amount" style="flex:0.8;text-align:center">¥{{ c.amount }}</span>
              <div class="coupon-progress" style="flex:1.5">
                <div class="progress-bar">
                  <div class="progress-fill" :class="rateClass(c.rate)" :style="{ width: c.rate + '%' }"></div>
                </div>
                <span class="rate-text">{{ c.rate }}%</span>
              </div>
              <span class="coupon-roi" :class="roiClass(c.roi)" style="flex:0.6;text-align:right">{{ c.roi }}x</span>
              <span class="coupon-usage" style="flex:1;text-align:right">{{ c.issued }} / {{ c.used }}</span>
            </div>
          </div>
        </div>

        <!-- 用户画像 -->
        <div class="admin-card">
          <h2 style="margin-bottom:16px">用户画像</h2>
          <div style="margin-bottom:20px">
            <div class="label-row"><span>新老用户占比</span></div>
            <div class="profile-bar">
              <div class="segment" style="width:62%;background:var(--primary)"></div>
              <div class="segment" style="width:38%;background:var(--brand-200)"></div>
            </div>
            <div class="legend-row">
              <span class="legend-item"><i class="dot" style="background:var(--primary)"></i>老用户 62%</span>
              <span class="legend-item"><i class="dot" style="background:var(--brand-200)"></i>新用户 38%</span>
            </div>
          </div>
          <div>
            <div class="label-row"><span>会员等级分布</span></div>
            <div class="profile-bar">
              <div class="segment" style="width:15%;background:var(--chart-4)"></div>
              <div class="segment" style="width:28%;background:var(--primary)"></div>
              <div class="segment" style="width:35%;background:var(--brand-200)"></div>
              <div class="segment" style="width:22%;background:var(--background-300)"></div>
            </div>
            <div class="legend-row">
              <span class="legend-item"><i class="dot" style="background:var(--chart-4)"></i>钻石 15%</span>
              <span class="legend-item"><i class="dot" style="background:var(--primary)"></i>金卡 28%</span>
              <span class="legend-item"><i class="dot" style="background:var(--brand-200)"></i>银卡 35%</span>
              <span class="legend-item"><i class="dot" style="background:var(--background-300)"></i>普通 22%</span>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ==================== 活动效果面板（含活动管理） ==================== -->
    <template v-else-if="activeDim === 'activity'">
      <!-- 核心指标 -->
      <div class="metric-grid">
        <div class="metric-card" v-for="m in activityMetrics" :key="m.label">
          <div class="metric-label">{{ m.label }}</div>
          <div class="metric-value" :style="m.color ? { color: m.color } : {}">{{ m.value }}</div>
          <div class="metric-sub">{{ m.sub }}</div>
        </div>
      </div>

      <div class="two-col-layout">
        <!-- 活动 GMV 对比 -->
        <div class="admin-card">
          <div class="card-head">
            <h2>活动 GMV 对比</h2>
            <div class="chart-legend">
              <span class="legend-item"><i class="sq" style="background:var(--primary)"></i>活动期间</span>
              <span class="legend-item"><i class="sq" style="background:var(--background-300)"></i>活动前</span>
            </div>
          </div>
          <div class="bar-chart">
            <div class="bar-group" v-for="g in gmvCompare" :key="g.label">
              <div class="bar-pair">
                <div class="bar" :style="{ height: g.before + '%', background: 'var(--background-300)' }" :data-value="g.beforeVal"></div>
                <div class="bar" :style="{ height: g.after + '%', background: 'var(--primary)' }" :data-value="g.afterVal"></div>
              </div>
              <div class="bar-label">{{ g.label }}</div>
            </div>
          </div>
        </div>

        <!-- 投入 vs 增量收益 -->
        <div class="admin-card">
          <div class="card-head"><h2>投入 vs 增量收益</h2></div>
          <div class="compare-row">
            <span class="compare-label">投入</span>
            <div class="progress-bar">
              <div class="progress-fill warning" style="width:35%"></div>
            </div>
            <span class="compare-value">¥18.2w</span>
          </div>
          <div class="compare-row">
            <span class="compare-label">增量GMV</span>
            <div class="progress-bar">
              <div class="progress-fill success" style="width:82%"></div>
            </div>
            <span class="compare-value">¥57.0w</span>
          </div>
          <div class="roi-summary">
            <span class="roi-text">综合投入产出比</span>
            <span class="roi-strong">每投入1元带来3.13元增量</span>
          </div>
        </div>
      </div>

      <!-- 活动管理列表（保留原功能） -->
      <div class="admin-card" style="margin-top:20px">
        <div class="card-head">
          <h2>活动管理</h2>
          <el-button type="primary" size="small" @click="handleAdd">新建活动</el-button>
        </div>
        <el-table :data="tableData" stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="name" label="活动名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="type" label="活动类型" width="100" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'UPCOMING' ? 'warning' : 'info'">
                {{ row.status === 'ACTIVE' ? '进行中' : row.status === 'UPCOMING' ? '预告中' : '已结束' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="startDate" label="开始日期" width="110" />
          <el-table-column prop="endDate" label="结束日期" width="110" />
          <el-table-column prop="participants" label="参与人数" width="90" />
          <el-table-column prop="gmv" label="GMV" width="100" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="display:flex;justify-content:flex-end;padding:16px 0 0">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="total"
            layout="total, sizes, prev, pager, next"
            @change="loadData"
          />
        </div>
      </div>

      <!-- 新建/编辑活动对话框 -->
      <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
        <el-form :model="editForm" label-width="100px">
          <el-form-item label="活动名称">
            <el-input v-model="editForm.name" placeholder="请输入活动名称" />
          </el-form-item>
          <el-form-item label="活动类型">
            <el-select v-model="editForm.type" placeholder="请选择活动类型" style="width:100%">
              <el-option label="满减" value="满减" />
              <el-option label="折扣" value="折扣" />
              <el-option label="秒杀" value="秒杀" />
              <el-option label="拼团" value="拼团" />
            </el-select>
          </el-form-item>
          <el-form-item label="开始日期">
            <el-date-picker v-model="editForm.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择开始日期" style="width:100%" />
          </el-form-item>
          <el-form-item label="结束日期">
            <el-date-picker v-model="editForm.endDate" type="date" value-format="YYYY-MM-DD" placeholder="选择结束日期" style="width:100%" />
          </el-form-item>
          <el-form-item label="活动描述">
            <el-input v-model="editForm.description" type="textarea" placeholder="请输入活动描述" />
          </el-form-item>
          <el-form-item label="预算">
            <el-input-number v-model="editForm.budget" :min="0" :precision="2" style="width:100%" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave">保存</el-button>
        </template>
      </el-dialog>
    </template>

    <!-- ==================== 秒杀效果面板 ==================== -->
    <template v-else-if="activeDim === 'flash'">
      <div class="metric-grid">
        <div class="metric-card" v-for="m in flashMetrics" :key="m.label">
          <div class="metric-label">{{ m.label }}</div>
          <div class="metric-value" :style="m.color ? { color: m.color } : {}">{{ m.value }}</div>
          <div class="metric-sub">{{ m.sub }}</div>
        </div>
      </div>

      <div class="admin-card" style="margin-top:20px">
        <div class="card-head">
          <h2>秒杀商品明细</h2>
          <span class="card-hint">共8场</span>
        </div>
        <div class="list-header">
          <span style="flex:2.5">商品</span>
          <span style="flex:1;text-align:center">售罄率</span>
          <span style="flex:0.8;text-align:right">状态</span>
          <span style="flex:1.5;text-align:right">详情</span>
        </div>
        <div class="coupon-item" v-for="f in flashList" :key="f.name">
          <div class="coupon-row">
            <span class="coupon-name" style="flex:2.5">{{ f.name }}</span>
            <div class="coupon-progress" style="flex:1;justify-content:center">
              <div class="progress-bar" style="max-width:80px">
                <div class="progress-fill" :class="rateClass(f.rate)" :style="{ width: f.rate + '%' }"></div>
              </div>
              <span class="rate-text">{{ f.rate }}%</span>
            </div>
            <div style="flex:0.8;text-align:right">
              <span class="status-tag" :class="flashStatusClass(f.status)">{{ f.status }}</span>
            </div>
            <span style="flex:1.5;text-align:right;font-size:12px;color:var(--text-400)">{{ f.detail }}</span>
          </div>
        </div>
      </div>
    </template>

    <!-- ==================== 分销佣金效果面板 ==================== -->
    <template v-else>
      <div class="metric-grid">
        <div class="metric-card" v-for="m in distMetrics" :key="m.label">
          <div class="metric-label">{{ m.label }}</div>
          <div class="metric-value" :style="m.color ? { color: m.color } : {}">{{ m.value }}</div>
          <div class="metric-sub">{{ m.sub }}</div>
        </div>
      </div>

      <div class="two-col-layout" style="margin-top:20px">
        <!-- 渠道 GMV 占比 -->
        <div class="admin-card">
          <h2 style="margin-bottom:16px">渠道 GMV 占比</h2>
          <div class="profile-bar" style="height:14px;border-radius:7px">
            <div class="segment" style="width:26%;background:var(--primary)"></div>
            <div class="segment" style="width:45%;background:var(--chart-4)"></div>
            <div class="segment" style="width:29%;background:var(--brand-200)"></div>
          </div>
          <div class="legend-row" style="margin-top:12px">
            <span class="legend-item"><i class="dot" style="background:var(--primary)"></i>分销渠道 26%</span>
            <span class="legend-item"><i class="dot" style="background:var(--chart-4)"></i>自然流量 45%</span>
            <span class="legend-item"><i class="dot" style="background:var(--brand-200)"></i>付费推广 29%</span>
          </div>
        </div>

        <!-- Top 分销员排行 -->
        <div class="admin-card">
          <div class="card-head">
            <h2>Top 分销员排行</h2>
            <span class="card-hint link">查看全部</span>
          </div>
          <div class="list-header">
            <span style="width:28px"></span>
            <span style="flex:1">分销员</span>
            <span style="flex:0.6;text-align:center">推广单数</span>
            <span style="flex:0.8;text-align:right">分销 GMV</span>
            <span style="flex:0.8;text-align:right">佣金</span>
          </div>
          <div class="rank-item" v-for="(r, idx) in rankList" :key="r.name">
            <div class="rank-badge" :class="idx < 3 ? 'top-' + (idx + 1) : 'normal'">{{ idx + 1 }}</div>
            <div style="flex:1;min-width:0">
              <div class="rank-name">{{ r.name }}</div>
            </div>
            <span style="flex:0.6;text-align:center;font-size:13px;color:var(--text-600)">{{ r.orders }} 单</span>
            <span style="flex:0.8;text-align:right;font-size:14px;font-weight:700;color:var(--text-800)">{{ r.gmv }}</span>
            <span style="flex:0.8;text-align:right;font-size:13px;color:var(--text-500)">{{ r.commission }}</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCampaigns, getMarketingEffects, deleteCampaign, createCampaign, updateCampaign } from '../api/admin'

// ===== 维度 Tab =====
const dimTabs = [
  { key: 'coupon', label: '优惠券' },
  { key: 'activity', label: '活动' },
  { key: 'flash', label: '秒杀' },
  { key: 'distribution', label: '分销' }
]
const activeDim = ref('coupon')

function switchDim(key) {
  activeDim.value = key
}

// ===== 优惠券指标与明细 =====
const couponMetrics = [
  { label: '总发放量', value: '12,860', sub: '较上周 +8.2%' },
  { label: '总核销量', value: '5,432', sub: '较上周 +12.5%' },
  { label: '核销率', value: '42.2%', sub: '较上周 +1.8pp', color: 'var(--state-success)' },
  { label: '带动 GMV', value: '¥86.4w', sub: '较上周 +15.3%' }
]
const couponList = [
  { name: '新人专享满99减20', amount: 20, rate: 68, roi: 3.2, issued: '3,200', used: '2,176' },
  { name: '全场满199减50', amount: 50, rate: 45, roi: 2.8, issued: '2,800', used: '1,260' },
  { name: '会员满299减80', amount: 80, rate: 55, roi: 4.1, issued: '2,100', used: '1,155' },
  { name: '洗护专场满159减30', amount: 30, rate: 32, roi: 1.9, issued: '1,960', used: '627' },
  { name: '玩具满89减15', amount: 15, rate: 18, roi: 0.8, issued: '1,600', used: '288' },
  { name: '618返场满399减100', amount: 100, rate: 38, roi: 2.5, issued: '1,200', used: '456' }
]

// ===== 活动指标与 GMV 对比 =====
const activityMetrics = [
  { label: '活动 GMV', value: '¥69.1w', sub: '占总GMV 39.4%' },
  { label: '活动订单数', value: '8,624', sub: '较上月 +18.2%' },
  { label: '拉新用户', value: '3,486', sub: '较活动前 +156%', color: 'var(--state-success)' },
  { label: '投入产出比', value: '3.13x', sub: '每1元投入带动3.13元', color: 'var(--state-success)' }
]
const gmvCompare = [
  { label: '618大促', before: 85, after: 100, beforeVal: '¥12.3w', afterVal: '¥28.6w' },
  { label: '春季焕新', before: 78, after: 95, beforeVal: '¥9.8w', afterVal: '¥22.1w' },
  { label: '会员日', before: 72, after: 88, beforeVal: '¥8.6w', afterVal: '¥18.4w' }
]

// ===== 秒杀指标与明细 =====
const flashMetrics = [
  { label: '参与率', value: '24.6%', sub: '较上月 +3.2pp' },
  { label: '成交率', value: '78.3%', sub: '较上月 +5.1pp', color: 'var(--state-success)' },
  { label: '平均售罄时长', value: '1m32s', sub: '较上月缩短18s', color: 'var(--state-warning)' },
  { label: '秒杀 GMV', value: '¥32.6w', sub: '占总GMV 18.5%' }
]
const flashList = [
  { name: '高端宠物洗护套装', rate: 100, status: '已售罄', detail: '售罄 48s / 500件' },
  { name: '舒适胸背带', rate: 96, status: '已售罄', detail: '售罄 1m12s / 300件' },
  { name: '互动益智玩具', rate: 74, status: '进行中', detail: '剩余 130件 / 500件' },
  { name: '宠物潮流外套', rate: 88, status: '已售罄', detail: '售罄 2m05s / 200件' },
  { name: '有机猫粮试用装', rate: 52, status: '已结束', detail: '售出 260件 / 500件' }
]

// ===== 分销指标与排行 =====
const distMetrics = [
  { label: '分销员总数', value: '1,286', sub: '本月新增 142' },
  { label: '活跃占比', value: '34.7%', sub: '较上月 +2.3pp', color: 'var(--primary)' },
  { label: '分销 GMV', value: '¥45.8w', sub: '占总GMV 26.1%' },
  { label: '佣金支出', value: '¥4.6w', sub: '佣金率 10.0%' }
]
const rankList = [
  { name: '萌宠达人小王', orders: 328, gmv: '¥8.6w', commission: '¥8,600' },
  { name: '宠物营养师Lisa', orders: 256, gmv: '¥6.2w', commission: '¥6,200' },
  { name: '爱猫人士张三', orders: 198, gmv: '¥5.1w', commission: '¥5,100' },
  { name: '养宠日记Amy', orders: 167, gmv: '¥4.3w', commission: '¥4,300' },
  { name: '汪星人铲屎官', orders: 134, gmv: '¥3.8w', commission: '¥3,800' },
  { name: '宠物测评Lab', orders: 112, gmv: '¥3.2w', commission: '¥3,200' },
  { name: '猫狗双全的日常', orders: 98, gmv: '¥2.7w', commission: '¥2,700' }
]

// 工具：状态/进度条颜色
function rateClass(rate) {
  if (rate >= 90) return 'success'
  if (rate >= 60) return 'warning'
  return ''
}
function roiClass(roi) {
  if (roi >= 2) return 'success'
  if (roi >= 1) return 'warning'
  return 'error'
}
function flashStatusClass(status) {
  if (status === '已售罄') return 'done'
  if (status === '进行中') return 'running'
  return 'ended'
}

// ===== 活动管理（保留原有功能） =====
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')

// 编辑表单字段对应后端 CampaignRequest DTO
const editForm = reactive({
  name: '',
  type: '满减',
  startDate: '',
  endDate: '',
  description: '',
  budget: 0
})

// 原始活动数据
const allCampaigns = ref([])

// 加载活动列表
async function loadCampaignEffects() {
  try {
    const data = await getCampaigns()
    const list = data && data.list ? data.list : (data || [])
    allCampaigns.value = list.map(item => ({
      id: item.id,
      name: item.name || '',
      type: item.type || '',
      status: item.status || 'UPCOMING',
      startDate: item.startDate || '',
      endDate: item.endDate || '',
      participants: item.participants ?? 0,
      gmv: item.gmv != null ? item.gmv : '-',
      budget: item.budget != null ? item.budget : '-',
      description: item.description || ''
    }))
    applyFilters()
  } catch (e) {
    console.error('获取活动列表失败', e)
  }
}

// 本地筛选（分页）
function applyFilters() {
  tableData.value = [...allCampaigns.value]
  total.value = allCampaigns.value.length
}

function loadData() {
  applyFilters()
}

function handleAdd() {
  dialogTitle.value = '新建活动'
  editForm.name = ''
  editForm.type = '满减'
  editForm.startDate = ''
  editForm.endDate = ''
  editForm.description = ''
  editForm.budget = 0
  editForm.id = undefined  // 清除ID确保调用创建API而非更新API
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑活动'
  editForm.name = row.name || ''
  editForm.type = row.type || '满减'
  editForm.startDate = row.startDate || ''
  editForm.endDate = row.endDate || ''
  editForm.description = row.description || ''
  editForm.budget = row.budget != null ? Number(row.budget) : 0
  editForm.id = row.id
  dialogVisible.value = true
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deleteCampaign(row.id)
    ElMessage.success('删除成功')
    await loadCampaignEffects()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}

async function handleSave() {
  try {
    const payload = {
      name: editForm.name,
      type: editForm.type,
      startDate: editForm.startDate,
      endDate: editForm.endDate,
      description: editForm.description,
      budget: editForm.budget
    }
    if (editForm.id) {
      await updateCampaign(editForm.id, payload)
    } else {
      await createCampaign(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadCampaignEffects()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}

onMounted(async () => {
  await Promise.all([loadCampaignEffects()])
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-title-area { margin-bottom: 24px; }
.page-title-area h1 { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0 0 6px; }
.page-title-area p { font-size: 13px; color: var(--text-400); margin: 0; }

/* 维度 Tab（与设计稿 admin-marketing-effect.html 胶囊式一致） */
.dim-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}
.dim-tab {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 38px;
  padding: 0 20px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease;
  border: none;
  background: transparent;
  color: var(--text-500);
  font-family: var(--font-sans);
}
.dim-tab.active {
  background: var(--primary);
  color: var(--primary-foreground);
}
.dim-tab:hover:not(.active) {
  background: var(--secondary);
}

/* 核心指标卡片（对齐设计稿：padding 18px 20px、数值 26px、hover 描边） */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.metric-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 18px 20px;
  box-shadow: var(--shadow-xs);
  transition: border-color 0.2s ease, transform 0.2s ease;
}
.metric-card:hover {
  border-color: var(--primary);
  transform: translateY(-1px);
}
.metric-label { font-size: 12px; font-weight: 500; color: var(--text-500); margin-bottom: 8px; }
.metric-value { font-size: 26px; font-weight: 700; color: var(--text-800); font-variant-numeric: tabular-nums; line-height: 1.2; }
.metric-sub { font-size: 12px; color: var(--text-400); margin-top: 6px; }

/* 双栏布局 */
.two-col-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-top: 20px;
}

/* 卡片（对齐设计稿：padding 20px 24px + hover 描边） */
.admin-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 20px 24px;
  box-shadow: var(--shadow-xs);
  transition: border-color 0.2s ease, transform 0.2s ease;
}
.admin-card:hover {
  border-color: var(--primary);
  transform: translateY(-1px);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.card-head h2 { font-size: 14px; font-weight: 600; color: var(--text-800); margin: 0; }
.card-hint { font-size: 12px; color: var(--text-400); }
.card-hint.link { color: var(--primary); cursor: pointer; }

/* 列表表头 */
.list-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
  color: var(--text-400);
  font-weight: 500;
}

/* 优惠券/秒杀条目 */
.coupon-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}
.coupon-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.coupon-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-800);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}
.coupon-amount { font-size: 14px; font-weight: 600; color: var(--state-error); }
.coupon-progress {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rate-text { font-size: 12px; font-weight: 600; color: var(--text-600); flex-shrink: 0; }
.coupon-roi { font-size: 14px; font-weight: 700; }
.coupon-roi.success { color: var(--state-success); }
.coupon-roi.warning { color: var(--state-warning); }
.coupon-roi.error { color: var(--state-error); }
.coupon-usage { font-size: 12px; color: var(--text-400); }

/* 进度条 */
.progress-bar {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: var(--background-200);
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--primary);
}
.progress-fill.success { background: var(--state-success); }
.progress-fill.warning { background: var(--state-warning); }
.progress-fill.error { background: var(--state-error); }

/* 用户画像分段条 */
.label-row { font-size: 12px; font-weight: 500; color: var(--text-500); margin-bottom: 10px; }
.profile-bar {
  display: flex;
  height: 12px;
  border-radius: 6px;
  overflow: hidden;
  background: var(--background-200);
}
.segment { height: 100%; }
.legend-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-600);
}
.dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.sq { width: 12px; height: 12px; border-radius: 3px; flex-shrink: 0; }
.chart-legend { display: flex; gap: 16px; }

/* 活动 GMV 对比柱状图 */
.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 32px;
  height: 180px;
  padding-top: 20px;
}
.bar-group {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  height: 100%;
}
.bar-pair {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 100%;
  width: 100%;
  justify-content: center;
}
.bar {
  width: 28px;
  border-radius: 4px 4px 0 0;
  position: relative;
  min-height: 4px;
  transition: height 0.3s ease;
}
.bar::after {
  content: attr(data-value);
  position: absolute;
  top: -18px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 10px;
  color: var(--text-400);
  white-space: nowrap;
}
.bar-label { font-size: 12px; color: var(--text-400); }

/* 投入 vs 增量收益 */
.compare-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.compare-label { width: 60px; font-size: 13px; color: var(--text-500); flex-shrink: 0; }
.compare-value { font-size: 13px; font-weight: 600; color: var(--text-800); width: 64px; text-align: right; flex-shrink: 0; }
.roi-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border-radius: var(--radius-sm);
  background: var(--state-success-surface);
}
.roi-text { font-size: 12px; color: var(--text-500); }
.roi-strong { font-size: 14px; font-weight: 700; color: var(--state-success); }

/* 秒杀状态 */
.status-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.status-tag.done { background: var(--state-success-surface); color: var(--state-success); }
.status-tag.running { background: var(--state-warning-surface); color: var(--state-warning); }
.status-tag.ended { background: var(--brand-50); color: var(--primary); }

/* 分销排行 */
.rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}
.rank-badge {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}
.rank-badge.top-1 { background: linear-gradient(135deg, #f9e5b3, #f5d78e); color: #8b6914; }
.rank-badge.top-2 { background: linear-gradient(135deg, #e8e8ed, #d1d1d6); color: #6e6e73; }
.rank-badge.top-3 { background: linear-gradient(135deg, #f0d5c9, #e5b9a8); color: #8b5e3c; }
.rank-badge.normal { background: var(--background-200); color: var(--text-500); }
.rank-name { font-size: 13px; font-weight: 500; color: var(--text-800); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* 活动表格 */
.el-table { width: 100%; }
</style>
