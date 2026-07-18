<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>价格历史</h2>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="商品名称">
          <el-input v-model="filters.keyword" placeholder="请输入关键词" clearable />
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
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
        <el-table-column prop="sku" label="SKU" width="130" />
        <el-table-column prop="originalPrice" label="原价" width="100">
          <template #default="{ row }">¥{{ row.originalPrice }}</template>
        </el-table-column>
        <el-table-column prop="adjustedPrice" label="调整后价格" width="110">
          <template #default="{ row }">¥{{ row.adjustedPrice }}</template>
        </el-table-column>
        <el-table-column prop="adjustType" label="调整类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.adjustType === '涨价' ? 'danger' : 'success'" size="small">{{ row.adjustType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="110" />
        <el-table-column prop="adjustTime" label="调整时间" width="170" />
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
import { getPriceList } from '../api/admin'

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dateRange = ref(null)

const filters = reactive({
  keyword: ''
})

const tableData = ref([])

// 从API加载价格历史数据（复用价格列表API）
async function loadData() {
  try {
    const res = await getPriceList()
    const list = (res && res.records) || res || []
    // 根据关键词过滤
    let filtered = list.filter(d => {
      const kw = filters.keyword.toLowerCase()
      if (kw && !d.productName.toLowerCase().includes(kw)) return false
      return true
    })
    total.value = filtered.length
    const start = (currentPage.value - 1) * pageSize.value
    tableData.value = filtered.slice(start, start + pageSize.value)
  } catch (e) {
    console.error('加载价格历史失败:', e)
  }
}

function handleSearch() { currentPage.value = 1; loadData() }

function handleReset() { filters.keyword = ''; dateRange.value = null; handleSearch() }

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
