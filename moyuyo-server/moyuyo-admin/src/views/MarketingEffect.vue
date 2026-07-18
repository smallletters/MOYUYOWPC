<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建活动</el-button>
      </div>
    </div>
    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-value">{{ kpiData.campaignCount }}</div>
          <div class="kpi-label">营销活动数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-value blue">{{ kpiData.totalImpression }}</div>
          <div class="kpi-label">总曝光</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-value green">{{ kpiData.totalClicks }}</div>
          <div class="kpi-label">总点击</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-value orange">{{ kpiData.conversionRate }}%</div>
          <div class="kpi-label">转化率</div>
        </el-card>
      </el-col>
    </el-row>
    <el-card shadow="never">
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="campaignName" label="活动名称" width="180" />
        <el-table-column prop="impression" label="曝光量" width="100" />
        <el-table-column prop="clicks" label="点击量" width="90" />
        <el-table-column label="点击率" width="90">
          <template #default="{ row }">{{ row.clickRate }}%</template>
        </el-table-column>
        <el-table-column prop="conversions" label="转化数" width="80" />
        <el-table-column label="ROI" width="80">
          <template #default="{ row }">{{ row.roi }}</template>
        </el-table-column>
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
        <el-form-item label="活动名称">
          <el-input v-model="editForm.campaignName" placeholder="请输入活动名称" />
        </el-form-item>
        <el-form-item label="曝光量">
          <el-input-number v-model="editForm.impression" :min="0" />
        </el-form-item>
        <el-form-item label="点击量">
          <el-input-number v-model="editForm.clicks" :min="0" />
        </el-form-item>
        <el-form-item label="转化数">
          <el-input-number v-model="editForm.conversions" :min="0" />
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
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  campaignName: '',
  impression: 0,
  clicks: 0,
  conversions: 0
})

// KPI数据（通过API获取）
const kpiData = reactive({
  campaignCount: 0,
  totalImpression: 0,
  totalClicks: 0,
  conversionRate: 0
})

// 原始营销活动数据
const allCampaigns = ref([])

// 加载KPI统计
async function loadKpi() {
  try {
    const effects = await getMarketingEffects()
    if (effects) {
      kpiData.campaignCount = effects.campaignCount ?? 0
      kpiData.totalImpression = effects.totalImpression ?? 0
      kpiData.totalClicks = effects.totalClicks ?? 0
      kpiData.conversionRate = effects.conversionRate ?? 0
    }
  } catch (e) {
    console.error('获取营销效果KPI失败', e)
  }
}

// 加载活动列表
async function loadCampaignEffects() {
  try {
    const data = await getCampaigns()
    allCampaigns.value = (data || []).map(item => ({
      id: item.id,
      campaignName: item.name || item.campaignName || '',
      impression: item.impression ?? 0,
      clicks: item.clicks ?? 0,
      clickRate: item.clickRate ?? 0,
      conversions: item.conversions ?? 0,
      roi: item.roi ?? '0'
    }))
    applyFilters()
  } catch (e) {
    console.error('获取活动列表失败', e)
  }
}

// 本地筛选
function applyFilters() {
  tableData.value = [...allCampaigns.value]
  total.value = allCampaigns.value.length
}

function loadData() {
  applyFilters()
}

function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建活动'; editForm.campaignName = ''; editForm.impression = 0; editForm.clicks = 0; editForm.conversions = 0; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑活动'; Object.assign(editForm, row); dialogVisible.value = true }
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
    if (editForm.id) {
      await updateCampaign(editForm.id, {
        campaignName: editForm.campaignName,
        impression: editForm.impression,
        clicks: editForm.clicks,
        conversions: editForm.conversions
      })
    } else {
      await createCampaign({
        campaignName: editForm.campaignName,
        impression: editForm.impression,
        clicks: editForm.clicks,
        conversions: editForm.conversions
      })
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
