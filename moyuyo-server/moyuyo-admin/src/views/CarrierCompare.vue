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
import { getCarriers, createCarrier, updateCarrier, deleteCarrier } from '../api/admin'

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

// 加载承运商数据
async function loadData() {
  try {
    const res = await getCarriers()
    const list = res || []
    let filtered = [...list]
    if (filters.keyword) {
      filtered = filtered.filter(item => item.name.includes(filters.keyword))
    }
    tableData.value = filtered
    total.value = filtered.length
  } catch (error) {
    console.error('获取承运商数据失败:', error)
    ElMessage.error('获取承运商数据失败')
  }
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建承运商'; editForm.name = ''; editForm.transportMode = '快递'; editForm.avgDays = 3; editForm.firstWeightPrice = 0; editForm.renewWeightPrice = 0; editForm.praiseRate = 95; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑承运商'; Object.assign(editForm, row); dialogVisible.value = true }
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deleteCarrier(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}
async function handleSave() {
  try {
    if (editForm.id) {
      await updateCarrier(editForm.id, {
        name: editForm.name,
        transportMode: editForm.transportMode,
        avgDays: editForm.avgDays,
        firstWeightPrice: editForm.firstWeightPrice,
        renewWeightPrice: editForm.renewWeightPrice,
        praiseRate: editForm.praiseRate
      })
    } else {
      await createCarrier({
        name: editForm.name,
        transportMode: editForm.transportMode,
        avgDays: editForm.avgDays,
        firstWeightPrice: editForm.firstWeightPrice,
        renewWeightPrice: editForm.renewWeightPrice,
        praiseRate: editForm.praiseRate
      })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}
onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
