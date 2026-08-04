<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建直播</el-button>
      </div>
    </div>

    <!-- ========== 违规商品推广 ========== -->
    <el-card shadow="never" class="block-card">
      <div class="block-header">
        <h3>违规商品推广</h3>
        <div class="block-header-right">
          <span class="block-sub">违规直播 / 商品待处置清单</span>
          <el-tag type="danger" effect="light">{{ violationList.length }} 条待处置</el-tag>
        </div>
      </div>
      <el-table :data="violationList" stripe>
        <el-table-column label="直播" min-width="200">
          <template #default="{ row }">
            <div class="live-cell">
              <span class="live-name">{{ row.liveName }}</span>
              <span class="live-host">{{ row.hostName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="product" label="商品" min-width="160" show-overflow-tooltip />
        <el-table-column prop="violationType" label="违规类型" width="140" show-overflow-tooltip />
        <el-table-column label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.riskLevel)" effect="light">{{ row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处置状态" width="100">
          <template #default="{ row }">
            <el-tag :type="disposeTagType(row.disposeStatus)" effect="plain">{{ row.disposeStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="handleOffShelf(row)">下架</el-button>
            <el-button type="warning" link size="small" @click="handleWarn(row)">警告</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ========== 合规监控 ========== -->
    <el-card shadow="never" class="block-card">
      <div class="block-header">
        <h3>合规监控</h3>
        <span class="block-sub">监控规则与实时告警</span>
      </div>
      <el-row :gutter="16">
        <el-col v-for="rule in monitorRules" :key="rule.id" :xs="24" :sm="12" :md="8">
          <div class="rule-card" :class="{ 'rule-card-disabled': rule.status === '停用' }">
            <div class="rule-card-head">
              <span class="rule-name">{{ rule.ruleName }}</span>
              <el-tag :type="rule.status === '启用' ? 'success' : 'info'" effect="light">{{ rule.status }}</el-tag>
            </div>
            <div class="rule-card-meta">
              <span class="rule-item">监控项：{{ rule.monitorItem }}</span>
            </div>
            <div class="rule-card-foot">
              <span class="rule-alert" :class="{ 'has-alert': rule.alertCount > 0 }">告警 {{ rule.alertCount }} 条</span>
              <el-button link type="primary" size="small" @click="handleToggleRule(rule)">{{ rule.status === '启用' ? '停用' : '启用' }}</el-button>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- ========== 实时违规告警 ========== -->
    <el-card shadow="never" class="block-card">
      <div class="block-header">
        <h3>实时违规告警</h3>
        <el-tag type="danger" effect="light">{{ alertList.length }} 条</el-tag>
      </div>
      <el-table :data="alertList" stripe>
        <el-table-column prop="alertType" label="告警类型" width="220" show-overflow-tooltip />
        <el-table-column prop="source" label="来源直播" min-width="160" show-overflow-tooltip />
        <el-table-column prop="time" label="触发时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleAlert(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ========== 直播 CRUD（保留现有功能） ========== -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入直播标题/主播" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never" class="block-card">
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="直播标题" width="200" show-overflow-tooltip />
        <el-table-column prop="hostName" label="主播" width="100" />
        <el-table-column label="直播状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === '直播中' ? 'danger' : row.status === '预告中' ? 'warning' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewerCount" label="观看人数" width="100" />
        <el-table-column prop="productCount" label="商品数" width="80" />
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column label="操作" width="180" fixed="right">
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
    </el-card>
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="直播标题">
          <el-input v-model="editForm.name" placeholder="请输入直播标题" />
        </el-form-item>
        <el-form-item label="主播">
          <el-input v-model="editForm.hostName" placeholder="请输入主播名称" />
        </el-form-item>
        <el-form-item label="直播状态">
          <el-select v-model="editForm.status">
            <el-option label="预告中" value="预告中" />
            <el-option label="直播中" value="直播中" />
            <el-option label="已结束" value="已结束" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品数">
          <el-input-number v-model="editForm.productCount" :min="0" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="editForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm" placeholder="选择开始时间" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLiveRooms, createLiveRoom, updateLiveRoom, deleteLiveRoom } from '../api/admin'

const pageTitle = '直播管理'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  name: '',
  hostName: '',
  status: '预告中',
  productCount: 0,
  startTime: ''
})

// 原始直播数据（用于本地筛选）
const allRooms = ref([])

// ===== 示例数据：违规商品推广（无真实 API，用于展示违规直播/商品列表） =====
const violationList = ref([
  { id: 1, liveName: '春季新品专场 限时秒杀', hostName: '小鱼宠物馆', product: '宠物营养膏 100g', violationType: '虚假宣传', riskLevel: '高', disposeStatus: '待处置' },
  { id: 2, liveName: '猫咪营养品专场', hostName: '萌宠日记', product: '猫咪冻干零食 50g', violationType: '夸大功效', riskLevel: '中', disposeStatus: '待处置' },
  { id: 3, liveName: '狗狗口粮测评专场', hostName: '汪星人好物', product: '全价狗粮 2kg', violationType: '价格欺诈', riskLevel: '中', disposeStatus: '已警告' },
  { id: 4, liveName: '宠物玩具清仓直播', hostName: '某某宠物店', product: '宠物磨牙棒 3 支装', violationType: '三无产品', riskLevel: '高', disposeStatus: '已下架' }
])

