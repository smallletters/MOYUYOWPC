<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建策略</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入策略名称" clearable />
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
        <el-table-column prop="strategyName" label="策略名称" width="160" />
        <el-table-column prop="region" label="适用区域" width="130" />
        <el-table-column label="发货方式" width="120">
          <template #default="{ row }">
            <el-tag :type="row.shippingMethod === '快递' ? 'primary' : row.shippingMethod === '海运' ? 'warning' : 'success'">{{ row.shippingMethod }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="feeRule" label="运费计算规则" width="200" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'danger'">{{ row.status }}</el-tag>
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
        <el-form-item label="策略名称">
          <el-input v-model="editForm.strategyName" placeholder="请输入策略名称" />
        </el-form-item>
        <el-form-item label="适用区域">
          <el-input v-model="editForm.region" placeholder="如：华东地区" />
        </el-form-item>
        <el-form-item label="发货方式">
          <el-select v-model="editForm.shippingMethod">
            <el-option label="快递" value="快递" />
            <el-option label="海运" value="海运" />
            <el-option label="空运" value="空运" />
          </el-select>
        </el-form-item>
        <el-form-item label="运费计算规则">
          <el-input v-model="editForm.feeRule" placeholder="如：首重10元，续重5元/kg" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="editForm.priority" :min="1" :max="99" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="启用" value="启用" />
            <el-option label="停用" value="停用" />
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

const pageTitle = '发货策略'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  strategyName: '',
  region: '',
  shippingMethod: '快递',
  feeRule: '',
  priority: 1,
  status: '启用'
})

const mockData = [
  { id: 1, strategyName: '华东次日达', region: '华东地区', shippingMethod: '快递', feeRule: '首重10元，续重5元/kg', priority: 1, status: '启用' },
  { id: 2, strategyName: '华北经济型', region: '华北地区', shippingMethod: '快递', feeRule: '首重8元，续重4元/kg', priority: 2, status: '启用' },
  { id: 3, strategyName: '美国西岸海运', region: '美国西海岸', shippingMethod: '海运', feeRule: '首重50元，续重20元/kg', priority: 3, status: '启用' },
  { id: 4, strategyName: '欧洲空运特快', region: '欧洲主要国家', shippingMethod: '空运', feeRule: '首重80元，续重35元/kg', priority: 4, status: '停用' },
  { id: 5, strategyName: '华南极速达', region: '华南地区', shippingMethod: '快递', feeRule: '首重12元，续重6元/kg', priority: 1, status: '启用' },
  { id: 6, strategyName: '日本海运经济', region: '日本', shippingMethod: '海运', feeRule: '首重30元，续重12元/kg', priority: 5, status: '启用' }
]

function loadData() {
  let filtered = [...mockData]
  if (filters.keyword) {
    filtered = filtered.filter(item => item.strategyName.includes(filters.keyword))
  }
  tableData.value = filtered
  total.value = filtered.length
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建策略'; editForm.strategyName = ''; editForm.region = ''; editForm.shippingMethod = '快递'; editForm.feeRule = ''; editForm.priority = 1; editForm.status = '启用'; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑策略'; Object.assign(editForm, row); dialogVisible.value = true }
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
