<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入仓库名称" clearable />
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
        <el-table-column prop="name" label="仓库名称" width="150" />
        <el-table-column prop="country" label="所在国家/地区" width="140" />
        <el-table-column prop="skuCount" label="库存SKU数" width="100" />
        <el-table-column prop="totalStock" label="库存总量" width="100" />
        <el-table-column label="仓容使用率" width="100">
          <template #default="{ row }">{{ row.usageRate }}%</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === '正常' ? 'success' : row.status === '满仓' ? 'danger' : 'warning'">{{ row.status }}</el-tag>
          </template>
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
      <el-form :model="editForm" label-width="120px">
        <el-form-item label="仓库名称">
          <el-input v-model="editForm.name" placeholder="请输入仓库名称" />
        </el-form-item>
        <el-form-item label="所在国家/地区">
          <el-input v-model="editForm.country" placeholder="请输入国家/地区" />
        </el-form-item>
        <el-form-item label="库存SKU数">
          <el-input-number v-model="editForm.skuCount" :min="0" />
        </el-form-item>
        <el-form-item label="库存总量">
          <el-input-number v-model="editForm.totalStock" :min="0" />
        </el-form-item>
        <el-form-item label="仓容使用率(%)">
          <el-input-number v-model="editForm.usageRate" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="正常" value="正常" />
            <el-option label="满仓" value="满仓" />
            <el-option label="维护中" value="维护中" />
          </el-select>
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

const pageTitle = '海外仓管理'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  name: '',
  country: '',
  skuCount: 0,
  totalStock: 0,
  usageRate: 0,
  status: '正常'
})

const mockData = [
  { id: 1, name: '洛杉矶海外仓', country: '美国', skuCount: 320, totalStock: 15800, usageRate: 72, status: '正常' },
  { id: 2, name: '纽约海外仓', country: '美国', skuCount: 280, totalStock: 12300, usageRate: 85, status: '正常' },
  { id: 3, name: '伦敦海外仓', country: '英国', skuCount: 150, totalStock: 7600, usageRate: 95, status: '满仓' },
  { id: 4, name: '东京海外仓', country: '日本', skuCount: 200, totalStock: 9800, usageRate: 55, status: '正常' },
  { id: 5, name: '法兰克福海外仓', country: '德国', skuCount: 180, totalStock: 8500, usageRate: 45, status: '维护中' },
  { id: 6, name: '悉尼海外仓', country: '澳大利亚', skuCount: 90, totalStock: 4200, usageRate: 38, status: '正常' }
]

function loadData() {
  let filtered = [...mockData]
  if (filters.keyword) {
    filtered = filtered.filter(item => item.name.includes(filters.keyword) || item.country.includes(filters.keyword))
  }
  tableData.value = filtered
  total.value = filtered.length
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建海外仓'; editForm.name = ''; editForm.country = ''; editForm.skuCount = 0; editForm.totalStock = 0; editForm.usageRate = 0; editForm.status = '正常'; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑海外仓'; Object.assign(editForm, row); dialogVisible.value = true }
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
