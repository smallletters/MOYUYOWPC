<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleSync">同步数据</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入海关编码/商品名称" clearable />
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
        <el-table-column prop="customsCode" label="海关编码" width="130" />
        <el-table-column prop="productName" label="商品名称" width="180" />
        <el-table-column label="税率" width="100">
          <template #default="{ row }">{{ row.taxRate }}%</template>
        </el-table-column>
        <el-table-column prop="regulatoryConditions" label="监管条件" width="200" />
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
      <el-form :model="editForm" label-width="110px">
        <el-form-item label="海关编码">
          <el-input v-model="editForm.customsCode" placeholder="请输入海关编码" />
        </el-form-item>
        <el-form-item label="商品名称">
          <el-input v-model="editForm.productName" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="税率(%)">
          <el-input-number v-model="editForm.taxRate" :min="0" :max="100" :step="0.1" />
        </el-form-item>
        <el-form-item label="监管条件">
          <el-input v-model="editForm.regulatoryConditions" placeholder="请输入监管条件" />
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

const pageTitle = '海关管理'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  customsCode: '',
  productName: '',
  taxRate: 0,
  regulatoryConditions: ''
})

const mockData = [
  { id: 1, customsCode: '2106.90', productName: '深海鱼油软胶囊', taxRate: 6.5, regulatoryConditions: 'A/B' },
  { id: 2, customsCode: '2106.90', productName: '有机螺旋藻片', taxRate: 6.5, regulatoryConditions: 'A/B' },
  { id: 3, customsCode: '2936.21', productName: '维生素C泡腾片', taxRate: 5.0, regulatoryConditions: 'A' },
  { id: 4, customsCode: '3004.50', productName: '辅酶Q10胶囊', taxRate: 3.0, regulatoryConditions: 'Q' },
  { id: 5, customsCode: '2106.90', productName: '益生菌胶囊', taxRate: 6.5, regulatoryConditions: 'A/B' },
  { id: 6, customsCode: '3304.99', productName: '保湿面霜', taxRate: 10.0, regulatoryConditions: 'A' }
]

function loadData() {
  let filtered = [...mockData]
  if (filters.keyword) {
    filtered = filtered.filter(item => item.customsCode.includes(filters.keyword) || item.productName.includes(filters.keyword))
  }
  tableData.value = filtered
  total.value = filtered.length
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleSync() {
  ElMessage.success('数据同步中，请稍候...')
  setTimeout(() => {
    ElMessage.success('海关数据同步完成')
    loadData()
  }, 1500)
}
function handleEdit(row) { dialogTitle.value = '编辑海关编码'; Object.assign(editForm, row); dialogVisible.value = true }
function handleDelete(row) { ElMessageBox.confirm('确定删除？','提示').then(() => { ElMessage.success('删除成功'); loadData() }) }
function handleSave() { ElMessage.success('保存成功'); dialogVisible.value = false; loadData() }
onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
