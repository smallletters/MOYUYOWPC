<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建活动</el-button>
      </div>
    </div>
    <!-- KPI 卡片 - 匹配后端返回的总GMV/订单数据 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-value">{{ kpiData.totalGmv }}</div>
          <div class="kpi-label">总GMV</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-value blue">{{ kpiData.totalOrders }}</div>
          <div class="kpi-label">总订单数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-value green">{{ kpiData.campaignGmv }}</div>
          <div class="kpi-label">近期活动GMV</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-value orange">{{ kpiData.campaignRatio }}%</div>
          <div class="kpi-label">活动订单占比</div>
        </el-card>
      </el-col>
    </el-row>
    <el-card shadow="never">
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="活动名称" width="180" show-overflow-tooltip />
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
        <el-table-column prop="budget" label="预算" width="100" />
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
    <!-- 新建/编辑活动对话框 - 匹配 CampaignRequest DTO -->
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCampaigns, getMarketingEffects, deleteCampaign, createCampaign, updateCampaign } from '../api/admin'

const pageTitle = '营销效果'
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')

// 编辑表单字段对应后端 CampaignRequest DTO：name, type, startDate, endDate, description, budget
const editForm = reactive({
  name: '',
  type: '满减',
  startDate: '',
  endDate: '',
  description: '',
  budget: 0
})

// KPI数据对应后端 getMarketingEffects 返回：totalGmv, totalOrders, campaignGmv, campaignRatio
const kpiData = reactive({
  totalGmv: '0',
  totalOrders: 0,
  campaignGmv: '0',
  campaignRatio: 0
})

// 原始活动数据
const allCampaigns = ref([])

// 加载KPI统计
async function loadKpi() {
  try {
    const effects = await getMarketingEffects()
    if (effects) {
      kpiData.totalGmv = effects.totalGmv != null ? effects.totalGmv : '0'
      kpiData.totalOrders = effects.totalOrders ?? 0
      kpiData.campaignGmv = effects.campaignGmv != null ? effects.campaignGmv : '0'
      kpiData.campaignRatio = effects.campaignRatio ?? 0
    }
  } catch (e) {
    console.error('获取营销效果KPI失败', e)
  }
}

// 加载活动列表 - 后端返回：id, name, type, status, startDate, endDate, participants, gmv, budget
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
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑活动'
  // 从行数据还原编辑表单
  editForm.name = row.name || ''
  editForm.type = row.type || '满减'
  editForm.startDate = row.startDate || ''
  editForm.endDate = row.endDate || ''
  editForm.description = row.description || ''
  editForm.budget = row.budget != null ? Number(row.budget) : 0
  // 保存ID用于区分新建/更新
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
    // 发送后端 CampaignRequest 期望的字段
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
  await Promise.all([loadKpi(), loadCampaignEffects()])
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.kpi-row { margin-bottom: 16px; }
.kpi-card { text-align: center; padding: 16px 0; }
.kpi-value { font-size: 28px; font-weight: 700; color: var(--text-800); }
.kpi-value.blue { color: var(--primary); }
.kpi-value.green { color: var(--state-success); }
.kpi-value.orange { color: var(--state-warning); }
.kpi-label { font-size: 13px; color: var(--text-400); margin-top: 4px; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
