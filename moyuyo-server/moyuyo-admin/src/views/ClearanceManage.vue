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
          <el-input v-model="filters.keyword" placeholder="请输入报关单号/订单号" clearable />
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
        <el-table-column prop="declarationNo" label="报关单号" width="160" />
        <el-table-column prop="orderNo" label="订单号" width="160" />
        <el-table-column prop="productName" label="商品名称" width="140" />
        <el-table-column label="清关状态" width="120">
          <template #default="{ row }">
            <el-tag :type="clearanceTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="declarationTime" label="申报时间" width="160" />
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
        <el-form-item label="报关单号">
          <el-input v-model="editForm.declarationNo" placeholder="请输入报关单号" />
        </el-form-item>
        <el-form-item label="订单号">
          <el-input v-model="editForm.orderNo" placeholder="请输入订单号" />
        </el-form-item>
        <el-form-item label="商品名称">
          <el-input v-model="editForm.productName" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="清关状态">
          <el-select v-model="editForm.status">
            <el-option label="待申报" value="待申报" />
            <el-option label="申报中" value="申报中" />
            <el-option label="已放行" value="已放行" />
            <el-option label="被查验" value="被查验" />
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
import { getClearance, createClearance, updateClearance, deleteClearance } from '../api/admin'

const pageTitle = '清关管理'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  declarationNo: '',
  orderNo: '',
  productName: '',
  status: '待申报'
})

function clearanceTagType(status) {
  const map = { '待申报': 'info', '申报中': 'warning', '已放行': 'success', '被查验': 'danger' }
  return map[status] || 'info'
}

// 加载报关数据
async function loadData() {
  try {
    const res = await getClearance()
    const list = res || []
    let filtered = [...list]
    if (filters.keyword) {
      filtered = filtered.filter(item => item.declarationNo.includes(filters.keyword) || item.orderNo.includes(filters.keyword))
    }
    tableData.value = filtered
    total.value = filtered.length
  } catch (error) {
    console.error('获取报关数据失败:', error)
    ElMessage.error('获取报关数据失败')
  }
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建清关申报'; editForm.declarationNo = ''; editForm.orderNo = ''; editForm.productName = ''; editForm.status = '待申报'; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑清关'; Object.assign(editForm, row); dialogVisible.value = true }
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deleteClearance(row.id)
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
      await updateClearance(editForm.id, {
        declarationNo: editForm.declarationNo,
        orderNo: editForm.orderNo,
        productName: editForm.productName,
        status: editForm.status
      })
    } else {
      await createClearance({
        declarationNo: editForm.declarationNo,
        orderNo: editForm.orderNo,
        productName: editForm.productName,
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
