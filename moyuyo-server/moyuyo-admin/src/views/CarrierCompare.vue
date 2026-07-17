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
          <el-input v-model="filters.keyword" placeholder="请输入承运商名称" clearable />
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
        <el-table-column prop="name" label="承运商名称" width="140" />
        <el-table-column prop="transportMode" label="运输方式" width="120" />
        <el-table-column prop="avgDays" label="平均时效(天)" width="120" />
        <el-table-column prop="firstWeightPrice" label="首重价格" width="100">
          <template #default="{ row }">￥{{ row.firstWeightPrice }}</template>
        </el-table-column>
        <el-table-column prop="renewWeightPrice" label="续重价格" width="100">
          <template #default="{ row }">￥{{ row.renewWeightPrice }}</template>
        </el-table-column>
        <el-table-column prop="praiseRate" label="好评率" width="100">
          <template #default="{ row }">{{ row.praiseRate }}%</template>
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
      <el-form :model="editForm" label-width="110px">
        <el-form-item label="承运商名称">
          <el-input v-model="editForm.name" placeholder="请输入承运商名称" />
        </el-form-item>
        <el-form-item label="运输方式">
          <el-select v-model="editForm.transportMode">
            <el-option label="快递" value="快递" />
            <el-option label="海运" value="海运" />
            <el-option label="空运" value="空运" />
            <el-option label="陆运" value="陆运" />
          </el-select>
        </el-form-item>
        <el-form-item label="平均时效(天)">
          <el-input-number v-model="editForm.avgDays" :min="1" :max="60" />
        </el-form-item>
        <el-form-item label="首重价格(元)">
          <el-input-number v-model="editForm.firstWeightPrice" :min="0" :step="0.5" />
        </el-form-item>
        <el-form-item label="续重价格(元)">
          <el-input-number v-model="editForm.renewWeightPrice" :min="0" :step="0.5" />
        </el-form-item>
        <el-form-item label="好评率(%)">
          <el-input-number v-model="editForm.praiseRate" :min="0" :max="100" :step="0.1" />
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

const pageTitle = '承运商对比'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  name: '',
  transportMode: '快递',
  avgDays: 3,
  firstWeightPrice: 0,
  renewWeightPrice: 0,
  praiseRate: 95
})

const mockData = [
  { id: 1, name: '顺丰速运', transportMode: '快递', avgDays: 2, firstWeightPrice: 12, renewWeightPrice: 5, praiseRate: 98.5 },
  { id: 2, name: '中通快递', transportMode: '快递', avgDays: 3, firstWeightPrice: 8, renewWeightPrice: 3, praiseRate: 96.2 },
  { id: 3, name: '中国邮政海运', transportMode: '海运', avgDays: 25, firstWeightPrice: 50, renewWeightPrice: 20, praiseRate: 92.8 },
  { id: 4, name: 'DHL国际快递', transportMode: '空运', avgDays: 5, firstWeightPrice: 80, renewWeightPrice: 35, praiseRate: 97.1 },
  { id: 5, name: 'FedEx国际', transportMode: '空运', avgDays: 4, firstWeightPrice: 75, renewWeightPrice: 30, praiseRate: 96.9 },
  { id: 6, name: '韵达陆运', transportMode: '陆运', avgDays: 5, firstWeightPrice: 6, renewWeightPrice: 2, praiseRate: 94.5 }
]

function loadData() {
  let filtered = [...mockData]
  if (filters.keyword) {
    filtered = filtered.filter(item => item.name.includes(filters.keyword))
  }
  tableData.value = filtered
  total.value = filtered.length
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建承运商'; editForm.name = ''; editForm.transportMode = '快递'; editForm.avgDays = 3; editForm.firstWeightPrice = 0; editForm.renewWeightPrice = 0; editForm.praiseRate = 95; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑承运商'; Object.assign(editForm, row); dialogVisible.value = true }
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
