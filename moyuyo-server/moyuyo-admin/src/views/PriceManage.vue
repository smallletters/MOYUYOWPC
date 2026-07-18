<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>价格管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">调整价格</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="商品名称">
          <el-input v-model="filters.keyword" placeholder="请输入关键词" clearable />
        </el-form-item>
        <el-form-item label="价格区间">
          <el-input-number v-model="filters.priceMin" :min="0" placeholder="最低价" style="width:130px" />
          <span style="margin:0 8px">-</span>
          <el-input-number v-model="filters.priceMax" :min="0" placeholder="最高价" style="width:130px" />
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
        <el-table-column prop="sku" label="SKU" width="140" />
        <el-table-column prop="originalPrice" label="原价" width="100">
          <template #default="{ row }">¥{{ row.originalPrice }}</template>
        </el-table-column>
        <el-table-column prop="sellingPrice" label="销售价" width="100">
          <template #default="{ row }">¥{{ row.sellingPrice }}</template>
        </el-table-column>
        <el-table-column prop="costPrice" label="成本价" width="100">
          <template #default="{ row }">¥{{ row.costPrice }}</template>
        </el-table-column>
        <el-table-column prop="margin" label="毛利率" width="100">
          <template #default="{ row }">{{ row.margin }}%</template>
        </el-table-column>
        <el-table-column prop="lastModifyTime" label="最后修改时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">调价</el-button>
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
        <el-form-item label="商品名称">
          <el-input v-model="editForm.productName" disabled />
        </el-form-item>
        <el-form-item label="SKU">
          <el-input v-model="editForm.sku" disabled />
        </el-form-item>
        <el-form-item label="原价">
          <el-input-number v-model="editForm.originalPrice" :min="0" :precision="2" style="width:100%" disabled />
        </el-form-item>
        <el-form-item label="销售价" required>
          <el-input-number v-model="editForm.sellingPrice" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="成本价">
          <el-input-number v-model="editForm.costPrice" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="调价原因">
          <el-input v-model="editForm.reason" type="textarea" :rows="3" placeholder="请输入调价原因" />
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
import { getPriceList, createPrice, updatePrice, deletePrice } from '../api/admin'

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)

const filters = reactive({
  keyword: '',
  priceMin: null,
  priceMax: null
})

const editForm = reactive({
  id: null,
  productName: '',
  sku: '',
  originalPrice: 0,
  sellingPrice: 0,
  costPrice: 0,
  margin: 0,
  reason: '',
  lastModifyTime: ''
})

const tableData = ref([])

// 从API加载价格列表数据
async function loadData() {
  try {
    const res = await getPriceList()
    const list = (res && res.records) || res || []
    // 根据筛选条件过滤
    let filtered = list.filter(d => {
      const kw = filters.keyword.toLowerCase()
      if (kw && !d.productName.toLowerCase().includes(kw)) return false
      if (filters.priceMin !== null && d.sellingPrice < filters.priceMin) return false
      if (filters.priceMax !== null && d.sellingPrice > filters.priceMax) return false
      return true
    })
    total.value = filtered.length
    const start = (currentPage.value - 1) * pageSize.value
    tableData.value = filtered.slice(start, start + pageSize.value)
  } catch (e) {
    console.error('加载价格列表失败:', e)
    ElMessage.error('加载价格列表失败')
  }
}

function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; filters.priceMin = null; filters.priceMax = null; handleSearch() }

function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '调整价格'
  editForm.id = null
  editForm.productName = ''
  editForm.sku = ''
  editForm.originalPrice = 0
  editForm.sellingPrice = 0
  editForm.costPrice = 0
  editForm.margin = 0
  editForm.reason = ''
  editForm.lastModifyTime = ''
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  dialogTitle.value = '调整价格'
  Object.assign(editForm, row)
  editForm.reason = ''
  dialogVisible.value = true
}

// 保存调价信息（调用API）
async function handleSave() {
  if (editForm.sellingPrice <= 0) {
    ElMessage.warning('请输入有效的销售价')
    return
  }
  try {
    if (isEdit.value) {
      await updatePrice({
        id: editForm.id,
        sellingPrice: editForm.sellingPrice,
        costPrice: editForm.costPrice,
        reason: editForm.reason
      })
      ElMessage.success('调价成功')
    } else {
      await createPrice({
        productName: editForm.productName,
        sku: editForm.sku,
        sellingPrice: editForm.sellingPrice,
        costPrice: editForm.costPrice
      })
      ElMessage.success('新增价格成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('保存价格失败:', e)
    ElMessage.error('保存失败')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
