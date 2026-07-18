<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>商品分析</h2>
    </div>
    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">总商品数</div>
            <div class="kpi-value">{{ kpiData.totalProducts }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">在售商品</div>
            <div class="kpi-value" style="color:#409eff">{{ kpiData.activeProducts }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">总浏览量</div>
            <div class="kpi-value" style="color:#e6a23c">{{ kpiData.totalViews }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">总销量</div>
            <div class="kpi-value" style="color:#67c23a">{{ kpiData.totalSales }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="商品名称">
          <el-input v-model="filters.keyword" placeholder="请输入关键词" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="productName" label="商品名称" min-width="160" />
        <el-table-column prop="views" label="浏览量" width="100" sortable />
        <el-table-column prop="favorites" label="收藏量" width="100" sortable />
        <el-table-column prop="cartAdds" label="加购量" width="100" sortable />
        <el-table-column prop="sales" label="销量" width="100" sortable />
        <el-table-column prop="revenue" label="销售额" width="120" sortable>
          <template #default="{ row }">¥{{ row.revenue.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleDetail(row)">查看详情</el-button>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProductAnalysisKpi, getProductAnalysisList } from '../api/admin'

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  keyword: ''
})

// KPI数据，从API获取
const kpiData = reactive({
  totalProducts: 0,
  activeProducts: 0,
  totalViews: 0,
  totalSales: 0
})

const tableData = ref([])

// 从API加载KPI和列表数据
async function loadData() {
  try {
    // 并行加载KPI和列表数据
    const [kpiRes, listRes] = await Promise.all([
      getProductAnalysisKpi(),
      getProductAnalysisList()
    ])
    // 填充KPI
    if (kpiRes) {
      Object.assign(kpiData, kpiRes)
    }
    // 填充列表
    const list = (listRes && listRes.records) || listRes || []
    const filtered = list.filter(d => {
      const kw = filters.keyword.toLowerCase()
      if (kw && !d.productName.toLowerCase().includes(kw)) return false
      return true
    })
    total.value = filtered.length
    const start = (currentPage.value - 1) * pageSize.value
    tableData.value = filtered.slice(start, start + pageSize.value)
  } catch (e) {
    console.error('加载商品分析数据失败:', e)
    ElMessage.error('加载商品分析数据失败')
  }
}

function handleSearch() { currentPage.value = 1; loadData() }

function handleReset() { filters.keyword = ''; handleSearch() }

function handleDetail(row) {
  ElMessage.info('查看商品详情：' + row.productName)
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.kpi-row { margin-bottom: 16px; }
.kpi-card { text-align: center; padding: 8px 0; }
.kpi-label { font-size: 14px; color: var(--text-400); margin-bottom: 8px; }
.kpi-value { font-size: 28px; font-weight: 700; color: var(--text-800); }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