// ===== 示例数据：合规监控规则（无真实 API，用于展示监控规则列表） =====
const monitorRules = ref([
  { id: 1, ruleName: '虚假宣传监测', monitorItem: '商品功效话术识别', status: '启用', alertCount: 3 },
  { id: 2, ruleName: '敏感词过滤', monitorItem: '直播音频实时转写', status: '启用', alertCount: 5 },
  { id: 3, ruleName: '商品资质校验', monitorItem: '上架商品证照核验', status: '启用', alertCount: 0 },
  { id: 4, ruleName: '低价引流监测', monitorItem: '价格异常波动检测', status: '停用', alertCount: 1 },
  { id: 5, ruleName: '未成年人保护', monitorItem: '观众画像识别', status: '启用', alertCount: 0 }
])

// ===== 示例数据：实时违规告警（无真实 API，用于展示最近告警列表） =====
const alertList = ref([
  { id: 1, alertType: '虚假宣传 - 夸大产品功效', source: '小鱼宠物馆', time: '3 分钟前' },
  { id: 2, alertType: '低俗用语警告', source: '萌宠日记', time: '15 分钟前' }
])

// 风险等级 → 标签类型映射
function riskTagType(level) {
  if (level === '高') return 'danger'
  if (level === '中') return 'warning'
  return 'info'
}

// 处置状态 → 标签类型映射
function disposeTagType(status) {
  if (status === '待处置') return 'danger'
  if (status === '已警告') return 'warning'
  if (status === '已下架') return 'success'
  return 'info'
}

// 下架商品（示例操作：无真实 API，仅本地更新状态）
function handleOffShelf(row) {
  row.disposeStatus = '已下架'
  ElMessage.success(`已下架「${row.product}」`)
}

// 发送警告（示例操作：无真实 API，仅本地更新状态）
function handleWarn(row) {
  row.disposeStatus = '已警告'
  ElMessage.success(`已对「${row.liveName}」发送警告`)
}

// 启用/停用监控规则（示例操作：无真实 API，仅本地切换状态）
function handleToggleRule(rule) {
  rule.status = rule.status === '启用' ? '停用' : '启用'
  ElMessage.success(`已${rule.status}「${rule.ruleName}」`)
}

// 处理实时告警（示例操作：无真实 API，仅本地移除）
function handleAlert(row) {
  alertList.value = alertList.value.filter(item => item.id !== row.id)
  ElMessage.success(`已处理告警「${row.alertType}」`)
}

// 加载直播列表
async function loadRooms() {
  try {
    const data = await getLiveRooms()
    allRooms.value = (data || []).map(item => ({
      id: item.id,
      name: item.name || '',
      hostName: item.hostName || '',
      status: item.status || '预告中',
      viewerCount: item.viewerCount ?? 0,
      productCount: item.productCount ?? 0,
      startTime: item.startTime || ''
    }))
    applyFilters()
  } catch (e) {
    console.error('获取直播列表失败', e)
  }
}

// 应用关键词筛选
function applyFilters() {
  let filtered = [...allRooms.value]
  if (filters.keyword) {
    filtered = filtered.filter(item =>
      item.name.includes(filters.keyword) || item.hostName.includes(filters.keyword)
    )
  }
  tableData.value = filtered
  total.value = filtered.length
}

function loadData() {
  applyFilters()
}

function handleSearch() { currentPage.value = 1; applyFilters() }
function handleReset() { filters.keyword = ''; handleSearch() }

function handleAdd() {
  dialogTitle.value = '新建直播'
  editForm.name = ''
  editForm.hostName = ''
  editForm.status = '预告中'
  editForm.productCount = 0
  editForm.startTime = ''
  editForm.id = undefined  // 清除ID确保调用创建API而非更新API
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑直播'
  Object.assign(editForm, row)
  dialogVisible.value = true
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deleteLiveRoom(row.id)
    ElMessage.success('删除成功')
    await loadRooms()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}

async function handleSave() {
  try {
    if (editForm.id) {
      await updateLiveRoom(editForm.id, {
        name: editForm.name,
        hostName: editForm.hostName,
        status: editForm.status,
        productCount: editForm.productCount,
        startTime: editForm.startTime
      })
    } else {
      await createLiveRoom({
        name: editForm.name,
        hostName: editForm.hostName,
        status: editForm.status,
        productCount: editForm.productCount,
        startTime: editForm.startTime
      })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadRooms()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

onMounted(() => loadRooms())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
/* 缺失区块通用卡片间距 */
.block-card { margin-bottom: 16px; }
.block-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.block-header h3 { font-size: 16px; font-weight: 700; color: var(--text-800); margin: 0; }
.block-header-right { display: flex; align-items: center; gap: 10px; }
.block-sub { font-size: 12px; color: var(--text-400); }
/* 违规商品推广 - 直播单元格 */
.live-cell { display: flex; flex-direction: column; line-height: 1.4; }
.live-name { font-weight: 600; color: var(--text-800); font-size: 13px; }
.live-host { font-size: 12px; color: var(--text-400); }
/* 合规监控 - 规则卡片 */
.rule-card { border: 1px solid var(--border); border-radius: var(--radius); padding: 14px 16px; margin-bottom: 16px; background: var(--card); transition: all 0.2s ease; }
.rule-card:hover { box-shadow: var(--shadow-md); border-color: var(--brand-300); }
.rule-card-disabled { opacity: 0.65; }
.rule-card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.rule-name { font-size: 14px; font-weight: 600; color: var(--text-800); }
.rule-item { font-size: 12px; color: var(--text-500); }
.rule-card-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 12px; padding-top: 10px; border-top: 1px dashed var(--border); }
.rule-alert { font-size: 12px; color: var(--text-400); }
.rule-alert.has-alert { color: var(--state-error); font-weight: 600; }
</style>
