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
          <el-input v-model="filters.keyword" placeholder="请输入搜索关键词" clearable />
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
        <el-table-column prop="mergeNo" label="合包编号" width="160" />
        <el-table-column prop="orderCount" label="包含订单数" width="100" />
        <el-table-column prop="packageCount" label="包裹数" width="80" />
        <el-table-column prop="totalWeight" label="总重量(kg)" width="100" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === '已合包' ? 'success' : 'warning'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
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
        <el-form-item label="合包编号">
          <el-input v-model="editForm.mergeNo" placeholder="请输入合包编号" />
        </el-form-item>
        <el-form-item label="包含订单数">
          <el-input-number v-model="editForm.orderCount" :min="1" />
        </el-form-item>
        <el-form-item label="包裹数">
          <el-input-number v-model="editForm.packageCount" :min="1" />
        </el-form-item>
        <el-form-item label="总重量(kg)">
          <el-input-number v-model="editForm.totalWeight" :min="0" :step="0.1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="待合包" value="待合包" />
            <el-option label="已合包" value="已合包" />
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
import { getMergePackages, createMergePackage, updateMergePackage, deleteMergePackage } from '../api/admin'

const pageTitle = '合包管理'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  mergeNo: '',
  orderCount: 1,
  packageCount: 1,
  totalWeight: 0,
  status: '待合包'
})

// 加载合包数据
async function loadData() {
  try {
    const res = await getMergePackages()
    const list = res || []
    let filtered = [...list]
    if (filters.keyword) {
      filtered = filtered.filter(item => item.mergeNo.includes(filters.keyword))
    }
    tableData.value = filtered
    total.value = filtered.length
  } catch (error) {
    console.error('获取合包数据失败:', error)
    ElMessage.error('获取合包数据失败')
  }
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建合包'; editForm.mergeNo = ''; editForm.orderCount = 1; editForm.packageCount = 1; editForm.totalWeight = 0; editForm.status = '待合包'; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑合包'; Object.assign(editForm, row); dialogVisible.value = true }
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deleteMergePackage(row.id)
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
      await updateMergePackage(editForm.id, {
        mergeNo: editForm.mergeNo,
        orderCount: editForm.orderCount,
        packageCount: editForm.packageCount,
        totalWeight: editForm.totalWeight,
        status: editForm.status
      })
    } else {
      await createMergePackage({
        mergeNo: editForm.mergeNo,
        orderCount: editForm.orderCount,
        packageCount: editForm.packageCount,
        totalWeight: editForm.totalWeight,
        status: editForm.status
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
