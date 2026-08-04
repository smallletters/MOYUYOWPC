<template>
  <div class="page-wrapper">
    <!-- 页面标题与操作区 -->
    <div class="page-header">
      <div class="page-header-left">
        <h2>A/B 测试</h2>
        <p class="page-desc">管理实验创建、流量分配与效果分析，数据驱动产品决策</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建实验</el-button>
      </div>
    </div>

    <!-- 状态筛选 -->
    <div class="filter-tabs">
      <button
        v-for="tab in filterTabs"
        :key="tab.key"
        class="filter-tab"
        :class="{ 'filter-tab-active': activeFilter === tab.key }"
        @click="activeFilter = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- 实验表格 -->
    <el-card shadow="never">
      <el-table :data="filteredData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="实验名称" min-width="160">
          <template #default="{ row }">
            <span class="exp-name">{{ row.name }}</span>
            <div class="exp-desc">{{ row.description || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="A 组（对照组）" width="140">
          <template #default="{ row }">
            <div class="group-cell">
              <span class="group-num">{{ row.groupAVisitors ?? 0 }}</span>
              <span class="group-conv">{{ fmtRate(row.groupAConvRate) }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="B 组（实验组）" width="140">
          <template #default="{ row }">
            <div class="group-cell">
              <span class="group-num">{{ row.groupBVisitors ?? 0 }}</span>
              <span class="group-conv">{{ fmtRate(row.groupBConvRate) }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="提升幅度" width="100">
          <template #default="{ row }">
            <span :class="liftClass(row)" v-if="liftOf(row) !== null">{{ liftOf(row) > 0 ? '+' : '' }}{{ liftOf(row) }}%</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">详情</el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === '运行中'" type="danger" link size="small" @click="handleStop(row)">终止</el-button>
            <el-button v-if="row.status === '运行中'" type="success" link size="small" @click="handleRollout(row)">推全量</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="filteredData.length === 0" description="暂无实验数据，点击右上角「新建实验」创建" />
    </el-card>

    <!-- 新建/编辑实验弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" :close-on-click-modal="false">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="实验名称" required>
          <el-input v-model="editForm.name" placeholder="请输入实验名称" />
        </el-form-item>
        <el-form-item label="实验描述">
          <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="实验目标与说明" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width:100%">
            <el-option label="运行中" value="运行中" />
            <el-option label="已结束" value="已结束" />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">A 组（对照组）</el-divider>
        <el-form-item label="访客数">
          <el-input-number v-model="editForm.groupAVisitors" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="转化率(%)">
          <el-input-number v-model="editForm.groupAConvRate" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-divider content-position="left">B 组（实验组）</el-divider>
        <el-form-item label="访客数">
          <el-input-number v-model="editForm.groupBVisitors" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="转化率(%)">
          <el-input-number v-model="editForm.groupBConvRate" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="实验详情" width="480px">
      <div v-if="detailRow" class="detail-body">
        <div class="detail-item"><span class="detail-label">实验名称</span><span class="detail-value">{{ detailRow.name }}</span></div>
        <div class="detail-item"><span class="detail-label">状态</span><span class="detail-value">{{ detailRow.status }}</span></div>
        <div class="detail-item"><span class="detail-label">描述</span><span class="detail-value">{{ detailRow.description || '-' }}</span></div>
        <div class="detail-item"><span class="detail-label">开始时间</span><span class="detail-value">{{ formatTime(detailRow.startTime) }}</span></div>
        <el-divider content-position="left">效果对比</el-divider>
        <div class="compare-grid">
          <div class="compare-card">
            <div class="compare-title">A 组（对照组）</div>
            <div class="compare-big">{{ detailRow.groupAVisitors ?? 0 }}</div>
            <div class="compare-sub">访客数 · 转化率 {{ fmtRate(detailRow.groupAConvRate) }}%</div>
          </div>
          <div class="compare-card">
            <div class="compare-title">B 组（实验组）</div>
            <div class="compare-big">{{ detailRow.groupBVisitors ?? 0 }}</div>
            <div class="compare-sub">访客数 · 转化率 {{ fmtRate(detailRow.groupBConvRate) }}%</div>
          </div>
        </div>
        <div class="detail-item" v-if="liftOf(detailRow) !== null">
          <span class="detail-label">提升幅度</span>
          <span :class="liftClass(detailRow)">{{ liftOf(detailRow) > 0 ? '+' : '' }}{{ liftOf(detailRow) }}%</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAbTests, createAbTest, updateAbTest } from '../api/admin'
import { toArray } from '../utils/safeArray'

const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const saving = ref(false)
const detailVisible = ref(false)
const detailRow = ref(null)

// 状态筛选
const activeFilter = ref('all')
const filterTabs = [
  { key: 'all', label: '全部' },
  { key: 'running', label: '运行中' },
  { key: 'completed', label: '已完成' }
]

// 编辑表单（与后端 AbTest 字段一致）
const editForm = reactive({
  id: undefined,
  name: '',
  description: '',
  status: '运行中',
  groupAVisitors: 0,
  groupBVisitors: 0,
  groupAConvRate: 0,
  groupBConvRate: 0
})

const filteredData = computed(() => {
  if (activeFilter.value === 'running') return tableData.value.filter(t => t.status === '运行中')
  if (activeFilter.value === 'completed') return tableData.value.filter(t => t.status === '已结束')
  return tableData.value
})

// 列表加载
async function loadData() {
  try {
    const res = await getAbTests()
    tableData.value = toArray(res)
  } catch (err) {
    console.error('获取A/B测试数据失败', err)
    ElMessage.error('获取A/B测试数据失败')
  }
}

function statusTag(status) {
  if (status === '运行中') return 'success'
  if (status === '已结束') return 'info'
  return 'warning'
}

function fmtRate(v) {
  if (v === null || v === undefined) return '0.00'
  return Number(v).toFixed(2)
}

// B 组相对 A 组的提升幅度（百分点）
function liftOf(row) {
  const a = Number(row.groupAConvRate)
  const b = Number(row.groupBConvRate)
  if (isNaN(a) || isNaN(b) || a === 0) return null
  return Number(((b - a) / a * 100).toFixed(2))
}

function liftClass(row) {
  const v = liftOf(row)
  if (v === null) return 'text-muted'
  return v > 0 ? 'lift-up' : (v < 0 ? 'lift-down' : 'text-muted')
}

function formatTime(v) {
  if (!v) return '-'
  return String(v).replace('T', ' ').slice(0, 16)
}

// 新建实验
function handleAdd() {
  dialogTitle.value = '新建实验'
  Object.assign(editForm, {
    id: undefined, name: '', description: '', status: '运行中',
    groupAVisitors: 0, groupBVisitors: 0, groupAConvRate: 0, groupBConvRate: 0
  })
  dialogVisible.value = true
}

// 编辑实验
function handleEdit(row) {
  dialogTitle.value = '编辑实验'
  Object.assign(editForm, {
    id: row.id,
    name: row.name || '',
    description: row.description || '',
    status: row.status || '运行中',
    groupAVisitors: row.groupAVisitors ?? 0,
    groupBVisitors: row.groupBVisitors ?? 0,
    groupAConvRate: row.groupAConvRate ?? 0,
    groupBConvRate: row.groupBConvRate ?? 0
  })
  dialogVisible.value = true
}

// 保存（新建或更新）
async function handleSave() {
  if (!editForm.name || !editForm.name.trim()) {
    ElMessage.warning('请输入实验名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: editForm.name.trim(),
      description: editForm.description || '',
      status: editForm.status,
      groupAVisitors: editForm.groupAVisitors ?? 0,
      groupBVisitors: editForm.groupBVisitors ?? 0,
      groupAConvRate: editForm.groupAConvRate ?? 0,
      groupBConvRate: editForm.groupBConvRate ?? 0
    }
    if (editForm.id) {
      await updateAbTest(editForm.id, payload)
      ElMessage.success('实验更新成功')
    } else {
      await createAbTest(payload)
      ElMessage.success('实验创建成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch (err) {
    console.error('保存实验失败', err)
    ElMessage.error('保存失败: ' + (err.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 查看详情
function handleView(row) {
  detailRow.value = row
  detailVisible.value = true
}

// 终止实验
async function handleStop(row) {
  try {
    await ElMessageBox.confirm(`确定终止实验「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch (e) {
    return // 用户取消或点击关闭按钮（值为 'cancel' 或 'close'）
  }
  try {
    await updateAbTest(row.id, { status: '已结束' })
    ElMessage.success('实验已终止')
    await loadData()
  } catch (e) {
    ElMessage.error('终止实验失败: ' + (e.message || '未知错误'))
  }
}

// 推全量：将运行中实验置为已结束，表示胜出版本已全量推广
async function handleRollout(row) {
  try {
    await ElMessageBox.confirm(`确定将实验「${row.name}」胜出版本推全量吗？推送后将停止实验流量分配。`, '推全量', { type: 'warning' })
  } catch (e) {
    return // 用户取消或点击关闭按钮（值为 'cancel' 或 'close'）
  }
  try {
    await updateAbTest(row.id, { status: '已结束' })
    ElMessage.success('已推全量，胜出版本全量生效')
    await loadData()
  } catch (e) {
    ElMessage.error('推全量失败: ' + (e.message || '未知错误'))
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.page-header-left h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0 0 4px; }
.page-desc { font-size: 12px; color: var(--text-400); margin: 0; }
.header-actions { display: flex; gap: 8px; }

/* 状态筛选 Tabs */
.filter-tabs { display: flex; gap: 8px; margin: 12px 0 16px; }
.filter-tab {
  padding: 6px 16px; border-radius: 999px; border: 1px solid var(--border);
  background: var(--card); color: var(--text-600); font-size: 13px; cursor: pointer;
}
.filter-tab-active { background: var(--primary); border-color: var(--primary); color: #fff; }

.exp-name { font-weight: 600; color: var(--text-800); }
.exp-desc { font-size: 12px; color: var(--text-400); margin-top: 2px; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.group-cell { display: flex; flex-direction: column; gap: 2px; }
.group-num { font-weight: 600; color: var(--text-800); }
.group-conv { font-size: 12px; color: var(--text-400); }
.text-muted { color: var(--text-400); }
.lift-up { color: var(--state-success); font-weight: 600; }
.lift-down { color: var(--state-error); font-weight: 600; }

/* 详情弹窗 */
.detail-body { padding: 0 4px; }
.detail-item { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid var(--background-100); font-size: 13px; }
.detail-item:last-child { border-bottom: none; }
.detail-label { color: var(--text-400); }
.detail-value { color: var(--text-700); font-weight: 500; }
.compare-grid { display: flex; gap: 12px; margin-bottom: 8px; }
.compare-card { flex: 1; border: 1px solid var(--border); border-radius: 8px; padding: 14px; text-align: center; }
.compare-title { font-size: 12px; color: var(--text-400); margin-bottom: 6px; }
.compare-big { font-size: 24px; font-weight: 700; color: var(--text-800); }
.compare-sub { font-size: 12px; color: var(--text-400); margin-top: 4px; }
</style>
