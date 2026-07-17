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

// KPI数据
const kpiData = reactive({
  campaignCount: 8,
  totalImpression: 568000,
  totalClicks: 85600,
  conversionRate: 3.2
})

const mockData = [
  { id: 1, campaignName: '夏日清凉大促', impression: 125000, clicks: 18500, clickRate: 14.8, conversions: 620, roi: '3.5' },
  { id: 2, campaignName: '618大促活动', impression: 250000, clicks: 42000, clickRate: 16.8, conversions: 1580, roi: '5.2' },
  { id: 3, campaignName: '新用户首单优惠', impression: 85000, clicks: 12800, clickRate: 15.1, conversions: 890, roi: '4.8' },
  { id: 4, campaignName: '海外购专场', impression: 58000, clicks: 6500, clickRate: 11.2, conversions: 280, roi: '2.9' },
  { id: 5, campaignName: '会员日特惠', impression: 32000, clicks: 3800, clickRate: 11.9, conversions: 150, roi: '3.1' },
  { id: 6, campaignName: '限时秒杀活动', impression: 18000, clicks: 2000, clickRate: 11.1, conversions: 95, roi: '2.6' }
]

function loadData() {
  tableData.value = [...mockData]
  total.value = mockData.length
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建活动'; editForm.campaignName = ''; editForm.impression = 0; editForm.clicks = 0; editForm.conversions = 0; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑活动'; Object.assign(editForm, row); dialogVisible.value = true }
function handleDelete(row) { ElMessageBox.confirm('确定删除？','提示').then(() => { ElMessage.success('删除成功'); loadData() }) }
function handleSave() { ElMessage.success('保存成功'); dialogVisible.value = false; loadData() }
onMounted(() => loadData())
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
